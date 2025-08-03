/*
 * Copyright (C) 2020 Google LLC.
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

package android.linkerconfig.gts;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.linkerconfig.gts.utils.LibraryListLoader;
import android.linkerconfig.gts.utils.LinkerConfigParser;
import android.linkerconfig.gts.utils.elements.Configuration;
import android.linkerconfig.gts.utils.elements.Namespace;
import android.linkerconfig.gts.utils.elements.Section;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.GmsTest;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.INativeDevice;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(DeviceJUnit4ClassRunner.class)
public class LinkerConfigTest extends BaseHostJUnit4Test {

    private static final String LINKER_CONFIG_LOCATION = "/linkerconfig/ld.config.txt";
    private static final String VENDOR_VNDK_LITE = "ro.vndk.lite";
    private static final String VENDOR_VNDK_VERSION = "ro.vndk.version";
    private static final int TARGET_MIN_VER = 30; // linkerconfig is available from R

    private static boolean isValidVersion(ITestDevice device) {
        try {
            return ApiLevelUtil.isAtLeast(device, TARGET_MIN_VER);
        } catch (DeviceNotAvailableException e) {
            fail("There is no available device : " + e.getMessage());
        }

        return false;
    }

    private static Configuration loadConfig(INativeDevice targetDevice, String path) {
        File target = null;

        try {
            target = targetDevice.pullFile(path);
        } catch (DeviceNotAvailableException e) {
            fail("There is no available device : " + e.getMessage());
        }

        assertTrue("Failed to get linker configuration from expected location",
                target.exists());

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(target))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
        } catch (Exception e) {
            fail("Failed to read file " + path + " with error : " + e.getMessage());
        }

        return LinkerConfigParser.parseConfiguration(lines);
    }

    private static void verifyVendorSection(Section section, Set<String> systemAvailableLibraries) {
        List<Namespace> systemNamespaces = section.namespaces.values().stream()
                .filter(ns -> ns.searchPaths.stream()
                        .anyMatch(searchPath -> searchPath.startsWith("/system")
                                && !searchPath.startsWith("/system/vendor")))
                .distinct()
                .collect(Collectors.toList());

        assertFalse("System namespace should not be visible",
                systemNamespaces.parallelStream().anyMatch(ns -> ns.isVisible));

        section.namespaces.values().forEach((ns) -> {
            boolean isVendorNamespace = false;
            for (String libPath : ns.searchPaths) {
                if (libPath.startsWith(("/vendor")) || libPath.startsWith("/odm")) {
                    isVendorNamespace = true;
                    break;
                }
            }

            if (!isVendorNamespace) {
                return;
            }

            assertThat(
                    "Vendor libs and System libs should not exist in same namespace : " + ns.name,
                    systemNamespaces, not(contains(ns)));

            ns.links.values().forEach((link) -> {
                if (systemNamespaces.contains(link.to)) {
                    assertFalse(
                            "It is not allowed to link all shared libs from non-system namespace "
                                    + link.from
                                    + "to system namespace " + link.to,
                            link.allowAll);

                    link.libraries.forEach(library -> {
                        assertTrue("Library " + library + " is not allowed to use",
                                systemAvailableLibraries.contains(library));
                    });
                }
            });
        });
    }


    private static Set<String> loadSystemAvailableLibraries(INativeDevice targetDevice,
            String vendorVndkVersion) {
        Set<String> libraries = new HashSet<>();

        // Add Sanitizer libraries
        libraries.addAll(LibraryListLoader.getLibrariesFromFile(targetDevice,
                "/system/etc/sanitizer.libraries.txt", true));

        // Add LLNDK libraries
        libraries.addAll(LibraryListLoader.getLibrariesFromFile(targetDevice,
                "/apex/com.android.vndk.v" + vendorVndkVersion + "/etc/llndk.libraries."
                        + vendorVndkVersion + ".txt", true));

        // Add Stub libraries
        libraries.addAll(LibraryListLoader.STUB_LIBRARIES);

        // Allowed on userdebug/eng for debugging.
        libraries.add("libfdtrack.so");

        // Add VNDK core variant libraries
        libraries.addAll(LibraryListLoader.getLibrariesFromFile(targetDevice,
                    "/system/etc/vndkcorevariant.libraries.txt", false));

        return libraries;
    }

    @GmsTest(requirement = "GMS-3.5-014")
    @Test
    public void shouldHaveLinkerConfigAtExpectedLocation() {
        ITestDevice targetDevice = getDevice();

        if (!isValidVersion(targetDevice)) {
            return;
        }

        try {
            File linkerConfigFile = targetDevice.pullFile(LINKER_CONFIG_LOCATION);
            assertTrue("Failed to get linker configuration from expected location",
                    linkerConfigFile.exists());
        } catch (DeviceNotAvailableException e) {
            fail("Target device is not available : " + e.getMessage());
        }
    }

    @GmsTest(requirement = "GMS-3.5-014")
    @Test
    public void shouldNotAccessSystemFromVendorExceptVndk() {
        ITestDevice targetDevice = getDevice();
        boolean vndkLiteEnabled = false;

        try {
            vndkLiteEnabled = Boolean.parseBoolean(targetDevice.getProperty(VENDOR_VNDK_LITE));
        } catch (DeviceNotAvailableException e) {
            fail("Target device is not available : " + e.getMessage());
        }

        if (!isValidVersion(targetDevice) || vndkLiteEnabled) {
            return;
        }

        String vendorVndkVersion = "";
        try {
            vendorVndkVersion = targetDevice.getProperty(VENDOR_VNDK_VERSION);
        } catch (DeviceNotAvailableException e) {
            fail("Target device is not available : " + e.getMessage());
        }

        if (vendorVndkVersion == null || vendorVndkVersion.isEmpty()) {
            return;
        }

        Configuration conf = loadConfig(targetDevice, LINKER_CONFIG_LOCATION);

        List<Section> vendorSections = conf.dirToSections.entrySet().stream()
                .filter(item -> item.getKey().startsWith("/vendor") || item.getKey().startsWith(
                        ("/odm")))
                .map(Map.Entry::getValue)
                .distinct()
                .collect(Collectors.toList());

        assertThat("No section for vendor", vendorSections, not(empty()));

        Set<String> availableLibraries = loadSystemAvailableLibraries(targetDevice,
                vendorVndkVersion);

        for (Section section : vendorSections) {
            verifyVendorSection(section, availableLibraries);
        }
    }
}
