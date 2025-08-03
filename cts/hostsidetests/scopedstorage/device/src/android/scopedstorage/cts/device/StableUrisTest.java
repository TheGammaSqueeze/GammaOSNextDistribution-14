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

package android.scopedstorage.cts.device;

import static android.app.AppOpsManager.permissionToOp;
import static android.os.SystemProperties.getBoolean;
import static android.scopedstorage.cts.device.FileCreationUtils.createContentFromResource;
import static android.scopedstorage.cts.lib.TestUtils.allowAppOpsToUid;
import static android.scopedstorage.cts.lib.TestUtils.getPicturesDir;
import static android.scopedstorage.cts.lib.TestUtils.readMaximumRowIdFromDatabaseAs;
import static android.scopedstorage.cts.lib.TestUtils.readMinimumRowIdFromDatabaseAs;
import static android.scopedstorage.cts.lib.TestUtils.waitForMountedAndIdleState;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Instrumentation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.platform.test.annotations.FlakyTest;
import android.provider.MediaStore;
import android.scopedstorage.cts.lib.ScopedStorageBaseDeviceTest;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.cts.install.lib.TestApp;

import com.google.common.io.Files;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RunWith(Parameterized.class)
public final class StableUrisTest extends ScopedStorageBaseDeviceTest {

    private static final String TAG = "StableUrisTest";

    // An app that has file manager (MANAGE_EXTERNAL_STORAGE) permission.
    private static final TestApp APP_FM = new TestApp("TestAppFileManager",
            "android.scopedstorage.cts.testapp.filemanager", 1, false,
            "CtsScopedStorageTestAppFileManager.apk");

    private static final String OPSTR_MANAGE_EXTERNAL_STORAGE =
            permissionToOp(Manifest.permission.MANAGE_EXTERNAL_STORAGE);

    private static final int MAX_MEDIA_FILES_COUNT_THRESHOLD = 1000;

    private Context mContext;
    private ContentResolver mContentResolver;
    private UiDevice mDevice;

    @Parameter()
    public String mVolumeName;

    /** Parameters data. */
    @Parameterized.Parameters(name = "volume={0}")
    public static Iterable<?> data() {
        return Arrays.asList(MediaStore.VOLUME_EXTERNAL);
    }

    @Before
    public void setUp() throws Exception {
        super.setupExternalStorage(mVolumeName);
        mContext = ApplicationProvider.getApplicationContext();
        mContentResolver = mContext.getContentResolver();
        final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        mDevice = UiDevice.getInstance(inst);
        final int mMediaFilesCount = getMediaFilesCount();
        Log.d(TAG, "Number of media files on device: " + mMediaFilesCount);

        assumeTrue("The number of media files is too large; Skipping the test as it "
                        + "will take too much time to execute",
                mMediaFilesCount <= MAX_MEDIA_FILES_COUNT_THRESHOLD);
    }

    @Test
    public void testUrisMapToExistingIds_withoutNextRowIdBackup() throws Exception {
        assumeFalse(getBoolean("persist.sys.fuse.backup.nextrowid_enabled", true));
        testScenario(/* nextRowIdBackupEnabled */ false);
    }

    @Test
    @FlakyTest
    @Ignore
    public void testAttributesRestoration() throws Exception {
        Map<File, Uri> fileToUriMap = new HashMap<>();

        try {
            setFlag("persist.sys.fuse.backup.internal_db_backup", true);
            setFlag("persist.sys.fuse.backup.external_volume_backup", true);

            fileToUriMap = createFiles(5);
            final Map<File, Bundle> fileToAttributesMapBeforeRestore = setAttributes(fileToUriMap);

            final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            final ContentResolver resolver = context.getContentResolver();
            MediaStore.waitForIdle(resolver);
            resolver.call(MediaStore.AUTHORITY, "idle_maintenance_for_stable_uris",
                    null, null);

            // Clear MediaProvider package data to trigger DB recreation.
            mDevice.executeShellCommand("pm clear " + getMediaProviderPackageName());

            // Sleeping to make sure the db recovering is completed
            Thread.sleep(40000);

            verifyAttributes(fileToUriMap, fileToAttributesMapBeforeRestore);
        } finally {
            for (File file : fileToUriMap.keySet()) {
                file.delete();
            }
        }
    }

