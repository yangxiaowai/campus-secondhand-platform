package com.campus.controller;

import com.campus.annotation.Log;
import com.campus.entity.Order;
import com.campus.entity.Product;
import com.campus.entity.User;
import com.campus.util.SessionUserHelper;
import com.campus.service.OrderService;
import com.campus.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

/**
 * 订单控制器
 */
@Controller
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductService productService;

    /**
     * 创建订单（立即购买）
     * 技术亮点：使用 @Transactional 保证订单创建和商品状态更新的原子性
     */
    @Log("创建订单")
    @RequestMapping(value = "/create", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Object> create(Integer productId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            User user = SessionUserHelper.getLoginUser(session);
            Product product = productService.findById(productId);

            if (product == null) {
                result.put("success", false);
                result.put("message", "商品不存在");
                return result;
            }

            if (product.getStatus() != 0) {
                result.put("success", false);
                result.put("message", "商品已售出或已下架");
                return result;
            }

            if (product.getUserId().equals(user.getId())) {
                result.put("success", false);
                result.put("message", "不能购买自己发布的商品");
                return result;
            }

            Order order = new Order();
            order.setUserId(user.getId());
            order.setProductId(productId);
            order.setTotalPrice(product.getPrice());

            boolean success = orderService.createOrder(order);
            result.put("success", success);
            if (!success) {
                result.put("message", "创建订单失败");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 我的订单（买家）
     */
    @RequestMapping("/myOrders")
    public String myOrders(HttpSession session, Model model) {
        User user = SessionUserHelper.getLoginUser(session);
        model.addAttribute("orders", orderService.findByUserId(user.getId()));
        return "order/myOrders";
    }

    /**
     * 我的销售（卖家）
     */
    @RequestMapping("/mySales")
    public String mySales(HttpSession session, Model model) {
        User user = SessionUserHelper.getLoginUser(session);
        model.addAttribute("orders", orderService.findBySellerId(user.getId()));
        return "order/mySales";
    }

    /**
     * 卖家确认完成订单
     */
    @Log("确认完成订单")
    @RequestMapping(value = "/confirm", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Object> confirmOrder(Integer orderId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            User user = SessionUserHelper.getLoginUser(session);
            boolean success = orderService.confirmOrder(orderId, user.getId());
            result.put("success", success);
            if (success) {
                result.put("message", "订单已确认完成");
            } else {
                result.put("message", "操作失败");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 卖家取消订单
     */
    @Log("取消订单")
    @RequestMapping(value = "/cancel", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Object> cancelOrder(Integer orderId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            User user = SessionUserHelper.getLoginUser(session);
            boolean success = orderService.cancelOrder(orderId, user.getId());
            result.put("success", success);
            if (success) {
                result.put("message", "订单已取消，商品已恢复上架");
            } else {
                result.put("message", "操作失败");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }
}



