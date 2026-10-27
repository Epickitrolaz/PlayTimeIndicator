package com.handheld.playtime;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.handheld.playtime.battery.BatteryReader;
import com.handheld.playtime.battery.PlayTimeEstimator;
import com.handheld.playtime.config.PlayTimeConfig;

import java.util.ArrayList;
import java.util.List;
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

    private SwitchMaterial mSwitchEnabled;
    private Spinner mSpinnerPosition;
    private Spinner mSpinnerDetailMode;
    private Spinner mSpinnerChargingSymbol;
    private Spinner mSpinnerFormat;
    private SwitchMaterial mSwitchTapToggle;
    private Spinner mSpinnerWindow;
    private Spinner mSpinnerInterval;
    private TextInputEditText mEtCapacityOverride;
    private MaterialButton mBtnApply;

    private SwitchMaterial mSwitchChargingSwitch;
    private Spinner mSpinnerChargingSwitchInterval;
    private CheckBox mCbSwitchTime;
    private CheckBox mCbSwitchPercent;
    private CheckBox mCbSwitchWatts;
    private CheckBox mCbSwitchVolts;
    private TextView mTvChargingSwitchNote;
    private LinearLayout mLayoutChargingSwitchOptions;
    private int mDiagCycleCounter = 0;

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

        mSwitchChargingSwitch = findViewById(R.id.switchChargingSwitch);
        mSpinnerChargingSwitchInterval = findViewById(R.id.spinnerChargingSwitchInterval);
        mCbSwitchTime = findViewById(R.id.cbSwitchTime);
        mCbSwitchPercent = findViewById(R.id.cbSwitchPercent);
        mCbSwitchWatts = findViewById(R.id.cbSwitchWatts);
        mCbSwitchVolts = findViewById(R.id.cbSwitchVolts);
        mTvChargingSwitchNote = findViewById(R.id.tvChargingSwitchNote);
        mLayoutChargingSwitchOptions = findViewById(R.id.layoutChargingSwitchOptions);

        mSwitchChargingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mLayoutChargingSwitchOptions.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        mSpinnerPosition.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateChargingSwitchNoteVisibility();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

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

        // Charging Switch Interval Spinner
        ArrayAdapter<CharSequence> chgIntervalAdapter = ArrayAdapter.createFromResource(
            this, R.array.charging_switch_interval_entries, android.R.layout.simple_spinner_item
        );
        chgIntervalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerChargingSwitchInterval.setAdapter(chgIntervalAdapter);
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

        // Charging switch
        mSwitchChargingSwitch.setChecked(mConfig.chargingSwitchEnabled);
        mLayoutChargingSwitchOptions.setVisibility(mConfig.chargingSwitchEnabled ? View.VISIBLE : View.GONE);

        // Charging switch interval
        String[] chgIntervalValues = getResources().getStringArray(R.array.charging_switch_interval_values);
        for (int i = 0; i < chgIntervalValues.length; i++) {
            if (Math.abs(Float.parseFloat(chgIntervalValues[i]) - mConfig.chargingSwitchInterval) < 0.05f) {
                mSpinnerChargingSwitchInterval.setSelection(i);
                break;
            }
        }

        // Selected meters checkboxes
        mCbSwitchTime.setOnCheckedChangeListener(null);
        mCbSwitchPercent.setOnCheckedChangeListener(null);
        mCbSwitchWatts.setOnCheckedChangeListener(null);
        mCbSwitchVolts.setOnCheckedChangeListener(null);

        List<String> meters = mConfig.getChargingSwitchMetersList();
        mCbSwitchTime.setChecked(meters.contains("TIME"));
        mCbSwitchPercent.setChecked(meters.contains("PERCENT"));
        mCbSwitchWatts.setChecked(meters.contains("WATTS"));
        mCbSwitchVolts.setChecked(meters.contains("VOLTS"));

        setupMeterCheckboxes();
        updateChargingSwitchNoteVisibility();
    }

    private void setupMeterCheckboxes() {
        CompoundButton.OnCheckedChangeListener listener = (buttonView, isChecked) -> {
            int count = 0;
            if (mCbSwitchTime.isChecked()) count++;
            if (mCbSwitchPercent.isChecked()) count++;
            if (mCbSwitchWatts.isChecked()) count++;
            if (mCbSwitchVolts.isChecked()) count++;

            if (isChecked && count > 3) {
                buttonView.setChecked(false);
                Toast.makeText(this, R.string.toast_max_meters, Toast.LENGTH_SHORT).show();
            } else if (!isChecked && count < 2) {
                buttonView.setChecked(true);
                Toast.makeText(this, R.string.toast_min_meters, Toast.LENGTH_SHORT).show();
            }
        };

        mCbSwitchTime.setOnCheckedChangeListener(listener);
        mCbSwitchPercent.setOnCheckedChangeListener(listener);
        mCbSwitchWatts.setOnCheckedChangeListener(listener);
        mCbSwitchVolts.setOnCheckedChangeListener(listener);
    }

    private void updateChargingSwitchNoteVisibility() {
        String[] positionValues = getResources().getStringArray(R.array.position_values);
        int posIdx = mSpinnerPosition.getSelectedItemPosition();
        boolean isReplace = posIdx >= 0 && posIdx < positionValues.length
            && "REPLACE_PERCENT".equalsIgnoreCase(positionValues[posIdx]);
        if (mTvChargingSwitchNote != null) {
            mTvChargingSwitchNote.setVisibility(isReplace ? View.GONE : View.VISIBLE);
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

        mConfig.chargingSwitchEnabled = mSwitchChargingSwitch.isChecked();

        String[] chgIntervalValues = getResources().getStringArray(R.array.charging_switch_interval_values);
        try {
            mConfig.chargingSwitchInterval = Float.parseFloat(chgIntervalValues[mSpinnerChargingSwitchInterval.getSelectedItemPosition()]);
        } catch (Throwable e) {
            mConfig.chargingSwitchInterval = 1.0f;
        }

        List<String> selectedMeters = new ArrayList<>();
        if (mCbSwitchTime.isChecked()) selectedMeters.add("TIME");
        if (mCbSwitchPercent.isChecked()) selectedMeters.add("PERCENT");
        if (mCbSwitchWatts.isChecked()) selectedMeters.add("WATTS");
        if (mCbSwitchVolts.isChecked()) selectedMeters.add("VOLTS");
        if (selectedMeters.size() < 2) {
            selectedMeters.add("TIME");
            selectedMeters.add("PERCENT");
        }
        mConfig.chargingSwitchMeters = TextUtils.join(",", selectedMeters);

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

                String previewStr;
                boolean isCharging = mBatteryReader.isCharging();
                boolean isReplace = "REPLACE_PERCENT".equalsIgnoreCase(mConfig.position);

                if (mConfig.enabled && isReplace && mConfig.chargingSwitchEnabled && isCharging) {
                    List<String> meters = mConfig.getChargingSwitchMetersList();
                    int idx = mDiagCycleCounter % meters.size();
                    String currentMeter = meters.get(idx);
                    mDiagCycleCounter++;
                    previewStr = mEstimator.getMeterDisplay(currentMeter, mConfig)
                        + " (Cycle: " + String.format(Locale.US, "%.1fs", mConfig.chargingSwitchInterval) + ")";
                } else {
                    previewStr = mEstimator.getFormattedDisplay(mConfig, 0);
                }

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
