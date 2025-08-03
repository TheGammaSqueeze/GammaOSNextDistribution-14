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

package android.adservices.cts;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.exceptions.AdServicesException;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AdServicesExceptionCtsTest {
    @Test
    public void testAdServicesExceptionWithMessageCreation() {
        String expectedErrorMessage = "Expected error message";

        AdServicesException adServicesException = new AdServicesException(expectedErrorMessage);

        assertThat(adServicesException).hasMessageThat().isEqualTo(expectedErrorMessage);
    }

    @Test
    public void testAdServicesExceptionWithCauseAndMessageCreation() {
        String expectedErrorMessage = "Expected error message";
        String innerExpectedErrorMessage = "Inner expected error message";
        IllegalStateException cause = new IllegalStateException(innerExpectedErrorMessage);

        AdServicesException adServicesException =
                new AdServicesException(expectedErrorMessage, cause);

        assertThat(adServicesException).hasMessageThat().isEqualTo(expectedErrorMessage);
        assertThat(adServicesException).hasCauseThat().isSameInstanceAs(cause);
        assertThat(adServicesException.getCause())
                .hasMessageThat()
                .isEqualTo(innerExpectedErrorMessage);
    }
}
