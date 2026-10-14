package com.handheld.playtime.config;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

public class PlayTimeConfig {
    private static final String TAG = "PlayTimeConfig";

    public static final String PREF_NAME = "playtime_settings";
    public static final String ACTION_UPDATE_CONFIG = "com.handheld.playtime.ACTION_UPDATE_CONFIG";
    public static final String AUTHORITY = "com.handheld.playtime.provider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);
    public static final String METHOD_GET_CONFIG = "getConfig";

    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_WINDOW_MINUTES = "window_minutes";
    public static final String KEY_INTERVAL_SECONDS = "interval_seconds";
    public static final String KEY_FORMAT = "format";
    public static final String KEY_POSITION = "position";
    public static final String KEY_DETAIL_MODE = "detail_mode";
    public static final String KEY_CHARGING_SYMBOL = "charging_symbol";
    public static final String KEY_FONT_SIZE = "font_size";
    public static final String KEY_TAP_TOGGLE = "tap_toggle";
    public static final String KEY_CAPACITY_OVERRIDE = "capacity_override";

    public boolean enabled = true;
    public int windowMinutes = 5;
    public int intervalSeconds = 5;
    public String format = "COMPACT"; // COMPACT, SHORT, COLON, DETAILED
    public String position = "BATTERY_LEFT"; // BATTERY_LEFT, BATTERY_RIGHT, CLOCK_LEFT, CLOCK_RIGHT
    public String detailMode = "TIME_ONLY"; // TIME_ONLY, TIME_WATTS, TIME_WATTS_VOLTS, TIME_VOLTS, WATTS_ONLY, VOLTS_ONLY, WATTS_VOLTS
    public String chargingSymbol = "BOLT_MONO"; // BOLT_MONO (↯), PLUS (+), TEXT (CHG), BOLT_COLOR (⚡)
    public float fontSize = 12f;
    public boolean tapToggle = true;
    public int capacityOverrideMah = 0; // 0 = auto-detect

    public static PlayTimeConfig fromSharedPreferences(Context context) {
        PlayTimeConfig config = new PlayTimeConfig();
        try {
            SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            config.enabled = sp.getBoolean(KEY_ENABLED, true);
            config.windowMinutes = sp.getInt(KEY_WINDOW_MINUTES, 5);
            config.intervalSeconds = sp.getInt(KEY_INTERVAL_SECONDS, 5);
            config.format = sp.getString(KEY_FORMAT, "COMPACT");

            String rawPos = sp.getString(KEY_POSITION, "BATTERY_LEFT");
            // Normalize legacy values
            if ("LEFT".equalsIgnoreCase(rawPos)) config.position = "BATTERY_LEFT";
            else if ("RIGHT".equalsIgnoreCase(rawPos)) config.position = "BATTERY_RIGHT";
            else config.position = rawPos;

            config.detailMode = sp.getString(KEY_DETAIL_MODE, "TIME_ONLY");
            config.chargingSymbol = sp.getString(KEY_CHARGING_SYMBOL, "BOLT_MONO");
            config.fontSize = sp.getFloat(KEY_FONT_SIZE, 12f);
            config.tapToggle = sp.getBoolean(KEY_TAP_TOGGLE, true);
            config.capacityOverrideMah = sp.getInt(KEY_CAPACITY_OVERRIDE, 0);
        } catch (Exception e) {
            Log.e(TAG, "Error loading SharedPreferences: " + e.getMessage());
        }
        return config;
    }

    public void saveToSharedPreferences(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putInt(KEY_WINDOW_MINUTES, windowMinutes)
            .putInt(KEY_INTERVAL_SECONDS, intervalSeconds)
            .putString(KEY_FORMAT, format)
            .putString(KEY_POSITION, position)
            .putString(KEY_DETAIL_MODE, detailMode)
            .putString(KEY_CHARGING_SYMBOL, chargingSymbol)
            .putFloat(KEY_FONT_SIZE, fontSize)
            .putBoolean(KEY_TAP_TOGGLE, tapToggle)
            .putInt(KEY_CAPACITY_OVERRIDE, capacityOverrideMah)
            .apply();
    }

    public Bundle toBundle() {
        Bundle b = new Bundle();
        b.putBoolean(KEY_ENABLED, enabled);
        b.putInt(KEY_WINDOW_MINUTES, windowMinutes);
        b.putInt(KEY_INTERVAL_SECONDS, intervalSeconds);
        b.putString(KEY_FORMAT, format);
        b.putString(KEY_POSITION, position);
        b.putString(KEY_DETAIL_MODE, detailMode);
        b.putString(KEY_CHARGING_SYMBOL, chargingSymbol);
        b.putFloat(KEY_FONT_SIZE, fontSize);
        b.putBoolean(KEY_TAP_TOGGLE, tapToggle);
        b.putInt(KEY_CAPACITY_OVERRIDE, capacityOverrideMah);
        return b;
    }

    public static PlayTimeConfig fromBundle(Bundle b) {
        PlayTimeConfig config = new PlayTimeConfig();
        if (b == null) return config;
        config.enabled = b.getBoolean(KEY_ENABLED, true);
        config.windowMinutes = b.getInt(KEY_WINDOW_MINUTES, 5);
        config.intervalSeconds = b.getInt(KEY_INTERVAL_SECONDS, 5);
        config.format = b.getString(KEY_FORMAT, "COMPACT");

        String rawPos = b.getString(KEY_POSITION, "BATTERY_LEFT");
        if ("LEFT".equalsIgnoreCase(rawPos)) config.position = "BATTERY_LEFT";
        else if ("RIGHT".equalsIgnoreCase(rawPos)) config.position = "BATTERY_RIGHT";
        else config.position = rawPos;

        config.detailMode = b.getString(KEY_DETAIL_MODE, "TIME_ONLY");
        config.chargingSymbol = b.getString(KEY_CHARGING_SYMBOL, "BOLT_MONO");
        config.fontSize = b.getFloat(KEY_FONT_SIZE, 12f);
        config.tapToggle = b.getBoolean(KEY_TAP_TOGGLE, true);
        config.capacityOverrideMah = b.getInt(KEY_CAPACITY_OVERRIDE, 0);
        return config;
    }

    public void sendUpdateBroadcast(Context context) {
        Intent intent = new Intent(ACTION_UPDATE_CONFIG);
        intent.putExtras(toBundle());
        intent.setPackage("com.android.systemui");
        context.sendBroadcast(intent);

        Intent globalIntent = new Intent(ACTION_UPDATE_CONFIG);
        globalIntent.putExtras(toBundle());
        context.sendBroadcast(globalIntent);
    }

    public static PlayTimeConfig loadFromProvider(Context context) {
        if (context == null) return new PlayTimeConfig();
        try {
            Bundle res = context.getContentResolver().call(
                CONTENT_URI, METHOD_GET_CONFIG, null, null
            );
            if (res != null) {
                return fromBundle(res);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to load config from ContentProvider: " + t.getMessage());
        }
        return new PlayTimeConfig();
    }
}
