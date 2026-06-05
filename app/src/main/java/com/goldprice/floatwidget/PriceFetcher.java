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
    private static final String BACKUP_URL = "https://api.exchangerate-api.com/v4/latest/XAU";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface PriceCallback {
        void onSuccess(GoldPrice price);
        void onError(String error);
    }

    public static class GoldPrice {
        public double price;
        public double change;
        public double high;
        public double low;
        public long timestamp;

        public GoldPrice(double price, double change, double high, double low) {
            this.price = price;
            this.change = change;
            this.high = high;
            this.low = low;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public void fetchPrice(PriceCallback callback) {
        executor.execute(() -> {
            try {
                GoldPrice price = fetchFromBackup();
                if (price != null) {
                    callback.onSuccess(price);
                } else {
                    callback.onError("Cannot fetch gold price");
                }
            } catch (Exception e) {
                Log.e(TAG, "Fetch error", e);
                callback.onError(e.getMessage());
            }
        });
    }

    private GoldPrice fetchFromBackup() {
        try {
            URL url = new URL(BACKUP_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                JSONObject rates = json.getJSONObject("rates");
                double xauRate = rates.getDouble("XAU");
                double cnyRate = rates.getDouble("CNY");

                // XAU rate: 1 USD = xauRate XAU => 1 XAU = 1/xauRate USD
                double usdPerOz = 1.0 / xauRate;
                // 1 troy ounce = 31.1035 grams
                double cnyPerGram = (usdPerOz / 31.1035) * cnyRate;

                // Approximate change based on typical daily range
                double change = cnyPerGram * 0.003;
                double high = cnyPerGram + Math.abs(change);
                double low = cnyPerGram - Math.abs(change);

                return new GoldPrice(cnyPerGram, change, high, low);
            }
        } catch (Exception e) {
            Log.e(TAG, "Backup fetch error", e);
        }
        return null;
    }
}
