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

package android.credentials.cts.unittests;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.credentials.ClearCredentialStateException;
import android.platform.test.annotations.AppModeFull;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;


@SmallTest
@AppModeFull(reason = "unit test")
@RunWith(AndroidJUnit4.class)
public class ClearCredentialStateExceptionTest {

    @Test
    public void testConstructor_nullType() {
        assertThrows(IllegalArgumentException.class,
                () -> new ClearCredentialStateException(null, null, null));
    }

    @Test
    public void testConstructor_type() {
        final String type = ClearCredentialStateException.TYPE_UNKNOWN;

        final ClearCredentialStateException exc = new ClearCredentialStateException(type);

        assertThat(exc.getType()).isEqualTo(type);
        assertThat(exc.getMessage()).isNull();
        assertThat(exc.getCause()).isNull();
    }

    @Test
    public void testConstructor_type_message() {
        final String type = ClearCredentialStateException.TYPE_UNKNOWN;
        final String message = "message";

        final ClearCredentialStateException exc = new ClearCredentialStateException(type, message);

        assertThat(exc.getType()).isEqualTo(type);
        assertThat(exc.getMessage()).isEqualTo(message);
        assertThat(exc.getCause()).isNull();
    }

    @Test
    public void testConstructor_type_cause() {
        final String type = ClearCredentialStateException.TYPE_UNKNOWN;
        final Throwable cause = new RuntimeException("cause");

        final ClearCredentialStateException exc = new ClearCredentialStateException(type, cause);

        assertThat(exc.getType()).isEqualTo(type);
        assertThat(exc.getMessage()).isNull();
        assertThat(exc.getCause()).isEqualTo(cause);
    }

    @Test
    public void testConstructor_type_message_cause() {
        final String type = ClearCredentialStateException.TYPE_UNKNOWN;
        final String message = "message";
        final Throwable cause = new RuntimeException("cause");

        final ClearCredentialStateException exc = new ClearCredentialStateException(type, message,
                cause);

        assertThat(exc.getType()).isEqualTo(type);
        assertThat(exc.getMessage()).isEqualTo(message);
        assertThat(exc.getCause()).isEqualTo(cause);
    }
}
