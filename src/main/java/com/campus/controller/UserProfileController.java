package com.campus.controller;

import com.campus.common.Result;
import com.campus.entity.User;
import com.campus.entity.UserProfile;
import com.campus.service.DegradeService;
import com.campus.service.UserProfileService;
import com.campus.util.SessionUserHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import com.campus.service.InboxService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * 用户兴趣画像控制器
 * 
 * 成员A：用户兴趣画像系统
 * 
 * 功能：
 *   1. 提供画像可视化接口，返回当前用户的完整画像JSON
 *   2. 提供画像调试接口，方便开发和验收
 * 
 * 验收方法：
 *   1. 登录后访问 /user/profile → 返回完整画像JSON ✅
 *   2. 浏览几个商品后再次访问 → 画像数据已更新 ✅
 *   3. 用Redis客户端查看 user:profile:{userId} → 数据一致 ✅
 */
@Controller
@RequestMapping("/user")
public class UserProfileController {

    private static final Logger log = LoggerFactory.getLogger(UserProfileController.class);

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private DegradeService degradeService;

    @Autowired
    private InboxService inboxService;
    /**
     * 获取当前登录用户的兴趣画像
     * 
     * 返回完整的画像数据，包括：
     *   - categoryWeights: 分类偏好权重
     *   - normalizedWeights: 归一化后的分类权重（总和=1）
     *   - priceRange: 价格偏好区间
     *   - keywords: 关键词偏好
     *   - browseCount: 浏览总数
     *   - purchaseCount: 购买次数
     * 
     * 给成员B使用：成员B可以通过此接口验证画像数据是否正确
     */
    @RequestMapping("/profile")
    @ResponseBody
    public Result<Map<String, Object>> getProfile(HttpSession session) {
        User user = SessionUserHelper.getLoginUser(session);
        if (user == null) {
            return Result.error(Result.CODE_UNAUTHORIZED, "请先登录");
        }

        UserProfile profile = userProfileService.getProfile(user.getId());
        if (profile == null) {
            return Result.error("画像数据不存在");
        }
        Map<String, Object> data = new HashMap<>();

        Map<Integer, Integer> categoryWeights = profile.getCategoryWeights();
        if (categoryWeights == null) {
            categoryWeights = new HashMap<>();
        }
        data.put("categoryWeights", categoryWeights);
        data.put("normalizedWeights", userProfileService.getNormalizedCategoryWeights(user.getId()));

        UserProfile.PriceRange priceRange = profile.getPriceRange();
        if (priceRange == null) {
            priceRange = new UserProfile.PriceRange();
        }
        Map<String, Object> priceMap = new HashMap<>();
        priceMap.put("min", priceRange.getMinPrice());
        priceMap.put("max", priceRange.getMaxPrice());
        priceMap.put("avg", priceRange.getAvgPrice());
        data.put("priceRange", priceMap);

        Map<String, Integer> keywords = profile.getKeywords();
        data.put("keywords", keywords != null ? keywords : new HashMap<>());
        data.put("browseCount", profile.getBrowseCount());
        data.put("purchaseCount", profile.getPurchaseCount());
        data.put("lastBrowseTime", profile.getLastBrowseTime());
        data.put("version", profile.getVersion());

        log.info("用户画像查询成功，userId={}, 分类数={}, 关键词数={}",
                user.getId(),
                categoryWeights.size(),
                keywords != null ? keywords.size() : 0);

        return Result.success("画像查询成功", data);
    }

    /**
     * 手动触发当前用户画像重建
     * 用于调试和验收
     */
    @RequestMapping("/profile/rebuild")
    @ResponseBody
    public Result<String> rebuildProfile(HttpSession session) {
        User user = SessionUserHelper.getLoginUser(session);
        if (user == null) {
            return Result.error(Result.CODE_UNAUTHORIZED, "请先登录");
        }

        userProfileService.rebuildProfile(user.getId());
        return Result.success("画像重建完成，请访问 /user/profile 查看");
    }

    /**
     * 获取画像的简要摘要（用于首页展示）
     * 返回用户最感兴趣的分类名称和关键词
     */
    @RequestMapping("/profile/summary")
    @ResponseBody
    public Result<Map<String, Object>> getProfileSummary(HttpSession session) {
        User user = SessionUserHelper.getLoginUser(session);
        if (user == null) {
            return Result.error(Result.CODE_UNAUTHORIZED, "请先登录");
        }

        UserProfile profile = userProfileService.getProfile(user.getId());
        Map<String, Object> summary = new HashMap<>();

        // 找出权重最高的分类
        Map<Integer, Integer> catWeights = profile.getCategoryWeights();
        if (catWeights != null && !catWeights.isEmpty()) {
            Map.Entry<Integer, Integer> topCategory = catWeights.entrySet()
                    .stream()
                    .max(Map.Entry.comparingByValue())
                    .orElse(null);
            if (topCategory != null) {
                summary.put("topCategoryId", topCategory.getKey());
                summary.put("topCategoryWeight", topCategory.getValue());
            }
        }

        // 找出频次最高的前5个关键词
        Map<String, Integer> keywords = profile.getKeywords();
        if (keywords != null && !keywords.isEmpty()) {
            String[] topKeywords = keywords.entrySet()
                    .stream()
                    .sorted((a, b) -> b.getValue() - a.getValue())
                    .limit(5)
                    .map(Map.Entry::getKey)
                    .toArray(String[]::new);
            summary.put("topKeywords", topKeywords);
        }

        summary.put("browseCount", profile.getBrowseCount());
        summary.put("purchaseCount", profile.getPurchaseCount());

        return Result.success(summary);
    }

