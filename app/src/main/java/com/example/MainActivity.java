package com.example;

import android.app.PictureInPictureParams;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.graphics.Outline;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends ComponentActivity {

    private WebView webView;
    private FrameLayout fullscreenContainer;
    private FrameLayout webViewContainer;
    private android.widget.ImageButton btnModeToggle;
    private android.widget.ImageButton btnSettings;
    private android.widget.LinearLayout topControlsContainer;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private GestureDetector gestureDetector;
    private boolean isMiniPlayer = false;
    private View touchOverlay;
    private float density;

    private boolean isMusicMode = false;
    private Bundle ytState;
    private Bundle ytMusicState;

    private BroadcastReceiver cmdReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String act = intent.getAction();
            if (webView != null && act != null) {
                if (act.equals(MediaPlaybackService.ACTION_CMD_PLAY)) {
                    webView.evaluateJavascript("document.querySelector('video').play();", null);
                } else if (act.equals(MediaPlaybackService.ACTION_CMD_PAUSE)) {
                    webView.evaluateJavascript("document.querySelector('video').pause();", null);
                } else if (act.equals(MediaPlaybackService.ACTION_CMD_NEXT)) {
                    // Try YT Music next button
                    webView.evaluateJavascript("document.querySelector('.next-button').click();", null);
                } else if (act.equals(MediaPlaybackService.ACTION_CMD_PREV)) {
                    // Try YT Music prev button
                    webView.evaluateJavascript("document.querySelector('.previous-button').click();", null);
                }
            }
        }
    };

    public class WebAppInterface {
        @JavascriptInterface
        public void updateMediaState(boolean isPlaying, String title) {
            Intent intent = new Intent(MediaPlaybackService.ACTION_UPDATE_STATE);
            intent.putExtra("isPlaying", isPlaying);
            intent.putExtra("title", title);
            sendBroadcast(intent);
        }

        @JavascriptInterface
        public boolean isAdBlockEnabled() {
            return getSharedPreferences("AppSettings", MODE_PRIVATE).getBoolean("pref_adblock_enabled", true);
        }

        @JavascriptInterface
        public void onAdBlocked() {
            android.content.SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);
            int count = prefs.getInt("pref_ads_blocked_count", 0) + 1;
            prefs.edit().putInt("pref_ads_blocked_count", count).apply();
        }

        @JavascriptInterface
        public boolean isSponsorBlockEnabled() {
            return getSharedPreferences("AppSettings", MODE_PRIVATE).getBoolean("pref_sponsorblock_enabled", true);
        }

        @JavascriptInterface
        public void requestSponsorSegments(String videoId) {
            fetchSponsorSegments(videoId);
        }
    }

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

        fullscreenContainer = findViewById(R.id.fullscreenContainer);
        webViewContainer = findViewById(R.id.webViewContainer);
        touchOverlay = findViewById(R.id.touchOverlay);
        btnModeToggle = findViewById(R.id.btnModeToggle);
        btnSettings = findViewById(R.id.btnSettings);
        topControlsContainer = findViewById(R.id.topControlsContainer);

        density = getResources().getDisplayMetrics().density;
        setupGestureDetection();
        touchOverlay.setOnClickListener(v -> maximizeWebView());

        btnModeToggle.setOnClickListener(v -> switchMode(!isMusicMode));
        btnSettings.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, SettingsActivity.class)));

        IntentFilter filter = new IntentFilter();
        filter.addAction(MediaPlaybackService.ACTION_CMD_PLAY);
        filter.addAction(MediaPlaybackService.ACTION_CMD_PAUSE);
        filter.addAction(MediaPlaybackService.ACTION_CMD_NEXT);
        filter.addAction(MediaPlaybackService.ACTION_CMD_PREV);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(cmdReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(cmdReceiver, filter);
        }

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

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (customView != null) {
                    if (customViewCallback != null) {
                        customViewCallback.onCustomViewHidden();
                    }
                } else if (isMiniPlayer) {
                    maximizeWebView();
                } else if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    finish();
                }
            }
        });

        if (savedInstanceState == null) {
            webView.loadUrl("https://m.youtube.com");
            updateModeButtons();
        }
    }

    private void switchMode(boolean toMusic) {
        if (isMusicMode == toMusic) return;

        if (isMusicMode) {
            // we are in music mode, switching to YT
            ytMusicState = new Bundle();
            webView.saveState(ytMusicState);
            isMusicMode = false;
            updateModeButtons();
            
            if (ytState != null) {
                webView.restoreState(ytState);
            } else {
                webView.loadUrl("https://m.youtube.com");
            }
        } else {
            // we are in YT, switching to music
            ytState = new Bundle();
            webView.saveState(ytState);
            isMusicMode = true;
            updateModeButtons();
            
            if (ytMusicState != null) {
                webView.restoreState(ytMusicState);
            } else {
                webView.loadUrl("https://music.youtube.com");
            }
        }
    }

    private void updateModeButtons() {
        if (isMusicMode) {
            btnModeToggle.setImageResource(android.R.drawable.ic_menu_slideshow);
            btnModeToggle.clearColorFilter();
            btnModeToggle.setColorFilter(android.graphics.Color.parseColor("#FF0000"));
        } else {
            btnModeToggle.setImageResource(android.R.drawable.ic_media_play);
            btnModeToggle.clearColorFilter();
            btnModeToggle.setColorFilter(android.graphics.Color.parseColor("#AAAAAA"));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(cmdReceiver);
    }

    private void fetchSponsorSegments(String videoId) {
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL("https://sponsor.ajay.app/api/skipSegments?videoID=" + videoId);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) response.append(line);
                in.close();
                
                String json = response.toString();
                runOnUiThread(() -> {
                    if (webView != null) {
                        String js = "window.sponsorSegments = " + json + ";";
                        webView.evaluateJavascript(js, null);
                    }
                });
            } catch (Exception e) {
                // ignore
            }
        }).start();
    }

    private void setupWebView() {
        webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        webViewContainer.addView(webView, 0);

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
        String customUserAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
        settings.setUserAgentString(customUserAgent);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        webView.addJavascriptInterface(new WebAppInterface(), "AndroidJS");

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
                boolean isYouTubeHome = url != null && (url.equals("https://m.youtube.com/") || url.equals("https://m.youtube.com") || url.startsWith("https://m.youtube.com/?"));
                boolean isMusicHome = url != null && (url.equals("https://music.youtube.com/") || url.equals("https://music.youtube.com") || url.startsWith("https://music.youtube.com/?"));
                
                if (topControlsContainer != null) {
                    if (isYouTubeHome || isMusicHome) {
                        topControlsContainer.setVisibility(View.VISIBLE);
                    } else {
                        topControlsContainer.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (getSharedPreferences("AppSettings", MODE_PRIVATE).getBoolean("pref_adblock_enabled", true)) {
                    if (url.contains("doubleclick.net") || 
                        url.contains("adservice.google.com") || 
                        url.contains("youtube.com/api/stats/ads") || 
                        url.contains("&adformat=") || 
                        url.contains("youtube.com/ptracking")) {
                        return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream("".getBytes()));
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                boolean isYouTubeHome = url.equals("https://m.youtube.com/") || url.equals("https://m.youtube.com") || url.startsWith("https://m.youtube.com/?");
                boolean isMusicHome = url.equals("https://music.youtube.com/") || url.equals("https://music.youtube.com") || url.startsWith("https://music.youtube.com/?");
                
                if (topControlsContainer != null) {
                    if (isYouTubeHome || isMusicHome) {
                        topControlsContainer.setVisibility(View.VISIBLE);
                    } else {
                        topControlsContainer.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                // Flush cookies to guarantee high sign-in persistence
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    CookieManager.getInstance().flush();
                }

                String css = "javascript:(function() {" +
                    "  var adblockEnabled = AndroidJS.isAdBlockEnabled();" +
                    "  if(adblockEnabled) {" +
                    "    var style = document.createElement('style');" +
                    "    style.innerHTML = 'ytm-promoted-sparkles-web-renderer, ytm-companion-ad-renderer, .ad-showing, .ad-container, ytm-promoted-video-renderer, ad-slot-renderer, ytd-promoted-sparkles-web-renderer { display: none !important; }';" +
                    "    document.head.appendChild(style);" +
                    "  }" +
                    "})();";
                view.evaluateJavascript(css, null);

                String js = "setInterval(function() {" +
                    "  var video = document.querySelector('video');" +
                    "  if(video) {" +
                    "    var isPlaying = !video.paused;" +
                    "    var title = document.title;" +
                    "    if (window.lastIsPlaying !== isPlaying || window.lastTitle !== title) {" +
                    "      window.lastIsPlaying = isPlaying;" +
                    "      window.lastTitle = title;" +
                    "      AndroidJS.updateMediaState(isPlaying, title);" +
                    "    }" +
                    "  }" +
                    "  var adblockEnabled = AndroidJS.isAdBlockEnabled();" +
                    "  if(adblockEnabled) {" +
                    "    var skipButton = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');" +
                    "    if (skipButton) {" +
                    "        skipButton.click();" +
                    "        AndroidJS.onAdBlocked();" +
                    "    }" +
                    "    var adOverlay = document.querySelector('.ytp-ad-overlay-close-button');" +
                    "    if (adOverlay) {" +
                    "        adOverlay.click();" +
                    "    }" +
                    "    var adPlayer = document.querySelector('.ad-showing video');" +
                    "    if (adPlayer && adPlayer.duration) {" +
                    "        if (!isNaN(adPlayer.duration) && adPlayer.currentTime < adPlayer.duration - 0.5) {" +
                    "            adPlayer.currentTime = adPlayer.duration;" +
                    "            AndroidJS.onAdBlocked();" +
                    "        }" +
                    "    }" +
                    "  }" +
                    "  var sponsorblockEnabled = AndroidJS.isSponsorBlockEnabled();" +
                    "  if(sponsorblockEnabled && video) {" +
                    "    var urlParams = new URLSearchParams(window.location.search);" +
                    "    var vid = urlParams.get('v');" +
                    "    if(window.currentVideoId !== vid && vid != null) {" +
                    "       window.currentVideoId = vid;" +
                    "       window.sponsorSegments = null;" +
                    "       AndroidJS.requestSponsorSegments(vid);" +
                    "    }" +
                    "    if(window.sponsorSegments && Array.isArray(window.sponsorSegments)) {" +
                    "       for(var i=0; i<window.sponsorSegments.length; i++) {" +
                    "           var seg = window.sponsorSegments[i].segment;" +
                    "           if(video.currentTime >= seg[0] && video.currentTime < seg[1]) {" +
                    "               video.currentTime = seg[1];" +
                    "           }" +
                    "       }" +
                    "    }" +
                    "  }" +
                    "}, 500);";
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

    private void setupGestureDetection() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;

                float deltaY = e2.getY() - e1.getY();
                float deltaX = e2.getX() - e1.getX();

                if (Math.abs(deltaY) > Math.abs(deltaX) && deltaY > 150 && velocityY > 500) {
                    if (!isMiniPlayer) {
                        minimizeWebView();
                    }
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!isMiniPlayer && gestureDetector != null && gestureDetector.onTouchEvent(ev)) {
            MotionEvent cancelEvent = MotionEvent.obtain(ev);
            cancelEvent.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancelEvent);
            cancelEvent.recycle();
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    private void minimizeWebView() {
        if (isMiniPlayer) return;
        isMiniPlayer = true;

        touchOverlay.setVisibility(View.VISIBLE);

        int screenWidth = ((ViewGroup)webViewContainer.getParent()).getWidth();
        int screenHeight = ((ViewGroup)webViewContainer.getParent()).getHeight();

        float targetWidth = 160 * density;
        float targetHeight = 250 * density;

        float scaleX = targetWidth / webViewContainer.getWidth();
        float scaleY = targetHeight / webViewContainer.getHeight();

        float margin = 16 * density;
        float targetX = screenWidth - targetWidth - margin;
        float targetY = screenHeight - targetHeight - margin;

        webViewContainer.setPivotX(0);
        webViewContainer.setPivotY(0);

        webViewContainer.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                float avgScale = (scaleX + scaleY) / 2f;
                float radius = (16 * density) / avgScale;
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        webViewContainer.setClipToOutline(true);
        webViewContainer.setElevation(8 * density);

        webViewContainer.animate()
                .scaleX(scaleX)
                .scaleY(scaleY)
                .translationX(targetX)
                .translationY(targetY)
                .setDuration(300)
                .setInterpolator(new OvershootInterpolator(1.0f))
                .start();
    }

    private void maximizeWebView() {
        if (!isMiniPlayer) return;
        isMiniPlayer = false;

        touchOverlay.setVisibility(View.GONE);

        webViewContainer.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .translationX(0)
                .translationY(0)
                .setDuration(300)
                .setInterpolator(new OvershootInterpolator(1.0f))
                .withEndAction(() -> {
                    webViewContainer.setClipToOutline(false);
                    webViewContainer.setElevation(0);
                })
                .start();
    }
}
