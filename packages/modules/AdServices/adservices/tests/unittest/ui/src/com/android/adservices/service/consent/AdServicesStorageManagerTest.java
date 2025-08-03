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

import static com.android.adservices.service.consent.ConsentManager.MANUAL_INTERACTIONS_RECORDED;
import static com.android.adservices.service.consent.ConsentManager.UNKNOWN;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.adservices.AdServicesManager;
import android.app.adservices.IAdServicesManager;
import android.app.adservices.consent.ConsentParcel;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.RemoteException;

import androidx.test.core.content.pm.ApplicationInfoBuilder;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.data.consent.AppConsentDaoFixture;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class AdServicesStorageManagerTest extends AdServicesMockitoTestCase {
    @Mock private IAdServicesManager mMockIAdServicesManager;

    private AdServicesStorageManager mAdServicesStorageManager;

    private AdServicesManager mAdServicesManager;

    @Mock private PackageManager mPackageManager;

    private static class ListMatcherIgnoreOrder implements ArgumentMatcher<List<String>> {
        @NonNull private final List<String> mStrings;

        private ListMatcherIgnoreOrder(@NonNull List<String> strings) {
            Objects.requireNonNull(strings);
            mStrings = strings;
        }

        @Override
        public boolean matches(@Nullable List<String> argument) {
            if (argument == null) {
                return false;
            }
            if (argument.size() != mStrings.size()) {
                return false;
            }
            if (!argument.containsAll(mStrings)) {
                return false;
            }
            return mStrings.containsAll(argument);
        }
    }

    @Test
    public void clearConsentForUninstalledApp() throws RemoteException {
        mAdServicesStorageManager.clearConsentForUninstalledApp(
                AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        verify(mMockIAdServicesManager)
                .clearConsentForUninstalledApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
    }

    @Before
    public void setup() throws IOException {
        mPackageManager = mSpyContext.getPackageManager();
        mAdServicesManager = new AdServicesManager(mMockIAdServicesManager);
        doReturn(mPackageManager).when(mSpyContext).getPackageManager();
        doReturn(mAdServicesManager).when(mSpyContext).getSystemService(AdServicesManager.class);
        mAdServicesStorageManager =
                spy(
                        new AdServicesStorageManager(
                                mAdServicesManager, mSpyContext.getPackageManager()));
    }

    @Test
    public void testGetKnownAppsWithConsent() throws RemoteException {
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);

        List<ApplicationInfo> applicationsInstalled =
                createApplicationInfos(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP20_PACKAGE_NAME,
                        AppConsentDaoFixture.APP30_PACKAGE_NAME);
        List<String> applicationsInstalledNames =
                applicationsInstalled.stream()
                        .map(applicationInfo -> applicationInfo.packageName)
                        .collect(Collectors.toList());
        mockInstalledApplications(applicationsInstalledNames);

        doReturn(applicationsInstalledNames)
                .when(mMockIAdServicesManager)
                .getKnownAppsWithConsent(
                        argThat(new ListMatcherIgnoreOrder(applicationsInstalledNames)));
        doReturn(List.of())
                .when(mMockIAdServicesManager)
                .getAppsWithRevokedConsent(
                        argThat(new ListMatcherIgnoreOrder(applicationsInstalledNames)));

        ImmutableList<String> knownAppsWithConsent =
                mAdServicesStorageManager.getKnownAppsWithConsent();
        ImmutableList<String> appsWithRevokedConsent =
                mAdServicesStorageManager.getAppsWithRevokedConsent();

        verify(mMockIAdServicesManager)
                .getKnownAppsWithConsent(
                        argThat(new ListMatcherIgnoreOrder(applicationsInstalledNames)));
        verify(mMockIAdServicesManager)
                .getAppsWithRevokedConsent(
                        argThat(new ListMatcherIgnoreOrder(applicationsInstalledNames)));

        // all apps have received a consent
        expect.that(knownAppsWithConsent).hasSize(3);
        expect.that(appsWithRevokedConsent).isEmpty();
    }

    @Test
    public void testIsFledgeConsentRevokedForAppAfterSetFledgeUseWithFullApiConsent()
            throws PackageManager.NameNotFoundException, RemoteException {
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);
        expect.that(mAdServicesStorageManager.getConsent(AdServicesApiType.ALL_API).isGiven())
                .isTrue();

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        doReturn(false)
                .when(mMockIAdServicesManager)
                .setConsentForAppIfNew(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP10_UID,
                        false);
        doReturn(true)
                .when(mMockIAdServicesManager)
                .setConsentForAppIfNew(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME,
                        AppConsentDaoFixture.APP20_UID,
                        false);
        doReturn(false)
                .when(mMockIAdServicesManager)
                .setConsentForAppIfNew(
                        AppConsentDaoFixture.APP30_PACKAGE_NAME,
                        AppConsentDaoFixture.APP30_UID,
                        false);

        expect.that(
                        mAdServicesStorageManager.setConsentForAppIfNew(
                                AppConsentDaoFixture.APP10_PACKAGE_NAME, false))
                .isFalse();
        expect.that(
                        mAdServicesStorageManager.setConsentForAppIfNew(
                                AppConsentDaoFixture.APP20_PACKAGE_NAME, false))
                .isTrue();
        expect.that(
                        mAdServicesStorageManager.setConsentForAppIfNew(
                                AppConsentDaoFixture.APP30_PACKAGE_NAME, false))
                .isFalse();
    }

    @Test
    public void testIsFledgeConsentRevokedForAppWithFullApiConsentGaUxDisabled()
            throws PackageManager.NameNotFoundException, RemoteException {
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);
        expect.that(mAdServicesStorageManager.getConsent(AdServicesApiType.ALL_API).isGiven())
                .isTrue();

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        doReturn(false)
                .when(mMockIAdServicesManager)
                .isConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        doReturn(true)
                .when(mMockIAdServicesManager)
                .isConsentRevokedForApp(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        doReturn(false)
                .when(mMockIAdServicesManager)
                .isConsentRevokedForApp(
                        AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        expect.that(
                        mAdServicesStorageManager.isConsentRevokedForApp(
                                AppConsentDaoFixture.APP10_PACKAGE_NAME))
                .isFalse();
        expect.that(
                        mAdServicesStorageManager.isConsentRevokedForApp(
                                AppConsentDaoFixture.APP20_PACKAGE_NAME))
                .isTrue();
        expect.that(
                        mAdServicesStorageManager.isConsentRevokedForApp(
                                AppConsentDaoFixture.APP30_PACKAGE_NAME))
                .isFalse();
    }

    @Test
    public void testIsFledgeConsentRevokedForAppWithFullApiConsentGaUxEnabled()
            throws PackageManager.NameNotFoundException, RemoteException {
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);

        expect.that(mAdServicesStorageManager.getConsent(AdServicesApiType.FLEDGE).isGiven())
                .isTrue();
        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        doReturn(false)
                .when(mMockIAdServicesManager)
                .isConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        doReturn(true)
                .when(mMockIAdServicesManager)
                .isConsentRevokedForApp(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        doReturn(false)
                .when(mMockIAdServicesManager)
                .isConsentRevokedForApp(
                        AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        expect.that(
                        mAdServicesStorageManager.isConsentRevokedForApp(
                                AppConsentDaoFixture.APP10_PACKAGE_NAME))
                .isFalse();
        expect.that(
                        mAdServicesStorageManager.isConsentRevokedForApp(
                                AppConsentDaoFixture.APP20_PACKAGE_NAME))
                .isTrue();
        expect.that(
                        mAdServicesStorageManager.isConsentRevokedForApp(
                                AppConsentDaoFixture.APP30_PACKAGE_NAME))
                .isFalse();
    }

    @Test
    public void testIsFledgeConsentRevokedForNotFoundAppGaUxDisabledThrows()
            throws RemoteException {
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);
        expect.that(mAdServicesStorageManager.getConsent(AdServicesApiType.ALL_API).isGiven())
                .isTrue();

        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAdServicesStorageManager.isConsentRevokedForApp(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForNotFoundAppGaUxEnabledThrows() throws RemoteException {
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);
        expect.that(mAdServicesStorageManager.getConsent(AdServicesApiType.FLEDGE).isGiven())
                .isTrue();

        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAdServicesStorageManager.isConsentRevokedForApp(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testManualInteractionWithConsentRecorded() throws RemoteException {
        expect.that(mAdServicesStorageManager.getUserManualInteractionWithConsent())
                .isEqualTo(UNKNOWN);

        verify(mMockIAdServicesManager).getUserManualInteractionWithConsent();

        doReturn(MANUAL_INTERACTIONS_RECORDED)
                .when(mMockIAdServicesManager)
                .getUserManualInteractionWithConsent();
        mAdServicesStorageManager.recordUserManualInteractionWithConsent(
                MANUAL_INTERACTIONS_RECORDED);

        expect.that(mAdServicesStorageManager.getUserManualInteractionWithConsent())
                .isEqualTo(MANUAL_INTERACTIONS_RECORDED);

        verify(mMockIAdServicesManager, times(2)).getUserManualInteractionWithConsent();
        verify(mMockIAdServicesManager).recordUserManualInteractionWithConsent(anyInt());
    }

    @Test
    public void testResetAllAppConsentAndAppData() throws RemoteException {
        mAdServicesManager.clearAllAppConsentData();
        verify(mMockIAdServicesManager).clearAllAppConsentData();
    }

    @Test
    public void testResetAllowedAppConsentAndAppData() throws RemoteException {
        mockInstalledApplications(new ArrayList<>());
        mAdServicesStorageManager.clearKnownAppsWithConsent();
        verify(mMockIAdServicesManager).clearKnownAppsWithConsent();
    }

    @Test
    public void testSetConsentForApp() throws Exception {

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);

        mAdServicesStorageManager.setConsentForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME, true);
        verify(mMockIAdServicesManager)
                .setConsentForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP10_UID,
                        true);

        mAdServicesStorageManager.setConsentForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME, false);
        verify(mMockIAdServicesManager)
                .setConsentForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP10_UID,
                        false);
    }

    private List<ApplicationInfo> createApplicationInfos(String... packageNames) {
        return Arrays.stream(packageNames)
                .map(s -> ApplicationInfoBuilder.newBuilder().setPackageName(s).build())
                .collect(Collectors.toList());
    }

    // Mock helper functions, we want to directly mock the method of  mAdServicesStorageManager
    // instead of PackageManagerCompatUtils, static mock cause some extra issue
    private void mockGetPackageUid(@NonNull String packageName, int uid)
            throws PackageManager.NameNotFoundException {
        doReturn(uid).when(mAdServicesStorageManager).getUidForInstalledPackageName(packageName);
    }

    private void mockInstalledApplications(List<String> applicationsInstalled) {
        doReturn(applicationsInstalled).when(mAdServicesStorageManager).getInstalledPackages();
    }

    private void mockThrowExceptionOnGetPackageUid(@NonNull String packageName) {
        doThrow(IllegalArgumentException.class)
                .when(mAdServicesStorageManager)
                .getUidForInstalledPackageName(packageName);
    }
}
