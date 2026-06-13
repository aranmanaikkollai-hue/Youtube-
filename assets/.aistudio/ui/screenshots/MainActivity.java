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
        
        startForegroundService(new android.content.Intent(this, MediaPlaybackService.class));

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        fullscreenContainer = findViewById(R.id.fullscreenContainer);
        webViewContainer = findViewById(R.id.webViewContainer);

        setupWebView();

        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (webView != null) {
                webView.reload();
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            webView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                String url = webView.getUrl();
                boolean isShorts = url != null && url.contains("/shorts/");
                swipeRefreshLayout.setEnabled(scrollY == 0 && !isShorts);
            });
        } else {
            // Fallback for older devices if getScrollY is needed
            webView.getViewTreeObserver().addOnScrollChangedListener(() -> {
                String url = webView.getUrl();
                boolean isShorts = url != null && url.contains("/shorts/");
                swipeRefreshLayout.setEnabled(webView.getScrollY() == 0 && !isShorts);
            });
        }

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
        Context webViewContext = this;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            webViewContext = createAttributionContext("play_music");
        }
        webView = new WebView(webViewContext);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        webViewContainer.addView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        // Set a standard Mobile Chrome user agent to prevent "Sign in to confirm you're not a robot"
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 13; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36");

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                if (url != null && url.contains("/shorts/")) {
                    swipeRefreshLayout.setEnabled(false);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    swipeRefreshLayout.setEnabled(view.getScrollY() == 0);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                swipeRefreshLayout.setRefreshing(false);

                // Inject CSS to improve youtube miniplayer on regular webpages (e.g. padding and visibility)
                String js = "javascript:(function() { " +
                        "if (!document.getElementById('custom-tweaks')) {" +
                        "  var style = document.createElement('style'); " +
                        "  style.id = 'custom-tweaks'; " +
                        "  style.innerHTML = '" +
                        "    ytm-miniplayer { bottom: env(safe-area-inset-bottom, 20px) !important; z-index: 9999 !important; } " +
                        "  '; " +
                        "  document.head.appendChild(style); " +
                        "}" +
                        "})()";
                view.evaluateJavascript(js, null);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String url = webView.getUrl();
            PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
            if (url != null && url.contains("/watch?")) {
                builder.setAspectRatio(new android.util.Rational(16, 9));
            }
            enterPictureInPictureMode(builder.build());
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            swipeRefreshLayout.setEnabled(false);
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
            String url = webView.getUrl();
            boolean isShorts = url != null && url.contains("/shorts/");
            if (isShorts) {
                swipeRefreshLayout.setEnabled(false);
            } else {
                swipeRefreshLayout.setEnabled(webView.getScrollY() == 0);
            }
            String js = "javascript:(function() { " +
                    "document.body.classList.remove('in-pip'); " +
                    "var style = document.getElementById('pip-style'); " +
                    "if (style) { style.parentNode.removeChild(style); } " +
                    "})()";
            webView.evaluateJavascript(js, null);
        }
    }
}
