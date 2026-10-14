package com.handheld.playtime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.handheld.playtime.config.PlayTimeConfig;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "PlayTime_Boot";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Log.i(TAG, "Boot-related broadcast received: " + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            try {
                PlayTimeConfig config = PlayTimeConfig.fromSharedPreferences(context);
                config.sendUpdateBroadcast(context);
                Log.i(TAG, "Dispatched boot config to SystemUI: pos=" + config.position + ", detail=" + config.detailMode);
            } catch (Throwable t) {
                Log.e(TAG, "Error in BootReceiver dispatch: " + t.getMessage(), t);
            }
        }
    }
}
