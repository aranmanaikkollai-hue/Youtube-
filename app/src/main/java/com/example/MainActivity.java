package com.example;

import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends ComponentActivity {

    private WebView webView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private FrameLayout fullscreenContainer;
    private FrameLayout webViewContainer;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        android.content.Intent serviceIntent = new android.content.Intent(this, MediaPlaybackService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        fullscreenContainer = findViewById(R.id.fullscreenContainer);
        webViewContainer = findViewById(R.id.webViewContainer);

        // Pre-create the WebView HTTP Cache Code Cache directory structure to prevent chromium warning logs on first run.
        try {
            java.io.File jsCacheDir = new java.io.File(getCacheDir(), "WebView/Default/HTTP Cache/Code Cache/js");
            if (!jsCacheDir.exists()) {
                jsCacheDir.mkdirs();
            }
            java.io.File wasmCacheDir = new java.io.File(getCacheDir(), "WebView/Default/HTTP Cache/Code Cache/wasm");
            if (!wasmCacheDir.exists()) {
                wasmCacheDir.mkdirs();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        setupWebView();

        // Disabling SwipeRefreshLayout to prevent intercepting touches in YouTube's internal scrolling container.
        // This fixes the issue where the mini-player disappears or scrolling doesn't work.
        swipeRefreshLayout.setEnabled(false);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (webView != null) {
                webView.reload();
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (customView != null) {
                    if (customViewCallback != null) {
                        customViewCallback.onCustomViewHidden();
                    }
                } else if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    finish();
                }
            }
        });

        if (savedInstanceState == null) {
            webView.loadUrl("https://m.youtube.com");
        }
    }

    private void setupWebView() {
        webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        webViewContainer.addView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);

        // Dynamically get the default User-Agent and strip the "; wv" (WebView) identifier.
        // Also remove "Version/4.0" which is another telltale sign of Android WebView.
        // This ensures the Chrome version in the UA matches the actual rendering engine,
        // which prevents Google's bot-detection from blocking sign-ins (Error 403: disallowed_useragent).
        String defaultUserAgent = settings.getUserAgentString();
        String safeUserAgent = defaultUserAgent.replace("; wv", "").replace("Version/4.0 ", "");
        settings.setUserAgentString(safeUserAgent);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebViewClient(new WebViewClient() {
            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrlLoading(view, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                return handleUrlLoading(view, url);
            }

            private boolean handleUrlLoading(WebView view, String url) {
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false; // Let WebView load it
                }

                try {
                    android.content.Intent intent = android.content.Intent.parseUri(url, android.content.Intent.URI_INTENT_SCHEME);
                    if (intent != null) {
                        view.getContext().startActivity(intent);
                        return true; // Redirection handled
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return true; // Safe check for unsupported custom schemes
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                // SwipeRefreshLayout is intentionally kept disabled
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                swipeRefreshLayout.setRefreshing(false);

                // Flush cookies to guarantee high sign-in persistence
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    CookieManager.getInstance().flush();
                }

                // Inject CSS to improve general visibility
                String js = "javascript:(function() { " +
                        "if (!document.getElementById('custom-tweaks')) {" +
                        "  var style = document.createElement('style'); " +
                        "  style.id = 'custom-tweaks'; " +
                        "  style.innerHTML = '" +
                        "    /* Ensure bottom bar is visible and padded for safe area */ " +
                        "    ytm-pivot-bar-renderer { bottom: 0 !important; z-index: 100 !important; visibility: visible !important; display: flex !important; } " +
                        "    body { padding-bottom: 48px !important; } " +
                        "  '; " +
                        "  document.head.appendChild(style); " +
                        "}" +
                        "})()";
                view.evaluateJavascript(js, null);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                WebView.HitTestResult result = view.getHitTestResult();
                String data = result.getExtra();
                if (data != null) {
                    view.loadUrl(data);
                } else {
                    Context webViewContext = view.getContext();
                    WebView transportWebView = new WebView(webViewContext);
                    transportWebView.setWebViewClient(new WebViewClient() {
                        @SuppressWarnings("deprecation")
                        @Override
                        public boolean shouldOverrideUrlLoading(WebView view, String url) {
                            webView.loadUrl(url);
                            return true;
                        }

                        @Override
                        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                            webView.loadUrl(request.getUrl().toString());
                            return true;
                        }
                    });
                    ((WebView.WebViewTransport) resultMsg.obj).setWebView(transportWebView);
                    resultMsg.sendToTarget();
                }
                return true;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                super.onShowCustomView(view, callback);
                if (customView != null) {
                    if (callback != null) {
                        callback.onCustomViewHidden();
                    }
                    return;
                }
                customView = view;
                customViewCallback = callback;
                fullscreenContainer.addView(view);
                fullscreenContainer.setVisibility(View.VISIBLE);
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }

            @Override
            public void onHideCustomView() {
                super.onHideCustomView();
                if (customView == null) return;

                fullscreenContainer.removeView(customView);
                customView = null;
                customViewCallback = null;
                fullscreenContainer.setVisibility(View.GONE);
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (webView != null) {
            webView.restoreState(savedInstanceState);
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                    PipHelper.enterPip(this, webView != null ? webView.getUrl() : null);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                enterPictureInPictureMode();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class PipHelper {
        @android.annotation.TargetApi(Build.VERSION_CODES.O)
        static void enterPip(android.app.Activity activity, String url) {
            PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
            if (url != null && url.contains("/watch?")) {
                builder.setAspectRatio(new android.util.Rational(16, 9));
            }
            activity.enterPictureInPictureMode(builder.build());
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            String js = "javascript:(function() { " +
                    "document.body.classList.add('in-pip'); " +
                    "var style = document.createElement('style'); " +
                    "style.id = 'pip-style'; " +
                    "style.innerHTML = '" +
                    ".in-pip header, .in-pip ytm-mobile-topbar-renderer, .in-pip ytm-item-section-renderer, .in-pip ytm-single-column-watch-next-results-renderer { display: none !important; } " +
                    ".in-pip .html5-video-player, .in-pip #player-container-id { position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; height: 100vh !important; z-index: 2147483647 !important; } " +
                    "'; " +
                    "document.head.appendChild(style); " +
                    "})()";
            webView.evaluateJavascript(js, null);
        } else {
            String js = "javascript:(function() { " +
                    "document.body.classList.remove('in-pip'); " +
                    "var style = document.getElementById('pip-style'); " +
                    "if (style) { style.parentNode.removeChild(style); } " +
                    "})()";
            webView.evaluateJavascript(js, null);
        }
    }
}
