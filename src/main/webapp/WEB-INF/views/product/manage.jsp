<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>我的商品管理</title>
    <link href="https://cdn.bootcdn.net/ajax/libs/bootstrap/4.6.2/css/bootstrap.min.css" rel="stylesheet">
</head>
<body>
    <nav class="navbar navbar-expand-lg navbar-light bg-light">
        <div class="container">
            <a class="navbar-brand" href="${ctx}/">校园二手交易平台</a>
            <div class="ml-auto">
                <a href="${ctx}/product/list" class="btn btn-sm btn-outline-primary">返回商品列表</a>
            </div>
        </div>
    </nav>

    <div class="container mt-4">
        <h2>我的商品管理</h2>
        <a href="${ctx}/product/publish" class="btn btn-success mb-3">发布新商品</a>
        <table class="table table-bordered">
            <thead>
                <tr>
                    <th>商品图片</th>
                    <th>商品名称</th>
                    <th>价格</th>
                    <th>分类</th>
                    <th>状态</th>
                    <th>发布时间</th>
                    <th>操作</th>
                </tr>
            </thead>
            <tbody>
                <c:forEach items="${products}" var="product">
                    <tr>
                        <td>
                            <img src="${product.imageUrl}" style="width: 80px; height: 80px; object-fit: cover;"
                                 onerror="this.src='https://via.placeholder.com/80'">
                        </td>
                        <td>${product.name}</td>
                        <td>¥${product.price}</td>
                        <td>${product.category.categoryName}</td>
                        <td>
                            <c:if test="${product.status == 0}">在售</c:if>
                            <c:if test="${product.status == 1}">已售出</c:if>
                            <c:if test="${product.status == 2}">已下架</c:if>
                        </td>
                        <td><fmt:formatDate value="${product.createTime}" pattern="yyyy-MM-dd HH:mm"/></td>
                        <td>
                            <a href="${ctx}/product/detail?id=${product.id}" class="btn btn-sm btn-info">查看</a>
                            <c:if test="${product.status == 0}">
                                <button class="btn btn-sm btn-warning" onclick="offline('${product.id}')">下架</button>
                            </c:if>
                            <c:if test="${product.status == 2}">
                                <button class="btn btn-sm btn-success" onclick="online('${product.id}')">上架</button>
                            </c:if>
                            <button class="btn btn-sm btn-danger" onclick="deleteProduct('${product.id}')">删除</button>
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
                    url: '${ctx}/product/updateStatus',
                    type: 'GET',
                    data: {id: id, status: 2},
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

        function online(id) {
            $.ajax({
                url: '${ctx}/product/updateStatus',
                type: 'GET',
                data: {id: id, status: 0},
                success: function(result) {
                    if (result.success) {
                        location.reload();
                    } else {
                        alert('操作失败');
                    }
                }
            });
        }

        function deleteProduct(id) {
            if (confirm('确定要删除此商品吗？此操作不可恢复！')) {
                $.ajax({
                    url: '${ctx}/product/delete',
                    type: 'GET',
                    data: {id: id},
                    success: function(result) {
                        if (result.success) {
                            location.reload();
                        } else {
                            alert('删除失败');
                        }
                    }
                });
            }
        }
    </script>
</body>
</html>



