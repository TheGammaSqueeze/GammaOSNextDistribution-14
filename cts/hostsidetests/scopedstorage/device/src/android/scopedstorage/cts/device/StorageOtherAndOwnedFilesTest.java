/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.scopedstorage.cts.device.OtherAppFilesRule.GrantModifications.GRANT;
import static android.scopedstorage.cts.device.OtherAppFilesRule.GrantModifications.REVOKE;
import static android.scopedstorage.cts.device.OtherAppFilesRule.modifyReadAccess;
import static android.scopedstorage.cts.device.OwnedAndOtherFilesRule.getResultForFilesQuery;
import static android.scopedstorage.cts.lib.TestUtils.getContentResolver;
import static android.scopedstorage.cts.lib.TestUtils.pollForPermission;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.Manifest;
import android.app.Instrumentation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
public class StorageOtherAndOwnedFilesTest {

    protected static final String TAG = "MediaProviderOtherAndOwnedFilePermissionTest";

    private static final Instrumentation sInstrumentation =
            InstrumentationRegistry.getInstrumentation();
    private static final ContentResolver sContentResolver = getContentResolver();

    @ClassRule
    public static final OwnedAndOtherFilesRule sFilesRule =
            new OwnedAndOtherFilesRule(sContentResolver);

    private static final String THIS_PACKAGE_NAME = ApplicationProvider.getApplicationContext()
            .getPackageName();

    private static final int TOTAL_OWNED_ITEMS = OwnedFilesRule.getAllFiles().size();

    /**
     * Inits test with correct permissions.
     */
    @BeforeClass
    public static void init() throws Exception {
        pollForPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, true);
        Assume.assumeTrue(isHardwareSupported());
    }

    @After
    public void cleanUp() throws IOException {
        // Clear all grants.
        for (File file : OtherAppFilesRule.getAllFiles()) {
            modifyReadAccess(file, THIS_PACKAGE_NAME, REVOKE);
        }
    }

    static boolean isHardwareSupported() {
        PackageManager pm = sInstrumentation.getContext().getPackageManager();

        // Do not run tests on Watches, TVs, Auto or devices without UI.
        return !pm.hasSystemFeature(pm.FEATURE_EMBEDDED)
                && !pm.hasSystemFeature(pm.FEATURE_WATCH)
                && !pm.hasSystemFeature(pm.FEATURE_LEANBACK)
                && !pm.hasSystemFeature(pm.FEATURE_AUTOMOTIVE);
    }

    @Test
    public void test_latestSelectionOnly_noGrantsPresent() {
        // Enable recent selection only in the queryArgs.
        Bundle queryArgs = new Bundle();
        queryArgs.putBoolean(MediaStore.QUERY_ARG_LATEST_SELECTION_ONLY, true);

        try (Cursor c = getResultForFilesQuery(sContentResolver, queryArgs)) {
            assertThat(c).isNotNull();
            // Now only recently selected items should be returned, in this case since there are no
            // grants 0 items should be returned.
            assertWithMessage("Expected number of items(only recently selected) is 0.")
                    .that(c.getCount()).isEqualTo(0);
        }
    }


    @Test
    public void test_latestSelectionOnly_withOwnedAndGrantedItems() throws Exception {
        // Only owned items should be returned since no other file item as been granted;
        try (Cursor c = getResultForFilesQuery(sContentResolver, null)) {
            assertThat(c).isNotNull();
            assertWithMessage(
                    String.format("Expected number of owned items to be %s:", TOTAL_OWNED_ITEMS))
                    .that(c.getCount()).isEqualTo(TOTAL_OWNED_ITEMS);
        }

        List<File> otherAppFiles = OtherAppFilesRule.getAllFiles();
        assertWithMessage("Need at least 2 non owned items").that(otherAppFiles.size()).isAtLeast(
                2);

        // give access for 1 file.
        modifyReadAccess(otherAppFiles.get(0), THIS_PACKAGE_NAME, GRANT);


        // Verify owned + granted items are returned.
        try (Cursor c = getResultForFilesQuery(sContentResolver, null)) {
            assertThat(c).isNotNull();
            assertWithMessage(String.format("Expected number of items(owned + 1 granted) to be %d.",
                    TOTAL_OWNED_ITEMS + 1))
                    .that(c.getCount()).isEqualTo(TOTAL_OWNED_ITEMS + 1);
        }

        // grant one more item.
        modifyReadAccess(otherAppFiles.get(1), THIS_PACKAGE_NAME, GRANT);
        // Verify owned + granted items are returned.
        try (Cursor c = getResultForFilesQuery(sContentResolver, null)) {
            assertThat(c).isNotNull();
            assertWithMessage(String.format("Expected number of items(owned + 2 granted) to be %d.",
                    TOTAL_OWNED_ITEMS + 2))
                    .that(c.getCount()).isEqualTo(TOTAL_OWNED_ITEMS + 2);
        }

        // Now enable recent selection only in the queryArgs.
        Bundle queryArgs = new Bundle();
        queryArgs.putBoolean(MediaStore.QUERY_ARG_LATEST_SELECTION_ONLY, true);

        try (Cursor c = getResultForFilesQuery(sContentResolver, queryArgs)) {
            assertThat(c).isNotNull();
            // Now only recently selected item should be returned.
            assertWithMessage("Expected number of items(only recently selected) is 1.")
                    .that(c.getCount()).isEqualTo(1);
            final Uri expectedMediaUri = MediaStore.scanFile(sContentResolver,
                    otherAppFiles.get(1));
            c.moveToFirst();
            assertWithMessage("Expected item Uri was: " + expectedMediaUri).that(
                    c.getInt(c.getColumnIndex(
                            MediaStore.Files.FileColumns._ID))).isEqualTo(
                    ContentUris.parseId(expectedMediaUri));
        }
    }
}
