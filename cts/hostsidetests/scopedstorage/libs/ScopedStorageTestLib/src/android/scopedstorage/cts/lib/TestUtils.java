/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.scopedstorage.cts.lib;

import static android.provider.MediaStore.VOLUME_EXTERNAL;
import static android.scopedstorage.cts.lib.RedactionTestHelper.EXIF_METADATA_QUERY;

import static androidx.test.InstrumentationRegistry.getContext;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static junit.framework.Assert.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.app.RecoverableSecurityException;
import android.app.UiAutomation;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.storage.StorageManager;
import android.provider.MediaStore;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BuildCompat;
import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;

import com.android.cts.install.lib.Install;
import com.android.cts.install.lib.InstallUtils;
import com.android.cts.install.lib.TestApp;
import com.android.cts.install.lib.Uninstall;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.io.ByteStreams;

import org.junit.Assert;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * General helper functions for ScopedStorageTest tests.
 */
public class TestUtils {
    static final String TAG = "ScopedStorageTest";

    public static final String QUERY_TYPE = "android.scopedstorage.cts.queryType";
    public static final String INTENT_EXTRA_PATH = "android.scopedstorage.cts.path";
    public static final String INTENT_EXTRA_CONTENT = "android.scopedstorage.cts.content";
    public static final String INTENT_EXTRA_URI = "android.scopedstorage.cts.uri";
    public static final String INTENT_EXTRA_CALLING_PKG = "android.scopedstorage.cts.calling_pkg";
    public static final String INTENT_EXTRA_ARGS = "android.scopedstorage.cts.args";
    public static final String INTENT_EXCEPTION = "android.scopedstorage.cts.exception";
    public static final String FILE_EXISTS_QUERY = "android.scopedstorage.cts.file_exists";
    public static final String CREATE_FILE_QUERY = "android.scopedstorage.cts.createfile";
    public static final String CREATE_IMAGE_ENTRY_QUERY =
            "android.scopedstorage.cts.createimageentry";
    public static final String DELETE_FILE_QUERY = "android.scopedstorage.cts.deletefile";
    public static final String DELETE_MEDIA_BY_URI_QUERY =
            "android.scopedstorage.cts.deletemediabyuri";
    public static final String UPDATE_MEDIA_BY_URI_QUERY =
            "android.scopedstorage.cts.update_media_by_uri";
    public static final String QUERY_MEDIA_BY_URI_QUERY =
            "android.scopedstorage.cts.query_media_by_uri";
    public static final String DELETE_RECURSIVE_QUERY = "android.scopedstorage.cts.deleteRecursive";
    public static final String CAN_OPEN_FILE_FOR_READ_QUERY =
            "android.scopedstorage.cts.can_openfile_read";
    public static final String CAN_OPEN_FILE_FOR_WRITE_QUERY =
            "android.scopedstorage.cts.can_openfile_write";
    public static final String IS_URI_REDACTED_VIA_FILE_DESCRIPTOR_FOR_READ =
            "android.scopedstorage.cts.is_uri_redacted_via_file_descriptor_for_read";
    public static final String IS_URI_REDACTED_VIA_FILE_DESCRIPTOR_FOR_WRITE =
            "android.scopedstorage.cts.is_uri_redacted_via_file_descriptor_for_write";
    public static final String IS_URI_REDACTED_VIA_FILEPATH =
            "android.scopedstorage.cts.is_uri_redacted_via_filepath";
    public static final String QUERY_URI = "android.scopedstorage.cts.query_uri";
    public static final String QUERY_MAX_ROW_ID = "android.scopedstorage.cts.query_max_row_id";
    public static final String QUERY_MIN_ROW_ID = "android.scopedstorage.cts.query_min_row_id";
    public static final String QUERY_OWNER_PACKAGE_NAMES =
            "android.scopedstorage.cts.query_owner_package_names";
    public static final String QUERY_WITH_ARGS = "android.scopedstorage.cts.query_with_args";
    public static final String OPEN_FILE_FOR_READ_QUERY =
            "android.scopedstorage.cts.openfile_read";
    public static final String OPEN_FILE_FOR_WRITE_QUERY =
            "android.scopedstorage.cts.openfile_write";
    public static final String CAN_READ_WRITE_QUERY =
            "android.scopedstorage.cts.can_read_and_write";
    public static final String READDIR_QUERY = "android.scopedstorage.cts.readdir";
    public static final String SETATTR_QUERY = "android.scopedstorage.cts.setattr";
    public static final String CHECK_DATABASE_ROW_EXISTS_QUERY =
            "android.scopedstorage.cts.check_database_row_exists";
    public static final String RENAME_FILE_QUERY = "android.scopedstorage.cts.renamefile";

    public static final String STR_DATA1 = "Just some random text";
    public static final String STR_DATA2 = "More arbitrary stuff";

    public static final byte[] BYTES_DATA1 = STR_DATA1.getBytes();
    public static final byte[] BYTES_DATA2 = STR_DATA2.getBytes();

    public static final String RENAME_FILE_PARAMS_SEPARATOR = ";";

    // Root of external storage
    private static File sExternalStorageDirectory = Environment.getExternalStorageDirectory();
    private static String sStorageVolumeName = MediaStore.VOLUME_EXTERNAL;

    /**
     * Set this to {@code false} if the test is verifying uri grants on testApp. Force stopping the
     * app will kill the app and it will lose uri grants.
     */
    private static boolean sShouldForceStopTestApp = true;

    private static final long POLLING_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(20);
    private static final long POLLING_SLEEP_MILLIS = 100;
    private static final long APP_INSTALL_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(8);

    /**
     * Creates the top level default directories.
     *
     * <p>Those are usually created by MediaProvider, but some naughty tests might delete them
     * and not restore them afterwards, so we make sure we create them before we make any
     * assumptions about their existence.
     */
    public static void setupDefaultDirectories() {
        for (File dir : getDefaultTopLevelDirs()) {
            dir.mkdirs();
            assertWithMessage("Could not setup default dir [%s]", dir.toString())
                    .that(dir.exists())
                    .isTrue();
        }
    }

