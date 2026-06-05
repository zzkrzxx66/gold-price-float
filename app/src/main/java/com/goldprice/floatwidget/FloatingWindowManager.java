package com.goldprice.floatwidget;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FloatingWindowManager {

    private static final String TAG = "FloatingWindowManager";

    public interface AlertCallback {
        void onAlert(String title, String message);
    }

    private final Context context;
    private final WindowManager windowManager;
    private final Handler handler;
    private final PriceFetcher priceFetcher;
    private final SettingsManager settings;
    private final AlertCallback alertCallback;

    private View floatingView;
    private WindowManager.LayoutParams params;

    private TextView tvCnyPrice;
    private TextView tvUsdPrice;
    private TextView tvChange;
    private LinearLayout layoutCollapsed;

    private TextView tvExpCny;
    private TextView tvExpUsd;
    private TextView tvExpChange;
    private TextView tvExpTime;
    private TextView tvExpCache;
    private LinearLayout layoutExpanded;

    private boolean isExpanded = false;
    private boolean isRunning = false;
    private boolean isDragging = false;
    private boolean alertTriggeredAbove = false;
    private boolean alertTriggeredBelow = false;
    private Runnable refreshRunnable;

    public FloatingWindowManager(Context context, AlertCallback alertCallback) {
        this.context = context;
        this.alertCallback = alertCallback;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.handler = new Handler(Looper.getMainLooper());
        this.priceFetcher = new PriceFetcher(context);
        this.settings = new SettingsManager(context);
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;
        createFloatingView();
        startAutoRefresh();
    }

    public void stop() {
        isRunning = false;
        if (refreshRunnable != null) handler.removeCallbacks(refreshRunnable);
        if (floatingView != null) {
            try { windowManager.removeView(floatingView); } catch (Exception ignored) {}
            floatingView = null;
        }
    }

    private void createFloatingView() {
        floatingView = LayoutInflater.from(context).inflate(R.layout.floating_widget, null);
        tvCnyPrice = floatingView.findViewById(R.id.tv_cny_price);
        tvUsdPrice = floatingView.findViewById(R.id.tv_usd_price);
        tvChange = floatingView.findViewById(R.id.tv_change);
        layoutCollapsed = floatingView.findViewById(R.id.layout_collapsed);
        tvExpCny = floatingView.findViewById(R.id.tv_exp_cny);
        tvExpUsd = floatingView.findViewById(R.id.tv_exp_usd);
        tvExpChange = floatingView.findViewById(R.id.tv_exp_change);
        tvExpTime = floatingView.findViewById(R.id.tv_exp_time);
        tvExpCache = floatingView.findViewById(R.id.tv_exp_cache);
        layoutExpanded = floatingView.findViewById(R.id.layout_expanded);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 20;
        params.y = 200;

        applyOpacity();
        applyLondonVisibility();
        setupTouchHandling();
        setupButtons();
        windowManager.addView(floatingView, params);
    }

    private void applyOpacity() {
        if (floatingView != null) floatingView.setAlpha(settings.getOpacity() / 100f);
    }

    private void applyLondonVisibility() {
        boolean show = settings.isShowLondon();
        if (tvUsdPrice != null) tvUsdPrice.setVisibility(show ? View.VISIBLE : View.GONE);
        if (tvExpUsd != null) tvExpUsd.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void setupTouchHandling() {
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private float startX, startY;
            private int startParamX, startParamY;
            private static final int THRESHOLD = 15;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        startY = event.getRawY();
                        startParamX = params.x;
                        startParamY = params.y;
                        isDragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - startX;
                        float dy = event.getRawY() - startY;
                        if (Math.abs(dx) > THRESHOLD || Math.abs(dy) > THRESHOLD) isDragging = true;
                        params.x = startParamX + (int) dx;
                        params.y = startParamY + (int) dy;
                        try { windowManager.updateViewLayout(floatingView, params); } catch (Exception ignored) {}
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!isDragging) toggleExpandCollapse();
                        return true;
                }
                return false;
            }
        });
    }

    private void setupButtons() {
        ImageView btnRefresh = floatingView.findViewById(R.id.btn_refresh);
        ImageView btnClose = floatingView.findViewById(R.id.btn_close);
        if (btnRefresh != null) btnRefresh.setOnClickListener(v -> fetchAndUpdatePrice());
        if (btnClose != null) btnClose.setOnClickListener(v -> {
            if (context instanceof GoldPriceService) ((GoldPriceService) context).stopSelf();
        });
    }

    private void toggleExpandCollapse() {
        isExpanded = !isExpanded;
        if (layoutExpanded != null) layoutExpanded.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        if (layoutCollapsed != null) layoutCollapsed.setVisibility(isExpanded ? View.GONE : View.VISIBLE);
    }

    private void startAutoRefresh() {
        fetchAndUpdatePrice();
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;
                applyOpacity();
                applyLondonVisibility();
                fetchAndUpdatePrice();
                handler.postDelayed(this, settings.getRefreshInterval() * 1000L);
            }
        };
        handler.postDelayed(refreshRunnable, settings.getRefreshInterval() * 1000L);
    }

    private void fetchAndUpdatePrice() {
        priceFetcher.fetchPrice(new PriceFetcher.PriceCallback() {
            @Override
            public void onSuccess(PriceFetcher.GoldPrice price) {
                handler.post(() -> {
                    if (!isRunning || floatingView == null) return;
                    updateDisplay(price);
                    checkPriceAlert(price);
                    if (context instanceof GoldPriceService) {
                        String status = price.fromCache ? "(离线) " : "";
                        ((GoldPriceService) context).updateNotification(
                                status + String.format("¥%.2f/克", price.cnyPerGram));
                    }
                });
            }

            @Override
            public void onError(String error) {
                handler.post(() -> {
                    if (!isRunning || floatingView == null) return;
                    if (tvCnyPrice != null) tvCnyPrice.setText("获取失败");
                    if (tvExpCny != null) tvExpCny.setText("获取失败: " + error);
                });
            }
        });
    }

    private void updateDisplay(PriceFetcher.GoldPrice price) {
        String cnyStr = String.format("¥%.2f/克", price.cnyPerGram);
        String usdStr = String.format("$%.2f/oz", price.usdPerOz);

        String changeStr;
        int changeColor;
        if (price.change > 0.001) {
            changeStr = String.format("+%.2f (+%.2f%%)", price.change, price.changePercent);
            changeColor = 0xFF4CAF50;
        } else if (price.change < -0.001) {
            changeStr = String.format("%.2f (%.2f%%)", price.change, price.changePercent);
            changeColor = 0xFFF44336;
        } else {
            changeStr = "—";
            changeColor = 0xFFAAAAAA;
        }

        if (tvCnyPrice != null) tvCnyPrice.setText(cnyStr);
        if (tvUsdPrice != null) tvUsdPrice.setText(usdStr);
        if (tvChange != null) {
            tvChange.setText(changeStr);
            tvChange.setTextColor(changeColor);
            blinkView(tvChange);
        }

        if (tvExpCny != null) tvExpCny.setText(cnyStr);
        if (tvExpUsd != null) tvExpUsd.setText("伦敦金 " + usdStr);
        if (tvExpChange != null) {
            tvExpChange.setText(changeStr);
            tvExpChange.setTextColor(changeColor);
        }
        if (tvExpTime != null) tvExpTime.setText("更新: " + price.updatedAt);
        if (tvExpCache != null) tvExpCache.setVisibility(price.fromCache ? View.VISIBLE : View.GONE);
    }

    private void blinkView(View view) {
        AlphaAnimation blink = new AlphaAnimation(1.0f, 0.3f);
        blink.setDuration(300);
        blink.setRepeatMode(Animation.REVERSE);
        blink.setRepeatCount(1);
        view.startAnimation(blink);
    }

    private void checkPriceAlert(PriceFetcher.GoldPrice price) {
        if (!settings.isAlertEnabled() || alertCallback == null) return;
        double above = settings.getAlertPriceAbove();
        double below = settings.getAlertPriceBelow();

        if (above > 0 && price.cnyPerGram >= above && !alertTriggeredAbove) {
            alertTriggeredAbove = true;
            alertCallback.onAlert("金价突破上限!",
                    String.format("国内金价已达 ¥%.2f/克，超过目标 ¥%.2f", price.cnyPerGram, above));
        } else if (above > 0 && price.cnyPerGram < above) {
            alertTriggeredAbove = false;
        }

        if (below > 0 && price.cnyPerGram <= below && !alertTriggeredBelow) {
            alertTriggeredBelow = true;
            alertCallback.onAlert("金价跌破下限!",
                    String.format("国内金价已跌至 ¥%.2f/克，低于目标 ¥%.2f", price.cnyPerGram, below));
        } else if (below > 0 && price.cnyPerGram > below) {
            alertTriggeredBelow = false;
        }
    }
}
