<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>校园二手交易平台</title>
    <link href="https://cdn.bootcdn.net/ajax/libs/bootstrap/4.6.2/css/bootstrap.min.css" rel="stylesheet">
    <link href="${ctx}/static/css/main.css" rel="stylesheet">
</head>
<body>
    <nav class="navbar navbar-expand-lg navbar-light bg-white">
        <div class="container">
            <a class="navbar-brand brand-strong" href="${ctx}/">校园二手交易平台</a>
            <div class="ml-auto">
                <c:choose>
                    <c:when test="${sessionScope.user != null}">
                        <span class="mr-3 text-muted">欢迎，${sessionScope.user.nickname}</span>
                        <a href="${ctx}/user/inbox/page" class="btn btn-sm btn-outline-info mr-2 position-relative">
                            推荐收件箱
                            <span data-inbox-badge class="badge badge-danger inbox-nav-badge">0</span>
                        </a>
                        <a href="${ctx}/user/center" class="btn btn-sm btn-outline-primary mr-2">个人中心</a>
                        <a href="${ctx}/product/publish" class="btn btn-sm btn-success mr-2">发布商品</a>
                        <c:if test="${sessionScope.user.role == 2}">
                            <a href="${ctx}/admin/index" class="btn btn-sm btn-warning mr-2">管理后台</a>
                        </c:if>
                        <a href="${ctx}/user/logout" class="btn btn-sm btn-outline-secondary">退出</a>
                    </c:when>
                    <c:otherwise>
                        <a href="${ctx}/user/loginPage" class="btn btn-sm btn-primary mr-2">登录</a>
                        <a href="${ctx}/user/registerPage" class="btn btn-sm btn-outline-primary">注册</a>
                    </c:otherwise>
                </c:choose>
            </div>
        </div>
    </nav>

    <div class="container mt-4">
        <div class="hero mb-4">
            <div class="d-flex flex-column flex-md-row align-items-md-center justify-content-between">
                <div>
                    <h2 class="mb-2">校园二手 · 省心买卖</h2>
                    <p class="mb-3">精选热门好物，低价入手；也可一键发布闲置，快速成交。</p>
                    <div class="hero-cta">
                        <a href="${ctx}/product/list" class="btn btn-light btn-sm mr-2">去逛逛</a>
                        <c:choose>
                            <c:when test="${sessionScope.user != null}">
                                <a href="${ctx}/product/publish" class="btn btn-sm btn-warning text-white">发布闲置</a>
                            </c:when>
                            <c:otherwise>
                                <a href="${ctx}/user/loginPage" class="btn btn-sm btn-warning text-white">登录后发布</a>
                            </c:otherwise>
                        </c:choose>
                    </div>
                </div>
                <div class="mt-3 mt-md-0 text-md-right">
                    <div class="tag-pill">安全交易</div>
                    <div class="tag-pill">低价好物</div>
                    <div class="tag-pill">快速发布</div>
                </div>
            </div>
        </div>

        <div class="row mt-3">
            <!-- 左侧分类导航 -->
            <div class="col-md-2 mb-3">
                <div class="category-nav">
                    <div class="px-3 pb-2 font-weight-bold text-muted">商品分类</div>
                    <a href="${ctx}/product/list" class="text-decoration-none">
                        <div class="category-item">
                            <span>全部商品</span>
                        </div>
                    </a>
                    <c:forEach items="${categories}" var="category">
                        <a href="${ctx}/product/list?categoryId=${category.id}" class="text-decoration-none">
                            <div class="category-item">
                                <span>${category.categoryName}</span>
                                <span class="text-muted">&gt;</span>
                            </div>
                        </a>
                    </c:forEach>
                </div>
            </div>

            <!-- 中间热门商品 -->
            <div class="col-md-7 mb-3">
                <h5 class="section-title">热门商品推荐</h5>
                <div class="row">
                    <c:forEach items="${hotProducts}" var="product">
                        <div class="col-md-4 mb-3">
                            <div class="card card-ui product-card-grid h-100">
                                <div class="badge-corner">
                                    <span class="badge-status badge-live">热度 ${product.viewCount}</span>
                                </div>
                                <a href="${ctx}/product/detail?id=${product.id}">
                                    <img src="${ctx}${product.imageUrl}" class="card-img-top product-image"
                                         alt="${product.name}" onerror="this.onerror=null;this.src='${ctx}/static/img/placeholder.svg';">
                                </a>
                                <div class="card-body">
                                    <c:if test="${product.category != null}">
                                        <div class="mb-1">
                                            <span class="tag-pill">${product.category.categoryName}</span>
                                        </div>
                                    </c:if>
                                    <div class="product-title mb-2">
                                        <a href="${ctx}/product/detail?id=${product.id}" class="text-dark text-decoration-none">
                                            ${product.name}
                                        </a>
                                    </div>
                                    <div class="d-flex justify-content-between align-items-center">
                                        <span class="price">¥${product.price}</span>
                                        <small class="text-muted">浏览 ${product.viewCount}</small>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </c:forEach>
                </div>
                <div class="text-center mt-2">
                    <a href="${ctx}/product/list" class="btn btn-primary btn-sm">查看更多商品</a>
                </div>
            </div>

            <!-- 右侧用户信息 -->
            <div class="col-md-3 mb-3">
                <div class="user-card mb-3">
                    <div class="d-flex align-items-center mb-3">
                        <div class="user-avatar mr-2">
                            <c:choose>
                                <c:when test="${sessionScope.user != null}">
                                    ${fn:substring(sessionScope.user.nickname,0,1)}
                                </c:when>
                                <c:otherwise>访</c:otherwise>
                            </c:choose>
                        </div>
                        <div>
                            <c:choose>
                                <c:when test="${sessionScope.user != null}">
                                    <div class="font-weight-bold">${sessionScope.user.nickname}</div>
                                    <small class="text-muted">欢迎回来～</small>
                                </c:when>
                                <c:otherwise>
                                    <div class="font-weight-bold">你好，访客</div>
                                    <small class="text-muted">登录后可发布闲置、下单购买</small>
                                </c:otherwise>
                            </c:choose>
                        </div>
                    </div>
                    <c:choose>
                        <c:when test="${sessionScope.user != null}">
                            <a href="${ctx}/product/publish" class="btn btn-sm btn-success btn-block mb-2">发布闲置</a>
                            <a href="${ctx}/order/myOrders" class="btn btn-sm btn-outline-primary btn-block mb-2">我的订单</a>
                            <a href="${ctx}/order/mySales" class="btn btn-sm btn-outline-primary btn-block">我的销售</a>
                        </c:when>
                        <c:otherwise>
                            <a href="${ctx}/user/loginPage" class="btn btn-sm btn-primary btn-block mb-2">立即登录</a>
                            <a href="${ctx}/user/registerPage" class="btn btn-sm btn-outline-primary btn-block">快速注册</a>
                        </c:otherwise>
                    </c:choose>
                </div>
            </div>
        </div>
    </div>

    <div class="footer">
        <div class="container d-flex justify-content-between">
            <span>校园二手交易平台</span>
            <span class="text-muted">让闲置流动起来 · 让好物继续发光</span>
        </div>
    </div>

    <script src="https://cdn.bootcdn.net/ajax/libs/jquery/3.6.0/jquery.min.js"></script>
    <script src="https://cdn.bootcdn.net/ajax/libs/bootstrap/4.6.2/js/bootstrap.bundle.min.js"></script>
    <c:if test="${sessionScope.user != null}">
        <script src="${ctx}/static/js/inbox-notify.js"></script>
        <script>InboxNotify.start('${ctx}');</script>
    </c:if>
</body>
</html>



