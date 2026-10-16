package com.handheld.playtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.handheld.playtime.battery.BatteryReader;
import com.handheld.playtime.battery.PlayTimeEstimator;
import com.handheld.playtime.config.PlayTimeConfig;

import org.junit.Before;
import org.junit.Test;

public class PlayTimeEstimatorTest {

    private static class TestClock implements PlayTimeEstimator.Clock {
        long timeMs = 1_000_000L;

        @Override
        public long elapsedRealtime() {
            return timeMs;
        }

        public void advanceSeconds(int sec) {
            timeMs += sec * 1000L;
        }
    }

    private static class MockBatteryReader extends BatteryReader {
        int currentMa = 1000;
        int remainingMah = 3000;
        int batteryPercent = 60;
        int voltageMv = 3800;
        boolean isCharging = false;

        public MockBatteryReader() {
            super(null);
        }

        @Override
        public int getCurrentMa() {
            return currentMa;
        }

        @Override
        public int getRemainingCapacityMah(int capacityOverrideMah) {
            if (capacityOverrideMah > 0) {
                return (int) (capacityOverrideMah * (batteryPercent / 100f));
            }
            return remainingMah;
        }

        @Override
        public int getBatteryLevelPercent() {
            return batteryPercent;
        }

        @Override
        public boolean isCharging() {
            return isCharging;
        }

        @Override
        public int getVoltageMv() {
            return voltageMv;
        }
    }

    private TestClock clock;
    private MockBatteryReader reader;
    private PlayTimeEstimator estimator;
    private PlayTimeConfig config;

    @Before
    public void setUp() {
        clock = new TestClock();
        reader = new MockBatteryReader();
        estimator = new PlayTimeEstimator(reader, clock);
        config = new PlayTimeConfig();
        config.windowMinutes = 5;
    }

    @Test
    public void testRollingWindowAverage() {
        estimator.recordDirectSample(config, 1000);
        assertEquals(1, estimator.getSampleCount());
        assertEquals(1000, estimator.getAverageCurrentMa());

        clock.advanceSeconds(60);
        estimator.recordDirectSample(config, 2000);
        assertEquals(2, estimator.getSampleCount());
        assertEquals(1500, estimator.getAverageCurrentMa());

        clock.advanceSeconds(300);
        estimator.recordDirectSample(config, 3000);

        assertEquals(2, estimator.getSampleCount());
        assertEquals(2500, estimator.getAverageCurrentMa());
    }

    @Test
    public void testSleepPurgesStaleData() {
        estimator.recordDirectSample(config, 1500);
        clock.advanceSeconds(10);
        estimator.recordDirectSample(config, 1500);
        assertEquals(2, estimator.getSampleCount());

        estimator.onScreenStateChanged(false);
        clock.advanceSeconds(7200);

        estimator.recordDirectSample(config, 20);
        assertEquals(2, estimator.getSampleCount());

        estimator.onScreenStateChanged(true);
        assertEquals(0, estimator.getSampleCount());
    }

    @Test
    public void testRemainingPlayTimeCalculation() {
        reader.remainingMah = 3000;
        estimator.recordDirectSample(config, 1500);

        int mins = estimator.getRemainingPlayTimeMinutes(config);
        assertEquals(120, mins);

        config.format = "COMPACT";
        assertEquals("2h 0m", estimator.getFormattedDisplay(config, 0));

        config.format = "SHORT";
        assertEquals("2h00", estimator.getFormattedDisplay(config, 0));

        config.format = "COLON";
        assertEquals("2:00", estimator.getFormattedDisplay(config, 0));

        config.format = "DETAILED";
        assertEquals("2 hr 0 min", estimator.getFormattedDisplay(config, 0));
    }

    @Test
    public void testDetailModesWattsAndVoltage() {
        reader.remainingMah = 3000;
        reader.voltageMv = 3850; // 3.85V
        estimator.recordDirectSample(config, 2000); // 2000mA * 3.85V = 7.7W, 3000/2000 = 1.5h = 1h 30m

        config.format = "COMPACT";

        // TIME_ONLY
        config.detailMode = "TIME_ONLY";
        assertEquals("1h 30m", estimator.getFormattedDisplay(config, 0));

        // TIME_WATTS
        config.detailMode = "TIME_WATTS";
        assertEquals("1h 30m · 7.7W", estimator.getFormattedDisplay(config, 0));

        // TIME_WATTS_VOLTS
        config.detailMode = "TIME_WATTS_VOLTS";
        assertEquals("1h 30m · 7.7W · 3.85V", estimator.getFormattedDisplay(config, 0));

        // TIME_VOLTS
        config.detailMode = "TIME_VOLTS";
        assertEquals("1h 30m · 3.85V", estimator.getFormattedDisplay(config, 0));

        // WATTS_ONLY
        config.detailMode = "WATTS_ONLY";
        assertEquals("7.7W", estimator.getFormattedDisplay(config, 0));

        // VOLTS_ONLY
        config.detailMode = "VOLTS_ONLY";
        assertEquals("3.85V", estimator.getFormattedDisplay(config, 0));

        // WATTS_VOLTS
        config.detailMode = "WATTS_VOLTS";
        assertEquals("7.7W · 3.85V", estimator.getFormattedDisplay(config, 0));
    }

