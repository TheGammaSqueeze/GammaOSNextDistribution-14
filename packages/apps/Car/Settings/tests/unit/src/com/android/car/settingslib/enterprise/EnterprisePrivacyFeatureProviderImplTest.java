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
package com.android.car.settingslib.enterprise;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.provider.Settings;

import com.android.car.settings.enterprise.BaseEnterpriseTestCase;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Date;

// TODO(b/208511815): move to SettingsLib
public final class EnterprisePrivacyFeatureProviderImplTest extends BaseEnterpriseTestCase {

    private static final String IME_PACKAGE_NAME = "acme.keyboards";
    private static final String IME_PACKAGE_LABEL = "ACME Keyboards";

    private EnterprisePrivacyFeatureProviderImpl mProvider;

    @Mock
    private ApplicationInfo mApplicationInfo;

    @Before
    public void setProvider() {
        mProvider = new EnterprisePrivacyFeatureProviderImpl(mSpiedContext, mDpm, mSpiedPm);
    }

    @Test
    public void testGetLastBugReportRequestTime_none() {
        mockGetLastBugreportTime(-1);

        assertWithMessage("getLastBugReportRequestTime()")
                .that(mProvider.getLastBugReportRequestTime()).isNull();
    }

    @Test
    public void testGetLastBugReportRequestTime_ok() {
        long now = System.currentTimeMillis();
        mockGetLastBugreportTime(now);

        Date last = mProvider.getLastBugReportRequestTime();
        assertWithMessage("getLastBugReportRequestTime()").that(last).isNotNull();
        assertWithMessage("getLastBugReportRequestTime().getTime()").that(last.getTime())
                .isEqualTo(now);
    }

    @Test
    public void testGetLastNetworkLogRetrievalTime_none() {
        mockGetLastNetworkLogRetrievalTime(-1);

        assertWithMessage("getLastNetworkLogRetrievalTime()")
                .that(mProvider.getLastNetworkLogRetrievalTime()).isNull();
    }

    @Test
    public void testGetLastNetworkLogRetrievalTime_ok() {
        long now = System.currentTimeMillis();
        mockGetLastNetworkLogRetrievalTime(now);

        Date last = mProvider.getLastNetworkLogRetrievalTime();
        assertWithMessage("getLastNetworkLogRetrievalTime()").that(last).isNotNull();
        assertWithMessage("getLastNetworkLogRetrievalTime().getTime()").that(last.getTime())
                .isEqualTo(now);
    }

    @Test
    public void testGetLastSecurityLogRetrievalTime_none() {
        mockGetLastSecurityLogRetrievalTime(-1);

        assertWithMessage("getLastSecurityLogRetrievalTime()")
                .that(mProvider.getLastSecurityLogRetrievalTime()).isNull();
    }

    @Test
    public void testGetLastSecurityLogRetrievalTime_ok() {
        long now = System.currentTimeMillis();
        mockGetLastSecurityLogRetrievalTime(now);

        Date last = mProvider.getLastSecurityLogRetrievalTime();
        assertWithMessage("getLastSecurityLogRetrievalTime()").that(last).isNotNull();
        assertWithMessage("getLastSecurityLogRetrievalTime().getTime()").that(last.getTime())
                .isEqualTo(now);
    }

    @Test
    public void testGetImeLabelIfOwnerSet_notSet() {
        mockIsCurrentInputMethodSetByOwner(false);

        assertWithMessage("getImeLabelIfOwnerSet()").that(mProvider.getImeLabelIfOwnerSet())
                .isNull();
    }

    @Test
    public void testGetImeLabelIfOwnerSet_nullString() {
        mockIsCurrentInputMethodSetByOwner(true);
        mockDefaultInputMehtodSettings(null);

        assertWithMessage("getImeLabelIfOwnerSet()").that(mProvider.getImeLabelIfOwnerSet())
                .isNull();
    }

