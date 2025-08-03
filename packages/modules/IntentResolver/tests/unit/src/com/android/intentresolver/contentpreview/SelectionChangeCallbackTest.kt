/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.intentresolver.contentpreview

import android.app.PendingIntent
import android.content.ComponentName
import android.content.ContentInterface
import android.content.Intent
import android.content.Intent.ACTION_CHOOSER
import android.content.Intent.ACTION_SEND
import android.content.Intent.ACTION_SEND_MULTIPLE
import android.content.Intent.EXTRA_ALTERNATE_INTENTS
import android.content.Intent.EXTRA_CHOOSER_CUSTOM_ACTIONS
import android.content.Intent.EXTRA_CHOOSER_MODIFY_SHARE_ACTION
import android.content.Intent.EXTRA_CHOOSER_REFINEMENT_INTENT_SENDER
import android.content.Intent.EXTRA_CHOOSER_TARGETS
import android.content.Intent.EXTRA_INTENT
import android.content.Intent.EXTRA_STREAM
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.service.chooser.AdditionalContentContract.MethodNames.ON_SELECTION_CHANGED
import android.service.chooser.ChooserAction
import android.service.chooser.ChooserTarget
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.intentresolver.any
import com.android.intentresolver.argumentCaptor
import com.android.intentresolver.capture
import com.android.intentresolver.mock
import com.android.intentresolver.whenever
import com.google.common.truth.Correspondence
import com.google.common.truth.Correspondence.BinaryPredicate
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

@RunWith(AndroidJUnit4::class)
class SelectionChangeCallbackTest {
    private val uri = Uri.parse("content://org.pkg/content-provider")
    private val chooserIntent = Intent(ACTION_CHOOSER)
    private val contentResolver = mock<ContentInterface>()
    private val context = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun testPayloadChangeCallbackContact() {
        val testSubject = SelectionChangeCallback(uri, chooserIntent, contentResolver)

        val u1 = createUri(1)
        val u2 = createUri(2)
        val targetIntent =
            Intent(ACTION_SEND_MULTIPLE).apply {
                val uris =
                    ArrayList<Uri>().apply {
                        add(u1)
                        add(u2)
                    }
                putExtra(EXTRA_STREAM, uris)
                type = "image/jpg"
            }
        testSubject.onSelectionChanged(targetIntent)

        val authorityCaptor = argumentCaptor<String>()
        val methodCaptor = argumentCaptor<String>()
        val argCaptor = argumentCaptor<String>()
        val extraCaptor = argumentCaptor<Bundle>()
        verify(contentResolver, times(1))
            .call(
                capture(authorityCaptor),
                capture(methodCaptor),
                capture(argCaptor),
                capture(extraCaptor)
            )
        assertWithMessage("Wrong additional content provider authority")
            .that(authorityCaptor.value)
            .isEqualTo(uri.authority)
        assertWithMessage("Wrong additional content provider #call() method name")
            .that(methodCaptor.value)
            .isEqualTo(ON_SELECTION_CHANGED)
        assertWithMessage("Wrong additional content provider argument value")
            .that(argCaptor.value)
            .isEqualTo(uri.toString())
        val extraBundle = extraCaptor.value
        assertWithMessage("Additional content provider #call() should have a non-null extras arg.")
            .that(extraBundle)
            .isNotNull()
        requireNotNull(extraBundle)
        val argChooserIntent = extraBundle.getParcelable(EXTRA_INTENT, Intent::class.java)
        assertWithMessage("#call() extras arg. should contain Intent#EXTRA_INTENT")
            .that(argChooserIntent)
            .isNotNull()
        requireNotNull(argChooserIntent)
        assertWithMessage("#call() extras arg's Intent#EXTRA_INTENT should be a Chooser intent")
            .that(argChooserIntent.action)
            .isEqualTo(chooserIntent.action)
        val argTargetIntent = argChooserIntent.getParcelableExtra(EXTRA_INTENT, Intent::class.java)
        assertWithMessage(
                "A chooser intent passed into #call() method should contain updated target intent"
            )
            .that(argTargetIntent)
            .isNotNull()
        requireNotNull(argTargetIntent)
        assertWithMessage("Incorrect target intent")
            .that(argTargetIntent.action)
            .isEqualTo(targetIntent.action)
        assertWithMessage("Incorrect target intent")
            .that(argTargetIntent.getParcelableArrayListExtra(EXTRA_STREAM, Uri::class.java))
            .containsExactly(u1, u2)
            .inOrder()
    }

