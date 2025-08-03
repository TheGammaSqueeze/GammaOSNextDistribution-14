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

package com.android.server

import android.os.Build
import androidx.test.filters.SmallTest
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import com.android.testutils.DevSdkIgnoreRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(DevSdkIgnoreRunner::class)
@SmallTest
@IgnoreUpTo(Build.VERSION_CODES.S)
class BpfLoaderRcUtilsTest {
    @Test
    fun testLoadExistingBpfRcFile() {

        val inputString = """
            service a
            # test comment
            service bpfloader /system/bin/bpfloader
                capabilities CHOWN SYS_ADMIN NET_ADMIN
                group root graphics network_stack net_admin net_bw_acct net_bw_stats net_raw system
                user root
                rlimit memlock 1073741824 1073741824
                oneshot
                # comment 漢字
                reboot_on_failure reboot,bpfloader-failed
                updatable
            
            #test comment
            on b 
              oneshot 
              # test comment
        """.trimIndent()
        val expectedResult = listOf(
            "service bpfloader /system/bin/bpfloader",
            "capabilities CHOWN SYS_ADMIN NET_ADMIN",
            "group root graphics network_stack net_admin net_bw_acct net_bw_stats net_raw system",
            "user root",
            "rlimit memlock 1073741824 1073741824",
            "oneshot",
            "reboot_on_failure reboot,bpfloader-failed",
            "updatable"
        )

        assertEquals(expectedResult,
                BpfLoaderRcUtils.loadExistingBpfRcFile(inputString.byteInputStream()))
    }

    @Test
    fun testCheckBpfRcFile() {
        assertTrue(BpfLoaderRcUtils.checkBpfLoaderRc())
    }
}
