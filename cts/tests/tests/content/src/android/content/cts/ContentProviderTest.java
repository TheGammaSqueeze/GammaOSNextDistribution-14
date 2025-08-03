/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package android.content.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.testng.Assert.assertThrows;

import android.content.ContentProvider;
import android.content.ContentProvider.CallingIdentity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.nene.TestApis;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Test {@link ContentProvider}.
 */
@RunWith(BedsteadJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class ContentProviderTest {
    private static final String TEST_PACKAGE_NAME = "android.content.cts";
    private static final String TEST_FILE_NAME = "testFile.tmp";
    private static final String TEST_DB_NAME = "test.db";

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final TestApis sTestApis = new TestApis();

    @After
    public void tearDown() throws Exception {
        sContext.deleteDatabase(TEST_DB_NAME);
        sContext.deleteFile(TEST_FILE_NAME);
    }

    @Test
    public void testOpenAssetFile() throws IOException {
        MockContentProvider mockContentProvider = new MockContentProvider();
        Uri uri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE +
                "://" + TEST_PACKAGE_NAME + "/" + R.raw.testimage);

        try {
            mockContentProvider.openAssetFile(uri, "r");
            fail("Should always throw out FileNotFoundException!");
        } catch (FileNotFoundException e) {
        }

        try {
            mockContentProvider.openFile(null, null);
            fail("Should always throw out FileNotFoundException!");
        } catch (FileNotFoundException e) {
        }
    }

    @Test
    public void testAttachInfo() {
        MockContentProvider mockContentProvider = new MockContentProvider();

        ProviderInfo info1 = new ProviderInfo();
        info1.readPermission = "android.permission.READ_SMS";
        info1.writePermission = null; // Guarded by an app op not a permission.
        mockContentProvider.attachInfo(sContext, info1);
        assertSame(sContext, mockContentProvider.getContext());
        assertEquals(info1.readPermission, mockContentProvider.getReadPermission());
        assertEquals(info1.writePermission, mockContentProvider.getWritePermission());

        ProviderInfo info2 = new ProviderInfo();
        info2.readPermission = "android.permission.READ_CONTACTS";
        info2.writePermission = "android.permission.WRITE_CONTACTS";
        mockContentProvider.attachInfo(null, info2);
        assertSame(sContext, mockContentProvider.getContext());
        assertEquals(info1.readPermission, mockContentProvider.getReadPermission());
        assertEquals(info1.writePermission, mockContentProvider.getWritePermission());

        mockContentProvider = new MockContentProvider();
        mockContentProvider.attachInfo(null, null);
        assertNull(mockContentProvider.getContext());
        assertNull(mockContentProvider.getReadPermission());
        assertNull(mockContentProvider.getWritePermission());

        mockContentProvider.attachInfo(null, info2);
        assertNull(mockContentProvider.getContext());
        assertEquals(info2.readPermission, mockContentProvider.getReadPermission());
        assertEquals(info2.writePermission, mockContentProvider.getWritePermission());

        mockContentProvider.attachInfo(sContext, info1);
        assertSame(sContext, mockContentProvider.getContext());
        assertEquals(info1.readPermission, mockContentProvider.getReadPermission());
        assertEquals(info1.writePermission, mockContentProvider.getWritePermission());
    }

    @Test
    public void testBulkInsert() {
        MockContentProvider mockContentProvider = new MockContentProvider();

        int count = 2;
        ContentValues[] values = new ContentValues[count];
        for (int i = 0; i < count; i++) {
            values[i] = new ContentValues();
        }
        Uri uri = Uri.parse("content://browser/bookmarks");
        assertEquals(count, mockContentProvider.bulkInsert(uri, values));
        assertEquals(count, mockContentProvider.getInsertCount());

        mockContentProvider = new MockContentProvider();
        try {
            assertEquals(count, mockContentProvider.bulkInsert(null, values));
        } finally {
            assertEquals(count, mockContentProvider.getInsertCount());
        }
    }

    @Test
    public void testGetContext() {
        MockContentProvider mockContentProvider = new MockContentProvider();
        assertNull(mockContentProvider.getContext());

        mockContentProvider.attachInfo(sContext, null);
        assertSame(sContext, mockContentProvider.getContext());
        mockContentProvider.attachInfo(null, null);
        assertSame(sContext, mockContentProvider.getContext());
    }

    @Test
    public void testAccessReadPermission() {
        MockContentProvider mockContentProvider = new MockContentProvider();
        assertNull(mockContentProvider.getReadPermission());

        String expected = "android.permission.READ_CONTACTS";
        mockContentProvider.setReadPermissionWrapper(expected);
        assertEquals(expected, mockContentProvider.getReadPermission());

        expected = "android.permission.READ_SMS";
        mockContentProvider.setReadPermissionWrapper(expected);
        assertEquals(expected, mockContentProvider.getReadPermission());

        mockContentProvider.setReadPermissionWrapper(null);
        assertNull(mockContentProvider.getReadPermission());
    }

    @Test
    public void testAccessWritePermission() {
        MockContentProvider mockContentProvider = new MockContentProvider();
        assertNull(mockContentProvider.getWritePermission());

        String expected = "android.permission.WRITE_CONTACTS";
        mockContentProvider.setWritePermissionWrapper(expected);
        assertEquals(expected, mockContentProvider.getWritePermission());

        mockContentProvider.setWritePermissionWrapper(null);
        assertNull(mockContentProvider.getWritePermission());
    }

    @Test
    public void testIsTemporary() {
        MockContentProvider mockContentProvider = new MockContentProvider();
        assertFalse(mockContentProvider.isTemporary());
    }

    @Test
    public void testOpenFile() {
        MockContentProvider mockContentProvider = new MockContentProvider();

        try {
            Uri uri = Uri.parse("content://test");
            mockContentProvider.openFile(uri, "r");
            fail("Should always throw out FileNotFoundException!");
        } catch (FileNotFoundException e) {
        }

        try {
            mockContentProvider.openFile(null, null);
            fail("Should always throw out FileNotFoundException!");
        } catch (FileNotFoundException e) {
        }
    }

    @Test
    public void testOpenFileHelper() throws IOException {
        // create a temporary File
        sContext.openFileOutput(TEST_FILE_NAME, Context.MODE_PRIVATE).close();
        File file = sContext.getFileStreamPath(TEST_FILE_NAME);
        assertTrue(file.exists());

        ContentProvider cp = new OpenFilePipeContentProvider(file.getAbsolutePath(), TEST_DB_NAME);

        Uri uri = Uri.parse("content://test");
        assertNotNull(cp.openFile(uri, "r"));

        try {
            uri = Uri.parse("content://test");
            cp.openFile(uri, "wrong");
            fail("Should throw IllegalArgumentException for bad mode!");
        } catch (IllegalArgumentException e) {
        }

        // delete the temporary file
        file.delete();

        try {
            uri = Uri.parse("content://test");
            cp.openFile(uri, "r");
            fail("Should throw FileNotFoundException!");
        } catch (FileNotFoundException e) {
        }

        try {
            cp.openFile((Uri) null, "r");
            fail("Should always throw FileNotFoundException!");
        } catch (FileNotFoundException e) {
        }
    }

    private static void assertAssetFileContents(AssetFileDescriptor assetFileDescriptor,
            String message) throws IOException {
        assertNotNull(assetFileDescriptor);
        try (DataInputStream dis = new DataInputStream(
                new FileInputStream(assetFileDescriptor.getFileDescriptor()))) {
            assertEquals(message, dis.readUTF());
        }
    }

    @Test
    public void testOpenPipeHelper() throws IOException {
        // create a temporary File
        sContext.openFileOutput(TEST_FILE_NAME, Context.MODE_PRIVATE).close();
        File file = sContext.getFileStreamPath(TEST_FILE_NAME);
        assertTrue(file.exists());

        ContentProvider cp = new OpenFilePipeContentProvider(file.getAbsolutePath(), TEST_DB_NAME);

        Uri uri = Uri.parse("content://test");
        assertAssetFileContents(cp.openAssetFile(uri, "r"), "OK");

        uri = Uri.parse("content://test");
        assertAssetFileContents(cp.openAssetFile(uri, "wrong"),
                "java.lang.IllegalArgumentException: Bad mode: wrong");

        // delete the temporary file
        file.delete();

        uri = Uri.parse("content://test");
        assertAssetFileContents(cp.openAssetFile(uri, "r"),
                "java.io.FileNotFoundException: open failed: ENOENT (No such file or directory)");

        assertAssetFileContents(cp.openAssetFile((Uri) null, "r"),
                "java.io.FileNotFoundException: open failed: ENOENT (No such file or directory)");
    }

    @Test
    public void testOnConfigurationChanged() {
        // cannot trigger this callback reliably
    }

    @Test
    public void testOnLowMemory() {
        // cannot trigger this callback reliably
    }

    @Test
    public void testRefresh_DefaultImplReturnsFalse() {
        MockContentProvider provider = new MockContentProvider();
        assertFalse(provider.refresh(null, null, null));
    }

    @Test
    public void testGetIContentProvider() {
        MockContentProvider mockContentProvider = new MockContentProvider();

        assertNotNull(mockContentProvider.getIContentProvider());
    }

    @Test
    public void testClearCallingIdentity() {
        final MockContentProvider provider = new MockContentProvider();
        provider.attachInfo(sContext, new ProviderInfo());

        final CallingIdentity ident = provider.clearCallingIdentity();
        try {
            assertEquals(android.os.Process.myUid(), Binder.getCallingUid());
            assertEquals(null, provider.getCallingPackage());
        } finally {
            provider.restoreCallingIdentity(ident);
        }
    }

    @Test
    public void testCheckUriPermission() {
        MockContentProvider provider = new MockContentProvider();
        final Uri uri = Uri.parse("content://test");
        assertEquals(PackageManager.PERMISSION_DENIED,
                provider.checkUriPermission(uri, android.os.Process.myUid(), 0));
    }

    @Test
    public void testCreateContentUriForUser_nullUri_throwsNPE() {
        assertThrows(
                NullPointerException.class,
                () -> ContentProvider.createContentUriForUser(null, UserHandle.of(7)));
    }

    @Test
    public void testCreateContentUriForUser_nonContentUri_throwsIAE() {
        final Uri uri = Uri.parse("notcontent://test");
        assertThrows(
                IllegalArgumentException.class,
                () -> ContentProvider.createContentUriForUser(uri, UserHandle.of(7)));
    }

    @Test
    public void testCreateContentUriForUser_UriWithDifferentUserID_throwsIAE() {
        final Uri uri = Uri.parse("content://07@Test");
        assertThrows(
                IllegalArgumentException.class,
                () -> ContentProvider.createContentUriForUser(uri, UserHandle.of(7)));
    }

    @Test
    public void testCreateContentUriForUser_UriWithUserID_unchanged() {
        final Uri uri = Uri.parse("content://7@Test");
        assertEquals(uri, ContentProvider.createContentUriForUser(uri, UserHandle.of(7)));
    }

    @Test
    @EnsureHasWorkProfile
    @AppModeFull
    public void createContentUriForUser_returnsCorrectUri() {
        final ContentResolver profileContentResolver =
                sTestApis.context().androidContextAsUser(sDeviceState.workProfile())
                        .getContentResolver();
        final String testContentDisplayName = "testContent.mp3";
        final Uri workProfileUriWithoutUserId = createAndInsertTestAudioFile(
                profileContentResolver, testContentDisplayName);

        final Uri workProfileUriWithUserId = ContentProvider.createContentUriForUser(
                workProfileUriWithoutUserId, sDeviceState.workProfile().userHandle());

        assertThat(getAudioContentDisplayName(
                sContext.getContentResolver(), workProfileUriWithUserId))
                .isEqualTo(testContentDisplayName);
    }

    private Uri createAndInsertTestAudioFile(ContentResolver resolver, String displayName) {
        final Uri audioCollection = MediaStore.Audio.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY);
        final ContentValues testContent = new ContentValues();
        testContent.put(MediaStore.Audio.Media.DISPLAY_NAME, displayName);
        return resolver.insert(audioCollection, testContent);
    }

    private String getAudioContentDisplayName(ContentResolver resolver, Uri uri) {
        String name = null;
        try (Cursor cursor = resolver.query(
                uri,
                /* projection = */ null,
                /* selection = */ null,
                /* selectionArgs = */ null,
                /* sortOrder = */ null)) {
            final int nameColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
            if (cursor.moveToNext()) {
                name = cursor.getString(nameColumn);
            }
        }
        return name;
    }

    private class MockContentProvider extends ContentProvider {
        private int mInsertCount = 0;

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            return 0;
        }

        @Override
        public String getType(Uri uri) {
            return null;
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            mInsertCount++;
            return null;
        }

        public int getInsertCount() {
            return mInsertCount;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection,
                String[] selectionArgs, String sortOrder) {
            return null;
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection,
                String[] selectionArgs) {
            return 0;
        }

        @Override
        public boolean onCreate() {
            return false;
        }

        // wrapper or override for the protected methods
        public void setReadPermissionWrapper(String permission) {
            super.setReadPermission(permission);
        }

        public void setWritePermissionWrapper(String permission) {
            super.setWritePermission(permission);
        }

        @Override
        protected boolean isTemporary() {
            return super.isTemporary();
        }

        public ParcelFileDescriptor openFileHelperWrapper(Uri uri, String mode)
                throws FileNotFoundException {
            return super.openFileHelper(uri, mode);
        }
    }

    /**
     * This provider implements openFile/openAssetFile() using
     * ContentProvider.openFileHelper/openPipeHelper().
     */
    private class OpenFilePipeContentProvider extends ContentProvider implements
            ContentProvider.PipeDataWriter<String> {
        private SQLiteDatabase mDb;

        OpenFilePipeContentProvider(String fileName, String dbName) {
            // delete the database if it already exists
            sContext.deleteDatabase(dbName);
            mDb = sContext.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null);
            mDb.execSQL("CREATE TABLE files ( _data TEXT );");
            mDb.execSQL("INSERT INTO files VALUES ( \"" + fileName + "\");");
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            throw new RuntimeException("not implemented");
        }

        @Override
        public String getType(Uri uri) {
            throw new RuntimeException("not implemented");
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            throw new RuntimeException("not implemented");
        }

        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                String sortOrder) {
            return mDb.query("files", projection, selection, selectionArgs, null, null, null);
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            throw new RuntimeException("not implemented");
        }

        @Override
        public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
            return openFileHelper(uri, mode);
        }

        @Override
        public AssetFileDescriptor openAssetFile(Uri uri, String mode)
                throws FileNotFoundException {
            return new AssetFileDescriptor(openPipeHelper(uri, "text/html", null,
                    mode, this), 0,
                    AssetFileDescriptor.UNKNOWN_LENGTH);
        }

        @Override
        public void writeDataToPipe(@NonNull ParcelFileDescriptor output, @NonNull Uri uri,
                @NonNull String mimeType, @Nullable Bundle opts, @Nullable String args) {
            try (DataOutputStream dos = new DataOutputStream(
                    new FileOutputStream(output.getFileDescriptor()))) {
                try (InputStream is = new FileInputStream(
                        openFile(uri, args).getFileDescriptor())) {
                    dos.writeUTF("OK");
                } catch (Throwable t) {
                    dos.writeUTF(t.toString());
                }
            } catch (IOException ignored) {

            }
        }
    }
}
