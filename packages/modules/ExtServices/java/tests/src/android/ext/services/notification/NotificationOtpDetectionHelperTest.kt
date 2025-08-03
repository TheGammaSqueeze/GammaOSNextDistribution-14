/**
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

package android.ext.services.notification

import android.app.Notification
import android.app.Notification.CATEGORY_EMAIL
import android.app.Notification.CATEGORY_MESSAGE
import android.app.Notification.CATEGORY_SOCIAL
import android.app.Notification.EXTRA_TEXT
import android.app.PendingIntent
import android.app.Person
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.android.modules.utils.build.SdkLevel
import android.platform.test.flag.junit.SetFlagsRule
import android.service.notification.Flags.FLAG_REDACT_SENSITIVE_NOTIFICATIONS_FROM_UNTRUSTED_LISTENERS
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class NotificationOtpDetectionHelperTest {
    val context = InstrumentationRegistry.getInstrumentation().targetContext!!

    @get:Rule
    val setFlagsRule = if (SdkLevel.isAtLeastV()) {
        SetFlagsRule()
    } else {
        // On < V, have a test rule that does nothing
        TestRule { statement, description -> statement}
    }

    @Before
    fun enableFlag() {
        assumeTrue(SdkLevel.isAtLeastV())
    }

    @Test
    fun testGetTextForDetection_emptyIfFlagDisabled() {
        (setFlagsRule as SetFlagsRule)
            .disableFlags(FLAG_REDACT_SENSITIVE_NOTIFICATIONS_FROM_UNTRUSTED_LISTENERS)
        val text = "text"
        val title = "title"
        val subtext = "subtext"
        val sensitive = NotificationOtpDetectionHelper.getTextForDetection(
            createNotification(text = text, title = title, subtext = subtext))
        assertWithMessage("expected sensitive text to be empty").that(sensitive).isEmpty()
    }


    @Test
    fun testGetTextForDetection_textFieldsIncluded() {
        val text = "text"
        val title = "title"
        val subtext = "subtext"
        val sensitive = NotificationOtpDetectionHelper.getTextForDetection(
            createNotification(text = text, title = title, subtext = subtext))
        assertWithMessage("expected sensitive text to contain $text").that(sensitive).contains(text)
        assertWithMessage("expected sensitive text to contain $title").that(sensitive)
                .contains(title)
        assertWithMessage("expected sensitive text to contain $subtext").that(sensitive)
                .contains(subtext)
    }

    @Test
    fun testGetTextForDetection_nullTextFields() {
        val text = "text"
        val title = "title"
        val subtext = "subtext"
        var sensitive = NotificationOtpDetectionHelper.getTextForDetection(
            createNotification(text = text, title = null, subtext = null))
        assertWithMessage("expected sensitive text to contain $text").that(sensitive).contains(text)
        assertWithMessage("expected sensitive text not to contain $title").that(sensitive)
                .doesNotContain(title)
        assertWithMessage("expected sensitive text not to contain $subtext").that(sensitive)
                .doesNotContain(subtext)
        sensitive = NotificationOtpDetectionHelper.getTextForDetection(
            createNotification(text = null, title = null, subtext = null))
        assertWithMessage("expected to get a nonnull string").that(sensitive).isNotNull()
        val nullExtras = createNotification(text = null, title = null, subtext = null).apply {
            this.extras = null
        }
       sensitive = NotificationOtpDetectionHelper.getTextForDetection(nullExtras)
        assertWithMessage("expected to get a nonnull string").that(sensitive).isNotNull()
    }

    @Test
    fun testGetTextForDetection_messagesIncludedSorted() {
        val empty = Person.Builder().setName("test name").build()
        val messageText1 = "message text 1"
        val messageText2 = "message text 2"
        val messageText3 = "message text 3"
        val timestamp1 = 0L
        val timestamp2 = 1000L
        val timestamp3 = 50L
        val message1 =
            Notification.MessagingStyle.Message(messageText1,
                timestamp1,
                empty)
        val message2 =
            Notification.MessagingStyle.Message(messageText2,
                timestamp2,
                empty)
        val message3 =
            Notification.MessagingStyle.Message(messageText3,
                timestamp3,
                empty)
        val style = Notification.MessagingStyle(empty).apply {
            addMessage(message1)
            addMessage(message2)
            addMessage(message3)
        }
        val notif = createNotification(style = style)
        val sensitive = NotificationOtpDetectionHelper.getTextForDetection(notif)
        assertWithMessage("expected sensitive text to contain $messageText1").that(sensitive)
                .contains(messageText1)
        assertWithMessage("expected sensitive text to contain $messageText2").that(sensitive)
                .contains(messageText2)
        assertWithMessage("expected sensitive text to contain $messageText3").that(sensitive)
                .contains(messageText3)

        // MessagingStyle notifications get their main text set automatically to their first
        // message, so we should skip to the end of that to find the message text
        val notifText = notif.extras.getCharSequence(EXTRA_TEXT)?.toString() ?: ""
        val messagesSensitiveStartIdx = sensitive.indexOf(notifText) + notifText.length
        val sensitiveSub = sensitive.substring(messagesSensitiveStartIdx)
        val text1Position = sensitiveSub.indexOf(messageText1)
        val text2Position = sensitiveSub.indexOf(messageText2)
        val text3Position = sensitiveSub.indexOf(messageText3)
        // The messages should be sorted by timestamp, newest first, so 2 -> 3 -> 1
        assertWithMessage("expected the newest message (2) to be first in \"$sensitiveSub\"")
                .that(text2Position).isLessThan(text1Position)
        assertWithMessage("expected the newest message (2) to be first in \"$sensitiveSub\"")
                .that(text2Position).isLessThan(text3Position)
        assertWithMessage("expected the middle message (3) to be center in \"$sensitiveSub\"")
                .that(text3Position).isLessThan(text1Position)
    }

    @Test
    fun testGetTextForDetection_textLinesIncluded() {
        val style = Notification.InboxStyle()
        val extraLine = "extra line"
        style.addLine(extraLine)
        val sensitive = NotificationOtpDetectionHelper
                .getTextForDetection(createNotification(style = style))
        assertWithMessage("expected sensitive text to contain $extraLine").that(sensitive)
                .contains(extraLine)
    }

    @Test
    fun testGetTextForDetection_maxLen() {
        val text = "0123456789".repeat(70) // 700 chars
        val sensitive =
            NotificationOtpDetectionHelper.getTextForDetection(createNotification(text = text))
        assertWithMessage("Expected to be 600 chars or fewer").that(sensitive.length).isAtMost(600)
    }

    @Test
    fun testShouldCheckForOtp_falseIfFlagDisabled() {
        (setFlagsRule as SetFlagsRule)
            .disableFlags(FLAG_REDACT_SENSITIVE_NOTIFICATIONS_FROM_UNTRUSTED_LISTENERS)
        val shouldCheck = NotificationOtpDetectionHelper
            .shouldCheckForOtp(createNotification(category = CATEGORY_MESSAGE))
        assertWithMessage("$CATEGORY_MESSAGE should not be checked").that(shouldCheck).isFalse()
    }


    @Test
    fun testShouldCheckForOtp_styles() {
        val style = Notification.InboxStyle()
        var shouldCheck = NotificationOtpDetectionHelper
                .shouldCheckForOtp(createNotification(style = style))
        assertWithMessage("InboxStyle should be checked").that(shouldCheck).isTrue()
        val empty = Person.Builder().setName("test").build()
        val style2 = Notification.MessagingStyle(empty)
        val style3 = Notification.BigPictureStyle()
        shouldCheck = NotificationOtpDetectionHelper
                .shouldCheckForOtp(createNotification(style = style2))
        assertWithMessage("MessagingStyle should be checked").that(shouldCheck).isTrue()
        shouldCheck = NotificationOtpDetectionHelper
                .shouldCheckForOtp(createNotification())
        assertWithMessage("No style should not be checked").that(shouldCheck).isFalse()
        shouldCheck = NotificationOtpDetectionHelper
                .shouldCheckForOtp(createNotification(style = style3))
        assertWithMessage("Valid non-messaging non-inbox style should not be checked")
                .that(shouldCheck).isFalse()
    }

    @Test
    fun testShouldCheckForOtp_categories() {
        var shouldCheck = NotificationOtpDetectionHelper
                .shouldCheckForOtp(createNotification(category = CATEGORY_MESSAGE))
        assertWithMessage("$CATEGORY_MESSAGE should be checked").that(shouldCheck).isTrue()
        shouldCheck = NotificationOtpDetectionHelper
                .shouldCheckForOtp(createNotification(category = CATEGORY_SOCIAL))
        assertWithMessage("$CATEGORY_SOCIAL should be checked").that(shouldCheck).isTrue()
        shouldCheck = NotificationOtpDetectionHelper
                .shouldCheckForOtp(createNotification(category = CATEGORY_EMAIL))
        assertWithMessage("$CATEGORY_EMAIL should be checked").that(shouldCheck).isTrue()
        shouldCheck = NotificationOtpDetectionHelper
                .shouldCheckForOtp(createNotification(category = ""))
        assertWithMessage("Empty string category should not be checked").that(shouldCheck).isFalse()
    }

    @Test
    fun testShouldCheckForOtp_regex() {
        var shouldCheck = NotificationOtpDetectionHelper
                .shouldCheckForOtp(createNotification(text = OTP_MESSAGE_BASIC, category = ""))
        assertWithMessage("Regex matches should be checked").that(shouldCheck).isTrue()
    }

    @Test
    fun testShouldCheckForOtp_publicVersion() {
        var publicVersion = createNotification(category = CATEGORY_MESSAGE)
        var shouldCheck = NotificationOtpDetectionHelper
                .shouldCheckForOtp(createNotification(publicVersion = publicVersion))
        assertWithMessage("notifications with a checked category in their public version should " +
                "be checked").that(shouldCheck).isTrue()
        publicVersion = createNotification(style = Notification.InboxStyle())
        shouldCheck = NotificationOtpDetectionHelper
            .shouldCheckForOtp(createNotification(publicVersion = publicVersion))
        assertWithMessage("notifications with a checked style in their public version should " +
                "be checked").that(shouldCheck).isTrue()
    }


    @Test
    fun testMatchesOtpRegex_length() {
        val tooShortAlphaNum = "123G"
        val tooShortNumOnly = "123"
        val minLenAlphaNum = "123G5"
        val minLenNumOnly = "123G5"
        val maxLen = "123456F8"
        val tooLong = "123T56789"
        assertWithMessage("$minLenAlphaNum should match").that(matches(minLenAlphaNum)).isTrue()
        assertWithMessage("$minLenNumOnly should match").that(matches(minLenNumOnly)).isTrue()
        assertWithMessage("$maxLen should match").that(matches(maxLen)).isTrue()
        assertWithMessage("$tooShortAlphaNum should not match (too short)")
            .that(matches(tooShortAlphaNum)).isFalse()
        assertWithMessage("$tooShortNumOnly should not match (too short)")
            .that(matches(tooShortNumOnly)).isFalse()
        assertWithMessage("$tooLong should not match (too long)").that(matches(tooLong)).isFalse()
    }

    @Test
    fun testMatchesOtpRegex_mustHaveNumber() {
        val noNums = "TEFHXES"
        assertWithMessage("$noNums should not match").that(matches(noNums)).isFalse()
    }

    @Test
    fun testMatchesOtpRegex_commonYearsDontMatch() {
        val twentyXX = "2009"
        val twentyOneXX = "2109"
        val thirtyXX = "3035"
        val nineteenXX = "1945"
        val eighteenXX = "1899"
        assertWithMessage("$twentyXX should not match").that(matches(twentyXX)).isFalse()
        assertWithMessage("$twentyOneXX should match").that(matches(twentyOneXX)).isTrue()
        assertWithMessage("$thirtyXX should match").that(matches(thirtyXX)).isTrue()
        assertWithMessage("$nineteenXX should not match").that(matches(nineteenXX)).isFalse()
        assertWithMessage("$eighteenXX should match").that(matches(eighteenXX)).isTrue()
    }

    @Test
    fun testMatchesOtpRegex_dateExclusion() {
        val date = "01-01-2001"
        val singleDigitDate = "1-1-2001"
        val twoDigitYear = "1-1-01"
        val dateWithOtpAfter = "1-1-01 is the date of your code T3425"
        val dateWithOtpBefore = "your code 54-234-3 was sent on 1-1-01"
        val otpWithDashesButInvalidDate = "34-58-30"
        val otpWithDashesButInvalidYear = "12-1-3089"

        assertWithMessage("$date should match if ensureNotDate is false")
                .that(matches(date, false)).isTrue()
        assertWithMessage("$date should not match if ensureNotDate is true")
                .that(matches(date, true)).isFalse()
        assertWithMessage("$singleDigitDate should not match").that(matches(singleDigitDate))
                .isFalse()
        assertWithMessage("$twoDigitYear should not match").that(matches(twoDigitYear)).isFalse()
        assertWithMessage("$dateWithOtpAfter should match").that(matches(dateWithOtpAfter)).isTrue()
        assertWithMessage("$dateWithOtpBefore should match").that(matches(dateWithOtpBefore))
                .isTrue()
        assertWithMessage("$otpWithDashesButInvalidDate should match").that(matches(
            otpWithDashesButInvalidDate)).isTrue()
        assertWithMessage("$otpWithDashesButInvalidYear should match").that(matches(
            otpWithDashesButInvalidYear)).isTrue()
    }

    @Test
    fun testMatchesOtpRegex_dashes() {
        val oneDash = "G-3d523"
        val manyDashes = "G-FD-745"
        val tooManyDashes = "6--7893"
        val oopsAllDashes = "------"
        assertWithMessage("$oneDash should match").that(matches(oneDash)).isTrue()
        assertWithMessage("$manyDashes should match").that(matches(manyDashes)).isTrue()
        assertWithMessage("$tooManyDashes should not match").that(matches(tooManyDashes)).isFalse()
        assertWithMessage("$oopsAllDashes should not match").that(matches(oopsAllDashes)).isFalse()
    }

    @Test
    fun testMatchesOtpRegex_startAndEnd() {
        val noSpaceStart = "your code isG-345821"
        val noSpaceEnd = "your code is G-345821for real"
        val colonStart = "your code is:G-345821"
        val parenStart = "your code is (G-345821"
        val newLineStart = "your code is \nG-345821"
        val periodEnd = "you code is G-345821."
        val parenEnd = "you code is (G-345821)"
        assertWithMessage("$noSpaceStart should not match").that(matches(noSpaceStart)).isFalse()
        assertWithMessage("$noSpaceEnd should not match").that(matches(noSpaceEnd)).isFalse()
        assertWithMessage("$colonStart should match").that(matches(colonStart)).isTrue()
        assertWithMessage("$parenStart should match").that(matches(parenStart)).isTrue()
        assertWithMessage("$newLineStart should match").that(matches(newLineStart)).isTrue()
        assertWithMessage("$periodEnd should match").that(matches(periodEnd)).isTrue()
        assertWithMessage("$parenEnd should match").that(matches(parenEnd)).isTrue()
    }

    @Test
    fun testMatchesOtpRegex_lookaheadMustBeOtpChar() {
        val validLookahead = "g4zy75"
        val spaceLookahead = "GVRXY 2"
        assertWithMessage("$validLookahead should match").that(matches(validLookahead)).isTrue()
        assertWithMessage("$spaceLookahead should not match")
            .that(matches(spaceLookahead)).isFalse()
    }


    private fun createNotification(
        text: String? = "",
        title: String? = "",
        subtext: String? = "",
        category: String? = "",
        style: Notification.Style? = null,
        publicVersion: Notification? = null
    ): Notification {
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
        if (publicVersion != null) {
            nb.setPublicVersion(publicVersion)
        }
        return nb.build()
    }

    private fun matches(text: String, ensureNotDate: Boolean = true): Boolean {
        return NotificationOtpDetectionHelper
                .matchesOtpRegex(createNotification(text), ensureNotDate)
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
        private const val OTP_MESSAGE_BASIC = "your one time code is 123645"
    }
}