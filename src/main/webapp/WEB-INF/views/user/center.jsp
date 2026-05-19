<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>个人中心 - 校园二手交易平台</title>
    <link href="https://cdn.bootcdn.net/ajax/libs/bootstrap/4.6.2/css/bootstrap.min.css" rel="stylesheet">
    <link href="${ctx}/static/css/main.css" rel="stylesheet">
    <style>
        .nav-tabs .nav-link {
            border-radius: 8px 8px 0 0;
        }
        .nav-tabs .nav-link.active {
            font-weight: bold;
            background: #fff;
            border-bottom: 2px solid #2563eb;
        }
        .quick-link-card {
            border: 1px solid #e5e7eb;
            border-radius: 12px;
            padding: 20px;
            text-align: center;
            transition: all 0.2s;
        }
        .quick-link-card:hover {
            border-color: #2563eb;
            box-shadow: 0 4px 12px rgba(37, 99, 235, 0.15);
        }
        .quick-link-card .icon {
            font-size: 32px;
            margin-bottom: 10px;
        }
    </style>
</head>
<body>
    <nav class="navbar navbar-expand-lg navbar-light bg-white">
        <div class="container">
            <a class="navbar-brand brand-strong" href="${ctx}/">校园二手交易平台</a>
            <div class="ml-auto">
                <a href="${ctx}/product/list" class="btn btn-sm btn-outline-primary mr-2">返回商品列表</a>
                <a href="${ctx}/user/logout" class="btn btn-sm btn-outline-secondary">退出登录</a>
            </div>
        </div>
    </nav>

    <div class="container mt-4">
        <div class="row mb-4">
            <div class="col-md-12">
                <div class="card card-ui">
                    <div class="card-body d-flex align-items-center">
                        <div class="user-avatar mr-3" style="width: 60px; height: 60px; font-size: 24px;">
                            ${not empty sessionScope.user.nickname ? sessionScope.user.nickname.substring(0,1) : '用'}
                        </div>
                        <div>
                            <h5 class="mb-1">${sessionScope.user.nickname}</h5>
                            <small class="text-muted">@${sessionScope.user.username}</small>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <ul class="nav nav-tabs" id="centerTabs" role="tablist">
            <li class="nav-item">
                <a class="nav-link active" id="profile-tab" data-toggle="tab" href="#profile" role="tab">个人信息</a>
            </li>
            <li class="nav-item">
                <a class="nav-link" id="orders-tab" data-toggle="tab" href="#orders" role="tab">我的订单</a>
            </li>
            <li class="nav-item">
                <a class="nav-link" id="sales-tab" data-toggle="tab" href="#sales" role="tab">我的销售</a>
            </li>
            <li class="nav-item">
                <a class="nav-link" id="products-tab" data-toggle="tab" href="#products" role="tab">我的商品</a>
            </li>
        </ul>

        <div class="tab-content mt-4" id="centerTabContent">
            <!-- 个人信息 -->
            <div class="tab-pane fade show active" id="profile" role="tabpanel">
                <div class="row">
                    <div class="col-md-6">
                        <div class="card card-ui">
                            <div class="card-body">
                                <h5 class="mb-4">编辑个人信息</h5>
                                <form id="updateForm">
                                    <div class="form-group">
                                        <label>用户名</label>
                                        <input type="text" class="form-control bg-light" value="${sessionScope.user.username}" disabled>
                                        <small class="form-text text-muted">用户名不可修改</small>
                                    </div>
                                    <div class="form-group">
                                        <label>昵称</label>
                                        <input type="text" name="nickname" class="form-control" value="${sessionScope.user.nickname}" placeholder="展示给其他用户的名称">
                                    </div>
                                    <div class="form-group">
                                        <label>手机号</label>
                                        <input type="text" name="phone" class="form-control" value="${sessionScope.user.phone}" placeholder="方便买家联系您">
                                    </div>
                                    <div class="form-group">
                                        <label>邮箱</label>
                                        <input type="email" name="email" class="form-control" value="${sessionScope.user.email}" placeholder="用于接收通知">
                                    </div>
                                    <button type="submit" class="btn btn-primary">保存修改</button>
                                </form>
                            </div>
                        </div>
                    </div>
                    <div class="col-md-6">
                        <div class="card card-ui">
                            <div class="card-body">
                                <h5 class="mb-4">账户安全</h5>
                                <p class="text-muted">账户状态：
                                    <c:if test="${sessionScope.user.status == 1}">
                                        <span class="badge badge-success">正常</span>
                                    </c:if>
                                    <c:if test="${sessionScope.user.status == 0}">
                                        <span class="badge badge-danger">已冻结</span>
                                    </c:if>
                                </p>
                                <p class="text-muted">用户角色：
                                    <c:if test="${sessionScope.user.role == 1}">
                                        <span class="badge badge-info">普通用户</span>
                                    </c:if>
                                    <c:if test="${sessionScope.user.role == 2}">
                                        <span class="badge badge-warning">管理员</span>
                                    </c:if>
                                </p>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <!-- 我的订单 -->
            <div class="tab-pane fade" id="orders" role="tabpanel">
                <div class="row">
                    <div class="col-md-4 mb-3">
                        <a href="${ctx}/order/myOrders" class="text-decoration-none">
                            <div class="quick-link-card">
                                <div class="icon">📦</div>
                                <h6>查看全部订单</h6>
                                <small class="text-muted">查看您购买的所有商品</small>
                            </div>
                        </a>
                    </div>
                    <div class="col-md-4 mb-3">
                        <a href="${ctx}/product/list" class="text-decoration-none">
                            <div class="quick-link-card">
                                <div class="icon">🛒</div>
                                <h6>继续逛逛</h6>
                                <small class="text-muted">发现更多好物</small>
                            </div>
                        </a>
                    </div>
                    <div class="col-md-4 mb-3">
                        <a href="${ctx}/product/history" class="text-decoration-none">
                            <div class="quick-link-card">
                                <div class="icon">📜</div>
                                <h6>浏览历史</h6>
                                <small class="text-muted">查看最近浏览的商品</small>
                            </div>
                        </a>
                    </div>
                </div>
            </div>

            <!-- 我的销售 -->
            <div class="tab-pane fade" id="sales" role="tabpanel">
                <div class="row">
                    <div class="col-md-4 mb-3">
                        <a href="${ctx}/order/mySales" class="text-decoration-none">
                            <div class="quick-link-card">
                                <div class="icon">💰</div>
                                <h6>查看全部销售</h6>
                                <small class="text-muted">查看您卖出的商品订单</small>
                            </div>
                        </a>
                    </div>
                    <div class="col-md-4 mb-3">
                        <a href="${ctx}/product/publish" class="text-decoration-none">
                            <div class="quick-link-card">
                                <div class="icon">📤</div>
                                <h6>发布新商品</h6>
                                <small class="text-muted">发布您的闲置物品</small>
                            </div>
                        </a>
                    </div>
                    <div class="col-md-4 mb-3">
                        <a href="${ctx}/product/manage" class="text-decoration-none">
                            <div class="quick-link-card">
                                <div class="icon">⚙️</div>
                                <h6>管理商品</h6>
                                <small class="text-muted">编辑或下架您的商品</small>
                            </div>
                        </a>
                    </div>
                </div>
            </div>

            <!-- 我的商品 -->
            <div class="tab-pane fade" id="products" role="tabpanel">
                <div class="row">
                    <div class="col-md-4 mb-3">
                        <a href="${ctx}/product/manage" class="text-decoration-none">
                            <div class="quick-link-card">
                                <div class="icon">📋</div>
                                <h6>管理我的商品</h6>
                                <small class="text-muted">查看、编辑、上下架商品</small>
                            </div>
                        </a>
                    </div>
                    <div class="col-md-4 mb-3">
                        <a href="${ctx}/product/publish" class="text-decoration-none">
                            <div class="quick-link-card">
                                <div class="icon">➕</div>
                                <h6>发布新商品</h6>
                                <small class="text-muted">发布您的闲置物品</small>
                            </div>
                        </a>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <div class="footer">
        <div class="container d-flex justify-content-between">
            <span>校园二手交易平台</span>
            <span class="text-muted">让闲置流动起来</span>
        </div>
    </div>

    <script src="https://cdn.bootcdn.net/ajax/libs/jquery/3.6.0/jquery.min.js"></script>
    <script src="https://cdn.bootcdn.net/ajax/libs/bootstrap/4.6.2/js/bootstrap.bundle.min.js"></script>
    <script>
        // 处理 URL 中的 hash，自动切换到对应 Tab
        $(document).ready(function() {
            var hash = window.location.hash;
            if (hash) {
                // 移除 hash 中可能的查询参数
                hash = hash.split('?')[0];
                // 激活对应的 Tab
                $('#centerTabs a[href="' + hash + '"]').tab('show');
            }

            // 点击 Tab 时更新 URL hash
            $('#centerTabs a').on('shown.bs.tab', function (e) {
                history.replaceState(null, null, e.target.getAttribute('href'));
            });
        });

        // 保存个人信息
        $('#updateForm').submit(function(e) {
            e.preventDefault();
            $.ajax({
                url: '${ctx}/user/update',
                type: 'POST',
                data: $(this).serialize(),
                success: function(result) {
                    if (result.success) {
                        alert('✅ 修改成功！');
                        location.reload();
                    } else {
                        alert('❌ 修改失败');
                    }
                },
                error: function() {
                    alert('网络异常，请稍后重试');
                }
            });
        });
    </script>
</body>
</html>
