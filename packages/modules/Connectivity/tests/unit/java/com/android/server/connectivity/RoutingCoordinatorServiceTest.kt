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

package com.android.server.connectivity

import android.net.INetd
import android.os.Build
import android.util.Log
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import com.android.testutils.DevSdkIgnoreRunner
import com.android.testutils.tryTest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock

@RunWith(DevSdkIgnoreRunner::class)
@IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
class RoutingCoordinatorServiceTest {
    val mNetd = mock(INetd::class.java)
    val mService = RoutingCoordinatorService(mNetd)

    @Test
    fun testInterfaceForward() {
        val inOrder = inOrder(mNetd)

        mService.addInterfaceForward("from1", "to1")
        inOrder.verify(mNetd).ipfwdEnableForwarding(any())
        inOrder.verify(mNetd).tetherAddForward("from1", "to1")
        inOrder.verify(mNetd).ipfwdAddInterfaceForward("from1", "to1")

        mService.addInterfaceForward("from2", "to1")
        inOrder.verify(mNetd).tetherAddForward("from2", "to1")
        inOrder.verify(mNetd).ipfwdAddInterfaceForward("from2", "to1")

        val hasFailed = AtomicBoolean(false)
        val prevHandler = Log.setWtfHandler { tag, what, system ->
            hasFailed.set(true)
        }
        tryTest {
            mService.addInterfaceForward("from2", "to1")
            assertTrue(hasFailed.get())
        } cleanup {
            Log.setWtfHandler(prevHandler)
        }

        mService.removeInterfaceForward("from1", "to1")
        inOrder.verify(mNetd).ipfwdRemoveInterfaceForward("from1", "to1")
        inOrder.verify(mNetd).tetherRemoveForward("from1", "to1")

        mService.removeInterfaceForward("from2", "to1")
        inOrder.verify(mNetd).ipfwdRemoveInterfaceForward("from2", "to1")
        inOrder.verify(mNetd).tetherRemoveForward("from2", "to1")

        inOrder.verify(mNetd).ipfwdDisableForwarding(any())
    }
}
