<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>浏览历史 - 校园二手交易平台</title>
    <link href="https://cdn.bootcdn.net/ajax/libs/bootstrap/4.6.2/css/bootstrap.min.css" rel="stylesheet">
    <link href="${ctx}/static/css/main.css" rel="stylesheet">
</head>
<body>
    <nav class="navbar navbar-expand-lg navbar-light bg-white">
        <div class="container">
            <a class="navbar-brand brand-strong" href="${ctx}/">校园二手交易平台</a>
            <div class="ml-auto">
                <a href="${ctx}/product/list" class="btn btn-sm btn-outline-primary">返回商品列表</a>
            </div>
        </div>
    </nav>

    <div class="container mt-4">
        <h4 class="section-title">
            📜 我的浏览历史
            <small class="text-muted font-weight-normal ml-2">最近浏览的商品</small>
        </h4>

        <c:choose>
            <c:when test="${not empty historyProducts}">
                <div class="row">
                    <c:forEach items="${historyProducts}" var="product">
                        <div class="col-md-3 mb-3">
                            <div class="card card-ui h-100">
                                <a href="${ctx}/product/detail?id=${product.id}">
                                    <img src="${ctx}${product.imageUrl}" class="card-img-top product-image" 
                                         alt="${product.name}" onerror="this.onerror=null;this.src='${ctx}/static/img/placeholder.svg';">
                                </a>
                                <div class="card-body">
                                    <c:if test="${product.category != null}">
                                        <span class="tag-pill mb-2">${product.category.categoryName}</span>
                                    </c:if>
                                    <h6 class="card-title product-title">
                                        <a href="${ctx}/product/detail?id=${product.id}" class="text-dark text-decoration-none">
                                            ${product.name}
                                        </a>
                                    </h6>
                                    <div class="d-flex justify-content-between align-items-center">
                                        <span class="price">¥${product.price}</span>
                                        <c:choose>
                                            <c:when test="${product.status == 0}">
                                                <span class="badge badge-success">在售</span>
                                            </c:when>
                                            <c:when test="${product.status == 1}">
                                                <span class="badge badge-secondary">已售出</span>
                                            </c:when>
                                            <c:otherwise>
                                                <span class="badge badge-warning">已下架</span>
                                            </c:otherwise>
                                        </c:choose>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </c:forEach>
                </div>
            </c:when>
            <c:otherwise>
                <div class="alert alert-info text-center">
                    <p class="mb-2">暂无浏览历史</p>
                    <a href="${ctx}/product/list" class="btn btn-primary">去逛逛</a>
                </div>
            </c:otherwise>
        </c:choose>
    </div>

    <div class="footer">
        <div class="container d-flex justify-content-between">
            <span>校园二手交易平台</span>
            <span class="text-muted">让闲置流动起来</span>
        </div>
    </div>

    <script src="https://cdn.bootcdn.net/ajax/libs/jquery/3.6.0/jquery.min.js"></script>
    <script src="https://cdn.bootcdn.net/ajax/libs/bootstrap/4.6.2/js/bootstrap.bundle.min.js"></script>
</body>
</html>

