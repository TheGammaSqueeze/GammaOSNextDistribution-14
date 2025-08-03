/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.bluetooth.btservice;


import static java.util.Objects.requireNonNull;

import android.annotation.RequiresPermission;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.util.Log;

import com.android.bluetooth.BluetoothMetricsProto;

/** Base class for a background service that runs a Bluetooth profile */
public abstract class ProfileService extends ContextWrapper {
    private static final boolean DBG = false;

    public static final String BLUETOOTH_PERM =
            android.Manifest.permission.BLUETOOTH;
    public static final String BLUETOOTH_PRIVILEGED =
            android.Manifest.permission.BLUETOOTH_PRIVILEGED;

    public interface IProfileServiceBinder extends IBinder {
        void cleanup();
    }

    private final IProfileServiceBinder mBinder;
    private final String mName;
    private AdapterService mAdapterService;
    private boolean mAvailable = false;
    private volatile boolean mTestModeEnabled = false;

    public String getName() {
        return getClass().getSimpleName();
    }

    public boolean isAvailable() {
        return mAvailable;
    }

    public void setAvailable(boolean available) {
        mAvailable = available;
    }

    protected boolean isTestModeEnabled() {
        return mTestModeEnabled;
    }

    /**
     * Called in ProfileService constructor to init binder interface for this profile service
     *
     * @return initialized binder interface for this profile service
     */
    protected abstract IProfileServiceBinder initBinder();

    /** Start service */
    public abstract void start();

    /** Stop service */
    public abstract void stop();

    /** Called when this object is completely discarded */
    public void cleanup() {}

    /**
     * @param testModeEnabled if the profile should enter or exit a testing mode
     */
    protected void setTestModeEnabled(boolean testModeEnabled) {
        mTestModeEnabled = testModeEnabled;
    }

    protected ProfileService(Context ctx) {
        super(ctx);
        mName = getName();
        if (DBG) {
            Log.d(mName, "Service created");
        }
        mBinder = requireNonNull(initBinder(), "Binder null is not allowed for " + mName);
    }

    /** return the binder of the profile */
    public IProfileServiceBinder getBinder() {
        return mBinder;
    }

    /**
     * Set the availability of an owned/managed component (Service, Activity, Provider, etc.)
     * using a string class name assumed to be in the Bluetooth package.
     *
     * It's expected that profiles can have a set of components that they may use to provide
     * features or interact with other services/the user. Profiles are expected to enable those
     * components when they start, and disable them when they stop.
     *
     * @param className The class name of the owned component residing in the Bluetooth package
     * @param enable True to enable the component, False to disable it
     */
    @RequiresPermission(android.Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE)
    protected void setComponentAvailable(String className, boolean enable) {
        if (DBG) {
            Log.d(mName, "setComponentAvailable(className=" + className + ", enable=" + enable
                    + ")");
        }
        if (className == null) {
            return;
        }
        ComponentName component = new ComponentName(getPackageName(), className);
        setComponentAvailable(component, enable);
    }

    /**
     * Set the availability of an owned/managed component (Service, Activity, Provider, etc.)
     *
     * It's expected that profiles can have a set of components that they may use to provide
     * features or interact with other services/the user. Profiles are expected to enable those
     * components when they start, and disable them when they stop.
     *
     * @param component The component name of owned component
     * @param enable True to enable the component, False to disable it
     */
    @RequiresPermission(android.Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE)
    protected void setComponentAvailable(ComponentName component, boolean enable) {
        if (DBG) {
            Log.d(mName, "setComponentAvailable(component=" + component + ", enable=" + enable
                    + ")");
        }
        if (component == null) {
            return;
        }
        getPackageManager().setComponentEnabledSetting(
                component,
                enable ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                       : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP | PackageManager.SYNCHRONOUS);
    }

    /**
     * Support dumping profile-specific information for dumpsys
     *
     * @param sb StringBuilder from the profile.
     */
    // Suppressed since this is called from framework
    @SuppressLint("AndroidFrameworkRequiresPermission")
    public void dump(StringBuilder sb) {
        sb.append("\nProfile: ");
        sb.append(mName);
        sb.append("\n");
    }

    /**
     * Support dumping scan events from GattService
     *
     * @param builder metrics proto builder
     */
    // Suppressed since this is called from framework
    @SuppressLint("AndroidFrameworkRequiresPermission")
    public void dumpProto(BluetoothMetricsProto.BluetoothLog.Builder builder) {
        // Do nothing
    }

    /**
     * Append an indented String for adding dumpsys support to subclasses.
     *
     * @param sb StringBuilder from the profile.
     * @param s String to indent and append.
     */
    public static void println(StringBuilder sb, String s) {
        sb.append("  ");
        sb.append(s);
        sb.append("\n");
    }
}
