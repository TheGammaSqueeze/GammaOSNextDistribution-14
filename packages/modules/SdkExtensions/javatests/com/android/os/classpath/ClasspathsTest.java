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

package com.android.os.classpath;

import static android.compat.testing.Classpaths.ClasspathType.BOOTCLASSPATH;
import static android.compat.testing.Classpaths.ClasspathType.DEX2OATBOOTCLASSPATH;
import static android.compat.testing.Classpaths.ClasspathType.SYSTEMSERVERCLASSPATH;
import static android.compat.testing.Classpaths.getJarsOnClasspath;

import static com.android.os.classpath.ClasspathsTest.ClasspathSubject.assertThat;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.truth.Fact.fact;
import static com.google.common.truth.Fact.simpleFact;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import com.android.modules.utils.build.testing.DeviceSdkLevel;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.Ordered;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.file.Paths;

/** Tests for the contents of *CLASSPATH environ variables on a device. */
@RunWith(DeviceJUnit4ClassRunner.class)
public class ClasspathsTest extends BaseHostJUnit4Test {

    // A selection of jars on *CLASSPATH that cover all categories:
    // - ART apex jar
    // - Non-updatable apex jar
    // - Updatable apex jar
    // - System jar on BOOTCLASSPATH
    // - System jar on SYSTEMSERVERCLASSPATH
    private static final String FRAMEWORK_JAR = "/system/framework/framework.jar";
    private static final String ICU4J_JAR = "/apex/com.android.i18n/javalib/core-icu4j.jar";
    private static final String LIBART_JAR = "/apex/com.android.art/javalib/core-libart.jar";
    private static final String SDKEXTENSIONS_JAR =
            "/apex/com.android.sdkext/javalib/framework-sdkextensions.jar";
    private static final String SERVICES_JAR = "/system/framework/services.jar";

    @Before
    public void before() throws Exception {
        ITestDevice device = getDevice();
        DeviceSdkLevel deviceSdkLevel = new DeviceSdkLevel(device);
        assumeTrue(deviceSdkLevel.isDeviceAtLeastS());
    }

    @Test
    public void testBootclasspath() throws Exception {
        ImmutableList<String> jars = getJarsOnClasspath(getDevice(), BOOTCLASSPATH);

        assertThat(jars).containsNoDuplicates();

        assertThat(jars)
                .containsAtLeast(LIBART_JAR, FRAMEWORK_JAR, ICU4J_JAR, SDKEXTENSIONS_JAR)
                .inOrder();
        assertThat(jars).doesNotContain(SERVICES_JAR);

        ImmutableList<String> expectedPrefixes =
                ImmutableList.of(
                        "/apex/com.android.art/",
                        "/system/",
                        "/system_ext/",
                        "/apex/com.android.i18n/",
                        "/apex/");
        assertThat(jars).prefixesMatch(expectedPrefixes).inOrder();

        assertThat(getUpdatableApexes(jars)).isInOrder();
    }

    @Test
    public void testDex2oatBootclasspath() throws Exception {
        ImmutableList<String> jars = getJarsOnClasspath(getDevice(), DEX2OATBOOTCLASSPATH);

        assertThat(jars).containsNoDuplicates();

        // DEX2OATBOOTCLASSPATH must only contain ART, core-icu4j, and platform system jars
        assertThat(jars).containsAtLeast(LIBART_JAR, FRAMEWORK_JAR, ICU4J_JAR).inOrder();
        assertThat(jars).containsNoneOf(SDKEXTENSIONS_JAR, SERVICES_JAR);

        // DEX2OATBOOTCLASSPATH must be a subset of BOOTCLASSPATH
        ImmutableList<String> bootJars = getJarsOnClasspath(getDevice(), BOOTCLASSPATH);
        assertThat(bootJars).containsAtLeastElementsIn(jars);

        ImmutableList<String> expectedPrefixes =
                ImmutableList.of(
                        "/apex/com.android.art/",
                        "/system/",
                        "/system_ext/",
                        "/apex/com.android.i18n/");
        assertThat(jars).prefixesMatch(expectedPrefixes).inOrder();

        // No updatable jars on DEX2OATBOOTCLASSPATH
        assertThat(getUpdatableApexes(jars)).isEmpty();
    }

