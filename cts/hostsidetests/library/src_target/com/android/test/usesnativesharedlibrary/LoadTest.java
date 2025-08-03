/*
 * Copyright 2020 The Android Open Source Project
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

package com.android.test.usesnativesharedlibrary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.hamcrest.core.Is.is;

import android.os.Build;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.PropertyUtil;

import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
/**
 * Tests if native shared libs are loadable or un-loadable as expected. The list of loadable libs is
 * in the asset file <code>available.txt</code> and the list of un-loadable libs is in the asset
 * file <code>unavailable.txt</code>. The files are dynamically created by the host-side test
 * <code>UsesNativeLibraryTestCase</code>.
 */
@RunWith(JUnit4.class)
public class LoadTest {
    private List<String> libNamesFromAssetFile(String filename) {
        List<String> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                ApplicationProvider.getApplicationContext().getAssets().open(filename)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty() && line.startsWith("lib") && line.endsWith(".so")) {
                    // libfoo.so -> foo because that's what System.loadLibrary accepts
                    result.add(line.substring(3, line.length()-3));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    private Set<String> vendorPublicLibraries() {
        try (Stream<String> lines = Files.lines(Paths.get("/vendor/etc/public.libraries.txt"))) {
            return lines.
                filter(line -> {
                    // filter-out empty lines or comment lines that start with #
                    String strip = line.trim();
                    return !strip.isEmpty() && !strip.startsWith("#");
                }).
                // line format is "name [bitness]". Extract the name part.
                map(line -> line.trim().split("\\s+")[0]).
                collect(Collectors.toSet());
        } catch (IOException e) {
            return Collections.emptySet();
        }
    }

    /**
     * Tests if libs listed in available.txt are all loadable
     */
    @CddTest(requirement="3.6/C-1-1,C-1-2")
    @Test
    public void testAvailableLibrariesAreLoaded() {
        List<String> unexpected = new ArrayList<>();
        for (String lib : libNamesFromAssetFile("available.txt")) {
            try {
                System.loadLibrary(lib);
            } catch (Throwable t) {
                if (!PropertyUtil.isVndkApiLevelNewerThan(Build.VERSION_CODES.R)) {
                    // Some old vendor.img might have stable entries in ./etc/public.libraries.txt
                    // Don't emit error in that case.
                    String libName = "lib" + lib + ".so";
                    boolean notFound = t.getMessage().equals("dlopen failed: library \"" + libName
                            + "\" not found");
                    boolean isVendorPublicLib = vendorPublicLibraries().contains(libName);
                    if (isVendorPublicLib && notFound) {
                        continue;
                    }
                }
                unexpected.add(t.getMessage());
            }
        };
        assertThat("Some libraries failed to load. Libraries shown below are listed in " +
                "/vendor/public.libraries.txt or /system/etc/public.libraries-COMPANYNAME.txt " +
                "as public libraries, but they may not exist or inaccessible. " +
                "You may fix this by unlisting them from the txt files"
                , unexpected, is(Collections.emptyList()));
    }

    /**
     * Tests if libs listed in unavailable.txt are all non-loadable
     */
    @CddTest(requirement="3.6/C-1-1,C-1-2")
    @Test
    public void testUnavailableLibrariesAreNotLoaded() {
        List<String> loadedLibs = new ArrayList<>();
        List<String> unexpectedFailures = new ArrayList<>();
        for (String lib : libNamesFromAssetFile("unavailable.txt")) {
            try {
                System.loadLibrary(lib);
                loadedLibs.add("lib" + lib + ".so");
            } catch (UnsatisfiedLinkError e) {
                // This is expected
            } catch (Throwable t) {
                unexpectedFailures.add(t.getMessage());
            }
        };
        assertThat("Some unavailable libraries were loaded", loadedLibs, is(Collections.emptyList()));
        assertThat("Unexpected errors occurred", unexpectedFailures, is(Collections.emptyList()));
    }
}
