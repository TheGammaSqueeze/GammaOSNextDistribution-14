/*
 * Copyright (C) 2008 The Android Open Source Project
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
package android.database.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.database.CharArrayBuffer;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CharArrayBufferTest {
    @Test
    public void testCharArrayBuffer() {
        CharArrayBuffer charArrayBuffer;

        charArrayBuffer = new CharArrayBuffer(0);
        assertEquals(0, charArrayBuffer.data.length);
        charArrayBuffer.data = new char[100];
        assertEquals(100, charArrayBuffer.data.length);

        assertEquals(100, (new CharArrayBuffer(100)).data.length);

        assertNull((new CharArrayBuffer(null)).data);

        char[] expectedData = new char[100];
        assertSame(expectedData, (new CharArrayBuffer(expectedData)).data);
    }
}
