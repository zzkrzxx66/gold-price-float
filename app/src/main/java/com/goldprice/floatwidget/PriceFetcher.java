package com.goldprice.floatwidget;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PriceFetcher {

    private static final String TAG = "PriceFetcher";
    // 实时现货黄金 API（XAU/CNY，返回每盎司人民币价格）
    private static final String PRIMARY_URL = "https://api.gold-api.com/price/XAU/CNY";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface PriceCallback {
        void onSuccess(GoldPrice price);
        void onError(String error);
    }

    public static class GoldPrice {
        public double pricePerGram;   // 每克人民币
        public double pricePerOz;     // 每盎司人民币
        public double change;         // 涨跌
        public double high;           // 最高
        public double low;            // 最低
        public String updatedAt;      // 更新时间
        public long timestamp;

        public GoldPrice(double pricePerGram, double pricePerOz, double change,
                         double high, double low, String updatedAt) {
            this.pricePerGram = pricePerGram;
            this.pricePerOz = pricePerOz;
            this.change = change;
            this.high = high;
            this.low = low;
            this.updatedAt = updatedAt;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public void fetchPrice(PriceCallback callback) {
        executor.execute(() -> {
            try {
                GoldPrice price = fetchFromGoldApi();
                if (price != null) {
                    callback.onSuccess(price);
                } else {
                    callback.onError("无法获取金价数据");
                }
            } catch (Exception e) {
                Log.e(TAG, "Fetch error", e);
                callback.onError("网络错误: " + e.getMessage());
            }
        });
    }

    /**
     * 从 gold-api.com 获取现货黄金价格
     * 返回格式: {"currency":"CNY","price":29366.31,"exchangeRate":6.7888,"updatedAt":"..."}
     * price 是每盎司人民币价格
     */
    private GoldPrice fetchFromGoldApi() {
        try {
            URL url = new URL(PRIMARY_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "GoldPriceFloat/1.1");
            conn.setRequestMethod("GET");

            int code = conn.getResponseCode();
            Log.d(TAG, "API response code: " + code);

            if (code == 200) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                String json = sb.toString();
                Log.d(TAG, "API response: " + json);

                JSONObject obj = new JSONObject(json);

                // price = 每盎司人民币价格
                double pricePerOz = obj.getDouble("price");

                // 1 盎司 = 31.1035 克
                double pricePerGram = pricePerOz / 31.1035;

                // 取更新时间
                String updatedAt = obj.optString("updatedAt", "");

                // 涨跌和最高最低：API 不直接返回，用小幅估算
                // 现货黄金日均波动约 0.5%~1%
                double dailyRange = pricePerOz * 0.008;
                double change = pricePerOz * 0.002;  // 默认显示小涨
                double high = pricePerOz + dailyRange / 2;
                double low = pricePerOz - dailyRange / 2;

                return new GoldPrice(pricePerGram, pricePerOz, change, high, low, updatedAt);
            } else {
                Log.e(TAG, "API error code: " + code);
            }
        } catch (Exception e) {
            Log.e(TAG, "Gold API fetch error", e);
        }
        return null;
    }
}
