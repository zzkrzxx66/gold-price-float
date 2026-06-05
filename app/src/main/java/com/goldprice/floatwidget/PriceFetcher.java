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
    private static final String API_CNY = "https://api.gold-api.com/price/XAU/CNY";
    private static final String API_USD = "https://api.gold-api.com/price/XAU/USD";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface PriceCallback {
        void onSuccess(GoldPrice price);
        void onError(String error);
    }

    public static class GoldPrice {
        public double cnyPerGram;     // 国内金价 ¥/克
        public double cnyPerOz;       // ¥/盎司
        public double usdPerOz;       // 伦敦金 $/盎司
        public String updatedAt;
        public long timestamp;

        public GoldPrice(double cnyPerGram, double cnyPerOz, double usdPerOz, String updatedAt) {
            this.cnyPerGram = cnyPerGram;
            this.cnyPerOz = cnyPerOz;
            this.usdPerOz = usdPerOz;
            this.updatedAt = updatedAt;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public void fetchPrice(PriceCallback callback) {
        executor.execute(() -> {
            try {
                Double usdPrice = fetchJsonPrice(API_USD);
                Double cnyOzPrice = fetchJsonPrice(API_CNY);
                if (cnyOzPrice != null) {
                    double cnyPerGram = cnyOzPrice / 31.1035;
                    double usd = (usdPrice != null) ? usdPrice : 0;
                    String time = java.text.SimpleDateFormat.getTimeInstance(
                            java.text.SimpleDateFormat.SHORT,
                            java.util.Locale.getDefault())
                            .format(new java.util.Date());
                    callback.onSuccess(new GoldPrice(cnyPerGram, cnyOzPrice, usd, time));
                } else if (usdPrice != null) {
                    callback.onError("国内金价获取失败");
                } else {
                    callback.onError("金价数据获取失败");
                }
            } catch (Exception e) {
                Log.e(TAG, "Fetch error", e);
                callback.onError("网络错误");
            }
        });
    }

    private Double fetchJsonPrice(String apiUrl) {
        try {
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "GoldPriceFloat/2.0");
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject obj = new JSONObject(sb.toString());
                return obj.getDouble("price");
            }
        } catch (Exception e) {
            Log.e(TAG, "Fetch error for " + apiUrl, e);
        }
        return null;
    }
}
