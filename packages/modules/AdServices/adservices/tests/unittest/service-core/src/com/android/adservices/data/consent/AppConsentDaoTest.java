/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.data.consent;

import static com.android.adservices.data.consent.AppConsentDao.DATASTORE_KEY_SEPARATOR;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.core.content.pm.ApplicationInfoBuilder;

import com.android.adservices.data.common.BooleanFileDatastore;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoSession;
import org.mockito.Spy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class AppConsentDaoTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private AppConsentDao mAppConsentDao;

    @Spy
    private BooleanFileDatastore mDatastoreSpy =
            new BooleanFileDatastore(mContext, AppConsentDaoFixture.TEST_DATASTORE_NAME, 1);

    private MockitoSession mMockitoSession;

    @Before
    public void setup() throws IOException {
        mMockitoSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(PackageManagerCompatUtils.class)
                        .initMocks(this)
                        .startMocking();
        mAppConsentDao = new AppConsentDao(mDatastoreSpy, mContext.getPackageManager());
    }

    @After
    public void teardown() throws IOException {
        mDatastoreSpy.clear();
        mMockitoSession.finishMocking();
    }

    @Test
    public void testInitializeOnlyOnce() throws IOException {
        verify(mDatastoreSpy, never()).initialize();

        mAppConsentDao.initializeDatastoreIfNeeded();
        mAppConsentDao.initializeDatastoreIfNeeded();
        mAppConsentDao.initializeDatastoreIfNeeded();

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testGetUidForInstalledPackageNameWithRealTestNameSuccess() {
        int expectedUid = mContext.getApplicationInfo().uid;
        int testUid = mAppConsentDao.getUidForInstalledPackageName(mContext.getPackageName());
        assertEquals(expectedUid, testUid);
    }

    @Test
    public void testGetUidForInstalledPackageNameWithFakePackageNameThrows() {
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mAppConsentDao.getUidForInstalledPackageName(
                                        AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
        assertTrue(exception.getCause() instanceof PackageManager.NameNotFoundException);
    }

    @Test
    public void testPackageNameToDatastoreKeySuccess() {
        mockPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        assertEquals(
                AppConsentDaoFixture.APP10_DATASTORE_KEY,
                mAppConsentDao.toDatastoreKey(AppConsentDaoFixture.APP10_PACKAGE_NAME));
    }

    @Test
    public void testNotFoundPackageNameToDatastoreKeyThrows() {
        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAppConsentDao.toDatastoreKey(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testPackageNameAndUidToDatastoreKeySuccess() {
        assertEquals(
                AppConsentDaoFixture.APP10_DATASTORE_KEY,
                mAppConsentDao.toDatastoreKey(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID));
    }

    @Test
    public void testPackageNameAndInvalidUidToDatastoreKeyThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mAppConsentDao.toDatastoreKey(AppConsentDaoFixture.APP10_PACKAGE_NAME, -10));
        assertThrows(
                IllegalArgumentException.class,
                () -> mAppConsentDao.toDatastoreKey(AppConsentDaoFixture.APP10_PACKAGE_NAME, 0));
    }

    @Test
    public void testDatastoreKeyToPackageNameSuccess() {
        String testPackageName =
                mAppConsentDao.datastoreKeyToPackageName(AppConsentDaoFixture.APP10_DATASTORE_KEY);
        assertEquals(AppConsentDaoFixture.APP10_PACKAGE_NAME, testPackageName);
    }

    @Test
    public void testEmptyDatastoreKeyToPackageNameThrows() {
        assertThrows(
                IllegalArgumentException.class, () -> mAppConsentDao.datastoreKeyToPackageName(""));
    }

    @Test
    public void testInvalidDatastoreKeyToPackageNameThrows() {
        assertThrows(
                "Missing UID should throw",
                IllegalArgumentException.class,
                () -> mAppConsentDao.datastoreKeyToPackageName("invalid.missing.uid"));
        assertThrows(
                "Missing package name should throw",
                IllegalArgumentException.class,
                () -> mAppConsentDao.datastoreKeyToPackageName("98"));
        assertThrows(
                "Missing separator should throw",
                IllegalArgumentException.class,
                () -> mAppConsentDao.datastoreKeyToPackageName("invalid.missing.separator22"));
    }

    @Test
    public void testDatastoreKeyConversion() {
        mockPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);

        // Package name to datastore key and back to package name
        String convertedDatastoreKey =
                mAppConsentDao.toDatastoreKey(AppConsentDaoFixture.APP10_PACKAGE_NAME);
        assertEquals(AppConsentDaoFixture.APP10_DATASTORE_KEY, convertedDatastoreKey);
        String convertedPackageName =
                mAppConsentDao.datastoreKeyToPackageName(convertedDatastoreKey);
        assertEquals(AppConsentDaoFixture.APP10_PACKAGE_NAME, convertedPackageName);

        // Datastore key to package name and back
        convertedPackageName =
                mAppConsentDao.datastoreKeyToPackageName(AppConsentDaoFixture.APP20_DATASTORE_KEY);
        assertEquals(AppConsentDaoFixture.APP20_PACKAGE_NAME, convertedPackageName);
        convertedDatastoreKey = mAppConsentDao.toDatastoreKey(convertedPackageName);
        assertEquals(AppConsentDaoFixture.APP20_DATASTORE_KEY, convertedDatastoreKey);
    }

    @Test
    public void testSetConsentForAppSuccess() throws IOException {
        mockPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);

        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));

        mAppConsentDao.setConsentForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME, true);

        assertEquals(true, mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));

        mAppConsentDao.setConsentForApp(AppConsentDaoFixture.APP20_PACKAGE_NAME, false);

        assertEquals(true, mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertEquals(false, mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testSetConsentForNotFoundAppThrows() throws IOException {
        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAppConsentDao.setConsentForApp(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME, true));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testSetConsentForAppIfNewWithNewKeysSuccess() throws IOException {
        mockPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);

        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));

        assertTrue(
                mAppConsentDao.setConsentForAppIfNew(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME, true));

        assertEquals(true, mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));

        assertFalse(
                mAppConsentDao.setConsentForAppIfNew(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME, false));

        assertEquals(true, mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertEquals(false, mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testSetConsentForAppIfNewWithExistingKeysUsesOldValues() throws IOException {
        mockPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);

        mDatastoreSpy.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastoreSpy.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);

        assertEquals(false, mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertEquals(true, mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));

        assertFalse(
                mAppConsentDao.setConsentForAppIfNew(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME, true));
        assertTrue(
                mAppConsentDao.setConsentForAppIfNew(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME, false));

        assertEquals(false, mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertEquals(true, mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testSetConsentForNotFoundAppIfNewThrows() throws IOException {
        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAppConsentDao.setConsentForApp(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME, true));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testIsConsentRevokedForAppSuccess() throws IOException {
        mockPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);

        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertFalse(mAppConsentDao.isConsentRevokedForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));
        assertFalse(mAppConsentDao.isConsentRevokedForApp(AppConsentDaoFixture.APP20_PACKAGE_NAME));

        mDatastoreSpy.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);

        assertEquals(true, mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertTrue(mAppConsentDao.isConsentRevokedForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));
        assertFalse(mAppConsentDao.isConsentRevokedForApp(AppConsentDaoFixture.APP20_PACKAGE_NAME));

        mDatastoreSpy.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, false);

        assertEquals(true, mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertTrue(mAppConsentDao.isConsentRevokedForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertEquals(false, mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));
        assertFalse(mAppConsentDao.isConsentRevokedForApp(AppConsentDaoFixture.APP20_PACKAGE_NAME));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testIsConsentRevokedForNotFoundAppThrows() throws IOException {
        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAppConsentDao.isConsentRevokedForApp(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testGetKnownAppsWithConsent() throws IOException {
        mDatastoreSpy.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastoreSpy.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, false);
        mDatastoreSpy.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, true);
        List<ApplicationInfo> applicationsInstalled =
                Arrays.asList(
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP10_PACKAGE_NAME)
                                .build(),
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP20_PACKAGE_NAME)
                                .build(),
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP30_PACKAGE_NAME)
                                .build());
        doReturn(applicationsInstalled)
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));

        final Set<String> knownAppsWithConsent = mAppConsentDao.getKnownAppsWithConsent();

        assertEquals(2, knownAppsWithConsent.size());
        assertTrue(
                knownAppsWithConsent.containsAll(
                        Arrays.asList(
                                AppConsentDaoFixture.APP10_PACKAGE_NAME,
                                AppConsentDaoFixture.APP20_PACKAGE_NAME)));
        assertFalse(knownAppsWithConsent.contains(AppConsentDaoFixture.APP30_PACKAGE_NAME));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testGetKnownAppsWithConsentNotExistentApp() throws IOException {
        mDatastoreSpy.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, true);
        List<ApplicationInfo> applicationsInstalled = new ArrayList<>();
        doReturn(applicationsInstalled)
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));

        final Set<String> knownAppsWithConsent = mAppConsentDao.getKnownAppsWithConsent();

        assertEquals(0, knownAppsWithConsent.size());

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testGetAppsWithRevokedConsent() throws IOException {
        mDatastoreSpy.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);
        mDatastoreSpy.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastoreSpy.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
        List<ApplicationInfo> applicationsInstalled =
                Arrays.asList(
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP10_PACKAGE_NAME)
                                .build(),
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP20_PACKAGE_NAME)
                                .build(),
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP30_PACKAGE_NAME)
                                .build());
        doReturn(applicationsInstalled)
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));

        final Set<String> appsWithRevokedConsent = mAppConsentDao.getAppsWithRevokedConsent();

        assertEquals(2, appsWithRevokedConsent.size());
        assertTrue(
                appsWithRevokedConsent.containsAll(
                        Arrays.asList(
                                AppConsentDaoFixture.APP10_PACKAGE_NAME,
                                AppConsentDaoFixture.APP20_PACKAGE_NAME)));
        assertFalse(appsWithRevokedConsent.contains(AppConsentDaoFixture.APP30_PACKAGE_NAME));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testGetAppsWithRevokedConsentNonExistentApp() throws IOException {
        mDatastoreSpy.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);
        List<ApplicationInfo> applicationsInstalled = new ArrayList<>();
        doReturn(applicationsInstalled)
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));

        final Set<String> appsWithRevokedConsent = mAppConsentDao.getAppsWithRevokedConsent();

        assertEquals(0, appsWithRevokedConsent.size());

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testClearAllConsentData() throws IOException {
        mDatastoreSpy.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);
        mDatastoreSpy.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastoreSpy.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);

        mAppConsentDao.clearAllConsentData();

        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));
        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP30_DATASTORE_KEY));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testClearKnownAppsWithConsent() throws IOException {
        mDatastoreSpy.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);
        mDatastoreSpy.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastoreSpy.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
        List<ApplicationInfo> applicationsInstalled =
                Arrays.asList(
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP10_PACKAGE_NAME)
                                .build(),
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP20_PACKAGE_NAME)
                                .build(),
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP30_PACKAGE_NAME)
                                .build());
        doReturn(applicationsInstalled)
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));

        mAppConsentDao.clearKnownAppsWithConsent();

        assertNotNull(mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertNotNull(mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));
        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP30_DATASTORE_KEY));

        assertTrue(mAppConsentDao.getKnownAppsWithConsent().isEmpty());
        assertFalse(mAppConsentDao.getAppsWithRevokedConsent().isEmpty());

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testClearConsentForUninstalledApp() throws IOException {
        mDatastoreSpy.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);
        mDatastoreSpy.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastoreSpy.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);

        mAppConsentDao.clearConsentForUninstalledApp(
                AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);

        assertNotNull(mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));
        assertNotNull(mDatastoreSpy.get(AppConsentDaoFixture.APP30_DATASTORE_KEY));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testClearConsentForUninstalledAppWithInvalidArgsThrows() throws IOException {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAppConsentDao.clearConsentForUninstalledApp(
                                AppConsentDaoFixture.APP10_PACKAGE_NAME, 0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAppConsentDao.clearConsentForUninstalledApp(
                                AppConsentDaoFixture.APP10_PACKAGE_NAME, -10));
        assertThrows(
                NullPointerException.class,
                () ->
                        mAppConsentDao.clearConsentForUninstalledApp(
                                null, AppConsentDaoFixture.APP10_UID));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testClearAllConsentForUninstalledApp() throws IOException {
        final String app20User10PackageName =
                AppConsentDaoFixture.APP20_PACKAGE_NAME
                        + DATASTORE_KEY_SEPARATOR
                        + AppConsentDaoFixture.APP10_UID;

        // Ensure that a different package name that begins with the one being uninstalled isn't
        // removed from the store.
        final String app20PackageNameAsPrefix =
                AppConsentDaoFixture.APP20_PACKAGE_NAME
                        + "test"
                        + DATASTORE_KEY_SEPARATOR
                        + AppConsentDaoFixture.APP10_UID;

        mDatastoreSpy.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);
        mDatastoreSpy.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastoreSpy.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
        mDatastoreSpy.put(app20User10PackageName, false);
        mDatastoreSpy.put(app20PackageNameAsPrefix, true);

        mAppConsentDao.clearConsentForUninstalledApp(AppConsentDaoFixture.APP20_PACKAGE_NAME);

        assertNotNull(mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));
        assertNotNull(mDatastoreSpy.get(AppConsentDaoFixture.APP30_DATASTORE_KEY));
        assertNull(mDatastoreSpy.get(app20User10PackageName));
        assertNotNull(mDatastoreSpy.get(app20PackageNameAsPrefix));

        verify(mDatastoreSpy).initialize();
        verify(mDatastoreSpy).removeByPrefix(any());
    }

    @Test
    public void testClearAllConsentForUninstalledAppWithInvalidArgsThrows() throws IOException {
        assertThrows(
                NullPointerException.class,
                () -> mAppConsentDao.clearConsentForUninstalledApp(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> mAppConsentDao.clearConsentForUninstalledApp(""));
        verify(mDatastoreSpy, never()).initialize();
    }

    private void mockPackageUid(@NonNull String packageName, int packageUid) {
        doReturn(packageUid)
                .when(
                        () ->
                                PackageManagerCompatUtils.getPackageUid(
                                        any(), eq(packageName), anyInt()));
    }

    private void mockThrowExceptionOnGetPackageUid(@NonNull String packageName) {
        doThrow(PackageManager.NameNotFoundException.class)
                .when(
                        () ->
                                PackageManagerCompatUtils.getPackageUid(
                                        any(), eq(packageName), anyInt()));
    }
}
