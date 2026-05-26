/**
 * 推荐收件箱实时提醒：轮询 /user/inbox/poll，顶部固定横幅 + 角标
 */
var InboxNotify = (function () {
    var ctx = '';
    var timerId = null;
    var pollIntervalMs = 3000;
    var lastUnread = -1;
    var storageKey = 'campus.inbox.lastUnread';

    var BANNER_CONTAINER_STYLE =
        'position:fixed;top:0;left:0;right:0;z-index:10050;padding:62px 16px 0;margin:0;box-sizing:border-box;pointer-events:none;';

    function api(path, options) {
        options = options || {};
        return $.ajax($.extend({
            url: ctx + path,
            dataType: 'json',
            cache: false,
            xhrFields: { withCredentials: true },
            headers: { 'X-Requested-With': 'XMLHttpRequest' }
        }, options));
    }

    function loadStoredUnread() {
        try {
            var v = sessionStorage.getItem(storageKey);
            if (v !== null && v !== '') {
                return parseInt(v, 10);
            }
        } catch (e) { /* ignore */ }
        return -1;
    }

    function saveUnread(n) {
        lastUnread = n;
        try {
            sessionStorage.setItem(storageKey, String(n));
        } catch (e) { /* ignore */ }
    }

    function updateBadge(count) {
        $('[data-inbox-badge]').each(function () {
            var $b = $(this);
            if (count > 0) {
                $b.text(count > 99 ? '99+' : String(count)).show();
            } else {
                $b.hide();
            }
        });
    }

    function ensureBannerContainer() {
        var $box = $('#inbox-banner-container');
        if (!$box.length) {
            $box = $('<div id="inbox-banner-container" class="inbox-banner-container"></div>');
            $box.attr('style', BANNER_CONTAINER_STYLE);
            $('body').prepend($box);
        }
        return $box;
    }

    function confirmNavigate(url, message) {
        if (window.confirm(message)) {
            window.location.href = url;
        }
    }

    function bindBannerActions($banner, opts) {
        var name = opts.name;
        var hasProduct = opts.hasProduct;
        var detailUrl = opts.detailUrl;
        var inboxUrl = ctx + '/user/inbox/page';

        $banner.on('click', function (e) {
            if ($(e.target).closest('.close, [data-dismiss="alert"], [data-inbox-action]').length) {
                return;
            }
            if (hasProduct) {
                confirmNavigate(detailUrl, '是否前往商品「' + name + '」的详情页？');
            } else {
                confirmNavigate(inboxUrl, '是否打开推荐收件箱查看全部推荐？');
            }
        });

        $banner.find('[data-inbox-action="detail"]').on('click', function (e) {
            e.stopPropagation();
            if (hasProduct) {
                confirmNavigate(detailUrl, '是否前往商品「' + name + '」的详情页？');
            }
        });

        $banner.find('[data-inbox-action="inbox"]').on('click', function (e) {
            e.stopPropagation();
            confirmNavigate(inboxUrl, '是否打开推荐收件箱？');
        });
    }

    function showBanner(n) {
        var $container = ensureBannerContainer();
        var name = n.name || ('商品 #' + n.productId);
        var score = n.matchScore != null ? n.matchScore : '';
        var hasProduct = n.productId != null && n.productId !== '';
        var detailUrl = hasProduct ? (ctx + '/product/detail?id=' + n.productId) : '';
        var titleHtml = '<span class="alert-link font-weight-bold inbox-banner-title">' +
            $('<span>').text(name).html() + '</span>';
        var detailBtn = hasProduct
            ? '<button type="button" class="btn btn-sm btn-warning text-white ml-2" data-inbox-action="detail">查看详情</button>'
            : '';
        var hint = hasProduct
            ? '<span class="small ml-2 opacity-75">点击「查看详情」可跳转</span>'
            : '<span class="small ml-2 opacity-75">点击横幅可打开收件箱</span>';
        var $banner = $('<div class="inbox-banner alert alert-primary alert-dismissible fade show shadow inbox-banner-clickable" role="alert" tabindex="0">' +
            '<strong>📬 新推荐匹配</strong> ' + titleHtml +
            (score !== '' ? ' <span class="badge badge-light text-primary ml-1">匹配 ' + score + '</span>' : '') +
            detailBtn +
            '<button type="button" class="btn btn-sm btn-light ml-2" data-inbox-action="inbox">收件箱</button>' +
            hint +
            '<button type="button" class="close" data-dismiss="alert" aria-label="关闭"><span aria-hidden="true">&times;</span></button></div>');
        bindBannerActions($banner, { name: name, hasProduct: hasProduct, detailUrl: detailUrl });
        $container.empty().append($banner);
        $('html, body').scrollTop(0);
        setTimeout(function () {
            $banner.alert('close');
        }, 12000);
    }

    function notifyNewItems(notifications, unreadCount, doNotDisturb) {
        if (doNotDisturb) {
            return;
        }
        if (notifications && notifications.length) {
            notifications.forEach(function (n) {
                showBanner(n);
            });
            return;
        }
        if (lastUnread >= 0 && unreadCount > lastUnread) {
            var delta = unreadCount - lastUnread;
            showBanner({ name: '您有 ' + delta + ' 条新推荐，点击查看收件箱', productId: '' });
        }
    }

    function refreshStatus() {
        return api('/user/inbox/status').then(function (res) {
            if (res && res.success && res.data) {
                var c = res.data.unreadCount || 0;
                updateBadge(c);
                if (lastUnread < 0) {
                    saveUnread(c);
                }
            }
        });
    }

    function poll() {
        api('/user/inbox/poll', { data: { since: 0 } })
            .done(function (res) {
                if (!res || !res.success || !res.data) {
                    return;
                }
                var data = res.data;
                var unread = data.unreadCount || 0;
                var dnd = !!data.doNotDisturb;
                updateBadge(unread);
                notifyNewItems(data.notifications || [], unread, dnd);
                saveUnread(unread);
            })
            .fail(function (xhr) {
                if (xhr && xhr.status === 401) {
                    stop();
                }
            });
    }

    function start(contextPath) {
        ctx = contextPath || '';
        lastUnread = loadStoredUnread();
        ensureBannerContainer();
        refreshStatus().always(function () {
            poll();
        });
        if (timerId) {
            clearInterval(timerId);
        }
        timerId = setInterval(poll, pollIntervalMs);
    }

    function stop() {
        if (timerId) {
            clearInterval(timerId);
            timerId = null;
        }
    }

    return {
        start: start,
        stop: stop,
        refreshStatus: refreshStatus,
        updateBadge: updateBadge
    };
})();
