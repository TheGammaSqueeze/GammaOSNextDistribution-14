/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.vcard.tests;

import com.android.vcard.exception.VCardVersionException;

import junit.framework.TestCase;

public class VCardVersionExceptionTest extends TestCase {
    static final String TEST_MESSAGE = "message";

    public void testExceptionWithoutMessage() {
        VCardVersionException exception = new VCardVersionException();
        assertNull(exception.getMessage());
    }

    public void testExceptionWithMessage() {
        VCardVersionException exception = new VCardVersionException(TEST_MESSAGE);
        assertEquals(exception.getMessage(), TEST_MESSAGE);
    }
}
