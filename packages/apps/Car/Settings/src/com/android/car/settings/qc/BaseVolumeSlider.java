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

import static android.car.media.CarAudioManager.AUDIO_FEATURE_VOLUME_GROUP_MUTING;

import static com.android.car.qc.QCItem.QC_ACTION_SLIDER_VALUE;
import static com.android.car.settings.qc.QCUtils.getActionDisabledDialogIntent;
import static com.android.car.settings.qc.QCUtils.getAvailabilityStatusForZoneFromXml;

import android.app.PendingIntent;
import android.car.CarNotConnectedException;
import android.car.media.CarAudioManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.UserManager;
import android.util.SparseArray;

import androidx.annotation.VisibleForTesting;
import androidx.annotation.XmlRes;

import com.android.car.qc.QCItem;
import com.android.car.qc.QCList;
import com.android.car.qc.QCRow;
import com.android.car.qc.QCSlider;
import com.android.car.settings.CarSettingsApplication;
import com.android.car.settings.R;
import com.android.car.settings.common.Logger;
import com.android.car.settings.enterprise.EnterpriseUtils;
import com.android.car.settings.sound.VolumeItemParser;

/**
 * Base class for showing a volume slider quick control view.
 * Extended classes should override {@link #getUsages} and specify the array of audio usages that
 * should be shown as part of the quick control.
 */
public abstract class BaseVolumeSlider extends SettingsQCItem {
    static final int QC_VOLUME_SELF_CHANGE = 7918;
    @VisibleForTesting
    static final String EXTRA_GROUP_ID = "QC_VOLUME_EXTRA_GROUP_ID";
    private static final Logger LOG = new Logger(BaseVolumeSlider.class);

    private final Context mContext;
    private final SparseArray<VolumeItemParser.VolumeItem> mVolumeItems;

    public BaseVolumeSlider(Context context) {
        super(context);
        mContext = context;
        setAvailabilityStatusForZone(getAvailabilityStatusForZoneFromXml(context,
                R.xml.sound_settings_fragment, R.string.pk_volume_settings));
        mVolumeItems = VolumeItemParser.loadAudioUsageItems(context, carVolumeItemsXml());
    }

    protected abstract int[] getUsages();

    @Override
    QCItem getQCItem() {
        if (isHiddenForZone()) {
            return null;
        }
        CarAudioManager carAudioManager = getCarAudioManager();
        int zoneId = getMyAudioZoneId();
        if (carAudioManager == null || zoneId == CarAudioManager.INVALID_AUDIO_ZONE) {
            return null;
        }

        String userRestriction = UserManager.DISALLOW_ADJUST_VOLUME;
        boolean hasDpmRestrictions = EnterpriseUtils.hasUserRestrictionByDpm(getContext(),
                userRestriction);
        boolean hasUmRestrictions = EnterpriseUtils.hasUserRestrictionByUm(getContext(),
                userRestriction);


        boolean isReadOnlyForZone = isReadOnlyForZone();
        PendingIntent disabledPendingIntent = isReadOnlyForZone
                ? QCUtils.getDisabledToastBroadcastIntent(getContext())
                : getActionDisabledDialogIntent(getContext(), userRestriction);

        QCList.Builder listBuilder = new QCList.Builder();
        for (int usage : getUsages()) {
            VolumeItemParser.VolumeItem volumeItem = mVolumeItems.get(usage);
            int groupId = carAudioManager.getVolumeGroupIdForUsage(zoneId, usage);
            int min = carAudioManager.getGroupMinVolume(zoneId, groupId);
            int max = carAudioManager.getGroupMaxVolume(zoneId, groupId);
            int value = carAudioManager.getGroupVolume(zoneId, groupId);
            int iconResId = volumeItem.getIcon();
            if (carAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_VOLUME_GROUP_MUTING)
                    && carAudioManager.isVolumeGroupMuted(zoneId, groupId)) {
                iconResId = volumeItem.getMuteIcon();
            }
            listBuilder.addRow(new QCRow.Builder()
                    .setTitle(getContext().getString(volumeItem.getTitle()))
                    .setIcon(showSliderWithIcon()
                            ? Icon.createWithResource(getContext(), iconResId) : null)
                    .addSlider(new QCSlider.Builder()
                            .setMin(min)
                            .setMax(max)
                            .setValue(value)
                            .setInputAction(createSliderAction(groupId))
                            .setEnabled(!hasUmRestrictions && !hasDpmRestrictions
                                    && isWritableForZone())
                            .setClickableWhileDisabled(hasDpmRestrictions || isReadOnlyForZone)
                            .setDisabledClickAction(disabledPendingIntent)
                            .build()
                    )
                    .build()
            );
        }
        return listBuilder.build();
    }

    @Override
    void onNotifyChange(Intent intent) {
        int value = intent.getIntExtra(QC_ACTION_SLIDER_VALUE, Integer.MIN_VALUE);
        int groupId = intent.getIntExtra(EXTRA_GROUP_ID, Integer.MIN_VALUE);
        if (value == Integer.MIN_VALUE || groupId == Integer.MIN_VALUE) {
            return;
        }
        setGroupVolume(groupId, value);
    }

    /**
     * The resource which lists the car volume resources associated with the various usage enums.
     */
    @XmlRes
    protected int carVolumeItemsXml() {
        return R.xml.car_volume_items;
    }

    private PendingIntent createSliderAction(int groupId) {
        Bundle extras = new Bundle();
        extras.putInt(EXTRA_GROUP_ID, groupId);
        return getBroadcastIntent(extras, groupId);
    }

    private void setGroupVolume(int volumeGroupId, int newVolume) {
        CarAudioManager carAudioManager = getCarAudioManager();
        int zoneId = getMyAudioZoneId();
        if (carAudioManager == null || zoneId == CarAudioManager.INVALID_AUDIO_ZONE) {
            return;
        }
        try {
            carAudioManager.setGroupVolume(zoneId, volumeGroupId, newVolume,
                    QC_VOLUME_SELF_CHANGE);
        } catch (CarNotConnectedException e) {
            LOG.w("Ignoring volume change event because the car isn't connected", e);
        }
    }

    protected boolean showSliderWithIcon() {
        return true; // by default
    }

    private int getMyAudioZoneId() {
        return ((CarSettingsApplication) mContext.getApplicationContext())
                .getMyAudioZoneId();
    }

    private CarAudioManager getCarAudioManager() {
        return ((CarSettingsApplication) mContext.getApplicationContext())
                .getCarAudioManager();
    }
}
