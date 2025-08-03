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

import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING_FOR_ZONE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_ZONE;
import static com.android.car.settings.common.PreferenceController.HIDDEN_FOR_ZONE;
import static com.android.car.settings.common.PreferenceXmlParser.PREF_AVAILABILITY_STATUS_HIDDEN;
import static com.android.car.settings.common.PreferenceXmlParser.PREF_AVAILABILITY_STATUS_READ;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.android.car.qc.QCItem;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Base class for QCItems provided by CarSettings.
 */
public abstract class SettingsQCItem {

    private final Context mContext;
    private int mAvailabilityStatusForZone;

    public SettingsQCItem(Context context) {
        mContext = context;
    }

    protected void setAvailabilityStatusForZone(String availabilityStatusForZone) {
        if (PREF_AVAILABILITY_STATUS_READ.equals(availabilityStatusForZone)) {
            mAvailabilityStatusForZone = AVAILABLE_FOR_VIEWING_FOR_ZONE;
        } else if (PREF_AVAILABILITY_STATUS_HIDDEN.equals(availabilityStatusForZone)) {
            mAvailabilityStatusForZone = HIDDEN_FOR_ZONE;
        } else {
            mAvailabilityStatusForZone = AVAILABLE_FOR_ZONE;
        }
    }

    /**
     * @return an complete instance of the {@link QCItem}.
     */
    abstract QCItem getQCItem();

    /**
     * @return a {@link android.content.ContentResolver#SCHEME_CONTENT content} {@link Uri} which
     * backs the {@link QCItem} returned by {@link #getQCItem()}.
     */
    abstract Uri getUri();

    /**
     * @return the context for the {@link SettingsQCItem}.
     */
    protected Context getContext() {
        return mContext;
    }

    /**
     * @return a boolean that indicates whether the QCItem is hidden for the current zone.
     */
    protected boolean isHiddenForZone() {
        return mAvailabilityStatusForZone == HIDDEN_FOR_ZONE;
    }

    /**
     * @return a boolean that indicates whether the QCItem is only readable for the current zone.
     */
    protected boolean isReadOnlyForZone() {
        return mAvailabilityStatusForZone == AVAILABLE_FOR_VIEWING_FOR_ZONE;
    }

    /**
     * @return a boolean that indicates whether the QCItem is writable for the current zone.
     */
    protected boolean isWritableForZone() {
        return mAvailabilityStatusForZone == AVAILABLE_FOR_ZONE;
    }

    /**
     * Handles the actions sent by the {@link Intent intents} bound to the {@link QCItem} returned
     * by {@link #getQCItem()}.
     *
     * @param intent which has the action taken on a {@link QCItem}.
     */
    void onNotifyChange(Intent intent) {}

    /**
     * Standardize the primary intent for the QCItem.
     */
    PendingIntent getActivityIntent(Intent intent) {
        return PendingIntent.getActivity(getContext(),
                0 /* requestCode */, intent,
                PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * See {@link #getBroadcastIntent(Bundle, int)}
     */
    PendingIntent getBroadcastIntent() {
        return getBroadcastIntent(/* extras= */ null);
    }

    /**
     * See {@link #getBroadcastIntent(Bundle, int)}
     */
    PendingIntent getBroadcastIntent(Bundle extras) {
        return getBroadcastIntent(extras, /* requestCode= */ 0);
    }

    /**
     * Standardize the intents returned to indicate actions by the QCItem.
     * <p>
     *     The {@link PendingIntent} is linked to {@link SettingsQCBroadcastReceiver} where the
     *     Intent Action is found by {@code getUri().toString()}.
     *
     * @return a {@link PendingIntent} linked to {@link SettingsQCBroadcastReceiver}.
     */
    PendingIntent getBroadcastIntent(Bundle extras, int requestCode) {
        Intent intent = new Intent(getUri().toString())
                .setData(getUri())
                .setClass(getContext(), SettingsQCBroadcastReceiver.class)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        if (extras != null) {
            intent.putExtras(extras);
        }
        return PendingIntent.getBroadcast(getContext(), requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
    }

    /**
     * Build an instance of a {@link SettingsQCItem} which has a {@link Context}-only constructor.
     */
    static SettingsQCItem createInstance(Context context,
            Class<? extends SettingsQCItem> qcItem) {
        try {
            Constructor<? extends SettingsQCItem> constructor =
                    qcItem.getConstructor(Context.class);
            Object[] params = new Object[]{context.getApplicationContext()};
            return constructor.newInstance(params);
        } catch (NoSuchMethodException | InstantiationException | IllegalArgumentException
                | InvocationTargetException | IllegalAccessException e) {
            throw new IllegalStateException(
                    "Invalid SettingsQCItem class: " + qcItem, e);
        }
    }

    /**
     * Settings QCItems which require background work, such as updating lists should implement a
     * {@link SettingsQCBackgroundWorker} and return it here. An example of background work is
     * updating a list of Wifi networks available in the area.
     *
     * @return a {@link Class<? extends SettingsQCBackgroundWorker>} to perform background work for
     * the QCItem.
     */
    Class<? extends SettingsQCBackgroundWorker> getBackgroundWorkerClass() {
        return null;
    }
}
