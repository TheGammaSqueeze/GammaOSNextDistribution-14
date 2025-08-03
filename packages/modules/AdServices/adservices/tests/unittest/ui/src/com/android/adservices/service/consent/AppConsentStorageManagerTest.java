/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.consent;

import static com.android.adservices.service.consent.ConsentManagerV2Test.ENROLLMENT_CHANNEL_COUNT;
import static com.android.adservices.service.consent.ConsentManagerV2Test.UX_TYPE_COUNT;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.test.core.content.pm.ApplicationInfoBuilder;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.data.common.BooleanFileDatastore;
import com.android.adservices.data.consent.AppConsentDao;
import com.android.adservices.data.consent.AppConsentDaoFixture;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.adservices.service.common.feature.PrivacySandboxFeatureType;
import com.android.adservices.service.ui.data.UxStatesDao;
import com.android.adservices.service.ui.enrollment.collection.PrivacySandboxEnrollmentChannelCollection;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;
import com.android.modules.utils.build.SdkLevel;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@SpyStatic(PackageManagerCompatUtils.class)
@SpyStatic(SdkLevel.class)
@SpyStatic(FlagsFactory.class)
public final class AppConsentStorageManagerTest extends AdServicesExtendedMockitoTestCase {

    private Context mContextSpy;
    private BooleanFileDatastore mAppDaoDatastore;
    private BooleanFileDatastore mConsentDatastore;
    private AppConsentDao mAppConsentDaoSpy;
    private AppConsentStorageManager mAppConsentStorageManager;
    @Mock private UxStatesDao mUxStatesDaoMock;

    @Before
    public void setup() {
        mContextSpy = Mockito.spy(appContext.get());
        mConsentDatastore =
                new BooleanFileDatastore(
                        mContextSpy,
                        ConsentConstants.STORAGE_XML_IDENTIFIER,
                        ConsentConstants.STORAGE_VERSION);

        mAppDaoDatastore =
                new BooleanFileDatastore(
                        mContextSpy, AppConsentDao.DATASTORE_NAME, AppConsentDao.DATASTORE_VERSION);

        mAppConsentDaoSpy =
                spy(new AppConsentDao(mAppDaoDatastore, mContextSpy.getPackageManager()));
        mAppConsentStorageManager =
                spy(
                        new AppConsentStorageManager(
                                mConsentDatastore, mAppConsentDaoSpy, mUxStatesDaoMock));
    }

    @After
    public void teardown() throws IOException {
        mAppDaoDatastore.clear();
        mConsentDatastore.clear();
    }

    @Test
    public void testAppNotFoundException() {
        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAppConsentStorageManager.isConsentRevokedForApp(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testClearAllAppConsentData() throws IOException {
        setConsentForOneApp();

        mAppConsentStorageManager.clearAllAppConsentData();
        expect.that(
                        mAppConsentStorageManager
                                .getKnownAppsWithConsent()
                                .contains(AppConsentDaoFixture.APP10_PACKAGE_NAME))
                .isFalse();
    }

    @Test
    public void testClearConsentForUninstalledApp() throws IOException {
        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);

        mAppConsentStorageManager.setConsentForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME, false);
        expect.that(mAppDaoDatastore.get(AppConsentDaoFixture.APP10_DATASTORE_KEY)).isFalse();
        mAppConsentStorageManager.clearConsentForUninstalledApp(
                AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        expect.that(mAppDaoDatastore.get(AppConsentDaoFixture.APP10_DATASTORE_KEY)).isNull();
    }

    @Test
    public void testClearConsentForUninstalledAppWithoutUid() throws IOException {
        mAppDaoDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);
        mAppDaoDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mAppDaoDatastore.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);

        mAppConsentStorageManager.clearConsentForUninstalledApp(
                AppConsentDaoFixture.APP20_PACKAGE_NAME);

