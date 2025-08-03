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

package com.android.timezone.location.common;

import static com.android.timezone.location.common.PiiLoggables.NULL_STRING;

import static org.junit.Assert.assertEquals;

import androidx.test.runner.AndroidJUnit4;

import com.android.timezone.location.common.PiiLoggablesTest.TestPiiLoggable;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link PiiLoggable}.
 */
@RunWith(AndroidJUnit4.class)
public class PiiLoggableTest {

    @Test
    public void toPiiString() {
        assertEquals(NULL_STRING, PiiLoggable.toPiiString(null));
        assertEquals("PII", PiiLoggable.toPiiString(new TestPiiLoggable("no PII", "PII")));
    }
}
