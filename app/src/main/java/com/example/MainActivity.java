package com.example;

import android.app.PictureInPictureParams;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;

public class MainActivity extends ComponentActivity {

    private WebView webViewYouTube;
    private WebView webViewMusic;
    private boolean isMusicMode = false;

    private FrameLayout fullscreenContainer;
    private FrameLayout webViewContainer;
    private float density;
    
    private ImageButton btnModeToggle;
    private ImageButton btnBack;
    private LinearLayout topControlsContainer;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private BroadcastReceiver cmdReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String act = intent.getAction();
            if (act != null) {
                WebView currentWebView = getCurrentWebView();
                if (currentWebView != null) {
                    if (act.equals(MediaPlaybackService.ACTION_CMD_PLAY)) {
                        currentWebView.evaluateJavascript("document.querySelector('video').play();", null);
                    } else if (act.equals(MediaPlaybackService.ACTION_CMD_PAUSE)) {
                        currentWebView.evaluateJavascript("document.querySelector('video').pause();", null);
                    } else if (act.equals(MediaPlaybackService.ACTION_CMD_NEXT)) {
                        currentWebView.evaluateJavascript("document.querySelector('.next-button').click();", null);
                    } else if (act.equals(MediaPlaybackService.ACTION_CMD_PREV)) {
                        currentWebView.evaluateJavascript("document.querySelector('.previous-button').click();", null);
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Workaround for Chromium Code Cache warning: ensure these exist before WebView init
        ensureCodeCacheDirs();

        setContentView(R.layout.activity_main);
        
        android.content.Intent serviceIntent = new android.content.Intent(this, MediaPlaybackService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        fullscreenContainer = findViewById(R.id.fullscreenContainer);
        webViewContainer = findViewById(R.id.webViewContainer);
        btnModeToggle = findViewById(R.id.btnModeToggle);
        btnBack = findViewById(R.id.btnBack);
        topControlsContainer = findViewById(R.id.topControlsContainer);

        density = getResources().getDisplayMetrics().density;

        btnBack.setOnClickListener(v -> {
            WebView currentWebView = getCurrentWebView();
            if (currentWebView != null && currentWebView.canGoBack()) {
                currentWebView.goBack();
                updateControlsState();
            }
        });
        btnModeToggle.setOnClickListener(v -> switchMode(!isMusicMode));

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

        setupWebView();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                WebView currentWebView = getCurrentWebView();
                if (customView != null) {
                    if (customViewCallback != null) {
                        customViewCallback.onCustomViewHidden();
                    }
                } else if (currentWebView != null && currentWebView.canGoBack()) {
                    currentWebView.goBack();
                    updateControlsState();
                } else {
                    finish();
                }
            }
        });

        if (savedInstanceState == null) {
            updateModeButtons();
        }
        updateControlsState();
    }

    private void switchMode(boolean toMusic) {
        if (isMusicMode == toMusic) return;

        isMusicMode = toMusic;
        updateModeButtons();
        
        if (isMusicMode) {
            webViewYouTube.setVisibility(View.GONE);
            webViewMusic.setVisibility(View.VISIBLE);
        } else {
            webViewMusic.setVisibility(View.GONE);
            webViewYouTube.setVisibility(View.VISIBLE);
        }
        updateControlsState();
    }

    private void updateModeButtons() {
        if (isMusicMode) {
            btnModeToggle.setImageResource(android.R.drawable.ic_media_pause);
        } else {
            btnModeToggle.setImageResource(android.R.drawable.ic_media_play);
        }
    }

