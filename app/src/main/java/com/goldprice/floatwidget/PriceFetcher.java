package com.goldprice.floatwidget;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PriceFetcher {

    private static final String TAG = "PriceFetcher";
    private static final String API_URL = "https://api.jd.com/routerjson?method=jingdong.gold.price.get&360buy_param_json={\"channel\":\"1\",\"type\":\"1"}";

    // Sina Finance gold price API (more reliable)
    private static final String SINA_URL = "https://hq.sinajs.cn/list=hf_GC";
    // Backup: use a simpler endpoint
    private static final String BACKUP_URL = "https://api.exchangerate-api.com/v4/latest/XAU";

    // Direct gold quote from Chinese market
    private static final String AU_URL = "https://hq.sinajs.cn/list=hf_GC,hf_SI,hf_XAU";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface PriceCallback {
        void onSuccess(GoldPrice price);
        void onError(String error);
    }

    public static class GoldPrice {
        public double price;    // Current price in CNY/gram
        public double change;   // Price change
        public double high;     // Day high
        public double low;      // Day low
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
                // Try fetching from multiple sources
                GoldPrice price = fetchFromSina();
                if (price == null) {
                    price = fetchFromBackup();
                }
                if (price != null) {
                    callback.onSuccess(price);
                } else {
                    callback.onError("无法获取金价数据");
                }
            } catch (Exception e) {
                Log.e(TAG, "Fetch error", e);
                callback.onError(e.getMessage());
            }
        });
    }

    private GoldPrice fetchFromSina() {
        try {
            URL url = new URL("https://hq.sinajs.cn/list=hf_GC");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Referer", "https://finance.sina.com.cn");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int code = conn.getResponseCode();
            if (code == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "GBK"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                String response = sb.toString();
                // Parse: var hq_str_hf_GC="...,current_price,...,high,...,low,...";
                if (response.contains("hf_GC")) {
                    String data = response.substring(response.indexOf("\"") + 1, response.lastIndexOf("\""));
                    String[] parts = data.split(",");
                    if (parts.length > 8) {
                        // parts[0] = name, parts[6] = price, etc.
                        // Sina returns USD/oz for COMEX gold
                        // We need to convert to CNY/gram
                        double usdPerOz = Double.parseDouble(parts[0]);
                        double prevClose = Double.parseDouble(parts[8]);
                        double high = Double.parseDouble(parts[4]);
                        double low = Double.parseDouble(parts[5]);

                        // 1 troy ounce = 31.1035 grams
                        // Fetch USD/CNY rate (approximate)
                        double usdToCny = fetchUSDCNY();
                        if (usdToCny <= 0) usdToCny = 7.25; // fallback

                        double cnyPerGram = (usdPerOz / 31.1035) * usdToCny;
                        double change = ((prevClose / 31.1035) * usdToCny) - cnyPerGram;
                        double highCNY = (high / 31.1035) * usdToCny;
                        double lowCNY = (low / 31.1035) * usdToCny;

                        return new GoldPrice(cnyPerGram, change, highCNY, lowCNY);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Sina fetch error", e);
        }
        return null;
    }

    private double fetchUSDCNY() {
        try {
            URL url = new URL("https://api.exchangerate-api.com/v4/latest/USD");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                JSONObject json = new JSONObject(sb.toString());
                return json.getJSONObject("rates").getDouble("CNY");
            }
        } catch (Exception e) {
            Log.w(TAG, "USD/CNY fetch failed", e);
        }
        return 0;
    }

    private GoldPrice fetchFromBackup() {
        try {
            URL url = new URL(BACKUP_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                double xauRate = json.getJSONObject("rates").getDouble("XAU");
                double cnyRate = json.getJSONObject("rates").getDouble("CNY");

                // 1 XAU = cnyRate / xauRate CNY
                // But XAU rate means 1 USD = xauRate XAU, so 1 XAU = 1/xauRate USD
                double usdPerOz = 1.0 / xauRate;
                double cnyPerGram = (usdPerOz / 31.1035) * cnyRate;

                return new GoldPrice(cnyPerGram, 0, cnyPerGram, cnyPerGram);
            }
        } catch (Exception e) {
            Log.e(TAG, "Backup fetch error", e);
        }
        return null;
    }
}
