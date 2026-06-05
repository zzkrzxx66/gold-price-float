package com.goldprice.floatwidget;

import android.animation.ValueAnimator;
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
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FloatingWindowManager {

    private static final String TAG = "FloatingWindowManager";

    private final Context context;
    private final WindowManager windowManager;
    private final Handler handler;
    private final PriceFetcher priceFetcher;
    private final SettingsManager settings;

    private View floatingView;
    private WindowManager.LayoutParams params;

    private TextView tvCnyPrice;
    private TextView tvUsdPrice;
    private TextView tvExpCny;
    private TextView tvExpUsd;
    private TextView tvExpTime;
    private LinearLayout layoutCollapsed;
    private LinearLayout layoutExpanded;

    private boolean isExpanded = false;
    private boolean isRunning = false;
    private boolean isDragging = false;

    private int refreshInterval = 30; // 秒
    private Runnable refreshRunnable;

    public FloatingWindowManager(Context context) {
        this.context = context;
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
        stopAutoRefresh();
        if (floatingView != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception e) {
                Log.e(TAG, "Error removing view", e);
            }
            floatingView = null;
        }
    }

    private void createFloatingView() {
        floatingView = LayoutInflater.from(context).inflate(R.layout.floating_widget, null);

        tvCnyPrice = floatingView.findViewById(R.id.tv_cny_price);
        tvUsdPrice = floatingView.findViewById(R.id.tv_usd_price);
        tvExpCny = floatingView.findViewById(R.id.tv_exp_cny);
        tvExpUsd = floatingView.findViewById(R.id.tv_exp_usd);
        tvExpTime = floatingView.findViewById(R.id.tv_exp_time);
        layoutCollapsed = floatingView.findViewById(R.id.layout_collapsed);
        layoutExpanded = floatingView.findViewById(R.id.layout_expanded);

        // 根据设置显示/隐藏伦敦金行
        tvUsdPrice.setVisibility(settings.isShowLondon() ? View.VISIBLE : View.GONE);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 20;
        params.y = 200;

        applyOpacity();
        setupTouchHandling();
        setupButtons();

        windowManager.addView(floatingView, params);
    }

    private void applyOpacity() {
        if (floatingView != null) {
            float alpha = settings.getOpacity() / 100f;
            floatingView.setAlpha(alpha);
        }
    }

    private void setupTouchHandling() {
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int lastAction;
            private float startX, startY;
            private int startParamX, startParamY;
            private static final int CLICK_THRESHOLD = 10;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        startY = event.getRawY();
                        startParamX = params.x;
                        startParamY = params.y;
                        isDragging = false;
                        lastAction = MotionEvent.ACTION_DOWN;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - startX;
                        float dy = event.getRawY() - startY;
                        if (Math.abs(dx) > CLICK_THRESHOLD || Math.abs(dy) > CLICK_THRESHOLD) {
                            isDragging = true;
                        }
                        params.x = startParamX + (int) dx;
                        params.y = startParamY + (int) dy;
                        windowManager.updateViewLayout(floatingView, params);
                        lastAction = MotionEvent.ACTION_MOVE;
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            toggleExpandCollapse();
                        }
                        lastAction = MotionEvent.ACTION_UP;
                        return true;
                }
                return false;
            }
        });
    }

    private void setupButtons() {
        ImageView btnRefresh = floatingView.findViewById(R.id.btn_refresh);
        ImageView btnClose = floatingView.findViewById(R.id.btn_close);

        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> fetchAndUpdatePrice());
        }
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> {
                if (context instanceof GoldPriceService) {
                    ((GoldPriceService) context).stopSelf();
                }
            });
        }
    }

    private void toggleExpandCollapse() {
        if (isExpanded) {
            showCollapsed();
        } else {
            showExpanded();
        }
    }

    private void showCollapsed() {
        isExpanded = false;
        if (layoutExpanded != null) layoutExpanded.setVisibility(View.GONE);
        if (layoutCollapsed != null) layoutCollapsed.setVisibility(View.VISIBLE);
    }

    private void showExpanded() {
        isExpanded = true;
        if (layoutCollapsed != null) layoutCollapsed.setVisibility(View.GONE);
        if (layoutExpanded != null) layoutExpanded.setVisibility(View.VISIBLE);
    }

    private void startAutoRefresh() {
        refreshInterval = settings.getRefreshInterval();
        fetchAndUpdatePrice();
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;
                refreshInterval = settings.getRefreshInterval();
                applyOpacity();
                fetchAndUpdatePrice();
                handler.postDelayed(this, refreshInterval * 1000L);
            }
        };
        handler.postDelayed(refreshRunnable, refreshInterval * 1000L);
    }

    private void stopAutoRefresh() {
        if (refreshRunnable != null) {
            handler.removeCallbacks(refreshRunnable);
        }
    }

    private void fetchAndUpdatePrice() {
        priceFetcher.fetchPrice(new PriceFetcher.PriceCallback() {
            @Override
            public void onSuccess(PriceFetcher.GoldPrice price) {
                handler.post(() -> {
                    if (!isRunning || floatingView == null) return;
                    updateDisplay(price);
                    if (context instanceof GoldPriceService) {
                        ((GoldPriceService) context).updateNotification(
                                String.format("¥%.2f/克", price.cnyPerGram));
                    }
                });
            }

            @Override
            public void onError(String error) {
                handler.post(() -> {
                    if (!isRunning || floatingView == null) return;
                    if (tvCnyPrice != null) tvCnyPrice.setText("加载失败");
                    if (tvUsdPrice != null) tvUsdPrice.setText(error);
                    if (tvExpCny != null) tvExpCny.setText("获取失败");
                    Log.w(TAG, "Price error: " + error);
                });
            }
        });
    }

    private void updateDisplay(PriceFetcher.GoldPrice price) {
        // 折叠态：国内金价（大字）+ 伦敦金（小字换行）
        if (tvCnyPrice != null) {
            tvCnyPrice.setText(String.format("¥%.2f/克", price.cnyPerGram));
        }
        if (tvUsdPrice != null) {
            tvUsdPrice.setText(String.format("$%.2f/oz", price.usdPerOz));
            tvUsdPrice.setVisibility(settings.isShowLondon() ? View.VISIBLE : View.GONE);
        }

        // 展开态
        if (tvExpCny != null) {
            tvExpCny.setText(String.format("¥%.2f/克", price.cnyPerGram));
        }
        if (tvExpUsd != null) {
            tvExpUsd.setText(String.format("伦敦金 $%.2f/盎司", price.usdPerOz));
            tvExpUsd.setVisibility(settings.isShowLondon() ? View.VISIBLE : View.GONE);
        }
        if (tvExpTime != null) {
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            tvExpTime.setText("更新: " + time);
        }
    }
}
