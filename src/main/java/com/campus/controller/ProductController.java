package com.campus.controller;

import com.campus.annotation.Log;
import com.campus.entity.Product;
import com.campus.entity.User;
import com.campus.service.CategoryService;
import com.campus.service.ProductService;
import com.campus.service.RecommendService;
import com.campus.service.UserProfileService;
import com.campus.util.FileUploadUtil;
import com.campus.util.MinIOUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品控制器
 */
@Controller
@RequestMapping("/product")
public class ProductController {
    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    @Autowired
    private ProductService productService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private RecommendService recommendService;

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private MinIOUtil minIOUtil;
    
    @Value("${upload.path:D:/upload/}")
    private String uploadPath;

    /**
     * 商品列表页
     */
    @RequestMapping("/list")
    public String list(@RequestParam(defaultValue = "1") Integer pageNum,
                       @RequestParam(defaultValue = "12") Integer pageSize,
                       String keyword,
                       Integer categoryId,
                       Model model) {
        PageInfo<Product> pageInfo = productService.findList(keyword, categoryId, pageNum, pageSize);
        model.addAttribute("pageInfo", pageInfo);
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("keyword", keyword);
        model.addAttribute("categoryId", categoryId);
        
        // 热门商品
        model.addAttribute("hotProducts", productService.findHotProducts(6));
        
        return "product/list";
    }

    /**
     * 商品详情页
     * 技术亮点：记录浏览历史 + 相似商品推荐 + 用户画像实时更新
     * 优化：只查询一次数据库，increaseViewCount 内部通过 SQL 自增，无需重新查询
     * 
     * 成员A：用户浏览商品时触发画像增量更新
     * 成员B：后续可通过 UserProfileService 获取画像数据进行匹配
     */
    @RequestMapping("/detail")
    public String detail(Integer id, Model model, HttpSession session) {
        Product product = productService.findById(id);
        if (product != null) {
            productService.increaseViewCount(id); // 增加浏览量（SQL 层自增，无需重新查询）
            product.setViewCount(product.getViewCount() + 1); // 本地 +1，避免重复查询

            // 记录浏览历史（用于个性化推荐）
            User user = (User) session.getAttribute("user");
            if (user != null) {
                recommendService.recordBrowseHistory(user.getId(), id);

                // 成员A：更新用户兴趣画像（增量更新）
                // 注意：keywords 参数暂时传空列表，成员B实现分词后可以传入
                // 成员B在 ProductFeatureService 中实现分词后，在这里调用：
                // List<String> keywords = productFeatureService.extractKeywords(product.getName());
                // userProfileService.recordBrowse(user.getId(), product.getCategoryId(),
                //         product.getPrice().doubleValue(), keywords);
                userProfileService.recordBrowse(
                        user.getId(),
                        product.getCategoryId(),
                        product.getPrice() != null ? product.getPrice().doubleValue() : null,
                        new ArrayList<>()  // 成员B接入分词后替换为真实关键词
                );
            }

            // 获取相似商品推荐
            List<Product> similarProducts = recommendService.getSimilarProducts(id, 4);
            model.addAttribute("similarProducts", similarProducts);
        }
        model.addAttribute("product", product);
        return "product/detail";
    }

    /**
     * 跳转到发布商品页
     */
    @RequestMapping("/publish")
    public String publishPage(Model model) {
        model.addAttribute("categories", categoryService.findAll());
        return "product/publish";
    }