    @Test
    public void testNonColorChargingBolt() {
        reader.isCharging = true;
        reader.batteryPercent = 40;
        config.capacityOverrideMah = 5000; // Need 3000 mAh
        estimator.recordDirectSample(config, 2000); // 3000/2000 = 1.5h = 1h 30m

        // Default monochrome zigzag bolt
        config.chargingSymbol = "BOLT_MONO";
        assertEquals("↯1h 30m", estimator.getFormattedDisplay(config, 0));

        // Plus symbol
        config.chargingSymbol = "PLUS";
        assertEquals("+1h 30m", estimator.getFormattedDisplay(config, 0));

        // Text CHG
        config.chargingSymbol = "TEXT";
        assertEquals("CHG 1h 30m", estimator.getFormattedDisplay(config, 0));

        // Color emoji
        config.chargingSymbol = "BOLT_COLOR";
        assertEquals("⚡1h 30m", estimator.getFormattedDisplay(config, 0));
    }

    @Test
    public void testColdStartReturnsCalculatingBeforeFirstSample() {
        // Before any interval sample is recorded, play time should return -1 and display "--"
        assertEquals(0, estimator.getSampleCount());
        assertEquals(-1, estimator.getRemainingPlayTimeMinutes(config));
        assertEquals("--", estimator.getFormattedDisplay(config, 0));
    }

    @Test
    public void testInstantCountingAndRoughEstimateProgression() {
        reader.remainingMah = 3000;

        // When screen turns on / device unlocks: 0 samples, counting starts instantly
        assertEquals(0, estimator.getSampleCount());
        assertEquals("--", estimator.getFormattedDisplay(config, 0));

        // After 1 interval (default 5 seconds): first rough estimate is displayed
        clock.advanceSeconds(5);
        estimator.recordDirectSample(config, 1500); // 1500 mA
        assertEquals(1, estimator.getSampleCount());
        assertEquals(120, estimator.getRemainingPlayTimeMinutes(config)); // 3000 * 60 / 1500 = 120 min
        assertEquals("2h 0m", estimator.getFormattedDisplay(config, 0));

        // After 2nd interval (10 seconds total): estimate refines with moving average
        clock.advanceSeconds(5);
        estimator.recordDirectSample(config, 1000); // 1000 mA
        assertEquals(2, estimator.getSampleCount());
        assertEquals(1250, estimator.getAverageCurrentMa());
        assertEquals(144, estimator.getRemainingPlayTimeMinutes(config)); // 3000 * 60 / 1250 = 144 min = 2h 24m
        assertEquals("2h 24m", estimator.getFormattedDisplay(config, 0));
    }

    @Test
    public void testShortSleepPurgesStaleDataOnWake() {
        estimator.recordDirectSample(config, 1500);
        assertEquals(1, estimator.getSampleCount());

        // Screen turns off (device locked / asleep) for 15 seconds (< 60s)
        estimator.onScreenStateChanged(false);
        clock.advanceSeconds(15);

        // Screen turns on / device unlocked
        estimator.onScreenStateChanged(true);

        // Stale samples from old session must be purged instantly so old power draw does not skew new game
        assertEquals(0, estimator.getSampleCount());
        assertEquals("--", estimator.getFormattedDisplay(config, 0));

        // After 1 interval of new game load (5 seconds), rough estimate is displayed
        clock.advanceSeconds(5);
        estimator.recordDirectSample(config, 2000);
        assertEquals(1, estimator.getSampleCount());
        assertEquals("1h 30m", estimator.getFormattedDisplay(config, 0));
    }

    @Test
    public void testResetClearsSamplesForInstantCounting() {
        estimator.recordDirectSample(config, 1500);
        assertEquals(1, estimator.getSampleCount());

        estimator.reset();
        assertEquals(0, estimator.getSampleCount());
        assertEquals(-1, estimator.getRemainingPlayTimeMinutes(config));
        assertEquals("--", estimator.getFormattedDisplay(config, 0));
    }
}
