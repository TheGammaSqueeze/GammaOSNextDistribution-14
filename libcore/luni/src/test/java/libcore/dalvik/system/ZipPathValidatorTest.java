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

package libcore.dalvik.system;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dalvik.system.ZipPathValidator;

import junit.framework.TestCase;

public class ZipPathValidatorTest extends TestCase {

    private ZipPathValidator.Callback mockZipCallback = mock(ZipPathValidator.Callback.class);

    @Override
    public void setUp() {
        ZipPathValidator.setCallback(mockZipCallback);
    }

    @Override
    protected void tearDown() throws Exception {
        ZipPathValidator.clearCallback();
    }

    public void testClearCallback() {
        ZipPathValidator.clearCallback();
        ZipPathValidator.Callback callbackFromGet = ZipPathValidator.getInstance();
        assertEquals(ZipPathValidator.DEFAULT, callbackFromGet);
    }

    public void testGetInstance() {
        ZipPathValidator.Callback callbackFromGet = ZipPathValidator.getInstance();
        assertEquals(mockZipCallback, callbackFromGet);
    }

    public void testZipFileCallback() throws Exception {
        final String[] entryNames = {
                "abc",
                "abc/def.ghi",
                "../foo.bar",
                "foo/../bar.baz",
                "foo/../../bar.baz",
                "foo.bar/..",
                "foo.bar/../",
                "..",
                "../",
                "/foo",
        };
        for (String entryName : entryNames) {
            final File tempFile = File.createTempFile("smdc", "zip");
            try {
                FileOutputStream tempFileStream = new FileOutputStream(tempFile);
                ZipOutputStream zipOutputStream = new ZipOutputStream(tempFileStream);
                zipOutputStream.putNextEntry(new ZipEntry(entryName));
                zipOutputStream.write(new byte[2]);
                zipOutputStream.closeEntry();
                zipOutputStream.close();
                tempFileStream.close();

                ZipFile zipFile = new ZipFile(tempFile);
                Enumeration<? extends ZipEntry> enumer = zipFile.entries();
                int counter = 0;
                while (enumer.hasMoreElements()) {
                    enumer.nextElement();
                    verify(mockZipCallback).onZipEntryAccess(entryNames[counter]);
                    counter++;
                }
            } finally {
                tempFile.delete();
            }
        }
    }

    public void testZipInputStreamCallback() throws Exception {
        final String[] entryNames = {
                "abc",
                "abc/def.ghi",
                "../foo.bar",
                "foo/../bar.baz",
                "foo/../../bar.baz",
                "foo.bar/..",
                "foo.bar/../",
                "..",
                "../",
                "/foo",
        };
        int counter = 0;
        for (String entryName : entryNames) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(bos);
            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);
            zos.write(new byte[2]);
            zos.closeEntry();
            zos.close();
            byte[] badZipBytes = bos.toByteArray();
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(badZipBytes))) {
                zis.getNextEntry();
                verify(mockZipCallback).onZipEntryAccess(entryNames[counter]);
                counter++;
            }
        }
    }

    /**
     * smdc.zip has a single file with a UTF-8 encoded name. Its name is 'файл' ('file' in Russian)
     * but the zip file is created with an ASCII encoder. In this case, the ZipPathValidator should
     * get the entry name decoded with UTF-8 even if the original encoder of the zip file is ASCII.
     */
    public void testZipFileDecodesEntryWithUTF8WhenZipCoderIsNotUTF8ButEntryIs() throws Exception {
        String entryName = "файл";
        final File tempFile = File.createTempFile("smdc", "zip");
        try {
            FileOutputStream tempFileStream = new FileOutputStream(tempFile);
            ZipOutputStream zipOutputStream = new ZipOutputStream(tempFileStream);
            zipOutputStream.putNextEntry(new ZipEntry(entryName));
            zipOutputStream.write(new byte[2]);
            zipOutputStream.closeEntry();
            zipOutputStream.close();
            tempFileStream.close();

            ZipFile zipFile = new ZipFile(tempFile, StandardCharsets.US_ASCII);
            verify(mockZipCallback).onZipEntryAccess(entryName);
        } finally {
            tempFile.delete();
        }
    }
}
