package com.goldprice.floatwidget;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class PriceFetcher {
    private static final String TAG = "PriceFetcher";
    private static final String PREF = "price_cache";
    private static final String API_GOLD_CNY = "https://api.gold-api.com/price/XAU/CNY";
    private static final String API_GOLD_USD = "https://api.gold-api.com/price/XAU/USD";
    private static final int MAX_RETRIES = 2;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Context context;
    private final AtomicInteger retryCount = new AtomicInteger(0);

    public interface PriceCallback {
        void onSuccess(GoldPrice price);
        void onError(String error);
    }

    public static class GoldPrice {
        public double cnyPerGram;
        public double cnyPerOz;
        public double usdPerOz;
        public double prevCnyPerGram; // 上次价格，用于计算涨跌
        public double change;         // 涨跌金额
        public double changePercent;  // 涨跌百分比
        public String updatedAt;
        public long timestamp;
        public boolean fromCache;

        public GoldPrice(double cnyPerGram, double cnyPerOz, double usdPerOz, String updatedAt) {
            this.cnyPerGram = cnyPerGram;
            this.cnyPerOz = cnyPerOz;
            this.usdPerOz = usdPerOz;
            this.updatedAt = updatedAt;
            this.timestamp = System.currentTimeMillis();
            this.fromCache = false;
        }
    }

    public PriceFetcher(Context context) {
        this.context = context.getApplicationContext();
    }

    public void fetchPrice(PriceCallback callback) {
        executor.execute(() -> {
            try {
                double prevPrice = getPrevPrice();
                Double usdPrice = fetchJsonPrice(API_GOLD_USD);
                Double cnyOzPrice = fetchJsonPrice(API_GOLD_CNY);

                if (cnyOzPrice != null) {
                    double cnyPerGram = cnyOzPrice / 31.1035;
                    double usd = (usdPrice != null) ? usdPrice : 0;
                    String time = new java.text.SimpleDateFormat("HH:mm:ss",
                            java.util.Locale.getDefault()).format(new java.util.Date());

                    GoldPrice price = new GoldPrice(cnyPerGram, cnyOzPrice, usd, time);
                    price.prevCnyPerGram = prevPrice;

                    if (prevPrice > 0) {
                        price.change = cnyPerGram - prevPrice;
                        price.changePercent = (price.change / prevPrice) * 100;
                    }

                    savePrice(cnyPerGram);
                    retryCount.set(0);
                    callback.onSuccess(price);
                } else {
                    // 失败时尝试重试
                    if (retryCount.incrementAndGet() <= MAX_RETRIES) {
                        fetchPrice(callback);
                    } else {
                        // 重试用尽，尝试返回缓存
                        GoldPrice cached = loadFromCache();
                        if (cached != null) {
                            callback.onSuccess(cached);
                        } else {
                            callback.onError("金价数据获取失败");
                        }
                        retryCount.set(0);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Fetch error", e);
                GoldPrice cached = loadFromCache();
                if (cached != null) {
                    callback.onSuccess(cached);
                } else {
                    callback.onError("网络错误");
                }
            }
        });
    }

    private Double fetchJsonPrice(String apiUrl) {
        try {
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "GoldPriceFloat/3.0");
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                return new JSONObject(sb.toString()).getDouble("price");
            }
        } catch (Exception e) {
            Log.e(TAG, "Fetch error: " + apiUrl, e);
        }
        return null;
    }

    private double getPrevPrice() {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getFloat("prev_price", 0f);
    }

    private void savePrice(double price) {
        SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        float old = sp.getFloat("prev_price", 0f);
        sp.edit()
                .putFloat("prev_price", old > 0 ? (float) price : (float) price)
                .putFloat("cached_price", (float) price)
                .putLong("cached_time", System.currentTimeMillis())
                .apply();
        // 首次运行时 prev = current，不显示涨跌
        if (old <= 0) {
            sp.edit().putFloat("prev_price", (float) price).apply();
        }
    }

    private GoldPrice loadFromCache() {
        SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        float cached = sp.getFloat("cached_price", 0f);
        if (cached > 0) {
            long time = sp.getLong("cached_time", 0);
            String ago = ((System.currentTimeMillis() - time) / 1000 / 60) + "分钟前";
            GoldPrice p = new GoldPrice(cached, cached * 31.1035, 0, ago);
            p.fromCache = true;
            p.prevCnyPerGram = sp.getFloat("prev_price", cached);
            p.change = p.cnyPerGram - p.prevCnyPerGram;
            p.changePercent = p.prevCnyPerGram > 0 ? (p.change / p.prevCnyPerGram) * 100 : 0;
            return p;
        }
        return null;
    }
}
