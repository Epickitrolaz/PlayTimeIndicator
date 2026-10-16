package com.handheld.playtime.battery;

import android.os.SystemClock;
import android.util.Log;

import com.handheld.playtime.config.PlayTimeConfig;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

public class PlayTimeEstimator {
    private static final String TAG = "PlayTimeEstimator";

    public interface Clock {
        long elapsedRealtime();
    }

    public static class Sample {
        public final long timestampMs;
        public final int currentMa;

        public Sample(long timestampMs, int currentMa) {
            this.timestampMs = timestampMs;
            this.currentMa = currentMa;
        }
    }

    private final Deque<Sample> mSamples = new ArrayDeque<>();
    private final BatteryReader mReader;
    private final Clock mClock;
    private boolean mScreenOn = true;
    private long mLastScreenOffTime = 0;

    public PlayTimeEstimator(BatteryReader reader) {
        this(reader, SystemClock::elapsedRealtime);
    }

    public PlayTimeEstimator(BatteryReader reader, Clock clock) {
        this.mReader = reader;
        this.mClock = clock;
    }

    public synchronized void onScreenStateChanged(boolean screenOn) {
        this.mScreenOn = screenOn;
        long now = mClock.elapsedRealtime();

        if (!screenOn) {
            mLastScreenOffTime = now;
        } else {
            // When screen turns on / device wakes up, clear stale samples
            // so we immediately start counting fresh for the new active session
            if (mLastScreenOffTime > 0) {
                mSamples.clear();
                Log.d(TAG, "Screen unlocked / woke up: cleared stale samples for instant fresh counting");
                mLastScreenOffTime = 0;
            }
        }
    }

    /**
     * Polls the current hardware power draw and adds to the rolling active window.
     */
    public synchronized void recordSample(PlayTimeConfig config) {
        if (!mScreenOn || mReader == null) {
            return;
        }

        int currentMa = mReader.getCurrentMa();
        if (currentMa <= 0) {
            return;
        }

        recordDirectSample(config, currentMa);
    }

    /**
     * Direct sample recording (used internally or for testing).
     */
    public synchronized void recordDirectSample(PlayTimeConfig config, int currentMa) {
        if (!mScreenOn || currentMa <= 0) {
            return;
        }

        long now = mClock.elapsedRealtime();
        mSamples.addLast(new Sample(now, currentMa));

        long windowMs = (config != null ? config.windowMinutes : 5) * 60_000L;

        while (!mSamples.isEmpty() && (now - mSamples.peekFirst().timestampMs) > windowMs) {
            mSamples.removeFirst();
        }
    }

    /**
     * Calculates the average active discharge current (in mA) over the rolling window.
     */
    public synchronized int getAverageCurrentMa() {
        if (mSamples.isEmpty()) {
            return mReader != null ? mReader.getCurrentMa() : 0;
        }

        long sum = 0;
        for (Sample s : mSamples) {
            sum += s.currentMa;
        }
        return (int) (sum / mSamples.size());
    }

    /**
     * Returns remaining active play time in minutes.
     * Returns -1 if no samples are available yet (initial interval in progress) or power draw <= 20mA.
     */
    public int getRemainingPlayTimeMinutes(PlayTimeConfig config) {
        if (mSamples.isEmpty()) {
            return -1;
        }

        int avgMa = getAverageCurrentMa();
        if (avgMa <= 20) {
            return -1;
        }

        int remainingMah = mReader != null ? mReader.getRemainingCapacityMah(config != null ? config.capacityOverrideMah : 0) : 0;
        return (int) (((long) remainingMah * 60) / avgMa);
    }

