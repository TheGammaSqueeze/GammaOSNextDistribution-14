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

package com.android.intentresolver

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.ResultReceiver
import androidx.lifecycle.Observer
import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.intentresolver.ChooserRefinementManager.RefinementCompletion
import com.android.intentresolver.chooser.ImmutableTargetInfo
import com.android.intentresolver.chooser.TargetInfo
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito

@RunWith(AndroidJUnit4::class)
@UiThreadTest
class ChooserRefinementManagerTest {
    private val refinementManager = ChooserRefinementManager()
    private val intentSender = mock<IntentSender>()
    private val application = mock<Application>()
    private val exampleSourceIntents =
        listOf(Intent(Intent.ACTION_VIEW), Intent(Intent.ACTION_EDIT))
    private val exampleTargetInfo =
        ImmutableTargetInfo.newBuilder().setAllSourceIntents(exampleSourceIntents).build()

    private val completionObserver =
        object : Observer<RefinementCompletion> {
            val failureCountDown = CountDownLatch(1)
            val successCountDown = CountDownLatch(1)
            var latestTargetInfo: TargetInfo? = null

            override fun onChanged(completion: RefinementCompletion) {
                if (completion.consume()) {
                    val targetInfo = completion.targetInfo
                    if (targetInfo == null) {
                        failureCountDown.countDown()
                    } else {
                        latestTargetInfo = targetInfo
                        successCountDown.countDown()
                    }
                }
            }
        }

    /** Synchronously executes post() calls. */
    private class FakeHandler(looper: Looper) : Handler(looper) {
        override fun sendMessageAtTime(msg: Message, uptimeMillis: Long): Boolean {
            dispatchMessage(msg)
            return true
        }
    }

    @Before
    fun setup() {
        refinementManager.refinementCompletion.observeForever(completionObserver)
    }

    @Test
    fun testTypicalRefinementFlow() {
        assertThat(
                refinementManager.maybeHandleSelection(
                    exampleTargetInfo,
                    intentSender,
                    application,
                    FakeHandler(checkNotNull(Looper.myLooper()))
                )
            )
            .isTrue()

        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        Mockito.verify(intentSender)
            .sendIntent(any(), eq(0), intentCaptor.capture(), eq(null), eq(null))

        val intent = intentCaptor.value
        assertThat(intent?.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java))
            .isEqualTo(exampleSourceIntents[0])

        val alternates =
            intent?.getParcelableArrayExtra(Intent.EXTRA_ALTERNATE_INTENTS, Intent::class.java)
        assertThat(alternates?.size).isEqualTo(1)
        assertThat(alternates?.get(0)).isEqualTo(exampleSourceIntents[1])

        // Complete the refinement
        val receiver =
            intent?.getParcelableExtra(Intent.EXTRA_RESULT_RECEIVER, ResultReceiver::class.java)
        val bundle = Bundle().apply { putParcelable(Intent.EXTRA_INTENT, exampleSourceIntents[0]) }
        receiver?.send(Activity.RESULT_OK, bundle)

        assertThat(completionObserver.successCountDown.await(1000, TimeUnit.MILLISECONDS)).isTrue()
        assertThat(completionObserver.latestTargetInfo?.resolvedIntent?.action)
            .isEqualTo(Intent.ACTION_VIEW)
    }

    @Test
    fun testRefinementCancelled() {
        assertThat(
                refinementManager.maybeHandleSelection(
                    exampleTargetInfo,
                    intentSender,
                    application,
                    FakeHandler(checkNotNull(Looper.myLooper()))
                )
            )
            .isTrue()

        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        Mockito.verify(intentSender)
            .sendIntent(any(), eq(0), intentCaptor.capture(), eq(null), eq(null))

        val intent = intentCaptor.value

        // Complete the refinement
        val receiver =
            intent?.getParcelableExtra(Intent.EXTRA_RESULT_RECEIVER, ResultReceiver::class.java)
        val bundle = Bundle().apply { putParcelable(Intent.EXTRA_INTENT, exampleSourceIntents[0]) }
        receiver?.send(Activity.RESULT_CANCELED, bundle)

        assertThat(completionObserver.failureCountDown.await(1000, TimeUnit.MILLISECONDS)).isTrue()
    }

    @Test
    fun testMaybeHandleSelection_noSourceIntents() {
        assertThat(
                refinementManager.maybeHandleSelection(
                    ImmutableTargetInfo.newBuilder().build(),
                    intentSender,
                    application,
                    FakeHandler(checkNotNull(Looper.myLooper()))
                )
            )
            .isFalse()
    }

    @Test
    fun testMaybeHandleSelection_suspended() {
        val targetInfo =
            ImmutableTargetInfo.newBuilder()
                .setAllSourceIntents(exampleSourceIntents)
                .setIsSuspended(true)
                .build()

        assertThat(
                refinementManager.maybeHandleSelection(
                    targetInfo,
                    intentSender,
                    application,
                    FakeHandler(checkNotNull(Looper.myLooper()))
                )
            )
            .isFalse()
    }

    @Test
    fun testMaybeHandleSelection_noIntentSender() {
        assertThat(
                refinementManager.maybeHandleSelection(
                    exampleTargetInfo,
                    /* IntentSender */ null,
                    application,
                    FakeHandler(checkNotNull(Looper.myLooper()))
                )
            )
            .isFalse()
    }

    @Test
    fun testConfigurationChangeDuringRefinement() {
        assertThat(
                refinementManager.maybeHandleSelection(
                    exampleTargetInfo,
                    intentSender,
                    application,
                    FakeHandler(checkNotNull(Looper.myLooper()))
                )
            )
            .isTrue()

        refinementManager.onActivityStop(/* config changing = */ true)
        refinementManager.onActivityResume()

        assertThat(completionObserver.failureCountDown.count).isEqualTo(1)
    }

    @Test
    fun testResumeDuringRefinement() {
        assertThat(
                refinementManager.maybeHandleSelection(
                    exampleTargetInfo,
                    intentSender,
                    application,
                    FakeHandler(checkNotNull(Looper.myLooper())!!)
                )
            )
            .isTrue()

        refinementManager.onActivityStop(/* config changing = */ false)
        // Resume during refinement but not during a config change, so finish the activity.
        refinementManager.onActivityResume()

        // Call should be synchronous, don't need to await for this one.
        assertThat(completionObserver.failureCountDown.count).isEqualTo(0)
    }

    @Test
    fun testRefinementCompletion() {
        val refinementCompletion = RefinementCompletion(exampleTargetInfo)
        assertThat(refinementCompletion.targetInfo).isEqualTo(exampleTargetInfo)
        assertThat(refinementCompletion.consume()).isTrue()
        assertThat(refinementCompletion.targetInfo).isEqualTo(exampleTargetInfo)

        // can only consume once.
        assertThat(refinementCompletion.consume()).isFalse()
    }
}