    @Test
    public void testGetImeLabelIfOwnerSet_emptyString() {
        mockIsCurrentInputMethodSetByOwner(true);
        mockDefaultInputMehtodSettings("");

        assertWithMessage("getImeLabelIfOwnerSet()").that(mProvider.getImeLabelIfOwnerSet())
                .isNull();
    }

    @Test
    public void testGetImeLabelIfOwnerSet_nonexistentPackage() throws Exception {
        mockIsCurrentInputMethodSetByOwner(true);
        mockDefaultInputMehtodSettings(IME_PACKAGE_NAME);
        mockGetApplicationInfoNotFound(IME_PACKAGE_NAME);

        assertWithMessage("getImeLabelIfOwnerSet()").that(mProvider.getImeLabelIfOwnerSet())
                .isNull();
    }

    @Test
    public void testGetImeLabelIfOwnerSet_existentPackage() throws Exception {
        mockIsCurrentInputMethodSetByOwner(true);
        mockDefaultInputMehtodSettings(IME_PACKAGE_NAME);
        mockGetApplicationInfoLabel(IME_PACKAGE_NAME, IME_PACKAGE_LABEL);

        assertWithMessage("getImeLabelIfOwnerSet()").that(mProvider.getImeLabelIfOwnerSet())
                .isEqualTo(IME_PACKAGE_LABEL);
    }

    @Test
    public void testGetMaximumFailedPasswordsBeforeWipeInCurrentUser_notOwner() {
        mockNoDeviceOwner();
        mockNoProfileOwnerAsUser();
        mockGetMaximumFailedPasswordsForWipe(MY_USER_ID.getIdentifier(), 42);

        assertWithMessage("getMaximumFailedPasswordsBeforeWipeInCurrentUser()")
                .that(mProvider.getMaximumFailedPasswordsBeforeWipeInCurrentUser())
                .isEqualTo(0);
    }

    @Test
    public void testGetMaximumFailedPasswordsBeforeWipeInCurrentUser_deviceOwner() {
        mockDeviceOwner();
        mockNoProfileOwnerAsUser();
        mockGetMaximumFailedPasswordsForWipe(MY_USER_ID.getIdentifier(), 42);

        assertWithMessage("getMaximumFailedPasswordsBeforeWipeInCurrentUser()")
                .that(mProvider.getMaximumFailedPasswordsBeforeWipeInCurrentUser())
                .isEqualTo(42);
    }

    @Test
    public void testGetMaximumFailedPasswordsBeforeWipeInCurrentUser_profileOwner() {
        mockNoDeviceOwner();
        mockProfileOwnerAsUser();
        mockGetMaximumFailedPasswordsForWipe(MY_USER_ID.getIdentifier(), 42);

        assertWithMessage("getMaximumFailedPasswordsBeforeWipeInCurrentUser()")
                .that(mProvider.getMaximumFailedPasswordsBeforeWipeInCurrentUser())
                .isEqualTo(42);
    }

    private void mockDefaultInputMehtodSettings(String value) {
        Settings.Secure.putString(mRealContext.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD, value);
    }

    private void mockGetApplicationInfoNotFound(String packageName) throws Exception {
        doThrow(new PackageManager.NameNotFoundException("D'OH!")).when(mSpiedPm)
                .getApplicationInfoAsUser(packageName, /* flags= */ 0, MY_USER_ID);
    }

    private void mockGetApplicationInfoLabel(String packageName, CharSequence label)
            throws Exception {
        doReturn(mApplicationInfo).when(mSpiedPm).getApplicationInfoAsUser(packageName,
                /* flags= */ 0, MY_USER_ID);
        doReturn(mApplicationInfo).when(mSpiedPm).getApplicationInfoAsUser(packageName,
                /* flags= */ 0, MY_USER_ID.getIdentifier());
        when(mApplicationInfo.loadLabel(mSpiedPm)).thenReturn(label);
    }
}
