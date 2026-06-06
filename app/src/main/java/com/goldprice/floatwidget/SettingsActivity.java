package com.goldprice.floatwidget;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private SettingsManager settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        settings = new SettingsManager(this);
        setupRefreshInterval();
        setupOpacity();
        setupLondonToggle();
        setupAlert();
        setupBackButton();
    }

    private void setupRefreshInterval() {
        RadioGroup rg = findViewById(R.id.rg_refresh);
        TextView tvLabel = findViewById(R.id.tv_refresh_label);

        int current = settings.getRefreshInterval();
        tvLabel.setText("刷新频率（当前: " + current + "秒）");

        // 根据当前值选中对应 RadioButton
        if (current <= 15) rg.check(R.id.rb_15s);
        else if (current <= 30) rg.check(R.id.rb_30s);
        else if (current <= 60) rg.check(R.id.rb_60s);
        else rg.check(R.id.rb_300s);

        rg.setOnCheckedChangeListener((group, checkedId) -> {
            int interval;
            if (checkedId == R.id.rb_15s) interval = 15;
            else if (checkedId == R.id.rb_60s) interval = 60;
            else if (checkedId == R.id.rb_300s) interval = 300;
            else interval = 30;

            settings.setRefreshInterval(interval);
            tvLabel.setText("刷新频率（当前: " + interval + "秒）");
            Toast.makeText(this, "已设为 " + interval + " 秒刷新", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupOpacity() {
        SeekBar seekBar = findViewById(R.id.seek_opacity);
        TextView tvLabel = findViewById(R.id.tv_opacity_label);

        int current = settings.getOpacity();
        seekBar.setProgress(current);
        tvLabel.setText("透明度: " + current + "%");

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = Math.max(20, progress); // 最低 20% 可见
                tvLabel.setText("透明度: " + val + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int val = Math.max(20, seekBar.getProgress());
                settings.setOpacity(val);
            }
        });
    }

    private void setupLondonToggle() {
        Switch sw = findViewById(R.id.sw_london);
        sw.setChecked(settings.isShowLondon());
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.setShowLondon(isChecked);
        });
    }

    private void setupAlert() {
        Switch swAlert = findViewById(R.id.sw_alert);
        View alertPanel = findViewById(R.id.layout_alert_settings);
        EditText etAbove = findViewById(R.id.et_alert_above);
        EditText etBelow = findViewById(R.id.et_alert_below);
        Button btnSave = findViewById(R.id.btn_save_alert);

        if (swAlert == null || alertPanel == null || etAbove == null || etBelow == null || btnSave == null) return;

        boolean enabled = settings.isAlertEnabled();
        swAlert.setChecked(enabled);
        alertPanel.setVisibility(enabled ? View.VISIBLE : View.GONE);

        double above = settings.getAlertPriceAbove();
        double below = settings.getAlertPriceBelow();
        if (above > 0) etAbove.setText(String.valueOf(above));
        if (below > 0) etBelow.setText(String.valueOf(below));

        swAlert.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.setAlertEnabled(isChecked);
            alertPanel.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        btnSave.setOnClickListener(v -> {
            try {
                String a = etAbove.getText().toString().trim();
                String b = etBelow.getText().toString().trim();
                double aboveVal = a.isEmpty() ? 0 : Double.parseDouble(a);
                double belowVal = b.isEmpty() ? 0 : Double.parseDouble(b);
                settings.setAlertPriceAbove(aboveVal);
                settings.setAlertPriceBelow(belowVal);
                settings.setAlertEnabled(swAlert.isChecked());
                Toast.makeText(this, "提醒价格已保存", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "请输入有效数字", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupBackButton() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }
}
