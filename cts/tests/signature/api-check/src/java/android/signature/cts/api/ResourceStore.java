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
package android.signature.cts.api;

import android.signature.cts.VirtualPath;
import android.util.Log;
import com.google.common.base.Suppliers;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * Manages local storage of resources that need to be extracted from the Jar into the local
 * filesystem before use.
 */
public class ResourceStore {

    private static final String TAG = ResourceStore.class.getSimpleName();

    /**
     * Supplier for the temporary directory.
     */
    private static final Supplier<Path> TEMPORARY_DIRECTORY = Suppliers.memoize(() -> {
        try {
            return Files.createTempDirectory("signature");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    })::get;

    /**
     * A map from a {@link VirtualPath} to a {@link ZipFile} for zip file resources that
     * have been extracted from the jar into the local file system.
     */
    private static final Map<VirtualPath, ZipFile> extractedZipFiles = new HashMap<>();

    public static Stream<VirtualPath> readResource(ClassLoader classLoader, String resourceName) {
        try {
            VirtualPath resourcePath = VirtualPath.get(classLoader, resourceName);
            if (resourceName.endsWith(".zip")) {
                // Extract to a temporary file and then read from there. If the resource has already
                // been extracted before then reuse the previous file.
                @SuppressWarnings("resource")
                ZipFile zip = extractedZipFiles.computeIfAbsent(resourcePath, virtualPath -> {
                    try {
                        Path path = extractResourceToFile(resourceName, resourcePath);
                        return new ZipFile(path.toFile());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                return zip.stream().map(entry -> VirtualPath.get(zip, entry));
            } else {
                return Stream.of(resourcePath);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Close any previously opened {@link ZipFile} instances.
     */
    public static void close() {
        for (ZipFile zipfile: extractedZipFiles.values()) {
            // If an error occurred when closing the ZipFile log the failure and continue.
            try {
                zipfile.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close ZipFile " + zipfile, e);
            }
        }
    }

    private static Path extractResourceToFile(String resourceName, VirtualPath resourcePath)
            throws IOException {
        Path tempDirectory = TEMPORARY_DIRECTORY.get();
        Path file = tempDirectory.resolve(resourceName);
        try (InputStream is = resourcePath.newInputStream()) {
            Log.i(TAG, "extractResourceToFile: extracting " + resourceName + " to " + file);
            Files.copy(is, file);
        }
        return file;
    }
}