    /**
     * 发布商品
     */
    @Log("发布商品")
    @RequestMapping(value = "/publish", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Object> publish(Product product,
                                       @RequestParam("imageFile") MultipartFile imageFile,
                                       HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            User user = (User) session.getAttribute("user");
            product.setUserId(user.getId());

            // 上传图片到 MinIO（多实例共享）
            if (imageFile != null && !imageFile.isEmpty()) {
                String imageUrl = uploadImageWithFallback(imageFile);
                product.setImageUrl(imageUrl);
            }

            boolean success = productService.publish(product);
            result.put("success", success);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 我的商品管理
     */
    @RequestMapping("/manage")
    public String manage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        model.addAttribute("products", productService.findByUserId(user.getId()));
        return "product/manage";
    }

    /**
     * 更新商品
     */
    @Log("更新商品")
    @RequestMapping(value = "/update", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Object> update(Product product,
                                       @RequestParam(value = "imageFile", required = false) MultipartFile imageFile) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (imageFile != null && !imageFile.isEmpty()) {
                String imageUrl = uploadImageWithFallback(imageFile);
                product.setImageUrl(imageUrl);
            }
            boolean success = productService.update(product);
            result.put("success", success);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 先尝试 MinIO，失败则自动回退到本地文件上传。
     */
    private String uploadImageWithFallback(MultipartFile imageFile) {
        try {
            return minIOUtil.upload(imageFile);
        } catch (Exception minioEx) {
            logger.warn("MinIO 上传失败，回退本地上传: {}", minioEx.getMessage());
            try {
                return FileUploadUtil.upload(imageFile, uploadPath);
            } catch (Exception localEx) {
                throw new RuntimeException("图片上传失败，请检查存储配置", localEx);
            }
        }
    }

    /**
     * 删除商品
     */
    @Log("删除商品")
    @RequestMapping("/delete")
    @ResponseBody
    public Map<String, Object> delete(Integer id, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("success", false);
            result.put("message", "登录已失效，请重新登录");
            return result;
        }
        if (id == null) {
            result.put("success", false);
            result.put("message", "商品ID不能为空");
            return result;
        }

        Product product = productService.findById(id);
        if (product == null) {
            result.put("success", false);
            result.put("message", "商品不存在或已删除");
            return result;
        }
        if (product.getUserId() == null || !product.getUserId().equals(user.getId())) {
            result.put("success", false);
            result.put("message", "无权限删除该商品");
            return result;
        }

        try {
            boolean success = productService.delete(id);
            result.put("success", success);
            if (!success) {
                result.put("message", "删除失败，请刷新后重试");
            }
        } catch (Exception e) {
            String errorMsg = e.getMessage() == null ? "" : e.getMessage();
            if (errorMsg.contains("foreign key constraint fails") || errorMsg.contains("Cannot delete or update a parent row")) {
                result.put("message", "该商品已有订单记录，不能直接删除，请先下架保留记录");
            } else {
                result.put("message", "删除失败：" + errorMsg);
            }
            result.put("success", false);
        }
        return result;
    }

    /**
     * 下架/上架商品
     */
    @Log("更新商品状态")
    @RequestMapping("/updateStatus")
    @ResponseBody
    public Map<String, Object> updateStatus(Integer id, Integer status) {
        Map<String, Object> result = new HashMap<>();
        boolean success = productService.updateStatus(id, status);
        result.put("success", success);
        return result;
    }

    /**
     * 获取用户浏览历史
     * 技术亮点：基于内存缓存的浏览历史记录
     */
    @RequestMapping("/history")
    public String browseHistory(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user != null) {
            List<Product> history = recommendService.getBrowseHistory(user.getId(), 20);
            model.addAttribute("historyProducts", history);
        }
        return "product/history";
    }

    /**
     * 获取个性化推荐
     * 技术亮点：基于用户浏览历史的个性化推荐算法
     */
    @RequestMapping("/recommendations")
    @ResponseBody
    public Map<String, Object> getRecommendations(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user != null) {
            List<Product> recommendations = recommendService.getPersonalizedRecommendations(user.getId(), 8);
            result.put("success", true);
            result.put("data", recommendations);
        } else {
            // 未登录用户返回热门商品
            List<Product> hotProducts = productService.findHotProducts(8);
            result.put("success", true);
            result.put("data", hotProducts);
        }
        return result;
    }
}
