/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.settings.qc;

import static com.android.car.settings.common.PreferenceXmlParser.METADATA_KEY;
import static com.android.car.settings.common.PreferenceXmlParser.METADATA_OCCUPANT_ZONE;
import static com.android.car.settings.common.PreferenceXmlParser.PREF_AVAILABILITY_STATUS_WRITE;
import static com.android.car.settings.common.PreferenceXmlParser.SUPPORTED_AVAILABILITY_STATUS;

import android.annotation.StringRes;
import android.annotation.XmlRes;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.car.CarOccupantZoneManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import com.android.car.settings.CarSettingsApplication;
import com.android.car.settings.R;
import com.android.car.settings.common.PreferenceXmlParser;
import com.android.car.settings.enterprise.ActionDisabledByAdminActivity;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.List;
/**
 * General helper methods for quick controls.
 */
public class QCUtils {
    private QCUtils() {
    }

    /**
     * See {@link #getActionDisabledDialogIntent(Context, String, int)}.
     */
    public static PendingIntent getActionDisabledDialogIntent(Context context, String restriction) {
        return getActionDisabledDialogIntent(context, restriction, /* requestCode= */ 0);
    }

    /**
     * Returns a {@link PendingIntent} for launching a {@link ActionDisabledByAdminActivity} with
     * the specified restriction and request code.
     */
    public static PendingIntent getActionDisabledDialogIntent(Context context, String restriction,
            int requestCode) {
        Intent intent = new Intent();
        intent.setClass(context, ActionDisabledByAdminActivity.class);
        intent.putExtra(DevicePolicyManager.EXTRA_RESTRICTION, restriction);
        return PendingIntent.getActivity(context, requestCode, intent,
                PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Creates a list of {@link PreferenceController}.
     *
     * @param context the {@link Context} used to instantiate the controllers.
     * @param xmlResId the XML resource containing the metadata of the controllers to
     *         create.
     * @param prefKeyResId the {@link StringRes} used to find the key for the preference
     *         managed by this Quick Controls.
     */
    static String getAvailabilityStatusForZoneFromXml(Context context, @XmlRes int xmlResId,
            @StringRes int prefKeyResId) {
        List<Bundle> preferenceMetadata;
        try {
            int zoneType = ((CarSettingsApplication) context.getApplicationContext())
                    .getMyOccupantZoneType();
            preferenceMetadata = PreferenceXmlParser.extractMetadata(context, xmlResId,
                    PreferenceXmlParser.MetadataFlag.FLAG_NEED_KEY
                            | PreferenceXmlParser.getMetadataFlagForOccupantZoneType(zoneType)
            );
        } catch (IOException | XmlPullParserException e) {
            throw new IllegalArgumentException(
                    "Failed to parse preference XML for getting controllers", e);
        }
        String targetPrefKey = context.getString(prefKeyResId);
        for (Bundle metadata : preferenceMetadata) {
            String key = metadata.getString(METADATA_KEY);
            if (TextUtils.isEmpty(key) || !key.equals(targetPrefKey)) {
                continue;
            }
            String availabilityStatusForZone = metadata.getString(METADATA_OCCUPANT_ZONE);
            if (!TextUtils.isEmpty(availabilityStatusForZone)
                    && SUPPORTED_AVAILABILITY_STATUS.contains(availabilityStatusForZone)) {
                return availabilityStatusForZone;
            } else {
                break;
            }
        }
        return PREF_AVAILABILITY_STATUS_WRITE;
    }

    /** Standardized intent for onclick when qc is disabled for zone */
    public static PendingIntent getDisabledToastBroadcastIntent(Context context) {
        String message = isPassengerUser(context)
                ? context.getString(R.string.restricted_for_passenger)
                : context.getString(R.string.restricted_for_driver);

        Intent intent = new Intent()
                .setClass(context, DisabledQCToastBroadcastReceiver.class)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        Bundle bundle = new Bundle();
        bundle.putString(DisabledQCToastBroadcastReceiver.DISABLED_QC_TOAST_KEY, message);
        intent.putExtras(bundle);

        return PendingIntent.getBroadcast(context, /* requestCode= */ 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
    }

    /** Returns whether the current user is a passenger or not */
    private static boolean isPassengerUser(Context context) {
        int zoneType = ((CarSettingsApplication) context.getApplicationContext())
                .getMyOccupantZoneType();
        return zoneType != CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER;
    }
}
