<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>推荐收件箱 - 校园二手交易平台</title>
    <link href="https://cdn.bootcdn.net/ajax/libs/bootstrap/4.6.2/css/bootstrap.min.css" rel="stylesheet">
    <link href="${ctx}/static/css/main.css" rel="stylesheet">
    <style>
        .inbox-toolbar {
            display: flex;
            flex-wrap: wrap;
            align-items: center;
            justify-content: space-between;
            gap: 12px;
            margin-bottom: 20px;
        }
        .inbox-item {
            border: 1px solid #e5e7eb;
            border-radius: 12px;
            padding: 16px;
            margin-bottom: 12px;
            display: flex;
            align-items: center;
            transition: box-shadow 0.2s;
        }
        .inbox-item.unread {
            border-left: 4px solid #2563eb;
            background: #f8fafc;
        }
        .inbox-item:hover {
            box-shadow: 0 4px 12px rgba(0,0,0,0.06);
        }
        .inbox-item img {
            width: 72px;
            height: 72px;
            object-fit: cover;
            border-radius: 8px;
            margin-right: 16px;
        }
        .match-badge {
            font-size: 0.85rem;
        }
        .custom-switch .custom-control-label::before {
            cursor: pointer;
        }
    </style>
</head>
<body>
    <nav class="navbar navbar-expand-lg navbar-light bg-white">
        <div class="container">
            <a class="navbar-brand brand-strong" href="${ctx}/">校园二手交易平台</a>
            <div class="ml-auto">
                <a href="${ctx}/product/list" class="btn btn-sm btn-outline-primary mr-2">商品列表</a>
                <a href="${ctx}/user/center" class="btn btn-sm btn-outline-secondary">个人中心</a>
            </div>
        </div>
    </nav>

    <div class="container mt-4 mb-5">
        <h4 class="mb-1">推荐收件箱</h4>
        <p class="text-muted small mb-4">当有新发布的商品与您的兴趣画像匹配时，会在此推送并按匹配度排序展示。</p>

        <div class="inbox-toolbar card card-ui p-3">
            <div>
                <span class="text-muted">未读 </span>
                <span id="inbox-unread-text" class="font-weight-bold text-primary">0</span>
                <span class="text-muted"> 条</span>
            </div>
            <div class="d-flex align-items-center flex-wrap">
                <div class="custom-control custom-switch mr-3">
                    <input type="checkbox" class="custom-control-input" id="dndSwitch">
                    <label class="custom-control-label" for="dndSwitch" title="开启后仍写入收件箱，但不弹出顶部/右下角提醒">免打扰（关闭弹窗提醒）</label>
                </div>
                <button type="button" id="btnReadAll" class="btn btn-sm btn-outline-secondary mr-2">一键已读</button>
                <button type="button" id="btnRefresh" class="btn btn-sm btn-primary">刷新</button>
            </div>
        </div>

        <div id="inboxEmpty" class="alert alert-light text-center" style="display:none;">
            暂无推荐推送。可先浏览/购买相关商品养成画像，或等待卖家发布匹配商品。
        </div>
        <div id="inboxList"></div>
    </div>

    <script src="https://cdn.bootcdn.net/ajax/libs/jquery/3.6.0/jquery.min.js"></script>
    <script src="https://cdn.bootcdn.net/ajax/libs/bootstrap/4.6.2/js/bootstrap.bundle.min.js"></script>
    <script src="${ctx}/static/js/inbox-notify.js"></script>
    <script>
        var ctx = '${ctx}';

        function loadStatus() {
            return $.getJSON(ctx + '/user/inbox/status').then(function (res) {
                if (!res.success) return;
                var d = res.data;
                $('#inbox-unread-text').text(d.unreadCount || 0);
                $('#dndSwitch').prop('checked', !!d.doNotDisturb);
                if (window.InboxNotify) {
                    InboxNotify.updateBadge(d.unreadCount || 0);
                }
            });
        }

        function renderList(rows) {
            var $list = $('#inboxList');
            $list.empty();
            if (!rows || rows.length === 0) {
                $('#inboxEmpty').show();
                return;
            }
            $('#inboxEmpty').hide();
            rows.forEach(function (row) {
                var unread = row.read === false;
                var img = row.imageUrl || (ctx + '/static/img/placeholder.svg');
                var detailUrl = ctx + '/product/detail?id=' + row.productId;
                var sourceLabel = row.source === 'inbox' ? '发布匹配' : (row.source || '');
                var $item = $('<div class="inbox-item' + (unread ? ' unread' : '') + '">' +
                    '<img src="' + img + '" alt="">' +
                    '<div class="flex-grow-1">' +
                    '<div class="d-flex justify-content-between align-items-start">' +
                    '<h6 class="mb-1"><a href="' + detailUrl + '">' + escapeHtml(row.name || '') + '</a></h6>' +
                    '<span class="badge badge-primary match-badge">匹配 ' + (row.matchScore != null ? row.matchScore : '-') + '</span>' +
                    '</div>' +
                    '<div class="text-muted small">¥' + (row.price != null ? row.price : '-') +
                    (sourceLabel ? ' · ' + sourceLabel : '') +
                    (unread ? ' · <span class="text-primary">未读</span>' : '') +
                    '</div></div></div>');
                $item.find('a').first().on('click', function () {
                    if (unread && row.productId) {
                        $.post(ctx + '/user/inbox/read-one', { productId: row.productId });
                    }
                });
                $list.append($item);
            });
        }

        function escapeHtml(s) {
            return $('<div>').text(s).html();
        }

        function loadInbox() {
            return $.getJSON(ctx + '/user/inbox').then(function (res) {
                if (!res.success) {
                    alert(res.message || '加载失败');
                    return;
                }
                renderList(res.data);
            });
        }

        function refreshAll() {
            loadStatus();
            loadInbox();
        }

        $('#btnRefresh').on('click', refreshAll);

        $('#btnReadAll').on('click', function () {
            $.post(ctx + '/user/inbox/read-all').done(function (res) {
                if (res.success) {
                    refreshAll();
                } else {
                    alert(res.message || '操作失败');
                }
            });
        });

        $('#dndSwitch').on('change', function () {
            var enabled = $(this).is(':checked');
            $.post(ctx + '/user/inbox/dnd', { enabled: enabled }).done(function (res) {
                if (!res.success) {
                    alert(res.message || '设置失败');
                    $('#dndSwitch').prop('checked', !enabled);
                }
            });
        });

        $(function () {
            refreshAll();
            if (window.InboxNotify) {
                InboxNotify.start(ctx);
            }
        });
    </script>
</body>
</html>