    private void updateControlsState() {
        runOnUiThread(() -> {
            WebView currentWebView = getCurrentWebView();
            if (currentWebView != null) {
                boolean canGoBack = currentWebView.canGoBack();
                if (canGoBack) {
                    btnBack.setVisibility(View.VISIBLE);
                    btnModeToggle.setVisibility(View.GONE);
                } else {
                    btnBack.setVisibility(View.GONE);
                    btnModeToggle.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private WebView getCurrentWebView() {
        return isMusicMode ? webViewMusic : webViewYouTube;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(cmdReceiver);
    }

    private void enterPipModeWrapper() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PictureInPictureParams.Builder pipBuilder = new PictureInPictureParams.Builder();
                enterPictureInPictureMode(pipBuilder.build());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        boolean isPremium = true; 
        boolean backgroundPlayEnabled = true;
        
        if (backgroundPlayEnabled) {
            return;
        }

        if (isPremium) {
            try {
                enterPipModeWrapper();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            topControlsContainer.setVisibility(View.GONE);
        } else {
            topControlsContainer.setVisibility(View.VISIBLE);
        }
    }

    private void setupWebView() {
        webViewYouTube = createConfiguredWebView("https://m.youtube.com");
        webViewMusic = createConfiguredWebView("https://music.youtube.com");
        
        webViewContainer.addView(webViewYouTube);
        webViewContainer.addView(webViewMusic);

        webViewMusic.setVisibility(View.GONE);
        webViewYouTube.setVisibility(View.VISIBLE);
    }

    private WebView createConfiguredWebView(String initialUrl) {
        WebView webView = new WebView(this);
        ensureCodeCacheDirs();
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        webView.setLayoutParams(layoutParams);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        String userAgentString = webSettings.getUserAgentString();
        userAgentString = userAgentString.replace("; wv", "");
        userAgentString = userAgentString.replace("Version/4.0 ", "");
        webSettings.setUserAgentString(userAgentString);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrlLoading(view, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrlLoading(view, request.getUrl().toString());
            }

            private boolean handleUrlLoading(WebView view, String url) {
                if (url.startsWith("intent://") || url.startsWith("android-app://")) {
                    try {
                        Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                        if (intent != null) {
                            startActivity(intent);
                            return true;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                ensureCodeCacheDirs();
                updateControlsState();
                injectScripts(view);
            }
            
            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                updateControlsState();
                injectScripts(view);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                boolean blockAds = true; // Always ON
                if (blockAds) {
                    String url = request.getUrl().toString().toLowerCase();
                    boolean isAd = false;
                    
                    if (url.contains("doubleclick") || 
                        url.contains("pagead") || 
                        url.contains("googleadservices") || 
                        url.contains("/ad_logic/") || 
                        url.contains("youtube.com/api/stats/ads") || 
                        url.contains("stats/ads") || 
                        url.contains("/ptracking") || 
                        url.contains("youtubei/v1/log_event") || 
                        url.contains("google-analytics") || 
                        url.contains("googlesyndication") || 
                        url.contains("adservice.google")) {
                        isAd = true;
                    } else if (url.contains("googlevideo.com/videoplayback") && 
                              (url.contains("adformat") || url.contains("oad=") || url.contains("dbm=") || url.contains("caub="))) {
                        isAd = true;
                    }
                    
                    if (isAd) {
                        android.content.SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);
                        int count = prefs.getInt("pref_ads_blocked_count", 0);
                        prefs.edit().putInt("pref_ads_blocked_count", count + 1).apply();
                        
                        return new WebResourceResponse("text/plain", "UTF-8", new java.io.ByteArrayInputStream("".getBytes()));
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                updateControlsState();
                injectScripts(view);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                super.onShowCustomView(view, callback);
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }

                customView = view;
                customViewCallback = callback;
                
                webViewContainer.setVisibility(View.GONE);
                fullscreenContainer.setVisibility(View.VISIBLE);
                fullscreenContainer.addView(customView);
                
                getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | 
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | 
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                );
            }

            @Override
            public void onHideCustomView() {
                super.onHideCustomView();
                if (customView == null) {
                    return;
                }
                
                fullscreenContainer.setVisibility(View.GONE);
                fullscreenContainer.removeView(customView);
                webViewContainer.setVisibility(View.VISIBLE);
                
                customView = null;
                customViewCallback = null;
                
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
            
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                return false;
            }
        });

        webView.loadUrl(initialUrl);
        return webView;
    }

    private void injectScripts(WebView webView) {
        boolean backgroundPlayEnabled = getSharedPreferences("AppSettings", MODE_PRIVATE).getBoolean("pref_backgroundplay_enabled", true);
        boolean adblockEnabled = true; // Always ON
        
        StringBuilder jsBuilder = new StringBuilder();
        jsBuilder.append("javascript:(function() { ");
        
        if (backgroundPlayEnabled) {
            jsBuilder.append("document.addEventListener('visibilitychange', function(e) { e.stopPropagation(); }, true); ");
            jsBuilder.append("document.addEventListener('webkitvisibilitychange', function(e) { e.stopPropagation(); }, true); ");
        }
        
        if (adblockEnabled) {
            // CSS styles to hide ads immediately
            jsBuilder.append("if (!document.getElementById('adblock-styles')) { ")
                .append("  var style = document.createElement('style'); ")
                .append("  style.id = 'adblock-styles'; ")
                .append("  style.innerHTML = '.ad-container, .ad-div, #masthead-ad, .ad-image, .ytd-carousel-ad-render, .ad-placement, .ytp-ad-overlay-container, #player-ads, .ytp-ad-message-container, .ytmusic-carousel-shelf-basic-renderer .ytmusic-ad-banner, #ad-slot, .ytp-ad-progress-list, .ytp-ad-player-overlay-instream-card, ytmusic-mealbar-promo-renderer, ytd-mealbar-promo-renderer, .video-ads, .ytp-ad-overlay-slot { display: none !important; }'; ")
                .append("  document.head.appendChild(style); ")
                .append("} ");
            
            // Continuous loop to check, speed up, and skip video ads
            jsBuilder.append("if (!window.adblockIntervalStarted) { ")
                .append("  window.adblockIntervalStarted = true; ")
                .append("  setInterval(function() { ")
                .append("    var isAdActive = document.querySelector('.ad-showing, .ad-interrupting, .ytp-ad-player-overlay, .ytp-ad-overlay-open, .ytp-ad-player-overlay-layout'); ")
                .append("    var video = document.querySelector('video'); ")
                .append("    if (isAdActive && video) { ")
                .append("      video.muted = true; ")
                .append("      video.playbackRate = 16.0; ")
                .append("      if (isFinite(video.duration) && video.duration > 0) { ")
                .append("        video.currentTime = video.duration - 0.1; ")
                .append("      } ")
                .append("    } ")
                .append("    var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-ad-skip-button-text, .ytp-skip-ad-button, .ytp-ad-skip-button-slot'); ")
                .append("    if (skipBtn) { ")
                .append("      skipBtn.click(); ")
                .append("    } ")
                .append("  }, 150); ")
                .append("} ");
        }
        
        jsBuilder.append("})()");
        
        webView.evaluateJavascript(jsBuilder.toString(), null);
    }
    
    private void ensureCodeCacheDirs() {
        try {
            java.io.File jsDir = new java.io.File(getCacheDir(), "WebView/Default/HTTP Cache/Code Cache/js");
            if (!jsDir.exists()) {
                jsDir.mkdirs();
            }
            java.io.File jsNoMedia = new java.io.File(jsDir, ".nomedia");
            if (!jsNoMedia.exists()) {
                jsNoMedia.createNewFile();
            }

            java.io.File wasmDir = new java.io.File(getCacheDir(), "WebView/Default/HTTP Cache/Code Cache/wasm");
            if (!wasmDir.exists()) {
                wasmDir.mkdirs();
            }
            java.io.File wasmNoMedia = new java.io.File(wasmDir, ".nomedia");
            if (!wasmNoMedia.exists()) {
                wasmNoMedia.createNewFile();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }
}
