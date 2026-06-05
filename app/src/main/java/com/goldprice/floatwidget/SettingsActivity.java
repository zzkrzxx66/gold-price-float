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
        setupCompactMode();
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    private void setupRefreshInterval() {
        RadioGroup rg = findViewById(R.id.rg_refresh);
        TextView tvLabel = findViewById(R.id.tv_refresh_label);
        int current = settings.getRefreshInterval();
        tvLabel.setText("刷新频率（当前: " + current + "秒）");

        if (current <= 15) rg.check(R.id.rb_15s);
        else if (current <= 30) rg.check(R.id.rb_30s);
        else if (current <= 60) rg.check(R.id.rb_60s);
        else rg.check(R.id.rb_300s);

        rg.setOnCheckedChangeListener((group, checkedId) -> {
            int interval = checkedId == R.id.rb_15s ? 15
                    : checkedId == R.id.rb_60s ? 60
                    : checkedId == R.id.rb_300s ? 300 : 30;
            settings.setRefreshInterval(interval);
            tvLabel.setText("刷新频率（当前: " + interval + "秒）");
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
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                tvLabel.setText("透明度: " + Math.max(20, progress) + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {
                settings.setOpacity(Math.max(20, sb.getProgress()));
            }
        });
    }

    private void setupLondonToggle() {
        Switch sw = findViewById(R.id.sw_london);
        sw.setChecked(settings.isShowLondon());
        sw.setOnCheckedChangeListener((v, checked) -> settings.setShowLondon(checked));
    }

    private void setupAlert() {
        Switch swAlert = findViewById(R.id.sw_alert);
        EditText etAbove = findViewById(R.id.et_alert_above);
        EditText etBelow = findViewById(R.id.et_alert_below);
        View alertContainer = findViewById(R.id.layout_alert_settings);

        swAlert.setChecked(settings.isAlertEnabled());
        alertContainer.setVisibility(settings.isAlertEnabled() ? View.VISIBLE : View.GONE);

        double above = settings.getAlertPriceAbove();
        double below = settings.getAlertPriceBelow();
        if (above > 0) etAbove.setText(String.valueOf(above));
        if (below > 0) etBelow.setText(String.valueOf(below));

        swAlert.setOnCheckedChangeListener((v, checked) -> {
            settings.setAlertEnabled(checked);
            alertContainer.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (checked) saveAlertPrices(etAbove, etBelow);
        });

        Button btnSave = findViewById(R.id.btn_save_alert);
        btnSave.setOnClickListener(v -> saveAlertPrices(etAbove, etBelow));
    }

    private void saveAlertPrices(EditText etAbove, EditText etBelow) {
        try {
            String aboveStr = etAbove.getText().toString().trim();
            String belowStr = etBelow.getText().toString().trim();
            settings.setAlertPriceAbove(aboveStr.isEmpty() ? 0 : Double.parseDouble(aboveStr));
            settings.setAlertPriceBelow(belowStr.isEmpty() ? 0 : Double.parseDouble(belowStr));
            Toast.makeText(this, "提醒价格已保存", Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "请输入有效数字", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupCompactMode() {
        Switch sw = findViewById(R.id.sw_compact);
        sw.setChecked(settings.isCompactMode());
        sw.setOnCheckedChangeListener((v, checked) -> settings.setCompactMode(checked));
    }
}
