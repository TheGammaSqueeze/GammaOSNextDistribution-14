package com.gammaos.screenmapper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;
import android.util.Log;

/**
 * Starts the overlay service on boot if screen mapping was enabled.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "ScreenMapBoot";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            String active = SystemProperties.get("persist.gammaos.screenmap.enabled", "0");
            if ("1".equals(active)) {
                Log.i(TAG, "Screen mapping enabled, starting overlay service");
                Intent svc = new Intent();
                svc.setClassName("com.gammaos.screenmapper",
                        "com.gammaos.screenmapper.ScreenMapOverlayService");
                svc.putExtra("mode", 1 /* MODE_PLAY */);
                try {
                    context.startForegroundService(svc);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start overlay service", e);
                }
            }
        }
    }
}
