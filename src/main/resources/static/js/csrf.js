(function (window, $) {
    'use strict';
    var cookieName = 'XSRF-TOKEN';
    var headerName = 'X-XSRF-TOKEN';
    function readToken() {
        var prefix = cookieName + '=';
        var cookies = document.cookie ? document.cookie.split(';') : [];
        for (var i = 0; i < cookies.length; i++) {
            var value = cookies[i].replace(/^\s+/, '');
            if (value.indexOf(prefix) === 0) return decodeURIComponent(value.substring(prefix.length));
        }
        return '';
    }
    window.getCsrfToken = readToken;
    if ($) {
        $.ajaxPrefilter(function (options, originalOptions, jqXHR) {
            var method = (options.type || options.method || 'GET').toUpperCase();
            var token = readToken();
            if (method !== 'GET' && method !== 'HEAD' && token) jqXHR.setRequestHeader(headerName, token);
        });
    }
    if (window.fetch) {
        var nativeFetch = window.fetch.bind(window);
        window.fetch = function (input, init) {
            init = init || {};
            var method = (init.method || (input && input.method) || 'GET').toUpperCase();
            if (method !== 'GET' && method !== 'HEAD') {
                var headers = new Headers(init.headers || {}), token = readToken();
                if (token) headers.set(headerName, token);
                init.headers = headers;
            }
            return nativeFetch(input, init);
        };
    }
}(window, window.jQuery));