        expect.that(mAppDaoDatastore.get(AppConsentDaoFixture.APP10_DATASTORE_KEY)).isTrue();
        expect.that(mAppDaoDatastore.get(AppConsentDaoFixture.APP20_DATASTORE_KEY)).isNull();
        expect.that(mAppDaoDatastore.get(AppConsentDaoFixture.APP30_DATASTORE_KEY)).isFalse();

        verify(mAppConsentDaoSpy).clearConsentForUninstalledApp(anyString());
    }

    @Test
    public void testClearConsentForUninstalledAppWithoutUid_validatesInput() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    mAppConsentStorageManager.clearConsentForUninstalledApp(null);
                });
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mAppConsentStorageManager.clearConsentForUninstalledApp("");
                });
    }

    @Test
    public void testDatastoreBasicApis() throws IOException {
        PrivacySandboxFeatureType privacySandboxFeatureType =
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT;
        mAppConsentStorageManager.setCurrentPrivacySandboxFeature(privacySandboxFeatureType);
        expect.that(mAppConsentStorageManager.getCurrentPrivacySandboxFeature())
                .isEqualTo(privacySandboxFeatureType);


        mAppConsentStorageManager.setU18Account(true);
        expect.that(mAppConsentStorageManager.isU18Account()).isTrue();

        for (AdServicesApiType apiType : AdServicesApiType.values()) {
            if (apiType == AdServicesApiType.UNKNOWN) {
                // Skip UNKNOWN, recordDefaultConsent will throw exception for unknown
                continue;
            }
            mAppConsentStorageManager.setConsent(apiType, true);
            expect.that(mAppConsentStorageManager.getConsent(apiType).isGiven()).isTrue();
        }
        mAppConsentStorageManager.recordGaUxNotificationDisplayed(true);
        expect.that(mAppConsentStorageManager.wasGaUxNotificationDisplayed()).isTrue();

        mAppConsentStorageManager.recordNotificationDisplayed(true);
        expect.that(mAppConsentStorageManager.wasNotificationDisplayed()).isTrue();

        mAppConsentStorageManager.setU18NotificationDisplayed(true);
        expect.that(mAppConsentStorageManager.wasU18NotificationDisplayed()).isTrue();
    }

    @Test
    public void testGetEnrollmentChannel() {
        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            for (PrivacySandboxEnrollmentChannelCollection channel :
                    ux.getEnrollmentChannelCollection()) {
                doReturn(channel).when(mUxStatesDaoMock).getEnrollmentChannel(ux);
                expect.that(mAppConsentStorageManager.getEnrollmentChannel(ux)).isEqualTo(channel);
                mAppConsentStorageManager.setEnrollmentChannel(ux, channel);
            }
        }

        verify(mUxStatesDaoMock, times(ENROLLMENT_CHANNEL_COUNT)).getEnrollmentChannel(any());
        verify(mUxStatesDaoMock, times(ENROLLMENT_CHANNEL_COUNT))
                .setEnrollmentChannel(any(), any());
    }

    @Test
    public void testGetKnownAppsWithConsentAfterConsentForOneOfThemWasRevoked() throws IOException {
        setMockfor3Apps(false);

        App app = App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME);

        // revoke consent for first app
        mAppConsentStorageManager.setConsentForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME, true);
        ImmutableList<String> knownAppsWithConsent =
                mAppConsentStorageManager.getKnownAppsWithConsent();
        ImmutableList<String> appsWithRevokedConsent =
                mAppConsentStorageManager.getAppsWithRevokedConsent();

        // all apps have received a consent
        expect.that(knownAppsWithConsent).hasSize(2);
        expect.that(appsWithRevokedConsent).hasSize(1);
        expect.that(appsWithRevokedConsent.get(0)).isEqualTo(app.getPackageName());
    }

    @Test
    public void testGetKnownAppsWithConsentAfterConsentForOneOfThemWasRevokedAndRestored()
            throws IOException {
        setMockfor3Apps(false);
        // revoke consent for first app
        mAppConsentStorageManager.setConsentForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME, true);
        ImmutableList<String> knownAppsWithConsent =
                mAppConsentStorageManager.getKnownAppsWithConsent();
        ImmutableList<String> appsWithRevokedConsent =
                mAppConsentStorageManager.getAppsWithRevokedConsent();

        // all apps have received a consent
        expect.that(knownAppsWithConsent).hasSize(2);
        expect.that(appsWithRevokedConsent).hasSize(1);
        expect.that(appsWithRevokedConsent.get(0))
                .isEqualTo(AppConsentDaoFixture.APP10_PACKAGE_NAME);

        // restore consent for first app
        mAppConsentStorageManager.setConsentForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME, false);
        knownAppsWithConsent = mAppConsentStorageManager.getKnownAppsWithConsent();
        appsWithRevokedConsent = mAppConsentStorageManager.getAppsWithRevokedConsent();

        // all apps have received a consent
        expect.that(knownAppsWithConsent).hasSize(3);
        expect.that(appsWithRevokedConsent).isEmpty();
    }

    @Test
    public void testGetKnownAppsWithConsent_ppApiOnly() throws IOException {
        setMockfor3Apps(false);

        ImmutableList<String> knownAppsWithConsent =
                mAppConsentStorageManager.getKnownAppsWithConsent();
        ImmutableList<String> appsWithRevokedConsent =
                mAppConsentStorageManager.getAppsWithRevokedConsent();

        // all apps have received a consent
        expect.that(knownAppsWithConsent).hasSize(3);
        expect.that(appsWithRevokedConsent).isEmpty();
    }

    @Test
    public void testGetSetUx() {
        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            doReturn(ux).when(mUxStatesDaoMock).getUx();
            expect.that(mAppConsentStorageManager.getUx()).isEqualTo(ux);

            mAppConsentStorageManager.setUx(ux);
        }

        verify(mUxStatesDaoMock, times(UX_TYPE_COUNT)).getUx();
        verify(mUxStatesDaoMock, times(UX_TYPE_COUNT)).setUx(any());
    }

    @Test
    public void testIsConsentRevokedForApp() throws IOException {
        // Moved from testIsFledgeConsentRevokedForAppWithFullApiConsentGaUxDisabled_ppApiOnly
        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mAppDaoDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mAppDaoDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);

        expect.that(
                        mAppConsentStorageManager.isConsentRevokedForApp(
                                AppConsentDaoFixture.APP10_PACKAGE_NAME))
                .isFalse();
        expect.that(
                        mAppConsentStorageManager.isConsentRevokedForApp(
                                AppConsentDaoFixture.APP20_PACKAGE_NAME))
                .isTrue();
        expect.that(
                        mAppConsentStorageManager.isConsentRevokedForApp(
                                AppConsentDaoFixture.APP30_PACKAGE_NAME))
                .isFalse();
    }

    @Test
    public void testRecordUserManualInteractionWithConsent() throws IOException {
        for (int i = 0; i < 3; i++) {
            int interaction = i - 1;
            mAppConsentStorageManager.recordUserManualInteractionWithConsent(interaction);
            expect.that(mAppConsentStorageManager.getUserManualInteractionWithConsent())
                    .isEqualTo(interaction);
        }
    }

    @Test
    public void testResetAllAppConsentAndAppData()
            throws IOException, PackageManager.NameNotFoundException {

        setMockfor3Apps(true);

        // Verify population was successful
        ImmutableList<String> knownAppsWithConsent =
                mAppConsentStorageManager.getKnownAppsWithConsent();
        ImmutableList<String> appsWithRevokedConsent =
                mAppConsentStorageManager.getAppsWithRevokedConsent();
        expect.that(knownAppsWithConsent).hasSize(2);
        expect.that(appsWithRevokedConsent).hasSize(1);

        mAppConsentStorageManager.clearAllAppConsentData();

        // All app consent data was deleted
        knownAppsWithConsent = mAppConsentStorageManager.getKnownAppsWithConsent();
        appsWithRevokedConsent = mAppConsentStorageManager.getAppsWithRevokedConsent();
        expect.that(knownAppsWithConsent).isEmpty();
        expect.that(appsWithRevokedConsent).isEmpty();
    }

    @Test
    public void testResetAllowedAppConsentAndAppData() throws IOException {

        setMockfor3Apps(true);

        // Verify population was successful
        ImmutableList<String> knownAppsWithConsentBeforeReset =
                mAppConsentStorageManager.getKnownAppsWithConsent();
        ImmutableList<String> appsWithRevokedConsentBeforeReset =
                mAppConsentStorageManager.getAppsWithRevokedConsent();
        expect.that(knownAppsWithConsentBeforeReset).hasSize(2);
        expect.that(appsWithRevokedConsentBeforeReset).hasSize(1);
        mAppConsentStorageManager.clearKnownAppsWithConsent();

        // Known apps with consent were cleared; revoked apps were not deleted
        ImmutableList<String> knownAppsWithConsentAfterReset =
                mAppConsentStorageManager.getKnownAppsWithConsent();
        ImmutableList<String> appsWithRevokedConsentAfterReset =
                mAppConsentStorageManager.getAppsWithRevokedConsent();
        expect.that(knownAppsWithConsentAfterReset).isEmpty();
        expect.that(appsWithRevokedConsentAfterReset).hasSize(1);

        expect.that(appsWithRevokedConsentAfterReset)
                .containsExactlyElementsIn(appsWithRevokedConsentBeforeReset);
    }

    @Test
    public void testSetConsentForApp() throws Exception {

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);

        mAppConsentStorageManager.setConsentForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME, true);
        expect.that(
                        mAppConsentStorageManager.isConsentRevokedForApp(
                                AppConsentDaoFixture.APP10_PACKAGE_NAME))
                .isTrue();

        mAppConsentStorageManager.setConsentForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME, false);
        expect.that(
                        mAppConsentStorageManager.isConsentRevokedForApp(
                                AppConsentDaoFixture.APP10_PACKAGE_NAME))
                .isFalse();
    }

    private List<ApplicationInfo> createApplicationInfos(String... packageNames) {
        return Arrays.stream(packageNames)
                .map(s -> ApplicationInfoBuilder.newBuilder().setPackageName(s).build())
                .collect(Collectors.toList());
    }

    private void mock3AppsInstalled() {
        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);
        List<ApplicationInfo> applicationsInstalled =
                createApplicationInfos(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP20_PACKAGE_NAME,
                        AppConsentDaoFixture.APP30_PACKAGE_NAME);
        mockInstalledApplications(applicationsInstalled);
    }

    private void mockGetPackageUid(@NonNull String packageName, int uid) {
        doReturn(uid)
                .when(
                        () ->
                                PackageManagerCompatUtils.getPackageUid(
                                        any(), eq(packageName), anyInt()));
    }

    private void mockInstalledApplications(List<ApplicationInfo> applicationsInstalled) {
        doReturn(applicationsInstalled)
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));
    }

    private void mockThrowExceptionOnGetPackageUid(@NonNull String packageName) {
        doThrow(PackageManager.NameNotFoundException.class)
                .when(
                        () ->
                                PackageManagerCompatUtils.getPackageUid(
                                        any(), eq(packageName), anyInt()));
    }

    private void setConsentForOneApp() throws IOException {
        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mAppConsentStorageManager.setConsentForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME, false);

        List<ApplicationInfo> applicationsInstalled =
                createApplicationInfos(AppConsentDaoFixture.APP10_PACKAGE_NAME);
        mockInstalledApplications(applicationsInstalled);
        expect.that(
                        mAppConsentStorageManager
                                .getKnownAppsWithConsent()
                                .contains(AppConsentDaoFixture.APP10_PACKAGE_NAME))
                .isTrue();
    }

    private void setMockfor3Apps(boolean value) throws IOException {
        mock3AppsInstalled();
        mAppDaoDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mAppDaoDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, value);
        mAppDaoDatastore.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
    }
}
