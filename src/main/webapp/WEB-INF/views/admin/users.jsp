<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>用户管理</title>
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
        <h2>用户管理</h2>
        <table class="table table-bordered">
            <thead>
                <tr>
                    <th>ID</th>
                    <th>用户名</th>
                    <th>昵称</th>
                    <th>手机号</th>
                    <th>角色</th>
                    <th>状态</th>
                    <th>注册时间</th>
                    <th>操作</th>
                </tr>
            </thead>
            <tbody>
                <c:forEach items="${users}" var="user">
                    <tr>
                        <td>${user.id}</td>
                        <td>${user.username}</td>
                        <td>${user.nickname}</td>
                        <td>${user.phone}</td>
                        <td>
                            <c:if test="${user.role == 1}">普通用户</c:if>
                            <c:if test="${user.role == 2}">管理员</c:if>
                        </td>
                        <td>
                            <c:if test="${user.status == 1}">正常</c:if>
                            <c:if test="${user.status == 0}">冻结</c:if>
                        </td>
                        <td><fmt:formatDate value="${user.createTime}" pattern="yyyy-MM-dd HH:mm"/></td>
                        <td>
                            <c:if test="${user.status == 1}">
                                <button class="btn btn-sm btn-warning" onclick="freeze('${user.id}')">冻结</button>
                            </c:if>
                            <c:if test="${user.status == 0}">
                                <button class="btn btn-sm btn-success" onclick="unfreeze('${user.id}')">解冻</button>
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
        function freeze(id) {
            if (confirm('确定要冻结此用户吗？')) {
                $.ajax({
                    url: '${ctx}/admin/user/updateStatus',
                    type: 'POST',
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
        }

        function unfreeze(id) {
            $.ajax({
                url: '${ctx}/admin/user/updateStatus',
                type: 'POST',
                data: {id: id, status: 1},
                success: function(result) {
                    if (result.success) {
                        location.reload();
                    } else {
                        alert('操作失败');
                    }
                }
            });
        }
    </script>
</body>
</html>