    /**
     * Grants {@link Manifest.permission#GRANT_RUNTIME_PERMISSIONS} to the given package.
     */
    public static void grantPermission(String packageName, String permission) {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity("android.permission.GRANT_RUNTIME_PERMISSIONS");
        try {
            uiAutomation.grantRuntimePermission(packageName, permission);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
        try {
            pollForPermission(packageName, permission, true);
        } catch (Exception e) {
            fail("Exception on polling for permission grant for " + packageName + " for "
                    + permission + ": " + e.getMessage());
        }
    }

    /**
     * Revokes permissions from the given package.
     */
    public static void revokePermission(String packageName, String permission) {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity("android.permission.REVOKE_RUNTIME_PERMISSIONS");
        try {
            uiAutomation.revokeRuntimePermission(packageName, permission);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
        try {
            pollForPermission(packageName, permission, false);
        } catch (Exception e) {
            fail("Exception on polling for permission revoke for " + packageName + " for "
                    + permission + ": " + e.getMessage());
        }
    }

    public static void revokeAccessMediaLocation() {
        revokeAppOpPermission(Manifest.permission.ACCESS_MEDIA_LOCATION,
                "android:access_media_location");
    }

    /**
     * Revoke the app op for the given permission. Unlike
     * {@link TestUtils#revokePermission(String, String)}, its usage does not kill the application.
     * It can be used to drop permissions previously granted to the test application, without
     * crashing the test application itself.
     */
    private static void revokeAppOpPermission(String manifestPermission, String appOp) {
        try {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity("android.permission.MANAGE_APP_OPS_MODES",
                            "android.permission.REVOKE_RUNTIME_PERMISSIONS");
            Context context =
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                            .getTargetContext();
            // Revoking the manifest permission will kill the test app.
            // Deny the permission App Op to revoke this permission.
            PackageManager packageManager = context.getPackageManager();
            String packageName = context.getPackageName();
            if (packageManager.checkPermission(manifestPermission,
                    packageName) == PackageManager.PERMISSION_GRANTED) {
                context.getPackageManager().updatePermissionFlags(
                        manifestPermission, packageName,
                        PackageManager.FLAG_PERMISSION_REVOKED_COMPAT,
                        PackageManager.FLAG_PERMISSION_REVOKED_COMPAT, context.getUser());
                context.getSystemService(AppOpsManager.class).setUidMode(
                        appOp, Process.myUid(),
                        AppOpsManager.MODE_IGNORED);
            }
        } finally {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    /**
     * Adopts shell permission identity for the given permissions.
     */
    public static void adoptShellPermissionIdentity(String... permissions) {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                permissions);
    }

    /**
     * Drops shell permission identity for all permissions.
     */
    public static void dropShellPermissionIdentity() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    /**
     * Executes a shell command.
     */
    public static String executeShellCommand(String pattern, Object...args) throws IOException {
        String command = String.format(pattern, args);
        int attempt = 0;
        while (attempt++ < 5) {
            try {
                return executeShellCommandInternal(command);
            } catch (InterruptedIOException e) {
                // Hmm, we had trouble executing the shell command; the best we
                // can do is try again a few more times
                Log.v(TAG, "Trouble executing " + command + "; trying again", e);
            }
        }
        throw new IOException("Failed to execute " + command);
    }

    private static String executeShellCommandInternal(String cmd) throws IOException {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try (FileInputStream output = new FileInputStream(
                     uiAutomation.executeShellCommand(cmd).getFileDescriptor())) {
            return new String(ByteStreams.toByteArray(output));
        }
    }

    /**
     * Makes the given {@code testApp} list the content of the given directory and returns the
     * result as an {@link ArrayList}
     */
    public static ArrayList<String> listAs(TestApp testApp, String dirPath) throws Exception {
        return getContentsFromTestApp(testApp, dirPath, READDIR_QUERY);
    }

    /**
     * Returns {@code true} iff the given {@code path} exists and is readable and
     * writable for for {@code testApp}.
     */
    public static boolean canReadAndWriteAs(TestApp testApp, String path) throws Exception {
        return getResultFromTestApp(testApp, path, CAN_READ_WRITE_QUERY);
    }

    /**
     * Makes the given {@code testApp} read the EXIF metadata from the given file and returns the
     * result as an {@link HashMap}
     */
    public static HashMap<String, String> readExifMetadataFromTestApp(
            TestApp testApp, String filePath) throws Exception {
        HashMap<String, String> res =
                getMetadataFromTestApp(testApp, filePath, EXIF_METADATA_QUERY);
        return res;
    }

    /**
     * Makes the given {@code testApp} create a file.
     *
     * <p>This method drops shell permission identity.
     */
    public static boolean createFileAs(TestApp testApp, String path) throws Exception {
        return getResultFromTestApp(testApp, path, CREATE_FILE_QUERY);
    }

    /**
     * Makes the given {@code testApp} create a file from the file descriptor passed through binder
     *
     * <p>This method drops shell permission identity.
     */
    public static boolean createFileAs(TestApp testApp, String path, IBinder content)
            throws Exception {
        return getResultFromTestApp(testApp, path, CREATE_FILE_QUERY, content);
    }

    /**
     * Makes the given {@code testApp} create a mediastore DB entry under
     * {@code MediaStore.Media.Images}.
     *
     * The {@code path} argument is treated as a relative path and a name separated
     * by an {@code '/'}.
     */
    public static boolean createImageEntryAs(TestApp testApp, String path) throws Exception {
        return createImageEntryForUriAs(testApp, path) != null;
    }

    /**
     * Makes the given {@code testApp} create a mediastore DB entry under
     * {@code MediaStore.Media.Images}.
     *
     * The {@code path} argument is treated as a relative path and a name separated
     * by an {@code '/'}.
     *
     * Returns URI of the created image.
     */
    public static Uri createImageEntryForUriAs(TestApp testApp, String path) throws Exception {
        final String actionName = CREATE_IMAGE_ENTRY_QUERY;
        final String uriString = getFromTestApp(testApp, path, actionName)
                .getString(actionName, null);
        return Uri.parse(uriString);
    }

    /**
     * Makes the given {@code testApp} query on {@code uri} to get all the ownerPackageName values.
     *
     * <p>This method drops shell permission identity.
     */
    public static String[] queryForOwnerPackageNamesAs(TestApp testApp, Uri uri) throws Exception {
        final String actionName = QUERY_OWNER_PACKAGE_NAMES;
        return getFromTestApp(testApp, uri, actionName).getStringArray(actionName);
    }

    /**
     * Makes the given {@code testApp} query on {@code uri} with the provided {@code queryArgs}.
     *
     * Returns the number of rows in the result cursor.
     *
     * <p>This method drops shell permission identity.
     */
    public static int queryWithArgsAs(TestApp testApp, Uri uri, Bundle queryArgs) throws Exception {
        final String actionName = QUERY_WITH_ARGS;
        return getFromTestApp(testApp, uri, actionName, queryArgs).getInt(actionName);
    }

    /**
     * Makes the given {@code testApp} delete media rows by the provided {@code uri}.
     *
     * Returns the number of deleted rows.
     *
     * <p>This method drops shell permission identity.
     */
    public static int deleteMediaByUriAs(TestApp testApp, Uri uri) throws Exception {
        final String actionName = DELETE_MEDIA_BY_URI_QUERY;
        return getFromTestApp(testApp, uri, actionName).getInt(actionName);
    }

    /**
     * Makes the given {@code testApp} update the media rows for the given {@code uri} by
     * updating values for the provided {@code attributes}.
     *
     * <p>This method drops shell permission identity.
     */
    public static boolean updateMediaByUriAs(TestApp testApp, Uri uri, Bundle attributes)
            throws Exception {
        final String actionName = UPDATE_MEDIA_BY_URI_QUERY;
        return getFromTestApp(testApp, uri, actionName, attributes).getBoolean(actionName);
    }

    /**
     * Makes the given {@code testApp} query media file by the given {@code uri}
     * and {@code projection}. An empty result will be returned if {@code uri}
     * indicates location of multiple files or no files at all.
     *
     * <p>This method drops shell permission identity.
     */
    public static Bundle queryMediaByUriAs(TestApp testApp, Uri uri, Set<String> projection)
            throws Exception {
        final String actionName = QUERY_MEDIA_BY_URI_QUERY;
        final Bundle bundle = new Bundle();
        if (projection != null) {
            for (String columnName : projection) {
                bundle.putString(columnName, "");
            }
        }

        return getFromTestApp(testApp, uri, actionName, bundle).getBundle(actionName);
    }

    /**
     * Makes the given {@code testApp} delete a file.
     *
     * <p>This method drops shell permission identity.
     */
    public static boolean deleteFileAs(TestApp testApp, String path) throws Exception {
        return getResultFromTestApp(testApp, path, DELETE_FILE_QUERY);
    }

    /**
     * Makes the given {@code testApp} delete a file or directory.
     * If the file is a directory, then deletes all of its children (file or directories)
     * recursively.
     *
     * <p>This method drops shell permission identity.
     */
    public static boolean deleteRecursivelyAs(TestApp testApp, String path) throws Exception {
        return getResultFromTestApp(testApp, path, DELETE_RECURSIVE_QUERY);
    }

    /**
     * Makes the given {@code testApp} delete a file. Doesn't throw in case of failure.
     */
    public static boolean deleteFileAsNoThrow(TestApp testApp, String path) {
        try {
            return deleteFileAs(testApp, path);
        } catch (Exception e) {
            Log.e(TAG,
                    "Error occurred while deleting file: " + path + " on behalf of app: " + testApp,
                    e);
            return false;
        }
    }

    /**
     * Makes the given {@code testApp} test {@code file} for existence.
     *
     * <p>This method drops shell permission identity.
     */
    public static boolean fileExistsAs(TestApp testApp, File file)
            throws Exception {
        return getResultFromTestApp(testApp, file.getPath(), FILE_EXISTS_QUERY);
    }

    /**
     * Makes the given {@code testApp} open {@code file} for read or write.
     *
     * <p>This method drops shell permission identity.
     */
    public static boolean canOpenFileAs(TestApp testApp, File file, boolean forWrite)
            throws Exception {
        String actionName = forWrite ? CAN_OPEN_FILE_FOR_WRITE_QUERY : CAN_OPEN_FILE_FOR_READ_QUERY;
        return getResultFromTestApp(testApp, file.getPath(), actionName);
    }

    /**
     * Makes the given {@code testApp} rename give {@code src} to {@code dst}.
     *
     * The method concatenates source and destination paths while sending the request to
     * {@code testApp}. Hence, {@link TestUtils#RENAME_FILE_PARAMS_SEPARATOR} shouldn't be used
     * in path names.
     *
     * <p>This method drops shell permission identity.
     */
    public static boolean renameFileAs(TestApp testApp, File src, File dst) throws Exception {
        final String paths = String.format("%s%s%s",
                src.getAbsolutePath(), RENAME_FILE_PARAMS_SEPARATOR, dst.getAbsolutePath());
        return getResultFromTestApp(testApp, paths, RENAME_FILE_QUERY);
    }

    /**
     * Makes the given {@code testApp} check if a database row exists for given {@code file}
     *
     * <p>This method drops shell permission identity.
     */
    public static boolean checkDatabaseRowExistsAs(TestApp testApp, File file) throws Exception {
        return getResultFromTestApp(testApp, file.getPath(), CHECK_DATABASE_ROW_EXISTS_QUERY);
    }

    /**
     * Makes the given {@code testApp} open file descriptor on {@code uri} and verifies that the fd
     * redacts EXIF metadata.
     *
     * <p> This method drops shell permission identity.
     */
    public static boolean isFileDescriptorRedacted(TestApp testApp, Uri uri)
            throws Exception {
        String actionName = IS_URI_REDACTED_VIA_FILE_DESCRIPTOR_FOR_READ;
        return getFromTestApp(testApp, uri, actionName).getBoolean(actionName, false);
    }

    /**
     * Makes the given {@code testApp} open file descriptor on {@code uri} and verifies that the fd
     * redacts EXIF metadata.
     *
     * <p> This method drops shell permission identity.
     */
    public static boolean canOpenRedactedUriForWrite(TestApp testApp, Uri uri)
            throws Exception {
        String actionName = IS_URI_REDACTED_VIA_FILE_DESCRIPTOR_FOR_WRITE;
        return getFromTestApp(testApp, uri, actionName).getBoolean(actionName, false);
    }


    /**
     * Makes the given {@code testApp} open file path associated with {@code uri} and verifies that
     * the path redacts EXIF metadata.
     *
     * <p>This method drops shell permission identity.
     */
    public static boolean isFileOpenRedacted(TestApp testApp, Uri uri)
            throws Exception {
        final String actionName = IS_URI_REDACTED_VIA_FILEPATH;
        return getFromTestApp(testApp, uri, actionName).getBoolean(actionName, false);
    }

    /**
     * Makes the given {@code testApp} query on {@code uri}.
     *
     * <p>This method drops shell permission identity.
     */
    public static boolean canQueryOnUri(TestApp testApp, Uri uri) throws Exception {
        final String actionName = QUERY_URI;
        return getFromTestApp(testApp, uri, actionName).getBoolean(actionName, false);
    }

    public static Uri insertFileFromExternalMedia(boolean useRelative) throws IOException {
        ContentValues values = new ContentValues();
        String filePath =
                getAndroidMediaDir().toString() + "/" + getContext().getPackageName() + "/"
                        + System.currentTimeMillis();
        if (useRelative) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    "Android/media/" + getContext().getPackageName());
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, System.currentTimeMillis());
        } else {
            values.put(MediaStore.MediaColumns.DATA, filePath);
        }

        return getContentResolver().insert(
                MediaStore.Files.getContentUri(sStorageVolumeName), values);
    }

    public static void insertFile(ContentValues values) {
        assertNotNull(getContentResolver().insert(
                MediaStore.Files.getContentUri(sStorageVolumeName), values));
    }

    public static int updateFile(Uri uri, ContentValues values) {
        return getContentResolver().update(uri, values, new Bundle());
    }

    public static void verifyInsertFromExternalPrivateDirViaRelativePath_denied() throws Exception {
        // Test that inserting files from Android/obb/.. is not allowed.
        final String androidObbDir = getExternalObbDir().toString();
        ContentValues values = new ContentValues();
        values.put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                androidObbDir.substring(androidObbDir.indexOf("Android")));
        assertThrows(IllegalArgumentException.class, () -> insertFile(values));

