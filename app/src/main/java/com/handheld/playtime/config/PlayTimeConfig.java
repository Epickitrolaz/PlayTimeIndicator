package com.handheld.playtime.config;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;

public class PlayTimeConfig {
    private static final String TAG = "PlayTimeConfig";

    public static final String PREF_NAME = "playtime_settings";
    public static final String SYSTEMUI_CACHE_PREF = "playtime_systemui_cache";
    public static final String ACTION_UPDATE_CONFIG = "com.handheld.playtime.ACTION_UPDATE_CONFIG";
    public static final String AUTHORITY = "com.handheld.playtime.provider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);
    public static final String METHOD_GET_CONFIG = "getConfig";
    public static final String SETTING_GLOBAL_KEY = "playtime_indicator_config";

    public static final String KEY_IS_VALID = "is_valid";
    public static final String KEY_HAS_SAVED_CONFIG = "has_saved_config";
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
    public String position = "BATTERY_LEFT"; // BATTERY_LEFT, BATTERY_RIGHT, CLOCK_LEFT, CLOCK_RIGHT, REPLACE_PERCENT
    public String detailMode = "TIME_ONLY"; // TIME_ONLY, TIME_WATTS, TIME_WATTS_VOLTS, TIME_VOLTS, WATTS_ONLY, VOLTS_ONLY, WATTS_VOLTS
    public String chargingSymbol = "BOLT_MONO"; // BOLT_MONO (↯), PLUS (+), TEXT (CHG), BOLT_COLOR (⚡)
    public float fontSize = 12f;
    public boolean tapToggle = true;
    public int capacityOverrideMah = 0; // 0 = auto-detect

