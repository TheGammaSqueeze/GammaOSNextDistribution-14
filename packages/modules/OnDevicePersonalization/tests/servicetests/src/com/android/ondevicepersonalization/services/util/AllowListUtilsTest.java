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

package com.android.ondevicepersonalization.services.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AllowListUtilsTest {
    @Test
    public void testIsAllowListed() {
        assertTrue(AllowListUtils.isAllowListed(null, "*"));
        assertTrue(AllowListUtils.isAllowListed("", "*"));
        assertTrue(AllowListUtils.isAllowListed("com.android.app", "*"));
        assertFalse(AllowListUtils.isAllowListed(null, ""));
        assertFalse(AllowListUtils.isAllowListed(" ", ""));
        assertFalse(AllowListUtils.isAllowListed("com.android.app ", ""));
        assertFalse(AllowListUtils.isAllowListed("com.android.app", "android.app"));
        assertFalse(AllowListUtils.isAllowListed("com.android.app", "com.play.app"));
        assertFalse(AllowListUtils.isAllowListed("com.android.app",
                "com.android.app.extension"));
        assertFalse(AllowListUtils.isAllowListed("com.android.app ", "com.android.app"));
        assertTrue(AllowListUtils.isAllowListed("com.android.app", "com.android.app"));
        assertTrue(AllowListUtils.isAllowListed("com.android.app",
                "com.play.app,com.android.app"));
        assertFalse(AllowListUtils.isAllowListed("com.android.app",
                "com.android.app1,com.android.app2"));
        assertTrue(AllowListUtils.isAllowListed("com.android.app",
                " com.android.app , com.play.app "));
    }

    @Test public void testIsPairAllowListed() {
        // Null flag.
        assertFalse(AllowListUtils.isPairAllowListed(
                "com.test.app1",
                "ABCD",
                "com.test.app2",
                "EFGH",
                null));
        // Empty flag.
        assertFalse(AllowListUtils.isPairAllowListed(
                "com.test.app1",
                "ABCD",
                "com.test.app2",
                "EFGH",
                ""));
        // Not found.
        assertFalse(AllowListUtils.isPairAllowListed(
                "com.test.app1",
                "ABCD",
                "com.test.app2",
                "EFGH",
                "com.test.app3:ZZZZ;com.test.app4:YYYY"));
        // Match
        assertTrue(AllowListUtils.isPairAllowListed(
                "com.test.app1",
                "ABCD",
                "com.test.app2",
                "EFGH",
                "com.test.app3:ZZZZ;com.test.app4:YYYY,com.test.app1:ABCD;com.test.app2:EFGH"));
        // Flag contains no cert - Match
        assertTrue(AllowListUtils.isPairAllowListed(
                "com.test.app1",
                "ABCD",
                "com.test.app2",
                "EFGH",
                "com.test.app3:ZZZZ;com.test.app4:YYYY,com.test.app1;com.test.app2"));
        // Second entity cert mismatch.
        assertFalse(AllowListUtils.isPairAllowListed(
                "com.test.app1",
                "ABCD",
                "com.test.app2",
                "EFGH",
                "com.test.app1:ABCD;com.test.app2:PQRS"));
        // First entity cert mismatch.
        assertFalse(AllowListUtils.isPairAllowListed(
                "com.test.app1",
                "ABCD",
                "com.test.app2",
                "EFGH",
                "com.test.app1:PQRS;com.test.app2:EFGH"));
        // Second entity missing.
        assertFalse(AllowListUtils.isPairAllowListed(
                "com.test.app1",
                "ABCD",
                "com.test.app2",
                "EFGH",
                "com.test.app1:ABCD"));
        // Bad flag.
        assertFalse(AllowListUtils.isPairAllowListed(
                "com.test.app1",
                "ABCD",
                "com.test.app2",
                "EFGH",
                "com.test.app1:ABCD;com.test.app2:EFGH;com.test.app3:PQRS"));
    }
}
