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

package android.net.thread.cts;

import static android.net.thread.ThreadNetworkException.ERROR_INTERNAL_ERROR;
import static android.net.thread.ThreadNetworkException.ERROR_THREAD_DISABLED;
import static android.net.thread.ThreadNetworkException.ERROR_UNKNOWN;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.net.thread.ThreadNetworkException;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/** CTS tests for {@link ThreadNetworkException}. */
@SmallTest
@RunWith(AndroidJUnit4.class)
public final class ThreadNetworkExceptionTest {
    @Test
    public void constructor_validValues_valuesAreConnectlySet() throws Exception {
        ThreadNetworkException errorThreadDisabled =
                new ThreadNetworkException(ERROR_THREAD_DISABLED, "Thread disabled error!");
        ThreadNetworkException errorInternalError =
                new ThreadNetworkException(ERROR_INTERNAL_ERROR, "internal error!");

        assertThat(errorThreadDisabled.getErrorCode()).isEqualTo(ERROR_THREAD_DISABLED);
        assertThat(errorThreadDisabled.getMessage()).isEqualTo("Thread disabled error!");
        assertThat(errorInternalError.getErrorCode()).isEqualTo(ERROR_INTERNAL_ERROR);
        assertThat(errorInternalError.getMessage()).isEqualTo("internal error!");
    }

    @Test
    public void constructor_nullMessage_throwsNullPointerException() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> new ThreadNetworkException(ERROR_UNKNOWN, null /* message */));
    }

    @Test
    public void constructor_tooSmallErrorCode_throwsIllegalArgumentException() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new ThreadNetworkException(0, "0"));
        // TODO: add argument check for too large error code when mainline CTS is ready. This was
        // not added here for CTS forward copatibility.
    }
}
