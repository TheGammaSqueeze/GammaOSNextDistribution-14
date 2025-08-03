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

package com.android.intentresolver.v2.ui

import android.app.PendingIntent
import android.compat.testing.PlatformCompatChangeRule
import android.content.ComponentName
import android.content.Intent
import android.os.Process
import android.service.chooser.ChooserResult
import android.service.chooser.Flags
import androidx.test.InstrumentationRegistry
import com.android.intentresolver.inject.FakeChooserServiceFlags
import com.android.intentresolver.v2.ui.model.ShareAction
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

@OptIn(ExperimentalCoroutinesApi::class)
class ShareResultSenderImplTest {

    private val context = InstrumentationRegistry.getInstrumentation().context

    @get:Rule val compatChangeRule: TestRule = PlatformCompatChangeRule()

    val flags = FakeChooserServiceFlags()

    @OptIn(ExperimentalCoroutinesApi::class)
    @EnableCompatChanges(ChooserResult.SEND_CHOOSER_RESULT)
    @Test
    fun onComponentSelected_chooserResultEnabled() = runTest {
        val pi = PendingIntent.getBroadcast(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
        val deferred = CompletableDeferred<Intent>()
        val intentDispatcher = IntentSenderDispatcher { _, intent -> deferred.complete(intent) }

        flags.setFlag(Flags.FLAG_ENABLE_CHOOSER_RESULT, true)

        val resultSender =
            ShareResultSenderImpl(
                flags = flags,
                scope = this,
                backgroundDispatcher = UnconfinedTestDispatcher(testScheduler),
                callerUid = Process.myUid(),
                resultSender = pi.intentSender,
                intentDispatcher = intentDispatcher
            )

        resultSender.onComponentSelected(ComponentName("example.com", "Foo"), true)
        runCurrent()

        val intentReceived = deferred.await()
        val chooserResult =
            intentReceived.getParcelableExtra(
                Intent.EXTRA_CHOOSER_RESULT,
                ChooserResult::class.java
            )
        assertThat(chooserResult).isNotNull()
        assertThat(chooserResult?.type).isEqualTo(ChooserResult.CHOOSER_RESULT_SELECTED_COMPONENT)
        assertThat(chooserResult?.selectedComponent).isEqualTo(ComponentName("example.com", "Foo"))
        assertThat(chooserResult?.isShortcut).isTrue()
    }

    @DisableCompatChanges(ChooserResult.SEND_CHOOSER_RESULT)
    @Test
    fun onComponentSelected_chooserResultDisabled() = runTest {
        val pi = PendingIntent.getBroadcast(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
        val deferred = CompletableDeferred<Intent>()
        val intentDispatcher = IntentSenderDispatcher { _, intent -> deferred.complete(intent) }

        flags.setFlag(Flags.FLAG_ENABLE_CHOOSER_RESULT, true)

        val resultSender =
            ShareResultSenderImpl(
                flags = flags,
                scope = this,
                backgroundDispatcher = UnconfinedTestDispatcher(testScheduler),
                callerUid = Process.myUid(),
                resultSender = pi.intentSender,
                intentDispatcher = intentDispatcher
            )

        resultSender.onComponentSelected(ComponentName("example.com", "Foo"), true)
        runCurrent()

        val intentReceived = deferred.await()
        val componentName =
            intentReceived.getParcelableExtra(
                Intent.EXTRA_CHOSEN_COMPONENT,
                ComponentName::class.java
            )

        assertWithMessage("EXTRA_CHOSEN_COMPONENT from received intent")
            .that(componentName)
            .isEqualTo(ComponentName("example.com", "Foo"))

        assertWithMessage("received intent has EXTRA_CHOOSER_RESULT")
            .that(intentReceived.hasExtra(Intent.EXTRA_CHOOSER_RESULT))
            .isFalse()
    }

    @EnableCompatChanges(ChooserResult.SEND_CHOOSER_RESULT)
    @Test
    fun onActionSelected_chooserResultEnabled() = runTest {
        val pi = PendingIntent.getBroadcast(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
        val deferred = CompletableDeferred<Intent>()
        val intentDispatcher = IntentSenderDispatcher { _, intent -> deferred.complete(intent) }

        flags.setFlag(Flags.FLAG_ENABLE_CHOOSER_RESULT, true)

        val resultSender =
            ShareResultSenderImpl(
                flags = flags,
                scope = this,
                backgroundDispatcher = UnconfinedTestDispatcher(testScheduler),
                callerUid = Process.myUid(),
                resultSender = pi.intentSender,
                intentDispatcher = intentDispatcher
            )

        resultSender.onActionSelected(ShareAction.SYSTEM_COPY)
        runCurrent()

        val intentReceived = deferred.await()
        val chosenComponent =
            intentReceived.getParcelableExtra(
                Intent.EXTRA_CHOSEN_COMPONENT,
                ChooserResult::class.java
            )
        assertThat(chosenComponent).isNull()

        val chooserResult =
            intentReceived.getParcelableExtra(
                Intent.EXTRA_CHOOSER_RESULT,
                ChooserResult::class.java
            )
        assertThat(chooserResult).isNotNull()
        assertThat(chooserResult?.type).isEqualTo(ChooserResult.CHOOSER_RESULT_COPY)
        assertThat(chooserResult?.selectedComponent).isNull()
        assertThat(chooserResult?.isShortcut).isFalse()
    }

    @DisableCompatChanges(ChooserResult.SEND_CHOOSER_RESULT)
    @Test
    fun onActionSelected_chooserResultDisabled() = runTest {
        val pi = PendingIntent.getBroadcast(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
        val deferred = CompletableDeferred<Intent>()
        val intentDispatcher = IntentSenderDispatcher { _, intent -> deferred.complete(intent) }

        flags.setFlag(Flags.FLAG_ENABLE_CHOOSER_RESULT, true)

        val resultSender =
            ShareResultSenderImpl(
                flags = flags,
                scope = this,
                backgroundDispatcher = UnconfinedTestDispatcher(testScheduler),
                callerUid = Process.myUid(),
                resultSender = pi.intentSender,
                intentDispatcher = intentDispatcher
            )

        resultSender.onActionSelected(ShareAction.SYSTEM_COPY)
        runCurrent()

        // No result should have been sent, this should never complete
        assertWithMessage("deferred result isComplete").that(deferred.isCompleted).isFalse()
    }
}