    public String toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put(KEY_ENABLED, enabled);
            obj.put(KEY_WINDOW_MINUTES, windowMinutes);
            obj.put(KEY_INTERVAL_SECONDS, intervalSeconds);
            obj.put(KEY_FORMAT, format);
            obj.put(KEY_POSITION, position);
            obj.put(KEY_DETAIL_MODE, detailMode);
            obj.put(KEY_CHARGING_SYMBOL, chargingSymbol);
            obj.put(KEY_FONT_SIZE, (double) fontSize);
            obj.put(KEY_TAP_TOGGLE, tapToggle);
            obj.put(KEY_CAPACITY_OVERRIDE, capacityOverrideMah);
            return obj.toString();
        } catch (Throwable t) {
            Log.e(TAG, "Error serializing config to JSON", t);
            return "";
        }
    }

    public static PlayTimeConfig fromJson(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) return null;
        try {
            JSONObject obj = new JSONObject(jsonStr);
            PlayTimeConfig config = new PlayTimeConfig();
            config.enabled = obj.optBoolean(KEY_ENABLED, true);
            config.windowMinutes = obj.optInt(KEY_WINDOW_MINUTES, 5);
            config.intervalSeconds = obj.optInt(KEY_INTERVAL_SECONDS, 5);
            config.format = obj.optString(KEY_FORMAT, "COMPACT");

            String rawPos = obj.optString(KEY_POSITION, "BATTERY_LEFT");
            if ("LEFT".equalsIgnoreCase(rawPos)) config.position = "BATTERY_LEFT";
            else if ("RIGHT".equalsIgnoreCase(rawPos)) config.position = "BATTERY_RIGHT";
            else config.position = rawPos;

            config.detailMode = obj.optString(KEY_DETAIL_MODE, "TIME_ONLY");
            config.chargingSymbol = obj.optString(KEY_CHARGING_SYMBOL, "BOLT_MONO");
            config.fontSize = (float) obj.optDouble(KEY_FONT_SIZE, 12.0);
            config.tapToggle = obj.optBoolean(KEY_TAP_TOGGLE, true);
            config.capacityOverrideMah = obj.optInt(KEY_CAPACITY_OVERRIDE, 0);
            return config;
        } catch (Throwable t) {
            Log.e(TAG, "Error parsing config from JSON", t);
            return null;
        }
    }

    public static PlayTimeConfig loadFromSettingsGlobal(Context context) {
        if (context == null) return null;
        try {
            String val = Settings.Global.getString(context.getContentResolver(), SETTING_GLOBAL_KEY);
            if (val != null && !val.trim().isEmpty()) {
                PlayTimeConfig cfg = fromJson(val);
                if (cfg != null) {
                    Log.i(TAG, "Loaded config from Settings.Global: pos=" + cfg.position + ", detail=" + cfg.detailMode);
                    return cfg;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to load from Settings.Global: " + t.getMessage());
        }
        return null;
    }

    public static boolean saveToSettingsGlobal(Context context, PlayTimeConfig config) {
        if (context == null || config == null) return false;
        try {
            String json = config.toJson();
            boolean ok = Settings.Global.putString(context.getContentResolver(), SETTING_GLOBAL_KEY, json);
            Log.i(TAG, "Saved config to Settings.Global (success=" + ok + "): " + json);
            return ok;
        } catch (Throwable t) {
            Log.w(TAG, "Failed to save to Settings.Global: " + t.getMessage());
            return false;
        }
    }

    public static PlayTimeConfig fromSharedPreferences(Context context) {
        PlayTimeConfig config = new PlayTimeConfig();
        if (context == null) return config;

        // 1. Try standard credential-encrypted storage
        try {
            SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            if (sp.getBoolean(KEY_HAS_SAVED_CONFIG, false) || sp.contains(KEY_POSITION)) {
                return fromPrefsInternal(sp);
            }
        } catch (Throwable ignored) {}

        // 2. Try device-protected storage (Direct Boot)
        try {
            Context de = context.createDeviceProtectedStorageContext();
            SharedPreferences sp = de.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            if (sp.getBoolean(KEY_HAS_SAVED_CONFIG, false) || sp.contains(KEY_POSITION)) {
                return fromPrefsInternal(sp);
            }
        } catch (Throwable ignored) {}

        // 3. Fallback to Settings.Global if available
        PlayTimeConfig fromGlobal = loadFromSettingsGlobal(context);
        if (fromGlobal != null) {
            return fromGlobal;
        }

        return config;
    }

    public void saveToSharedPreferences(Context context) {
        if (context == null) return;
        saveToPrefsInternal(context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE));
        try {
            Context de = context.createDeviceProtectedStorageContext();
            saveToPrefsInternal(de.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE));
        } catch (Throwable ignored) {}

        makePrefsFilesWorldReadable(context);
    }

    private static void makePrefsFilesWorldReadable(Context context) {
        try {
            File ceDir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
            ceDir.setReadable(true, false);
            ceDir.setExecutable(true, false);
            File ceFile = new File(ceDir, PREF_NAME + ".xml");
            if (ceFile.exists()) {
                ceFile.setReadable(true, false);
            }

            Context de = context.createDeviceProtectedStorageContext();
            File deDir = new File(de.getDataDir(), "shared_prefs");
            deDir.setReadable(true, false);
            deDir.setExecutable(true, false);
            File deFile = new File(deDir, PREF_NAME + ".xml");
            if (deFile.exists()) {
                deFile.setReadable(true, false);
            }
        } catch (Throwable ignored) {}
    }

    public static PlayTimeConfig loadFromLocalCache(Context context) {
        if (context == null) return null;
        try {
            SharedPreferences sp = context.getSharedPreferences(SYSTEMUI_CACHE_PREF, Context.MODE_PRIVATE);
            if (sp.getBoolean(KEY_HAS_SAVED_CONFIG, false) || sp.contains(KEY_POSITION)) {
                return fromPrefsInternal(sp);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to load from local cache: " + t.getMessage());
        }
        return null;
    }

    public void saveToLocalCache(Context context) {
        if (context == null) return;
        try {
            SharedPreferences sp = context.getSharedPreferences(SYSTEMUI_CACHE_PREF, Context.MODE_PRIVATE);
            saveToPrefsInternal(sp);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to save to local cache: " + t.getMessage());
        }
    }

    private void saveToPrefsInternal(SharedPreferences sp) {
        if (sp == null) return;
        sp.edit()
            .putBoolean(KEY_HAS_SAVED_CONFIG, true)
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
            .commit(); // Synchronous commit to ensure file is written to disk
    }

    private static PlayTimeConfig fromPrefsInternal(SharedPreferences sp) {
        PlayTimeConfig config = new PlayTimeConfig();
        config.enabled = sp.getBoolean(KEY_ENABLED, true);
        config.windowMinutes = sp.getInt(KEY_WINDOW_MINUTES, 5);
        config.intervalSeconds = sp.getInt(KEY_INTERVAL_SECONDS, 5);
        config.format = sp.getString(KEY_FORMAT, "COMPACT");

        String rawPos = sp.getString(KEY_POSITION, "BATTERY_LEFT");
        if ("LEFT".equalsIgnoreCase(rawPos)) config.position = "BATTERY_LEFT";
        else if ("RIGHT".equalsIgnoreCase(rawPos)) config.position = "BATTERY_RIGHT";
        else config.position = rawPos;

        config.detailMode = sp.getString(KEY_DETAIL_MODE, "TIME_ONLY");
        config.chargingSymbol = sp.getString(KEY_CHARGING_SYMBOL, "BOLT_MONO");
        config.fontSize = sp.getFloat(KEY_FONT_SIZE, 12f);
        config.tapToggle = sp.getBoolean(KEY_TAP_TOGGLE, true);
        config.capacityOverrideMah = sp.getInt(KEY_CAPACITY_OVERRIDE, 0);
        return config;
    }

    public Bundle toBundle() {
        Bundle b = new Bundle();
        b.putBoolean(KEY_IS_VALID, true);
        b.putBoolean(KEY_HAS_SAVED_CONFIG, true);
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
        b.putString("json", toJson());
        return b;
    }

    public static PlayTimeConfig fromBundle(Bundle b) {
        if (b == null) return new PlayTimeConfig();
        if (b.containsKey("json")) {
            PlayTimeConfig fromJ = fromJson(b.getString("json"));
            if (fromJ != null) return fromJ;
        }
        PlayTimeConfig config = new PlayTimeConfig();
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
        if (context == null) return;
        try {
            Intent intent = new Intent(ACTION_UPDATE_CONFIG);
            intent.putExtras(toBundle());
            intent.setPackage("com.android.systemui");
            context.sendBroadcast(intent);

            Intent globalIntent = new Intent(ACTION_UPDATE_CONFIG);
            globalIntent.putExtras(toBundle());
            context.sendBroadcast(globalIntent);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to send update broadcast: " + t.getMessage());
        }
    }

    public static PlayTimeConfig loadFromProvider(Context context) {
        if (context == null) return null;
        try {
            Bundle res = context.getContentResolver().call(
                CONTENT_URI, METHOD_GET_CONFIG, null, null
            );
            if (res != null && res.getBoolean(KEY_IS_VALID, false)) {
                return fromBundle(res);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to load config from ContentProvider: " + t.getMessage());
        }
        return null;
    }
}
