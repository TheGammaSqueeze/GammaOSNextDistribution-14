/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.car.audio;

import android.annotation.UserIdInt;
import android.car.settings.CarSettings;
import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;

import com.android.car.CarServiceUtils;

import java.util.Objects;

/**
 * Use to save/load car volume settings
 */
public class CarAudioSettings {

    // The trailing slash forms a directory-liked hierarchy and
    // allows listening for both GROUP/MEDIA and GROUP/NAVIGATION.
    private static final String VOLUME_SETTINGS_KEY_FOR_GROUP_PREFIX = "android.car.VOLUME_GROUP/";

    // The trailing slash forms a directory-liked hierarchy and
    // allows listening for both GROUP/MEDIA and GROUP/NAVIGATION.
    private static final String VOLUME_SETTINGS_KEY_FOR_GROUP_MUTE_PREFIX =
            "android.car.VOLUME_GROUP_MUTE/";

    // Key to persist master mute state in system settings
    private static final String VOLUME_SETTINGS_KEY_MASTER_MUTE = "android.car.MASTER_MUTE";

    private final Context mContext;

    CarAudioSettings(Context context) {
        mContext = Objects.requireNonNull(context);
    }

    int getStoredVolumeGainIndexForUser(@UserIdInt int userId, int zoneId, int configId,
            int groupId) {
        return getIntForUser(getVolumeSettingsKeyForGroup(zoneId, configId, groupId),
                /* defaultValue= */ -1, userId);
    }

    void storeVolumeGainIndexForUser(@UserIdInt int userId, int zoneId, int configId, int groupId,
            int gainIndex) {
        putIntForUser(getVolumeSettingsKeyForGroup(zoneId, configId, groupId),
                gainIndex,
                userId);
    }

    void storeMasterMute(Boolean masterMuteValue) {
        Settings.Global.putInt(mContext.getContentResolver(),
                VOLUME_SETTINGS_KEY_MASTER_MUTE,
                masterMuteValue ? 1 : 0);
    }

    boolean isMasterMute() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                VOLUME_SETTINGS_KEY_MASTER_MUTE, 0) != 0;
    }

    void storeVolumeGroupMuteForUser(@UserIdInt int userId, int zoneId, int configId, int groupId,
            boolean isMuted) {
        putIntForUser(getMuteSettingsKeyForGroup(zoneId, configId, groupId),
                isMuted ? 1 : 0, userId);
    }

    boolean getVolumeGroupMuteForUser(@UserIdInt int userId, int zoneId, int configId,
            int groupId) {
        return getIntForUser(getMuteSettingsKeyForGroup(zoneId, configId, groupId),
                /* defaultValue= */ 0, userId) != 0;
    }

    boolean isPersistVolumeGroupMuteEnabled(@UserIdInt int userId) {
        return getSecureIntForUser(CarSettings.Secure.KEY_AUDIO_PERSIST_VOLUME_GROUP_MUTE_STATES,
                /* defaultValue= */ 0, userId) == 1;
    }

    /**
     * Determines if for a given userId the reject navigation on call setting is enabled
     */
    public boolean isRejectNavigationOnCallEnabledInSettings(@UserIdInt int userId) {
        return getSecureIntForUser(
                CarSettings.Secure.KEY_AUDIO_FOCUS_NAVIGATION_REJECTED_DURING_CALL,
                /* defaultValue= */  0, userId) == 1;
    }

    private int getIntForUser(String name, int defaultValue, @UserIdInt int userId) {
        return Settings.System.getInt(getContentResolverForUser(userId), name, defaultValue);
    }

    private void putIntForUser(String name, int value, @UserIdInt int userId) {
        Settings.System.putInt(getContentResolverForUser(userId), name, value);
    }

    private int getSecureIntForUser(String name, int defaultValue, @UserIdInt int userId) {
        return Settings.Secure.getInt(getContentResolverForUser(userId), name, defaultValue);
    }

    private static String getVolumeSettingsKeyForGroup(int zoneId, int configId, int groupId) {
        return getFormattedZoneIdAndGroupIdKey(VOLUME_SETTINGS_KEY_FOR_GROUP_PREFIX, zoneId,
                configId, groupId);
    }

    private static String getMuteSettingsKeyForGroup(int zoneId, int configId, int groupId) {
        return getFormattedZoneIdAndGroupIdKey(VOLUME_SETTINGS_KEY_FOR_GROUP_MUTE_PREFIX, zoneId,
                configId, groupId);
    }

    private static String getFormattedZoneIdAndGroupIdKey(String prefix, int zoneId, int configId,
            int groupId) {
        return new StringBuilder().append(prefix).append(zoneId).append('/').append(configId)
                .append('/').append(groupId).toString();
    }

    ContentResolver getContentResolverForUser(@UserIdInt int userId) {
        return CarServiceUtils.getContentResolverForUser(mContext, userId);
    }
}
