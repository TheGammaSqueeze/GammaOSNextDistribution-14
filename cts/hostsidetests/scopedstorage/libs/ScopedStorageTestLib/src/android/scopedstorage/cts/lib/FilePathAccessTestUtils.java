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

package android.scopedstorage.cts.lib;

import static android.scopedstorage.cts.lib.TestUtils.assertThrows;
import static android.scopedstorage.cts.lib.TestUtils.getAndroidDir;
import static android.system.OsConstants.F_OK;
import static android.system.OsConstants.R_OK;
import static android.system.OsConstants.W_OK;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;

import android.system.ErrnoException;
import android.system.Os;

import java.io.File;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class to test media access using the file system.
 *
 * Its twin class {@link ResolverAccessTestUtils } covers testing using the Content Resolver API.
 */
public class FilePathAccessTestUtils {
    public static void assertFileAccess_existsOnly(File file) throws Exception {
        assertThat(file.isFile()).isTrue();
        assertAccess(file, true /* file exists */, false /* can read */, false /* can write */);
    }

    public static void assertFileAccess_readOnly(File file) throws Exception {
        assertThat(file.isFile()).isTrue();
        assertAccess(file, true /* file exists */, true /* can read */, false /* can write */);
    }

    public static void assertFileAccess_readWrite(File file) throws Exception {
        assertThat(file.isFile()).isTrue();
        assertAccess(file, true/* file exists */, true /* can read */, true /* can write */);
    }

    public static void assertDirectoryAccess(File dir, boolean exists, boolean canWrite)
            throws Exception {
        // This util does not handle app data directories.
        assumeFalse(dir.getAbsolutePath().startsWith(getAndroidDir().getAbsolutePath())
                && !dir.equals(getAndroidDir()));
        assertThat(dir.isDirectory()).isEqualTo(exists);
        // For non-app data directories, exists => canRead().
        assertAccess(dir, exists, exists, exists && canWrite);
    }

    public static void assertAccess(File file, boolean exists, boolean canRead, boolean canWrite)
            throws Exception {
        assertAccess(file, exists, canRead, canWrite, true /* checkExists */);
    }

    public static void assertCannotReadOrWrite(File file)
            throws Exception {
        // App data directories have different 'x' bits on upgrading vs new devices. Let's not
        // check 'exists', by passing checkExists=false. But assert this app cannot read or write
        // the other app's file.
        assertAccess(file, false /* value is moot */, false /* canRead */,
                false /* canWrite */, false /* checkExists */);
    }

    public static void assertCanAccessMyAppFile(File file)
            throws Exception {
        assertAccess(file, true, true /* canRead */,
                true /*canWrite */, true /* checkExists */);
    }

    private static void assertAccess(File file, boolean exists, boolean canRead, boolean canWrite,
            boolean checkExists) throws Exception {
        if (checkExists) {
            assertThat(file.exists()).isEqualTo(exists);
        }
        assertThat(file.canRead()).isEqualTo(canRead);
        assertThat(file.canWrite()).isEqualTo(canWrite);
        if (file.isDirectory()) {
            if (checkExists) {
                assertThat(file.canExecute()).isEqualTo(exists);
            }
        } else {
            assertThat(file.canExecute()).isFalse(); // Filesytem is mounted with MS_NOEXEC
        }

        // Test some combinations of mask.
        assertAccess(file, R_OK, canRead);
        assertAccess(file, W_OK, canWrite);
        assertAccess(file, R_OK | W_OK, canRead && canWrite);
        assertAccess(file, W_OK | F_OK, canWrite);

        if (checkExists) {
            assertAccess(file, F_OK, exists);
        }
    }

    private static void assertAccess(File file, int mask, boolean expected) throws Exception {
        if (expected) {
            assertThat(Os.access(file.getAbsolutePath(), mask)).isTrue();
        } else {
            assertThrows(ErrnoException.class, () -> Os.access(file.getAbsolutePath(), mask));
        }
    }

    /**
     * Checks that specific files are visible and others are not in a given folder
     */
    public static void assertFileAccess_listFiles(File dir, Set<File> expected,
            Set<File> notExpected) {
        assertThat(dir.isDirectory()).isTrue();
        Set<File> files = Arrays.stream(dir.listFiles()).collect(Collectors.toSet());
        assertThat(files).containsAtLeastElementsIn(expected);
        assertThat(files).containsNoneIn(notExpected);
    }
}
