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

package com.android.car.internal.util;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.io.IOException;

public final class ExceptionUtilsTest {

    @Test
    public void testWrapUnwrapIOException() {
        IOException e = new IOException();
        RuntimeException runtimeException = assertThrows(RuntimeException.class, () -> {
            ExceptionUtils.wrap(e);
        });
        assertThrows(IOException.class, () -> {
            ExceptionUtils.maybeUnwrapIOException(runtimeException);
        });
    }

    @Test
    public void testGetCompleteMessage() {
        IOException inner = new IOException("message1");
        IOException outer = new IOException("message2", inner);
        String msg = ExceptionUtils.getCompleteMessage(outer);
        assertThat(msg).contains("message1");
        assertThat(msg).contains("message2");
    }

    @Test
    public void testGetCompleteMessageWithMsg() {
        IOException inner = new IOException("message1");
        IOException outer = new IOException("message2", inner);
        String msg = ExceptionUtils.getCompleteMessage("mymessage", outer);
        assertThat(msg).contains("mymessage");
        assertThat(msg).contains("message1");
        assertThat(msg).contains("message2");
    }

    @Test
    public void testPropagate() {
        // None RuntimeException, None Error should be thrown as RuntimeException.
        assertThrows(RuntimeException.class, () -> {
            ExceptionUtils.propagate(new IOException());
        });
        // Error should be thrown as error.
        assertThrows(Error.class, () -> {
            ExceptionUtils.propagate(new AssertionError());
        });
        // RuntimeException should be thrown as RuntimeException.
        assertThrows(RuntimeException.class, () -> {
            ExceptionUtils.propagate(new RuntimeException());
        });
    }

    @Test
    public void testGetRootCause() {
        IOException inner = new IOException("message1");
        IOException outer = new IOException("message2", inner);

        assertThat((IOException) ExceptionUtils.getRootCause(outer)).isEqualTo(inner);
    }

    @Test
    public void testAppendCause() {
        IOException inner = new IOException("message1");
        IOException outer = new IOException();

        ExceptionUtils.appendCause(outer, inner);

        assertThat((IOException) ExceptionUtils.getRootCause(outer)).isEqualTo(inner);
    }
}
