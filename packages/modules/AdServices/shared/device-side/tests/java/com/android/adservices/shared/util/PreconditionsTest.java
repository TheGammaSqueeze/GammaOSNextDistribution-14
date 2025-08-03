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

package com.android.adservices.shared.util;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class PreconditionsTest {
    private static final String MESSAGE = "A test message";
    private static final String MESSAGE_TEMPLATE = "Expect a message: %s";

    @Test
    public void testCheckState_success() {
        Preconditions.checkState(true, MESSAGE_TEMPLATE, MESSAGE);
    }

    @Test
    public void testCheckState_failure() {
        Exception e =
                assertThrows(
                        IllegalStateException.class,
                        () -> Preconditions.checkState(false, MESSAGE));
        assertThat(e).hasMessageThat().isEqualTo(MESSAGE);
    }

    @Test
    public void testCheckState_formattedMessage_success() {
        Preconditions.checkState(true, MESSAGE_TEMPLATE, MESSAGE);
    }

    @Test
    public void testCheckState_formattedMessage_failure() {
        Exception e =
                assertThrows(
                        IllegalStateException.class,
                        () -> Preconditions.checkState(false, MESSAGE_TEMPLATE, MESSAGE));
        assertThat(e).hasMessageThat().isEqualTo(String.format(MESSAGE_TEMPLATE, MESSAGE));
    }
}
