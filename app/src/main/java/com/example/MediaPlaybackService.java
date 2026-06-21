package com.example;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.KeyEvent;

import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

public class MediaPlaybackService extends Service {

    private static final String CHANNEL_ID = "media_playback_channel";
    private static final int NOTIFICATION_ID = 1;

    public static final String ACTION_UPDATE_STATE = "com.example.UPDATE_MEDIA_STATE";
    public static final String ACTION_CMD_PLAY = "com.example.CMD_PLAY";
    public static final String ACTION_CMD_PAUSE = "com.example.CMD_PAUSE";
    public static final String ACTION_CMD_NEXT = "com.example.CMD_NEXT";
    public static final String ACTION_CMD_PREV = "com.example.CMD_PREV";

    private MediaSessionCompat mediaSession;
    private boolean isPlaying = false;
    private String currentTitle = "YouTube Music";

    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = focusChange -> {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                sendCmd(ACTION_CMD_PAUSE);
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                break;
        }
    };

    private BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_UPDATE_STATE.equals(intent.getAction())) {
                boolean newIsPlaying = intent.getBooleanExtra("isPlaying", false);
                String title = intent.getStringExtra("title");
                boolean titleChanged = title != null && !title.isEmpty() && !title.equals(currentTitle);
                boolean stateChanged = newIsPlaying != isPlaying;
                
                if (titleChanged || stateChanged) {
                    isPlaying = newIsPlaying;
                    if (title != null && !title.isEmpty()) {
                        currentTitle = title;
                    }
                    updateMediaSession();
                    updateNotification();
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        mediaSession = new MediaSessionCompat(this, "YTMediaSession");
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                sendCmd(ACTION_CMD_PLAY);
            }
            @Override
            public void onPause() {
                sendCmd(ACTION_CMD_PAUSE);
            }
            @Override
            public void onSkipToNext() {
                sendCmd(ACTION_CMD_NEXT);
            }
            @Override
            public void onSkipToPrevious() {
                sendCmd(ACTION_CMD_PREV);
            }
        });
        mediaSession.setActive(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, new IntentFilter(ACTION_UPDATE_STATE), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateReceiver, new IntentFilter(ACTION_UPDATE_STATE));
        }

        updateMediaSession();
    }

    private void sendCmd(String action) {
        sendBroadcast(new Intent(action));
    }

    private void requestAudioFocus() {
        if (audioManager == null) {
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        }
        if (audioManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest == null) {
                AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build();
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(playbackAttributes)
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener(audioFocusChangeListener)
                        .build();
            }
            audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    private void abandonAudioFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            }
        } else {
            audioManager.abandonAudioFocus(audioFocusChangeListener);
        }
    }

    private void updateMediaSession() {
        int state = isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        PlaybackStateCompat playbackState = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .build();
        mediaSession.setPlaybackState(playbackState);

        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "YouTube")
                .build();
        mediaSession.setMetadata(metadata);

        if (isPlaying) {
            requestAudioFocus();
        } else {
            abandonAudioFocus();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        updateNotification();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(stateReceiver);
        abandonAudioFocus();
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Media Playback Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private void updateNotification() {
        Notification notification = getNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification getNotification() {
        Intent playIntent = new Intent(this, MediaPlaybackService.class);
        playIntent.setAction("ACTION_PLAY_PAUSE");
        // Not actually used directly since we use MediaStyle standard actions, 
        // but we'll use media buttons

        PendingIntent playPausePendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(isPlaying ? ACTION_CMD_PAUSE : ACTION_CMD_PLAY), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent nextPendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_CMD_NEXT), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent prevPendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_CMD_PREV), PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Action playPauseAction = new NotificationCompat.Action.Builder(
                isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                isPlaying ? "Pause" : "Play", playPausePendingIntent).build();

        NotificationCompat.Action prevAction = new NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_previous, "Previous", prevPendingIntent).build();

        NotificationCompat.Action nextAction = new NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_next, "Next", nextPendingIntent).build();

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(currentTitle)
                .setContentText("YouTube")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .addAction(prevAction)
                .addAction(playPauseAction)
                .addAction(nextAction)
                .setStyle(new MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .build();
    }
}