    /**
     * Generates the formatted display string for the status bar.
     * @param overrideMode 0: Default configured detail mode, 1: Watts only, 2: Volts only, 3: Battery %
     */
    public String getFormattedDisplay(PlayTimeConfig config, int overrideMode) {
        boolean charging = mReader != null && mReader.isCharging();
        int currentMa = getAverageCurrentMa();
        int mv = mReader != null ? mReader.getVoltageMv() : 3800;
        float volts = mv / 1000f;
        float watts = (currentMa * volts) / 1000f;

        // Interactive tap override modes
        if (overrideMode == 1) {
            return formatWatts(watts, charging);
        } else if (overrideMode == 2) {
            return String.format(Locale.US, "%.2fV", volts);
        } else if (overrideMode == 3) {
            return (mReader != null ? mReader.getBatteryLevelPercent() : 0) + "%";
        }

        // Determine non-color or custom charging symbol prefix
        String chgPrefix = "";
        if (charging) {
            String sym = config != null && config.chargingSymbol != null ? config.chargingSymbol : "BOLT_MONO";
            switch (sym) {
                case "PLUS":
                    chgPrefix = "+";
                    break;
                case "TEXT":
                    chgPrefix = "CHG ";
                    break;
                case "BOLT_COLOR":
                    chgPrefix = "⚡";
                    break;
                case "BOLT_MONO":
                default:
                    chgPrefix = "↯"; // Monochrome zigzag arrow (non-color)
                    break;
            }
        }

        // Compute time component
        String timeStr;
        if (charging) {
            if (mSamples.isEmpty()) {
                timeStr = chgPrefix.trim();
            } else if (currentMa > 100) {
                int totalMah = config != null && config.capacityOverrideMah > 0 ? config.capacityOverrideMah : 5000;
                int remainingMah = mReader != null ? mReader.getRemainingCapacityMah(config != null ? config.capacityOverrideMah : 0) : 0;
                int neededMah = Math.max(0, totalMah - remainingMah);
                int minsToFull = (int) (((long) neededMah * 60) / currentMa);
                timeStr = chgPrefix + formatTime(minsToFull, config != null ? config.format : "COMPACT");
            } else {
                timeStr = chgPrefix.trim();
            }
        } else {
            int remainingMinutes = getRemainingPlayTimeMinutes(config);
            timeStr = formatTime(remainingMinutes, config != null ? config.format : "COMPACT");
        }

        String wattsStr = formatWatts(watts, charging);
        String voltsStr = String.format(Locale.US, "%.2fV", volts);

        String mode = config != null && config.detailMode != null ? config.detailMode : "TIME_ONLY";
        switch (mode) {
            case "TIME_WATTS":
                return timeStr + " · " + wattsStr;
            case "TIME_WATTS_VOLTS":
                return timeStr + " · " + wattsStr + " · " + voltsStr;
            case "TIME_VOLTS":
                return timeStr + " · " + voltsStr;
            case "WATTS_ONLY":
                return wattsStr;
            case "VOLTS_ONLY":
                return voltsStr;
            case "WATTS_VOLTS":
                return wattsStr + " · " + voltsStr;
            case "TIME_ONLY":
            default:
                return timeStr;
        }
    }

    private String formatWatts(float watts, boolean charging) {
        if (charging) {
            return String.format(Locale.US, "+%.1fW", watts);
        } else {
            return String.format(Locale.US, "%.1fW", watts);
        }
    }

    private String formatTime(int remainingMinutes, String format) {
        if (remainingMinutes < 0) {
            return "--";
        }
        int hours = remainingMinutes / 60;
        int mins = remainingMinutes % 60;

        if (hours > 48) {
            return ">48h";
        }

        String fmt = format != null ? format : "COMPACT";
        switch (fmt) {
            case "SHORT":
                return String.format(Locale.US, "%dh%02d", hours, mins);
            case "COLON":
                return String.format(Locale.US, "%d:%02d", hours, mins);
            case "DETAILED":
                return String.format(Locale.US, "%d hr %d min", hours, mins);
            case "COMPACT":
            default:
                if (hours > 0) {
                    return String.format(Locale.US, "%dh %dm", hours, mins);
                } else {
                    return String.format(Locale.US, "%dm", mins);
                }
        }
    }

    public synchronized int getSampleCount() {
        return mSamples.size();
    }

    public synchronized void reset() {
        mSamples.clear();
        mLastScreenOffTime = 0;
        Log.d(TAG, "Estimator reset: cleared samples for instant fresh counting");
    }
}
