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
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FloatingWindowManager {

    private static final String TAG = "FloatingWindow";
    private static final int REFRESH_INTERVAL_MS = 30000; // 30 seconds
    private static final int COLLAPSE_DELAY_MS = 5000;   // 5 seconds

    private final Context context;
    private final WindowManager windowManager;
    private final Handler handler;
    private final PriceFetcher priceFetcher;

    private View floatingView;
    private WindowManager.LayoutParams params;
    private boolean isExpanded = false;
    private boolean isRunning = false;

    // Collapsed views
    private View collapsedView;
    private TextView tvCollapsedPrice;
    private TextView tvCollapsedLabel;

    // Expanded views
    private View expandedView;
    private TextView tvAuPrice;
    private TextView tvAuChange;
    private TextView tvAuHigh;
    private TextView tvAuLow;
    private TextView tvUpdateTime;
    private TextView tvExpandLabel;

    private float initialTouchX, initialTouchY;
    private int initialX, initialY;
    private boolean isDragging = false;

    public FloatingWindowManager(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.handler = new Handler(Looper.getMainLooper());
        this.priceFetcher = new PriceFetcher();
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;
        createFloatingView();
        startPriceUpdates();
    }

    public void stop() {
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
        if (floatingView != null && floatingView.isAttachedToWindow()) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception e) {
                Log.e(TAG, "Error removing view", e);
            }
        }
    }

    private void createFloatingView() {
        floatingView = LayoutInflater.from(context).inflate(R.layout.floating_widget, null);

        // Find views
        collapsedView = floatingView.findViewById(R.id.collapsed_view);
        expandedView = floatingView.findViewById(R.id.expanded_view);
        tvCollapsedPrice = floatingView.findViewById(R.id.tv_collapsed_price);
        tvCollapsedLabel = floatingView.findViewById(R.id.tv_collapsed_label);
        tvAuPrice = floatingView.findViewById(R.id.tv_au_price);
        tvAuChange = floatingView.findViewById(R.id.tv_au_change);
        tvAuHigh = floatingView.findViewById(R.id.tv_au_high);
        tvAuLow = floatingView.findViewById(R.id.tv_au_low);
        tvUpdateTime = floatingView.findViewById(R.id.tv_update_time);
        tvExpandLabel = floatingView.findViewById(R.id.tv_expand_label);
        ImageView btnClose = floatingView.findViewById(R.id.btn_close);
        ImageView btnRefresh = floatingView.findViewById(R.id.btn_refresh);

        // Window params
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

        windowManager.addView(floatingView, params);

        // Touch handling for drag and click
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int lastAction;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        lastAction = MotionEvent.ACTION_DOWN;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - initialTouchX;
                        float dy = event.getRawY() - initialTouchY;
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isDragging = true;
                            params.x = initialX + (int) dx;
                            params.y = initialY + (int) dy;
                            windowManager.updateViewLayout(floatingView, params);
                            lastAction = MotionEvent.ACTION_MOVE;
                        }
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

        // Close button
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> {
                GoldPriceService service = (GoldPriceService) context;
                service.stopSelf();
            });
        }

        // Refresh button
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> fetchAndUpdatePrice());
        }

        // Start collapsed
        showCollapsed();
    }

    private void toggleExpandCollapse() {
        if (isExpanded) {
            showCollapsed();
        } else {
            showExpanded();
            // Auto-collapse after delay
            handler.postDelayed(this::showCollapsed, COLLAPSE_DELAY_MS);
        }
    }

    private void showCollapsed() {
        if (collapsedView != null) collapsedView.setVisibility(View.VISIBLE);
        if (expandedView != null) expandedView.setVisibility(View.GONE);
        isExpanded = false;
    }

    private void showExpanded() {
        if (collapsedView != null) collapsedView.setVisibility(View.GONE);
        if (expandedView != null) expandedView.setVisibility(View.VISIBLE);
        isExpanded = true;
    }

    private void startPriceUpdates() {
        fetchAndUpdatePrice();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isRunning) {
                    fetchAndUpdatePrice();
                    handler.postDelayed(this, REFRESH_INTERVAL_MS);
                }
            }
        }, REFRESH_INTERVAL_MS);
    }

    private void fetchAndUpdatePrice() {
        priceFetcher.fetchPrice(new PriceFetcher.PriceCallback() {
            @Override
            public void onSuccess(PriceFetcher.GoldPrice price) {
                handler.post(() -> updateDisplay(price));
            }

            @Override
            public void onError(String error) {
                handler.post(() -> {
                    if (tvCollapsedPrice != null) {
                        tvCollapsedPrice.setText("--");
                    }
                    Log.w(TAG, "Price fetch error: " + error);
                });
            }
        });
    }

    private void updateDisplay(PriceFetcher.GoldPrice price) {
        String priceStr = String.format("%.2f", price.price);
        String changeStr;
        int changeColor;

        if (price.change >= 0) {
            changeStr = String.format("+%.2f ↑", price.change);
            changeColor = 0xFF4CAF50; // Green
        } else {
            changeStr = String.format("%.2f ↓", price.change);
            changeColor = 0xFFF44336; // Red
        }

        // Update collapsed view
        if (tvCollapsedPrice != null) {
            tvCollapsedPrice.setText("¥" + priceStr);
            tvCollapsedPrice.setTextColor(changeColor);
        }
        if (tvCollapsedLabel != null) {
            tvCollapsedLabel.setText("Au99.99");
        }

        // Update expanded view
        if (tvAuPrice != null) tvAuPrice.setText("¥" + priceStr + "/克");
        if (tvAuChange != null) {
            tvAuChange.setText(changeStr);
            tvAuChange.setTextColor(changeColor);
        }
        if (tvAuHigh != null) tvAuHigh.setText("最高: ¥" + String.format("%.2f", price.high));
        if (tvAuLow != null) tvAuLow.setText("最低: ¥" + String.format("%.2f", price.low));
        if (tvUpdateTime != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);
            tvUpdateTime.setText("更新: " + sdf.format(new Date()));
        }

        // Update notification
        String notifyText = String.format("Au99.99: ¥%.2f %s", price.price, changeStr);
        if (context instanceof GoldPriceService) {
            ((GoldPriceService) context).updateNotification(notifyText);
        }
    }
}
