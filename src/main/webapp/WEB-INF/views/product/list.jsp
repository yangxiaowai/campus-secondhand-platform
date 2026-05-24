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
                    <div class="col-md-2 mb-2 text-right">
                        <a href="${ctx}/product/publish" class="btn btn-success btn-block">发布商品</a>
                    </div>
                </div>
            </form>
            <c:if test="${not empty keyword}">
                <div class="mt-2 small text-muted">
                    引擎：${searchResult.engine}
                    · 模式：${searchResult.searchMode}
                    · 语义：${searchResult.semanticEngine}
                    · Embedding：${searchResult.embeddingModel}
                    · 降级：${searchResult.degradeLevel}
                    · 分片：${searchResult.shardCount}
                    · 耗时：${searchResult.tookMs}ms
                    · 命中：${searchResult.total} 件
                </div>
            </c:if>
        </div>

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
                                <small class="text-muted">发布时间 ${product.createTime}</small>
                            </div>
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
                        <a class="page-link" href="${ctx}/product/list?pageNum=${pageInfo.prePage}&keyword=${keyword}&categoryId=${categoryId}&searchMode=${searchMode}">上一页</a>
                    </li>
                </c:if>
                <c:forEach items="${pageInfo.navigatepageNums}" var="num">
                    <li class="page-item ${num == pageInfo.pageNum ? 'active' : ''}">
                        <a class="page-link" href="${ctx}/product/list?pageNum=${num}&keyword=${keyword}&categoryId=${categoryId}&searchMode=${searchMode}">${num}</a>
                    </li>
                </c:forEach>
                <c:if test="${pageInfo.hasNextPage}">
                    <li class="page-item">
                        <a class="page-link" href="${ctx}/product/list?pageNum=${pageInfo.nextPage}&keyword=${keyword}&categoryId=${categoryId}&searchMode=${searchMode}">下一页</a>
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
</body>
</html>



