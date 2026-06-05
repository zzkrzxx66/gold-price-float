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
    private static final int NOTIFICATION_ID = 1001;

    public static boolean isRunning = false;
    private FloatingWindowManager floatingWindowManager;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            Notification notification = createNotification("金价播报服务运行中");
            startForeground(NOTIFICATION_ID, notification);
            isRunning = true;

            if (floatingWindowManager == null) {
                floatingWindowManager = new FloatingWindowManager(this);
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

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "金价播报",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("实时黄金价格悬浮播报");
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
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
}
