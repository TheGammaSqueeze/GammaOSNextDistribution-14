/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tv.settings.basic;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.tv.settings.util.ResourcesUtil;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Optional;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

/**
 * Implementation of {@link BasicModeFeatureProvider}.
 */
public class BasicModeFeatureProviderImplX implements BasicModeFeatureProvider {

    private static final String TAG = "BasicModeFeatureX";

    // The string "offline_mode" is a static protocol and should not be changed in general.
    private static final String KEY_BASIC_MODE = "offline_mode";

    private static final String OEM_AUTHORITY = "tvlauncher.config";
    private static final String OEM_CONTRACT_SCHEME = "content";
    private static final String OEM_CONFIG_PATH = "configuration";
    private static final String DEVICE_MODE = "device-mode";
    private static final String STORE_DEMO = "store_demo";
    private static final String VALUE = "value";

    @Override
    public boolean isBasicMode(@NonNull Context context) {
        final String providerUriString = ResourcesUtil.getString(context,
                "basic_mode_provider_uri");
        if (TextUtils.isEmpty(providerUriString)) {
            Log.e(TAG, "ContentProvider for basic mode is undefined.");
            return false;
        }
        try {
            Uri contentUri = Uri.parse(providerUriString);
            Cursor cursor = context.getContentResolver().query(contentUri, null, null, null);
            if (cursor != null && cursor.getCount() != 0) {
                cursor.moveToFirst();
                String basicMode = cursor.getString(cursor.getColumnIndex(KEY_BASIC_MODE));
                return "1".equals(basicMode);
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            Log.e(TAG, "Unable to query the ContentProvider for basic mode.", e);
            return false;
        }
        return false;
    }

    @Override
    public void startBasicModeExitActivity(@NonNull Activity activity) {
        final String basicModeExit = ResourcesUtil.getString(activity, "basic_mode_exit_data");
        startBasicModeExitActivity(activity, basicModeExit);
    }

    @Override
    public void startBasicModeInternetBlock(@NonNull Activity activity) {
        final String basicModeExit = ResourcesUtil.getString(activity, "basic_mode_exit_internet");
        startBasicModeExitActivity(activity, basicModeExit);
    }

    private void startBasicModeExitActivity(@NonNull Activity activity, String basicModeExitType) {
        final String basicModeExitPackage = ResourcesUtil.getString(activity,
                "basic_mode_exit_package");
        final String basicModeExitComponent =
                ResourcesUtil.getString(activity, "basic_mode_exit_component");
        if (TextUtils.isEmpty(basicModeExitPackage) || TextUtils.isEmpty(basicModeExitComponent)
                || TextUtils.isEmpty(basicModeExitType)) {
            Log.e(TAG, "Basic mode exit activity undefined.");
            return;
        }
        ComponentName componentName =
                new ComponentName(basicModeExitPackage, basicModeExitComponent);
        Uri dataUri = Uri.parse(basicModeExitType);
        Intent intent = new Intent().setComponent(componentName).setData(dataUri);
        List<ResolveInfo> intentHandlingActivities =
                activity.getPackageManager().queryIntentActivities(intent, 0);
        for (ResolveInfo info : intentHandlingActivities) {
            if (info.activityInfo != null && info.activityInfo.enabled) {
                Log.d(TAG, "Starting basic mode exit activity.");
                activity.startActivity(intent);
                if (!activity.isFinishing()) {
                    // We finish TvSettings instead of leaving it dangling in the activity stack
                    // as the expected Activity for handling basic mode exit is a HOME that also
                    // intercepts BACK key pressing.
                    activity.finish();
                }
            }
        }
        Log.e(TAG, "Basic mode exit activity not found.");
    }

    @Override
    public boolean isStoreDemoMode(@NonNull Context context) {
        if (getCustomizationAppPackageName(context).isEmpty()) {
            // There is no customizations apk for this device.
            return false;
        }
        Uri oemUri =
                new Uri.Builder()
                        .scheme(OEM_CONTRACT_SCHEME)
                        .authority(OEM_AUTHORITY)
                        .path(OEM_CONFIG_PATH)
                        .build();
        try (InputStream inputStream =
                     context.getContentResolver().openInputStream(oemUri)) {
            if (inputStream == null) {
                return false;
            }
            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setInput(new InputStreamReader(inputStream, UTF_8));
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    if (DEVICE_MODE.equals(parser.getName())) {
                        Log.e(TAG, "Got is Store Demo: " + STORE_DEMO.equals(
                                parser.getAttributeValue(null, VALUE)));
                        return STORE_DEMO.equals(parser.getAttributeValue(null, VALUE));
                    }
                }
                eventType = parser.next();
            }
            return false;
        } catch (Exception e) {
            // Fallback to default mode if we can't get a value.
            Log.e(TAG, "Unable to determine store demo mode", e);
            return false;
        }
    }

    private Optional<String> getCustomizationAppPackageName(@NonNull Context context) {
        return Optional.ofNullable(
                        context
                                .getPackageManager()
                                .resolveContentProvider(
                                        OEM_AUTHORITY, /* flags= */ 0))
                .filter(providerInfo -> hasSystemAppFlags(providerInfo.applicationInfo))
                .map(providerInfo -> providerInfo.applicationInfo.packageName);
    }

    private static boolean hasSystemAppFlags(ApplicationInfo info) {
        return (info.flags & (ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
                | ApplicationInfo.FLAG_SYSTEM))
                != 0;
    }
}
