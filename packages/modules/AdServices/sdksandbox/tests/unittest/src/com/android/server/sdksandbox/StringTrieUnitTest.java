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

package com.android.server.sdksandbox.verifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StringTrieUnitTest {
    private StringTrie<String> mTrie = null;
    private static final String STORED_VAL_A = "Some API rule A";
    private static final String STORED_VAL_B = "Some API rule B";
    private static final String STORED_VAL_C = "Some API rule C";

    @Before
    public void setUp() throws Exception {
        mTrie = new StringTrie<String>();
    }

    @Test
    public void testString_exists() {
        mTrie.put(STORED_VAL_A, "Landroid", "app", "sdksandbox");

        assertEquals(STORED_VAL_A, mTrie.retrieve("Landroid", "app", "sdksandbox"));
    }

    @Test
    public void testString_null() {
        mTrie.put(STORED_VAL_A, "Landroid", "app", "sdksandbox");

        assertNull(mTrie.retrieve("Landroid", "app", "ActivityManager"));
    }

    @Test
    public void testCaseSensitive_null() {
        mTrie.put(STORED_VAL_A, "Landroid", "app", "sdksandbox");

        assertNull(mTrie.retrieve("Landroid", "app", "SdkSandbox"));
    }

    @Test
    public void testString_multiChild() {
        mTrie.put(STORED_VAL_A, "Landroid", "app", "sdksandbox");
        mTrie.put(STORED_VAL_B, "Landroid", "app", "ActivityManager");
        mTrie.put(STORED_VAL_C, "Landroid", "content");

        assertEquals(STORED_VAL_A, mTrie.retrieve("Landroid", "app", "sdksandbox"));
        assertEquals(STORED_VAL_B, mTrie.retrieve("Landroid", "app", "ActivityManager"));
        assertEquals(STORED_VAL_C, mTrie.retrieve("Landroid", "content"));

        assertNull(mTrie.retrieve("Ljava"));
        assertNull(mTrie.retrieve("Landroid", "content", "Context"));
        assertNull(mTrie.retrieve("Landroid"));
    }

    @Test
    public void testString_innerKeyValue() {
        mTrie.put(STORED_VAL_A, "Landroid", "app", "sdksandbox");
        mTrie.put(STORED_VAL_B, "Landroid");

        assertEquals(STORED_VAL_A, mTrie.retrieve("Landroid", "app", "sdksandbox"));
        assertEquals(STORED_VAL_B, mTrie.retrieve("Landroid"));

        assertNull(mTrie.retrieve("Ljava"));
        assertNull(mTrie.retrieve("Landroid", "app", "Context"));
    }

    @Test
    public void testWildcard() {
        mTrie.put(STORED_VAL_A, "Landroid", "app", "sdksandbox", null);
        mTrie.put(STORED_VAL_B, "Landroid", "content", null);
        mTrie.put(STORED_VAL_C, "Ljava", null);

        assertEquals(
                STORED_VAL_A, mTrie.retrieve("Landroid", "app", "sdksandbox", "LoadSdkException"));
        assertEquals(STORED_VAL_B, mTrie.retrieve("Landroid", "content", "pm", "PackageManager"));
        assertEquals(STORED_VAL_C, mTrie.retrieve("Ljava", "io", "File"));

        assertNull(mTrie.retrieve("Landroid", "net"));
        assertNull(mTrie.retrieve("Landroid", "app", "KeyguardManager"));
    }

    @Test
    public void testWildcard_literalPrecedence() {
        // Put values before and after the wildcard to check for ordering effects
        mTrie.put(STORED_VAL_A, "Landroid", "app");
        mTrie.put(STORED_VAL_B, "Landroid", null);
        mTrie.put(STORED_VAL_C, "Landroid", "content", "pm");

        assertEquals(STORED_VAL_A, mTrie.retrieve("Landroid", "app"));
        assertEquals(STORED_VAL_B, mTrie.retrieve("Landroid", "net"));
        assertEquals(STORED_VAL_C, mTrie.retrieve("Landroid", "content", "pm"));
    }

    @Test
    public void testBacktrack_succeeds() {
        // Bactracks in cases where a specific match fails later,
        // and a wildcard match could be used instead
        mTrie.put(STORED_VAL_A, "Landroid", "content", null);
        mTrie.put(STORED_VAL_B, "Landroid", "content", "Context", "getDisplay");

        assertNull(mTrie.retrieve("Landroid", "content"));
        assertEquals(STORED_VAL_A, mTrie.retrieve("Landroid", "content", "pm"));
        assertEquals(STORED_VAL_A, mTrie.retrieve("Landroid", "content", "Context"));
        assertEquals(STORED_VAL_A, mTrie.retrieve("Landroid", "content", "Context", "getDrawable"));
        assertEquals(STORED_VAL_B, mTrie.retrieve("Landroid", "content", "Context", "getDisplay"));
    }

    @Test
    public void testWildcard_noCapture() throws NullPointerException {
        mTrie.put(STORED_VAL_A, "Landroid", null);
        String[] key = new String[] {"Landroid", "app", "sdksandbox"};

        mTrie.retrieve(key);
        mTrie.retrieve(null, key);
        // test passes if no exceptions were thrown
    }

    @Test
    public void testWildcard_captureTail() {
        mTrie.put(STORED_VAL_A, "Landroid", "app", null);
        List<String> captures = new ArrayList<String>();

        assertNull(mTrie.retrieve(captures, "Landroid", "app"));
        assertEquals(0, captures.size());
        assertEquals(Arrays.asList(), captures);

        assertEquals(STORED_VAL_A, mTrie.retrieve(captures, "Landroid", "app", "sdksandbox"));
        assertEquals(1, captures.size());
        assertEquals(Arrays.asList("sdksandbox"), captures);

        assertEquals(
                STORED_VAL_A,
                mTrie.retrieve(captures, "Landroid", "app", "sdksandbox", "SdkSandboxManager"));
        assertEquals(2, captures.size());
        assertEquals(Arrays.asList("sdksandbox", "SdkSandboxManager"), captures);

        assertEquals(
                STORED_VAL_A,
                mTrie.retrieve(
                        captures,
                        "Landroid",
                        "app",
                        "sdksandbox",
                        "sdkprovider",
                        "SdkSandboxActivityHandler"));
        assertEquals(3, captures.size());
        assertEquals(
                Arrays.asList("sdksandbox", "sdkprovider", "SdkSandboxActivityHandler"), captures);
    }

    @Test
    public void testWildcard_innerMatch() {
        mTrie.put(STORED_VAL_A, "Landroid", null, "Display", "getDisplayMetrics");

        assertNull(mTrie.retrieve("Landroid", "app"));

        assertNull(mTrie.retrieve("Landroid", "content", "Display", "getWidth"));

        assertEquals(
                STORED_VAL_A,
                mTrie.retrieve("Landroid", "graphics", "Display", "getDisplayMetrics"));

        assertEquals(
                STORED_VAL_A,
                mTrie.retrieve("Landroid", "content", "content", "Display", "getDisplayMetrics"));
    }

    @Test
    public void testWildcard_captureInner() {
        mTrie.put(STORED_VAL_A, "Landroid", null, "getDisplay");
        List<String> captures = new ArrayList<String>();

        assertNull(mTrie.retrieve(captures, "Landroid", "app"));
        assertEquals(1, captures.size());
        assertEquals(Arrays.asList("app"), captures);

        assertEquals(STORED_VAL_A, mTrie.retrieve(captures, "Landroid", "content", "getDisplay"));
        assertEquals(1, captures.size());
        assertEquals(Arrays.asList("content"), captures);

        assertEquals(
                STORED_VAL_A,
                mTrie.retrieve(captures, "Landroid", "content", "Context", "getDisplay"));
        assertEquals(2, captures.size());
        assertEquals(Arrays.asList("content", "Context"), captures);

        assertEquals(
                STORED_VAL_A,
                mTrie.retrieve(
                        captures, "Landroid", "hardware", "graphics", "display", "getDisplay"));
        assertEquals(3, captures.size());
        assertEquals(Arrays.asList("hardware", "graphics", "display"), captures);
    }
}
