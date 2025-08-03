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

package android.mediav2.cts;

import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class MediaFormatUnitTest {
    static final int PER_TEST_TIMEOUT_MS = 10000;

    static {
        System.loadLibrary("ctsmediav2utils_jni");
    }

    @Rule
    public Timeout timeout = new Timeout(PER_TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

    @ApiTest(apis = {"AMediaFormat_getInt32", "AMediaFormat_setInt32",
            "AMediaFormat_copy", "AMediaFormat_clear", "AMediaFormat_toString",
            "AMediaFormat_new", "AMediaFormat_delete"})
    @Test
    public void testMediaFormatNativeInt32() {
        assertTrue(nativeTestMediaFormatInt32());
    }

    private native boolean nativeTestMediaFormatInt32();

    @ApiTest(apis = {"AMediaFormat_getInt64", "AMediaFormat_setInt64",
            "AMediaFormat_copy", "AMediaFormat_clear", "AMediaFormat_toString",
            "AMediaFormat_new", "AMediaFormat_delete"})
    @Test
    public void testMediaFormatNativeInt64() {
        assertTrue(nativeTestMediaFormatInt64());
    }

    private native boolean nativeTestMediaFormatInt64();

    @ApiTest(apis = {"AMediaFormat_getFloat", "AMediaFormat_setFloat",
            "AMediaFormat_copy", "AMediaFormat_clear", "AMediaFormat_toString",
            "AMediaFormat_new", "AMediaFormat_delete"})
    @Test
    public void testMediaFormatNativeFloat() {
        assertTrue(nativeTestMediaFormatFloat());
    }

    private native boolean nativeTestMediaFormatFloat();

    @ApiTest(apis = {"AMediaFormat_getDouble", "AMediaFormat_setDouble",
            "AMediaFormat_copy", "AMediaFormat_clear", "AMediaFormat_toString",
            "AMediaFormat_new", "AMediaFormat_delete"})
    @Test
    public void testMediaFormatNativeDouble() {
        assertTrue(nativeTestMediaFormatDouble());
    }

    private native boolean nativeTestMediaFormatDouble();

    @ApiTest(apis = {"AMediaFormat_getSize", "AMediaFormat_setSize",
            "AMediaFormat_copy", "AMediaFormat_clear", "AMediaFormat_toString",
            "AMediaFormat_new", "AMediaFormat_delete"})
    @Test
    public void testMediaFormatNativeSize() {
        assertTrue(nativeTestMediaFormatSize());
    }

    private native boolean nativeTestMediaFormatSize();

    @ApiTest(apis = {"AMediaFormat_getString", "AMediaFormat_setString",
            "AMediaFormat_copy", "AMediaFormat_clear", "AMediaFormat_toString",
            "AMediaFormat_new", "AMediaFormat_delete"})
    @Test
    public void testMediaFormatNativeString() {
        assertTrue(nativeTestMediaFormatString());
    }

    private native boolean nativeTestMediaFormatString();

    @ApiTest(apis = {"AMediaFormat_getRect", "AMediaFormat_setRect",
            "AMediaFormat_copy", "AMediaFormat_clear", "AMediaFormat_toString",
            "AMediaFormat_new", "AMediaFormat_delete"})
    @Test
    public void testMediaFormatNativeRect() {
        assertTrue(nativeTestMediaFormatRect());
    }

    private native boolean nativeTestMediaFormatRect();

    @ApiTest(apis = {"AMediaFormat_getBuffer", "AMediaFormat_setBuffer",
            "AMediaFormat_copy", "AMediaFormat_clear", "AMediaFormat_toString",
            "AMediaFormat_new", "AMediaFormat_delete"})
    @Test
    public void testMediaFormatNativeBuffer() {
        assertTrue(nativeTestMediaFormatBuffer());
    }

    private native boolean nativeTestMediaFormatBuffer();

    @ApiTest(apis = {"AMediaFormat_getInt32", "AMediaFormat_setInt32",
            "AMediaFormat_getInt64", "AMediaFormat_setInt64",
            "AMediaFormat_getFloat", "AMediaFormat_setFloat",
            "AMediaFormat_getDouble", "AMediaFormat_setDouble",
            "AMediaFormat_getString", "AMediaFormat_setString",
            "AMediaFormat_getSize", "AMediaFormat_setSize",
            "AMediaFormat_getRect", "AMediaFormat_setRect",
            "AMediaFormat_getBuffer", "AMediaFormat_setBuffer",
            "AMediaFormat_copy", "AMediaFormat_clear", "AMediaFormat_toString",
            "AMediaFormat_new", "AMediaFormat_delete"})
    @Test
    public void testMediaFormatNativeAll() {
        assertTrue(nativeTestMediaFormatAll());
    }

    private native boolean nativeTestMediaFormatAll();
}
