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

package com.android.server.net

import android.os.Handler
import android.os.HandlerThread
import com.android.net.module.util.SharedLog
import com.android.testutils.DevSdkIgnoreRunner
import java.io.PrintWriter
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

@DevSdkIgnoreRunner.MonitorThreadLeak
@RunWith(DevSdkIgnoreRunner::class)
class SkDestroyListenerTest {
    @Mock lateinit var sharedLog: SharedLog
    val handlerThread = HandlerThread("SkDestroyListenerTest")

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        handlerThread.start()
    }

    @After
    fun tearDown() {
        handlerThread.quitSafely()
        handlerThread.join()
    }

    @Test
    fun testDump() {
        doReturn(sharedLog).`when`(sharedLog).forSubComponent(any())

        val handler = Handler(handlerThread.looper)
        val skDestroylistener = SkDestroyListener(null /* cookieTagMap */, handler, sharedLog)
        val pw = PrintWriter(System.out)
        skDestroylistener.dump(pw)

        verify(sharedLog).reverseDump(pw)
    }
}