    @Test
    public void testSystemServerClasspath() throws Exception {
        ImmutableList<String> jars = getJarsOnClasspath(getDevice(), SYSTEMSERVERCLASSPATH);

        assertThat(jars).containsNoDuplicates();

        assertThat(jars).containsNoneOf(LIBART_JAR, FRAMEWORK_JAR, ICU4J_JAR, SDKEXTENSIONS_JAR);
        assertThat(jars).contains(SERVICES_JAR);

        ImmutableList<String> expectedPrefixes =
                ImmutableList.of("/system/", "/system_ext/", "/apex/");
        assertThat(jars).prefixesMatch(expectedPrefixes).inOrder();

        assertThat(getUpdatableApexes(jars)).isInOrder();
    }

    @Test
    public void testDex2oatJarsAreFirstOnBootclasspath() throws Exception {
        ImmutableList<String> bootJars = getJarsOnClasspath(getDevice(), BOOTCLASSPATH);
        ImmutableList<String> dex2oatJars = getJarsOnClasspath(getDevice(), DEX2OATBOOTCLASSPATH);

        // All preopt jars on BOOTCLASSPATH must come before updatable jars.
        assertThat(bootJars).startsWith(dex2oatJars);
    }

    /**
     * Returns a derived subject with names of the updatable APEXes preserving the original order.
     */
    private static ImmutableList<String> getUpdatableApexes(ImmutableList<String> jars) {
        return jars.stream()
                .filter(jar -> jar.startsWith("/apex"))
                // ICU4J_JAR is the last non-updatable APEX jar, i.e. everything after is
                // considered to be an updatable APEX jar
                .dropWhile(jar -> !jar.equals(ICU4J_JAR))
                .skip(1)
                // Map to APEX name from "/apex/<name>/javalibs/foo.jar"
                .map(jar -> Paths.get(jar).getName(1).toString())
                .collect(ImmutableList.toImmutableList());
    }

    static final class ClasspathSubject extends IterableSubject {

        private static final Ordered EMPTY_ORDERED = () -> {};

        private final ImmutableList<String> actual;

        protected ClasspathSubject(FailureMetadata metadata, ImmutableList<String> iterable) {
            super(metadata, iterable);
            actual = iterable;
        }

        public static ClasspathSubject assertThat(ImmutableList<String> jars) {
            return assertAbout(ClasspathSubject::new).that(jars);
        }

        /**
         * Checks that the actual iterable contains only jars that start with the expected prefixes
         * or fails.
         *
         * <p>To also test that the prefixes appear in the given order, make a call to {@code
         * inOrder} on the object returned by this method. The expected elements must appear in the
         * given order within the actual elements.
         */
        public Ordered prefixesMatch(ImmutableList<String> expected) {
            checkArgument(
                    expected.stream().distinct().count() == expected.size(),
                    "No duplicates are allowed in expected values.");

            ImmutableList.Builder<String> unexpectedJars = ImmutableList.builder();
            boolean ordered = true;
            int currentPrefixIndex = expected.isEmpty() ? -1 : 0;
            for (String jar : actual) {
                int prefixIndex = findFirstMatchingPrefix(jar, expected);
                if (prefixIndex == -1) {
                    unexpectedJars.add(jar);
                    continue;
                }
                if (prefixIndex != currentPrefixIndex) {
                    if (prefixIndex < currentPrefixIndex) {
                        ordered = false;
                    }
                    currentPrefixIndex = prefixIndex;
                }
            }

            ImmutableList<String> unexpected = unexpectedJars.build();
            if (!unexpected.isEmpty()) {
                Fact expectedOrder =
                        fact("expected jar filepaths to be prefixes with one of", expected);
                ImmutableList.Builder<Fact> facts = ImmutableList.builder();
                for (String e : unexpected) {
                    facts.add(fact("unexpected", e));
                }
                facts.add(simpleFact("---"));
                facts.add(simpleFact(""));
                failWithoutActual(expectedOrder, facts.build().toArray(new Fact[0]));
                return EMPTY_ORDERED;
            }

            if (ordered) {
                return EMPTY_ORDERED;
            }

            return () ->
                    failWithActual(
                            simpleFact("all jars have valid partitions, but the order was wrong"),
                            fact("expected order", expected));
        }

        /** Checks that the actual iterable starts with expected elements. */
        public void startsWith(ImmutableList<String> expected) {
            if (actual.size() < expected.size()) {
                failWithActual("expected at least number of elements", expected.size());
                return;
            }
            assertWithMessage("Unexpected initial elements of the list")
                    .that(actual.subList(0, expected.size()))
                    .isEqualTo(expected);
        }

        private static int findFirstMatchingPrefix(String value, ImmutableList<String> prefixes) {
            for (int i = 0; i < prefixes.size(); i++) {
                if (value.startsWith(prefixes.get(i))) {
                    return i;
                }
            }
            return -1;
        }
    }
}
