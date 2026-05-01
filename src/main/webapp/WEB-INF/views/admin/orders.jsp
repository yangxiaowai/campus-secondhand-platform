<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>订单管理</title>
    <link href="https://cdn.bootcdn.net/ajax/libs/bootstrap/4.6.2/css/bootstrap.min.css" rel="stylesheet">
</head>
<body>
    <nav class="navbar navbar-expand-lg navbar-dark bg-dark">
        <div class="container">
            <a class="navbar-brand" href="${ctx}/admin/index">管理后台</a>
            <div class="ml-auto">
                <a href="${ctx}/admin/index" class="btn btn-sm btn-outline-light">返回</a>
            </div>
        </div>
    </nav>

    <div class="container mt-4">
        <h2>订单管理</h2>
        <table class="table table-bordered">
            <thead>
                <tr>
                    <th>订单号</th>
                    <th>商品</th>
                    <th>买家</th>
                    <th>价格</th>
                    <th>状态</th>
                    <th>下单时间</th>
                </tr>
            </thead>
            <tbody>
                <c:forEach items="${orders}" var="order">
                    <tr>
                        <td>${order.orderNo}</td>
                        <td>
                            <img src="${order.product.imageUrl}" style="width: 50px; height: 50px; object-fit: cover;"
                                 onerror="this.src='https://via.placeholder.com/50'">
                            ${order.product.name}
                        </td>
                        <td>${order.buyer.nickname}</td>
                        <td>¥${order.totalPrice}</td>
                        <td>
                            <c:if test="${order.status == 0}">待处理</c:if>
                            <c:if test="${order.status == 1}">已完成</c:if>
                            <c:if test="${order.status == 2}">已取消</c:if>
                        </td>
                        <td><fmt:formatDate value="${order.createTime}" pattern="yyyy-MM-dd HH:mm"/></td>
                    </tr>
                </c:forEach>
            </tbody>
        </table>
    </div>

    <script src="https://cdn.bootcdn.net/ajax/libs/jquery/3.6.0/jquery.min.js"></script>
    <script src="https://cdn.bootcdn.net/ajax/libs/bootstrap/4.6.2/js/bootstrap.bundle.min.js"></script>
</body>
</html>



