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

package android.media.audio.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.media.AudioHalVersionInfo;
import android.platform.test.annotations.AppModeSdkSandbox;

import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@NonMainlineTest
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
@RunWith(AndroidJUnit4.class)
public class AudioHalVersionInfoTest {
    /**
     * Test compareTo() method: smaller index means newer version.
     *
     * @throws Exception
     */
    @Test
    public void testComparator() throws Exception {
        int listSize = AudioHalVersionInfo.VERSIONS.size();
        for (int i = 0; i < listSize - 1; i++) {
            AudioHalVersionInfo current = AudioHalVersionInfo.VERSIONS.get(i);
            AudioHalVersionInfo next = AudioHalVersionInfo.VERSIONS.get(i + 1);
            assertTrue(current.compareTo(next) > 0);
            assertTrue(next.compareTo(current) < 0);
            assertTrue(current.compareTo(current) == 0);
        }

        // Verify compareTo() for the last item in list.
        AudioHalVersionInfo last = AudioHalVersionInfo.VERSIONS.get(listSize - 1);
        assertTrue(last.compareTo(last) == 0);
    }

    /** Validate predefined HAL versions. */
    @Test
    public void test_AIDL_1_0() throws Exception {
        assertEquals(
                AudioHalVersionInfo.AUDIO_HAL_TYPE_AIDL, AudioHalVersionInfo.AIDL_1_0.getHalType());
        assertEquals(1, AudioHalVersionInfo.AIDL_1_0.getMajorVersion());
        assertEquals(0, AudioHalVersionInfo.AIDL_1_0.getMinorVersion());
        assertEquals("AIDL:1.0", AudioHalVersionInfo.AIDL_1_0.toString());
    }

    @Test
    public void test_HIDL_7_1() throws Exception {
        assertEquals(
                AudioHalVersionInfo.AUDIO_HAL_TYPE_HIDL, AudioHalVersionInfo.HIDL_7_1.getHalType());
        assertEquals(7, AudioHalVersionInfo.HIDL_7_1.getMajorVersion());
        assertEquals(1, AudioHalVersionInfo.HIDL_7_1.getMinorVersion());
        assertEquals("HIDL:7.1", AudioHalVersionInfo.HIDL_7_1.toString());
    }

    @Test
    public void test_HIDL_7_0() throws Exception {
        assertEquals(
                AudioHalVersionInfo.AUDIO_HAL_TYPE_HIDL, AudioHalVersionInfo.HIDL_7_0.getHalType());
        assertEquals(7, AudioHalVersionInfo.HIDL_7_0.getMajorVersion());
        assertEquals(0, AudioHalVersionInfo.HIDL_7_0.getMinorVersion());
        assertEquals("HIDL:7.0", AudioHalVersionInfo.HIDL_7_0.toString());
    }

    @Test
    public void test_HIDL_6_0() throws Exception {
        assertEquals(
                AudioHalVersionInfo.AUDIO_HAL_TYPE_HIDL, AudioHalVersionInfo.HIDL_6_0.getHalType());
        assertEquals(6, AudioHalVersionInfo.HIDL_6_0.getMajorVersion());
        assertEquals(0, AudioHalVersionInfo.HIDL_6_0.getMinorVersion());
        assertEquals("HIDL:6.0", AudioHalVersionInfo.HIDL_6_0.toString());
    }

    @Test
    public void test_HIDL_5_0() throws Exception {
        assertEquals(
                AudioHalVersionInfo.AUDIO_HAL_TYPE_HIDL, AudioHalVersionInfo.HIDL_5_0.getHalType());
        assertEquals(5, AudioHalVersionInfo.HIDL_5_0.getMajorVersion());
        assertEquals(0, AudioHalVersionInfo.HIDL_5_0.getMinorVersion());
        assertEquals("HIDL:5.0", AudioHalVersionInfo.HIDL_5_0.toString());
    }

    @Test
    public void test_HIDL_4_0() throws Exception {
        assertEquals(
                AudioHalVersionInfo.AUDIO_HAL_TYPE_HIDL, AudioHalVersionInfo.HIDL_4_0.getHalType());
        assertEquals(4, AudioHalVersionInfo.HIDL_4_0.getMajorVersion());
        assertEquals(0, AudioHalVersionInfo.HIDL_4_0.getMinorVersion());
        assertEquals("HIDL:4.0", AudioHalVersionInfo.HIDL_4_0.toString());
    }

    @Test
    public void test_HIDL_2_0() throws Exception {
        assertEquals(
                AudioHalVersionInfo.AUDIO_HAL_TYPE_HIDL, AudioHalVersionInfo.HIDL_2_0.getHalType());
        assertEquals(2, AudioHalVersionInfo.HIDL_2_0.getMajorVersion());
        assertEquals(0, AudioHalVersionInfo.HIDL_2_0.getMinorVersion());
        assertEquals("HIDL:2.0", AudioHalVersionInfo.HIDL_2_0.toString());
    }

    /**
     * Check VERSIONS list certain all supported HAL versions. This test case needs to be updated
     * every time we introduce a newer HAL version.
     */
    @Test
    public void test_VERSIONS_contains() throws Exception {
        // supported AIDL versions
        assertTrue(AudioHalVersionInfo.VERSIONS.contains(AudioHalVersionInfo.AIDL_1_0));

        // supported HIDL versions
        assertTrue(AudioHalVersionInfo.VERSIONS.contains(AudioHalVersionInfo.HIDL_7_1));
        assertTrue(AudioHalVersionInfo.VERSIONS.contains(AudioHalVersionInfo.HIDL_7_0));
        assertTrue(AudioHalVersionInfo.VERSIONS.contains(AudioHalVersionInfo.HIDL_6_0));
        assertTrue(AudioHalVersionInfo.VERSIONS.contains(AudioHalVersionInfo.HIDL_5_0));
    }

    /**
     * Check VERSIONS list doesn't contain deprecated HAL versions. This test case needs to be
     * updated every time we deprecate an old HAL version.
     */
    @Test
    public void test_VERSIONS_not_contains() throws Exception {
        assertFalse(AudioHalVersionInfo.VERSIONS.contains(AudioHalVersionInfo.HIDL_2_0));
        assertFalse(AudioHalVersionInfo.VERSIONS.contains(AudioHalVersionInfo.HIDL_4_0));
    }
}
