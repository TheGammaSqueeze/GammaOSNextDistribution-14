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

import static com.android.car.qc.QCItem.QC_ACTION_TOGGLE_STATE;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.Intent;
import android.os.UserManager;
import android.provider.Settings;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.qc.QCActionItem;
import com.android.car.qc.QCItem;
import com.android.car.qc.QCList;
import com.android.car.qc.QCRow;
import com.android.car.settings.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class AdaptiveBrightnessSwitchTest extends BaseSettingsQCItemTestCase {
    private AdaptiveBrightnessSwitch mSwitch;
    private boolean mIsAdaptiveBrightnessSupported;
    private boolean mInitialAdaptiveBrightnessState;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mSwitch = new TestAdaptiveBrightnessSwitch(mContext);
        mIsAdaptiveBrightnessSupported = true;
        mInitialAdaptiveBrightnessState = isAdaptiveBrightnessEnabled();
    }

    @After
    public void tearDown() {
        setAdaptiveBrightnessEnabled(mInitialAdaptiveBrightnessState);
    }

    @Test
    public void getQCItem_unsupported_returnsNull() {
        mIsAdaptiveBrightnessSupported = false;
        QCItem item = mSwitch.getQCItem();
        assertThat(item).isNull();
    }

    @Test
    public void getQCItem_supported_returnsRow() {
        QCItem item = mSwitch.getQCItem();
        assertThat(item instanceof QCList).isTrue();
        QCList list = (QCList) item;
        assertThat(list.getRows().size()).isEqualTo(1);
    }

    @Test
    public void getQCItem_createsRow_zoneWrite() {
        mSwitch.setAvailabilityStatusForZone("write");
        QCRow row = getQCRow();
        QCActionItem actionItem = row.getEndItems().get(0);
        assertThat(actionItem.isEnabled()).isTrue();
    }

    @Test
    public void getQCItem_createsRow_zoneRead() {
        mSwitch.setAvailabilityStatusForZone("read");
        QCRow row = getQCRow();
        QCActionItem actionItem = row.getEndItems().get(0);
        assertThat(actionItem.isEnabled()).isFalse();
        assertThat(actionItem.isClickableWhileDisabled()).isTrue();
    }

    @Test
    public void getQCItem_createsRow_zoneHidden() {
        mSwitch.setAvailabilityStatusForZone("hidden");
        QCItem item = mSwitch.getQCItem();
        assertThat(item).isNull();
    }

    @Test
    public void getQCItem_titleSet() {
        QCRow row = getQCRow();
        assertThat(row.getTitle()).isEqualTo(mContext.getString(R.string.auto_brightness_title));
    }

    @Test
    public void getQCItem_hasActionSwitch() {
        QCRow row = getQCRow();
        assertThat(row.getEndItems().size()).isEqualTo(1);
        QCActionItem actionItem = row.getEndItems().get(0);
        assertThat(actionItem.getType()).isEqualTo(QCItem.QC_TYPE_ACTION_SWITCH);
    }

    @Test
    public void getQCItem_adaptiveBrightnessOn_switchChecked() {
        setAdaptiveBrightnessEnabled(true);
        QCRow row = getQCRow();
        QCActionItem actionItem = row.getEndItems().get(0);
        assertThat(actionItem.isChecked()).isTrue();
    }

    @Test
    public void getQCItem_adaptiveBrightnessOff_switchUnchecked() {
        setAdaptiveBrightnessEnabled(false);
        QCRow row = getQCRow();
        QCActionItem actionItem = row.getEndItems().get(0);
        assertThat(actionItem.isChecked()).isFalse();
    }

    @Test
    public void getQCItem_hasBaseUmRestriction_switchDisabled() {
        setAdaptiveBrightnessEnabled(false);
        setBaseUserRestriction(UserManager.DISALLOW_CONFIG_BRIGHTNESS, /* restricted= */ true);
        QCRow row = getQCRow();
        QCActionItem actionItem = row.getEndItems().get(0);
        assertThat(actionItem.isEnabled()).isFalse();
        assertThat(actionItem.isClickableWhileDisabled()).isFalse();
    }

    @Test
    public void getQCItem_hasUmRestriction_switchClickableWhileDisabled() {
        setAdaptiveBrightnessEnabled(false);
        setUserRestriction(UserManager.DISALLOW_CONFIG_BRIGHTNESS, /* restricted= */ true);
        QCRow row = getQCRow();
        QCActionItem actionItem = row.getEndItems().get(0);
        assertThat(actionItem.isEnabled()).isFalse();
        assertThat(actionItem.isClickableWhileDisabled()).isTrue();
    }

    @Test
    public void onNotifyChange_enable_enablesAdaptiveBrightness() {
        setAdaptiveBrightnessEnabled(false);
        Intent intent = new Intent();
        intent.putExtra(QC_ACTION_TOGGLE_STATE, true);
        mSwitch.onNotifyChange(intent);
        assertThat(isAdaptiveBrightnessEnabled()).isTrue();
    }

    @Test
    public void onNotifyChange_disable_disablesAdaptiveBrightness() {
        setAdaptiveBrightnessEnabled(true);
        Intent intent = new Intent();
        intent.putExtra(QC_ACTION_TOGGLE_STATE, false);
        mSwitch.onNotifyChange(intent);
        assertThat(isAdaptiveBrightnessEnabled()).isFalse();
    }

    private QCRow getQCRow() {
        QCItem item = mSwitch.getQCItem();
        assertThat(item instanceof QCList).isTrue();
        QCList list = (QCList) item;
        assertThat(list.getRows().size()).isEqualTo(1);
        return list.getRows().get(0);
    }

    private boolean isAdaptiveBrightnessEnabled() {
        int brightnessMode = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        return brightnessMode != Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;
    }

    private void setAdaptiveBrightnessEnabled(boolean enabled) {
        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                enabled ? Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                        : Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
    }

    private class TestAdaptiveBrightnessSwitch extends AdaptiveBrightnessSwitch {

        TestAdaptiveBrightnessSwitch(Context context) {
            super(context);
        }

        @Override
        boolean supportsAdaptiveBrightness() {
            return mIsAdaptiveBrightnessSupported;
        }
    }
}
