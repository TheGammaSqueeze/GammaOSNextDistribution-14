/*
 * Copyright (C) 2011 The Android Open Source Project
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

import static android.Manifest.permission.WRITE_MEDIA_STORAGE;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.DownloadManager;
import android.app.DownloadManager.Query;
import android.app.DownloadManager.Request;
import android.app.stubs.GetResultActivity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.FileUtils;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.Pair;

import androidx.test.filters.FlakyTest;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;

import com.android.compatibility.common.util.CddTest;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@RunWith(AndroidJUnit4.class)
public class DownloadManagerTest extends DownloadManagerTestBase {
    private static final String DOWNLOAD_STORAGE_PROVIDER_AUTHORITY =
            "com.android.providers.downloads.documents";
    @FlakyTest
    @Test
    public void testDownloadManager() throws Exception {
        final DownloadCompleteReceiver receiver = new DownloadCompleteReceiver();
        try {
            IntentFilter intentFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            mContext.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED);

            long goodId = mDownloadManager.enqueue(new Request(getGoodUrl()));
            long badId = mDownloadManager.enqueue(new Request(getBadUrl()));

            int allDownloads = getTotalNumberDownloads();
            assertEquals(2, allDownloads);

            assertDownloadQueryableById(goodId);
            assertDownloadQueryableById(badId);

            receiver.waitForDownloadComplete(SHORT_TIMEOUT, goodId, badId);

            assertDownloadQueryableByStatus(DownloadManager.STATUS_SUCCESSFUL);
            assertDownloadQueryableByStatus(DownloadManager.STATUS_FAILED);

            assertRemoveDownload(goodId, allDownloads - 1);
            assertRemoveDownload(badId, allDownloads - 2);
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    @FlakyTest
    @Test
    public void testDownloadManagerSupportsHttp() throws Exception {
        final DownloadCompleteReceiver receiver = new DownloadCompleteReceiver();
        try {
            IntentFilter intentFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            mContext.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED);

            long id = mDownloadManager.enqueue(new Request(getGoodUrl()));

            assertEquals(1, getTotalNumberDownloads());

            assertDownloadQueryableById(id);

            receiver.waitForDownloadComplete(SHORT_TIMEOUT, id);

            assertDownloadQueryableByStatus(DownloadManager.STATUS_SUCCESSFUL);

            assertRemoveDownload(id, 0);
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    @FlakyTest
    @Test
    public void testDownloadManagerSupportsHttpWithExternalWebServer() throws Exception {
        if (!hasInternetConnection()) {
            Log.i(TAG, "testDownloadManagerSupportsHttpWithExternalWebServer() ignored on device without Internet");
            return;
        }

        // As a result of testDownloadManagerSupportsHttpsWithExternalWebServer relying on an
        // external resource https://www.example.com this test uses http://www.example.com to help
        // disambiguate errors from testDownloadManagerSupportsHttpsWithExternalWebServer.

        final DownloadCompleteReceiver receiver = new DownloadCompleteReceiver();
        try {
            IntentFilter intentFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            mContext.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED);

            long id = mDownloadManager.enqueue(new Request(Uri.parse("http://www.example.com")));

            assertEquals(1, getTotalNumberDownloads());

            assertDownloadQueryableById(id);

            receiver.waitForDownloadComplete(LONG_TIMEOUT, id);

            assertDownloadQueryableByStatus(DownloadManager.STATUS_SUCCESSFUL);

            assertRemoveDownload(id, 0);
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    @Test
    public void testDownloadManagerSupportsHttpsWithExternalWebServer() throws Exception {
        if (!hasInternetConnection()) {
            Log.i(TAG, "testDownloadManagerSupportsHttpsWithExternalWebServer() ignored on device without Internet");
            return;
        }

        // For HTTPS, DownloadManager trusts only SSL server certs issued by CAs trusted by the
        // system. Unfortunately, this means that it cannot trust the mock web server's SSL cert.
        // Until this is resolved (e.g., by making it possible to specify additional CA certs to
        // trust for a particular download), this test relies on https://www.example.com being
        // operational and reachable from the Android under test.

        final DownloadCompleteReceiver receiver = new DownloadCompleteReceiver();
        try {
            IntentFilter intentFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            mContext.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED);

            long id = mDownloadManager.enqueue(new Request(Uri.parse("https://www.example.com")));

            assertEquals(1, getTotalNumberDownloads());

            assertDownloadQueryableById(id);

            receiver.waitForDownloadComplete(LONG_TIMEOUT, id);

            assertDownloadQueryableByStatus(DownloadManager.STATUS_SUCCESSFUL);

            assertRemoveDownload(id, 0);
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    @CddTest(requirement="7.6.1")
    @Test
    public void testMinimumDownload() throws Exception {
        final DownloadCompleteReceiver receiver = new DownloadCompleteReceiver();
        try {
            IntentFilter intentFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            mContext.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED);

            long id = mDownloadManager.enqueue(new Request(getMinimumDownloadUrl()));
            receiver.waitForDownloadComplete(LONG_TIMEOUT, id);

            ParcelFileDescriptor fileDescriptor = mDownloadManager.openDownloadedFile(id);
            assertEquals(MINIMUM_DOWNLOAD_BYTES, fileDescriptor.getStatSize());

            Cursor cursor = null;
            try {
                cursor = mDownloadManager.query(new Query().setFilterById(id));
                assertTrue(cursor.moveToNext());
                assertEquals(DownloadManager.STATUS_SUCCESSFUL, cursor.getInt(
                        cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)));
                assertEquals(MINIMUM_DOWNLOAD_BYTES, cursor.getInt(
                        cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)));
                assertFalse(cursor.moveToNext());
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }

            assertRemoveDownload(id, 0);
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    /**
     * Set download locations and verify that file is downloaded to correct location.
     *
     * Checks three different methods of setting location: directly via setDestinationUri, and
     * indirectly through setDestinationInExternalFilesDir and setDestinationinExternalPublicDir.
     */
    @FlakyTest
    @Test
    public void testDownloadManagerDestination() throws Exception {
        File uriLocation = new File(mContext.getExternalFilesDir(null), "uriFile.bin");
        deleteFromShell(uriLocation);

        File extFileLocation = new File(mContext.getExternalFilesDir(null), "extFile.bin");
        deleteFromShell(extFileLocation);

        File publicLocation = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "publicFile.bin");
        deleteFromShell(publicLocation);

        final DownloadCompleteReceiver receiver = new DownloadCompleteReceiver();
        try {
            IntentFilter intentFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            mContext.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED);

            Request requestUri = new Request(getGoodUrl());
            requestUri.setDestinationUri(Uri.fromFile(uriLocation));
            long uriId = mDownloadManager.enqueue(requestUri);

            Request requestExtFile = new Request(getGoodUrl());
            requestExtFile.setDestinationInExternalFilesDir(mContext, null, "extFile.bin");
            long extFileId = mDownloadManager.enqueue(requestExtFile);

            Request requestPublic = new Request(getGoodUrl());
            requestPublic.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                    "publicFile.bin");
            long publicId = mDownloadManager.enqueue(requestPublic);

            int allDownloads = getTotalNumberDownloads();
            assertEquals(3, allDownloads);

            receiver.waitForDownloadComplete(SHORT_TIMEOUT, uriId, extFileId, publicId);

            assertSuccessfulDownload(uriId, uriLocation);
            assertSuccessfulDownload(extFileId, extFileLocation);
            assertSuccessfulDownload(publicId, publicLocation);

            assertRemoveDownload(uriId, allDownloads - 1);
            assertRemoveDownload(extFileId, allDownloads - 2);
            assertRemoveDownload(publicId, allDownloads - 3);
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    /**
     * Set the download location and verify that the extension of the file name is left unchanged.
     */
    @Test
    public void testDownloadManagerDestinationExtension() throws Exception {
        String noExt = "noiseandchirps";
        File noExtLocation = new File(mContext.getExternalFilesDir(null), noExt);
        deleteFromShell(noExtLocation);

        String wrongExt = "noiseandchirps.wrong";
        File wrongExtLocation = new File(mContext.getExternalFilesDir(null), wrongExt);
        deleteFromShell(wrongExtLocation);

        final DownloadCompleteReceiver receiver = new DownloadCompleteReceiver();
        try {
            IntentFilter intentFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            mContext.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED);

            Request requestNoExt = new Request(getAssetUrl(noExt));
            requestNoExt.setDestinationUri(Uri.fromFile(noExtLocation));
            long noExtId = mDownloadManager.enqueue(requestNoExt);

            Request requestWrongExt = new Request(getAssetUrl(wrongExt));
            requestWrongExt.setDestinationUri(Uri.fromFile(wrongExtLocation));
            long wrongExtId = mDownloadManager.enqueue(requestWrongExt);

            int allDownloads = getTotalNumberDownloads();
            assertEquals(2, allDownloads);

            receiver.waitForDownloadComplete(SHORT_TIMEOUT, noExtId, wrongExtId);

            assertSuccessfulDownload(noExtId, noExtLocation);
            assertSuccessfulDownload(wrongExtId, wrongExtLocation);

            assertRemoveDownload(noExtId, allDownloads - 1);
            assertRemoveDownload(wrongExtId, allDownloads - 2);
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    @Test
    public void testSetDestinationUri_privateAppDir() throws Exception {
        // Make sure the private app directory exists
        runShellCommand("mkdir -p /sdcard/Android/data/com.android.shell -m 2770");
        final File path = new File("/sdcard/Android/data/com.android.shell/"
                + TAG + System.currentTimeMillis());

        final DownloadCompleteReceiver receiver = new DownloadCompleteReceiver();
        try {
            IntentFilter intentFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            mContext.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED);

            DownloadManager.Request requestPublic = new DownloadManager.Request(getGoodUrl());
            requestPublic.setDestinationUri(Uri.fromFile(path));
            mDownloadManager.enqueue(requestPublic);
            Assert.fail("Cannot download files into other app's private directories");
        } catch (SecurityException expected) {
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    @Test
    public void testSetDestinationUri_invalidRequests() throws Exception {
        final File documentsFile = new File(
                Environment.getExternalStoragePublicDirectory("TestDir"),
                "uriFile.bin");
        deleteFromShell(documentsFile);

        final Request badRequest = new Request(getGoodUrl());
        badRequest.setDestinationUri(Uri.fromFile(documentsFile));
        try {
            mDownloadManager.enqueue(badRequest);
            fail(documentsFile + " is not valid for setDestinationUri()");
        } catch (Exception e) {
            // Expected
        }

        final Uri badUri = Uri.parse("file:///sdcard/Android/data/"
                + mContext.getPackageName() + "/../uriFile.bin");
        final Request badRequest2 = new Request(getGoodUrl());
        badRequest2.setDestinationUri(badUri);
        try {
            mDownloadManager.enqueue(badRequest2);
            fail(badUri + " is not valid for setDestinationUri()");
        } catch (Exception e) {
            // Expected
        }

        final File sdcardPath = new File("/sdcard/uriFile.bin");
        final Request badRequest3 = new Request(getGoodUrl());
        badRequest3.setDestinationUri(Uri.fromFile(sdcardPath));
        try {
            mDownloadManager.enqueue(badRequest2);
            fail(sdcardPath + " is not valid for setDestinationUri()");
        } catch (Exception e) {
            // Expected
        }
    }

    @Test
    public void testSetDestinationUri() throws Exception {
        final File[] destinationFiles = {
                new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), System.nanoTime() + "_uriFile.bin"),
                new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), System.nanoTime() + "_file.jpg"),
        };

        for (File downloadsFile : destinationFiles) {
            final DownloadCompleteReceiver receiver = new DownloadCompleteReceiver();
            try {
                IntentFilter intentFilter = new IntentFilter(
                        DownloadManager.ACTION_DOWNLOAD_COMPLETE);
                mContext.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED);

                DownloadManager.Request request = new DownloadManager.Request(getGoodUrl());
                request.setDestinationUri(Uri.fromFile(downloadsFile));
                long id = mDownloadManager.enqueue(request);

                int allDownloads = getTotalNumberDownloads();
                assertEquals(1, allDownloads);

                receiver.waitForDownloadComplete(SHORT_TIMEOUT, id);
                assertSuccessfulDownload(id, downloadsFile);

                assertRemoveDownload(id, 0);
            } finally {
                mContext.unregisterReceiver(receiver);
            }
        }
    }

    @Test
    public void testSetDestinationInExternalPublicDownloadDir() throws Exception {
        final String[] destinations = {
                Environment.DIRECTORY_DOWNLOADS,
                Environment.DIRECTORY_DCIM,
        };
        final String[] subPaths = {
                "testing/" + System.nanoTime() + "_publicFile.bin",
                System.nanoTime() + "_file.jpg",
        };

        for (int i = 0; i < destinations.length; ++i) {
            final String destination = destinations[i];
            final String subPath = subPaths[i];

            final DownloadCompleteReceiver receiver = new DownloadCompleteReceiver();
            try {
                IntentFilter intentFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
                mContext.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED);

                DownloadManager.Request requestPublic = new DownloadManager.Request(getGoodUrl());
                requestPublic.setDestinationInExternalPublicDir(destination, subPath);
                long id = mDownloadManager.enqueue(requestPublic);

                int allDownloads = getTotalNumberDownloads();
                assertEquals(1, allDownloads);

                receiver.waitForDownloadComplete(SHORT_TIMEOUT, id);
                assertSuccessfulDownload(id, new File(
                        Environment.getExternalStoragePublicDirectory(destination), subPath));

                assertRemoveDownload(id, 0);
            } finally {
                mContext.unregisterReceiver(receiver);
            }
        }
    }

    @Test
    public void testSetDestinationInExternalPublicDir_invalidRequests() {
        final Request badRequest2 = new Request(getGoodUrl());
        try {
            badRequest2.setDestinationInExternalPublicDir("TestDir", "uriFile.bin");
            mDownloadManager.enqueue(badRequest2);
            fail("/sdcard/TestDir/uriFile.bin"
                    + " is not valid for setDestinationInExternalPublicDir()");
        } catch (Exception e) {
            // Expected
        }
    }

    private String cannonicalizeProcessName(ApplicationInfo ai) {
        return cannonicalizeProcessName(ai.processName, ai);
    }

    private String cannonicalizeProcessName(String process, ApplicationInfo ai) {
        if (process == null) {
            return null;
        }
        // Handle private scoped process names.
        if (process.startsWith(":")) {
            return ai.packageName + process;
        }
        return process;
    }

    private int getExternalVolumeMediaStoreFilesCount() {
        Uri rootUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL);
        String[] projection = {MediaStore.MediaColumns._ID};
        int count = 0;
        try (Cursor cursor = mContext.getContentResolver().query(rootUri, projection, null, null)) {
            count = cursor.getCount();
        }
        return count;
    }

    @FlakyTest
    @Test
    public void testProviderAcceptsCleartext() throws Exception {
        // Check that all the applications that share an android:process with the DownloadProvider
        // accept cleartext traffic. Otherwise process loading races can lead to inconsistent flags.
        final PackageManager pm = mContext.getPackageManager();
        ProviderInfo downloadInfo = pm.resolveContentProvider("downloads", 0);
        assertNotNull(downloadInfo);
        String downloadProcess
                = cannonicalizeProcessName(downloadInfo.processName, downloadInfo.applicationInfo);

        for (PackageInfo pi : mContext.getPackageManager().getInstalledPackages(0)) {
            if (downloadProcess.equals(cannonicalizeProcessName(pi.applicationInfo))) {
                assertTrue("package: " + pi.applicationInfo.packageName
                        + " must set android:usesCleartextTraffic=true"
                        ,(pi.applicationInfo.flags & ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC)
                        != 0);
            }
        }
    }

    /**
     * Test that a download marked as not visible in Downloads ui can be successfully downloaded.
     */
    @Test
    public void testDownloadNotVisibleInUi() throws Exception {
        File uriLocation = new File(mContext.getExternalFilesDir(null), "uriFile.bin");
        deleteFromShell(uriLocation);

        final DownloadCompleteReceiver receiver = new DownloadCompleteReceiver();
        try {
            IntentFilter intentFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            mContext.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED);

            final Request request = new Request(getGoodUrl());
            request.setDestinationUri(Uri.fromFile(uriLocation))
                    .setVisibleInDownloadsUi(false);
            long uriId = mDownloadManager.enqueue(request);

            int allDownloads = getTotalNumberDownloads();
            assertEquals(1, allDownloads);

            receiver.waitForDownloadComplete(SHORT_TIMEOUT, uriId);

            assertSuccessfulDownload(uriId, uriLocation);

            assertRemoveDownload(uriId, 0);
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    //TODO(b/130797842): Remove FlakyTest annotation after that bug is fixed.
    @FlakyTest
    @Test
    public void testAddCompletedDownload() throws Exception {
        final String fileContents = "RED;GREEN;BLUE";
        final File file = createFile(mContext.getExternalFilesDir(null), "colors.txt");

        writeToFile(file, fileContents);

        final long id = mDownloadManager.addCompletedDownload(file.getName(), "Test desc", true,
                "text/plain", file.getPath(), fileContents.getBytes().length, true);
        final String actualContents = readFromFile(mDownloadManager.openDownloadedFile(id));
        assertEquals(fileContents, actualContents);

        final Uri downloadUri = mDownloadManager.getUriForDownloadedFile(id);
        mContext.grantUriPermission("com.android.shell", downloadUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION);
        final String rawFilePath = getRawFilePath(downloadUri);
        final String rawFileContents = readFromRawFile(rawFilePath);
        assertEquals(fileContents, rawFileContents);
        assertRemoveDownload(id, 0);
    }

    @Test
    public void testAddCompletedDownload_downloadDir() throws Exception {
        final String path = createFile(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), "file1.txt").getPath();

        final String fileContents = "Test content:" + path + "_" + System.nanoTime();

        final File file = new File(path);
        writeToFile(new File(path), fileContents);

        final long id = mDownloadManager.addCompletedDownload(file.getName(), "Test desc", true,
                "text/plain", path, fileContents.getBytes().length, true);
        final String actualContents = readFromFile(mDownloadManager.openDownloadedFile(id));
        assertEquals(fileContents, actualContents);

        final Uri downloadUri = mDownloadManager.getUriForDownloadedFile(id);
        mContext.grantUriPermission("com.android.shell", downloadUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION);
        final String rawFilePath = getRawFilePath(downloadUri);
        final String rawFileContents = readFromRawFile(rawFilePath);
        assertEquals(fileContents, rawFileContents);
        assertRemoveDownload(id, 0);
    }

    @Test
    public void testAddCompletedDownload_invalidPaths() throws Exception {
        final String fileContents = "RED;GREEN;BLUE";

        // Try adding internal path
        File file = createFile(mContext.getFilesDir(), "colors.txt");
        writeToFile(file, fileContents);
        try {
            mDownloadManager.addCompletedDownload("Test title", "Test desc", true,
                    "text/plain", file.getPath(), fileContents.getBytes().length, true);
            fail("addCompletedDownload should have failed for adding internal path");
        } catch (Exception e) {
            // expected
        }

        // Try adding path in top-level documents dir
        file = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "colors.txt");
        writeToFileWithDelegator(file, fileContents);
        try {
            mDownloadManager.addCompletedDownload("Test title", "Test desc", true,
                    "text/plain", file.getPath(), fileContents.getBytes().length, true);
            fail("addCompletedDownload should have failed for top-level download dir");
        } catch (SecurityException e) {
            // expected
        }

        // Try adding top-level sdcard path
        final String path = "/sdcard/test-download.txt";
        writeToFileWithDelegator(new File(path), fileContents);
        try {
            mDownloadManager.addCompletedDownload("Test title", "Test desc", true,
                    "text/plain", path, fileContents.getBytes().length, true);
            fail("addCompletedDownload should have failed for top-level sdcard path");
        } catch (SecurityException e) {
            // expected
        }


        final String path2 = "/sdcard/Android/data/" + mContext.getPackageName()
                + "/../uriFile.bin";
        try {
            mDownloadManager.addCompletedDownload("Test title", "Test desc", true,
                    "text/plain", path2, fileContents.getBytes().length, true);
            fail(path2 + " is not valid for addCompleteDownload()");
        } catch (Exception e) {
            // Expected
        }

        // Try adding non-existent path
        try {
            mDownloadManager.addCompletedDownload("Test title", "Test desc", true, "text/plain",
                    new File(mContext.getExternalFilesDir(null), "test_file.mp4").getPath(),
                    fileContents.getBytes().length, true);
            fail("addCompletedDownload should have failed for adding non-existent path");
        } catch (Exception e) {
            // expected
        }

        // Try adding random string
        try {
            mDownloadManager.addCompletedDownload("Test title", "Test desc", true,
                    "text/plain", "RANDOM", fileContents.getBytes().length, true);
            fail("addCompletedDownload should have failed for adding random string");
        } catch (Exception e) {
            // expected
        }
    }

    @Test
    public void testDownload_mediaScanned() throws Exception {
        final String[] destinations = {
                Environment.DIRECTORY_MUSIC,
                Environment.DIRECTORY_DOWNLOADS,
        };
        final String[] subPaths = {
                "testmp3.mp3",
                "testvideo.3gp",
        };
        final Pair<String, String>[] expectedMediaAttributes = new Pair[] {
                Pair.create(MediaStore.Audio.AudioColumns.IS_MUSIC, "1"),
                Pair.create(MediaStore.Video.VideoColumns.DURATION, "11047"),
        };

        for (int i = 0; i < destinations.length; ++i) {
            final String destination = destinations[i];
            final String subPath = subPaths[i];

            final DownloadCompleteReceiver receiver = new DownloadCompleteReceiver();
            try {
                IntentFilter intentFilter = new IntentFilter(
                        DownloadManager.ACTION_DOWNLOAD_COMPLETE);
                mContext.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED);

                DownloadManager.Request requestPublic = new DownloadManager.Request(
                        getAssetUrl(subPath));
                requestPublic.setDestinationInExternalPublicDir(destination, subPath);
                long id = mDownloadManager.enqueue(requestPublic);

                int allDownloads = getTotalNumberDownloads();
                assertEquals(1, allDownloads);

                int countBeforeDownload = getExternalVolumeMediaStoreFilesCount();
                receiver.waitForDownloadComplete(SHORT_TIMEOUT, id);
                assertSuccessfulDownload(id, new File(
                        Environment.getExternalStoragePublicDirectory(destination), subPath));

                int countAfterDownload = getExternalVolumeMediaStoreFilesCount();
                // Asserts that only one row entry is added for 1 download
                assertEquals((countBeforeDownload + 1), countAfterDownload);
                final Uri downloadUri = mDownloadManager.getUriForDownloadedFile(id);
                mContext.grantUriPermission("com.android.shell", downloadUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                final Uri mediaStoreUri = getMediaStoreUri(downloadUri);
                assertEquals(expectedMediaAttributes[i].second,
                        getMediaStoreColumnValue(mediaStoreUri, expectedMediaAttributes[i].first));
                final int expectedSize = getTotalBytes(
                        mContext.getContentResolver().openInputStream(downloadUri));
                assertEquals(expectedSize, Integer.parseInt(getMediaStoreColumnValue(
                        mediaStoreUri, MediaStore.MediaColumns.SIZE)));

                assertRemoveDownload(id, 0);
            } finally {
                mContext.unregisterReceiver(receiver);
            }
        }
    }

    @Test
    public void testDownload_onMediaStoreDownloadsDeleted() throws Exception {
        assumeDocumentsUiAvailableOnFormFactor();

        // prepare file
        File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "cts" + System.nanoTime() + ".mp3");
        try {
            stageFile("testmp3.mp3", file);
            Uri mediaStoreUri = MediaStore.scanFile(mContext.getContentResolver(), file);

            // setup for activity
            GetResultActivity activity = setUpForActivity();

            // call activity as we want uri permission grant on DownloadStorageProvider Uri
            final Intent intent = new Intent();
            intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            Uri rootUri = DocumentsContract.buildRootUri(DOWNLOAD_STORAGE_PROVIDER_AUTHORITY,
                    "downloads");
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, rootUri);
            activity.startActivityForResult(intent, REQUEST_CODE);
            mDevice.waitForIdle();

            findDocument(file.getName()).click();
            final GetResultActivity.Result result = activity.getResult();
            final Uri uri = result.data.getData();

            assertTrue(PERMISSION_GRANTED == checkUriPermission(uri));

            // onMediaStoreDownloadsDeleted API is called on MediaProvider#delete()
            // for download files. This API revokes grants on DownloadStorageProvider granted Uris
            LongSparseArray<String> lpa = new LongSparseArray<String>();
            final long mediaStoreId = Long.parseLong(getMediaStoreColumnValue(mediaStoreUri,
                    MediaStore.Files.FileColumns._ID));
            final String mimeType = getMediaStoreColumnValue(mediaStoreUri,
                    MediaStore.Files.FileColumns.MIME_TYPE);
            lpa.put(mediaStoreId, mimeType);
            // This API is permission protected
            mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(WRITE_MEDIA_STORAGE);
            try {
                mDownloadManager.onMediaStoreDownloadsDeleted(lpa);
            } finally {
                mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
            }

            assertTrue(PERMISSION_DENIED == checkUriPermission(uri));
        } finally {
            file.delete();
        }
    }

    private GetResultActivity setUpForActivity() {
        final PackageManager pm = mContext.getPackageManager();
        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        final ResolveInfo ri = pm.resolveActivity(intent, 0);
        mDocumentsUiPackageId = ri.activityInfo.packageName;

        final Intent intent2 = new Intent(mContext, GetResultActivity.class);
        intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        GetResultActivity activity = (GetResultActivity) mInstrumentation.startActivitySync(
                intent2);
        mInstrumentation.waitForIdleSync();
        activity.clearResult();
        return activity;
    }

    private int checkUriPermission(Uri uri) {
        return mContext.checkUriPermission(uri, android.os.Process.myPid(),
                android.os.Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    private UiObject findDocument(String label) throws UiObjectNotFoundException {
        final UiSelector docList = new UiSelector().resourceId(getDocumentsUiPackageId()
                + ":id/dir_list");

        // Wait for the first list item to appear
        assertTrue("First list item",
                new UiObject(docList.childSelector(new UiSelector()))
                        .waitForExists(LONG_TIMEOUT));

        try {
            //Enforce to set the list mode
            //Because UiScrollable can't reach the real bottom (when WEB_LINKABLE_FILE item)
            // in grid mode when screen landscape mode
            new UiObject(new UiSelector().resourceId(getDocumentsUiPackageId()
                    + ":id/sub_menu_list")).click();
            mDevice.waitForIdle();
        } catch (UiObjectNotFoundException e) {
            //do nothing, already be in list mode.
        }

        // Repeat swipe gesture to find our item
        // (UiScrollable#scrollIntoView does not seem to work well with SwipeRefreshLayout)
        UiObject targetObject = new UiObject(docList.childSelector(new UiSelector().text(label)));
        UiObject saveButton = findSaveButton();
        int stepLimit = 10;
        while (stepLimit-- > 0) {
            if (targetObject.exists()) {
                boolean targetObjectFullyVisible = !saveButton.exists()
                        || targetObject.getVisibleBounds().bottom
                        <= saveButton.getVisibleBounds().top;
                if (targetObjectFullyVisible) {
                    break;
                }
            }

            mDevice.swipe(/* startX= */ mDevice.getDisplayWidth() / 2,
                    /* startY= */ mDevice.getDisplayHeight() / 2,
                    /* endX= */ mDevice.getDisplayWidth() / 2,
                    /* endY= */ 0,
                    /* steps= */ 40);
        }
        return targetObject;
    }

    private UiObject findSaveButton() throws UiObjectNotFoundException {
        return new UiObject(new UiSelector().resourceId(
                getDocumentsUiPackageId() + ":id/container_save")
                .childSelector(new UiSelector().resourceId("android:id/button1")));
    }

    private String getDocumentsUiPackageId() {
        return mDocumentsUiPackageId;
    }

    private File stageFile(String resId, File file) throws IOException {
        // The caller may be trying to stage into a location only available to
        // the shell user, so we need to perform the entire copy as the shell
        final File dir = file.getParentFile();
        dir.mkdirs();
        if (!dir.exists()) {
            throw new FileNotFoundException("Failed to create parent for " + file);
        }
        try (InputStream source = mContext.getAssets().open(resId);
             OutputStream target = new FileOutputStream(file)) {
            FileUtils.copy(source, target);
        }
        return file;
    }
}
