/**
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

package android.ext.services.notification

import android.app.ActivityManager
import android.app.Notification
import android.app.Notification.CATEGORY_MESSAGE
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.FEATURE_WATCH
import android.os.Process
import android.service.notification.Adjustment
import android.service.notification.Adjustment.KEY_SENSITIVE_CONTENT
import android.service.notification.Adjustment.KEY_TEXT_REPLIES
import android.service.notification.StatusBarNotification
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextClassifier
import android.view.textclassifier.TextClassifier.TYPE_FLIGHT_NUMBER
import android.view.textclassifier.TextClassifier.TYPE_OTP_CODE
import android.view.textclassifier.TextLanguage
import android.view.textclassifier.TextLinks
import androidx.test.platform.app.InstrumentationRegistry
import com.android.modules.utils.build.SdkLevel
import com.android.textclassifier.notification.SmartSuggestions
import com.android.textclassifier.notification.SmartSuggestionsHelper
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Stubber

@RunWith(JUnit4::class)
class AssistantTest {
    val context = InstrumentationRegistry.getInstrumentation().targetContext!!
    lateinit var mockSuggestions: SmartSuggestionsHelper
    lateinit var mockTc: TextClassifier
    lateinit var assistant: Assistant
    lateinit var mockPm: PackageManager
    lateinit var mockAm: ActivityManager
    val EXECUTOR_AWAIT_TIME = 200L

    private fun <T> Stubber.whenKt(mock: T): T = `when`(mock)

    @Before
    fun setUpMocks() {
        assumeTrue(SdkLevel.isAtLeastV())
        assistant = spy(Assistant())
        mockSuggestions = mock(SmartSuggestionsHelper::class.java)
        mockTc = mock(TextClassifier::class.java)
        mockAm = mock(ActivityManager::class.java)
        mockPm = mock(PackageManager::class.java)
        assistant.mAm = mockAm
        assistant.mPm = mockPm
        assistant.mSmartSuggestionsHelper = mockSuggestions
        doReturn(SmartSuggestions(emptyList(), emptyList()))
                .whenKt(mockSuggestions).onNotificationEnqueued(any())
        assistant.mTcm = context.getSystemService(TextClassificationManager::class.java)!!
        assistant.mTcm.setTextClassifier(mockTc)
        doReturn(TextLinks.Builder("").build()).whenKt(mockTc).generateLinks(any())
    }

    @Test
    fun onNotificationEnqueued_callsTextClassifierForOtpAndSuggestions() {
        val sbn = createSbn()
        var entities: List<String> = emptyList()
        doAnswer { invocation: InvocationOnMock ->
            val request = invocation.getArgument<TextLinks.Request>(0)
            assertWithMessage("Expected a non-null entity config")
                .that(request.entityConfig).isNotNull()
            entities = request.entityConfig!!.resolveEntityListModifications(emptyList()).toList()
            return@doAnswer TextLinks.Builder(request.text.toString()).build()
        }.whenKt(mockTc).generateLinks(any())
        doReturn(true).whenKt(assistant).shouldUseTcForOtpDetection(any(), any())
        assistant.onNotificationEnqueued(sbn, NotificationChannel("0", "", IMPORTANCE_DEFAULT))
        Thread.sleep(EXECUTOR_AWAIT_TIME)
        assertWithMessage("Expected entities to contain otp_code").that(entities).contains(
            TYPE_OTP_CODE)
        verify(mockTc, times(1)).generateLinks(any())
        verify(assistant.mSmartSuggestionsHelper, times(1)).onNotificationEnqueued(eq(sbn))
        // A false result shouldn't result in an adjustment call for the otp
        verify(assistant, never())
            .createNotificationAdjustment(any(), isNull(), isNull(), eq(false))
        // One adjustment for the suggestions and OTP together
        verify(assistant).createNotificationAdjustment(any(),
            eq(ArrayList<Notification.Action>()), eq(ArrayList<CharSequence>()), eq(false))
    }

    @Test
    fun onNotificationEnqueued_doesntUseTcForOtpIfLanguageNotSupported() {
        val sbn = createSbn(TEXT_WITH_OTP)
        // Empty list of detected languages means that the notification language didn't match
        doReturn(TextLanguage.Builder().build())
            .whenKt(mockTc).detectLanguage(any())
        var entities: List<String> = emptyList()
        doAnswer { invocation: InvocationOnMock ->
            val request = invocation.getArgument<TextLinks.Request>(0)
            assertWithMessage("Expected a non-null entity config")
                .that(request.entityConfig).isNotNull()
            entities = request.entityConfig!!.resolveEntityListModifications(emptyList()).toList()
            return@doAnswer TextLinks.Builder(request.text.toString()).build()
        }.whenKt(mockTc).generateLinks(any())
        assistant.onNotificationEnqueued(sbn, NotificationChannel("0", "", IMPORTANCE_DEFAULT))
        Thread.sleep(EXECUTOR_AWAIT_TIME)
        assertWithMessage("Expected entities not to contain otp_code").that(entities)
            .doesNotContain(TYPE_OTP_CODE)
        // The TC should be used here to detect false positives for flight numbers
        assertWithMessage("Expected entities to contain \"flight\"").that(entities).contains(
            TYPE_FLIGHT_NUMBER)
        verify(assistant.mSmartSuggestionsHelper, times(1)).onNotificationEnqueued(eq(sbn))
    }

    @Test
    fun onNotificationEnqueued_usesBothRegexAndTc() {
        val sbn = createSbn(TEXT_WITH_OTP)
        doReturn(true).whenKt(assistant).shouldUseTcForOtpDetection(any(), any())
        val directReturn =
            assistant.onNotificationEnqueued(sbn, NotificationChannel("0", "", IMPORTANCE_DEFAULT))
        // Expect an adjustment to be returned, due to regex
        assertThat(directReturn).isNotNull()
        assertThat(directReturn!!.signals.getBoolean(KEY_SENSITIVE_CONTENT)).isTrue()
        assertThat(directReturn.signals.getCharSequenceArrayList(KEY_TEXT_REPLIES)).isNull()
        Thread.sleep(EXECUTOR_AWAIT_TIME)
        // Expect a call to the TC, that returns false, and thus, doesn't send an early adjustment
        verify(mockTc).generateLinks(any())
        verify(assistant, never())
            .createNotificationAdjustment(any(), isNull(), isNull(), eq(false))
        // Expect adjustment for the suggestions and OTP together, with a false value
        verify(assistant).createNotificationAdjustment(any(),
            eq(ArrayList<Notification.Action>()), eq(ArrayList<CharSequence>()), eq(false))
    }

    @Test
    fun onNotificationEnqueued_returnsNullIfRegexDoesntMatch() {
        val sbn = createSbn(text = "")
        val directReturn =
            assistant.onNotificationEnqueued(sbn, NotificationChannel("0", "", IMPORTANCE_DEFAULT))
        // Expect an adjustment to be returned, due to regex
        assertThat(directReturn).isNull()
    }

    @Test
    fun onNotificationEnqueued_doesntUseTcIfWatch() {
        val sbn = createSbn(TEXT_WITH_OTP)
        doReturn(true).whenKt(mockPm).hasSystemFeature(eq(FEATURE_WATCH))
        assistant.setUseTextClassifier()
        doReturn(false).whenKt(assistant).shouldUseTcForOtpDetection(any(), any())
        // Empty list of detected languages means that the notification language didn't match
        doReturn(TextLanguage.Builder().build())
            .whenKt(mockTc).detectLanguage(any())
        assistant.onNotificationEnqueued(sbn, NotificationChannel("0", "", IMPORTANCE_DEFAULT))
        Thread.sleep(EXECUTOR_AWAIT_TIME)
        verify(mockTc, never()).generateLinks(any())
        // Never calls generateLinks, but still gets an adjustment, due to regex
        verify(assistant, atLeast(1))
            .createNotificationAdjustment(any(), any(), any(), eq(true))
        verify(assistant.mSmartSuggestionsHelper, times(1)).onNotificationEnqueued(eq(sbn))
    }

    @Test
    fun onNotificationEnqueued_doesntUseTcIfLowRamDevice() {
        val sbn = createSbn(TEXT_WITH_OTP)
        doReturn(true).whenKt(mockAm).isLowRamDevice
        assistant.setUseTextClassifier()
        doReturn(false).whenKt(assistant).shouldUseTcForOtpDetection(any(), any())
        // Empty list of detected languages means that the notification language didn't match
        doReturn(TextLanguage.Builder().build())
            .whenKt(mockTc).detectLanguage(any())
        assistant.onNotificationEnqueued(sbn, NotificationChannel("0", "", IMPORTANCE_DEFAULT))
        Thread.sleep(EXECUTOR_AWAIT_TIME)
        verify(mockTc, never()).generateLinks(any())
        verify(assistant, atLeast(1))
            .createNotificationAdjustment(any(), any(), any(), eq(true))
        verify(assistant.mSmartSuggestionsHelper, times(1)).onNotificationEnqueued(eq(sbn))
    }

    @Test
    fun onNotificationEnqueued_usesHelperToGetText() {
        var sensitiveString: String? = null
        doAnswer { invocation: InvocationOnMock ->
            val request = invocation.getArgument<TextLinks.Request>(0)
            sensitiveString = request.text.toString()
            return@doAnswer TextLinks.Builder(request.text.toString())

        }.whenKt(mockTc).generateLinks(any())
        doReturn(true).whenKt(assistant).shouldUseTcForOtpDetection(any(), any())
        val sbn = createSbn(text = "text", title = "title", subtext = "subtext")
        assistant.onNotificationEnqueued(sbn, NotificationChannel("0", "", IMPORTANCE_DEFAULT))
        Thread.sleep(EXECUTOR_AWAIT_TIME)
        val expectedText = NotificationOtpDetectionHelper.getTextForDetection(sbn.notification)
        assertWithMessage("Expected sensitive text to be $expectedText, but was $sensitiveString")
            .that(sensitiveString).isEqualTo(expectedText)
    }

    @Test
    fun onNotificationEnqueued_checksHelperBeforeClassifying() {
        doReturn(true).whenKt(assistant).shouldUseTcForOtpDetection(any(), any())
        // Category, Style, Regex all don't match
        var sbn = createSbn(text = "text", title = "title", subtext = "subtext", category = "")
        assistant.onNotificationEnqueued(sbn, NotificationChannel("0", "", IMPORTANCE_DEFAULT))
        Thread.sleep(EXECUTOR_AWAIT_TIME)
        verify(mockTc, times(0)).generateLinks(any())
        // Category matching is checked implicitly in other tests
        // Style matches
        sbn = createSbn(text = "text", title = "title", subtext = "subtext", category = "",
            style = Notification.InboxStyle())
        assistant.onNotificationEnqueued(sbn, NotificationChannel("0", "", IMPORTANCE_DEFAULT))
        Thread.sleep(EXECUTOR_AWAIT_TIME)
        verify(mockTc, times(1)).generateLinks(any())
        // Regex matches
        sbn = createSbn(text = "87THF4", title = "title", subtext = "subtext", category = "")
        assistant.onNotificationEnqueued(sbn, NotificationChannel("0", "", IMPORTANCE_DEFAULT))
        Thread.sleep(EXECUTOR_AWAIT_TIME)
        verify(mockTc, times(2)).generateLinks(any())
    }

    @Test
    fun onNotificationEnqueued_noSensitiveAdjustmentIfConfidenceLow() {
        val sbn = createSbn()
        doReturn(TextLinks.Builder("   ").addLink(0, 1, mapOf(TYPE_OTP_CODE to 0.59f)).build())
            .whenKt(mockTc).generateLinks(any(TextLinks.Request::class.java))
        doReturn(true).whenKt(assistant).shouldUseTcForOtpDetection(any(), any())
        assistant.onNotificationEnqueued(sbn, NotificationChannel("0", "", IMPORTANCE_DEFAULT))
        Thread.sleep(EXECUTOR_AWAIT_TIME)
        verify(assistant, atLeast(1)).createNotificationAdjustment(
            any(StatusBarNotification::class.java), any(), any(), eq(false))
    }

    @Test
    fun onNotificationEnqueued_sensitiveAdjustmentIfConfidenceHigh() {
        val sbn = createSbn()
        doReturn(TextLinks.Builder("   ").addLink(0, 1, mapOf(TYPE_OTP_CODE to 0.7f)).build())
            .whenKt(mockTc).generateLinks(any(TextLinks.Request::class.java))
        doReturn(true).whenKt(assistant).shouldUseTcForOtpDetection(any(), any())
        assistant.onNotificationEnqueued(sbn, NotificationChannel("0", "", IMPORTANCE_DEFAULT))
        Thread.sleep(EXECUTOR_AWAIT_TIME)
        verify(assistant, atLeast(1)).createNotificationAdjustment(
            any(StatusBarNotification::class.java), any(), any(), eq(true))
    }
    @Test
    fun createEnqueuedNotificationAdjustment_hasAdjustmentIfCheckedForOtpCode() {
        val adjustment = assistant.createNotificationAdjustment(
            createSbn(),
            arrayListOf<Notification.Action>(),
            arrayListOf<CharSequence>(),
            true)
        assertThat(adjustment.signals.getBoolean(KEY_SENSITIVE_CONTENT)).isTrue()
        val adjustment2 = assistant.createNotificationAdjustment(
            createSbn(),
            arrayListOf<Notification.Action>(),
            arrayListOf<CharSequence>(),
            false)
        assertThat(adjustment2.signals.getBoolean(KEY_SENSITIVE_CONTENT)).isFalse()
        val adjustment3 = assistant.createNotificationAdjustment(
            createSbn(),
            arrayListOf<Notification.Action>(),
            arrayListOf<CharSequence>(),
            null)
        assertThat(adjustment3.signals.containsKey(KEY_SENSITIVE_CONTENT)).isFalse()
    }

    private fun createSbn(
        text: String = "",
        title: String = "",
        subtext: String = "",
        category: String = CATEGORY_MESSAGE,
        style: Notification.Style? = null
    ): StatusBarNotification {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.setFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    or Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        intent.setAction(Intent.ACTION_MAIN)
        intent.setPackage(context.packageName)

        val nb = Notification.Builder(context, "")
        nb.setContentText(text)
        nb.setContentTitle(title)
        nb.setSubText(subtext)
        nb.setCategory(category)
        nb.setContentIntent(createTestPendingIntent())
        if (style != null) {
            nb.setStyle(style)
        }
        return StatusBarNotification(context.packageName, context.packageName, 0, "",
            Process.myUid(), 0, 0, nb.build(), Process.myUserHandle(), System.currentTimeMillis())
    }

    private fun createTestPendingIntent(): PendingIntent {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.setFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    or Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        intent.setAction(Intent.ACTION_MAIN)
        intent.setPackage(context.packageName)

        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_MUTABLE)
    }

    companion object {
        const val TEXT_WITH_OTP = "Your login code is 345454"
    }

}
