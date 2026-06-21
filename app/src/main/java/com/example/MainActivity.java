package com.example;

import android.app.PictureInPictureParams;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
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

import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView;

public class MainActivity extends ComponentActivity {

    private WebView webViewYouTube;
    private WebView webViewMusic;
    private boolean isMusicMode = false;

    private FrameLayout fullscreenContainer;
    private FrameLayout webViewContainer;
    private FrameLayout nativePlayerContainer;
    private YouTubePlayerView youtubePlayerView;
    private YouTubePlayer currentYouTubePlayer;
    private View playerTouchOverlay;
    private GestureDetector gestureDetector;
    private boolean isNativeMiniPlayer = false;
    private float density;
    
    private ImageButton btnModeToggle;
    private ImageButton btnBack;
    private ImageButton btnSettings;
    private LinearLayout topControlsContainer;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private BroadcastReceiver cmdReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String act = intent.getAction();
            if (act != null) {
                if (nativePlayerContainer != null && nativePlayerContainer.getVisibility() == View.VISIBLE && currentYouTubePlayer != null) {
                    if (act.equals(MediaPlaybackService.ACTION_CMD_PLAY)) {
                        currentYouTubePlayer.play();
                    } else if (act.equals(MediaPlaybackService.ACTION_CMD_PAUSE)) {
                        currentYouTubePlayer.pause();
                    }
                } else {
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
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Workaround for Chromium Code Cache warning: ensure these exist before WebView init
        try {
            java.io.File jsDir = new java.io.File(getCacheDir(), "WebView/Default/HTTP Cache/Code Cache/js");
            jsDir.mkdirs();
            new java.io.File(jsDir, ".nomedia").createNewFile();
            java.io.File wasmDir = new java.io.File(getCacheDir(), "WebView/Default/HTTP Cache/Code Cache/wasm");
            wasmDir.mkdirs();
            new java.io.File(wasmDir, ".nomedia").createNewFile();
        } catch (Exception e) {}

        setContentView(R.layout.activity_main);
        
        android.content.Intent serviceIntent = new android.content.Intent(this, MediaPlaybackService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        fullscreenContainer = findViewById(R.id.fullscreenContainer);
        webViewContainer = findViewById(R.id.webViewContainer);
        nativePlayerContainer = findViewById(R.id.nativePlayerContainer);
        youtubePlayerView = findViewById(R.id.youtubePlayerView);
        playerTouchOverlay = findViewById(R.id.playerTouchOverlay);
        btnModeToggle = findViewById(R.id.btnModeToggle);
        btnBack = findViewById(R.id.btnBack);
        btnSettings = findViewById(R.id.btnSettings);
        topControlsContainer = findViewById(R.id.topControlsContainer);

        density = getResources().getDisplayMetrics().density;
        
        setupNativePlayer();

        btnBack.setOnClickListener(v -> {
            WebView currentWebView = getCurrentWebView();
            if (currentWebView != null && currentWebView.canGoBack()) {
                currentWebView.goBack();
                updateControlsState();
            }
        });
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

        setupWebView();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                WebView currentWebView = getCurrentWebView();
                if (customView != null) {
                    if (customViewCallback != null) {
                        customViewCallback.onCustomViewHidden();
                    }
                } else if (nativePlayerContainer != null && nativePlayerContainer.getVisibility() == View.VISIBLE) {
                    if (!isNativeMiniPlayer) {
                        minimizeNativePlayer();
                    } else {
                        if (currentYouTubePlayer != null) {
                            currentYouTubePlayer.pause();
                        }
                        nativePlayerContainer.setVisibility(View.GONE);
                        isNativeMiniPlayer = false;
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
        boolean isPremium = true; // Hardcoded or get from SharedPreferences
        boolean backgroundPlayEnabled = true;
        
        if (backgroundPlayEnabled) {
            return;
        }

        if (isPremium) {
            try {
                if (nativePlayerContainer != null && nativePlayerContainer.getVisibility() == View.VISIBLE) {
                    enterPipModeWrapper();
                } else {
                    enterPipModeWrapper();
                }
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
            if (nativePlayerContainer != null && nativePlayerContainer.getVisibility() == View.VISIBLE) {
                isNativeMiniPlayer = true;
                maximizeNativePlayer();
            }
        } else {
            topControlsContainer.setVisibility(View.VISIBLE);
            minimizeNativePlayer();
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
                updateControlsState();
            }
            
            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                updateControlsState();
                if (url.contains("watch?v=")) {
                    try {
                        android.net.Uri uri = android.net.Uri.parse(url);
                        String videoId = uri.getQueryParameter("v");
                        if (videoId != null && !videoId.isEmpty()) {
                            view.evaluateJavascript("document.querySelectorAll('video').forEach(v => v.pause());", null);
                            playInNativePlayer(videoId);
                            view.goBack();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                boolean blockAds = getSharedPreferences("AppSettings", MODE_PRIVATE).getBoolean("pref_adblock_enabled", true);
                if (blockAds) {
                    String url = request.getUrl().toString().toLowerCase();
                    if (url.contains("googleads.g.doubleclick.net") || 
                        url.contains("pagead2.googlesyndication.com") || 
                        url.contains("/ad_logic/") || 
                        url.contains("youtubei/v1/log_event") ||
                        url.contains("youtubei/v1/guide")) {
                        return new WebResourceResponse("text/plain", "UTF-8", new java.io.ByteArrayInputStream("".getBytes()));
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                updateControlsState();
                boolean isPrem = getSharedPreferences("AppSettings", MODE_PRIVATE).getBoolean("pref_premium_enabled", true);
                boolean bgPlay = getSharedPreferences("AppSettings", MODE_PRIVATE).getBoolean("pref_backgroundplay_enabled", true);
                if (isPrem || bgPlay) {
                    injectBackgroundPlayScript(view);
                }
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

    private void injectBackgroundPlayScript(WebView webView) {
        String js = "javascript:(function() { " +
                "document.addEventListener('visibilitychange', function(e) { " +
                "  e.stopPropagation(); " +
                "}, true); " +
                "document.addEventListener('webkitvisibilitychange', function(e) { " +
                "  e.stopPropagation(); " +
                "}, true); " +
                "})()";
        webView.evaluateJavascript(js, null);
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // deliberately leaving out currentWebView.saveState(outState) 
        // to avoid TransactionTooLargeException
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (nativePlayerContainer != null && nativePlayerContainer.getVisibility() == View.VISIBLE && !isNativeMiniPlayer && gestureDetector != null) {
            if (gestureDetector.onTouchEvent(ev)) {
                return true;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private void setupNativePlayer() {
        getLifecycle().addObserver(youtubePlayerView);
        youtubePlayerView.addYouTubePlayerListener(new AbstractYouTubePlayerListener() {
            @Override
            public void onReady(YouTubePlayer youTubePlayer) {
                currentYouTubePlayer = youTubePlayer;
            }
        });

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float deltaY = e2.getY() - e1.getY();
                float deltaX = e2.getX() - e1.getX();
                if (Math.abs(deltaY) > Math.abs(deltaX) && deltaY > 150 && velocityY > 500) {
                    if (!isNativeMiniPlayer) {
                        minimizeNativePlayer();
                    }
                    return true;
                }
                return false;
            }
        });

        playerTouchOverlay.setOnClickListener(v -> maximizeNativePlayer());
    }

    private void playInNativePlayer(String videoId) {
        if (currentYouTubePlayer != null) {
            currentYouTubePlayer.loadVideo(videoId, 0f);
        } else {
            youtubePlayerView.addYouTubePlayerListener(new AbstractYouTubePlayerListener() {
                @Override
                public void onReady(YouTubePlayer youTubePlayer) {
                    youTubePlayer.loadVideo(videoId, 0f);
                }
            });
        }
        nativePlayerContainer.setVisibility(View.VISIBLE);
        maximizeNativePlayer();
    }

    private void minimizeNativePlayer() {
        if (isNativeMiniPlayer) return;
        isNativeMiniPlayer = true;
        playerTouchOverlay.setVisibility(View.VISIBLE);

        int screenWidth = ((ViewGroup)nativePlayerContainer.getParent()).getWidth();
        int screenHeight = ((ViewGroup)nativePlayerContainer.getParent()).getHeight();

        float targetWidth = 160 * density;
        float targetHeight = 90 * density; // 16:9 ratio approximately

        float scaleX = targetWidth / nativePlayerContainer.getWidth();
        float scaleY = targetHeight / nativePlayerContainer.getHeight();

        float margin = 16 * density;
        float targetX = screenWidth - targetWidth - margin;
        float targetY = screenHeight - targetHeight - margin;

        nativePlayerContainer.setPivotX(0);
        nativePlayerContainer.setPivotY(0);

        nativePlayerContainer.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                float avgScale = (scaleX + scaleY) / 2f;
                float radius = (16 * density) / avgScale;
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        nativePlayerContainer.setClipToOutline(true);
        nativePlayerContainer.setElevation(8 * density);

        nativePlayerContainer.animate()
                .scaleX(scaleX)
                .scaleY(scaleY)
                .translationX(targetX)
                .translationY(targetY)
                .setDuration(300)
                .setInterpolator(new OvershootInterpolator(1.0f))
                .start();
    }

    private void maximizeNativePlayer() {
        if (!isNativeMiniPlayer) return;
        isNativeMiniPlayer = false;
        playerTouchOverlay.setVisibility(View.GONE);

        nativePlayerContainer.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .translationX(0)
                .translationY(0)
                .setDuration(300)
                .setInterpolator(new OvershootInterpolator(1.0f))
                .withEndAction(() -> {
                    nativePlayerContainer.setClipToOutline(false);
                    nativePlayerContainer.setElevation(0);
                })
                .start();
    }
}
