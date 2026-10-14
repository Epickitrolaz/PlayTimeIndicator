package com.handheld.playtime;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.handheld.playtime.battery.BatteryReader;
import com.handheld.playtime.battery.PlayTimeEstimator;
import com.handheld.playtime.config.PlayTimeConfig;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private PlayTimeConfig mConfig;
    private BatteryReader mBatteryReader;
    private PlayTimeEstimator mEstimator;

    private MaterialCardView mCardStatus;
    private TextView mTvStatusTitle;
    private TextView mTvStatusHint;

    private TextView mTvLiveCurrent;
    private TextView mTvLiveCapacity;
    private TextView mTvLiveVoltage;
    private TextView mTvLiveEstimate;

    private MaterialSwitch mSwitchEnabled;
    private Spinner mSpinnerPosition;
    private Spinner mSpinnerDetailMode;
    private Spinner mSpinnerChargingSymbol;
    private Spinner mSpinnerFormat;
    private MaterialSwitch mSwitchTapToggle;
    private Spinner mSpinnerWindow;
    private Spinner mSpinnerInterval;
    private EditText mEtCapacityOverride;
    private Button mBtnApply;

    private Handler mDiagHandler;
    private Runnable mDiagRunnable;

    public static boolean isModuleActive() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mConfig = PlayTimeConfig.fromSharedPreferences(this);
        mBatteryReader = new BatteryReader(this);
        mEstimator = new PlayTimeEstimator(mBatteryReader);

        initViews();
        setupSpinners();
        loadConfigToUi();
        updateStatusCard();
        startDiagnosticLoop();

        // Broadcast current config to SystemUI so status bar is immediately updated
        mConfig.sendUpdateBroadcast(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mConfig != null) {
            mConfig.sendUpdateBroadcast(this);
        }
    }

    private void initViews() {
        mCardStatus = findViewById(R.id.cardStatus);
        mTvStatusTitle = findViewById(R.id.tvStatusTitle);
        mTvStatusHint = findViewById(R.id.tvStatusHint);

        mTvLiveCurrent = findViewById(R.id.tvLiveCurrent);
        mTvLiveCapacity = findViewById(R.id.tvLiveCapacity);
        mTvLiveVoltage = findViewById(R.id.tvLiveVoltage);
        mTvLiveEstimate = findViewById(R.id.tvLiveEstimate);

        mSwitchEnabled = findViewById(R.id.switchEnabled);
        mSpinnerPosition = findViewById(R.id.spinnerPosition);
        mSpinnerDetailMode = findViewById(R.id.spinnerDetailMode);
        mSpinnerChargingSymbol = findViewById(R.id.spinnerChargingSymbol);
        mSpinnerFormat = findViewById(R.id.spinnerFormat);
        mSwitchTapToggle = findViewById(R.id.switchTapToggle);
        mSpinnerWindow = findViewById(R.id.spinnerWindow);
        mSpinnerInterval = findViewById(R.id.spinnerInterval);
        mEtCapacityOverride = findViewById(R.id.etCapacityOverride);
        mBtnApply = findViewById(R.id.btnApply);

        mBtnApply.setOnClickListener(v -> saveAndApplySettings());
    }

    private void setupSpinners() {
        // Position Spinner
        ArrayAdapter<CharSequence> positionAdapter = ArrayAdapter.createFromResource(
            this, R.array.position_entries, android.R.layout.simple_spinner_item
        );
        positionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerPosition.setAdapter(positionAdapter);

        // Detail Mode Spinner
        ArrayAdapter<CharSequence> detailAdapter = ArrayAdapter.createFromResource(
            this, R.array.detail_mode_entries, android.R.layout.simple_spinner_item
        );
        detailAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerDetailMode.setAdapter(detailAdapter);

        // Charging Symbol Spinner
        ArrayAdapter<CharSequence> symbolAdapter = ArrayAdapter.createFromResource(
            this, R.array.charging_symbol_entries, android.R.layout.simple_spinner_item
        );
        symbolAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerChargingSymbol.setAdapter(symbolAdapter);

        // Format Spinner
        ArrayAdapter<CharSequence> formatAdapter = ArrayAdapter.createFromResource(
            this, R.array.format_entries, android.R.layout.simple_spinner_item
        );
        formatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerFormat.setAdapter(formatAdapter);

        // Window Spinner
        ArrayAdapter<CharSequence> windowAdapter = ArrayAdapter.createFromResource(
            this, R.array.window_entries, android.R.layout.simple_spinner_item
        );
        windowAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerWindow.setAdapter(windowAdapter);

        // Interval Spinner
        ArrayAdapter<CharSequence> intervalAdapter = ArrayAdapter.createFromResource(
            this, R.array.interval_entries, android.R.layout.simple_spinner_item
        );
        intervalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerInterval.setAdapter(intervalAdapter);
    }

    private void loadConfigToUi() {
        mSwitchEnabled.setChecked(mConfig.enabled);
        mSwitchTapToggle.setChecked(mConfig.tapToggle);
        mEtCapacityOverride.setText(String.valueOf(mConfig.capacityOverrideMah));

        // Position
        String[] positionValues = getResources().getStringArray(R.array.position_values);
        for (int i = 0; i < positionValues.length; i++) {
            if (positionValues[i].equalsIgnoreCase(mConfig.position)) {
                mSpinnerPosition.setSelection(i);
                break;
            }
        }

        // Detail Mode
        String[] detailValues = getResources().getStringArray(R.array.detail_mode_values);
        for (int i = 0; i < detailValues.length; i++) {
            if (detailValues[i].equalsIgnoreCase(mConfig.detailMode)) {
                mSpinnerDetailMode.setSelection(i);
                break;
            }
        }

        // Charging Symbol
        String[] symbolValues = getResources().getStringArray(R.array.charging_symbol_values);
        for (int i = 0; i < symbolValues.length; i++) {
            if (symbolValues[i].equalsIgnoreCase(mConfig.chargingSymbol)) {
                mSpinnerChargingSymbol.setSelection(i);
                break;
            }
        }

        // Format
        String[] formatValues = getResources().getStringArray(R.array.format_values);
        for (int i = 0; i < formatValues.length; i++) {
            if (formatValues[i].equalsIgnoreCase(mConfig.format)) {
                mSpinnerFormat.setSelection(i);
                break;
            }
        }

        // Window
        String[] windowValues = getResources().getStringArray(R.array.window_values);
        for (int i = 0; i < windowValues.length; i++) {
            if (Integer.parseInt(windowValues[i]) == mConfig.windowMinutes) {
                mSpinnerWindow.setSelection(i);
                break;
            }
        }

        // Interval
        String[] intervalValues = getResources().getStringArray(R.array.interval_values);
        for (int i = 0; i < intervalValues.length; i++) {
            if (Integer.parseInt(intervalValues[i]) == mConfig.intervalSeconds) {
                mSpinnerInterval.setSelection(i);
                break;
            }
        }
    }

    private void saveAndApplySettings() {
        mConfig.enabled = mSwitchEnabled.isChecked();
        mConfig.tapToggle = mSwitchTapToggle.isChecked();

        String[] positionValues = getResources().getStringArray(R.array.position_values);
        mConfig.position = positionValues[mSpinnerPosition.getSelectedItemPosition()];

        String[] detailValues = getResources().getStringArray(R.array.detail_mode_values);
        mConfig.detailMode = detailValues[mSpinnerDetailMode.getSelectedItemPosition()];

        String[] symbolValues = getResources().getStringArray(R.array.charging_symbol_values);
        mConfig.chargingSymbol = symbolValues[mSpinnerChargingSymbol.getSelectedItemPosition()];

        String[] formatValues = getResources().getStringArray(R.array.format_values);
        mConfig.format = formatValues[mSpinnerFormat.getSelectedItemPosition()];

        String[] windowValues = getResources().getStringArray(R.array.window_values);
        mConfig.windowMinutes = Integer.parseInt(windowValues[mSpinnerWindow.getSelectedItemPosition()]);

        String[] intervalValues = getResources().getStringArray(R.array.interval_values);
        mConfig.intervalSeconds = Integer.parseInt(intervalValues[mSpinnerInterval.getSelectedItemPosition()]);

        try {
            String capText = mEtCapacityOverride.getText() != null ? mEtCapacityOverride.getText().toString().trim() : "0";
            mConfig.capacityOverrideMah = Integer.parseInt(capText);
        } catch (NumberFormatException e) {
            mConfig.capacityOverrideMah = 0;
        }

        mConfig.saveToSharedPreferences(this);
        mConfig.sendUpdateBroadcast(this);

        // Also save to Settings.Global via root shell as an extra guarantee
        saveToSettingsGlobalViaRootAsync(mConfig.toJson());

        Toast.makeText(this, R.string.toast_applied, Toast.LENGTH_SHORT).show();
    }

    private void saveToSettingsGlobalViaRootAsync(String json) {
        if (json == null || json.isEmpty()) return;
        new Thread(() -> {
            try {
                String escaped = json.replace("'", "'\\''");
                Process process = Runtime.getRuntime().exec(new String[]{
                    "su", "-c", "settings put global " + PlayTimeConfig.SETTING_GLOBAL_KEY + " '" + escaped + "'"
                });
                process.waitFor();
            } catch (Throwable ignored) {}
        }).start();
    }

    private void updateStatusCard() {
        boolean active = isModuleActive();
        if (active) {
            mTvStatusTitle.setText(R.string.status_active);
            mTvStatusTitle.setTextColor(getResources().getColor(R.color.status_green, getTheme()));
            mTvStatusHint.setText("Hook active in SystemUI. Status bar indicator is running.");
            mCardStatus.setStrokeColor(Color.parseColor("#4CAF50"));
        } else {
            mTvStatusTitle.setText(R.string.status_inactive);
            mTvStatusTitle.setTextColor(getResources().getColor(R.color.status_red, getTheme()));
            mTvStatusHint.setText(R.string.status_hint);
            mCardStatus.setStrokeColor(Color.parseColor("#E53935"));
        }
    }

    private void startDiagnosticLoop() {
        mDiagHandler = new Handler(Looper.getMainLooper());
        mDiagRunnable = new Runnable() {
            @Override
            public void run() {
                mEstimator.recordSample(mConfig);

                int currentMa = mBatteryReader.getCurrentMa();
                int mv = mBatteryReader.getVoltageMv();
                float watts = (currentMa * (mv / 1000f)) / 1000f;
                int remainingMah = mBatteryReader.getRemainingCapacityMah(mConfig.capacityOverrideMah);
                int pct = mBatteryReader.getBatteryLevelPercent();
                String previewStr = mEstimator.getFormattedDisplay(mConfig, 0);

                mTvLiveCurrent.setText(String.format(Locale.US, "Current Draw: %d mA (%.1f W)", currentMa, watts));
                mTvLiveCapacity.setText(String.format(Locale.US, "Remaining Capacity: %d mAh (%d%%)", remainingMah, pct));
                mTvLiveVoltage.setText(String.format(Locale.US, "Battery Voltage: %.2f V", mv / 1000f));
                mTvLiveEstimate.setText(String.format(Locale.US, "Status Bar Preview: %s", previewStr));

                mDiagHandler.postDelayed(this, 2000);
            }
        };
        mDiagHandler.post(mDiagRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mDiagHandler != null && mDiagRunnable != null) {
            mDiagHandler.removeCallbacks(mDiagRunnable);
        }
    }
}
