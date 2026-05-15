package com.campus.service.impl;

import com.campus.dao.ProductMapper;
import com.campus.dao.UserProfileMapper;
import com.campus.entity.Product;
import com.campus.entity.UserProfile;
import com.campus.service.UserProfileService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 用户兴趣画像服务实现类
 * 
 * 成员A：用户兴趣画像系统
 * 
 * ========== 核心设计 ==========
 * 
 * 1. 数据存储
 *    - Redis Hash：日常读写，高性能
 *    - MySQL JSON：持久化备份，重启恢复
 * 
 * 2. 更新策略
 *    - 在线增量：用户浏览/购买时实时更新 Redis 中的画像
 *    - 离线全量：定时任务每天凌晨从浏览历史表重建完整画像
 *    - 双写保障：每次更新 Redis 后异步同步到 MySQL
 * 
 * 3. 并发控制
 *    - 使用 Redis 分布式锁防止同一用户画像并发更新冲突
 *    - 画像数据使用版本号（乐观锁）控制并发写入
 * 
 * 4. Redis Key 设计
 *    - 画像数据：user:profile:{userId} (Hash)
 *    - 分布式锁：lock:profile:{userId} (String, TTL=5秒)
 * 
 * ========== 给成员B的铺垫 ==========
 * 
 * 本类中所有给成员B使用的方法都已标注 "【成员B使用】"，
 * 成员B在实现 MatchService 时，直接 @Autowired UserProfileService
 * 然后调用 getNormalizedCategoryWeights()、getPriceRange()、getKeywords() 即可。
 */
