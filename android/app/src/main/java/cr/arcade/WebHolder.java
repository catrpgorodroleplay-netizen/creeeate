package com.cr.arcade;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.MutableContextWrapper;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Один-единственный WebView на всё приложение.
 * Он НЕ принадлежит Activity: его просто переставляют то в Activity, то в оверлей,
 * поэтому сайт не перезагружается и не теряет состояние.
 */
public class WebHolder {
    public static final String URL_HOME = "https://wyikhedfghhopyewfvjkurrhncswehipkhf.vercel.app/";

    private static WebView web;
    private static MutableContextWrapper ctx;

    public static synchronized WebView get(Context c) {
        if (web == null) {
            ctx = new MutableContextWrapper(c.getApplicationContext());
            web = new WebView(ctx);
            configure(web);
            web.loadUrl(URL_HOME);
        }
        return web;
    }

    public static boolean exists() { return web != null; }

    public static void setBase(Context c) {
        if (ctx != null) ctx.setBaseContext(c);
    }

    public static void detach() {
        if (web != null && web.getParent() instanceof ViewGroup) {
            ((ViewGroup) web.getParent()).removeView(web);
        }
    }

    public static synchronized void destroy() {
        if (web != null) {
            detach();
            try { web.stopLoading(); web.loadUrl("about:blank"); web.destroy(); } catch (Exception ignored) {}
            web = null;
            ctx = null;
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private static void configure(WebView w) {
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(w, true);
        w.setBackgroundColor(0xFF0E0E0E);
        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                request.grant(request.getResources()); // микрофон/камера для сайта
            }
        });
        w.setWebViewClient(new WebViewClient());
    }
}
