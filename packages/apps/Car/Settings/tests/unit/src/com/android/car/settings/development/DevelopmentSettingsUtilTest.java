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

package com.android.car.settings.development;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.UserManager;
import android.provider.Settings;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class DevelopmentSettingsUtilTest {
    private Context mContext = ApplicationProvider.getApplicationContext();
    private Context mSpiedContext = spy(mContext);

    @Mock
    private UserManager mMockUserManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // default to no debugging restrictions
        when(mMockUserManager.hasUserRestriction(
                UserManager.DISALLOW_DEBUGGING_FEATURES)).thenReturn(false);
        when(mSpiedContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);
        when(mSpiedContext.getContentResolver()).thenReturn(mContext.getContentResolver());
    }

    @Test
    public void isEnabled_settingsOff_isAdmin_shouldReturnFalse() {
        when(mMockUserManager.isAdminUser()).thenReturn(true);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0);

        assertThat(DevelopmentSettingsUtil.isDevelopmentSettingsEnabled(mContext, mMockUserManager))
                .isFalse();
    }

    @Test
    public void isEnabled_settingsOn_isAdmin_shouldReturnTrue() {
        when(mMockUserManager.isAdminUser()).thenReturn(true);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);

        assertThat(DevelopmentSettingsUtil.isDevelopmentSettingsEnabled(mContext, mMockUserManager))
                .isTrue();
    }

    @Test
    public void isEnabled_settingsOn_notAdmin_shouldReturnFalse() {
        when(mMockUserManager.isAdminUser()).thenReturn(false);

        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);

        assertThat(DevelopmentSettingsUtil.isDevelopmentSettingsEnabled(mContext, mMockUserManager))
                .isFalse();
    }

    @Test
    public void isEnabled_hasDisallowDebuggingRestriction_shouldReturnFalse() {
        when(mMockUserManager.isAdminUser()).thenReturn(true);

        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);

        when(mMockUserManager.hasUserRestriction(
                UserManager.DISALLOW_DEBUGGING_FEATURES)).thenReturn(true);

        assertThat(DevelopmentSettingsUtil
                .isDevelopmentSettingsEnabled(mSpiedContext, mMockUserManager)).isFalse();
    }

    @Test
    public void isEnabled_doesNotHaveDisallowDebuggingRestriction_shouldReturnTrue() {
        when(mMockUserManager.isAdminUser()).thenReturn(true);

        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);

        assertThat(DevelopmentSettingsUtil.isDevelopmentSettingsEnabled(mContext, mMockUserManager))
                .isTrue();
    }

    @Test
    public void setDevelopmentSettingsEnabled_setTrue() {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0);

        DevelopmentSettingsUtil.setDevelopmentSettingsEnabled(mContext, true);

        assertThat(Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)).isEqualTo(1);
    }

    @Test
    public void setDevelopmentSettingsEnabled_setFalse() {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);

        DevelopmentSettingsUtil.setDevelopmentSettingsEnabled(mContext, false);

        assertThat(Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)).isEqualTo(0);
    }

    @Test
    public void isDeviceProvisioned_true() {
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.DEVICE_PROVISIONED,
                1);
        assertThat(DevelopmentSettingsUtil.isDeviceProvisioned(mContext)).isTrue();
    }

    @Test
    public void isDeviceProvisioned_false() {
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.DEVICE_PROVISIONED,
                0);
        assertThat(DevelopmentSettingsUtil.isDeviceProvisioned(mContext)).isFalse();
    }
}