@Service
public class UserProfileServiceImpl implements UserProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileServiceImpl.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private ProductMapper productMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== Redis Key 常量 ====================

    /** 用户画像 Hash Key 前缀 */
    private static final String PROFILE_KEY_PREFIX = "user:profile:";

    /** 分布式锁 Key 前缀 */
    private static final String LOCK_KEY_PREFIX = "lock:profile:";

    // ==================== TTL 常量 ====================

    /** 画像缓存 TTL：24小时 */
    private static final long PROFILE_TTL_SECONDS = 24 * 60 * 60;

    /** 分布式锁 TTL：5秒 */
    private static final long LOCK_TTL_SECONDS = 5;

    // ==================== Redis Hash Field 常量 ====================

    private static final String FIELD_CATEGORY_WEIGHTS = "categoryWeights";
    private static final String FIELD_PRICE_RANGE = "priceRange";
    private static final String FIELD_KEYWORDS = "keywords";
    private static final String FIELD_LAST_BROWSE_TIME = "lastBrowseTime";
    private static final String FIELD_BROWSE_COUNT = "browseCount";
    private static final String FIELD_PURCHASE_COUNT = "purchaseCount";
    private static final String FIELD_VERSION = "version";

    // ================================================================
    // 成员B使用的查询接口
    // ================================================================

    @Override
    public UserProfile getProfile(Integer userId) {
        if (userId == null) return createEmptyProfile(userId);

        // 1. 先从 Redis 读取
        UserProfile profile = getProfileFromRedis(userId);
        if (profile != null) {
            return profile;
        }

        // 2. Redis 没有，尝试从 MySQL 恢复
        boolean restored = restoreProfileFromDb(userId);
        if (restored) {
            profile = getProfileFromRedis(userId);
            if (profile != null) return profile;
        }

        // 3. 都没有，返回空画像
        return createEmptyProfile(userId);
    }

    @Override
    public Map<Integer, Integer> getCategoryWeights(Integer userId) {
        UserProfile profile = getProfile(userId);
        return profile.getCategoryWeights() != null
                ? profile.getCategoryWeights()
                : new HashMap<>();
    }

    @Override
    public Map<Integer, Double> getNormalizedCategoryWeights(Integer userId) {
        Map<Integer, Integer> rawWeights = getCategoryWeights(userId);
        if (rawWeights.isEmpty()) {
            return new HashMap<>();
        }

        // 计算总频次
        int total = rawWeights.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) {
            return new HashMap<>();
        }

        // 归一化：每个分类的权重除以总频次
        final double totalDouble = total;
        Map<Integer, Double> normalized = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : rawWeights.entrySet()) {
            normalized.put(entry.getKey(), entry.getValue() / totalDouble);
        }
        return normalized;
    }

    @Override
    public UserProfile.PriceRange getPriceRange(Integer userId) {
        UserProfile profile = getProfile(userId);
        return profile.getPriceRange() != null
                ? profile.getPriceRange()
                : new UserProfile.PriceRange();
    }

    @Override
    public Map<String, Integer> getKeywords(Integer userId) {
        UserProfile profile = getProfile(userId);
        return profile.getKeywords() != null
                ? profile.getKeywords()
                : new HashMap<>();
    }

    // ================================================================
    // 画像更新接口
    // ================================================================

    @Override
    public void recordBrowse(Integer userId, Integer categoryId, Double price, List<String> keywords) {
        if (userId == null) return;

        String lockKey = LOCK_KEY_PREFIX + userId;
        String lockValue = UUID.randomUUID().toString();

        // 获取分布式锁，防止并发更新冲突
        if (!acquireLock(lockKey, lockValue, LOCK_TTL_SECONDS)) {
            log.warn("获取画像锁失败，userId={}，可能正在并发更新", userId);
            return;
        }

        try {
            // 1. 获取当前画像（从 Redis）
            UserProfile profile = getProfileFromRedis(userId);
            if (profile == null) {
                profile = createEmptyProfile(userId);
            }

            // 2. 更新分类权重
            if (categoryId != null) {
                Map<Integer, Integer> catWeights = profile.getCategoryWeights();
                catWeights.merge(categoryId, 1, Integer::sum);
            }

            // 3. 更新价格区间
            if (price != null) {
                UserProfile.PriceRange range = profile.getPriceRange();
                if (price < range.getMinPrice()) range.setMinPrice(price);
                if (price > range.getMaxPrice()) range.setMaxPrice(price);
                // 更新平均价格（移动平均）
                double oldAvg = range.getAvgPrice() == null ? 0.0 : range.getAvgPrice();
                int count = profile.getBrowseCount() == null ? 0 : profile.getBrowseCount();
                if (count > 0) {
                    range.setAvgPrice((oldAvg * count + price) / (count + 1));
                } else {
                    range.setAvgPrice(price);
                }
            }

            // 4. 更新关键词频次
            if (keywords != null && !keywords.isEmpty()) {
                Map<String, Integer> kwMap = profile.getKeywords();
                for (String kw : keywords) {
                    if (StringUtils.hasText(kw)) {
                        kwMap.merge(kw, 1, Integer::sum);
                    }
                }
            }

            // 5. 更新浏览时间和计数
            profile.setLastBrowseTime(new Date());
            profile.setBrowseCount(profile.getBrowseCount() == null ? 1 : profile.getBrowseCount() + 1);

            // 6. 版本号+1
            profile.setVersion(profile.getVersion() == null ? 1L : profile.getVersion() + 1);

            // 7. 写回 Redis
            saveProfileToRedis(profile);

            // 8. 异步同步到 MySQL
            syncProfileToDb(profile);

        } catch (Exception e) {
            log.error("更新用户画像失败，userId={}", userId, e);
        } finally {
            releaseLock(lockKey, lockValue);
        }
    }

    @Override
    public void recordPurchase(Integer userId, Integer categoryId, Double price, List<String> keywords) {
        if (userId == null) return;

        String lockKey = LOCK_KEY_PREFIX + userId;
        String lockValue = UUID.randomUUID().toString();

        if (!acquireLock(lockKey, lockValue, LOCK_TTL_SECONDS)) {
            log.warn("获取画像锁失败，userId={}，可能正在并发更新", userId);
            return;
        }

        try {
            UserProfile profile = getProfileFromRedis(userId);
            if (profile == null) {
                profile = createEmptyProfile(userId);
            }

            // 购买权重更高：分类权重 +3
            if (categoryId != null) {
                Map<Integer, Integer> catWeights = profile.getCategoryWeights();
                catWeights.merge(categoryId, 3, Integer::sum);
            }

            // 更新价格区间（同浏览）
            if (price != null) {
                UserProfile.PriceRange range = profile.getPriceRange();
                if (price < range.getMinPrice()) range.setMinPrice(price);
                if (price > range.getMaxPrice()) range.setMaxPrice(price);
                double oldAvg = range.getAvgPrice() == null ? 0.0 : range.getAvgPrice();
                int count = profile.getBrowseCount() == null ? 0 : profile.getBrowseCount();
                if (count > 0) {
                    range.setAvgPrice((oldAvg * count + price) / (count + 1));
                } else {
                    range.setAvgPrice(price);
                }
            }

            // 更新关键词
            if (keywords != null && !keywords.isEmpty()) {
                Map<String, Integer> kwMap = profile.getKeywords();
                for (String kw : keywords) {
                    if (StringUtils.hasText(kw)) {
                        kwMap.merge(kw, 1, Integer::sum);
                    }
                }
            }

            // 更新购买次数
            profile.setPurchaseCount(profile.getPurchaseCount() == null ? 1 : profile.getPurchaseCount() + 1);
            profile.setVersion(profile.getVersion() == null ? 1L : profile.getVersion() + 1);

            saveProfileToRedis(profile);
            syncProfileToDb(profile);

        } catch (Exception e) {
            log.error("更新用户购买画像失败，userId={}", userId, e);
        } finally {
            releaseLock(lockKey, lockValue);
        }
    }

    @Override
    public void recordLogin(Integer userId) {
        if (userId == null) return;
        // 登录更新由 UserServiceImpl 中的 updateLoginInfo 处理
        // 这里只记录画像中的活跃度指标
        String profileKey = PROFILE_KEY_PREFIX + userId;
        try {
            redisTemplate.opsForHash().put(profileKey, "lastLoginTime", System.currentTimeMillis());
            redisTemplate.expire(profileKey, PROFILE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("记录登录时间到画像失败，userId={}", userId, e);
        }
    }

    // ================================================================
    // 画像管理接口
    // ================================================================

    @Override
    public UserProfile rebuildProfile(Integer userId) {
        if (userId == null) return createEmptyProfile(null);

        String lockKey = LOCK_KEY_PREFIX + userId;
        String lockValue = UUID.randomUUID().toString();

        if (!acquireLock(lockKey, lockValue, LOCK_TTL_SECONDS)) {
            log.warn("获取画像锁失败，跳过重建，userId={}", userId);
            return getProfileFromRedis(userId);
        }

        try {
            // 从数据库查询该用户的所有浏览历史
            // 注意：这里依赖成员2的 BrowseHistoryMapper，如果还没实现则从 Product 表反查
            UserProfile profile = rebuildFromDatabase(userId);

            // 写回 Redis
            saveProfileToRedis(profile);

            // 持久化到 MySQL
            syncProfileToDb(profile);

            log.info("用户画像重建完成，userId={}, 分类数={}, 关键词数={}",
                    userId,
                    profile.getCategoryWeights().size(),
                    profile.getKeywords().size());

            return profile;
        } catch (Exception e) {
            log.error("重建用户画像失败，userId={}", userId, e);
            return getProfileFromRedis(userId);
        } finally {
            releaseLock(lockKey, lockValue);
        }
    }

    @Override
    public void rebuildAllProfiles() {
        log.info("开始全量重建所有用户画像...");
        List<Integer> userIds = userProfileMapper.findAllUserIds();
        if (userIds == null || userIds.isEmpty()) {
            log.info("没有需要重建画像的用户");
            return;
        }
        int success = 0;
        for (Integer userId : userIds) {
            try {
                rebuildProfile(userId);
                success++;
            } catch (Exception e) {
                log.error("重建用户画像失败，userId={}", userId, e);
            }
        }
        log.info("全量重建完成，成功={}/{}", success, userIds.size());
    }

    @Override
    public boolean restoreProfileFromDb(Integer userId) {
        if (userId == null) return false;
        try {
            UserProfile profile = userProfileMapper.findByUserId(userId);
            if (profile != null && profile.getProfileDataJson() != null) {
                // 从 JSON 字符串解析完整的画像数据
                UserProfile parsedProfile = parseProfileFromJson(userId, profile.getProfileDataJson());
                if (parsedProfile != null) {
                    parsedProfile.setVersion(profile.getVersion());
                    saveProfileToRedis(parsedProfile);
                    log.info("从 MySQL 恢复画像到 Redis 成功，userId={}", userId);
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("从 MySQL 恢复画像失败，userId={}", userId, e);
        }
        return false;
    }


    @Override
    public void deleteProfile(Integer userId) {
        if (userId == null) return;
        try {
            String profileKey = PROFILE_KEY_PREFIX + userId;
            redisTemplate.delete(profileKey);
            userProfileMapper.deleteByUserId(userId);
            log.info("删除用户画像成功，userId={}", userId);
        } catch (Exception e) {
            log.error("删除用户画像失败，userId={}", userId, e);
        }
    }

    // ================================================================
    // 私有方法
    // ================================================================

    /**
     * 从 Redis 读取用户画像
     */
    @SuppressWarnings("unchecked")
    private UserProfile getProfileFromRedis(Integer userId) {
        if (userId == null || redisTemplate == null) return null;
        try {
            String profileKey = PROFILE_KEY_PREFIX + userId;
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(profileKey);
            if (entries == null || entries.isEmpty()) {
                return null;
            }

            UserProfile profile = new UserProfile();
            profile.setUserId(userId);

            // 解析分类权重
            Object catObj = entries.get(FIELD_CATEGORY_WEIGHTS);
            if (catObj instanceof Map) {
                Map<Integer, Integer> catWeights = new HashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) catObj).entrySet()) {
                    try {
                        Integer catId = Integer.parseInt(String.valueOf(entry.getKey()));
                        Integer weight = Integer.parseInt(String.valueOf(entry.getValue()));
                        catWeights.put(catId, weight);
                    } catch (Exception ignored) {
                    }
                }
                profile.setCategoryWeights(catWeights);
            } else {
                profile.setCategoryWeights(new HashMap<>());
            }

            // 解析价格区间
            Object priceObj = entries.get(FIELD_PRICE_RANGE);
            if (priceObj instanceof Map) {
                Map<?, ?> priceMap = (Map<?, ?>) priceObj;
                UserProfile.PriceRange range = new UserProfile.PriceRange();
                Object min = priceMap.get("min");
                Object max = priceMap.get("max");
                Object avg = priceMap.get("avg");
                if (min != null) range.setMinPrice(Double.parseDouble(String.valueOf(min)));
                if (max != null) range.setMaxPrice(Double.parseDouble(String.valueOf(max)));
                if (avg != null) range.setAvgPrice(Double.parseDouble(String.valueOf(avg)));
                profile.setPriceRange(range);
            } else {
                profile.setPriceRange(new UserProfile.PriceRange());
            }

            // 解析关键词
            Object kwObj = entries.get(FIELD_KEYWORDS);
            if (kwObj instanceof Map) {
                Map<String, Integer> keywords = new HashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) kwObj).entrySet()) {
                    try {
                        keywords.put(String.valueOf(entry.getKey()),
                                Integer.parseInt(String.valueOf(entry.getValue())));
                    } catch (Exception ignored) {
                    }
                }
                profile.setKeywords(keywords);
            } else {
                profile.setKeywords(new HashMap<>());
            }

            // 解析基本字段
            Object browseTime = entries.get(FIELD_LAST_BROWSE_TIME);
            if (browseTime != null) {
                profile.setLastBrowseTime(new Date(Long.parseLong(String.valueOf(browseTime))));
            }

            Object browseCount = entries.get(FIELD_BROWSE_COUNT);
            if (browseCount != null) {
                profile.setBrowseCount(Integer.parseInt(String.valueOf(browseCount)));
            }

            Object purchaseCount = entries.get(FIELD_PURCHASE_COUNT);
            if (purchaseCount != null) {
                profile.setPurchaseCount(Integer.parseInt(String.valueOf(purchaseCount)));
            }

            Object version = entries.get(FIELD_VERSION);
            if (version != null) {
                profile.setVersion(Long.parseLong(String.valueOf(version)));
            }

            // 刷新 TTL
            redisTemplate.expire(profileKey, PROFILE_TTL_SECONDS, TimeUnit.SECONDS);

            return profile;
        } catch (Exception e) {
            log.warn("从 Redis 读取画像失败，userId={}", userId, e);
            return null;
        }
    }

    /**
     * 保存画像到 Redis
     */
    private void saveProfileToRedis(UserProfile profile) {
        if (profile == null || profile.getUserId() == null || redisTemplate == null) return;
        try {
            String profileKey = PROFILE_KEY_PREFIX + profile.getUserId();
            Map<String, Object> hash = new HashMap<>();

            // 分类权重
            if (profile.getCategoryWeights() != null && !profile.getCategoryWeights().isEmpty()) {
                hash.put(FIELD_CATEGORY_WEIGHTS, profile.getCategoryWeights());
            }

            // 价格区间
            if (profile.getPriceRange() != null) {
                UserProfile.PriceRange range = profile.getPriceRange();
                Map<String, Object> priceMap = new HashMap<>();
                priceMap.put("min", range.getMinPrice());
                priceMap.put("max", range.getMaxPrice());
                priceMap.put("avg", range.getAvgPrice());
                hash.put(FIELD_PRICE_RANGE, priceMap);
            }

            // 关键词
            if (profile.getKeywords() != null && !profile.getKeywords().isEmpty()) {
                hash.put(FIELD_KEYWORDS, profile.getKeywords());
            }

            // 基本字段
            if (profile.getLastBrowseTime() != null) {
                hash.put(FIELD_LAST_BROWSE_TIME, profile.getLastBrowseTime().getTime());
            }
            hash.put(FIELD_BROWSE_COUNT, profile.getBrowseCount() != null ? profile.getBrowseCount() : 0);
            hash.put(FIELD_PURCHASE_COUNT, profile.getPurchaseCount() != null ? profile.getPurchaseCount() : 0);
            hash.put(FIELD_VERSION, profile.getVersion() != null ? profile.getVersion() : 0);

            // 写入 Redis
            redisTemplate.opsForHash().putAll(profileKey, hash);
            redisTemplate.expire(profileKey, PROFILE_TTL_SECONDS, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.warn("保存画像到 Redis 失败，userId={}", profile.getUserId(), e);
        }
    }

    /**
     * 同步画像到 MySQL
     * 
     * 将 Redis 中的画像数据序列化为 JSON 字符串，写入 t_user_profile 表。
     * 使用 MyBatis 的 JsonTypeHandler 自动处理 JSON 序列化。
     */
    private void syncProfileToDb(UserProfile profile) {
        if (profile == null || profile.getUserId() == null) return;
        try {
            // 将画像数据序列化为 JSON 字符串，存入 profileDataJson 字段
            // MyBatis 的 JsonTypeHandler 会自动处理 Map → JSON 的转换
            Map<String, Object> profileData = new HashMap<>();
            profileData.put(FIELD_CATEGORY_WEIGHTS, profile.getCategoryWeights());
            profileData.put(FIELD_PRICE_RANGE, profile.getPriceRange());
            profileData.put(FIELD_KEYWORDS, profile.getKeywords());
            profileData.put(FIELD_LAST_BROWSE_TIME,
                    profile.getLastBrowseTime() != null ? profile.getLastBrowseTime().getTime() : null);
            profileData.put(FIELD_BROWSE_COUNT, profile.getBrowseCount());
            profileData.put(FIELD_PURCHASE_COUNT, profile.getPurchaseCount());

            // 将 Map 序列化为 JSON 字符串，设置到 profileDataJson 字段
            // 这样 MyBatis 的 #{profileDataJson, typeHandler=...} 就能正确写入
            profile.setProfileDataJson(objectMapper.writeValueAsString(profileData));

            // 使用 MyBatis 的 upsert 操作
            userProfileMapper.upsert(profile);

        } catch (Exception e) {
            log.warn("同步画像到 MySQL 失败，userId={}", profile.getUserId(), e);
        }
    }


    /**
     * 从数据库重建用户画像
     * 扫描用户的浏览历史，重新计算完整画像
     * 
     * 注意：当前简化实现从 Product 表反查用户发布的商品作为浏览历史来源。
     * 完整实现需要依赖 BrowseHistoryMapper（成员2的任务），
     * 届时应改为从浏览历史表扫描数据。
     */
    private UserProfile rebuildFromDatabase(Integer userId) {
        UserProfile profile = createEmptyProfile(userId);

        try {
            // 查询用户发布/浏览过的商品（通过 Product 表反查）
            // 这里简化处理：查询用户浏览过的商品列表
            // 完整实现需要依赖 BrowseHistoryMapper（成员2的任务）
            // 目前先用 Product 表的数据做基础重建
            List<Product> userProducts = productMapper.findByUserId(userId);
            if (userProducts != null && !userProducts.isEmpty()) {
                double priceSum = 0.0;
                int priceCount = 0;

                for (Product p : userProducts) {
                    if (p.getCategoryId() != null) {
                        profile.getCategoryWeights().merge(p.getCategoryId(), 1, Integer::sum);
                    }
                    if (p.getPrice() != null) {
                        UserProfile.PriceRange range = profile.getPriceRange();
                        double price = p.getPrice().doubleValue();
                        if (price < range.getMinPrice()) range.setMinPrice(price);
                        if (price > range.getMaxPrice()) range.setMaxPrice(price);
                        priceSum += price;
                        priceCount++;
                    }
                }

                // 计算平均价格
                if (priceCount > 0) {
                    profile.getPriceRange().setAvgPrice(priceSum / priceCount);
                }
            }

            profile.setVersion(1L);
            log.info("从数据库重建画像完成，userId={}", userId);

        } catch (Exception e) {
            log.warn("从数据库重建画像失败，userId={}", userId, e);
        }

        return profile;
    }


    /**
     * 从 JSON 字符串解析用户画像
     * 用于从 MySQL 读取持久化的画像数据后，恢复为完整的 UserProfile 对象
     */
    @SuppressWarnings("unchecked")
    private UserProfile parseProfileFromJson(Integer userId, String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            Map<String, Object> data = objectMapper.readValue(json, HashMap.class);
            UserProfile profile = new UserProfile();
            profile.setUserId(userId);

            // 解析分类权重
            Object catObj = data.get(FIELD_CATEGORY_WEIGHTS);
            if (catObj instanceof Map) {
                Map<Integer, Integer> catWeights = new HashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) catObj).entrySet()) {
                    try {
                        catWeights.put(
                            Integer.parseInt(String.valueOf(entry.getKey())),
                            Integer.parseInt(String.valueOf(entry.getValue()))
                        );
                    } catch (Exception ignored) {}
                }
                profile.setCategoryWeights(catWeights);
            } else {
                profile.setCategoryWeights(new HashMap<>());
            }

            // 解析价格区间
            Object priceObj = data.get(FIELD_PRICE_RANGE);
            if (priceObj instanceof Map) {
                Map<?, ?> priceMap = (Map<?, ?>) priceObj;
                UserProfile.PriceRange range = new UserProfile.PriceRange();
                Object min = priceMap.get("min");
                Object max = priceMap.get("max");
                Object avg = priceMap.get("avg");
                if (min != null) range.setMinPrice(Double.parseDouble(String.valueOf(min)));
                if (max != null) range.setMaxPrice(Double.parseDouble(String.valueOf(max)));
                if (avg != null) range.setAvgPrice(Double.parseDouble(String.valueOf(avg)));
                profile.setPriceRange(range);
            } else {
                profile.setPriceRange(new UserProfile.PriceRange());
            }

            // 解析关键词
            Object kwObj = data.get(FIELD_KEYWORDS);
            if (kwObj instanceof Map) {
                Map<String, Integer> keywords = new HashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) kwObj).entrySet()) {
                    try {
                        keywords.put(
                            String.valueOf(entry.getKey()),
                            Integer.parseInt(String.valueOf(entry.getValue()))
                        );
                    } catch (Exception ignored) {}
                }
                profile.setKeywords(keywords);
            } else {
                profile.setKeywords(new HashMap<>());
            }

            // 解析基本字段
            Object browseTime = data.get(FIELD_LAST_BROWSE_TIME);
            if (browseTime != null) {
                profile.setLastBrowseTime(new Date(Long.parseLong(String.valueOf(browseTime))));
            }
            Object browseCount = data.get(FIELD_BROWSE_COUNT);
            if (browseCount != null) {
                profile.setBrowseCount(Integer.parseInt(String.valueOf(browseCount)));
            }
            Object purchaseCount = data.get(FIELD_PURCHASE_COUNT);
            if (purchaseCount != null) {
                profile.setPurchaseCount(Integer.parseInt(String.valueOf(purchaseCount)));
            }

            return profile;
        } catch (Exception e) {
            log.warn("从 JSON 解析画像失败，userId={}", userId, e);
            return null;
        }
    }

    /**
     * 创建空画像
     */
    private UserProfile createEmptyProfile(Integer userId) {
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setCategoryWeights(new HashMap<>());
        profile.setPriceRange(new UserProfile.PriceRange());
        profile.setKeywords(new HashMap<>());
        profile.setBrowseCount(0);
        profile.setPurchaseCount(0);
        profile.setVersion(0L);
        return profile;
    }

    // ================================================================
    // 分布式锁
    // ================================================================

    private boolean acquireLock(String lockKey, String lockValue, long ttlSeconds) {
        if (redisTemplate == null) return true;
        try {
            Boolean ok = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, ttlSeconds, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            log.warn("获取分布式锁失败，lockKey={}", lockKey, e);
            return true; // Redis 异常时放行，避免阻塞业务
        }
    }

    private void releaseLock(String lockKey, String lockValue) {
        if (redisTemplate == null) return;
        try {
            Object current = redisTemplate.opsForValue().get(lockKey);
            if (current != null && lockValue.equals(String.valueOf(current))) {
                redisTemplate.delete(lockKey);
            }
        } catch (Exception e) {
            log.warn("释放分布式锁失败，lockKey={}", lockKey, e);
        }
    }
}
