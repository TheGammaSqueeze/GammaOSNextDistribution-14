/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.server.wm;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.app.ActivityOptions;
import android.car.builtin.util.Slogf;
import android.car.feature.Flags;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.ServiceSpecificException;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

/**
 * This class handles launching the display compat host app.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class CarDisplayCompatActivityInterceptor implements CarActivityInterceptorUpdatable {

    public static final String TAG = CarDisplayCompatActivityInterceptor.class.getSimpleName();
    private static final String DISPLAYCOMPAT_SYSTEM_FEATURE = "android.car.displaycompatibility";
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);
    private static final ActivityOptionsWrapper EMPTY_LAUNCH_OPTIONS_WRAPPER =
            ActivityOptionsWrapper.create(ActivityOptions.makeBasic());
    @VisibleForTesting
    static final String LAUNCHED_FROM_HOST =
            "android.car.app.CarDisplayCompatManager.launched_from_host";
    @VisibleForTesting
    static final String LAUNCH_ACTIVITY_OPTIONS =
            "android.car.app.CarDisplayCompatManager.launch_activity_options";
    @VisibleForTesting
    static final String PERMISSION_DISPLAY_COMPATIBILITY =
            "android.car.permission.QUERY_DISPLAY_COMPATIBILITY";
    @NonNull
    private final Context mContext;
    @NonNull
    private final CarDisplayCompatScaleProviderUpdatable mDisplayCompatProvider;
    @Nullable
    private ComponentName mHostActivity;

    public CarDisplayCompatActivityInterceptor(@NonNull Context context,
            @NonNull CarDisplayCompatScaleProviderUpdatable carDisplayCompatProvider) {
        mContext = context;
        mDisplayCompatProvider = carDisplayCompatProvider;
        if (!Flags.displayCompatibility()) {
            Slogf.i(TAG, Flags.FLAG_DISPLAY_COMPATIBILITY + " is not enabled");
            return;
        }
        PackageManager packageManager = context.getPackageManager();
        if (packageManager != null
                && !packageManager.hasSystemFeature(DISPLAYCOMPAT_SYSTEM_FEATURE)) {
            Slogf.i(TAG, DISPLAYCOMPAT_SYSTEM_FEATURE + " is not available");
            return;
        }
        Resources r = context.getResources();
        if (r == null) {
            // This happens during tests where mock context is used.
            Slogf.e(TAG, "Couldn't read DisplayCompat host activity.");
            return;
        }
        int id = r.getIdentifier("config_defaultDisplayCompatHostActivity", "string", "android");
        if (id != 0) {
            mHostActivity = ComponentName.unflattenFromString(r.getString(id));
            if (mHostActivity == null) {
                Slogf.e(TAG, "Couldn't read DisplayCompat host activity.");
            }
        }
    }

    @Override
    public ActivityInterceptResultWrapper onInterceptActivityLaunch(
            ActivityInterceptorInfoWrapper info) {
        if (mHostActivity == null) {
            return null;
        }
        Intent launchIntent = info.getIntent();
        if (launchIntent == null || launchIntent.getComponent() == null) {
            return null;
        }
        try {
            boolean requiresDisplayCompat = mDisplayCompatProvider
                    .requiresDisplayCompat(launchIntent.getComponent().getPackageName(),
                            info.getUserId());
            if (!requiresDisplayCompat) {
                return null;
            }

            boolean isLaunchedFromHost = launchIntent
                    .getBooleanExtra(LAUNCHED_FROM_HOST, false);
            int callingPid = info.getCallingPid();
            int callingUid = info.getCallingUid();
            boolean hasPermission = (mContext.checkPermission(
                    PERMISSION_DISPLAY_COMPATIBILITY, callingPid, callingUid)
                            == PERMISSION_GRANTED);
            if (isLaunchedFromHost && !hasPermission) {
                Slogf.e(TAG, "Calling package (%s) doesn't have required permissions %s",
                        info.getCallingPackage(),
                        PERMISSION_DISPLAY_COMPATIBILITY);
                // fall-through, we'll launch the host instead.
            }

            ActivityOptionsWrapper launchOptions = info.getCheckedOptions();
            if (launchOptions == null) {
                launchOptions = EMPTY_LAUNCH_OPTIONS_WRAPPER;
            }
            if (!isLaunchedFromHost || (isLaunchedFromHost && !hasPermission)) {
                // Launch the host
                Intent intent = new Intent();
                intent.setComponent(mHostActivity);

                intent.putExtra(Intent.EXTRA_INTENT, launchIntent);
                intent.putExtra(LAUNCH_ACTIVITY_OPTIONS, launchOptions.getOptions().toBundle());

                // Launch host on the display that the app was supposed to be launched.
                ActivityOptionsWrapper optionsWrapper =
                        ActivityOptionsWrapper.create(ActivityOptions.makeBasic());
                int hostDisplayId = launchOptions.getOptions().getLaunchDisplayId();
                if (DBG) {
                    Slogf.d(TAG, "DisplayCompat host displayId %d LaunchDisplayId %d",
                            hostDisplayId, launchOptions.getOptions().getLaunchDisplayId());
                }
                optionsWrapper.setLaunchDisplayId(hostDisplayId);
                return ActivityInterceptResultWrapper.create(intent, optionsWrapper.getOptions());
            }
        } catch (ServiceSpecificException e) {
            Slogf.e(TAG, "Error while intercepting activity " + launchIntent.getComponent(), e);
        }

        return null;
    }
}
