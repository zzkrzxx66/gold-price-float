package com.goldprice.floatwidget;

import android.content.Context;
import android.content.SharedPreferences;
public class SettingsManager {
    private static final String PREF_NAME = "gold_price_settings";
    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public int getRefreshInterval() {
        return prefs.getInt("refresh_interval", 30);
    }

    public void setRefreshInterval(int seconds) {
        prefs.edit().putInt("refresh_interval", seconds).apply();
    }

    public int getOpacity() {
        return prefs.getInt("opacity", 90);
    }

    public void setOpacity(int percent) {
        prefs.edit().putInt("opacity", percent).apply();
    }

    public boolean isShowLondon() {
        return prefs.getBoolean("show_london", true);
    }

    public void setShowLondon(boolean show) {
        prefs.edit().putBoolean("show_london", show).apply();
    }

    // 金价提醒
    public boolean isAlertEnabled() {
        return prefs.getBoolean("alert_enabled", false);
    }

    public void setAlertEnabled(boolean enabled) {
        prefs.edit().putBoolean("alert_enabled", enabled).apply();
    }

    public double getAlertPriceAbove() {
        return Double.longBitsToDouble(prefs.getLong("alert_above", Double.doubleToLongBits(0)));
    }

    public void setAlertPriceAbove(double price) {
        prefs.edit().putLong("alert_above", Double.doubleToLongBits(price)).apply();
    }

    public double getAlertPriceBelow() {
        return Double.longBitsToDouble(prefs.getLong("alert_below", Double.doubleToLongBits(0)));
    }

    public void setAlertPriceBelow(double price) {
        prefs.edit().putLong("alert_below", Double.doubleToLongBits(price)).apply();
    }

    public boolean isCompactMode() {
        return prefs.getBoolean("compact_mode", false);
    }

    public void setCompactMode(boolean compact) {
        prefs.edit().putBoolean("compact_mode", compact).apply();
    }
}
