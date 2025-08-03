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

package android.security.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import androidx.test.runner.AndroidJUnit4;

import dalvik.system.BaseDexClassLoader;
import dalvik.system.ZipPathValidator;

import com.google.common.collect.Iterators;

import libcore.io.Streams;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@RunWith(AndroidJUnit4.class)
public class ZipPathValidatorDeviceTest {
    @Test
    public void newZipFile_whenZipFileHasDangerousEntriesAndChangeEnabled_throws() throws
            Exception {
        final String[] dangerousEntryNames = {
                "../foo.bar",
                "foo/../bar.baz",
                "foo/../../bar.baz",
                "foo.bar/..",
                "foo.bar/../",
                "..",
                "../",
                "/foo",
        };
        for (String entryName : dangerousEntryNames) {
            final File tempFile = File.createTempFile("smdc", "zip");
            try {
                writeZipFileOutputStreamWithEmptyEntry(tempFile, entryName);

                assertThrows(
                        "ZipException expected for entry: " + entryName,
                        ZipException.class,
                        () -> {
                            new ZipFile(tempFile);
                        });
            } finally {
                tempFile.delete();
            }
        }
    }

    @Test
    public void zipInputStreamGetNextEntry_whenZipFileHasDangerousEntriesAndChangeEnabled_throws()
            throws Exception {
        final String[] dangerousEntryNames = {
                "../foo.bar",
                "foo/../bar.baz",
                "foo/../../bar.baz",
                "foo.bar/..",
                "foo.bar/../",
                "..",
                "../",
                "/foo",
        };
        for (String entryName : dangerousEntryNames) {
            byte[] badZipBytes = getZipBytesFromZipOutputStreamWithEmptyEntry(entryName);
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(badZipBytes))) {
                assertThrows(
                        "ZipException expected for entry: " + entryName,
                        ZipException.class,
                        () -> {
                            zis.getNextEntry();
                        });
            }
        }
    }

    @Test
    public void newZipFile_whenZipFileHasNormalEntriesAndChangeEnabled_doesNotThrow() throws
            Exception {
        final String[] normalEntryNames = {
                "foo", "foo.bar", "foo..bar",
        };
        for (String entryName : normalEntryNames) {
            final File tempFile = File.createTempFile("smdc", "zip");
            try {
                writeZipFileOutputStreamWithEmptyEntry(tempFile, entryName);
                ZipFile zipFile = new ZipFile((tempFile));
            } finally {
                tempFile.delete();
            }
        }
    }

    @Test
    public void
            zipInputStreamGetNextEntry_whenZipFileHasNormalEntriesAndChangeEnabled_doesNotThrow()
                    throws Exception {
        final String[] normalEntryNames = {
                "foo", "foo.bar", "foo..bar",
        };
        for (String entryName : normalEntryNames) {
            byte[] zipBytes = getZipBytesFromZipOutputStreamWithEmptyEntry(entryName);
            ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes));
            ZipEntry zipEntry = zis.getNextEntry();
        }
    }

    @Test
    public void newZipFile_whenZipFileHasNormalAndDangerousEntriesAndChangeDisabled_doesNotThrow()
            throws Exception {
        final String[] entryNames = {
                "../foo.bar",
                "foo/../bar.baz",
                "foo/../../bar.baz",
                "foo.bar/..",
                "foo.bar/../",
                "..",
                "../",
                "/foo",
                "foo",
                "foo.bar",
                "foo..bar",
        };
        for (String entryName : entryNames) {
            final File tempFile = File.createTempFile("smdc", "zip");
            try {
                writeZipFileOutputStreamWithEmptyEntry(tempFile, entryName);
                ZipFile zipFile = new ZipFile((tempFile));
            } finally {
                tempFile.delete();
            }
        }
    }

    @Test
    public void
            zipInputStreamGetNextEntry_whenZipFileHasNormalAndDangerousEntriesAndChangeDisabled_doesNotThrow()
                    throws Exception {
        final String[] entryNames = {
                "../foo.bar",
                "foo/../bar.baz",
                "foo/../../bar.baz",
                "foo.bar/..",
                "foo.bar/../",
                "..",
                "../",
                "/foo",
                "foo",
                "foo.bar",
                "foo..bar",
        };
        for (String entryName : entryNames) {
            byte[] zipBytes = getZipBytesFromZipOutputStreamWithEmptyEntry(entryName);
            ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes));
            ZipEntry zipEntry = zis.getNextEntry();
        }
    }

    @Test
    public void loadingApksWillNotCallZipPathValidator_changeEnabledOrDisabled() throws Exception {
        try {
            // Set a mock callback
            ZipPathValidator.Callback mockZipCallback = mock(ZipPathValidator.Callback.class);
            ZipPathValidator.setCallback(mockZipCallback);

            // Put contents resources/test.jar into a temporary file jar
            ClassLoader pcl = ZipPathValidatorDeviceTest.class.getClassLoader();
            File jar = File.createTempFile("test", ".jar");
            try (InputStream in = pcl.getResourceAsStream("test.jar");
                 FileOutputStream out = new FileOutputStream(jar)) {
                Streams.copy(in, out);
            }

            // Load the temporary file into a ClassLoader
            ClassLoader cl = new BaseDexClassLoader(jar.getPath(), null, null,
                    ClassLoader.getSystemClassLoader());

            // Get resources for the jar file, should be a single resource
            Enumeration<URL> resources = cl.getResources("test/resource.txt");
            int resourcesSize = Iterators.size(resources.asIterator());
            assertEquals(1, resourcesSize);

            // Should not call ZipPathValidator callback
            verify(mockZipCallback, never()).onZipEntryAccess("test/resource.txt");
        } finally {
            ZipPathValidator.clearCallback();
        }
    }

    private void writeZipFileOutputStreamWithEmptyEntry(File tempFile, String entryName)
            throws IOException {
        FileOutputStream tempFileStream = new FileOutputStream(tempFile);
        writeZipOutputStreamWithEmptyEntry(tempFileStream, entryName);
        tempFileStream.close();
    }

    private byte[] getZipBytesFromZipOutputStreamWithEmptyEntry(String entryName)
            throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        writeZipOutputStreamWithEmptyEntry(bos, entryName);
        return bos.toByteArray();
    }

    private void writeZipOutputStreamWithEmptyEntry(OutputStream os, String entryName)
            throws IOException {
        ZipOutputStream zos = new ZipOutputStream(os);
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        zos.write(new byte[2]);
        zos.closeEntry();
        zos.close();
    }
}
