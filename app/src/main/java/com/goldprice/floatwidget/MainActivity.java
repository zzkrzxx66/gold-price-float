package com.goldprice.floatwidget;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int OVERLAY_PERMISSION_CODE = 1001;
    private static final int NOTIFICATION_PERMISSION_CODE = 1002;

    private Button btnToggle;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnToggle = findViewById(R.id.btn_toggle);
        tvStatus = findViewById(R.id.tv_status);
        Button btnSettings = findViewById(R.id.btn_settings);

        btnToggle.setOnClickListener(v -> toggleService());
        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });

        requestPermissions();
        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_CODE);
            }
        }
    }

    private void toggleService() {
        if (GoldPriceService.isRunning) {
            stopFloatingService();
        } else {
            if (!Settings.canDrawOverlays(this)) {
                requestOverlayPermission();
            } else {
                startFloatingService();
            }
        }
    }

    private void requestOverlayPermission() {
        Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, OVERLAY_PERMISSION_CODE);
    }

    private void startFloatingService() {
        try {
            Intent serviceIntent = new Intent(this, GoldPriceService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            updateUI();
            Toast.makeText(this, "金价播报已启动", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "启动失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopFloatingService() {
        try {
            Intent serviceIntent = new Intent(this, GoldPriceService.class);
            stopService(serviceIntent);
            updateUI();
            Toast.makeText(this, "金价播报已停止", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "停止失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void updateUI() {
        boolean running = GoldPriceService.isRunning;
        if (running) {
            btnToggle.setText("停止播报");
            btnToggle.setBackgroundResource(R.drawable.bg_secondary_button);
            btnToggle.setTextColor(ContextCompat.getColor(this, R.color.gold));
            tvStatus.setText("状态：运行中 ●");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.running_green));
        } else {
            btnToggle.setText("开始播报");
            btnToggle.setBackgroundResource(R.drawable.bg_primary_button);
            btnToggle.setTextColor(0xFF281700);
            tvStatus.setText("状态：已停止");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.stopped_gray));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_CODE) {
            if (Settings.canDrawOverlays(this)) {
                startFloatingService();
            } else {
                Toast.makeText(this, "需要悬浮窗权限才能显示浮动金价", Toast.LENGTH_LONG).show();
            }
        }
    }
}