    @Test
    fun testPayloadChangeCallbackUpdatesCustomActions() {
        val a1 =
            ChooserAction.Builder(
                    Icon.createWithContentUri(createUri(10)),
                    "Action 1",
                    PendingIntent.getBroadcast(
                        context,
                        1,
                        Intent("test"),
                        PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .build()
        val a2 =
            ChooserAction.Builder(
                    Icon.createWithContentUri(createUri(11)),
                    "Action 2",
                    PendingIntent.getBroadcast(
                        context,
                        1,
                        Intent("test"),
                        PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .build()
        whenever(contentResolver.call(any<String>(), any(), any(), any()))
            .thenReturn(
                Bundle().apply { putParcelableArray(EXTRA_CHOOSER_CUSTOM_ACTIONS, arrayOf(a1, a2)) }
            )

        val testSubject = SelectionChangeCallback(uri, chooserIntent, contentResolver)

        val targetIntent = Intent(ACTION_SEND_MULTIPLE)
        val result = testSubject.onSelectionChanged(targetIntent)
        assertWithMessage("Callback result should not be null").that(result).isNotNull()
        requireNotNull(result)
        assertWithMessage("Unexpected custom actions")
            .that(result.customActions?.map { it.icon to it.label })
            .containsExactly(a1.icon to a1.label, a2.icon to a2.label)
            .inOrder()

        assertThat(result.modifyShareAction).isNull()
        assertThat(result.alternateIntents).isNull()
        assertThat(result.callerTargets).isNull()
        assertThat(result.refinementIntentSender).isNull()
    }

    @Test
    fun testPayloadChangeCallbackUpdatesReselectionAction() {
        val modifyShare =
            ChooserAction.Builder(
                    Icon.createWithContentUri(createUri(10)),
                    "Modify Share",
                    PendingIntent.getBroadcast(
                        context,
                        1,
                        Intent("test"),
                        PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .build()
        whenever(contentResolver.call(any<String>(), any(), any(), any()))
            .thenReturn(
                Bundle().apply { putParcelable(EXTRA_CHOOSER_MODIFY_SHARE_ACTION, modifyShare) }
            )

        val testSubject = SelectionChangeCallback(uri, chooserIntent, contentResolver)

        val targetIntent = Intent(ACTION_SEND)
        val result = testSubject.onSelectionChanged(targetIntent)
        assertWithMessage("Callback result should not be null").that(result).isNotNull()
        requireNotNull(result)
        assertWithMessage("Unexpected modify share action: wrong icon")
            .that(result.modifyShareAction?.icon)
            .isEqualTo(modifyShare.icon)
        assertWithMessage("Unexpected modify share action: wrong label")
            .that(result.modifyShareAction?.label)
            .isEqualTo(modifyShare.label)

        assertThat(result.customActions).isNull()
        assertThat(result.alternateIntents).isNull()
        assertThat(result.callerTargets).isNull()
        assertThat(result.refinementIntentSender).isNull()
    }

    @Test
    fun testPayloadChangeCallbackUpdatesAlternateIntents() {
        val alternateIntents =
            arrayOf(
                Intent(ACTION_SEND_MULTIPLE).apply {
                    addCategory("test")
                    type = ""
                }
            )
        whenever(contentResolver.call(any<String>(), any(), any(), any()))
            .thenReturn(
                Bundle().apply { putParcelableArray(EXTRA_ALTERNATE_INTENTS, alternateIntents) }
            )

        val testSubject = SelectionChangeCallback(uri, chooserIntent, contentResolver)

        val targetIntent = Intent(ACTION_SEND)
        val result = testSubject.onSelectionChanged(targetIntent)
        assertWithMessage("Callback result should not be null").that(result).isNotNull()
        requireNotNull(result)
        assertWithMessage("Wrong number of alternate intents")
            .that(result.alternateIntents)
            .hasSize(1)
        assertWithMessage("Wrong alternate intent: action")
            .that(result.alternateIntents?.get(0)?.action)
            .isEqualTo(alternateIntents[0].action)
        assertWithMessage("Wrong alternate intent: categories")
            .that(result.alternateIntents?.get(0)?.categories)
            .containsExactlyElementsIn(alternateIntents[0].categories)
        assertWithMessage("Wrong alternate intent: mime type")
            .that(result.alternateIntents?.get(0)?.type)
            .isEqualTo(alternateIntents[0].type)

        assertThat(result.customActions).isNull()
        assertThat(result.modifyShareAction).isNull()
        assertThat(result.callerTargets).isNull()
        assertThat(result.refinementIntentSender).isNull()
    }

    @Test
    fun testPayloadChangeCallbackUpdatesCallerTargets() {
        val t1 =
            ChooserTarget(
                "Target 1",
                Icon.createWithContentUri(createUri(1)),
                0.99f,
                ComponentName("org.pkg.app", ".ClassA"),
                null
            )
        val t2 =
            ChooserTarget(
                "Target 2",
                Icon.createWithContentUri(createUri(1)),
                1f,
                ComponentName("org.pkg.app", ".ClassB"),
                null
            )
        whenever(contentResolver.call(any<String>(), any(), any(), any()))
            .thenReturn(
                Bundle().apply { putParcelableArray(EXTRA_CHOOSER_TARGETS, arrayOf(t1, t2)) }
            )

        val testSubject = SelectionChangeCallback(uri, chooserIntent, contentResolver)

        val targetIntent = Intent(ACTION_SEND)
        val result = testSubject.onSelectionChanged(targetIntent)
        assertWithMessage("Callback result should not be null").that(result).isNotNull()
        requireNotNull(result)
        assertWithMessage("Wrong caller targets")
            .that(result.callerTargets)
            .comparingElementsUsing(
                Correspondence.from(
                    BinaryPredicate<ChooserTarget?, ChooserTarget> { actual, expected ->
                        expected.componentName == actual?.componentName &&
                            expected.title == actual?.title &&
                            expected.icon == actual?.icon &&
                            expected.score == actual?.score
                    },
                    ""
                )
            )
            .containsExactly(t1, t2)
            .inOrder()

        assertThat(result.customActions).isNull()
        assertThat(result.modifyShareAction).isNull()
        assertThat(result.alternateIntents).isNull()
        assertThat(result.refinementIntentSender).isNull()
    }

    @Test
    fun testPayloadChangeCallbackUpdatesRefinementIntentSender() {
        val broadcast =
            PendingIntent.getBroadcast(context, 1, Intent("test"), PendingIntent.FLAG_IMMUTABLE)

        whenever(contentResolver.call(any<String>(), any(), any(), any()))
            .thenReturn(
                Bundle().apply {
                    putParcelable(EXTRA_CHOOSER_REFINEMENT_INTENT_SENDER, broadcast.intentSender)
                }
            )

        val testSubject = SelectionChangeCallback(uri, chooserIntent, contentResolver)

        val targetIntent = Intent(ACTION_SEND)
        val result = testSubject.onSelectionChanged(targetIntent)
        assertWithMessage("Callback result should not be null").that(result).isNotNull()
        requireNotNull(result)
        assertThat(result.customActions).isNull()
        assertThat(result.modifyShareAction).isNull()
        assertThat(result.alternateIntents).isNull()
        assertThat(result.callerTargets).isNull()
        assertThat(result.refinementIntentSender).isNotNull()
    }

    @Test
    fun testPayloadChangeCallbackProvidesInvalidData_invalidDataIgnored() {
        whenever(contentResolver.call(any<String>(), any(), any(), any()))
            .thenReturn(
                Bundle().apply {
                    putParcelableArrayList(EXTRA_CHOOSER_CUSTOM_ACTIONS, ArrayList<ChooserAction>())
                    putParcelable(EXTRA_CHOOSER_MODIFY_SHARE_ACTION, createUri(1))
                    putParcelableArrayList(EXTRA_ALTERNATE_INTENTS, ArrayList<Intent>())
                    putParcelableArrayList(EXTRA_CHOOSER_TARGETS, ArrayList<ChooserTarget>())
                    putParcelable(EXTRA_CHOOSER_REFINEMENT_INTENT_SENDER, createUri(2))
                }
            )

        val testSubject = SelectionChangeCallback(uri, chooserIntent, contentResolver)

        val targetIntent = Intent(ACTION_SEND)
        val result = testSubject.onSelectionChanged(targetIntent)
        assertWithMessage("Callback result should not be null").that(result).isNotNull()
        requireNotNull(result)
        assertThat(result.customActions).isNull()
        assertThat(result.modifyShareAction).isNull()
        assertThat(result.alternateIntents).isNull()
        assertThat(result.callerTargets).isNull()
        assertThat(result.refinementIntentSender).isNull()
    }
}

private fun createUri(id: Int) = Uri.parse("content://org.pkg.images/$id.png")
