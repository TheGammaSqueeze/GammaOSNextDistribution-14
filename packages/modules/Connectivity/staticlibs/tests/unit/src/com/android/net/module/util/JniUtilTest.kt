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

package com.android.net.module.util

import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
public final class JniUtilTest {
    private val TEST_JAVA_UTIL_NAME = "java_util_jni"
    private val TEST_ORG_JUNIT_NAME = "org_junit_jni"

    @Test
    fun testGetJniLibraryName() {
        assertEquals(TEST_JAVA_UTIL_NAME,
                JniUtil.getJniLibraryName(java.util.Set::class.java.getPackage()))
        assertEquals(TEST_ORG_JUNIT_NAME,
                JniUtil.getJniLibraryName(org.junit.Before::class.java.getPackage()))
    }
}
