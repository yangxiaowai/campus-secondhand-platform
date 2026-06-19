<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>我的订单 - 校园二手交易平台</title>
    <link href="https://cdn.bootcdn.net/ajax/libs/bootstrap/4.6.2/css/bootstrap.min.css" rel="stylesheet">
    <link href="${ctx}/static/css/main.css" rel="stylesheet">
    <style>
        .order-card {
            border: 1px solid #e5e7eb;
            border-radius: 12px;
            margin-bottom: 16px;
            overflow: hidden;
        }
        .order-header {
            background: #f9fafb;
            padding: 12px 16px;
            border-bottom: 1px solid #e5e7eb;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        .order-body {
            padding: 16px;
            display: flex;
            align-items: center;
        }
        .product-img {
            width: 80px;
            height: 80px;
            object-fit: cover;
            border-radius: 8px;
            margin-right: 16px;
        }
        .product-info {
            flex: 1;
        }
        .status-pending {
            color: #f59e0b;
            font-weight: bold;
        }
        .status-completed {
            color: #10b981;
            font-weight: bold;
        }
        .status-cancelled {
            color: #6b7280;
        }
    </style>
</head>
<body>
    <nav class="navbar navbar-expand-lg navbar-light bg-white">
        <div class="container">
            <a class="navbar-brand brand-strong" href="${ctx}/">校园二手交易平台</a>
            <div class="ml-auto">
                <a href="${ctx}/user/center#orders" class="btn btn-sm btn-outline-primary">返回个人中心</a>
            </div>
        </div>
    </nav>

    <div class="container mt-4">
        <h4 class="mb-4">📦 我的订单</h4>
        
        <c:if test="${empty orders}">
            <div class="text-center py-5">
                <p class="text-muted">暂无订单记录</p>
                <a href="${ctx}/product/list" class="btn btn-primary">去逛逛</a>
            </div>
        </c:if>

        <c:forEach items="${orders}" var="order">
            <div class="order-card">
                <div class="order-header">
                    <div>
                        <span class="text-muted">订单号：</span>
                        <span>${order.orderNo}</span>
                    </div>
                    <div>
                        <c:if test="${order.status == 0}">
                            <span class="status-pending">⏳ 待处理</span>
                        </c:if>
                        <c:if test="${order.status == 1}">
                            <span class="status-completed">✅ 已完成</span>
                        </c:if>
                        <c:if test="${order.status == 2}">
                            <span class="status-cancelled">❌ 已取消</span>
                        </c:if>
                    </div>
                </div>
                <div class="order-body">
                    <a href="${ctx}/product/detail?id=${order.productId}">
                        <img src="${ctx}${order.product.imageUrl}" class="product-img" alt="${order.product.name}"
                             onerror="this.onerror=null;this.src='${ctx}/static/img/placeholder.svg';">
                    </a>
                    <div class="product-info">
                        <h6 class="mb-1">
                            <a href="${ctx}/product/detail?id=${order.productId}" class="text-dark">
                                ${order.product.name}
                            </a>
                        </h6>
                        <p class="mb-0">
                            <span class="text-danger font-weight-bold">¥${order.totalPrice}</span>
                            <span class="text-muted ml-3">
                                <fmt:formatDate value="${order.createTime}" pattern="yyyy-MM-dd HH:mm"/>
                            </span>
                        </p>
                    </div>
                    <div>
                        <c:if test="${order.status == 0}">
                            <small class="text-muted">等待卖家确认</small>
                        </c:if>
                        <c:if test="${order.status == 1}">
                            <small class="text-success">交易成功</small>
                        </c:if>
                        <c:if test="${order.status == 2}">
                            <small class="text-muted">卖家已取消</small>
                        </c:if>
                    </div>
                </div>
            </div>
        </c:forEach>
    </div>

    <div class="footer">
        <div class="container text-center">
            <span>校园二手交易平台</span>
        </div>
    </div>

    <script src="https://cdn.bootcdn.net/ajax/libs/jquery/3.6.0/jquery.min.js"></script>
    <script src="https://cdn.bootcdn.net/ajax/libs/bootstrap/4.6.2/js/bootstrap.bundle.min.js"></script>
</body>
</html>
