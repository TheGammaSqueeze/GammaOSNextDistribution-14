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

package com.android.car.settings.location;

import static com.android.car.ui.preference.CarUiTwoActionTextPreference.SECONDARY_ACTION_STYLE_BORDERLESS;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.IconDrawableFactory;

import com.android.car.settings.R;
import com.android.car.settings.common.Logger;
import com.android.car.ui.preference.CarUiTwoActionTextPreference;

/** Utilities related to location-powered ADAS privacy policy. */
public final class AdasPrivacyPolicyUtil {
    private static final Logger LOG = new Logger(AdasPrivacyPolicyUtil.class);
    private static final String PRIVACY_POLICY_KEY = "privacy_policy";

    private AdasPrivacyPolicyUtil() {}

    /**
     * Creates a {@link CarUiTwoActionTextPreference} for ADAS app with it's privacy policy and a
     * link to its location permission settings.
     */
    public static CarUiTwoActionTextPreference createPrivacyPolicyPreference(
            Context context, PackageManager packageManager, String pkgName, UserHandle userHandle) {
        CarUiTwoActionTextPreference pref =
                new CarUiTwoActionTextPreference(context, SECONDARY_ACTION_STYLE_BORDERLESS);

        IconDrawableFactory drawableFactory = IconDrawableFactory.newInstance(context);
        int userId = userHandle.getIdentifier();

        ApplicationInfo appInfo;
        try {
            appInfo =
                    packageManager.getApplicationInfoAsUser(
                            pkgName, PackageManager.GET_META_DATA, userId);
        } catch (NameNotFoundException ex) {
            LOG.e("Failed to get application info for " + pkgName);
            return null;
        }

        pref.setIcon(drawableFactory.getBadgedIcon(appInfo, userId));

        CharSequence appLabel = packageManager.getApplicationLabel(appInfo);
        CharSequence badgedAppLabel = packageManager.getUserBadgedLabel(appLabel, userHandle);
        pref.setTitle(badgedAppLabel);

        pref.setOnPreferenceClickListener(
                preference -> {
                    Intent intent = new Intent(Intent.ACTION_MANAGE_APP_PERMISSION);
                    intent.putExtra(
                            Intent.EXTRA_PERMISSION_GROUP_NAME, Manifest.permission_group.LOCATION);
                    intent.putExtra(Intent.EXTRA_PACKAGE_NAME, pkgName);
                    intent.putExtra(Intent.EXTRA_USER, userHandle);
                    context.startActivity(intent);
                    return true;
                });

        pref.setSecondaryActionText(R.string.location_driver_assistance_privacy_policy_button_text);

        Bundle bundle = appInfo.metaData;

        if (bundle == null) {
            LOG.e(pkgName + "doesn't provide meta data in manifest");
            return pref;
        }
        CharSequence privacyPolicyLink = bundle.getCharSequence(PRIVACY_POLICY_KEY);
        if (privacyPolicyLink == null) {
            LOG.e(pkgName + " doesn't provide privacy policy");
            return pref;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(privacyPolicyLink.toString()));

        pref.setOnSecondaryActionClickListener(
                () -> {
                    context.startActivity(intent);
                });
        return pref;
    }
}
