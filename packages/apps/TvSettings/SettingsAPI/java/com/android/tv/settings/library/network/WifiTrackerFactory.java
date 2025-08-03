package com.android.tv.settings.library.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;

public class WifiTrackerFactory {
    private static WifiTracker sTestingWifiTracker;

    @Keep // Keep proguard from stripping this method since it is only used in tests
    public static void setTestingWifiTracker(WifiTracker tracker) {
        sTestingWifiTracker = tracker;
    }

    public static WifiTracker create(
            Context context, WifiTracker.WifiListener wifiListener, @NonNull Lifecycle lifecycle,
            boolean includeSaved, boolean includeScans) {
        if(sTestingWifiTracker != null) {
            return sTestingWifiTracker;
        }
        return new WifiTracker(context, wifiListener, lifecycle,
                context.getSystemService(WifiManager.class),
                context.getSystemService(ConnectivityManager.class));
    }

}
