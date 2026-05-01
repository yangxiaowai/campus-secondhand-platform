package com.campus.controller;

import com.campus.annotation.Log;
import com.campus.entity.Product;
import com.campus.entity.User;
import com.campus.service.CategoryService;
import com.campus.service.ProductMatchService;
import com.campus.service.ProductService;
import com.campus.service.RecommendService;
import com.campus.util.FileUploadUtil;
import com.github.pagehelper.PageInfo;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品控制器
 */
@Controller
@RequestMapping("/product")
public class ProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private RecommendService recommendService;

    @Autowired
    private ProductMatchService productMatchService;

    @Value("${upload.path}")
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
     * 技术亮点：记录浏览历史 + 相似商品推荐
     */
    @RequestMapping("/detail")
    public String detail(Integer id, Model model, HttpSession session) {
        Product product = productService.findById(id);
        if (product != null) {
            productService.increaseViewCount(id); // 增加浏览量
            product = productService.findById(id); // 重新查询获取最新浏览量

            // 记录浏览历史（用于个性化推荐）
            User user = (User) session.getAttribute("user");
            if (user != null) {
                recommendService.recordBrowseHistory(user.getId(), id);
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

            // 上传图片
            if (imageFile != null && !imageFile.isEmpty()) {
                String imageUrl = FileUploadUtil.upload(imageFile, uploadPath);
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
                String imageUrl = FileUploadUtil.upload(imageFile, uploadPath);
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
     * 删除商品
     */
    @Log("删除商品")
    @RequestMapping("/delete")
    @ResponseBody
    public Map<String, Object> delete(Integer id) {
        Map<String, Object> result = new HashMap<>();
        boolean success = productService.delete(id);
        result.put("success", success);
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
            List<Product> recommendations = new java.util.ArrayList<>();
            for (com.campus.recommend.RecommendationItem item : productMatchService.getInboxRecommendations(user.getId(), 8)) {
                recommendations.add(item.getProduct());
            }
            if (recommendations.isEmpty()) {
                recommendations = recommendService.getPersonalizedRecommendations(user.getId(), 8);
            }
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
