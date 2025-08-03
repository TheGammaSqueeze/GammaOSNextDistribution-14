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

package com.android.ondevicepersonalization.libraries.plugin.internal.util;

import com.google.common.collect.ImmutableList;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/** Util class to unzip and load code form plugin apks. */
public final class ApkReader {
    private static final String DEX_SUFFIX = ".dex";
    private static final int PAGE_SIZE_BYTES = 4_096;

    /** Loads the plugin apk dex files into a ByteBuffer. */
    public static ByteBuffer[] loadPluginCode(FileInputStream pluginArchive) throws IOException {
        return loadPluginCode(ImmutableList.of(pluginArchive));
    }

    /** Loads multiple plugin apks' dex files into a ByteBuffer. */
    public static ByteBuffer[] loadPluginCode(Collection<FileInputStream> pluginArchives)
            throws IOException {
        ArrayList<ByteBuffer> dexes = new ArrayList<>();
        byte[] buffer = new byte[PAGE_SIZE_BYTES];
        for (FileInputStream pluginArchive : pluginArchives) {
            try (ZipInputStream archive = new ZipInputStream(pluginArchive)) {
                ZipEntry entry;
                while ((entry = archive.getNextEntry()) != null) {
                    String fileName = getValidatedName(entry);

                    // multidex support
                    if (fileName.endsWith(DEX_SUFFIX)) {
                        try (ByteArrayOutputStream decompressedFile = new ByteArrayOutputStream()) {
                            int length;
                            while ((length = archive.read(buffer, /*off=*/ 0, buffer.length))
                                    >= 0) {
                                decompressedFile.write(buffer, /*off=*/ 0, length);
                            }
                            dexes.add(ByteBuffer.wrap(decompressedFile.toByteArray()));
                        }
                    }
                }
            }
        }

        return dexes.toArray(new ByteBuffer[0]);
    }

    /**
     * Returns the name of a ZipEntry after verifying that it does not exploit any path traversal
     * attacks.
     */
    static String getValidatedName(ZipEntry zipEntry) throws ZipException {
        // Forked from com.google.android.libraries.security.zip.SafeZipEntry because it is not
        // available externally.
        String name = zipEntry.getName();
        if (name.contains("..")) {
            // If the string does contain "..", break it down into its actual name
            // elements to ensure it actually contains ".." as a name, not just a
            // name like "foo..bar" or even "foo..", which should be fine.
            File file = new File(name);
            while (file != null) {
                if (file.getName().equals("..")) {
                    throw new ZipException("Illegal name: " + name);
                }
                file = file.getParentFile();
            }
        }
        return name;
    }

    private ApkReader() {}
}
