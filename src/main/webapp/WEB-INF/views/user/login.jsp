<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>用户登录 - 校园二手交易平台</title>
    <link href="https://cdn.bootcdn.net/ajax/libs/bootstrap/4.6.2/css/bootstrap.min.css" rel="stylesheet">
    <link href="${ctx}/static/css/main.css" rel="stylesheet">
    <style>
        .captcha-img {
            cursor: pointer;
            height: 40px;
            border-radius: 4px;
        }
        .captcha-group {
            display: flex;
            gap: 10px;
        }
        .captcha-group input {
            flex: 1;
        }
        .error-msg {
            color: #dc3545;
            font-size: 14px;
            margin-top: 10px;
            display: none;
        }
    </style>
</head>
<body style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); min-height: 100vh;">
    <div class="container" style="padding-top: 80px;">
        <div class="row justify-content-center">
            <div class="col-md-5 col-lg-4">
                <div class="text-center mb-4">
                    <h2 class="text-white">校园二手交易平台</h2>
                    <p class="text-white-50">让闲置流动起来</p>
                </div>
                <div class="card card-ui" style="border-radius: 16px;">
                    <div class="card-body p-4">
                        <h4 class="text-center mb-2">用户登录</h4>
                        <p class="text-center text-muted mb-4">登录后可发布闲置、下单购买</p>
                        
                        <div id="errorMsg" class="alert alert-danger error-msg"></div>
                        
                        <form id="loginForm">
                            <div class="form-group">
                                <label><i class="text-danger">*</i> 用户名</label>
                                <input type="text" name="username" class="form-control" 
                                       placeholder="请输入用户名" required>
                            </div>
                            <div class="form-group">
                                <label><i class="text-danger">*</i> 密码</label>
                                <input type="password" name="password" class="form-control" 
                                       placeholder="请输入密码" required>
                            </div>
                            <div class="form-group">
                                <label><i class="text-danger">*</i> 验证码</label>
                                <div class="captcha-group">
                                    <input type="text" name="captcha" class="form-control" 
                                           placeholder="请输入验证码" maxlength="4" required>
                                    <img id="captchaImg" class="captcha-img"
                                         alt="验证码"
                                         onclick="refreshCaptcha()"
                                         title="点击刷新验证码">
                                </div>
                                <script type="text/javascript">
                                    (function () {
                                        var el = document.getElementById('captchaImg');
                                        if (el) {
                                            el.src = '${ctx}/captcha/image?t=' + Date.now();
                                        }
                                    })();
                                </script>
                                <small class="form-text text-muted">点击图片可刷新验证码</small>
                            </div>
                            <button type="submit" class="btn btn-primary btn-block btn-lg mt-4">登 录</button>
                        </form>
                        
                        <hr class="my-4">
                        
                        <div class="text-center">
                            <span class="text-muted">还没有账号？</span>
                            <a href="${ctx}/user/registerPage" class="text-primary">立即注册</a>
                        </div>
                        <div class="text-center mt-2">
                            <a href="${ctx}/" class="text-muted">← 返回首页</a>
                        </div>
                    </div>
                </div>
                
                <div class="text-center mt-4 text-white-50">
                    <small>© 2024 校园二手交易平台</small>
                </div>
            </div>
        </div>
    </div>

    <script src="https://cdn.bootcdn.net/ajax/libs/jquery/3.6.0/jquery.min.js"></script>
    <script src="https://cdn.bootcdn.net/ajax/libs/bootstrap/4.6.2/js/bootstrap.bundle.min.js"></script>
    <script>
        // 刷新验证码
        function refreshCaptcha() {
            document.getElementById('captchaImg').src = '${ctx}/captcha/image?' + new Date().getTime();
        }

        // 显示错误信息
        function showError(msg) {
            var errorDiv = $('#errorMsg');
            errorDiv.text(msg).fadeIn();
            // 刷新验证码
            refreshCaptcha();
        }

        // 隐藏错误信息
        function hideError() {
            $('#errorMsg').fadeOut();
        }

        // 表单提交
        $('#loginForm').submit(function(e) {
            e.preventDefault();
            hideError();
            
            var username = $('input[name="username"]').val().trim();
            var password = $('input[name="password"]').val().trim();
            var captcha = $('input[name="captcha"]').val().trim();
            
            if (!username || !password || !captcha) {
                showError('请填写完整的登录信息');
                return;
            }
            
            $.ajax({
                url: '${ctx}/user/login',
                type: 'POST',
                data: $(this).serialize(),
                success: function(result) {
                    if (result.success) {
                        // 登录成功，跳转到首页
                        window.location.href = '${ctx}/';
                    } else {
                        showError(result.message || '登录失败');
                    }
                },
                error: function() {
                    showError('网络异常，请稍后重试');
                }
            });
        });

        // 页面初始验证码已带时间戳，不再额外刷新，避免覆盖用户看到的验证码
    </script>
</body>
</html>
