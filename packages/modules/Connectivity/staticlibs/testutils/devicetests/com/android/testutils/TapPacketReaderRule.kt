/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.testutils

import android.Manifest.permission.MANAGE_TEST_NETWORKS
import android.net.TestNetworkInterface
import android.net.TestNetworkManager
import android.os.Handler
import android.os.HandlerThread
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import kotlin.test.assertFalse
import kotlin.test.fail

private const val HANDLER_TIMEOUT_MS = 10_000L

/**
 * A [TestRule] that sets up a [TapPacketReader] on a [TestNetworkInterface] for use in the test.
 *
 * @param maxPacketSize Maximum size of packets read in the [TapPacketReader] buffer.
 * @param autoStart Whether to initialize the interface and start the reader automatically for every
 *                  test. If false, each test must either call start() and stop(), or be annotated
 *                  with TapPacketReaderTest before using the reader or interface.
 */
class TapPacketReaderRule @JvmOverloads constructor(
    private val maxPacketSize: Int = 1500,
    private val autoStart: Boolean = true
) : TestRule {
    // Use lateinit as the below members can't be initialized in the rule constructor (the
    // InstrumentationRegistry may not be ready), but from the point of view of test cases using
    // this rule with autoStart = true, the members are always initialized (in setup/test/teardown):
    // tests cases should be able use them directly.
    // lateinit also allows getting good exceptions detailing what went wrong if the members are
    // referenced before they could be initialized (typically if autoStart is false and the test
    // does not call start or use @TapPacketReaderTest).
    lateinit var iface: TestNetworkInterface
    lateinit var reader: TapPacketReader

    @Volatile
    private var readerRunning = false

    /**
     * Indicates that the [TapPacketReaderRule] should initialize its [TestNetworkInterface] and
     * start the [TapPacketReader] before the test, and tear them down afterwards.
     *
     * For use when [TapPacketReaderRule] is created with autoStart = false.
     */
    annotation class TapPacketReaderTest

    /**
     * Initialize the tap interface and start the [TapPacketReader].
     *
     * Tests using this method must also call [stop] before exiting.
     * @param handler Handler to run the reader on. Callers are responsible for safely terminating
     *                the handler when the test ends. If null, a handler thread managed by the
     *                rule will be used.
     */
    @JvmOverloads
    fun start(handler: Handler? = null) {
        if (this::iface.isInitialized) {
            fail("${TapPacketReaderRule::class.java.simpleName} was already started")
        }

        val ctx = InstrumentationRegistry.getInstrumentation().context
        iface = runAsShell(MANAGE_TEST_NETWORKS) {
            val tnm = ctx.getSystemService(TestNetworkManager::class.java)
                    ?: fail("Could not obtain the TestNetworkManager")
            tnm.createTapInterface()
        }
        val usedHandler = handler ?: HandlerThread(
                TapPacketReaderRule::class.java.simpleName).apply { start() }.threadHandler
        reader = TapPacketReader(usedHandler, iface.fileDescriptor.fileDescriptor, maxPacketSize)
        reader.startAsyncForTest()
        readerRunning = true
    }

    /**
     * Stop the [TapPacketReader].
     *
     * Tests calling [start] must call this method before exiting. If a handler was specified in
     * [start], all messages on that handler must also be processed after calling this method and
     * before exiting.
     *
     * If [start] was not called, calling this method is a no-op.
     */
    fun stop() {
        // The reader may not be initialized if the test case did not use the rule, even though
        // other test cases in the same class may be using it (so test classes may call stop in
        // tearDown even if start is not called for all test cases).
        if (!this::reader.isInitialized) return
        reader.handler.post {
            reader.stop()
            readerRunning = false
        }
    }

    override fun apply(base: Statement, description: Description): Statement {
        return TapReaderStatement(base, description)
    }

    private inner class TapReaderStatement(
        private val base: Statement,
        private val description: Description
    ) : Statement() {
        override fun evaluate() {
            val shouldStart = autoStart ||
                    description.getAnnotation(TapPacketReaderTest::class.java) != null
            if (shouldStart) {
                start()
            }

            try {
                base.evaluate()
            } finally {
                if (shouldStart) {
                    stop()
                    reader.handler.looper.apply {
                        quitSafely()
                        thread.join(HANDLER_TIMEOUT_MS)
                        assertFalse(thread.isAlive,
                                "HandlerThread did not exit within $HANDLER_TIMEOUT_MS ms")
                    }
                }

                if (this@TapPacketReaderRule::iface.isInitialized) {
                    iface.fileDescriptor.close()
                }
            }

            assertFalse(readerRunning,
                    "stop() was not called, or the provided handler did not process the stop " +
                    "message before the test ended. If not using autostart, make sure to call " +
                    "stop() after the test. If a handler is specified in start(), make sure all " +
                    "messages are processed after calling stop(), before quitting (for example " +
                    "by using HandlerThread#quitSafely and HandlerThread#join).")
        }
    }
}