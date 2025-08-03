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

package android.net.wifi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.os.Parcel;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Test;

public class MscsParamsTest {
    private static final int TEST_FRAME_CLASSIFIER_FIELDS =
            MscsParams.FRAME_CLASSIFIER_IP_VERSION | MscsParams.FRAME_CLASSIFIER_SRC_PORT;
    private static final int TEST_USER_PRIORITY_BITMAP = 1 << 7;
    private static final int TEST_USER_PRIORITY_LIMIT = 5;
    private static final int TEST_STREAM_TIMEOUT_US = 5550;

    @Before
    public void setUp() {
        assumeTrue(SdkLevel.isAtLeastV());
    }

    /** Create an MscsParams object with all fields set to the test values. */
    private MscsParams createTestMscsParams() {
        return new MscsParams.Builder()
                .setFrameClassifierFields(TEST_FRAME_CLASSIFIER_FIELDS)
                .setUserPriorityBitmap(TEST_USER_PRIORITY_BITMAP)
                .setUserPriorityLimit(TEST_USER_PRIORITY_LIMIT)
                .setStreamTimeoutUs(TEST_STREAM_TIMEOUT_US)
                .build();
    }

    /** Test that an exception is thrown when invalid values are provided to the builder. */
    @Test
    public void testBuilderInvalid() {
        MscsParams.Builder builder = new MscsParams.Builder();

        // Bitmap arguments can only use bits 0 - 7.
        assertThrows(IllegalArgumentException.class,
                () -> builder.setFrameClassifierFields(1 << 8));
        assertThrows(IllegalArgumentException.class,
                () -> builder.setUserPriorityBitmap(1 << 8));

        // User priority limit must be between 0 - 7 (inclusive)
        assertThrows(IllegalArgumentException.class,
                () -> builder.setUserPriorityLimit(-1));
        assertThrows(IllegalArgumentException.class,
                () -> builder.setUserPriorityLimit(8));

        // Stream timeout value must be between 0 - 60 seconds.
        assertThrows(IllegalArgumentException.class,
                () -> builder.setStreamTimeoutUs(-1));
        assertThrows(IllegalArgumentException.class,
                () -> builder.setStreamTimeoutUs(MscsParams.MAX_STREAM_TIMEOUT_US + 1));
    }

    /**
     * Tests that the builder works as expected when provided valid values.
     * Fields that are unset should be assigned their default value.
     */
    @Test
    public void testBuilderValid() {
        MscsParams params = new MscsParams.Builder()
                .setFrameClassifierFields(TEST_FRAME_CLASSIFIER_FIELDS)
                .setUserPriorityLimit(TEST_USER_PRIORITY_LIMIT)
                .build();
        assertEquals(TEST_FRAME_CLASSIFIER_FIELDS, params.getFrameClassifierFields());
        assertEquals(TEST_USER_PRIORITY_LIMIT, params.getUserPriorityLimit());

        // Fields that were not explicitly assigned should be given a default value.
        assertEquals(MscsParams.DEFAULT_USER_PRIORITY_BITMAP, params.getUserPriorityBitmap());
        assertEquals(MscsParams.MAX_STREAM_TIMEOUT_US, params.getStreamTimeoutUs());
    }

    /**
     * Tests that all fields are assigned a default value if they are not explicitly
     * assigned in the builder.
     */
    @Test
    public void testBuilderDefaultValues() {
        MscsParams params = new MscsParams.Builder().build();
        assertEquals(MscsParams.DEFAULT_FRAME_CLASSIFIER_FIELDS, params.getFrameClassifierFields());
        assertEquals(MscsParams.DEFAULT_USER_PRIORITY_BITMAP, params.getUserPriorityBitmap());
        assertEquals(MscsParams.DEFAULT_USER_PRIORITY_LIMIT, params.getUserPriorityLimit());
        assertEquals(MscsParams.MAX_STREAM_TIMEOUT_US, params.getStreamTimeoutUs());
    }

    /** Tests that this class can be properly parceled and unparceled. */
    @Test
    public void testParcelReadWrite() {
        MscsParams params = createTestMscsParams();
        Parcel parcel = Parcel.obtain();
        params.writeToParcel(parcel, 0);
        parcel.setDataPosition(0); // Rewind data position back to the beginning for read.
        MscsParams unparceledParams = MscsParams.CREATOR.createFromParcel(parcel);
        assertTrue(unparceledParams.equals(params));
    }

    /** Tests the equality and hashcode operations on equivalent instances. */
    @Test
    public void testSameObjectComparison() {
        MscsParams params1 = createTestMscsParams();
        MscsParams params2 = createTestMscsParams();
        assertTrue(params1.equals(params2));
        assertEquals(params1.hashCode(), params2.hashCode());
    }

    /** Tests the equality and hashcode operations on different instances. */
    @Test
    public void testDifferentObjectComparison() {
        MscsParams testParams = createTestMscsParams();
        MscsParams defaultParams = new MscsParams.Builder().build();
        assertFalse(testParams.equals(defaultParams));
        assertNotEquals(testParams.hashCode(), defaultParams.hashCode());
    }
}