    @Test
    public void testUrisMapToNewIds_withNextRowIdBackup() throws Exception {
        assumeTrue(getBoolean("persist.sys.fuse.backup.nextrowid_enabled", false));
        testScenario(/* nextRowIdBackupEnabled */ true);
    }

    private void testScenario(boolean nextRowIdBackupEnabled) throws Exception {
        List<File> files = new ArrayList<>();

        try {
            // Test App needs to be explicitly granted MES app op.
            final int fmUid = mContext.getPackageManager().getPackageUid(APP_FM.getPackageName(),
                    0);
            allowAppOpsToUid(fmUid, OPSTR_MANAGE_EXTERNAL_STORAGE);

            files.addAll(createFiles(5).keySet());

            long maxRowIdOfInternalDbBeforeReset = readMaximumRowIdFromDatabaseAs(APP_FM,
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_INTERNAL));
            Log.d(TAG, "maxRowIdOfInternalDbBeforeReset:" + maxRowIdOfInternalDbBeforeReset);
            long maxRowIdOfExternalDbBeforeReset = readMaximumRowIdFromDatabaseAs(APP_FM,
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL));
            Log.d(TAG, "maxRowIdOfExternalDbBeforeReset:" + maxRowIdOfExternalDbBeforeReset);

            // Clear MediaProvider package data to trigger DB recreation.
            mDevice.executeShellCommand("pm clear " + getMediaProviderPackageName());
            waitForMountedAndIdleState(mContentResolver);
            MediaStore.scanVolume(mContentResolver, mVolumeName);

