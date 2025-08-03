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

package android.ext.services.common;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.truth.Expect;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.Spy;
import org.mockito.quality.Strictness;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;

public final class AdServicesFilesCleanupBootCompleteReceiverTest {
    private static final String ADSERVICES_FILE_NAME = "adservices_file";
    private static final String NON_ADSERVICES_FILE_NAME = "some_other_file";
    private static final String NON_ADSERVICES_FILE_WITH_PREFIX_IN_NAME =
            "some_file_with_adservices_in_name";
    private static final String ADSERVICES_FILE_NAME_MIXED_CASE = "AdServicesFileMixedCase.txt";
    private static final String NON_ADSERVICE_FILE_NAME_2 = "adservice_but_no_s.txt";

    // Update this list with the previous name every time the receiver is renamed
    private static final List<String> PREVIOUSLY_USED_CLASS_NAMES = List.of();

    // TODO(b/297207132): Replace with AdServicesExtendedMockitoRule
    private MockitoSession mMockitoSession;

    @Spy
    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Spy
    private AdServicesFilesCleanupBootCompleteReceiver mReceiver;

    @Mock
    private PackageManager mPackageManager;

    @Rule
    public final Expect expect = Expect.create();

    @Before
    public void setup() {
        mMockitoSession = ExtendedMockito.mockitoSession()
                .initMocks(this)
                .strictness(Strictness.WARN)
                .startMocking();

        doReturn(mPackageManager).when(mContext).getPackageManager();
        doNothing().when(mReceiver).scheduleAppsearchDeleteJob(any());
    }

