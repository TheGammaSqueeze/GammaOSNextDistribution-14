/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.os.cts;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.res.AssetManager;
import android.os.Build;
import android.platform.test.annotations.RestrictedBuildTest;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class BuildVersionTest {

    private static final String LOG_TAG = "BuildVersionTest";
    private static final int EXPECTED_SDK = 34;
    private static final String EXPECTED_BUILD_VARIANT = "user";
    private static final String EXPECTED_KEYS = "release-keys";
    private static final String PLATFORM_RELEASES_FILE = "platform_releases.txt";

    @Test
    @SuppressWarnings("deprecation")
    @RestrictedBuildTest
    public void testReleaseVersion() {
        // Applications may rely on the exact release version
        assertThat("BUILD.VERSION.RELEASE", Build.VERSION.RELEASE, in(getExpectedReleases()));
        if ("REL".equals(Build.VERSION.CODENAME)) {
            assertEquals("BUILD.VERSION.RELEASE_OR_CODENAME", Build.VERSION.RELEASE,
                    Build.VERSION.RELEASE_OR_CODENAME);
        } else {
            assertEquals("BUILD.VERSION.RELEASE_OR_CODENAME", Build.VERSION.CODENAME,
                    Build.VERSION.RELEASE_OR_CODENAME);
        }
        assertEquals("Build.VERSION.SDK", "" + EXPECTED_SDK, Build.VERSION.SDK);
        assertEquals("Build.VERSION.SDK_INT", EXPECTED_SDK, Build.VERSION.SDK_INT);
    }

    @Test
    public void testIncremental() {
        assertThat(Build.VERSION.INCREMENTAL, not(emptyOrNullString()));
    }

    /**
     * Verifies {@link Build#FINGERPRINT} follows expected format:
     * <p/>
     * <code>
     * (BRAND)/(PRODUCT)/(DEVICE):(VERSION.RELEASE_OR_CODENAME)/(BUILD_ID)/
     * (BUILD_NUMBER):(BUILD_VARIANT)/(TAGS)
     * </code>
     */
    @Test
    @RestrictedBuildTest
    public void testBuildFingerprint() {
        String fingerprint = Build.FINGERPRINT;
        Log.i(LOG_TAG, String.format("Testing fingerprint %s", fingerprint));

        verifyFingerprintStructure(fingerprint);

        String[] fingerprintSegs = fingerprint.split("/");
        assertEquals(Build.BRAND, fingerprintSegs[0]);
        assertEquals(Build.PRODUCT, fingerprintSegs[1]);

        String[] devicePlatform = fingerprintSegs[2].split(":");
        assertEquals(2, devicePlatform.length);
        assertEquals(Build.DEVICE, devicePlatform[0]);
        assertEquals(Build.VERSION.RELEASE_OR_CODENAME, devicePlatform[1]);

        assertEquals(Build.ID, fingerprintSegs[3]);

        String[] buildNumberVariant = fingerprintSegs[4].split(":");
        String buildVariant = buildNumberVariant[1];
        assertEquals("Variant", EXPECTED_BUILD_VARIANT, buildVariant);

        List<String> buildTagsList = Arrays.asList(fingerprintSegs[5].split(","));
        assertThat("Keys", buildTagsList, hasItem(EXPECTED_KEYS));
    }

    @Test
    public void testPartitions() {
        List<Build.Partition> partitions = Build.getFingerprintedPartitions();
        Set<String> seenPartitions = new HashSet<>();
        for (Build.Partition partition : partitions) {
            verifyFingerprintStructure(partition.getFingerprint());
            assertTrue(partition.getBuildTimeMillis() > 0);
            boolean unique = seenPartitions.add(partition.getName());
            assertTrue("partitions not unique, " + partition.getName() + " is duplicated", unique);
        }
        assertTrue(seenPartitions.contains(Build.Partition.PARTITION_NAME_SYSTEM));
    }

    private void verifyFingerprintStructure(String fingerprint) {
        assertEquals("Build fingerprint must not include whitespace", -1, fingerprint.indexOf(' '));

        String[] segments = fingerprint.split("/");
        assertEquals("Build fingerprint does not match expected format", 6, segments.length);

        String[] devicePlatform = segments[2].split(":");
        assertEquals(2, devicePlatform.length);

        assertTrue(segments[4].contains(":"));
        String buildVariant = segments[4].split(":")[1];
        assertTrue(buildVariant.length() > 0);
    }

    private Set<String> getExpectedReleases() {
        Set<String> expectedReleases = new HashSet<String>();
        final AssetManager assets =
                InstrumentationRegistry.getInstrumentation().getTargetContext().getAssets();
        String line;
        try (BufferedReader br =
                new BufferedReader(new InputStreamReader(assets.open(PLATFORM_RELEASES_FILE)))) {
            while ((line = br.readLine()) != null) {
                expectedReleases.add(line);
            }
        } catch (IOException e) {
            fail("Could not open file " + PLATFORM_RELEASES_FILE + " to run test");
        }
        return expectedReleases;
    }
}
