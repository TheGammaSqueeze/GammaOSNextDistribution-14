/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.compat.testing;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;

/**
 * For extracting and storing shared library information in CTS.
 */
public final class SharedLibraryInfo {
    static final String HELPER_APP_APK = "SharedLibraryInfoTestApp.apk";
    static final String HELPER_APP_PACKAGE = "android.compat.testing.app";
    static final String HELPER_APP_CLASS = HELPER_APP_PACKAGE + ".SharedLibraryInfoDeviceTest";

    public final String name;
    public final String type;
    public final String version;
    public final ImmutableList<String> paths;

    private SharedLibraryInfo(String name, String type, String version,
            ImmutableList<String> paths) {
        this.name = name;
        this.type = type;
        this.version = version;
        this.paths = paths;
    }

    private static SharedLibraryInfo readFromLine(String line) {
        String[] words = line.split(" ");
        assertWithMessage(
                "expected each line to be in the format: <name> <type> <version> <path>...")
                .that(words.length)
                .isAtLeast(4);
        String libraryName = words[0];
        String libraryType = words[1];
        String libraryVersion = words[2];
        ImmutableList.Builder<String> paths = ImmutableList.builder();
        for (int i = 3; i < words.length; i++) {
            String path = words[i];
            paths.add(path);
        }
        return new SharedLibraryInfo(libraryName, libraryType, libraryVersion, paths.build());
    }

    static ImmutableList<SharedLibraryInfo> getSharedLibraryInfos(String sharedLibsTxtContent) {
        ImmutableList.Builder<SharedLibraryInfo> libraries = ImmutableList.builder();
        for (String line : sharedLibsTxtContent.split("\n")) {
            libraries.add(readFromLine(line));
        }
        return libraries.build();
    }
}
