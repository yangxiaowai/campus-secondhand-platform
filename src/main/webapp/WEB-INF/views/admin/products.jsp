<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>商品管理</title>
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
        <h2>商品管理</h2>
        <table class="table table-bordered">
            <thead>
                <tr>
                    <th>商品图片</th>
                    <th>商品名称</th>
                    <th>价格</th>
                    <th>卖家</th>
                    <th>状态</th>
                    <th>发布时间</th>
                    <th>操作</th>
                </tr>
            </thead>
            <tbody>
                <c:forEach items="${products}" var="product">
                    <tr>
                        <td>
                            <img src="${ctx}${product.imageUrl}" style="width: 80px; height: 80px; object-fit: cover;"
                                 onerror="this.onerror=null;this.src='${ctx}/static/img/placeholder.svg';">
                        </td>
                        <td>${product.name}</td>
                        <td>¥${product.price}</td>
                        <td>${product.seller.nickname}</td>
                        <td>
                            <c:if test="${product.status == 0}">在售</c:if>
                            <c:if test="${product.status == 1}">已售出</c:if>
                            <c:if test="${product.status == 2}">已下架</c:if>
                        </td>
                        <td><fmt:formatDate value="${product.createTime}" pattern="yyyy-MM-dd HH:mm"/></td>
                        <td>
                            <a href="${ctx}/product/detail?id=${product.id}" class="btn btn-sm btn-info">查看</a>
                            <c:if test="${product.status != 2}">
                                <button class="btn btn-sm btn-warning" onclick="offline(${product.id})">下架</button>
                            </c:if>
                        </td>
                    </tr>
                </c:forEach>
            </tbody>
        </table>
    </div>

    <script src="https://cdn.bootcdn.net/ajax/libs/jquery/3.6.0/jquery.min.js"></script>
    <script src="https://cdn.bootcdn.net/ajax/libs/bootstrap/4.6.2/js/bootstrap.bundle.min.js"></script>
    <script>
        function offline(id) {
            if (confirm('确定要下架此商品吗？')) {
                $.ajax({
                    url: '${ctx}/admin/product/offline',
                    type: 'POST',
                    data: {id: id},
                    success: function(result) {
                        if (result.success) {
                            location.reload();
                        } else {
                            alert('操作失败');
                        }
                    }
                });
            }
        }
    </script>
</body>
</html>



