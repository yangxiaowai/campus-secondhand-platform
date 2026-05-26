<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>商品列表 - 校园二手交易平台</title>
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
        <c:if test="${not empty errorMsg}">
            <div class="alert alert-warning alert-dismissible fade show" role="alert">
                ${errorMsg}
                <button type="button" class="close" data-dismiss="alert" aria-label="Close">
                    <span aria-hidden="true">&times;</span>
                </button>
            </div>
        </c:if>
        <div class="filter-card mb-4">
            <form action="${ctx}/product/list" method="get">
                <div class="form-row align-items-center">
                    <div class="col-md-4 mb-2">
                        <input type="text" name="keyword" class="form-control" placeholder="搜索商品、描述..."
                               value="${keyword}">
                    </div>
                    <div class="col-md-2 mb-2">
                        <select name="categoryId" class="form-control">
                            <option value="0">全部分类</option>
                            <c:forEach items="${categories}" var="category">
                                <option value="${category.id}" ${categoryId == category.id ? 'selected' : ''}>
                                    ${category.categoryName}
                                </option>
                            </c:forEach>
                        </select>
                    </div>
                    <div class="col-md-2 mb-2">
                        <select name="searchMode" class="form-control" title="搜索模式">
                            <option value="HYBRID" ${searchMode == 'HYBRID' ? 'selected' : ''}>混合搜索</option>
                            <option value="KEYWORD" ${searchMode == 'KEYWORD' ? 'selected' : ''}>关键词</option>
                            <option value="SEMANTIC" ${searchMode == 'SEMANTIC' ? 'selected' : ''}>语义搜索</option>
                        </select>
                    </div>
                    <div class="col-md-2 mb-2">
                        <button type="submit" class="btn btn-primary btn-block">搜索</button>
                    </div>
                </div>
                <div class="form-row align-items-center mt-2 pt-2 border-top">
                    <div class="col-md-2 mb-2">
                        <input type="number" step="0.01" min="0" name="minPrice" class="form-control form-control-sm"
                               placeholder="偏好最低价 ¥" value="${minPrice}" title="用于推荐排序，不隐藏其它商品">
                    </div>
                    <div class="col-md-2 mb-2">
                        <input type="number" step="0.01" min="0" name="maxPrice" class="form-control form-control-sm"
                               placeholder="偏好最高价 ¥" value="${maxPrice}" title="用于推荐排序，不隐藏其它商品">
                    </div>
                    <div class="col-md-2 mb-2">
                        <select name="maxPublishDays" class="form-control form-control-sm" title="时效偏好：最新按发布日期排序；近N天为加权优先">
                            <option value="">时效不限</option>
                            <option value="-1" ${maxPublishDays == -1 ? 'selected' : ''}>最新（按日期）</option>
                            <option value="3" ${maxPublishDays == 3 ? 'selected' : ''}>优先近 3 天</option>
                            <option value="7" ${maxPublishDays == 7 ? 'selected' : ''}>优先近 7 天</option>
                            <option value="30" ${maxPublishDays == 30 ? 'selected' : ''}>优先近 30 天</option>
                        </select>
                    </div>
                    <div class="col-md-2 mb-2">
                        <select name="sortBy" class="form-control form-control-sm" title="推荐排序">
                            <option value="BEST_FIT" ${sortBy == 'BEST_FIT' ? 'selected' : ''}>综合推荐</option>
                            <option value="NEWEST" ${sortBy == 'NEWEST' ? 'selected' : ''}>最新发布</option>
                            <option value="PRICE_ASC" ${sortBy == 'PRICE_ASC' ? 'selected' : ''}>价格从低到高</option>
                            <option value="PRICE_DESC" ${sortBy == 'PRICE_DESC' ? 'selected' : ''}>价格从高到低</option>
                        </select>
                    </div>
                    <div class="col-md-2 mb-2">
                        <a href="${ctx}/product/list" class="btn btn-outline-secondary btn-block btn-sm">重置筛选</a>
                    </div>
                    <div class="col-md-4 mb-2 text-muted small">
                        分类与关键词为筛选；价格、时效与画像仅影响全局推荐排序，不隐藏商品。
                    </div>
                </div>
            </form>
            <c:if test="${not empty keyword}">
                <div class="mt-2 small text-muted">共找到 ${pageInfo.total} 件相关商品</div>
            </c:if>
        </div>

        <c:if test="${empty pageInfo.list}">
            <div class="alert alert-light text-center">暂无商品，试试调整分类或搜索关键词。</div>
        </c:if>
        <div class="row">
            <c:forEach items="${pageInfo.list}" var="product">
                <div class="col-md-3 mb-3">
                    <div class="card card-ui product-card-grid h-100">
                        <div class="badge-corner">
                            <span class="badge-status badge-live">浏览 ${product.viewCount}</span>
                        </div>
                        <a href="${ctx}/product/detail?id=${product.id}">
                            <img src="${ctx}${product.imageUrl}" class="card-img-top product-image"
                                 alt="${product.name}" onerror="this.onerror=null;this.src='${ctx}/static/img/placeholder.svg';">
                        </a>
                        <div class="card-body">
                            <div class="mb-2">
                                <span class="tag-pill">${product.category != null ? product.category.categoryName : '未分类'}</span>
                            </div>
                            <div class="product-title mb-2">
                                <a href="${ctx}/product/detail?id=${product.id}" class="text-dark text-decoration-none">
                                    ${product.name}
                                </a>
                            </div>
                            <div class="d-flex justify-content-between align-items-center">
                                <span class="price">¥${product.price}</span>
                                <c:if test="${sessionScope.user != null && (sortBy == 'BEST_FIT' || maxPublishDays == -1)}">
                                    <c:set var="fitScore" value="${rankScores[product.id]}" />
                                    <c:if test="${not empty fitScore}">
                                        <span class="badge badge-primary" title="综合推荐分">推荐 ${fitScore}</span>
                                    </c:if>
                                </c:if>
                            </div>
                            <small class="text-muted d-block mt-1">发布 ${product.createTime}</small>
                        </div>
                    </div>
                </div>
            </c:forEach>
        </div>

        <!-- 分页 -->
        <nav aria-label="Page navigation">
            <ul class="pagination justify-content-center">
                <c:if test="${pageInfo.hasPreviousPage}">
                    <li class="page-item">
                        <a class="page-link" href="${ctx}/product/list?pageNum=${pageInfo.prePage}&keyword=${keyword}&categoryId=${categoryId}&searchMode=${searchMode}&minPrice=${minPrice}&maxPrice=${maxPrice}&maxPublishDays=${maxPublishDays}&sortBy=${sortBy}">上一页</a>
                    </li>
                </c:if>
                <c:forEach items="${pageInfo.navigatepageNums}" var="num">
                    <li class="page-item ${num == pageInfo.pageNum ? 'active' : ''}">
                        <a class="page-link" href="${ctx}/product/list?pageNum=${num}&keyword=${keyword}&categoryId=${categoryId}&searchMode=${searchMode}&minPrice=${minPrice}&maxPrice=${maxPrice}&maxPublishDays=${maxPublishDays}&sortBy=${sortBy}">${num}</a>
                    </li>
                </c:forEach>
                <c:if test="${pageInfo.hasNextPage}">
                    <li class="page-item">
                        <a class="page-link" href="${ctx}/product/list?pageNum=${pageInfo.nextPage}&keyword=${keyword}&categoryId=${categoryId}&searchMode=${searchMode}&minPrice=${minPrice}&maxPrice=${maxPrice}&maxPublishDays=${maxPublishDays}&sortBy=${sortBy}">下一页</a>
                    </li>
                </c:if>
            </ul>
        </nav>
    </div>

    <div class="footer">
        <div class="container d-flex justify-content-between">
            <span>校园二手交易平台</span>
            <span class="text-muted">安全 · 美观 · 省心</span>
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



