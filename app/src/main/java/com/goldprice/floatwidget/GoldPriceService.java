package com.goldprice.floatwidget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class GoldPriceService extends Service {

    private static final String TAG = "GoldPriceService";
    private static final String CHANNEL_ID = "gold_price_channel";
    private static final String ALERT_CHANNEL_ID = "gold_alert_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int ALERT_NOTIFICATION_ID = 2001;

    public static boolean isRunning = false;
    private FloatingWindowManager floatingWindowManager;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            Notification notification = createNotification("金价播报服务运行中");
            startForeground(NOTIFICATION_ID, notification);
            isRunning = true;

            if (floatingWindowManager == null) {
                floatingWindowManager = new FloatingWindowManager(this, this::sendPriceAlert);
            }
            floatingWindowManager.start();
        } catch (Exception e) {
            Log.e(TAG, "Error starting service", e);
            stopSelf();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (floatingWindowManager != null) {
            floatingWindowManager.stop();
            floatingWindowManager = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "金价播报", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("实时黄金价格悬浮播报");
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);

            NotificationChannel alertChannel = new NotificationChannel(
                    ALERT_CHANNEL_ID, "金价提醒", NotificationManager.IMPORTANCE_HIGH);
            alertChannel.setDescription("金价到达目标价格时提醒");
            manager.createNotificationChannel(alertChannel);
        }
    }

    private Notification createNotification(String text) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("💰 金价播报")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_gold)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    public void updateNotification(String priceInfo) {
        try {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, createNotification(priceInfo));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating notification", e);
        }
    }

    private void sendPriceAlert(String title, String message) {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                    intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            Notification notification = new NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setSmallIcon(R.drawable.ic_gold)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .build();

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(ALERT_NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending alert", e);
        }
    }
}
