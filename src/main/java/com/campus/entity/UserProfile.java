package com.campus.entity;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;
import java.util.Map;

/**
 * 用户兴趣画像实体类
 * 
 * 成员A：用户兴趣画像系统
 * 
 * 功能：记录每个用户的多维兴趣信息，用于后续的个性化推荐匹配。
 * 数据同时存储在 Redis（Hash，用于快速读写）和 MySQL（JSON，用于持久化）中。
 * 
 * Redis Hash 存储结构：
 *   Key: user:profile:{userId}
 *   Field "categoryWeights" → {"1": 5, "3": 3, "5": 1}  （分类ID → 浏览频次）
 *   Field "priceRange"      → {"min": 10.0, "max": 99.0, "avg": 45.0}
 *   Field "keywords"        → {"算法": 3, "Java": 2, "编程": 1}  （关键词 → 频次）
 *   Field "lastBrowseTime"  → 1680000000000  （时间戳）
 *   Field "browseCount"     → 42
 *   Field "purchaseCount"   → 3
 *   Field "version"         → 5  （乐观锁版本号）
 * 
 * MySQL 存储结构：
 *   t_user_profile 表，profile_data 字段存储完整的 JSON 字符串
 * 
 * ========== 给成员B的接口说明 ==========
 * 成员B（匹配引擎）需要通过 UserProfileService 调用以下方法获取画像数据：
 *   1. getProfile(userId)          → 获取完整画像
 *   2. getCategoryWeights(userId)  → 获取分类权重（用于分类匹配度计算）
 *   3. getPriceRange(userId)       → 获取价格区间（用于价格匹配度计算）
 *   4. getKeywords(userId)         → 获取关键词频次（用于关键词匹配度计算）
 * 详见 UserProfileService 接口注释。
 */
@Data
public class UserProfile implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Integer userId;

    /** 分类偏好权重（key: categoryId, value: 浏览频次） */
    private Map<Integer, Integer> categoryWeights;

    /** 价格区间 */
    private PriceRange priceRange;

    /** 关键词偏好（key: 关键词, value: 出现频次） */
    private Map<String, Integer> keywords;

    /** 最近浏览时间 */
    private Date lastBrowseTime;

    /** 总浏览数（近30天） */
    private Integer browseCount;

    /** 购买次数 */
    private Integer purchaseCount;

    /** 画像版本号（用于乐观锁控制并发更新） */
    private Long version;

    /** 最后更新时间 */
    private Date updatedTime;

    /** 
     * JSON 格式的画像数据（用于 MyBatis typeHandler 序列化/反序列化）
     * 注意：此字段不直接存储业务数据，而是作为 MyBatis JSON typeHandler 的中间载体。
     * 在 syncProfileToDb() 中会将 categoryWeights/priceRange/keywords 等序列化为 JSON 字符串存入此字段。
     */
    private String profileDataJson;

    /**
     * 价格区间内部类
     */
    @Data
    public static class PriceRange implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 浏览过的最低价格 */
        private Double minPrice;

        /** 浏览过的最高价格 */
        private Double maxPrice;

        /** 平均浏览价格 */
        private Double avgPrice;

        public PriceRange() {
            this.minPrice = Double.MAX_VALUE;
            this.maxPrice = 0.0;
            this.avgPrice = 0.0;
        }

        public PriceRange(Double minPrice, Double maxPrice, Double avgPrice) {
            this.minPrice = minPrice;
            this.maxPrice = maxPrice;
            this.avgPrice = avgPrice;
        }

        /**
         * 判断商品价格是否在用户常浏览的价格区间内
         * 给成员B（匹配引擎）使用：用于价格匹配度计算
         */
        public boolean contains(Double price) {
            if (price == null) return false;
            return price >= minPrice && price <= maxPrice;
        }
    }
}
