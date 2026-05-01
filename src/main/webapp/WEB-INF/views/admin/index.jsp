<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>管理后台</title>
    <link href="https://cdn.bootcdn.net/ajax/libs/bootstrap/4.6.2/css/bootstrap.min.css" rel="stylesheet">
</head>
<body>
    <nav class="navbar navbar-expand-lg navbar-dark bg-dark">
        <div class="container">
            <a class="navbar-brand" href="${ctx}/admin/index">管理后台</a>
            <div class="ml-auto">
                <a href="${ctx}/" class="btn btn-sm btn-outline-light">返回首页</a>
            </div>
        </div>
    </nav>

    <div class="container mt-4">
        <h2>管理后台</h2>
        <div class="row mt-4">
            <div class="col-md-4">
                <div class="card">
                    <div class="card-body">
                        <h5 class="card-title">注册用户数</h5>
                        <h2>${userCount}</h2>
                    </div>
                </div>
            </div>
            <div class="col-md-4">
                <div class="card">
                    <div class="card-body">
                        <h5 class="card-title">总交易额</h5>
                        <h2>¥${totalAmount}</h2>
                    </div>
                </div>
            </div>
            <div class="col-md-4">
                <div class="card">
                    <div class="card-body">
                        <h5 class="card-title">订单总数</h5>
                        <h2>${orderCount}</h2>
                    </div>
                </div>
            </div>
        </div>

        <div class="mt-4">
            <a href="${ctx}/admin/users" class="btn btn-primary mr-2">用户管理</a>
            <a href="${ctx}/admin/products" class="btn btn-primary mr-2">商品管理</a>
            <a href="${ctx}/admin/orders" class="btn btn-primary">订单管理</a>
        </div>
    </div>

    <script src="https://cdn.bootcdn.net/ajax/libs/jquery/3.6.0/jquery.min.js"></script>
    <script src="https://cdn.bootcdn.net/ajax/libs/bootstrap/4.6.2/js/bootstrap.bundle.min.js"></script>
</body>
</html>