    @After
    public void tearDown() {
        if (mMockitoSession != null) {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testReceiverDoesNotReuseClassNames() {
        assertThat(PREVIOUSLY_USED_CLASS_NAMES)
                .doesNotContain(AdServicesFilesCleanupBootCompleteReceiver.class.getName());
    }

    @Test
    public void testReceiverSkipsDeletionIfDisabled() {
        mockReceiverEnabled(false);

        mReceiver.onReceive(mContext, /* intent= */ null);

        verify(mContext, never()).getDataDir();
        verify(mContext, never()).getPackageManager();
        verify(mReceiver, never()).scheduleAppsearchDeleteJob(any());
    }

    @Test
    public void testReceiverDisablesItselfIfDeleteSuccessful() {
        mockReceiverEnabled(true);
        doNothing().when(mPackageManager).setComponentEnabledSetting(any(), anyInt(), anyInt());
        doReturn(true).when(mReceiver).deleteAdServicesFiles(any());

        mReceiver.onReceive(mContext, /* intent= */ null);
        verify(mReceiver).scheduleAppsearchDeleteJob(any());
        verifyDisableComponentCalled();
    }

    @Test
    public void testReceiverDisablesItselfIfDeleteUnsuccessful() {
        mockReceiverEnabled(true);
        doReturn(false).when(mReceiver).deleteAdServicesFiles(any());

        mReceiver.onReceive(mContext, /* intent= */ null);
        verify(mReceiver).scheduleAppsearchDeleteJob(any());
        verifyDisableComponentCalled();
    }

    @Test
    public void testReceiverDeletesAdServicesFiles() throws Exception {
        List<String> adServicesNames = List.of(ADSERVICES_FILE_NAME,
                ADSERVICES_FILE_NAME_MIXED_CASE);
        List<String> nonAdServicesNames = List.of(NON_ADSERVICES_FILE_NAME,
                NON_ADSERVICES_FILE_WITH_PREFIX_IN_NAME, NON_ADSERVICE_FILE_NAME_2);

        try {
            createFiles(adServicesNames);
            createFiles(nonAdServicesNames);
            createDatabases(adServicesNames);
            createDatabases(nonAdServicesNames);

            mReceiver.deleteAdServicesFiles(mContext.getDataDir());

            // Check if the appropriate files were deleted
            String[] remainingFiles = mContext.getFilesDir().list();
            List<String> remainingFilesList = Arrays.asList(remainingFiles);
            expect.that(remainingFilesList).containsNoneIn(adServicesNames);
            expect.that(remainingFilesList).containsAtLeastElementsIn(nonAdServicesNames);
            expectDatabasesExist(nonAdServicesNames);
            expectDatabasesDoNotExist(adServicesNames);
        } finally {
            deleteFiles(adServicesNames);
            deleteFiles(nonAdServicesNames);
            deleteDatabases(adServicesNames);
            deleteDatabases(nonAdServicesNames);
        }
    }

    @Test
    public void testReceiverDeletesAdServicesDirectories() throws Exception {
        String dataRoot = "data_root";
        Path root = mContext.getFilesDir().toPath();

        try {
            File file1 = createFile(root, dataRoot, "level_1.txt"); // Preserved
            File file2 = createFile(root, dataRoot, "adservices_level_1.txt"); // Deleted
            File file3 = createFile(root, dataRoot + "/non_adservices",
                    "level_2.txt"); // Preserved
            File file4 = createFile(root, dataRoot + "/non_adservices",
                    "adservices_level_2.txt"); // Deleted
            File file5 = createFile(root, dataRoot + "/non_adservices/adservices_nested",
                    "level_3.txt"); // Deleted
            File file6 = createFile(root, dataRoot + "/non_adservices/adservices_nested",
                    "adservices.level_3.txt"); // Deleted
            File file7 = createFile(root, dataRoot + "/non_adservices",
                    "AdServices_level_2.txt"); // Deleted
            File file8 = createFile(root, dataRoot + "/adservices-data",
                    "level_2.txt"); // Deleted
            File file9 = createFile(root, dataRoot + "/adservices-data/nested",
                    "level_3.txt"); // Deleted
            File file10 = createFile(root, dataRoot + "/AdServices-data/nested",
                    "level_3_1.txt");

            mReceiver.deleteAdServicesFiles(mContext.getDataDir());

            expectFilesExist(file1, file3);
            expectFilesDoNotExist(file2, file4, file5, file6, file7, file8, file9, file10);
        } finally {
            deletePathRecursively(root.resolve(dataRoot));
        }
    }

    @Test
    public void testReceiverHandlesSecurityException() {
        // Simulate a directory with three files, and the first one throws an exception on delete
        File file1 = mock(File.class);
        doReturn(ADSERVICES_FILE_NAME).when(file1).getName();
        doThrow(SecurityException.class).when(file1).delete();

        File file2 = mock(File.class);
        doReturn(ADSERVICES_FILE_NAME_MIXED_CASE).when(file2).getName();

        File file3 = mock(File.class);
        doReturn(NON_ADSERVICES_FILE_NAME).when(file3).getName();

        File dir = mock(File.class);
        doReturn(true).when(dir).isDirectory();
        doReturn(new File[] { file1, file2, file3 }).when(dir).listFiles();

        // Execute the receiver
        mReceiver.deleteAdServicesFiles(dir);

        // Verify that deletion of both file1 and file2 was attempted, in spite of the exception
        verify(file1).delete();
        verify(file2).delete();
        verify(file3, never()).delete();
    }

    @Test
    public void testDeleteAdServicesFiles_invalidInput() {
        // Null input
        assertThat(mReceiver.deleteAdServicesFiles(null)).isTrue();

        // Not a directory
        File file = mock(File.class);
        assertThat(mReceiver.deleteAdServicesFiles(file)).isTrue();
        verify(file, never()).listFiles();

        // Throws an exception
        File file2 = mock(File.class);
        doThrow(SecurityException.class).when(file2).isDirectory();
        assertThat(mReceiver.deleteAdServicesFiles(file2)).isFalse();
        verify(file2, never()).listFiles();
    }

    private void mockReceiverEnabled(boolean value) {
        doReturn(value).when(mReceiver).isReceiverEnabled();
    }

    private void verifyDisableComponentCalled() {
        verify(mPackageManager).setComponentEnabledSetting(any(),
                eq(PackageManager.COMPONENT_ENABLED_STATE_DISABLED), eq(0));
    }

    private void expectFilesExist(File... files) {
        for (File file: files) {
            expect.withMessage("%s exists", file.getPath()).that(file.exists()).isTrue();
        }
    }

    private void expectFilesDoNotExist(File... files) {
        for (File file: files) {
            expect.withMessage("%s exists", file.getPath()).that(file.exists()).isFalse();
        }
    }

    private void expectDatabasesExist(List<String> databaseNames) {
        for (String db: databaseNames) {
            expect.withMessage("%s exists", db)
                    .that(mContext.getDatabasePath(db).exists())
                    .isTrue();
        }
    }

    private void expectDatabasesDoNotExist(List<String> databaseNames) {
        for (String db: databaseNames) {
            expect.withMessage("%s exists", db)
                    .that(mContext.getDatabasePath(db).exists())
                    .isFalse();
        }
    }

    private void createFiles(List<String> names) throws Exception {
        File dir = mContext.getFilesDir();
        for (String name : names) {
            createFile(name, dir);
        }
    }

    private void createDatabases(List<String> names) {
        for (String name : names) {
            try (SQLiteDatabase unused = mContext.openOrCreateDatabase(name, 0, null)) {
                // Intentionally do nothing.
            }
        }
    }

    private void deleteFiles(List<String> names) {
        for (String name : names) {
            File file = new File(mContext.getFilesDir(), name);
            if (file.exists()) {
                file.delete();
            }
        }
    }

    private void deleteDatabases(List<String> names) {
        for (String name : names) {
            mContext.deleteDatabase(name);
        }
    }

    private File createFile(String name, File directory) throws Exception {
        File file = new File(directory, name);
        try (FileWriter writer = new FileWriter(file)) {
            writer.append("test data");
            writer.flush();
        }

        return file;
    }

    private File createFile(Path root, String path, String fileName) throws Exception {
        Path dir = root.resolve(path);
        Files.createDirectories(dir);
        return createFile(fileName, dir.toFile());
    }

    private void deletePathRecursively(Path path) throws Exception {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                    throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
