/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.net.thread;

import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link ThreadNetworkException} to cover what is not covered in CTS tests. */
@SmallTest
@RunWith(AndroidJUnit4.class)
public final class ThreadNetworkExceptionTest {
    @Test
    public void constructor_tooLargeErrorCode_throwsIllegalArgumentException() throws Exception {
        // TODO (b/323791003): move this test case to cts/ThreadNetworkExceptionTest when mainline
        // CTS is ready.
        assertThrows(IllegalArgumentException.class, () -> new ThreadNetworkException(13, "13"));
    }
}
