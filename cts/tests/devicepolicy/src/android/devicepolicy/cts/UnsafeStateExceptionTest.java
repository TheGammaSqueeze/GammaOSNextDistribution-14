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
package android.devicepolicy.cts;

import static android.app.admin.DevicePolicyManager.OPERATION_SAFETY_REASON_DRIVING_DISTRACTION;
import static android.app.admin.DevicePolicyManager.OPERATION_SAFETY_REASON_NONE;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.testng.Assert.assertThrows;

import android.app.admin.UnsafeStateException;

import com.android.bedstead.harrier.BedsteadJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

// TODO(b/174859111): move to automotive-specific section
@RunWith(BedsteadJUnit4.class)
public final class UnsafeStateExceptionTest {

    private static final int VALID_OPERATION = Integer.MAX_VALUE; // Value doesn't really matter...

    @Test
    public void testValidReason_drivingDistraction() {
        assertExceptionWithValidReason(OPERATION_SAFETY_REASON_DRIVING_DISTRACTION);
    }

    @Test
    public void testInvalidReason_none() {
        assertExceptionWithInvalidReason(OPERATION_SAFETY_REASON_NONE);
    }

    @Test
    public void testInvalidReason_arbitrary() {
        assertExceptionWithInvalidReason(0);
        assertExceptionWithInvalidReason(42);
        assertExceptionWithInvalidReason(108);
        assertExceptionWithInvalidReason(Integer.MIN_VALUE);
        assertExceptionWithInvalidReason(Integer.MAX_VALUE);
    }

    private void assertExceptionWithValidReason(int reason) {
        UnsafeStateException exception = new UnsafeStateException(VALID_OPERATION, reason);

        assertWithMessage("operation").that(exception.getOperation()).isEqualTo(VALID_OPERATION);
        assertWithMessage("reasons").that(exception.getReasons()).containsExactly(reason);
    }

    private void assertExceptionWithInvalidReason(int reason) {
        assertThrows(IllegalArgumentException.class,
                () -> new UnsafeStateException(VALID_OPERATION, reason));
    }
}
