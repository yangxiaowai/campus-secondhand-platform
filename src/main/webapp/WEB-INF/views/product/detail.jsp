<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>${product.name} - 商品详情</title>
    <link href="https://cdn.bootcdn.net/ajax/libs/bootstrap/4.6.2/css/bootstrap.min.css" rel="stylesheet">
    <link href="${ctx}/static/css/main.css" rel="stylesheet">
    <style>
        .status-badge {
            position: absolute;
            top: 10px;
            right: 10px;
            z-index: 10;
        }
        .product-main-img {
            border-radius: 12px;
            max-height: 450px;
            object-fit: contain;
            background: #f8f9fa;
        }
        .seller-card {
            background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
            border-radius: 12px;
            padding: 20px;
        }
        .similar-card {
            transition: transform 0.2s, box-shadow 0.2s;
        }
        .similar-card:hover {
            transform: translateY(-4px);
            box-shadow: 0 8px 24px rgba(0,0,0,0.1);
        }
        .similar-img {
            height: 150px;
            object-fit: cover;
            border-radius: 8px 8px 0 0;
        }
    </style>
</head>
<body>
    <nav class="navbar navbar-expand-lg navbar-light bg-white">
        <div class="container">
            <a class="navbar-brand brand-strong" href="${ctx}/">校园二手交易平台</a>
            <div class="ml-auto">
                <c:choose>
                    <c:when test="${sessionScope.user != null}">
                        <span class="mr-3 text-muted">欢迎，${sessionScope.user.nickname}</span>
                        <a href="${ctx}/user/center" class="btn btn-sm btn-outline-primary mr-2">个人中心</a>
                        <a href="${ctx}/product/publish" class="btn btn-sm btn-success mr-2">发布商品</a>
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
        <c:if test="${product != null}">
            <!-- 面包屑导航 -->
            <nav aria-label="breadcrumb" class="mb-3">
                <ol class="breadcrumb bg-transparent p-0">
                    <li class="breadcrumb-item"><a href="${ctx}/">首页</a></li>
                    <li class="breadcrumb-item"><a href="${ctx}/product/list">商品列表</a></li>
                    <c:if test="${product.category != null}">
                        <li class="breadcrumb-item">
                            <a href="${ctx}/product/list?categoryId=${product.categoryId}">${product.category.categoryName}</a>
                        </li>
                    </c:if>
                    <li class="breadcrumb-item active">${product.name}</li>
                </ol>
            </nav>

            <div class="row">
                <!-- 商品图片 -->
                <div class="col-md-6 mb-4">
                    <div class="card card-ui position-relative">
                        <c:if test="${product.status == 1}">
                            <span class="status-badge badge badge-dark px-3 py-2">已售出</span>
                        </c:if>
                        <c:if test="${product.status == 2}">
                            <span class="status-badge badge badge-warning px-3 py-2">已下架</span>
                        </c:if>
                        <img src="${ctx}${product.imageUrl}" class="card-img-top product-main-img p-3"
                             alt="${product.name}" onerror="this.onerror=null;this.src='${ctx}/static/img/placeholder.svg';">
                    </div>
                </div>

                <!-- 商品信息 -->
                <div class="col-md-6 mb-4">
                    <div class="card card-ui h-100">
                        <div class="card-body d-flex flex-column">
                            <div class="d-flex justify-content-between align-items-start mb-2">
                                <h4 class="mb-0 flex-grow-1">${product.name}</h4>
                                <c:if test="${product.category != null}">
                                    <span class="tag-pill ml-2">${product.category.categoryName}</span>
                                </c:if>
                            </div>
                            
                            <div class="mb-3">
                                <span class="price" style="font-size: 28px;">¥${product.price}</span>
                                <span class="badge-status badge-live ml-2">浏览 ${product.viewCount}</span>
                            </div>
                            
                            <p class="text-muted mb-3">
                                <small>发布于：<fmt:formatDate value="${product.createTime}" pattern="yyyy-MM-dd HH:mm"/></small>
                            </p>
                            
                            <hr>
                            
                            <h6 class="font-weight-bold">商品描述</h6>
                            <p class="text-secondary">${product.description}</p>
                            
                            <hr>
                            
                            <!-- 卖家信息 -->
                            <div class="seller-card mb-3">
                                <h6 class="font-weight-bold mb-3">卖家信息</h6>
                                <div class="d-flex align-items-center">
                                    <div class="user-avatar mr-3" style="width: 50px; height: 50px; font-size: 20px;">
                                        ${product.seller.nickname.substring(0,1)}
                                    </div>
                                    <div>
                                        <p class="mb-1 font-weight-bold">${product.seller.nickname}</p>
                                        <c:if test="${product.seller.phone != null}">
                                            <small class="text-muted d-block">手机：${product.seller.phone}</small>
                                        </c:if>
                                        <c:if test="${product.seller.email != null && !empty product.seller.email}">
                                            <small class="text-muted d-block">邮箱：${product.seller.email}</small>
                                        </c:if>
                                    </div>
                                </div>
                            </div>
                            
                            <!-- 购买按钮 -->
                            <div class="mt-auto">
                                <c:if test="${product.status == 0}">
                                    <c:choose>
                                        <c:when test="${sessionScope.user != null && sessionScope.user.id != product.userId}">
                                            <button class="btn btn-danger btn-lg btn-block" onclick="buyProduct(${product.id})">
                                                🛒 立即购买
                                            </button>
                                        </c:when>
                                        <c:when test="${sessionScope.user != null && sessionScope.user.id == product.userId}">
                                            <a href="${ctx}/product/manage" class="btn btn-outline-primary btn-lg btn-block">
                                                这是您发布的商品
                                            </a>
                                        </c:when>
                                        <c:otherwise>
                                            <a href="${ctx}/user/loginPage" class="btn btn-danger btn-lg btn-block">
                                                登录后购买
                                            </a>
                                        </c:otherwise>
                                    </c:choose>
                                </c:if>
                                <c:if test="${product.status == 1}">
                                    <button class="btn btn-secondary btn-lg btn-block" disabled>商品已售出</button>
                                </c:if>
                                <c:if test="${product.status == 2}">
                                    <button class="btn btn-secondary btn-lg btn-block" disabled>商品已下架</button>
                                </c:if>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <!-- 相似商品推荐 -->
            <c:if test="${not empty similarProducts}">
                <div class="mt-5">
                    <h5 class="section-title">
                        💡 相似商品推荐
                        <small class="text-muted font-weight-normal ml-2">基于分类的智能推荐</small>
                    </h5>
                    <div class="row">
                        <c:forEach items="${similarProducts}" var="sp">
                            <div class="col-md-3 mb-3">
                                <div class="card similar-card h-100">
                                    <a href="${ctx}/product/detail?id=${sp.id}">
                                        <img src="${ctx}${sp.imageUrl}" class="card-img-top similar-img" 
                                             alt="${sp.name}" onerror="this.onerror=null;this.src='${ctx}/static/img/placeholder.svg';">
                                    </a>
                                    <div class="card-body p-3">
                                        <h6 class="card-title product-title mb-2">
                                            <a href="${ctx}/product/detail?id=${sp.id}" class="text-dark text-decoration-none">
                                                ${sp.name}
                                            </a>
                                        </h6>
                                        <div class="d-flex justify-content-between align-items-center">
                                            <span class="price">¥${sp.price}</span>
                                            <small class="text-muted">浏览 ${sp.viewCount}</small>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </c:forEach>
                    </div>
                </div>
            </c:if>
        </c:if>

        <c:if test="${product == null}">
            <div class="alert alert-warning text-center">
                <h4>商品不存在或已被删除</h4>
                <a href="${ctx}/product/list" class="btn btn-primary mt-3">返回商品列表</a>
            </div>
        </c:if>
    </div>

    <div class="footer">
        <div class="container d-flex justify-content-between">
            <span>校园二手交易平台</span>
            <span class="text-muted">让闲置流动起来 · 让好物继续发光</span>
        </div>
    </div>

    <script src="https://cdn.bootcdn.net/ajax/libs/jquery/3.6.0/jquery.min.js"></script>
    <script src="https://cdn.bootcdn.net/ajax/libs/bootstrap/4.6.2/js/bootstrap.bundle.min.js"></script>
    <script>
        function buyProduct(productId) {
            if (confirm('确定要购买此商品吗？\n\n点击确定后将生成订单，请与卖家联系完成交易。')) {
                $.ajax({
                    url: '${ctx}/order/create',
                    type: 'POST',
                    data: {productId: productId},
                    success: function(result) {
                        if (result.success) {
                            alert('🎉 订单创建成功！\n\n请尽快与卖家联系完成交易。');
                            window.location.href = '${ctx}/order/myOrders';
                        } else {
                            alert('❌ ' + (result.message || '购买失败'));
                        }
                    },
                    error: function() {
                        alert('网络异常，请稍后重试');
                    }
                });
            }
        }
    </script>
</body>
</html>
