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

package com.android.modules.conformanceframework;


import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import com.android.modules.proto.ClasspathClasses.ClasspathClassesDump;
import com.android.modules.proto.ClasspathClasses.ClasspathEntry;
import com.android.modules.proto.ClasspathClasses.Jar;
import com.android.modules.targetprep.ClasspathFetcher;
import com.android.modules.utils.build.testing.DeviceSdkLevel;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;



/**
 * Tests for detecting no duplicate class files are present on BOOTCLASSPATH and
 * SYSTEMSERVERCLASSPATH.
 *
 * <p>Duplicate class files are not safe as some of the jars on *CLASSPATH are updated outside of
 * the main dessert release cycle; they also contribute to unnecessary disk space usage.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class DuplicateClassesTest extends BaseHostJUnit4Test {
    private static ImmutableSet<String> sBootclasspathJars;
    private static ImmutableSet<String> sSystemserverclasspathJars;

    private static ImmutableMultimap<String, String> sJarsToClasses;
    private static String sApexPackage;

    private DeviceSdkLevel mDeviceSdkLevel;

    /**
     * Fetch all classpath info extracted by ClasspathFetcher.
     *
     */
    @BeforeClassWithInfo
    public static void setupOnce(TestInformation testInfo) throws Exception {
        final String dctArtifactsPath = Objects.requireNonNull(
                testInfo.properties().get(ClasspathFetcher.DEVICE_JAR_ARTIFACTS_TAG));
        sApexPackage = testInfo.properties().get(ClasspathFetcher.APEX_PKG_TAG);
        final ImmutableMultimap.Builder<String, String> jarsToClasses =
                new ImmutableMultimap.Builder<>();
        final File bcpDumpFile = new File(dctArtifactsPath, ClasspathFetcher.BCP_CLASSES_FILE);
        final ClasspathClassesDump bcpDump =
                ClasspathClassesDump.parseFrom(new FileInputStream(bcpDumpFile));
        sBootclasspathJars = bcpDump.getEntriesList().stream()
            .map(entry -> entry.getJar().getPath())
            .collect(ImmutableSet.toImmutableSet());
        bcpDump.getEntriesList().stream()
            .forEach(entry -> {
                jarsToClasses.putAll(entry.getJar().getPath(), entry.getClassesList());
            });
        final File sscpDumpFile = new File(dctArtifactsPath, ClasspathFetcher.SSCP_CLASSES_FILE);
        final ClasspathClassesDump sscpDump =
                ClasspathClassesDump.parseFrom(new FileInputStream(sscpDumpFile));
        sSystemserverclasspathJars = sscpDump.getEntriesList().stream()
            .map(entry -> entry.getJar().getPath())
            .collect(ImmutableSet.toImmutableSet());
            sscpDump.getEntriesList().stream()
            .forEach(entry -> {
                jarsToClasses.putAll(entry.getJar().getPath(), entry.getClassesList());
            });
        sJarsToClasses = jarsToClasses.build();
    }

    @Before
    public void setup() {
        mDeviceSdkLevel = new DeviceSdkLevel(getDevice());
    }

    /**
     * Ensure that there are no duplicate classes among jars listed in BOOTCLASSPATH.
     */
    @Test
    public void testBootclasspath_nonDuplicateClasses() throws Exception {
        assumeTrue(mDeviceSdkLevel.isDeviceAtLeastR());
        assertThat(getDuplicateClasses(sBootclasspathJars)).isEmpty();
    }

    /**
     * Ensure that there are no duplicate classes among jars listed in SYSTEMSERVERCLASSPATH.
     */
    @Test
    public void testSystemserverClasspath_nonDuplicateClasses() throws Exception {
        assumeTrue(mDeviceSdkLevel.isDeviceAtLeastR());
        assertThat(getDuplicateClasses(sSystemserverclasspathJars)).isEmpty();
    }

    /**
     * Ensure that there are no duplicate classes among jars listed in BOOTCLASSPATH and
     * SYSTEMSERVERCLASSPATH.
     */
    @Test
    public void testSystemserverAndBootClasspath_nonDuplicateClasses() throws Exception {
        assumeTrue(mDeviceSdkLevel.isDeviceAtLeastR());
        final ImmutableSet.Builder<String> jars = new ImmutableSet.Builder<>();
        jars.addAll(sBootclasspathJars);
        jars.addAll(sSystemserverclasspathJars);
        assertThat(getDuplicateClasses(jars.build())).isEmpty();
    }

    /**
     * Gets the duplicate classes within a list of jar files.
     *
     * @param jars a list of jar files.
     * @return a multimap with the class name as a key and the jar files as a value.
     */
    private Multimap<String, String> getDuplicateClasses(ImmutableCollection<String> jars) {
        final HashMultimap<String, String> allClasses = HashMultimap.create();
        Multimaps.invertFrom(Multimaps.filterKeys(sJarsToClasses, jars::contains), allClasses);
        return Multimaps.filterKeys(allClasses, key -> validDuplicates(allClasses.get(key)));
    }

    /**
     * Filtering function for excluding invalid / uninteresting duplicates.
     *
     * This will filter out classes that are in only 1 jar, or duplicates that
     * do not include jars in the apex under test.
     */

    private boolean validDuplicates(Collection<String> duplicateJars) {
        if (duplicateJars.size() <= 1) {
            return false;
        }
        if (sApexPackage.equals(ClasspathFetcher.PLATFORM_PACKAGE)) {
            return duplicateJars.stream()
                .anyMatch(jar -> !jar.startsWith("/apex"));
        }
        final String apexPrefix = "/apex/" + sApexPackage;
        return duplicateJars.stream()
            .anyMatch(jar -> jar.startsWith(apexPrefix));

    }
}
