package com.handheld.playtime.battery;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class BatteryReader {
    private static final String TAG = "BatteryReader";

    private final Context mContext;
    private final BatteryManager mBatteryManager;

    private static final String[] SYSFS_CURRENT_PATHS = {
        "/sys/class/power_supply/battery/current_now",
        "/sys/class/power_supply/bms/current_now",
        "/sys/class/power_supply/battery/BatteryAverageCurrent",
        "/sys/class/power_supply/battery/device/power_supply/battery/current_now"
    };

    private static final String[] SYSFS_CHARGE_COUNTER_PATHS = {
        "/sys/class/power_supply/battery/charge_counter",
        "/sys/class/power_supply/battery/charge_now",
        "/sys/class/power_supply/bms/charge_counter"
    };

    public BatteryReader(Context context) {
        if (context != null) {
            this.mContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
            this.mBatteryManager = (BatteryManager) this.mContext.getSystemService(Context.BATTERY_SERVICE);
        } else {
            this.mContext = null;
            this.mBatteryManager = null;
        }
    }

    /**
     * Returns instantaneous discharge/charge current in mA (positive value).
     */
    public int getCurrentMa() {
        int current = 0;

        // 1. Try standard Android BatteryManager API
        if (mBatteryManager != null) {
            try {
                int curUa = mBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                if (curUa != Integer.MIN_VALUE && curUa != 0) {
                    current = normalizeCurrentToMa(curUa);
                }
            } catch (Exception e) {
                Log.w(TAG, "BatteryManager current_now failed: " + e.getMessage());
            }
        }

        // 2. Sysfs fallback if BatteryManager was 0 or failed
        if (current == 0) {
            current = readSysfsCurrent();
        }

        return Math.abs(current);
    }

    /**
     * Converts raw current (which could be in uA, mA, positive, or negative) into positive mA.
     */
    private int normalizeCurrentToMa(int raw) {
        int absVal = Math.abs(raw);
        // If raw value is > 10,000, it's almost certainly microamps (uA)
        if (absVal >= 10000) {
            return absVal / 1000;
        }
        // Otherwise, it's already in milliamps (mA)
        return absVal;
    }

    private int readSysfsCurrent() {
        for (String path : SYSFS_CURRENT_PATHS) {
            File file = new File(path);
            if (file.exists() && file.canRead()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String line = reader.readLine();
                    if (line != null) {
                        int raw = Integer.parseInt(line.trim());
                        return normalizeCurrentToMa(raw);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return 0;
    }

    /**
     * Returns remaining battery capacity in mAh.
     */
    public int getRemainingCapacityMah(int capacityOverrideMah) {
        int pct = getBatteryLevelPercent();

        // If user manually set battery capacity (e.g., 8000 for Odin 2, 5000 for RP4 Pro)
        if (capacityOverrideMah > 0) {
            return Math.max(1, (int) (capacityOverrideMah * (pct / 100.0f)));
        }

        // Try reading charge_counter from BatteryManager
        if (mBatteryManager != null) {
            try {
                long chargeCounter = mBatteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                if (chargeCounter > 0) {
                    if (chargeCounter >= 100000) {
                        return (int) (chargeCounter / 1000); // from uAh to mAh
                    } else {
                        return (int) chargeCounter; // already mAh
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "BatteryManager charge_counter failed: " + e.getMessage());
            }
        }

        // Try reading sysfs charge counter
        for (String path : SYSFS_CHARGE_COUNTER_PATHS) {
            File file = new File(path);
            if (file.exists() && file.canRead()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String line = reader.readLine();
                    if (line != null) {
                        long raw = Long.parseLong(line.trim());
                        if (raw >= 100000) {
                            return (int) (raw / 1000);
                        } else if (raw > 0) {
                            return (int) raw;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // Fallback estimate assuming standard 5000mAh handheld cell
        return Math.max(1, (int) (5000 * (pct / 100.0f)));
    }

    /**
     * Returns current battery percentage (0 - 100).
     */
    public int getBatteryLevelPercent() {
        if (mBatteryManager != null) {
            int cap = mBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            if (cap > 0 && cap <= 100) {
                return cap;
            }
        }

        if (mContext != null) {
            Intent intent = mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (intent != null) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    return Math.round((level / (float) scale) * 100f);
                }
            }
        }
        return 50;
    }

    /**
     * Returns true if device is connected to a charger and charging.
     */
    public boolean isCharging() {
        if (mContext != null) {
            Intent intent = mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (intent != null) {
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                       status == BatteryManager.BATTERY_STATUS_FULL;
            }
        }
        return false;
    }

    /**
     * Returns battery voltage in millivolts (mV).
     */
    public int getVoltageMv() {
        if (mContext != null) {
            Intent intent = mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (intent != null) {
                return intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 3800);
            }
        }
        return 3800;
    }
}
