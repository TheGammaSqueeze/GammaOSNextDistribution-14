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
import com.android.net.module.util.NetworkIdentityUtils.scrubSubscriberId
import com.android.net.module.util.NetworkIdentityUtils.scrubSubscriberIds
import com.android.testutils.assertContainsStringsExactly
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
@SmallTest
class NetworkIdentityUtilsTest {
    @Test
    fun testScrubSubscriberId() {
        assertEquals("123456...", scrubSubscriberId("1234567890123"))
        assertEquals("123456...", scrubSubscriberId("1234567"))
        assertEquals("123...", scrubSubscriberId("123"))
        assertEquals("...", scrubSubscriberId(""))
        assertEquals("null", scrubSubscriberId(null))
    }

    @Test
    fun testScrubSubscriberIds() {
        assertContainsStringsExactly(scrubSubscriberIds(arrayOf("1234567", "", null))!!,
                "123456...", "...", "null")
        assertContainsStringsExactly(scrubSubscriberIds(arrayOf("12345"))!!, "12345...")
        assertContainsStringsExactly(scrubSubscriberIds(arrayOf())!!)
        assertNull(scrubSubscriberIds(null))
    }
}