package com.example;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

public class MediaPlaybackService extends Service {

    private static final String CHANNEL_ID = "media_playback_channel";
    private static final int NOTIFICATION_ID = 1;

    private Context serviceContext;

    @Override
    public String getAttributionTag() {
        return "ytplayer_attribution";
    }

    @Override
    public void onCreate() {
        super.onCreate();
        serviceContext = this;
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, getNotification(), 2 /* FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK */);
        } else {
            startForeground(NOTIFICATION_ID, getNotification());
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannelHelper.createChannel(serviceContext, CHANNEL_ID);
        }
    }

    private static class NotificationChannelHelper {
        @android.annotation.TargetApi(Build.VERSION_CODES.O)
        static void createChannel(Context context, String channelId) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    channelId,
                    "Media Playback Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private Notification getNotification() {
        return new NotificationCompat.Builder(serviceContext, CHANNEL_ID)
                .setContentTitle("Playing Media")
                .setContentText("Background playback active")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
    }
}
