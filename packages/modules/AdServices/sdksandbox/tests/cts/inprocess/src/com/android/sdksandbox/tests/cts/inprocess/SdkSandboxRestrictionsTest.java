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

package com.android.sdksandbox.tests.cts.inprocess;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.NotificationManager;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.DeviceSupportUtils;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaDrm;
import android.media.UnsupportedSchemeException;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.UUID;

/**
 * Tests to check SDK sandbox process restrictions.
 */
@RunWith(JUnit4.class)
public class SdkSandboxRestrictionsTest {

    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Before
    public void setUp() {
        assumeTrue(
                DeviceSupportUtils.isSdkSandboxSupported(
                        InstrumentationRegistry.getInstrumentation().getContext()));
    }

    /** Tests that the SDK sandbox cannot send notifications. */
    @Test
    public void testNoNotifications() {
        NotificationManager notificationManager =
                mContext.getSystemService(NotificationManager.class);
        assertThat(notificationManager.areNotificationsEnabled()).isFalse();
    }

    /**
     * Tests that sandbox cannot access the Widevine ID.
     */
    @Test
    public void testNoWidevineAccess() throws Exception {
        UUID widevineUuid = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);

        UnsupportedSchemeException thrown = assertThrows(
                UnsupportedSchemeException.class,
                () -> new MediaDrm(widevineUuid));
        assertThat(thrown).hasMessageThat().contains("NO_INIT");
    }

    /**
     * Tests that the SDK sandbox cannot broadcast to PermissionController to request permissions.
     */
    @Test
    public void testCannotRequestPermissions() {
        Intent intent =
                new Intent(PackageManager.ACTION_REQUEST_PERMISSIONS)
                        .putExtra(
                                PackageManager.EXTRA_REQUEST_PERMISSIONS_NAMES,
                                new String[] {Manifest.permission.INSTALL_PACKAGES})
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        String packageName;
        try {
            packageName = mContext.getPackageManager().getPermissionControllerPackageName();
        } catch (Exception e) {
            packageName = "test.package";
        }
        intent.setPackage(packageName);

        SecurityException thrown =
                assertThrows(SecurityException.class, () -> mContext.startActivity(intent));
        assertThat(thrown).hasMessageThat().contains("may not be started from an SDK sandbox uid.");
    }

    /** Tests that sandbox cannot send implicit broadcast intents. */
    @Test
    public void testNoImplicitIntents() {
        final Intent sendIntent =
                new Intent()
                        .setAction(Intent.ACTION_SEND)
                        .putExtra(Intent.EXTRA_TEXT, "text")
                        .setType("text/plain")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        SecurityException thrown =
                assertThrows(SecurityException.class, () -> mContext.startActivity(sendIntent));
        assertThat(thrown).hasMessageThat().contains("may not be started from an SDK sandbox uid.");
    }

    /** Tests that the sandbox cannot send broadcasts. */
    @Test
    public void testSendBroadcastsRestrictions_withAction() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        SecurityException thrown =
                assertThrows(SecurityException.class, () -> mContext.sendBroadcast(intent));
        assertThat(thrown).hasMessageThat().contains("may not be broadcast from an SDK sandbox");
    }

    /** Tests that the sandbox cannot send broadcasts. */
    @Test
    public void testSendBroadcastRestrictions_withoutAction() {
        assumeTrue(SdkLevel.isAtLeastU());

        SecurityException thrown =
                assertThrows(SecurityException.class, () -> mContext.sendBroadcast(new Intent()));
        assertThat(thrown).hasMessageThat().contains("may not be broadcast from an SDK sandbox");
    }

    /**
     * Tests that sandbox can open URLs in a browser.
     */
    @Test
    public void testUrlViewIntents() {
        Intent intent =
                new Intent()
                        .setAction(Intent.ACTION_VIEW)
                        .setData(Uri.parse("https://www.android.com"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        mContext.startActivity(intent);
    }

    /** Tests that the sandbox cannot send explicit intents by specifying a package or component. */
    @Test
    public void testNoExplicitIntents() {
        Intent packageIntent =
                new Intent(Intent.ACTION_VIEW)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .setPackage("test.package");
        assertThrows(ActivityNotFoundException.class, () -> mContext.startActivity(packageIntent));

        Intent componentIntent = new Intent(Intent.ACTION_VIEW);
        componentIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        componentIntent.setComponent(new ComponentName("test.package", "TestClass"));
        assertThrows(
                ActivityNotFoundException.class, () -> mContext.startActivity(componentIntent));
    }

    /** Tests that sandbox cannot execute code in read-write locations. */
    @Test
    public void testSandboxCannotExecute_WriteLocation() throws Exception {
        Path scriptPath = Paths.get(mContext.getDataDir().toString(), "example.sh");
        String scriptContents = "#!/bin/bash\necho \"Should not run!\"";

        Files.write(scriptPath, scriptContents.getBytes());
        assertTrue(Files.exists(scriptPath));

        Set<PosixFilePermission> permissions =
                Set.of(
                        PosixFilePermission.OWNER_EXECUTE,
                        PosixFilePermission.GROUP_EXECUTE,
                        PosixFilePermission.OTHERS_EXECUTE);
        Files.setPosixFilePermissions(scriptPath, permissions);

        assertThat(Files.getPosixFilePermissions(scriptPath)).isEqualTo(permissions);

        assertThat(scriptPath.toFile().canExecute()).isFalse();
        assertThrows(
                String.format("Cannot run program \"%s\": error=13, Permission denied", scriptPath),
                IOException.class,
                () -> Runtime.getRuntime().exec(scriptPath.toString()));
    }

    /** Tests that Sdk Sandbox cannot access app specific external storage */
    @Test
    public void testSandboxCannotAccess_AppSpecificFiles() throws Exception {
        // Check that the sandbox does not have legacy external storage access
        assertThat(Environment.isExternalStorageLegacy()).isFalse();

        // Can't read ExternalStorageDir
        assertThat(Environment.getExternalStorageDirectory().list()).isNull();

        final String[] types = new String[] {
                Environment.DIRECTORY_MUSIC,
                Environment.DIRECTORY_PODCASTS,
                Environment.DIRECTORY_RINGTONES,
                Environment.DIRECTORY_ALARMS,
                Environment.DIRECTORY_NOTIFICATIONS,
                Environment.DIRECTORY_PICTURES,
                Environment.DIRECTORY_MOVIES,
                Environment.DIRECTORY_DOWNLOADS,
                Environment.DIRECTORY_DCIM,
                Environment.DIRECTORY_DOCUMENTS
        };
        for (String type : types) {
            File dir = mContext.getExternalFilesDir(type);
            assertThat(dir).isNull();
        }

        // Also, cannot access app-specific cache files
        assertThat(mContext.getExternalCacheDir()).isNull();
    }

    /** Tests that Sdk Sandbox cannot access app specific external storage */
    @Test
    public void testSandboxCannotAccess_MediaStoreApi() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());

        ContentResolver resolver = mContext.getContentResolver();

        // Cannot create new item on media store
        Uri audioCollection =
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        ContentValues newItem = new ContentValues();
        newItem.put(MediaStore.Audio.Media.DISPLAY_NAME, "New Audio Item");
        newItem.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg");
        assertThrows(SecurityException.class, () -> resolver.insert(audioCollection, newItem));

        // Cannot query on media store
        String[] projection = new String[] {
            MediaStore.Audio.Media._ID,
        };
        assertThrows(
                SecurityException.class,
                () -> resolver.query(audioCollection, projection, null, null, null, null));
    }

    @Test
    public void testSdkCannotAccessSdkSandboxManager() throws Exception {
        SdkSandboxManager sdkSandboxManager = mContext.getSystemService(SdkSandboxManager.class);
        assertThat(sdkSandboxManager).isNull();
    }

    /** Tests that Sdk Sandbox cannot access Storage Access Framework */
    @Test
    public void testSandboxCannotAccess_StorageAccessFramework() throws Exception {
        final String[] intentList = {
                Intent.ACTION_CREATE_DOCUMENT,
                Intent.ACTION_OPEN_DOCUMENT,
                Intent.ACTION_OPEN_DOCUMENT_TREE};

        for (int i = 0; i < intentList.length; i++) {
            Intent intent = new Intent(intentList[i]);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            SecurityException thrown =
                    assertThrows(SecurityException.class, () -> mContext.startActivity(intent));
            assertThat(thrown)
                    .hasMessageThat()
                    .contains("may not be started from an SDK sandbox uid.");
        }
    }

    /** Test that sdk sandbox can't grant read uri permission. */
    @Test
    public void testCheckUriPermission() throws Exception {
        Uri uri = Uri.parse("content://com.example.sdk.provider/abc");
        int ret =
                mContext.checkCallingOrSelfUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        assertThat(ret).isEqualTo(PackageManager.PERMISSION_DENIED);
    }
}