            long minRowIdOfInternalDbAfterReset = readMinimumRowIdFromDatabaseAs(APP_FM,
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_INTERNAL));
            Log.d(TAG, "minRowIdOfInternalDbAfterReset:" + minRowIdOfInternalDbAfterReset);
            long minRowIdOfExternalDbAfterReset = readMinimumRowIdFromDatabaseAs(APP_FM,
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL));
            Log.d(TAG, "minRowIdOfExternalDbAfterReset:" + minRowIdOfExternalDbAfterReset);

            if (nextRowIdBackupEnabled) {
                assertWithMessage(
                        "Expected minimum row id after internal database reset to be greater "
                                + "than max row id before reset").that(
                        minRowIdOfInternalDbAfterReset > maxRowIdOfInternalDbBeforeReset).isTrue();
                assertWithMessage(
                        "Expected minimum row id after external database reset to be greater "
                                + "than max row id before reset").that(
                        minRowIdOfExternalDbAfterReset > maxRowIdOfExternalDbBeforeReset).isTrue();
            } else {
                assertWithMessage(
                        "Expected internal database row ids to be reused without next row id "
                                + "backup").that(
                        minRowIdOfInternalDbAfterReset <= maxRowIdOfInternalDbBeforeReset).isTrue();
                assertWithMessage(
                        "Expected external database row ids to be reused without next row id "
                                + "backup").that(
                        minRowIdOfExternalDbAfterReset <= maxRowIdOfExternalDbBeforeReset).isTrue();
            }

        } finally {
            for (File file : files) {
                file.delete();
            }
        }
    }

    private Map<File, Uri> createFiles(int count) throws Exception {
        final Map<File, Uri> files = new HashMap<>();
        File buffer = new File(getPicturesDir(),
                "Cts_buffer_" + System.currentTimeMillis() + ".jpg");
        createContentFromResource(R.raw.img_with_metadata, buffer);
        for (int i = 1; i <= count; i++) {
            final File file = new File(getPicturesDir(),
                    "Cts_" + System.currentTimeMillis() + ".jpg");

            if (!file.createNewFile()) {
                throw new RuntimeException(
                        "File was not created on path: " + file.getAbsolutePath());
            }
            Files.copy(buffer, file);

            final Uri uri = MediaStore.scanFile(mContentResolver, file);
            if (uri == null) {
                throw new RuntimeException("Scanning returned null uri for file "
                        + file.getAbsolutePath());
            }
            files.put(file, uri);
        }

        return files;
    }

    private void verifyAttributes(Map<File, Uri> fileToUriMap,
            Map<File, Bundle> fileToAttributesMapBeforeRestore) {
        Log.d(TAG, "Started attributes verification after db restore");
        for (Map.Entry<File, Uri> entry : fileToUriMap.entrySet()) {
            final Bundle originalAttributes = fileToAttributesMapBeforeRestore.get(entry.getKey());
            final Bundle attributesAfterRestore = queryMedia(entry.getValue(),
                    originalAttributes.keySet());

            assertWithMessage("Uri doesn't point to a media file after db restore")
                    .that(attributesAfterRestore.isEmpty()).isFalse();

            for (String attribute : originalAttributes.keySet()) {
                final String afterRestore = attributesAfterRestore.getString(attribute);
                final String beforeRestore = fileToAttributesMapBeforeRestore
                        .get(entry.getKey()).getString(attribute);

                final String assertMessage = String.format("Expected values for %s attribute to be "
                        + "equal before and after DB restoration", attribute);
                assertWithMessage(assertMessage)
                        .that(afterRestore).isEqualTo(beforeRestore);
            }
        }
        Log.d(TAG, "Finished attributes verification after db restore");
    }

    private Map<File, Bundle> setAttributes(Map<File, Uri> fileToUriMap) {
        final Map<File, Bundle> fileToAttributes = new HashMap<>();
        int seed = 0;
        for (Map.Entry<File, Uri> entry : fileToUriMap.entrySet()) {
            final Bundle attributes = generateAttributes(seed++);
            updateMedia(entry.getValue(), attributes);

            final Bundle autoGeneratedAttributes = queryMedia(entry.getValue(),
                    new HashSet<>(Arrays.asList(
                            MediaStore.MediaColumns._ID,
                            MediaStore.MediaColumns.DATE_EXPIRES,
                            MediaStore.MediaColumns.OWNER_PACKAGE_NAME)));

            attributes.putAll(autoGeneratedAttributes);
            fileToAttributes.put(entry.getKey(), attributes);
            Log.d(TAG, String.format("Attributes to verify - uri: %s, attributes: %s",
                    entry.getKey(), attributes));
        }
        return fileToAttributes;
    }

    private Bundle generateAttributes(int seed) {
        final Bundle attributes = new Bundle();
        attributes.putString(MediaStore.MediaColumns.IS_FAVORITE, seed % 2 == 0 ? "1" : "0");
        attributes.putString(MediaStore.MediaColumns.IS_PENDING, seed % 3 == 0 ? "1" : "0");
        // Shouldn't set both IS_PENDING and IS_TRASHED
        attributes.putString(MediaStore.MediaColumns.IS_TRASHED,
                seed % 4 == 0 && seed % 3 != 0 ? "1" : "0");

        return attributes;
    }

    private Bundle queryMedia(Uri uri, Set<String> projection) {
        try (Cursor c = mContentResolver.query(uri,
                projection.toArray(new String[0]), null, null)) {
            final Bundle result = new Bundle();
            c.moveToFirst();
            for (String column : projection) {
                result.putString(column, c.getString(c.getColumnIndex(column)));
            }

            return result;
        }
    }

    private int getMediaFilesCount() {
        try (Cursor c = mContentResolver.query(MediaStore.Files.getContentUri(mVolumeName),
                new String[]{MediaStore.MediaColumns.DISPLAY_NAME},
                null, null)) {
            return c.getCount();
        }
    }

    private boolean updateMedia(Uri uri, Bundle attributes) {
        final ContentValues values = new ContentValues();
        for (String key : attributes.keySet()) {
            values.put(key, attributes.getString(key));
        }
        return mContentResolver.update(uri, values, null, null) == 1;
    }

    private static void setFlag(String flagName, boolean value) throws Exception {
        final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        final UiDevice uiDevice = UiDevice.getInstance(inst);
        uiDevice.executeShellCommand(
                "setprop " + flagName + " " + value);
        final String newValue = uiDevice.executeShellCommand("getprop " + flagName).trim();

        assumeTrue("Not able to set flag: " + flagName,
                String.valueOf(value).equals(newValue));
    }

    private static String getMediaProviderPackageName() {
        final Instrumentation inst = androidx.test.InstrumentationRegistry.getInstrumentation();
        final PackageManager packageManager = inst.getContext().getPackageManager();
        final ProviderInfo providerInfo = packageManager.resolveContentProvider(
                MediaStore.AUTHORITY, PackageManager.MATCH_ALL);
        return providerInfo.packageName;
    }
}
