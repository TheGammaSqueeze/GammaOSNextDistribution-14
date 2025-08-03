package android.provider.cts.contacts.testapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class BootReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = "CallLogTestBootReceiver";
    public static final String BOOT_COMPLETE = "boot_complete";
    public static final String LOCKED_BOOT_COMPLETE = "locked_boot_complete";
    public static final String SHARED_PREFS_NAME = "boot_complete_info";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences prefs = context.createDeviceProtectedStorageContext()
                    .getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(LOCKED_BOOT_COMPLETE, true).commit();
            Log.i(LOG_TAG, "Received locked boot complete");
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences dePrefs = context.createDeviceProtectedStorageContext()
                    .getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences cePrefs = context.createCredentialProtectedStorageContext()
                    .getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
            dePrefs.edit().putBoolean(BOOT_COMPLETE, true).commit();
            cePrefs.edit().putBoolean(BOOT_COMPLETE, true)
                    .putBoolean(LOCKED_BOOT_COMPLETE, true).commit();
            Log.i(LOG_TAG, "Received boot complete");
        }
    }

    public static boolean waitForBootComplete(Context context, String action, long timeoutMillis)
            throws Exception {
        SharedPreferences prefs =
                context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        CompletableFuture<Void> onBootCompleteChanged = new CompletableFuture<>();
        prefs.registerOnSharedPreferenceChangeListener(
                (sharedPreferences, key) -> {
                    if (action.equals(key)) {
                        onBootCompleteChanged.complete(null);
                    }
                });
        if (prefs.getBoolean(action, false)) return true;
        try {
            onBootCompleteChanged.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            Log.w(LOG_TAG, "Timed out waiting for " + action);
            return false;
        }
        return true;
    }
}
