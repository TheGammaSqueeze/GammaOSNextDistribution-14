/*
 * Copyright (C) 2019 The Android Open Source Project
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
package android.app.cts;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import android.app.DownloadManager;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.FileUtils;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteCallback;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.webkit.cts.CtsTestServer;

import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class DownloadManagerTestBase {
    protected static final String TAG = "DownloadManagerTest";

    /**
     * According to the CDD Section 7.6.1, the DownloadManager implementation must be able to
     * download individual files of 100 MB.
     */
    protected static final int MINIMUM_DOWNLOAD_BYTES = 100 * 1024 * 1024;

    protected static final long SHORT_TIMEOUT = 5 * DateUtils.SECOND_IN_MILLIS;
    protected static final long MEDIUM_TIMEOUT = 30 * DateUtils.SECOND_IN_MILLIS;
    protected static final long LONG_TIMEOUT = 3 * DateUtils.MINUTE_IN_MILLIS;
    private static final String ACTION_CREATE_FILE_WITH_CONTENT =
            "com.android.cts.action.CREATE_FILE_WITH_CONTENT";
    private static final String EXTRA_PATH = "path";
    private static final String EXTRA_CONTENTS = "contents";
    private static final String EXTRA_CALLBACK = "callback";
    private static final String KEY_ERROR = "error";
    private static final String STORAGE_DELEGATOR_PACKAGE = "com.android.test.storagedelegator";
    protected static final int REQUEST_CODE = 42;

    protected Context mContext;
    protected DownloadManager mDownloadManager;
    protected UiDevice mDevice;
    protected String mDocumentsUiPackageId;
    protected Instrumentation mInstrumentation;

    private WifiManager mWifiManager;
    private ConnectivityManager mCm;
    private CtsTestServer mWebServer;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mDownloadManager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
        mWifiManager = mContext.getSystemService(WifiManager.class);
        mCm = mContext.getSystemService(ConnectivityManager.class);
        mWebServer = new CtsTestServer(mContext);
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mDevice = UiDevice.getInstance(mInstrumentation);
        clearDownloads();
        checkConnection();
    }

    @After
    public void tearDown() throws Exception {
        mWebServer.shutdown();
        clearDownloads();
    }

    protected void updateUri(Uri uri, String column, String value) throws Exception {
        final String cmd = String.format("content update --uri %s --bind %s:s:%s",
                uri, column, value);
        final String res = runShellCommand(cmd).trim();
        assertTrue(res, TextUtils.isEmpty(res));
    }

    protected static byte[] hash(InputStream in) throws Exception {
        try (DigestInputStream digestIn = new DigestInputStream(in,
                MessageDigest.getInstance("SHA-1"));
             OutputStream out = new FileOutputStream(new File("/dev/null"))) {
            FileUtils.copy(digestIn, out);
            return digestIn.getMessageDigest().digest();
        } finally {
            FileUtils.closeQuietly(in);
        }
    }

    protected static Uri getMediaStoreUri(Uri downloadUri) throws Exception {
        final Context context = InstrumentationRegistry.getTargetContext();
        Cursor cursor = context.getContentResolver().query(downloadUri, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            // DownloadManager.COLUMN_MEDIASTORE_URI is not a column in the query result.
            // COLUMN_MEDIAPROVIDER_URI value maybe the same as COLUMN_MEDIASTORE_URI but NOT
            // guaranteed.
            int index = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIAPROVIDER_URI);
            return Uri.parse(cursor.getString(index));
        } else {
            throw new FileNotFoundException("Failed to find entry for " + downloadUri);
        }
    }

    protected String getMediaStoreColumnValue(Uri mediaStoreUri, String columnName)
            throws Exception {
        if (!MediaStore.Files.FileColumns.MEDIA_TYPE.equals(columnName)) {
            final int mediaType = getMediaType(mediaStoreUri);
            final String volumeName = MediaStore.getVolumeName(mediaStoreUri);
            final long id = ContentUris.parseId(mediaStoreUri);
            switch (mediaType) {
                case MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO:
                    mediaStoreUri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.getContentUri(volumeName), id);
                    break;
                case MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE:
                    mediaStoreUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.getContentUri(volumeName), id);
                    break;
                case MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO:
                    mediaStoreUri = ContentUris.withAppendedId(
                            MediaStore.Video.Media.getContentUri(volumeName), id);
                    break;
            }
        }
        // Need to pass in the user id to support multi-user scenarios.
        final int userId = getUserId();
        final String cmd = String.format("content query --uri %s --projection %s --user %s",
                mediaStoreUri, columnName, userId);
        final String res = runShellCommand(cmd).trim();
        final String str = columnName + "=";
        final int i = res.indexOf(str);
        if (i >= 0) {
            return res.substring(i + str.length());
        } else {
            throw new FileNotFoundException("Failed to find "
                    + columnName + " for "
                    + mediaStoreUri + "; found " + res);
        }
    }

    private int getMediaType(Uri mediaStoreUri) throws Exception {
        final Uri filesUri = MediaStore.Files.getContentUri(
                MediaStore.getVolumeName(mediaStoreUri),
                ContentUris.parseId(mediaStoreUri));
        return Integer.parseInt(getMediaStoreColumnValue(filesUri,
                MediaStore.Files.FileColumns.MEDIA_TYPE));
    }

    protected int getTotalBytes(InputStream in) throws Exception {
        try {
            int total = 0;
            final byte[] buf = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buf)) != -1) {
                total += bytesRead;
            }
            return total;
        } finally {
            FileUtils.closeQuietly(in);
        }
    }

    private static int getUserId() {
        return Process.myUserHandle().getIdentifier();
    }

    protected static String getRawFilePath(Uri uri) throws Exception {
        return getFileData(uri, "_data");
    }

    private void checkConnection() throws Exception {
        if (!hasConnectedNetwork(mCm)) {
            Log.d(TAG, "Enabling WiFi to ensure connectivity for this test");
            runShellCommand("svc wifi enable");
            runWithShellPermissionIdentity(mWifiManager::reconnect,
                    android.Manifest.permission.NETWORK_SETTINGS);
            final long startTime = SystemClock.elapsedRealtime();
            while (!hasConnectedNetwork(mCm)
                && (SystemClock.elapsedRealtime() - startTime) < MEDIUM_TIMEOUT) {
                Thread.sleep(500);
            }
            if (!hasConnectedNetwork(mCm)) {
                fail("Unable to connect to any network");
            }
        }
    }

    private static String getFileData(Uri uri, String projection) throws Exception {
        final Context context = InstrumentationRegistry.getTargetContext();
        final String[] projections =  new String[] { projection };
        Cursor c = context.getContentResolver().query(uri, projections, null, null, null);
        if (c != null && c.getCount() > 0) {
            c.moveToFirst();
            return c.getString(0);
        } else {
            String msg = String.format("Failed to find %s for %s", projection, uri);
            throw new FileNotFoundException(msg);
        }
    }

    protected static String readContentsFromUri(Uri uri) throws Exception {
        final Context context = InstrumentationRegistry.getTargetContext();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            return readFromInputStream(inputStream);
        }
    }

    protected static String readFromRawFile(String filePath) throws Exception {
        Log.d(TAG, "Reading form file: " + filePath);
        return readFromFile(
            ParcelFileDescriptor.open(new File(filePath), ParcelFileDescriptor.MODE_READ_ONLY));
    }

    protected static String readFromFile(ParcelFileDescriptor pfd) throws Exception {
        BufferedReader br = null;
        try (final InputStream in = new FileInputStream(pfd.getFileDescriptor())) {
            br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String str;
            StringBuilder out = new StringBuilder();
            while ((str = br.readLine()) != null) {
                out.append(str);
            }
            return out.toString();
        } finally {
            if (br != null) {
                br.close();
            }
        }
    }

    protected static File createFile(File baseDir, String fileName) {
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        return new File(baseDir, fileName);
    }

    protected static void deleteFromShell(File file) {
        runShellCommand("rm " + file);
    }

    protected static void writeToFile(File file, String contents) throws Exception {
        file.getParentFile().mkdirs();
        file.delete();

        try (final PrintWriter out = new PrintWriter(file)) {
            out.print(contents);
        }

        final String actual;
        try (FileInputStream fis = new FileInputStream(file)) {
            actual = readFromInputStream(fis);
        }
        assertEquals(contents, actual);
    }

    protected void writeToFileWithDelegator(File file, String contents) throws Exception {
        final CompletableFuture<Bundle> callbackResult = new CompletableFuture<>();

        mContext.startActivity(new Intent(ACTION_CREATE_FILE_WITH_CONTENT)
                .setPackage(STORAGE_DELEGATOR_PACKAGE)
                .putExtra(EXTRA_PATH, file.getAbsolutePath())
                .putExtra(EXTRA_CONTENTS, contents)
                .setFlags(FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_CALLBACK, new RemoteCallback(callbackResult::complete)));

        final Bundle resultBundle = callbackResult.get(SHORT_TIMEOUT, TimeUnit.MILLISECONDS);
        if (resultBundle.getString(KEY_ERROR) != null) {
            fail("Failed to create the file " + file + ", error:"
                    + resultBundle.getString(KEY_ERROR));
        }
    }

    private static String readFromInputStream(InputStream inputStream) throws Exception {
        final StringBuffer res = new StringBuffer();
        final byte[] buf = new byte[512];
        int bytesRead;
        while ((bytesRead = inputStream.read(buf)) != -1) {
            res.append(new String(buf, 0, bytesRead));
        }
        return res.toString();
    }

    protected void clearDownloads() {
        if (getTotalNumberDownloads() > 0) {
            Cursor cursor = null;
            try {
                DownloadManager.Query query = new DownloadManager.Query();
                cursor = mDownloadManager.query(query);
                int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_ID);
                long[] removeIds = new long[cursor.getCount()];
                for (int i = 0; cursor.moveToNext(); i++) {
                    removeIds[i] = cursor.getLong(columnIndex);
                }
                assertEquals(removeIds.length, mDownloadManager.remove(removeIds));
                assertEquals(0, getTotalNumberDownloads());
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
    }

    protected Uri getGoodUrl() {
        return Uri.parse(mWebServer.getTestDownloadUrl("cts-good-download", 0));
    }

    protected Uri getBadUrl() {
        return Uri.parse(mWebServer.getBaseUri() + "/nosuchurl");
    }

    protected Uri getMinimumDownloadUrl() {
        return Uri.parse(mWebServer.getTestDownloadUrl("cts-minimum-download",
                MINIMUM_DOWNLOAD_BYTES));
    }

    protected Uri getAssetUrl(String asset) {
        return Uri.parse(mWebServer.getAssetUrl(asset));
    }

    protected int getTotalNumberDownloads() {
        Cursor cursor = null;
        try {
            DownloadManager.Query query = new DownloadManager.Query();
            cursor = mDownloadManager.query(query);
            return cursor.getCount();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    protected void assertDownloadQueryableById(long downloadId) {
        Cursor cursor = null;
        try {
            DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
            cursor = mDownloadManager.query(query);
            assertEquals(1, cursor.getCount());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    protected void assertDownloadQueryableByStatus(final int status) {
        new PollingCheck() {
            @Override
            protected boolean check() {
                Cursor cursor= null;
                try {
                    DownloadManager.Query query = new DownloadManager.Query().setFilterByStatus(status);
                    cursor = mDownloadManager.query(query);
                    return 1 == cursor.getCount();
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            }
        }.run();
    }

    private static boolean hasConnectedNetwork(final ConnectivityManager cm) {
        return cm.getActiveNetwork() != null;
    }

    protected void assertSuccessfulDownload(long id, File location) throws Exception {
        Cursor cursor = null;
        try {
            final File expectedLocation = location.getCanonicalFile();
            cursor = mDownloadManager.query(new DownloadManager.Query().setFilterById(id));
            assertTrue(cursor.moveToNext());
            assertEquals(DownloadManager.STATUS_SUCCESSFUL, cursor.getInt(
                    cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)));
            assertEquals(Uri.fromFile(expectedLocation).toString(),
                    cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)));

            // Use shell to check if file is created as normal app doesn't have
            // visibility to see other packages dirs.
            String result = SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                    "file " + expectedLocation.getCanonicalPath());
            assertFalse("Cannot create file in other packages",
                    result.contains("No such file or directory"));
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    protected void assertRemoveDownload(long removeId, int expectedNumDownloads) {
        Cursor cursor = null;
        try {
            assertEquals(1, mDownloadManager.remove(removeId));
            DownloadManager.Query query = new DownloadManager.Query();
            cursor = mDownloadManager.query(query);
            assertEquals(expectedNumDownloads, cursor.getCount());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    protected boolean hasInternetConnection() {
        final PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
                || pm.hasSystemFeature(PackageManager.FEATURE_WIFI)
                || pm.hasSystemFeature(PackageManager.FEATURE_ETHERNET);
    }

    /**
     * Some non-mobile form factors ship a "stub" DocumentsUI package. Such stub packages may
     * effectively declare "no-op" components similar to those in the "real" DocUI.
     * For example, WearOS devices ship FrameworkPackageStubs that declares an Activity that should
     * handle {@link Intent#ACTION_OPEN_DOCUMENT}, that when started will simply return
     * {@link android.app.Activity#RESULT_CANCELED} right away.
     * <p>
     * This method "runs" a few {@link org.junit.Assume assumptions} to make sure we are not running
     * on one of the form factors that ship with such stub packages.
     * <p>
     * For now, these form factors are: Auto (Android Automotive OS), TVs and wearables (Wear OS).
     */
    protected void assumeDocumentsUiAvailableOnFormFactor() {
        final PackageManager pm = mContext.getPackageManager();
        assumeFalse(pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));
        assumeFalse(pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)); // TVs
        assumeFalse(pm.hasSystemFeature(PackageManager.FEATURE_WATCH));
    }

    public static class DownloadCompleteReceiver extends BroadcastReceiver {
        private HashSet<Long> mCompleteIds = new HashSet<>();

        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (mCompleteIds) {
                mCompleteIds.add(intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1));
                mCompleteIds.notifyAll();
            }
        }

        private boolean isCompleteLocked(long... ids) {
            for (long id : ids) {
                if (!mCompleteIds.contains(id)) {
                    return false;
                }
            }
            return true;
        }

        public void waitForDownloadComplete(long timeoutMillis, long... waitForIds)
                throws InterruptedException {
            if (waitForIds.length == 0) {
                throw new IllegalArgumentException("Missing IDs to wait for");
            }

            final long startTime = SystemClock.elapsedRealtime();
            do {
                synchronized (mCompleteIds) {
                    mCompleteIds.wait(timeoutMillis);
                    if (isCompleteLocked(waitForIds)) return;
                }
            } while ((SystemClock.elapsedRealtime() - startTime) < timeoutMillis);

            throw new InterruptedException("Timeout waiting for IDs " + Arrays.toString(waitForIds)
                    + "; received " + mCompleteIds.toString()
                    + ".  Make sure you have WiFi or some other connectivity for this test.");
        }
    }
}