        // Test that inserting files from Android/data/.. is not allowed.
        final String androidDataDir = getExternalFilesDir().toString();
        values.put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                androidDataDir.substring(androidDataDir.indexOf("Android")));
        assertThrows(IllegalArgumentException.class, () -> insertFile(values));
    }

    public static void verifyInsertFromExternalMediaDirViaRelativePath_allowed() throws Exception {
        // Test that inserting files from Android/media/.. is allowed.
        final String androidMediaDir = getExternalMediaDir().toString();
        final ContentValues values = new ContentValues();
        values.put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                androidMediaDir.substring(androidMediaDir.indexOf("Android")));
        insertFile(values);
    }

    public static void verifyInsertFromExternalPrivateDirViaData_denied() throws Exception {
        ContentValues values = new ContentValues();

        // Test that inserting files from Android/obb/.. is not allowed.
        final String androidObbDir =
                getExternalObbDir().toString() + "/" + System.currentTimeMillis();
        values.put(MediaStore.MediaColumns.DATA, androidObbDir);
        assertThrows(IllegalArgumentException.class, () -> insertFile(values));

        // Test that inserting files from Android/data/.. is not allowed.
        final String androidDataDir = getExternalFilesDir().toString();
        values.put(MediaStore.MediaColumns.DATA, androidDataDir);
        assertThrows(IllegalArgumentException.class, () -> insertFile(values));
    }

    public static void verifyInsertFromExternalMediaDirViaData_allowed() throws Exception {
        // Test that inserting files from Android/media/.. is allowed.
        ContentValues values = new ContentValues();
        final String androidMediaDirFile =
                getExternalMediaDir().toString() + "/" + System.currentTimeMillis();
        values.put(MediaStore.MediaColumns.DATA, androidMediaDirFile);
        insertFile(values);
    }

    // NOTE: While updating, DATA field should be ignored for all the apps including file manager.
    public static void verifyUpdateToExternalDirsViaData_denied() throws Exception {
        Uri uri = insertFileFromExternalMedia(false);

        final String androidMediaDirFile =
                getExternalMediaDir().toString() + "/" + System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DATA, androidMediaDirFile);
        assertEquals(0, updateFile(uri, values));

        final String androidObbDir =
                getExternalObbDir().toString() + "/" + System.currentTimeMillis();
        values.put(MediaStore.MediaColumns.DATA, androidObbDir);
        assertEquals(0, updateFile(uri, values));

        final String androidDataDir = getExternalFilesDir().toString();
        values.put(MediaStore.MediaColumns.DATA, androidDataDir);
        assertEquals(0, updateFile(uri, values));
    }

    public static void verifyUpdateToExternalMediaDirViaRelativePath_allowed()
            throws IOException {
        Uri uri = insertFileFromExternalMedia(true);

        // Test that update to files from Android/media/.. is allowed.
        final String androidMediaDir = getExternalMediaDir().toString();
        ContentValues values = new ContentValues();
        values.put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                androidMediaDir.substring(androidMediaDir.indexOf("Android")));
        assertNotEquals(0, updateFile(uri, values));
    }

    public static void verifyUpdateToExternalPrivateDirsViaRelativePath_denied()
            throws Exception {
        Uri uri = insertFileFromExternalMedia(true);

        // Test that update to files from Android/obb/.. is not allowed.
        final String androidObbDir = getExternalObbDir().toString();
        ContentValues values = new ContentValues();
        values.put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                androidObbDir.substring(androidObbDir.indexOf("Android")));
        assertThrows(IllegalArgumentException.class, () -> updateFile(uri, values));

        // Test that update to files from Android/data/.. is not allowed.
        final String androidDataDir = getExternalFilesDir().toString();
        values.put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                androidDataDir.substring(androidDataDir.indexOf("Android")));
        assertThrows(IllegalArgumentException.class, () -> updateFile(uri, values));
    }

    /**
     * Makes the given {@code testApp} open a file for read or write.
     *
     * <p>This method drops shell permission identity.
     */
    public static ParcelFileDescriptor openFileAs(TestApp testApp, File file, boolean forWrite)
            throws Exception {
        String actionName = forWrite ? OPEN_FILE_FOR_WRITE_QUERY : OPEN_FILE_FOR_READ_QUERY;
        String mode = forWrite ? "rw" : "r";
        return getPfdFromTestApp(testApp, file, actionName, mode);
    }

    /**
     * Makes the given {@code testApp} setattr for given file path.
     *
     * <p>This method drops shell permission identity.
     */
    public static boolean setAttrAs(TestApp testApp, String path)
            throws Exception {
        return getResultFromTestApp(testApp, path, SETATTR_QUERY);
    }

    /**
     * Installs a {@link TestApp} without storage permissions.
     */
    public static void installApp(TestApp testApp) throws Exception {
        installApp(testApp, /* grantStoragePermission */ false);
    }

    /**
     * Installs a {@link TestApp} with storage permissions.
     */
    public static void installAppWithStoragePermissions(TestApp testApp) throws Exception {
        installApp(testApp, /* grantStoragePermission */ true);
    }

    /**
     * Installs a {@link TestApp} and may grant it storage permissions.
     */
    public static void installApp(TestApp testApp, boolean grantStoragePermission)
            throws Exception {
        Log.d(TAG, String.format("Started installation of %s app", testApp.getPackageName()));
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            final String packageName = testApp.getPackageName();
            uiAutomation.adoptShellPermissionIdentity(
                    Manifest.permission.INSTALL_PACKAGES, Manifest.permission.DELETE_PACKAGES);
            if (isAppInstalled(testApp)) {
                Uninstall.packages(packageName);
            }
            Install.single(testApp).setTimeout(APP_INSTALL_TIMEOUT_MILLIS).commit();
            assertThat(InstallUtils.getInstalledVersion(packageName)).isEqualTo(1);
            if (grantStoragePermission) {
                addressStoragePermissions(packageName, true);
            }
            Log.d(TAG, String.format("Successfully installed %s app", testApp.getPackageName()));
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Grants or revokes storage read permissions.
     */
    public static void addressStoragePermissions(String packageName, boolean grantPermission) {
        if (grantPermission) {
            grantPermission(packageName, Manifest.permission.READ_EXTERNAL_STORAGE);
            if (SdkLevel.isAtLeastT()) {
                grantPermission(packageName, Manifest.permission.READ_MEDIA_IMAGES);
                grantPermission(packageName, Manifest.permission.READ_MEDIA_AUDIO);
                grantPermission(packageName, Manifest.permission.READ_MEDIA_VIDEO);
            }
        } else {
            revokePermission(packageName, Manifest.permission.READ_EXTERNAL_STORAGE);
            if (SdkLevel.isAtLeastT()) {
                revokePermission(packageName, Manifest.permission.READ_MEDIA_IMAGES);
                revokePermission(packageName, Manifest.permission.READ_MEDIA_AUDIO);
                revokePermission(packageName, Manifest.permission.READ_MEDIA_VIDEO);
            }
        }
    }

    public static boolean isAppInstalled(TestApp testApp) {
        boolean isAppInstalled = InstallUtils.getInstalledVersion(testApp.getPackageName()) != -1;

        Log.d(TAG, String.format("Test app %s is %sinstalled", testApp.getPackageName(),
                isAppInstalled ? "" : "not "));
        return isAppInstalled;
    }

    /**
     * Uninstalls a {@link TestApp}.
     */
    public static void uninstallApp(TestApp testApp) throws Exception {
        Log.d(TAG, String.format("Started to uninstall %s test app", testApp.getPackageName()));
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            final String packageName = testApp.getPackageName();
            uiAutomation.adoptShellPermissionIdentity(Manifest.permission.DELETE_PACKAGES);

            Uninstall.packages(packageName);
            assertThat(InstallUtils.getInstalledVersion(packageName)).isEqualTo(-1);
            Log.d(TAG, String.format("Successfully uninstalled %s app", testApp.getPackageName()));
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Uninstalls a {@link TestApp}. Doesn't throw in case of failure.
     */
    public static void uninstallAppNoThrow(TestApp testApp) {
        try {
            uninstallApp(testApp);
        } catch (Exception e) {
            Log.e(TAG, "Exception occurred while uninstalling app: " + testApp, e);
        }
    }

    public static ContentResolver getContentResolver() {
        return getContext().getContentResolver();
    }

    /**
     * Inserts a file into the database using {@link MediaStore.MediaColumns#DATA}.
     */
    public static Uri insertFileUsingDataColumn(@NonNull File file) {
        final ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DATA, file.getPath());
        return getContentResolver().insert(MediaStore.Files.getContentUri(sStorageVolumeName),
                values);
    }

    /**
     * Returns the content URI for images based on the current storage volume.
     */
    public static Uri getImageContentUri() {
        return MediaStore.Images.Media.getContentUri(sStorageVolumeName);
    }

    /**
     * Returns the content URI for videos based on the current storage volume.
     */
    public static Uri getVideoContentUri() {
        return MediaStore.Video.Media.getContentUri(sStorageVolumeName);
    }

    /**
     * Renames the given file using {@link ContentResolver} and {@link MediaStore} and APIs.
     * This method uses the data column, and not all apps can use it.
     *
     * @see MediaStore.MediaColumns#DATA
     */
    public static int renameWithMediaProvider(@NonNull File oldPath, @NonNull File newPath) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DATA, newPath.getPath());
        return getContentResolver().update(MediaStore.Files.getContentUri(sStorageVolumeName),
                values, /*where*/ MediaStore.MediaColumns.DATA + "=?",
                /*whereArgs*/ new String[]{oldPath.getPath()});
    }

    /**
     * Queries {@link ContentResolver} for a file and returns the corresponding {@link Uri} for its
     * entry in the database. Returns {@code null} if file doesn't exist in the database.
     */
    @Nullable
    public static Uri getFileUri(@NonNull File file) {
        final Uri contentUri = MediaStore.Files.getContentUri(sStorageVolumeName);
        final int id = getFileRowIdFromDatabase(file);
        return id == -1 ? null : ContentUris.withAppendedId(contentUri, id);
    }

    /**
     * Queries {@link ContentResolver} for a file and returns the corresponding row ID for its
     * entry in the database. Returns {@code -1} if file is not found.
     */
    public static int getFileRowIdFromDatabase(@NonNull File file) {
        return getFileRowIdFromDatabase(getContentResolver(), file);
    }

    /**
     * Queries given {@link ContentResolver} for a file and returns the corresponding row ID for
     * its entry in the database. Returns {@code -1} if file is not found.
     */
    public static int getFileRowIdFromDatabase(ContentResolver cr, @NonNull File file) {
        int id = -1;
        try (Cursor c = queryFile(cr, file, MediaStore.MediaColumns._ID)) {
            if (c.moveToFirst()) {
                id = c.getInt(0);
            }
        }
        return id;
    }

    /**
     * Queries {@link ContentResolver} for a file and returns the corresponding owner package name
     * for its entry in the database.
     */
    @Nullable
    public static String getFileOwnerPackageFromDatabase(@NonNull File file) {
        String ownerPackage = null;
        try (Cursor c = queryFile(file, MediaStore.MediaColumns.OWNER_PACKAGE_NAME)) {
            if (c.moveToFirst()) {
                ownerPackage = c.getString(0);
            }
        }
        return ownerPackage;
    }

    /**
     * Queries {@link ContentResolver} for a file and returns the corresponding file size for its
     * entry in the database. Returns {@code -1} if file is not found.
     */
    @Nullable
    public static int getFileSizeFromDatabase(@NonNull File file) {
        int size = -1;
        try (Cursor c = queryFile(file, MediaStore.MediaColumns.SIZE)) {
            if (c.moveToFirst()) {
                size = c.getInt(0);
            }
        }
        return size;
    }

    /**
     * Queries {@link ContentResolver} for a video file and returns a {@link Cursor} with the given
     * columns.
     */
    @NonNull
    public static Cursor queryVideoFile(File file, String... projection) {
        return queryFile(getContentResolver(),
                MediaStore.Video.Media.getContentUri(sStorageVolumeName), file,
                /*includePending*/ true, projection);
    }

    /**
     * Queries {@link ContentResolver} for an image file and returns a {@link Cursor} with the given
     * columns.
     */
    @NonNull
    public static Cursor queryImageFile(File file, String... projection) {
        return queryFile(getContentResolver(),
                MediaStore.Images.Media.getContentUri(sStorageVolumeName), file,
                /*includePending*/ true, projection);
    }

    /**
     * Queries {@link ContentResolver} for an audio file and returns a {@link Cursor} with the given
     * columns.
     */
    @NonNull
    public static Cursor queryAudioFile(File file, String... projection) {
        return queryFile(getContentResolver(),
                MediaStore.Audio.Media.getContentUri(sStorageVolumeName), file,
                /*includePending*/ true, projection);
    }

    /**
     * Queries {@link ContentResolver} for a file and returns the corresponding mime type for its
     * entry in the database.
     */
    @NonNull
    public static String getFileMimeTypeFromDatabase(@NonNull File file) {
        String mimeType = "";
        try (Cursor c = queryFile(file, MediaStore.MediaColumns.MIME_TYPE)) {
            if (c.moveToFirst()) {
                mimeType = c.getString(0);
            }
        }
        return mimeType;
    }

    /**
     * Sets {@link AppOpsManager#MODE_ALLOWED} for the given {@code ops} and the given {@code uid}.
     *
     * <p>This method drops shell permission identity.
     */
    public static void allowAppOpsToUid(int uid, @NonNull String... ops) {
        setAppOpsModeForUid(uid, AppOpsManager.MODE_ALLOWED, ops);
    }

    /**
     * Sets {@link AppOpsManager#MODE_ERRORED} for the given {@code ops} and the given {@code uid}.
     *
     * <p>This method drops shell permission identity.
     */
    public static void denyAppOpsToUid(int uid, @NonNull String... ops) {
        setAppOpsModeForUid(uid, AppOpsManager.MODE_ERRORED, ops);
    }

    /**
     * Deletes the given file through {@link ContentResolver} and {@link MediaStore} APIs,
     * and asserts that the file was successfully deleted from the database.
     */
    public static void deleteWithMediaProvider(@NonNull File file) {
        Bundle extras = new Bundle();
        extras.putString(ContentResolver.QUERY_ARG_SQL_SELECTION,
                MediaStore.MediaColumns.DATA + " = ?");
        extras.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                new String[]{file.getPath()});
        extras.putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE);
        extras.putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE);
        assertThat(getContentResolver().delete(
                MediaStore.Files.getContentUri(sStorageVolumeName), extras)).isEqualTo(1);
    }

    /**
     * Deletes db rows and files corresponding to uri through {@link ContentResolver} and
     * {@link MediaStore} APIs.
     */
    public static void deleteWithMediaProviderNoThrow(Uri... uris) {
        for (Uri uri : uris) {
            if (uri == null) continue;

            try {
                getContentResolver().delete(uri, Bundle.EMPTY);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Renames the given file through {@link ContentResolver} and {@link MediaStore} APIs,
     * and asserts that the file was updated in the database.
     */
    public static void updateDisplayNameWithMediaProvider(Uri uri, String relativePath,
            String oldDisplayName, String newDisplayName) {
        String selection = MediaStore.MediaColumns.RELATIVE_PATH + " = ? AND "
                + MediaStore.MediaColumns.DISPLAY_NAME + " = ?";
        String[] selectionArgs = {relativePath + '/', oldDisplayName};
        Bundle extras = new Bundle();
        extras.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection);
        extras.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs);
        extras.putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE);
        extras.putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE);

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, newDisplayName);

        assertThat(getContentResolver().update(uri, values, extras)).isEqualTo(1);
    }

    /**
     * Opens the given file through {@link ContentResolver} and {@link MediaStore} APIs.
     */
    @NonNull
    public static ParcelFileDescriptor openWithMediaProvider(@NonNull File file, String mode)
            throws Exception {
        final Uri fileUri = getFileUri(file);
        assertThat(fileUri).isNotNull();
        Log.i(TAG, "Uri: " + fileUri + ". Data: " + file.getPath());
        ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(fileUri, mode);
        assertThat(pfd).isNotNull();
        return pfd;
    }

    /**
     * Opens the given file via file path
     */
    @NonNull
    public static ParcelFileDescriptor openWithFilePath(File file, boolean forWrite)
            throws IOException {
        return ParcelFileDescriptor.open(file,
                forWrite
                        ? ParcelFileDescriptor.MODE_READ_WRITE
                        : ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /**
     * Returns whether we can open the file.
     */
    public static boolean canOpen(File file, boolean forWrite) {
        try (ParcelFileDescriptor ignore = openWithFilePath(file, forWrite)) {
            return true;
        } catch (IOException expected) {
            return false;
        }
    }

    /**
     * Asserts the given operation throws an exception of type {@code T}.
     */
    public static <T extends Exception> void assertThrows(Class<T> clazz, Operation<Exception> r)
            throws Exception {
        assertThrows(clazz, "", r);
    }

    /**
     * Asserts the given operation throws an exception of type {@code T}.
     */
    public static <T extends Exception> void assertThrows(
            Class<T> clazz, String errMsg, Operation<Exception> r) throws Exception {
        try {
            r.run();
            fail("Expected " + clazz + " to be thrown");
        } catch (Exception e) {
            if (!clazz.isAssignableFrom(e.getClass()) || !e.getMessage().contains(errMsg)) {
                Log.e(TAG, "Expected " + clazz + " exception with error message: " + errMsg, e);
                throw e;
            }
        }
    }

    public static void setShouldForceStopTestApp(boolean value) {
        sShouldForceStopTestApp = value;
    }

    public static long readMaximumRowIdFromDatabaseAs(TestApp app, Uri uri) throws Exception {
        final String actionName = QUERY_MAX_ROW_ID;
        return getFromTestApp(app, uri, actionName).getLong(actionName, Long.MIN_VALUE);
    }

    public static long readMinimumRowIdFromDatabaseAs(TestApp app, Uri uri) throws Exception {
        final String actionName = QUERY_MIN_ROW_ID;
        return getFromTestApp(app, uri, actionName).getLong(actionName, Long.MAX_VALUE);
    }

    public static void doEscalation(RecoverableSecurityException exception) throws Exception {
        doEscalation(exception.getUserAction().getActionIntent());
    }

    public static void doEscalation(PendingIntent pi) throws Exception {
        doEscalation(pi, true /* allowAccess */, false /* shouldCheckDialogShownValue */,
                false /* isDialogShownExpectedExpected */);
    }

    public static void doEscalation(PendingIntent pi, boolean allowAccess,
            boolean shouldCheckDialogShownValue, boolean isDialogShownExpected) throws Exception {
        // Try launching the action to grant ourselves access
        final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        final Intent intent = new Intent(inst.getContext(), GetResultActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Wake up the device and dismiss the keyguard before the test starts
        final UiDevice device = UiDevice.getInstance(inst);
        device.executeShellCommand("input keyevent KEYCODE_WAKEUP");
        device.executeShellCommand("wm dismiss-keyguard");

        final GetResultActivity activity = (GetResultActivity) inst.startActivitySync(intent);
        // Wait for the UI Thread to become idle.
        inst.waitForIdleSync();
        activity.clearResult();
        device.waitForIdle();
        activity.startIntentSenderForResult(pi.getIntentSender(), 42, null, 0, 0, 0);

        device.waitForIdle();
        final long timeout = 5_000;
        if (allowAccess) {
            // Some dialogs may have granted access automatically, so we're willing
            // to keep rolling forward if we can't find our grant button
            final UiSelector grant = new UiSelector().textMatches("(?i)Allow");
            if (isWatch(inst.getContext().getPackageManager())) {
                scrollIntoView(grant);
            }
            final boolean grantExists = new UiObject(grant).waitForExists(timeout);

            if (shouldCheckDialogShownValue) {
                assertThat(grantExists).isEqualTo(isDialogShownExpected);
            }

            if (grantExists) {
                device.findObject(grant).click();
            }
            final GetResultActivity.Result res = activity.getResult();
            // Verify that we now have access
            Assert.assertEquals(Activity.RESULT_OK, res.resultCode);
        } else {
            // fine the Deny button
            final UiSelector deny = new UiSelector().textMatches("(?i)Deny");
            if (isWatch(inst.getContext().getPackageManager())) {
                scrollIntoView(deny);
            }
            final boolean denyExists = new UiObject(deny).waitForExists(timeout);

            assertThat(denyExists).isTrue();

            device.findObject(deny).click();

            final GetResultActivity.Result res = activity.getResult();
            // Verify that we don't have access
            Assert.assertEquals(Activity.RESULT_CANCELED, res.resultCode);
        }
    }

    private static boolean isWatch(PackageManager packageManager) {
        return hasFeature(packageManager, PackageManager.FEATURE_WATCH);
    }

    private static boolean hasFeature(PackageManager packageManager, String feature) {
        return packageManager.hasSystemFeature(feature);
    }

    private static void scrollIntoView(UiSelector selector) {
        UiScrollable uiScrollable = new UiScrollable(new UiSelector().scrollable(true));
        try {
            uiScrollable.scrollIntoView(selector);
        } catch (UiObjectNotFoundException e) {
            // Scrolling can fail if the UI is not scrollable
        }
    }

    /**
     * A functional interface representing an operation that takes no arguments,
     * returns no arguments and might throw an {@link Exception} of any kind.
     *
     * @param T the subclass of {@link java.lang.Exception} that this operation might throw.
     */
    @FunctionalInterface
    public interface Operation<T extends Exception> {
        /**
         * This is the method that gets called for any object that implements this interface.
         */
        void run() throws T;
    }

    /**
     * Deletes the given file. If the file is a directory, then deletes all of its children (files
     * or directories) recursively.
     */
    public static boolean deleteRecursively(@NonNull File path) {
        if (path.isDirectory()) {
            for (File child : path.listFiles()) {
                if (!deleteRecursively(child)) {
                    return false;
                }
            }
        }
        return path.delete();
    }

    /**
     * Asserts can rename file.
     */
    public static void assertCanRenameFile(File oldFile, File newFile) {
        assertCanRenameFile(oldFile, newFile, /* checkDB */ true);
    }

    /**
     * Asserts can rename file and optionally checks if the database is updated after rename.
     */
    public static void assertCanRenameFile(File oldFile, File newFile, boolean checkDatabase) {
        assertThat(oldFile.renameTo(newFile)).isTrue();
        assertThat(oldFile.exists()).isFalse();
        assertThat(newFile.exists()).isTrue();
        if (checkDatabase) {
            assertThat(getFileRowIdFromDatabase(oldFile)).isEqualTo(-1);
            assertThat(getFileRowIdFromDatabase(newFile)).isNotEqualTo(-1);
        }
    }

    /**
     * Asserts cannot rename file.
     */
    public static void assertCantRenameFile(File oldFile, File newFile) {
        final int rowId = getFileRowIdFromDatabase(oldFile);
        assertThat(oldFile.renameTo(newFile)).isFalse();
        assertThat(oldFile.exists()).isTrue();
        assertThat(getFileRowIdFromDatabase(oldFile)).isEqualTo(rowId);
    }

    /**
     * Assert that app cannot insert files in other app's private directories
     *
     * @param fileName                    name of the file
     * @param throwsExceptionForDataValue Apps like System Gallery for which Data column is not
     *                                    respected, will not throw an Exception as the Data value
     *                                    is ignored.
     * @param otherApp                    Other test app in whose external private directory we will
     *                                    attempt to insert
     * @param callingPackageName          Calling package name
     */
    public static void assertCantInsertToOtherPrivateAppDirectories(String fileName,
            boolean throwsExceptionForDataValue, TestApp otherApp, String callingPackageName)
            throws Exception {
        // Create directory in which the device test will try to insert file to
        final File otherAppExternalDataDir = new File(getExternalFilesDir().getPath().replace(
                callingPackageName, otherApp.getPackageName()));
        final File file = new File(otherAppExternalDataDir, fileName);
        String absolutePath = file.getAbsolutePath();

        final ContentValues valuesWithRelativePath = new ContentValues();
        final String absoluteDirectoryPath = otherAppExternalDataDir.getAbsolutePath();
        valuesWithRelativePath.put(MediaStore.MediaColumns.RELATIVE_PATH,
                absoluteDirectoryPath.substring(absoluteDirectoryPath.indexOf("Android")));
        valuesWithRelativePath.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);

        try {
            assertThat(createFileAs(otherApp, file.getPath())).isTrue();
            assertCantInsertDataValue(throwsExceptionForDataValue, absolutePath);
            assertCantInsertDataValue(throwsExceptionForDataValue,
                    "/sdcard/" + absolutePath.substring(absolutePath.indexOf("Android")));
            assertCantInsertDataValue(throwsExceptionForDataValue,
                    "/storage/emulated/0/Pictures/../"
                            + absolutePath.substring(absolutePath.indexOf("Android")));

            try {
                getContentResolver().insert(MediaStore.Files.getContentUri(VOLUME_EXTERNAL),
                        valuesWithRelativePath);
                fail("File insert expected to fail: " + file);
            } catch (IllegalArgumentException expected) {
            }
        } finally {
            deleteFileAsNoThrow(otherApp, file.getPath());
        }
    }

    private static void assertCantInsertDataValue(boolean throwsExceptionForDataValue,
            String path) throws Exception {
        if (throwsExceptionForDataValue) {
            assertThrowsErrorOnInsertToOtherAppPrivateDirectories(path);
        } else {
            insertDataWithValue(path);
            try (Cursor c = getContentResolver().query(
                    MediaStore.Files.getContentUri(VOLUME_EXTERNAL),
                    new String[]{MediaStore.MediaColumns.DATA},
                    MediaStore.MediaColumns.DATA + "=?", new String[]{path}, null)) {
                assertThat(c.getCount()).isEqualTo(0);
            }
        }
    }

    private static void assertThrowsErrorOnInsertToOtherAppPrivateDirectories(String path)
            throws Exception {
        assertThrows(IllegalArgumentException.class, () -> insertDataWithValue(path));
    }

    private static void insertDataWithValue(String path) {
        final ContentValues valuesWithData = new ContentValues();
        valuesWithData.put(MediaStore.MediaColumns.DATA, path);

        getContentResolver().insert(MediaStore.Files.getContentUri(VOLUME_EXTERNAL),
                valuesWithData);
    }

    /**
     * Assert that app cannot update files in other app's private directories
     *
     * @param fileName                    name of the file
     * @param throwsExceptionForDataValue Apps like non-legacy System Gallery/MES for which
     *                                    Data column is not respected, will not throw an Exception
     *                                    as the Data value is ignored.
     * @param otherApp                    Other test app in whose external private directory we will
     *                                    attempt to insert
     * @param callingPackageName          Calling package name
     */
    public static void assertCantUpdateToOtherPrivateAppDirectories(String fileName,
            boolean throwsExceptionForDataValue, TestApp otherApp, String callingPackageName)
            throws Exception {
        // Create priv-app file and add to the database that we will try to update
        final File otherAppExternalDataDir = new File(getExternalFilesDir().getPath().replace(
                callingPackageName, otherApp.getPackageName()));
        final File file = new File(otherAppExternalDataDir, fileName);
        try {
            assertThat(createFileAs(otherApp, file.getPath())).isTrue();
            MediaStore.scanFile(getContentResolver(), file);

            final ContentValues valuesWithData = new ContentValues();
            valuesWithData.put(MediaStore.MediaColumns.DATA, file.getAbsolutePath());
            try {
                int res = getContentResolver().update(
                        MediaStore.Files.getContentUri(VOLUME_EXTERNAL),
                        valuesWithData, Bundle.EMPTY);

                if (throwsExceptionForDataValue) {
                    fail("File update expected to fail: " + file);
                } else {
                    assertThat(res).isEqualTo(0);
                }
            } catch (IllegalArgumentException expected) {
            }

            final ContentValues valuesWithRelativePath = new ContentValues();
            final String path = file.getAbsolutePath();
            valuesWithRelativePath.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    path.substring(path.indexOf("Android")));
            valuesWithRelativePath.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            try {
                getContentResolver().update(MediaStore.Files.getContentUri(VOLUME_EXTERNAL),
                        valuesWithRelativePath, Bundle.EMPTY);
                fail("File update expected to fail: " + file);
            } catch (IllegalArgumentException expected) {
            }
        } finally {
            deleteFileAsNoThrow(otherApp, file.getPath());
        }
    }

    private static void copyContentsAndDir(final Path source, final Path target)
            throws IOException {
        Files.walkFileTree(source, new java.nio.file.SimpleFileVisitor<Path>() {
            private java.nio.file.FileVisitResult copyFileOrEmptyDir(final Path source,
                    final Path sourceRoot, final Path targetRoot) throws IOException {
                final Path target = targetRoot.resolve(sourceRoot.relativize(source));
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.LinkOption.NOFOLLOW_LINKS);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(Path sourceDir,
                    java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                return copyFileOrEmptyDir(sourceDir, source, target);
            }
            @Override
            public java.nio.file.FileVisitResult visitFile(Path sourceFile,
                    java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                return copyFileOrEmptyDir(sourceFile, source, target);
            }
        });
    }

    private static boolean renameDirectoryWithOptionalFallbackToCopy(
            File oldDirectory, File newDirectory, boolean allowCopyFallback) {
        if (oldDirectory.renameTo(newDirectory)) {
            return true;
        }

        if (!allowCopyFallback) {
            return false;
        }

        if (!oldDirectory.isDirectory()) {
            return false;
        }
        if (newDirectory.exists()
                && (!newDirectory.isDirectory() || newDirectory.listFiles().length > 0)) {
            return false;
        }

        final Path oldPath = oldDirectory.toPath();
        final Path newPath = newDirectory.toPath();
        Log.v(TAG, "Recovering failed rename from " + oldPath + " to " + newPath);
        try {
            copyContentsAndDir(oldPath, newPath);
        } catch (IOException e) {
            Log.v(TAG, "Failed to recover rename: ", e);
            return false;
        }
        deleteRecursively(oldDirectory);
        return true;
    }

    /**
     * Asserts can rename directory.
     */
    public static void assertCanRenameDirectory(File oldDirectory, File newDirectory,
            @Nullable File[] oldFilesList, @Nullable File[] newFilesList) {
        assertCanRenameDirectory(oldDirectory, newDirectory, oldFilesList, newFilesList,
                false /* allowCopyFallback */);
    }

    /**
     * Asserts can rename directory. When {@code allowCopyFallback} is true and the simple rename
     * fails, falls back to recursively copying {@code oldDirectory} into {@code newDirectory}.
     * Note that the file attributes will not be copied on the fallback.
     */
    public static void assertCanRenameDirectory(File oldDirectory, File newDirectory,
            @Nullable File[] oldFilesList, @Nullable File[] newFilesList,
            boolean allowCopyFallback) {
        assertThat(renameDirectoryWithOptionalFallbackToCopy(
                    oldDirectory, newDirectory, allowCopyFallback)).isTrue();
        assertThat(oldDirectory.exists()).isFalse();
        assertThat(newDirectory.exists()).isTrue();
        for (File file : oldFilesList != null ? oldFilesList : new File[0]) {
            assertThat(file.exists()).isFalse();
            assertThat(getFileRowIdFromDatabase(file)).isEqualTo(-1);
        }
        for (File file : newFilesList != null ? newFilesList : new File[0]) {
            assertThat(file.exists()).isTrue();
            assertThat(getFileRowIdFromDatabase(file)).isNotEqualTo(-1);
        }
    }

    /**
     * Asserts cannot rename directory.
     */
    public static void assertCantRenameDirectory(
            File oldDirectory, File newDirectory, @Nullable File[] oldFilesList) {
        assertThat(oldDirectory.renameTo(newDirectory)).isFalse();
        assertThat(oldDirectory.exists()).isTrue();
        for (File file : oldFilesList != null ? oldFilesList : new File[0]) {
            assertThat(file.exists()).isTrue();
            assertThat(getFileRowIdFromDatabase(file)).isNotEqualTo(-1);
        }
    }

    public static void assertMountMode(String packageName, int uid, int expectedMountMode) {
        adoptShellPermissionIdentity("android.permission.WRITE_MEDIA_STORAGE");
        try {
            final StorageManager storageManager = getContext().getSystemService(
                    StorageManager.class);
            final int actualMountMode = storageManager.getExternalStorageMountMode(uid,
                    packageName);
            assertWithMessage("mount mode (%s=%s, %s=%s) for package %s and uid %s",
                    expectedMountMode, mountModeToString(expectedMountMode),
                    actualMountMode, mountModeToString(actualMountMode),
                    packageName, uid).that(actualMountMode).isEqualTo(expectedMountMode);
        } finally {
            dropShellPermissionIdentity();
        }
    }

    public static String mountModeToString(int mountMode) {
        switch (mountMode) {
            case 0:
                return "EXTERNAL_NONE";
            case 1:
                return "DEFAULT";
            case 2:
                return "INSTALLER";
            case 3:
                return "PASS_THROUGH";
            case 4:
                return "ANDROID_WRITABLE";
            default:
                return "INVALID(" + mountMode + ")";
        }
    }

    public static void assertCanAccessPrivateAppAndroidDataDir(boolean canAccess,
            TestApp testApp, String callingPackage, String fileName) throws Exception {
        File[] dataDirs = getContext().getExternalFilesDirs(null);
        canReadWriteFilesInDirs(dataDirs, canAccess, testApp, callingPackage, fileName);
    }

    public static void assertCanAccessPrivateAppAndroidObbDir(boolean canAccess,
            TestApp testApp, String callingPackage, String fileName) throws Exception {
        File[] obbDirs = getContext().getObbDirs();
        canReadWriteFilesInDirs(obbDirs, canAccess, testApp, callingPackage, fileName);
    }

    private static void canReadWriteFilesInDirs(File[] dirs, boolean canAccess, TestApp testApp,
            String callingPackage, String fileName) throws Exception {
        for (File dir : dirs) {
            final File otherAppExternalDataDir = new File(dir.getPath().replace(
                    callingPackage, testApp.getPackageName()));
            final File file = new File(otherAppExternalDataDir, fileName);
            try {
                assertThat(file.exists()).isFalse();

                assertThat(createFileAs(testApp, file.getPath())).isTrue();
                if (canAccess) {
                    assertThat(file.canRead()).isTrue();
                    assertThat(file.canWrite()).isTrue();
                } else {
                    assertThat(file.canRead()).isFalse();
                    assertThat(file.canWrite()).isFalse();
                }
            } finally {
                deleteFileAsNoThrow(testApp, file.getAbsolutePath());
            }
        }
    }

    /**
     * Polls for external storage to be mounted.
     */
    public static void pollForExternalStorageState() throws Exception {
        pollForCondition(
                () -> Environment.getExternalStorageState(getExternalStorageDir())
                        .equals(Environment.MEDIA_MOUNTED),
                "Timed out while waiting for ExternalStorageState to be MEDIA_MOUNTED");
    }

    /**
     * Polls until we're granted or denied a given permission.
     */
    public static void pollForPermission(String perm, boolean granted) throws Exception {
        pollForCondition(() -> granted == checkPermissionAndAppOp(perm),
                "Timed out while waiting for permission " + perm + " to be "
                        + (granted ? "granted" : "revoked"));
    }

    /**
     * Polls until {@code app} is granted or denied the given permission.
     */
    public static void pollForPermission(TestApp app, String perm, boolean granted)
            throws Exception {
        pollForPermission(app.getPackageName(), perm, granted);
    }

    /**
     * Polls until {@code packageName} is granted or denied the given permission.
     */
    public static void pollForPermission(String packageName, String perm, boolean granted)
            throws Exception {
        pollForCondition(
                () -> granted == checkPermission(packageName, perm),
                "Timed out while waiting for permission " + perm + " to be "
                        + (granted ? "granted" : "revoked"));
    }

    /**
     * Returns true iff {@code packageName} is granted a given permission.
     */
    public static boolean checkPermission(String packageName, String perm) {
        try {
            int uid = getContext().getPackageManager().getPackageUid(packageName, 0);

            Optional<ActivityManager.RunningAppProcessInfo> process = getAppProcessInfo(
                    packageName);
            int pid = process.isPresent() ? process.get().pid : -1;
            return checkPermissionAndAppOp(perm, packageName, pid, uid);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Returns true iff {@code app} is granted a given permission.
     */
    public static boolean checkPermission(TestApp app, String perm) {
        return checkPermission(app.getPackageName(), perm);
    }

    /**
     * Asserts the entire content of the file equals exactly {@code expectedContent}.
     */
    public static void assertFileContent(File file, byte[] expectedContent) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            assertInputStreamContent(fis, expectedContent);
        }
    }

    /**
     * Asserts the entire content of the file equals exactly {@code expectedContent}.
     * <p>Sets {@code fd} to beginning of file first.
     */
    public static void assertFileContent(FileDescriptor fd, byte[] expectedContent)
            throws IOException, ErrnoException {
        Os.lseek(fd, 0, OsConstants.SEEK_SET);
        try (FileInputStream fis = new FileInputStream(fd)) {
            assertInputStreamContent(fis, expectedContent);
        }
    }

    /**
     * Asserts that {@code dir} is a directory and that it doesn't contain any of
     * {@code unexpectedContent}
     */
    public static void assertDirectoryDoesNotContain(@NonNull File dir, File... unexpectedContent) {
        assertThat(dir.isDirectory()).isTrue();
        assertThat(Arrays.asList(dir.listFiles())).containsNoneIn(unexpectedContent);
    }

    /**
     * Asserts that {@code dir} is a directory and that it contains all of {@code expectedContent}
     */
    public static void assertDirectoryContains(@NonNull File dir, File... expectedContent) {
        assertThat(dir.isDirectory()).isTrue();
        assertThat(Arrays.asList(dir.listFiles())).containsAtLeastElementsIn(expectedContent);
    }

    public static File getExternalStorageDir() {
        return sExternalStorageDirectory;
    }

    public static void setExternalStorageVolume(@NonNull String volName) {
        sStorageVolumeName = volName.toLowerCase(Locale.ROOT);
        sExternalStorageDirectory = new File("/storage/" + volName);
    }

    /**
     * Resets the root directory of external storage to the default.
     *
     * @see Environment#getExternalStorageDirectory()
     */
    public static void resetDefaultExternalStorageVolume() {
        sStorageVolumeName = MediaStore.VOLUME_EXTERNAL;
        sExternalStorageDirectory = Environment.getExternalStorageDirectory();
    }

    /**
     * Asserts the default volume used in helper methods is the primary volume.
     */
    public static void assertDefaultVolumeIsPrimary() {
        assertVolumeType(true /* isPrimary */);
    }

    /**
     * Asserts the default volume used in helper methods is a public volume.
     */
    public static void assertDefaultVolumeIsPublic() {
        assertVolumeType(false /* isPrimary */);
    }

    /**
     * Creates and returns the Android data sub-directory belonging to the calling package.
     */
    public static File getExternalFilesDir() {
        final String packageName = getContext().getPackageName();
        final File res = new File(getAndroidDataDir(), packageName + "/files");
        if (!res.equals(getContext().getExternalFilesDir(null))) {
            res.mkdirs();
        }
        return res;
    }

    /**
     * Creates and returns the Android obb sub-directory belonging to the calling package.
     */
    public static File getExternalObbDir() {
        final String packageName = getContext().getPackageName();
        final File res = new File(getAndroidObbDir(), packageName);
        if (!res.equals(getContext().getObbDirs()[0])) {
            res.mkdirs();
        }
        return res;
    }

    /**
     * Creates and returns the Android media sub-directory belonging to the calling package.
     */
    public static File getExternalMediaDir() {
        final String packageName = getContext().getPackageName();
        final File res = new File(getAndroidMediaDir(), packageName);
        if (!res.equals(getContext().getExternalMediaDirs()[0])) {
            res.mkdirs();
        }
        return res;
    }

    public static File getAlarmsDir() {
        return new File(getExternalStorageDir(),
                Environment.DIRECTORY_ALARMS);
    }

    public static File getAndroidDir() {
        return new File(getExternalStorageDir(),
                "Android");
    }

    public static File getAudiobooksDir() {
        return new File(getExternalStorageDir(),
                Environment.DIRECTORY_AUDIOBOOKS);
    }

    public static File getDcimDir() {
        return new File(getExternalStorageDir(), Environment.DIRECTORY_DCIM);
    }

    public static File getDocumentsDir() {
        return new File(getExternalStorageDir(),
                Environment.DIRECTORY_DOCUMENTS);
    }

    public static File getDownloadDir() {
        return new File(getExternalStorageDir(),
                Environment.DIRECTORY_DOWNLOADS);
    }

    public static File getMusicDir() {
        return new File(getExternalStorageDir(),
                Environment.DIRECTORY_MUSIC);
    }

    public static File getMoviesDir() {
        return new File(getExternalStorageDir(),
                Environment.DIRECTORY_MOVIES);
    }

    public static File getNotificationsDir() {
        return new File(getExternalStorageDir(),
                Environment.DIRECTORY_NOTIFICATIONS);
    }

    public static File getPicturesDir() {
        return new File(getExternalStorageDir(),
                Environment.DIRECTORY_PICTURES);
    }

    public static File getPodcastsDir() {
        return new File(getExternalStorageDir(),
                Environment.DIRECTORY_PODCASTS);
    }

    public static File getRecordingsDir() {
        return new File(getExternalStorageDir(),
                Environment.DIRECTORY_RECORDINGS);
    }

    public static File getRingtonesDir() {
        return new File(getExternalStorageDir(),
                Environment.DIRECTORY_RINGTONES);
    }

    public static File getAndroidDataDir() {
        return new File(getAndroidDir(), "data");
    }

    public static File getAndroidObbDir() {
        return new File(getAndroidDir(), "obb");
    }

    public static File getAndroidMediaDir() {
        return new File(getAndroidDir(), "media");
    }

    public static File[] getDefaultTopLevelDirs() {
        if (BuildCompat.isAtLeastS()) {
            return new File[] {
                getAlarmsDir(),
                getAudiobooksDir(),
                getDcimDir(),
                getDocumentsDir(),
                getDownloadDir(),
                getMusicDir(),
                getMoviesDir(),
                getNotificationsDir(),
                getPicturesDir(),
                getPodcastsDir(),
                getRecordingsDir(),
                getRingtonesDir()
            };
        }
        return new File[] {
            getAlarmsDir(),
            getAudiobooksDir(),
            getDcimDir(),
            getDocumentsDir(),
            getDownloadDir(),
            getMusicDir(),
            getMoviesDir(),
            getNotificationsDir(),
            getPicturesDir(),
            getPodcastsDir(),
            getRingtonesDir()
        };
    }

    private static void assertInputStreamContent(InputStream in, byte[] expectedContent)
            throws IOException {
        assertThat(ByteStreams.toByteArray(in)).isEqualTo(expectedContent);
    }

    /**
     * Checks if the given {@code permission} is granted and corresponding AppOp is MODE_ALLOWED.
     */
    private static boolean checkPermissionAndAppOp(String permission) {
        final int pid = Os.getpid();
        final int uid = Os.getuid();
        final String packageName = getContext().getPackageName();
        return checkPermissionAndAppOp(permission, packageName, pid, uid);
    }

    /**
     * Checks if the given {@code permission} is granted and corresponding AppOp is MODE_ALLOWED.
     */
    private static boolean checkPermissionAndAppOp(String permission, String packageName, int pid,
            int uid) {
        final Context context = getContext();
        if (context.checkPermission(permission, pid, uid) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        final String op = AppOpsManager.permissionToOp(permission);
        // No AppOp associated with the given permission, skip AppOp check.
        if (op == null) {
            return true;
        }

        final AppOpsManager appOps = context.getSystemService(AppOpsManager.class);
        try {
            appOps.checkPackage(uid, packageName);
        } catch (SecurityException e) {
            return false;
        }

        return appOps.unsafeCheckOpNoThrow(op, uid, packageName) == AppOpsManager.MODE_ALLOWED;
    }

    /**
     * <p>This method drops shell permission identity.
     */
    public static void forceStopApp(String packageName) throws Exception {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity(Manifest.permission.FORCE_STOP_PACKAGES);

            getContext().getSystemService(ActivityManager.class).forceStopPackage(packageName);
            pollForCondition(() -> {
                return !isProcessRunning(packageName);
            }, "Timed out while waiting for " + packageName + " to be stopped");
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    private static void launchTestApp(TestApp testApp, String actionName,
            BroadcastReceiver broadcastReceiver, CountDownLatch latch, Intent intent)
            throws InterruptedException, TimeoutException {

        // Register broadcast receiver
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(actionName);
        intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
        getContext().registerReceiver(broadcastReceiver, intentFilter,
                Context.RECEIVER_EXPORTED_UNAUDITED);

        // Launch the test app.
        intent.setPackage(testApp.getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(QUERY_TYPE, actionName);
        intent.putExtra(INTENT_EXTRA_CALLING_PKG, getContext().getPackageName());
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        getContext().startActivity(intent);
        if (!latch.await(POLLING_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            final String errorMessage = "Timed out while waiting to receive " + actionName
                    + " intent from " + testApp.getPackageName();
            throw new TimeoutException(errorMessage);
        }
        getContext().unregisterReceiver(broadcastReceiver);
    }

    /**
     * Sends intent to {@code testApp} for actions on {@code dirPath}
     *
     * <p>This method drops shell permission identity.
     */
    private static void sendIntentToTestApp(TestApp testApp, String dirPath, String actionName,
            IBinder fileDescriptorBinder, BroadcastReceiver broadcastReceiver, CountDownLatch latch)
            throws Exception {
        if (sShouldForceStopTestApp) {
            final String packageName = testApp.getPackageName();
            forceStopApp(packageName);
        }

        // Launch the test app.
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.putExtra(INTENT_EXTRA_PATH, dirPath);
        if (fileDescriptorBinder != null) {
            final Bundle bundle = new Bundle();
            bundle.putBinder(INTENT_EXTRA_CONTENT, fileDescriptorBinder);
            intent.putExtra(INTENT_EXTRA_CONTENT, bundle);
        }
        launchTestApp(testApp, actionName, broadcastReceiver, latch, intent);
    }

    /**
     * Sends intent to {@code testApp} for actions on {@code uri}
     *
     * <p>This method drops shell permission identity.
     */
    private static void sendIntentToTestApp(TestApp testApp, Uri uri, String actionName,
            BroadcastReceiver broadcastReceiver, CountDownLatch latch,
            Bundle args) throws Exception {
        if (sShouldForceStopTestApp) {
            final String packageName = testApp.getPackageName();
            forceStopApp(packageName);
        }

        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.putExtra(INTENT_EXTRA_URI, uri);
        intent.putExtra(INTENT_EXTRA_ARGS, args);
        launchTestApp(testApp, actionName, broadcastReceiver, latch, intent);
    }

    /**
     * Gets images/video metadata from a test app.
     *
     * <p>This method drops shell permission identity.
     */
    private static HashMap<String, String> getMetadataFromTestApp(
            TestApp testApp, String dirPath, String actionName) throws Exception {
        Bundle bundle = getFromTestApp(testApp, dirPath, actionName);
        return (HashMap<String, String>) bundle.get(actionName);
    }

    /**
     * <p>This method drops shell permission identity.
     */
    private static ArrayList<String> getContentsFromTestApp(
            TestApp testApp, String dirPath, String actionName) throws Exception {
        Bundle bundle = getFromTestApp(testApp, dirPath, actionName);
        return bundle.getStringArrayList(actionName);
    }

    /**
     * <p>This method drops shell permission identity.
     */
    private static boolean getResultFromTestApp(TestApp testApp, String dirPath, String actionName)
            throws Exception {
        Bundle bundle = getFromTestApp(testApp, dirPath, actionName);
        return bundle.getBoolean(actionName, false);
    }

    /**
     * <p>This method drops shell permission identity.
     */
    private static boolean getResultFromTestApp(TestApp testApp, String dirPath, String actionName,
            IBinder fileDescriptorBinder)
            throws Exception {
        Bundle bundle = getFromTestApp(testApp, dirPath, actionName, fileDescriptorBinder);
        return bundle.getBoolean(actionName, false);
    }


    private static ParcelFileDescriptor getPfdFromTestApp(TestApp testApp, File dirPath,
            String actionName, String mode) throws Exception {
        Bundle bundle = getFromTestApp(testApp, dirPath.getPath(), actionName);
        return getContentResolver().openFileDescriptor(bundle.getParcelable(actionName), mode);
    }

    /**
     * <p>This method drops shell permission identity.
     */
    private static Bundle getFromTestApp(TestApp testApp, String dirPath, String actionName)
            throws Exception {
        return getFromTestApp(testApp, dirPath, actionName, null);
    }

    /**
     * <p>This method drops shell permission identity.
     */
    private static Bundle getFromTestApp(TestApp testApp, String dirPath, String actionName,
            @Nullable IBinder fileDescriptorBinder)
            throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final Bundle[] bundle = new Bundle[1];
        final Exception[] exception = new Exception[1];
        exception[0] = null;
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.hasExtra(INTENT_EXCEPTION)) {
                    exception[0] = (Exception) (intent.getSerializableExtra(INTENT_EXCEPTION));
                } else {
                    bundle[0] = intent.getExtras();
                }
                latch.countDown();
            }
        };

        sendIntentToTestApp(testApp, dirPath, actionName, fileDescriptorBinder, broadcastReceiver,
                latch);
        if (exception[0] != null) {
            throw exception[0];
        }
        return bundle[0];
    }

    /**
     * <p>This method drops shell permission identity.
     */
    private static Bundle getFromTestApp(TestApp testApp, Uri uri, String actionName)
            throws Exception {
        return getFromTestApp(testApp, uri, actionName, null);
    }

    /**
     * <p>This method drops shell permission identity.
     */
    private static Bundle getFromTestApp(TestApp testApp, Uri uri, String actionName, Bundle args)
            throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final Bundle[] bundle = new Bundle[1];
        final Exception[] exception = new Exception[1];
        exception[0] = null;
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.hasExtra(INTENT_EXCEPTION)) {
                    exception[0] = (Exception) (intent.getSerializableExtra(INTENT_EXCEPTION));
                } else {
                    bundle[0] = intent.getExtras();
                }
                latch.countDown();
            }
        };

        sendIntentToTestApp(testApp, uri, actionName, broadcastReceiver, latch, args);
        if (exception[0] != null) {
            throw exception[0];
        }
        return bundle[0];
    }

    /**
     * Sets {@code mode} for the given {@code ops} and the given {@code uid}.
     *
     * <p>This method drops shell permission identity.
     */
    public static void setAppOpsModeForUid(int uid, int mode, @NonNull String... ops) {
        adoptShellPermissionIdentity(null);
        try {
            for (String op : ops) {
                getContext().getSystemService(AppOpsManager.class).setUidMode(op, uid, mode);
            }
        } finally {
            dropShellPermissionIdentity();
        }
    }

    /**
     * Queries {@link ContentResolver} for a file IS_PENDING=0 and returns a {@link Cursor} with the
     * given columns.
     */
    @NonNull
    public static Cursor queryFileExcludingPending(@NonNull File file, String... projection) {
        return queryFile(getContentResolver(), MediaStore.Files.getContentUri(sStorageVolumeName),
                file, /*includePending*/ false, projection);
    }

    @NonNull
    public static Cursor queryFile(ContentResolver cr, @NonNull File file, String... projection) {
        return queryFile(cr, MediaStore.Files.getContentUri(sStorageVolumeName),
                file, /*includePending*/ true, projection);
    }

    @NonNull
    public static Cursor queryFile(@NonNull File file, String... projection) {
        return queryFile(getContentResolver(), MediaStore.Files.getContentUri(sStorageVolumeName),
                file, /*includePending*/ true, projection);
    }

    @NonNull
    private static Cursor queryFile(ContentResolver cr, @NonNull Uri uri, @NonNull File file,
            boolean includePending, String... projection) {
        Bundle queryArgs = new Bundle();
        queryArgs.putString(ContentResolver.QUERY_ARG_SQL_SELECTION,
                MediaStore.MediaColumns.DATA + " = ?");
        queryArgs.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                new String[]{file.getAbsolutePath()});
        queryArgs.putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE);

        if (includePending) {
            queryArgs.putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE);
        } else {
            queryArgs.putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_EXCLUDE);
        }

        final Cursor c = cr.query(uri, projection, queryArgs, null);
        assertThat(c).isNotNull();
        return c;
    }

    private static boolean isObbDirUnmounted() {
        List<String> mounts = new ArrayList<>();
        try {
            for (String line : executeShellCommand("cat /proc/mounts").split("\n")) {
                String[] split = line.split(" ");
                // Only check obb dirs with tmpfs, as if it's mounted for app data
                // isolation, it will be tmpfs only.
                if (split[0].equals("tmpfs") && split[1].startsWith("/storage/")
                        && split[1].endsWith("/obb")) {
                    return false;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to execute shell command", e);
        }
        return true;
    }

    private static boolean isVolumeMounted(String type) {
        try {
            final String volume = executeShellCommand("sm list-volumes " + type).trim();
            return volume != null && volume.contains(" mounted");
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isPublicVolumeMounted() {
        return isVolumeMounted("public");
    }

    private static boolean isEmulatedVolumeMounted() {
        return isVolumeMounted("emulated");
    }

    private static boolean isFuseReady() {
        for (String volumeName : MediaStore.getExternalVolumeNames(getContext())) {
            final Uri uri = MediaStore.Files.getContentUri(volumeName);
            try (Cursor c = getContentResolver().query(uri, null, null, null)) {
                assertThat(c).isNotNull();
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return true;
    }

    /**
     * Prepare or create a public volume for testing
     */
    public static void preparePublicVolume() throws Exception {
        if (getCurrentPublicVolumeName() == null) {
            createNewPublicVolume();
            return;
        }

        if (!Boolean.parseBoolean(executeShellCommand("sm has-adoptable").trim())) {
            unmountAppDirs();
            // ensure the volume is visible
            executeShellCommand("sm set-force-adoptable on");
            Thread.sleep(2000);
            pollForCondition(TestUtils::isPublicVolumeMounted,
                    "Timed out while waiting for public volume");
            pollForCondition(TestUtils::isEmulatedVolumeMounted,
                    "Timed out while waiting for emulated volume");
            pollForCondition(TestUtils::isFuseReady,
                    "Timed out while waiting for fuse");
        }
    }

    public static boolean isAdoptableStorageSupported() throws Exception {
        return hasAdoptableStorageFeature() || hasAdoptableStorageFstab();
    }

    private static boolean hasAdoptableStorageFstab() throws Exception {
        return Boolean.parseBoolean(executeShellCommand("sm has-adoptable").trim());
    }

    private static boolean hasAdoptableStorageFeature() throws Exception {
        return getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_ADOPTABLE_STORAGE);
    }

    /**
     * Unmount app's obb and data dirs.
     */
    public static void unmountAppDirs() throws Exception {
        if (TestUtils.isObbDirUnmounted()) {
            return;
        }
        executeShellCommand("sm unmount-app-data-dirs " + getContext().getPackageName() + " "
                + android.os.Process.myPid() + " " + android.os.UserHandle.myUserId());
        pollForCondition(TestUtils::isObbDirUnmounted,
                "Timed out while waiting for unmounting obb dir");
    }

    /**
     * Creates a new virtual public volume and returns the volume's name.
     */
    public static void createNewPublicVolume() throws Exception {
        // Unmount data and obb dirs for test app first so test app won't be killed during
        // volume unmount.
        unmountAppDirs();
        executeShellCommand("sm set-force-adoptable on");
        executeShellCommand("sm set-virtual-disk true");
        Thread.sleep(2000);
        pollForCondition(TestUtils::partitionDisk, "Timed out while waiting for disk partitioning");
    }

    private static boolean partitionDisk() {
        try {
            final String listDisks = executeShellCommand("sm list-disks").trim();
            if (TextUtils.isEmpty(listDisks)) {
                return false;
            }
            executeShellCommand("sm partition " + listDisks + " public");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets the name of the public volume, waiting for a bit for it to be available.
     */
    public static String getPublicVolumeName() throws Exception {
        final String[] volName = new String[1];
        pollForCondition(() -> {
            volName[0] = getCurrentPublicVolumeName();
            return volName[0] != null;
        }, "Timed out while waiting for public volume to be ready");

        return volName[0];
    }

    /**
     * @return the currently mounted public volume, if any.
     */
    public static String getCurrentPublicVolumeName() {
        final String[] allVolumeDetails;
        try {
            allVolumeDetails = executeShellCommand("sm list-volumes")
                    .trim().split("\n");
        } catch (Exception e) {
            Log.e(TAG, "Failed to execute shell command", e);
            return null;
        }
        for (String volDetails : allVolumeDetails) {
            if (volDetails.startsWith("public")) {
                final String[] publicVolumeDetails = volDetails.trim().split(" ");
                String res = publicVolumeDetails[publicVolumeDetails.length - 1];
                if ("null".equals(res)) {
                    continue;
                }
                return res;
            }
        }
        return null;
    }

    /**
     * Returns the content URI of the volume on which the test is running.
     */
    public static Uri getTestVolumeFileUri() {
        return MediaStore.Files.getContentUri(sStorageVolumeName);
    }

    public static void pollForCondition(Supplier<Boolean> condition, String errorMessage)
            throws Exception {
        for (int i = 0; i < POLLING_TIMEOUT_MILLIS / POLLING_SLEEP_MILLIS; i++) {
            if (condition.get()) {
                return;
            }
            Thread.sleep(POLLING_SLEEP_MILLIS);
        }
        throw new TimeoutException(errorMessage);
    }

    /**
     * Polls for all files access to be allowed.
     */
    public static void pollForManageExternalStorageAllowed() throws Exception {
        pollForCondition(
                () -> Environment.isExternalStorageManager(),
                "Timed out while waiting for MANAGE_EXTERNAL_STORAGE");
    }

    private static void assertVolumeType(boolean isPrimary) {
        String[] parts = getExternalFilesDir().getAbsolutePath().split("/");
        assertThat(parts.length).isAtLeast(3);
        assertThat(parts[1]).isEqualTo("storage");
        if (isPrimary) {
            assertThat(parts[2]).isEqualTo("emulated");
        } else {
            assertThat(parts[2]).isNotEqualTo("emulated");
        }
    }

    private static boolean isProcessRunning(String packageName) {
        return getAppProcessInfo(packageName).isPresent();
    }

    private static Optional<ActivityManager.RunningAppProcessInfo> getAppProcessInfo(
            String packageName) {
        return getContext().getSystemService(ActivityManager.class)
                .getRunningAppProcesses()
                .stream()
                .filter(p -> packageName.equals(p.processName))
                .findFirst();
    }

    public static void trashFileAndAssert(Uri uri) {
        final ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.IS_TRASHED, 1);
        assertWithMessage("Result of ContentResolver#update for " + uri + " with values to trash "
                + "file " + values)
                .that(getContentResolver().update(uri, values, Bundle.EMPTY)).isEqualTo(1);
    }

    public static void untrashFileAndAssert(Uri uri) {
        final ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.IS_TRASHED, 0);
        assertWithMessage("Result of ContentResolver#update for " + uri + " with values to untrash "
                + "file " + values)
                .that(getContentResolver().update(uri, values, Bundle.EMPTY)).isEqualTo(1);
    }

    public static void waitForMountedAndIdleState(ContentResolver resolver) throws Exception {
        // We purposefully perform these operations twice in this specific
        // order, since clearing the data on a package can asynchronously
        // perform a vold reset, which can make us think storage is ready and
        // mounted when it's moments away from being torn down.
        pollForExternalStorageMountedState();
        MediaStore.waitForIdle(resolver);
        pollForExternalStorageMountedState();
        MediaStore.waitForIdle(resolver);
    }

    private static void pollForExternalStorageMountedState() throws Exception {
        final File target = Environment.getExternalStorageDirectory();
        pollForCondition(() -> isExternalStorageDirectoryMounted(target),
                "Timed out while waiting for ExternalStorageState to be MEDIA_MOUNTED");
    }

    private static boolean isExternalStorageDirectoryMounted(File target) {
        boolean isMounted = Environment.MEDIA_MOUNTED.equals(
                Environment.getExternalStorageState(target));
        if (isMounted) {
            try {
                return Os.statvfs(target.getAbsolutePath()).f_blocks > 0;
            } catch (Exception e) {
                // Waiting for external storage to be mounted
            }
        }
        return false;
    }
}
