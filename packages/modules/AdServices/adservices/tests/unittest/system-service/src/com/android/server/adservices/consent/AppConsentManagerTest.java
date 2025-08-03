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

package com.android.server.adservices.consent;

import static com.android.adservices.shared.testing.common.DumpHelper.assertDumpHasPrefix;
import static com.android.adservices.shared.testing.common.DumpHelper.dump;
import static com.android.server.adservices.consent.AppConsentManager.DUMP_PREFIX;
import static com.android.server.adservices.consent.ConsentManager.STORAGE_XML_IDENTIFIER;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.shared.storage.BooleanFileDatastore;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AppConsentManagerTest {
    @Rule public final MockitoRule rule = MockitoJUnit.rule();
    private AppConsentManager mAppConsentManager;

    private static final Context PPAPI_CONTEXT = ApplicationProvider.getApplicationContext();
    private static final String BASE_DIR = PPAPI_CONTEXT.getFilesDir().getAbsolutePath();

    @Spy
    private BooleanFileDatastore mDatastoreSpy =
            new BooleanFileDatastore(
                    BASE_DIR,
                    AppConsentManagerFixture.TEST_DATASTORE_NAME,
                    1,
                    STORAGE_XML_IDENTIFIER);

    @Before
    public void setup() throws IOException {
        mAppConsentManager = new AppConsentManager(mDatastoreSpy);
        mDatastoreSpy.initialize();
    }

    @After
    public void teardown() throws IOException {
        mDatastoreSpy.clear();
    }

    @Test
    public void testCreateAppConsentManagerWithNullBaseDir_throwNpe() {
        assertThrows(
                AppConsentManager.BASE_DIR_MUST_BE_PROVIDED_ERROR_MESSAGE,
                NullPointerException.class,
                () -> AppConsentManager.createAppConsentManager(null, 0));
    }

    @Test
    public void testPackageNameToDatastoreKeySuccess() {
        assertEquals(
                AppConsentManagerFixture.APP10_DATASTORE_KEY,
                mAppConsentManager.toDatastoreKey(
                        AppConsentManagerFixture.APP10_PACKAGE_NAME,
                        AppConsentManagerFixture.APP10_UID));
    }

    @Test
    public void testPackageNameAndUidToDatastoreKeySuccess() {
        assertEquals(
                AppConsentManagerFixture.APP10_DATASTORE_KEY,
                mAppConsentManager.toDatastoreKey(
                        AppConsentManagerFixture.APP10_PACKAGE_NAME,
                        AppConsentManagerFixture.APP10_UID));
    }

    @Test
    public void testPackageNameAndInvalidUidToDatastoreKeyThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAppConsentManager.toDatastoreKey(
                                AppConsentManagerFixture.APP10_PACKAGE_NAME, -10));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAppConsentManager.toDatastoreKey(
                                AppConsentManagerFixture.APP10_PACKAGE_NAME, 0));
    }

    @Test
    public void testDatastoreKeyToPackageNameSuccess() {
        String testPackageName =
                mAppConsentManager.datastoreKeyToPackageName(
                        AppConsentManagerFixture.APP10_DATASTORE_KEY);
        assertEquals(AppConsentManagerFixture.APP10_PACKAGE_NAME, testPackageName);
    }

    @Test
    public void testEmptyDatastoreKeyToPackageNameThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mAppConsentManager.datastoreKeyToPackageName(""));
    }

    @Test
    public void testInvalidDatastoreKeyToPackageNameThrows() {
        assertThrows(
                "Missing UID should throw",
                IllegalArgumentException.class,
                () -> mAppConsentManager.datastoreKeyToPackageName("invalid.missing.uid"));
        assertThrows(
                "Missing package name should throw",
                IllegalArgumentException.class,
                () -> mAppConsentManager.datastoreKeyToPackageName("98"));
        assertThrows(
                "Missing separator should throw",
                IllegalArgumentException.class,
                () -> mAppConsentManager.datastoreKeyToPackageName("invalid.missing.separator22"));
    }

    @Test
    public void testDatastoreKeyConversion() throws PackageManager.NameNotFoundException {
        // Package name to datastore key and back to package name
        String convertedDatastoreKey =
                mAppConsentManager.toDatastoreKey(
                        AppConsentManagerFixture.APP10_PACKAGE_NAME,
                        AppConsentManagerFixture.APP10_UID);
        assertEquals(AppConsentManagerFixture.APP10_DATASTORE_KEY, convertedDatastoreKey);
        String convertedPackageName =
                mAppConsentManager.datastoreKeyToPackageName(convertedDatastoreKey);
        assertEquals(AppConsentManagerFixture.APP10_PACKAGE_NAME, convertedPackageName);

        // Datastore key to package name and back
        convertedPackageName =
                mAppConsentManager.datastoreKeyToPackageName(
                        AppConsentManagerFixture.APP20_DATASTORE_KEY);
        assertEquals(AppConsentManagerFixture.APP20_PACKAGE_NAME, convertedPackageName);
        convertedDatastoreKey =
                mAppConsentManager.toDatastoreKey(
                        convertedPackageName, AppConsentManagerFixture.APP20_UID);
        assertEquals(AppConsentManagerFixture.APP20_DATASTORE_KEY, convertedDatastoreKey);
    }

    @Test
    public void testSetConsentForAppSuccess() throws IOException {

        assertNull(mDatastoreSpy.get(AppConsentManagerFixture.APP10_DATASTORE_KEY));
        assertNull(mDatastoreSpy.get(AppConsentManagerFixture.APP20_DATASTORE_KEY));

        mAppConsentManager.setConsentForApp(
                AppConsentManagerFixture.APP10_PACKAGE_NAME,
                AppConsentManagerFixture.APP10_UID,
                true);

        assertEquals(true, mDatastoreSpy.get(AppConsentManagerFixture.APP10_DATASTORE_KEY));
        assertNull(mDatastoreSpy.get(AppConsentManagerFixture.APP20_DATASTORE_KEY));

        mAppConsentManager.setConsentForApp(
                AppConsentManagerFixture.APP20_PACKAGE_NAME,
                AppConsentManagerFixture.APP20_UID,
                false);

        assertEquals(true, mDatastoreSpy.get(AppConsentManagerFixture.APP10_DATASTORE_KEY));
        assertEquals(false, mDatastoreSpy.get(AppConsentManagerFixture.APP20_DATASTORE_KEY));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testSetConsentForAppIfNewWithNewKeysSuccess() throws IOException {

        assertNull(mDatastoreSpy.get(AppConsentManagerFixture.APP10_DATASTORE_KEY));
        assertNull(mDatastoreSpy.get(AppConsentManagerFixture.APP20_DATASTORE_KEY));

        assertTrue(
                mAppConsentManager.setConsentForAppIfNew(
                        AppConsentManagerFixture.APP10_PACKAGE_NAME,
                        AppConsentManagerFixture.APP10_UID,
                        true));

        assertEquals(true, mDatastoreSpy.get(AppConsentManagerFixture.APP10_DATASTORE_KEY));
        assertNull(mDatastoreSpy.get(AppConsentManagerFixture.APP20_DATASTORE_KEY));

        assertFalse(
                mAppConsentManager.setConsentForAppIfNew(
                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                        AppConsentManagerFixture.APP20_UID,
                        false));

        assertEquals(true, mDatastoreSpy.get(AppConsentManagerFixture.APP10_DATASTORE_KEY));
        assertEquals(false, mDatastoreSpy.get(AppConsentManagerFixture.APP20_DATASTORE_KEY));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testSetConsentForAppIfNewWithExistingKeysUsesOldValues()
            throws IOException, PackageManager.NameNotFoundException {

        mDatastoreSpy.put(AppConsentManagerFixture.APP10_DATASTORE_KEY, false);
        mDatastoreSpy.put(AppConsentManagerFixture.APP20_DATASTORE_KEY, true);

        assertEquals(false, mDatastoreSpy.get(AppConsentManagerFixture.APP10_DATASTORE_KEY));
        assertEquals(true, mDatastoreSpy.get(AppConsentManagerFixture.APP20_DATASTORE_KEY));

        assertFalse(
                mAppConsentManager.setConsentForAppIfNew(
                        AppConsentManagerFixture.APP10_PACKAGE_NAME,
                        AppConsentManagerFixture.APP10_UID,
                        true));
        assertTrue(
                mAppConsentManager.setConsentForAppIfNew(
                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                        AppConsentManagerFixture.APP20_UID,
                        false));

        assertEquals(false, mDatastoreSpy.get(AppConsentManagerFixture.APP10_DATASTORE_KEY));
        assertEquals(true, mDatastoreSpy.get(AppConsentManagerFixture.APP20_DATASTORE_KEY));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testIsConsentRevokedForAppSuccess() throws IOException {

        assertNull(mDatastoreSpy.get(AppConsentManagerFixture.APP10_DATASTORE_KEY));
        assertFalse(
                mAppConsentManager.isConsentRevokedForApp(
                        AppConsentManagerFixture.APP10_PACKAGE_NAME,
                        AppConsentManagerFixture.APP10_UID));
        assertNull(mDatastoreSpy.get(AppConsentManagerFixture.APP20_DATASTORE_KEY));
        assertFalse(
                mAppConsentManager.isConsentRevokedForApp(
                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                        AppConsentManagerFixture.APP20_UID));

        mDatastoreSpy.put(AppConsentManagerFixture.APP10_DATASTORE_KEY, true);

        assertEquals(true, mDatastoreSpy.get(AppConsentManagerFixture.APP10_DATASTORE_KEY));
        assertTrue(
                mAppConsentManager.isConsentRevokedForApp(
                        AppConsentManagerFixture.APP10_PACKAGE_NAME,
                        AppConsentManagerFixture.APP10_UID));
        assertNull(mDatastoreSpy.get(AppConsentManagerFixture.APP20_DATASTORE_KEY));
        assertFalse(
                mAppConsentManager.isConsentRevokedForApp(
                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                        AppConsentManagerFixture.APP20_UID));

        mDatastoreSpy.put(AppConsentManagerFixture.APP20_DATASTORE_KEY, false);

        assertEquals(true, mDatastoreSpy.get(AppConsentManagerFixture.APP10_DATASTORE_KEY));
        assertTrue(
                mAppConsentManager.isConsentRevokedForApp(
                        AppConsentManagerFixture.APP10_PACKAGE_NAME,
                        AppConsentManagerFixture.APP10_UID));
        assertEquals(false, mDatastoreSpy.get(AppConsentManagerFixture.APP20_DATASTORE_KEY));
        assertFalse(
                mAppConsentManager.isConsentRevokedForApp(
                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                        AppConsentManagerFixture.APP20_UID));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testGetKnownAppsWithConsent() throws IOException {
        mDatastoreSpy.put(AppConsentManagerFixture.APP10_DATASTORE_KEY, false);
        mDatastoreSpy.put(AppConsentManagerFixture.APP20_DATASTORE_KEY, false);
        mDatastoreSpy.put(AppConsentManagerFixture.APP30_DATASTORE_KEY, true);
        List<String> applicationsInstalled =
                Arrays.asList(
                        AppConsentManagerFixture.APP10_PACKAGE_NAME,
                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                        AppConsentManagerFixture.APP30_PACKAGE_NAME);

        final List<String> knownAppsWithConsent =
                mAppConsentManager.getKnownAppsWithConsent(applicationsInstalled);

        assertEquals(2, knownAppsWithConsent.size());
        assertTrue(
                knownAppsWithConsent.containsAll(
                        Arrays.asList(
                                AppConsentManagerFixture.APP10_PACKAGE_NAME,
                                AppConsentManagerFixture.APP20_PACKAGE_NAME)));
        assertFalse(knownAppsWithConsent.contains(AppConsentManagerFixture.APP30_PACKAGE_NAME));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testGetKnownAppsWithConsentNotExistentApp() throws IOException {
        mDatastoreSpy.put(AppConsentManagerFixture.APP30_DATASTORE_KEY, true);

        final List<String> knownAppsWithConsent =
                mAppConsentManager.getKnownAppsWithConsent(new ArrayList<>());

        assertEquals(0, knownAppsWithConsent.size());

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testGetAppsWithRevokedConsent() throws IOException {
        mDatastoreSpy.put(AppConsentManagerFixture.APP10_DATASTORE_KEY, true);
        mDatastoreSpy.put(AppConsentManagerFixture.APP20_DATASTORE_KEY, true);
        mDatastoreSpy.put(AppConsentManagerFixture.APP30_DATASTORE_KEY, false);
        List<String> applicationsInstalled =
                Arrays.asList(
                        AppConsentManagerFixture.APP10_PACKAGE_NAME,
                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                        AppConsentManagerFixture.APP30_PACKAGE_NAME);

        final List<String> appsWithRevokedConsent =
                mAppConsentManager.getAppsWithRevokedConsent(applicationsInstalled);

        assertEquals(2, appsWithRevokedConsent.size());
        assertTrue(
                appsWithRevokedConsent.containsAll(
                        Arrays.asList(
                                AppConsentManagerFixture.APP10_PACKAGE_NAME,
                                AppConsentManagerFixture.APP20_PACKAGE_NAME)));
        assertFalse(appsWithRevokedConsent.contains(AppConsentManagerFixture.APP30_PACKAGE_NAME));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testGetAppsWithRevokedConsentNonExistentApp() throws IOException {
        mDatastoreSpy.put(AppConsentManagerFixture.APP10_DATASTORE_KEY, true);

        final List<String> appsWithRevokedConsent =
                mAppConsentManager.getAppsWithRevokedConsent(new ArrayList<>());

        assertEquals(0, appsWithRevokedConsent.size());

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testClearAllConsentData() throws IOException {
        mDatastoreSpy.put(AppConsentManagerFixture.APP10_DATASTORE_KEY, true);
        mDatastoreSpy.put(AppConsentManagerFixture.APP20_DATASTORE_KEY, true);
        mDatastoreSpy.put(AppConsentManagerFixture.APP30_DATASTORE_KEY, false);

        mAppConsentManager.clearAllAppConsentData();

        assertNull(mDatastoreSpy.get(AppConsentManagerFixture.APP10_DATASTORE_KEY));
        assertNull(mDatastoreSpy.get(AppConsentManagerFixture.APP20_DATASTORE_KEY));
        assertNull(mDatastoreSpy.get(AppConsentManagerFixture.APP30_DATASTORE_KEY));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testClearKnownAppsWithConsent() throws IOException {
        mDatastoreSpy.put(AppConsentManagerFixture.APP10_DATASTORE_KEY, true);
        mDatastoreSpy.put(AppConsentManagerFixture.APP20_DATASTORE_KEY, true);
        mDatastoreSpy.put(AppConsentManagerFixture.APP30_DATASTORE_KEY, false);
        List<String> applicationsInstalled =
                Arrays.asList(
                        AppConsentManagerFixture.APP10_PACKAGE_NAME,
                        AppConsentManagerFixture.APP20_PACKAGE_NAME,
                        AppConsentManagerFixture.APP30_PACKAGE_NAME);

        mAppConsentManager.clearKnownAppsWithConsent();

        assertNotNull(mDatastoreSpy.get(AppConsentManagerFixture.APP10_DATASTORE_KEY));
        assertNotNull(mDatastoreSpy.get(AppConsentManagerFixture.APP20_DATASTORE_KEY));
        assertNull(mDatastoreSpy.get(AppConsentManagerFixture.APP30_DATASTORE_KEY));

        assertTrue(mAppConsentManager.getKnownAppsWithConsent(applicationsInstalled).isEmpty());
        assertFalse(mAppConsentManager.getAppsWithRevokedConsent(applicationsInstalled).isEmpty());

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testClearConsentForUninstalledApp() throws IOException {
        mDatastoreSpy.put(AppConsentManagerFixture.APP10_DATASTORE_KEY, true);
        mDatastoreSpy.put(AppConsentManagerFixture.APP20_DATASTORE_KEY, true);
        mDatastoreSpy.put(AppConsentManagerFixture.APP30_DATASTORE_KEY, false);

        mAppConsentManager.clearConsentForUninstalledApp(
                AppConsentManagerFixture.APP20_PACKAGE_NAME, AppConsentManagerFixture.APP20_UID);

        assertNotNull(mDatastoreSpy.get(AppConsentManagerFixture.APP10_DATASTORE_KEY));
        assertNull(mDatastoreSpy.get(AppConsentManagerFixture.APP20_DATASTORE_KEY));
        assertNotNull(mDatastoreSpy.get(AppConsentManagerFixture.APP30_DATASTORE_KEY));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testClearConsentForUninstalledAppWithInvalidArgsThrows() throws IOException {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAppConsentManager.clearConsentForUninstalledApp(
                                AppConsentManagerFixture.APP10_PACKAGE_NAME, 0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAppConsentManager.clearConsentForUninstalledApp(
                                AppConsentManagerFixture.APP10_PACKAGE_NAME, -10));
        assertThrows(
                NullPointerException.class,
                () ->
                        mAppConsentManager.clearConsentForUninstalledApp(
                                null, AppConsentManagerFixture.APP10_UID));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testDump() throws Exception {
        String prefix = "_";

        String dump = dump(pw -> mAppConsentManager.dump(pw, prefix));

        assertWithMessage("content of dump()").that(dump).startsWith(prefix + "AppConsentManager:");
        assertDumpHasPrefix(dump, prefix);

        String datastoreDump =
                dump(pw -> mDatastoreSpy.dump(pw, prefix + DUMP_PREFIX + DUMP_PREFIX));
        assertWithMessage("content of dump() (datastore)").that(dump).contains(datastoreDump);
    }
}
