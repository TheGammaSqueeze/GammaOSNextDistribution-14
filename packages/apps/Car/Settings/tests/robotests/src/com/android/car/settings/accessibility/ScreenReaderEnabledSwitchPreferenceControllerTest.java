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

package com.android.car.settings.accessibility;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.content.Context;
import android.os.UserHandle;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ApplicationProvider;

import com.android.car.settings.R;
import com.android.car.settings.common.ColoredSwitchPreference;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.internal.accessibility.util.AccessibilityUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ScreenReaderEnabledSwitchPreferenceControllerTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private ColoredSwitchPreference mColoredSwitchPreference;
    private PreferenceControllerTestHelper<ScreenReaderEnabledSwitchPreferenceController>
            mPreferenceControllerHelper;
    private ComponentName mScreenReaderComponent;

    @Before
    public void setUp() {
        mColoredSwitchPreference = new ColoredSwitchPreference(mContext);
        mPreferenceControllerHelper =
                new PreferenceControllerTestHelper<>(mContext,
                        ScreenReaderEnabledSwitchPreferenceController.class,
                        mColoredSwitchPreference);
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        mScreenReaderComponent = ComponentName.unflattenFromString(
                mContext.getString(R.string.config_default_screen_reader));
    }

    @Test
    public void testRefreshUi_screenReaderEnabled_switchSetToOn() {
        setScreenReaderEnabled(true);

        mPreferenceControllerHelper.getController().refreshUi();

        assertThat(mColoredSwitchPreference.isChecked()).isTrue();
    }

    @Test
    public void testRefreshUi_screenReaderDisabled_switchSetToOff() {
        setScreenReaderEnabled(false);

        mPreferenceControllerHelper.getController().refreshUi();

        assertThat(mColoredSwitchPreference.isChecked()).isFalse();
    }

    @Test
    public void testSwitchedSetOn_setsScreenReaderEnabled() {
        setScreenReaderEnabled(false);

        mColoredSwitchPreference.getOnPreferenceChangeListener().onPreferenceChange(
                mColoredSwitchPreference, true);

        assertThat(AccessibilityUtils.getEnabledServicesFromSettings(mContext,
                UserHandle.myUserId())).contains(mScreenReaderComponent);
    }

    @Test
    public void testSwitchedSetOff_setsScreenReaderDisabled() {
        setScreenReaderEnabled(true);

        mColoredSwitchPreference.getOnPreferenceChangeListener().onPreferenceChange(
                mColoredSwitchPreference, false);

        assertThat(AccessibilityUtils.getEnabledServicesFromSettings(mContext,
                UserHandle.myUserId())).doesNotContain(mScreenReaderComponent);
    }

    private void setScreenReaderEnabled(boolean enabled) {
        AccessibilityUtils.setAccessibilityServiceState(mContext,
                mScreenReaderComponent, enabled,
                UserHandle.myUserId());
    }
}
