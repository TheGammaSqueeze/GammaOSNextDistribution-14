package org.lineageos.netflixconfig;

import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

public class NetflixBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "NetflixBroadcastReceiver";

    // Setting name
    private static final String NRDP_PLATFORM_CAP = "nrdp_platform_capabilities";
    private static final String NRDP_AUDIO_CAP    = "nrdp_audio_platform_capabilities";
    private static final String NRDP_VIDEO_CAP    = "nrdp_video_platform_capabilities";

    // Shared preference name
    private static final String SAVED_BUILD_DATE = "saved_build_date";

    @Override
    public void onReceive(Context context, Intent intent) {
        String buildDate = Build.VERSION.INCREMENTAL;
        boolean needUpdate = !buildDate.equals(getSavedBuildDate(context));

        // This also includes first boot
        if (needUpdate) {
            setNrdpCapabilites(context, NRDP_PLATFORM_CAP,
                context.getString(R.string.nrdp_platform_capabilities));
            setNrdpCapabilites(context, NRDP_AUDIO_CAP,
                context.getString(R.string.nrdp_audio_platform_capabilities));
            setNrdpCapabilites(context, NRDP_VIDEO_CAP,
                context.getString(R.string.nrdp_video_platform_capabilities));
            setSavedBuildDate(context, buildDate);
        }
    }

    private static String getSavedBuildDate(Context ctx) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        return sp.getString(SAVED_BUILD_DATE, "");
    }

    private static void setSavedBuildDate(Context ctx, String date) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(SAVED_BUILD_DATE, date);
        editor.apply();
    }

    private void setNrdpCapabilites(Context ctx, String setting, String cap) {
        Settings.Global.putString(ctx.getContentResolver(), setting, cap);
        Log.i(TAG, setting + ": " + cap);
    }
}
