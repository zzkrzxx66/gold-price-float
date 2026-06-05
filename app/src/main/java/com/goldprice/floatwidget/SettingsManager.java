package com.goldprice.floatwidget;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {
    private static final String PREF_NAME = "gold_price_settings";
    private static final String KEY_REFRESH_INTERVAL = "refresh_interval";
    private static final String KEY_OPACITY = "opacity";
    private static final String KEY_SHOW_LONDON = "show_london";

    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /** 刷新间隔（秒） */
    public int getRefreshInterval() {
        return prefs.getInt(KEY_REFRESH_INTERVAL, 30);
    }

    public void setRefreshInterval(int seconds) {
        prefs.edit().putInt(KEY_REFRESH_INTERVAL, seconds).apply();
    }

    /** 透明度 0~100 */
    public int getOpacity() {
        return prefs.getInt(KEY_OPACITY, 85);
    }

    public void setOpacity(int percent) {
        prefs.edit().putInt(KEY_OPACITY, percent).apply();
    }

    /** 是否显示伦敦金 */
    public boolean isShowLondon() {
        return prefs.getBoolean(KEY_SHOW_LONDON, true);
    }

    public void setShowLondon(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_LONDON, show).apply();
    }
}