    /**
     * 成员4：推荐收件箱（Redis ZSet，按匹配度降序，含已读状态）
     */
    @RequestMapping("/inbox")
    @ResponseBody
    public Result<List<Map<String, Object>>> getRecommendInbox(HttpSession session) {
        User user = SessionUserHelper.getLoginUser(session);
        if (user == null) {
            return Result.error(Result.CODE_UNAUTHORIZED, "请先登录");
        }
        List<Map<String, Object>> rows = degradeService.inboxForUser(user.getId(), 50);
        enrichReadFlags(user.getId(), rows);
        return Result.success("推荐收件箱", rows);
    }

    /**
     * 收件箱状态：未读数、免打扰
     */
    @RequestMapping("/inbox/status")
    @ResponseBody
    public Result<Map<String, Object>> getInboxStatus(HttpSession session) {
        User user = SessionUserHelper.getLoginUser(session);
        if (user == null) {
            return Result.error(Result.CODE_UNAUTHORIZED, "请先登录");
        }
        Map<String, Object> status = new HashMap<>();
        status.put("unreadCount", inboxService.getUnreadCount(user.getId()));
        status.put("doNotDisturb", inboxService.isDoNotDisturb(user.getId()));
        return Result.success(status);
    }

    /**
     * 轮询新推荐通知（前端每几秒调用，实现近实时提醒）
     */
    @RequestMapping("/inbox/poll")
    @ResponseBody
    public Result<Map<String, Object>> pollInbox(HttpSession session,
                                                 @RequestParam(value = "since", defaultValue = "0") long since) {
        User user = SessionUserHelper.getLoginUser(session);
        if (user == null) {
            return Result.error(Result.CODE_UNAUTHORIZED, "请先登录");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("notifications", inboxService.pollNotifications(user.getId(), since));
        data.put("unreadCount", inboxService.getUnreadCount(user.getId()));
        data.put("doNotDisturb", inboxService.isDoNotDisturb(user.getId()));
        data.put("serverTime", System.currentTimeMillis());
        return Result.success(data);
    }

    /**
     * 单条已读（点击商品时）
     */
    @RequestMapping(value = "/inbox/read-one", method = RequestMethod.POST)
    @ResponseBody
    public Result<Map<String, Object>> markInboxOneRead(HttpSession session,
                                                       @RequestParam("productId") Integer productId) {
        User user = SessionUserHelper.getLoginUser(session);
        if (user == null) {
            return Result.error(Result.CODE_UNAUTHORIZED, "请先登录");
        }
        inboxService.markRead(user.getId(), productId);
        Map<String, Object> data = new HashMap<>();
        data.put("unreadCount", inboxService.getUnreadCount(user.getId()));
        return Result.success("已标为已读", data);
    }

    /**
     * 一键已读
     */
    @RequestMapping(value = "/inbox/read-all", method = RequestMethod.POST)
    @ResponseBody
    public Result<Map<String, Object>> markInboxAllRead(HttpSession session) {
        User user = SessionUserHelper.getLoginUser(session);
        if (user == null) {
            return Result.error(Result.CODE_UNAUTHORIZED, "请先登录");
        }
        inboxService.markAllRead(user.getId());
        Map<String, Object> data = new HashMap<>();
        data.put("unreadCount", 0);
        return Result.success("已全部标为已读", data);
    }

    /**
     * 免打扰开关
     */
    @RequestMapping(value = "/inbox/dnd", method = RequestMethod.POST)
    @ResponseBody
    public Result<Map<String, Object>> setInboxDnd(HttpSession session,
                                                  @RequestParam("enabled") boolean enabled) {
        User user = SessionUserHelper.getLoginUser(session);
        if (user == null) {
            return Result.error(Result.CODE_UNAUTHORIZED, "请先登录");
        }
        inboxService.setDoNotDisturb(user.getId(), enabled);
        Map<String, Object> data = new HashMap<>();
        data.put("doNotDisturb", enabled);
        return Result.success(enabled ? "已开启免打扰" : "已关闭免打扰", data);
    }

    private void enrichReadFlags(Integer userId, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            if (!"inbox".equals(String.valueOf(row.get("source")))) {
                row.put("read", true);
                continue;
            }
            Integer productId = toProductId(row.get("productId"));
            if (productId == null) {
                row.put("read", true);
            } else {
                row.put("read", inboxService.isRead(userId, productId));
            }
        }
    }

    private static Integer toProductId(Object pid) {
        if (pid instanceof Number) {
            return ((Number) pid).intValue();
        }
        if (pid == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(pid));
        } catch (Exception e) {
            return null;
        }
    }

}
