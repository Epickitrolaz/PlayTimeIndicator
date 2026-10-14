package com.handheld.playtime.hook;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.handheld.playtime.battery.BatteryReader;
import com.handheld.playtime.battery.PlayTimeEstimator;
import com.handheld.playtime.config.PlayTimeConfig;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class SystemUIHook implements IXposedHookLoadPackage {
    public static final String TAG = "PlayTime_SystemUI";
    private static final String MODULE_PACKAGE = "com.handheld.playtime";
    private static final String VIEW_TAG = "PLAYTIME_INDICATOR_TEXTVIEW";

    private final Set<View> mBatteryViews = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private PlayTimeConfig mConfig = new PlayTimeConfig();
    private boolean mConfigLoaded = false;
    private PlayTimeEstimator mEstimator;
    private BatteryReader mBatteryReader;
    private Context mAppContext;

    private TextView mPlayTimeView;
    private View mClockView;
    private View mBatteryMeterView;
    private ViewGroup mStatusBarView;

    private HandlerThread mWorkerThread;
    private Handler mWorkerHandler;
    private Handler mMainHandler;
    private Runnable mUpdateRunnable;

    private int mCurrentTint = Color.WHITE;
    private int mMetricMode = 0; // 0: Configured, 1: Watts, 2: Volts, 3: Battery %
    private boolean mReceiversRegistered = false;

    private static void logI(String msg) {
        Log.i(TAG, msg);
        try {
            XposedBridge.log(TAG + ": " + msg);
        } catch (Throwable ignored) {}
    }

    private static void logE(String msg, Throwable t) {
        Log.e(TAG, msg, t);
        try {
            XposedBridge.log(TAG + ": " + msg + (t != null ? " | " + t.getMessage() : ""));
        } catch (Throwable ignored) {}
    }

    private boolean isReplacePercent() {
        return mConfig != null && "REPLACE_PERCENT".equalsIgnoreCase(mConfig.position);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        logI(">>> handleLoadPackage: packageName=" + lpparam.packageName + ", processName=" + lpparam.processName);

        // 1. Self-hook for module activation check in MainActivity
        if (MODULE_PACKAGE.equals(lpparam.packageName)) {
            try {
                XposedHelpers.findAndHookMethod(
                    "com.handheld.playtime.MainActivity",
                    lpparam.classLoader,
                    "isModuleActive",
                    XC_MethodReplacement.returnConstant(true)
                );
                logI("Successfully hooked MainActivity.isModuleActive()");
            } catch (Throwable t) {
                logE("Failed to hook isModuleActive", t);
            }
            return;
        }

        // 2. Alert if user selected 'android' (System Framework)
        if ("android".equals(lpparam.packageName)) {
            logI("Loaded inside 'android' (System Framework). Make sure 'System UI' (com.android.systemui) is also checked in Vector scopes!");
            return;
        }

        // 3. SystemUI Hook
        boolean isSystemUI = "com.android.systemui".equals(lpparam.packageName)
            || (lpparam.processName != null && lpparam.processName.contains("systemui"));

        if (isSystemUI) {
            logI("Target process identified as SystemUI! Setting up status bar hooks...");

            mMainHandler = new Handler(Looper.getMainLooper());

            hookClockClass(lpparam.classLoader);
            hookBatteryMeterViewClasses(lpparam.classLoader);
            hookPhoneStatusBarView(lpparam.classLoader, lpparam.packageName);
        }
    }

    private void hookClockClass(ClassLoader classLoader) {
        Class<?> clockClass = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.policy.Clock", classLoader);
        if (clockClass == null) {
            clockClass = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.phone.Clock", classLoader);
        }

        if (clockClass != null) {
            logI("Found Clock class: " + clockClass.getName());
            XposedBridge.hookAllConstructors(clockClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    final View clock = (View) param.thisObject;
                    clock.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                        @Override
                        public void onViewAttachedToWindow(View v) {
                            mClockView = v;
                            initServicesIfNeeded(v.getContext());
                            applyPositionAndText();
                        }

                        @Override
                        public void onViewDetachedFromWindow(View v) {}
                    });
                }
            });
        }
    }

    private void hookBatteryMeterViewClasses(ClassLoader classLoader) {
        String[] candidates = {
            "com.android.systemui.battery.BatteryMeterView",
            "com.android.systemui.BatteryMeterView",
            "com.android.systemui.statusbar.policy.BatteryMeterView",
            "com.android.systemui.statusbar.phone.BatteryMeterView"
        };

        for (String className : candidates) {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, classLoader);
            if (clazz != null) {
                logI("Found BatteryMeterView class: " + className);
                hookBatteryMeterView(clazz);
            }
        }

        Class<?> controllerClass = XposedHelpers.findClassIfExists(
            "com.android.systemui.battery.BatteryMeterViewController",
            classLoader
        );
        if (controllerClass != null) {
            hookBatteryMeterViewController(controllerClass);
        }
    }

    private void hookBatteryMeterView(Class<?> batteryMeterViewClass) {
        // Hook all constructors
        XposedBridge.hookAllConstructors(batteryMeterViewClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                final View battery = (View) param.thisObject;
                mBatteryViews.add(battery);

                battery.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {
                        mBatteryViews.add(v);
                        mBatteryMeterView = v;
                        initServicesIfNeeded(v.getContext());
                        applyPositionAndText();
                    }

                    @Override
                    public void onViewDetachedFromWindow(View v) {
                        mBatteryViews.remove(v);
                    }
                });

                if (battery.isAttachedToWindow()) {
                    mBatteryViews.add(battery);
                    mBatteryMeterView = battery;
                    initServicesIfNeeded(battery.getContext());
                    applyPositionAndText();
                }
            }
        });

        // Hook onDarkChanged for tint matching
        try {
            XposedHelpers.findAndHookMethod(
                batteryMeterViewClass,
                "onDarkChanged",
                ArrayList.class,
                float.class,
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        mCurrentTint = (int) param.args[2];
                        if (mPlayTimeView != null) {
                            mPlayTimeView.setTextColor(mCurrentTint);
                        }
                    }
                }
            );
        } catch (Throwable ignored) {}

        // Hook updateColors (Android 12+)
        try {
            Method updateColorsMethod = XposedHelpers.findMethodExactIfExists(
                batteryMeterViewClass, "updateColors", int.class, int.class, int.class
            );
            if (updateColorsMethod != null) {
                XposedBridge.hookMethod(updateColorsMethod, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        mCurrentTint = (int) param.args[0];
                        if (mPlayTimeView != null) {
                            mPlayTimeView.setTextColor(mCurrentTint);
                        }
                    }
                });
            }
        } catch (Throwable ignored) {}

        // Hook updatePercentText to replace text in REPLACE_PERCENT mode
        try {
            XposedHelpers.findAndHookMethod(
                batteryMeterViewClass,
                "updatePercentText",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (mConfig != null && mConfig.enabled && isReplacePercent()) {
                            try {
                                TextView percentView = (TextView) XposedHelpers.getObjectField(param.thisObject, "mBatteryPercentView");
                                if (percentView != null && mEstimator != null) {
                                    String text = mEstimator.getFormattedDisplay(mConfig, mMetricMode);
                                    percentView.setText(text);
                                    percentView.setVisibility(View.VISIBLE);
                                    setupTapListener(percentView);
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            );
        } catch (Throwable ignored) {}

        // Hook setPercentTextAtCurrentLevel
        try {
            XposedHelpers.findAndHookMethod(
                batteryMeterViewClass,
                "setPercentTextAtCurrentLevel",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (mConfig != null && mConfig.enabled && isReplacePercent()) {
                            try {
                                TextView percentView = (TextView) XposedHelpers.getObjectField(param.thisObject, "mBatteryPercentView");
                                if (percentView != null && mEstimator != null) {
                                    String text = mEstimator.getFormattedDisplay(mConfig, mMetricMode);
                                    percentView.setText(text);
                                    setupTapListener(percentView);
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            );
        } catch (Throwable ignored) {}

        // Hook updateShowPercent so percent view is forced to inflate when REPLACE_PERCENT is active
        try {
            XposedHelpers.findAndHookMethod(
                batteryMeterViewClass,
                "updateShowPercent",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (mConfig != null && mConfig.enabled && isReplacePercent()) {
                            // MODE_ON = 1 forces BatteryMeterView to create mBatteryPercentView
                            XposedHelpers.setIntField(param.thisObject, "mShowPercentMode", 1);
                        }
                    }
                }
            );
        } catch (Throwable ignored) {}
    }

    private void hookBatteryMeterViewController(Class<?> controllerClass) {
        try {
            XposedHelpers.findAndHookMethod(
                controllerClass,
                "onViewAttached",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            View view = (View) XposedHelpers.getObjectField(param.thisObject, "mView");
                            if (view != null) {
                                mBatteryViews.add(view);
                                mBatteryMeterView = view;
                                initServicesIfNeeded(view.getContext());
                                applyPositionAndText();
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            );
        } catch (Throwable ignored) {}
    }

    private void hookPhoneStatusBarView(ClassLoader classLoader, final String packageName) {
        Class<?> statusBarViewClass = XposedHelpers.findClassIfExists(
            "com.android.systemui.statusbar.phone.PhoneStatusBarView",
            classLoader
        );
        if (statusBarViewClass == null) return;

        logI("Found PhoneStatusBarView class: " + statusBarViewClass.getName());

        XposedBridge.hookAllConstructors(statusBarViewClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                final ViewGroup statusBarView = (ViewGroup) param.thisObject;
                statusBarView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {
                        mStatusBarView = (ViewGroup) v;
                        initServicesIfNeeded(v.getContext());
                        statusBarView.post(() -> applyPositionAndText());
                    }

                    @Override
                    public void onViewDetachedFromWindow(View v) {}
                });
            }
        });
    }

    private synchronized void initServicesIfNeeded(Context context) {
        if (context == null || mAppContext != null) return;

        mAppContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;

        // TIER 1: Native Android Settings.Global (100% persistent across reboots, 0ms boot latency)
        PlayTimeConfig loaded = PlayTimeConfig.loadFromSettingsGlobal(mAppContext);

        // TIER 2: XSharedPreferences via LSPosed/Vector bridge
        if (loaded == null) {
            loaded = PlayTimeConfig.loadFromXposedPrefs();
        }

        // TIER 3: Local disk cache in SystemUI's private storage
        if (loaded == null) {
            loaded = PlayTimeConfig.loadFromLocalCache(mAppContext);
        }

        // TIER 4: ContentProvider
        if (loaded == null) {
            loaded = PlayTimeConfig.loadFromProvider(mAppContext);
        }

        if (loaded != null) {
            mConfig = loaded;
            mConfigLoaded = true;
            PlayTimeConfig.saveToSettingsGlobal(mAppContext, mConfig);
            logI("Active config successfully loaded on boot: pos=" + mConfig.position + ", detail=" + mConfig.detailMode + ", symbol=" + mConfig.chargingSymbol);
        } else {
            mConfig = new PlayTimeConfig();
            mConfigLoaded = false;
            logI("No saved config found yet; using defaults until first sync");
        }

        mBatteryReader = new BatteryReader(mAppContext);
        mEstimator = new PlayTimeEstimator(mBatteryReader);
        registerReceivers(mAppContext);
        startSamplingLoop();
    }

    private synchronized void ensurePlayTimeViewCreated(Context context) {
        if (mPlayTimeView != null) return;

        mPlayTimeView = new TextView(context);
        mPlayTimeView.setTag(VIEW_TAG);
        mPlayTimeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, mConfig.fontSize);
        mPlayTimeView.setTextColor(mCurrentTint);
        mPlayTimeView.setGravity(Gravity.CENTER_VERTICAL);
        mPlayTimeView.setSingleLine(true);
        mPlayTimeView.setIncludeFontPadding(false);

        int padH = dpToPx(context, 4);
        mPlayTimeView.setPadding(padH, 0, padH, 0);
        mPlayTimeView.setShadowLayer(1.5f, 0.5f, 0.5f, 0x80000000);

        setupTapListener(mPlayTimeView);
    }

    private void setupTapListener(View view) {
        if (view == null) return;
        view.setOnClickListener(v -> {
            if (mConfig != null && mConfig.tapToggle) {
                mMetricMode = (mMetricMode + 1) % 4; // 0: Configured, 1: Watts, 2: Volts, 3: Battery %
                applyPositionAndText();
            }
        });
    }

    private synchronized void applyPositionAndText() {
        if (mAppContext == null || mEstimator == null || mConfig == null) return;

        final String text = mEstimator.getFormattedDisplay(mConfig, mMetricMode);

        mMainHandler.post(() -> {
            try {
                if (!mConfig.enabled) {
                    if (mPlayTimeView != null) mPlayTimeView.setVisibility(View.GONE);
                    return;
                }

                // Check also if battery view was discovered from status bar
                View foundBat = findBatteryView();
                if (foundBat != null) {
                    mBatteryViews.add(foundBat);
                }

                // CASE 1: Replace Battery Percentage Mode
                if (isReplacePercent()) {
                    if (mPlayTimeView != null) {
                        mPlayTimeView.setVisibility(View.GONE);
                        if (mPlayTimeView.getParent() != null) {
                            ((ViewGroup) mPlayTimeView.getParent()).removeView(mPlayTimeView);
                        }
                    }

                    for (View bView : mBatteryViews) {
                        if (bView != null && bView.isAttachedToWindow()) {
                            try {
                                TextView percentView = (TextView) XposedHelpers.getObjectField(bView, "mBatteryPercentView");
                                if (percentView != null) {
                                    percentView.setText(text);
                                    percentView.setVisibility(View.VISIBLE);
                                    setupTapListener(percentView);
                                } else {
                                    XposedHelpers.callMethod(bView, "updatePercentView");
                                }
                            } catch (Throwable t) {
                                logE("Error updating mBatteryPercentView in replace mode", t);
                            }
                        }
                    }
                    return;
                }

                // CASE 2: Standalone Indicator next to Battery or Clock
                // When not in replace mode, restore stock percentage display on battery views
                for (View bView : mBatteryViews) {
                    if (bView != null && bView.isAttachedToWindow()) {
                        try {
                            XposedHelpers.callMethod(bView, "updatePercentText");
                        } catch (Throwable ignored) {}
                    }
                }

                ensurePlayTimeViewCreated(mAppContext);
                mPlayTimeView.setText(text);
                mPlayTimeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, mConfig.fontSize);
                mPlayTimeView.setVisibility(View.VISIBLE);

                View targetAnchor = null;
                boolean isLeft = true;

                String pos = mConfig.position != null ? mConfig.position : "BATTERY_LEFT";
                if ("CLOCK_LEFT".equalsIgnoreCase(pos) || "CLOCK_RIGHT".equalsIgnoreCase(pos)) {
                    isLeft = "CLOCK_LEFT".equalsIgnoreCase(pos);
                    targetAnchor = findClockView();
                } else {
                    isLeft = !"BATTERY_RIGHT".equalsIgnoreCase(pos);
                    targetAnchor = findBatteryView();
                }

                if (targetAnchor != null && targetAnchor.getParent() instanceof ViewGroup) {
                    ViewGroup parent = (ViewGroup) targetAnchor.getParent();
                    int targetIndex = parent.indexOfChild(targetAnchor);
                    int insertIndex = isLeft ? targetIndex : targetIndex + 1;

                    if (mPlayTimeView.getParent() != parent || parent.indexOfChild(mPlayTimeView) != insertIndex) {
                        if (mPlayTimeView.getParent() != null) {
                            ((ViewGroup) mPlayTimeView.getParent()).removeView(mPlayTimeView);
                            targetIndex = parent.indexOfChild(targetAnchor);
                            insertIndex = isLeft ? targetIndex : targetIndex + 1;
                        }

                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        );
                        lp.gravity = Gravity.CENTER_VERTICAL;
                        int margin = dpToPx(parent.getContext(), 4);
                        if (isLeft) {
                            lp.rightMargin = margin;
                            lp.leftMargin = dpToPx(parent.getContext(), 2);
                        } else {
                            lp.leftMargin = margin;
                            lp.rightMargin = dpToPx(parent.getContext(), 2);
                        }

                        int finalIdx = Math.max(0, Math.min(insertIndex, parent.getChildCount()));
                        parent.addView(mPlayTimeView, finalIdx, lp);
                        logI(">>> Injected PlayTimeView next to " + targetAnchor.getClass().getSimpleName() + " (isLeft=" + isLeft + ", pos=" + pos + ")");
                    }

                    int color = resolveColor(targetAnchor, parent);
                    mPlayTimeView.setTextColor(color);
                }
            } catch (Throwable t) {
                logE("Error applying indicator position", t);
            }
        });
    }

    private View findClockView() {
        if (mClockView != null && mClockView.isAttachedToWindow()) {
            return mClockView;
        }
        if (mStatusBarView != null) {
            String pkg = mStatusBarView.getContext().getPackageName();
            int resId = mStatusBarView.getResources().getIdentifier("clock", "id", pkg);
            if (resId != 0) {
                View v = mStatusBarView.findViewById(resId);
                if (v != null) {
                    mClockView = v;
                    return v;
                }
            }
        }
        return null;
    }

    private View findBatteryView() {
        if (mBatteryMeterView != null && mBatteryMeterView.isAttachedToWindow()) {
            return mBatteryMeterView;
        }
        if (mStatusBarView != null) {
            String pkg = mStatusBarView.getContext().getPackageName();
            int resId = mStatusBarView.getResources().getIdentifier("battery", "id", pkg);
            if (resId == 0) {
                resId = mStatusBarView.getResources().getIdentifier("batteryRemainingIcon", "id", pkg);
            }
            if (resId != 0) {
                View v = mStatusBarView.findViewById(resId);
                if (v != null) {
                    mBatteryMeterView = v;
                    return v;
                }
            }
        }
        return null;
    }

    private int resolveColor(View anchor, ViewGroup parent) {
        if (anchor instanceof TextView) {
            return ((TextView) anchor).getCurrentTextColor();
        }
        if (anchor != null) {
            try {
                int textColor = XposedHelpers.getIntField(anchor, "mTextColor");
                if (textColor != 0) return textColor;
            } catch (Throwable ignored) {}

            try {
                int fgColor = XposedHelpers.getIntField(anchor, "mNonAdaptedForegroundColor");
                if (fgColor != 0) return fgColor;
            } catch (Throwable ignored) {}
        }
        return mCurrentTint != 0 ? mCurrentTint : Color.WHITE;
    }

    private void registerReceivers(Context context) {
        if (mReceiversRegistered) return;
        mReceiversRegistered = true;

        IntentFilter filter = new IntentFilter();
        filter.addAction(PlayTimeConfig.ACTION_UPDATE_CONFIG);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_BOOT_COMPLETED);
        filter.addAction(Intent.ACTION_USER_PRESENT);

        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent == null || intent.getAction() == null) return;

                String action = intent.getAction();
                if (PlayTimeConfig.ACTION_UPDATE_CONFIG.equals(action)) {
                    mConfig = PlayTimeConfig.fromBundle(intent.getExtras());
                    mConfigLoaded = true;
                    if (mAppContext != null) {
                        PlayTimeConfig.saveToSettingsGlobal(mAppContext, mConfig);
                        mConfig.saveToLocalCache(mAppContext);
                    }
                    logI("Received config update! Saved to Settings.Global: pos=" + mConfig.position + ", detail=" + mConfig.detailMode + ", chg=" + mConfig.chargingSymbol);
                    applyPositionAndText();
                } else if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_USER_PRESENT.equals(action)) {
                    syncConfigFromProviderAsync();
                } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    if (mEstimator != null) {
                        mEstimator.onScreenStateChanged(true);
                    }
                    startSamplingLoop();
                } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    if (mEstimator != null) {
                        mEstimator.onScreenStateChanged(false);
                    }
                    if (mWorkerHandler != null && mUpdateRunnable != null) {
                        mWorkerHandler.removeCallbacks(mUpdateRunnable);
                    }
                } else if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                    applyPositionAndText();
                }
            }
        }, filter);
    }

    private void syncConfigFromProviderAsync() {
        if (mWorkerHandler != null && mAppContext != null) {
            mWorkerHandler.post(() -> {
                try {
                    // Check Settings.Global
                    PlayTimeConfig global = PlayTimeConfig.loadFromSettingsGlobal(mAppContext);
                    if (global != null) {
                        mConfig = global;
                        mConfigLoaded = true;
                        mMainHandler.post(() -> applyPositionAndText());
                        return;
                    }
                    // Check XSharedPreferences
                    PlayTimeConfig xsp = PlayTimeConfig.loadFromXposedPrefs();
                    if (xsp != null) {
                        mConfig = xsp;
                        mConfigLoaded = true;
                        PlayTimeConfig.saveToSettingsGlobal(mAppContext, mConfig);
                        mMainHandler.post(() -> applyPositionAndText());
                        return;
                    }
                    // Check ContentProvider
                    PlayTimeConfig fromProv = PlayTimeConfig.loadFromProvider(mAppContext);
                    if (fromProv != null) {
                        mConfig = fromProv;
                        mConfigLoaded = true;
                        PlayTimeConfig.saveToSettingsGlobal(mAppContext, mConfig);
                        mConfig.saveToLocalCache(mAppContext);
                        logI("Synced config from provider on boot/unlock: pos=" + mConfig.position);
                        mMainHandler.post(() -> applyPositionAndText());
                    }
                } catch (Throwable t) {
                    logE("Failed to sync config asynchronously", t);
                }
            });
        }
    }

    private synchronized void startSamplingLoop() {
        if (mWorkerThread == null) {
            mWorkerThread = new HandlerThread("PlayTimeSampler");
            mWorkerThread.start();
            mWorkerHandler = new Handler(mWorkerThread.getLooper());
        }

        if (mUpdateRunnable != null) {
            mWorkerHandler.removeCallbacks(mUpdateRunnable);
        }

        mUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if (!mConfigLoaded && mAppContext != null) {
                        syncConfigFromProviderAsync();
                    }

                    if (mEstimator != null && mConfig != null) {
                        mEstimator.recordSample(mConfig);
                        applyPositionAndText();
                    }
                } catch (Throwable t) {
                    logE("Error in sampling loop", t);
                }

                int intervalMs = Math.max(2, mConfig.intervalSeconds) * 1000;
                if (mWorkerHandler != null) {
                    mWorkerHandler.postDelayed(this, intervalMs);
                }
            }
        };

        mWorkerHandler.post(mUpdateRunnable);
    }

    private static int dpToPx(Context context, int dp) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics()
        );
    }
}
