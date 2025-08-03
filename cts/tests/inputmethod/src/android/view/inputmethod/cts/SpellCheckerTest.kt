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
package android.view.inputmethod.cts

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.platform.test.annotations.AppModeSdkSandbox
import android.provider.Settings
import android.text.style.SuggestionSpan
import android.text.style.SuggestionSpan.FLAG_GRAMMAR_ERROR
import android.text.style.SuggestionSpan.FLAG_MISSPELLED
import android.text.style.SuggestionSpan.SUGGESTIONS_MAX_SIZE
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.cts.util.EndToEndImeTestBase
import android.view.inputmethod.cts.util.InputMethodVisibilityVerifier
import android.view.inputmethod.cts.util.TestActivity
import android.view.inputmethod.cts.util.TestUtils.runOnMainSync
import android.view.inputmethod.cts.util.TestUtils.waitOnMainUntil
import android.view.inputmethod.cts.util.UnlockScreenRule
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SpellCheckerSubtype
import android.view.textservice.SuggestionsInfo
import android.view.textservice.SuggestionsInfo.RESULT_ATTR_DONT_SHOW_UI_FOR_SUGGESTIONS
import android.view.textservice.SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY
import android.view.textservice.SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR
import android.view.textservice.SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import android.widget.EditText
import android.widget.LinearLayout
import androidx.annotation.UiThread
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.android.compatibility.common.util.CtsTouchUtils
import com.android.compatibility.common.util.PollingCheck
import com.android.compatibility.common.util.SettingsStateChangerRule
import com.android.compatibility.common.util.SystemUtil
import com.android.cts.mockime.ImeEventStreamTestUtils.expectCommand
import com.android.cts.mockime.MockImeSession
import com.android.cts.mockspellchecker.EXTRAS_KEY_PREFIX
import com.android.cts.mockspellchecker.MockSpellChecker
import com.android.cts.mockspellchecker.MockSpellCheckerClient
import com.android.cts.mockspellchecker.MockSpellCheckerProto
import com.android.cts.mockspellchecker.MockSpellCheckerProto.MockSpellCheckerConfiguration
import com.google.common.truth.Truth.assertThat
import java.lang.IllegalArgumentException
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.collections.ArrayList
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@MediumTest
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
class SpellCheckerTest : EndToEndImeTestBase() {

    private val TAG = "SpellCheckerTest"
    private val SPELL_CHECKING_IME_ID = "com.android.cts.spellcheckingime/.SpellCheckingIme"
    private val TIMEOUT = TimeUnit.SECONDS.toMillis(5)

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.getTargetContext()
    private val uiDevice: UiDevice = UiDevice.getInstance(instrumentation)
    private val uiAutomation: UiAutomation = instrumentation.uiAutomation
    private val ctsTouchUtils: CtsTouchUtils = CtsTouchUtils(context)

    @Rule
    fun unlockScreenRule() = UnlockScreenRule()

    @Rule
    fun spellCheckerSettingsRule() = SettingsStateChangerRule(
            context,
        Settings.Secure.SELECTED_SPELL_CHECKER,
        MockSpellChecker.getId()
    )

    @Rule
    fun spellCheckerSubtypeSettingsRule() = SettingsStateChangerRule(
            context,
        Settings.Secure.SELECTED_SPELL_CHECKER_SUBTYPE,
            SpellCheckerSubtype.SUBTYPE_ID_NONE.toString()
    )

    @Before
    fun setUp() {
        val tsm = context.getSystemService(TextServicesManager::class.java)!!
        // Skip if spell checker is not enabled by default.
        Assume.assumeNotNull(tsm)
        Assume.assumeTrue(tsm.isSpellCheckerEnabled)
    }

    @Test
    fun misspelled_easyCorrect() {
        val uniqueSuggestion = "s618397" // "s" + a random number
        val configuration = MockSpellCheckerConfiguration.newBuilder()
                .addSuggestionRules(
                        MockSpellCheckerProto.SuggestionRule.newBuilder()
                                .setMatch("match")
                                .addSuggestions(uniqueSuggestion)
                                .setAttributes(RESULT_ATTR_LOOKS_LIKE_TYPO)
                ).build()
        MockImeSession.create(context).use { session ->
            MockSpellCheckerClient.create(context, configuration).use {
                val (_, editText) = startTestActivity()
                ctsTouchUtils.emulateTapOnViewCenter(instrumentation, null, editText)
                waitOnMainUntil({ editText.hasFocus() }, TIMEOUT)
                InputMethodVisibilityVerifier.expectImeVisible(TIMEOUT)
                session.callCommitText("match", 1)
                session.callCommitText(" ", 1)
                waitOnMainUntil({
                    findSuggestionSpanWithFlags(editText, FLAG_MISSPELLED) != null
                }, TIMEOUT)
                // Tap inside 'match'.
                emulateTapAtOffset(editText, 2)
                // Wait until the cursor moves inside 'match'.
                waitOnMainUntil({ isCursorInside(editText, 1, 4) }, TIMEOUT)
                // Wait for the suggestion to come up, and click it.
                uiDevice.wait(Until.findObject(By.text(uniqueSuggestion)), TIMEOUT).also {
                    assertThat(it).isNotNull()
                }.click()
                // Verify that the text ('match') is replaced with the suggestion.
                waitOnMainUntil({ "$uniqueSuggestion " == editText.text.toString() }, TIMEOUT)
                // The SuggestionSpan should be removed.
                waitOnMainUntil({
                    findSuggestionSpanWithFlags(editText, FLAG_MISSPELLED) == null
                }, TIMEOUT)
            }
        }
    }

    @Test
    fun misspelled_noEasyCorrect() {
        val uniqueSuggestion = "s974355" // "s" + a random number
        val configuration = MockSpellCheckerConfiguration.newBuilder()
                .addSuggestionRules(
                        MockSpellCheckerProto.SuggestionRule.newBuilder()
                                .setMatch("match")
                                .addSuggestions(uniqueSuggestion)
                                .setAttributes(
                                    RESULT_ATTR_LOOKS_LIKE_TYPO
                                        or RESULT_ATTR_DONT_SHOW_UI_FOR_SUGGESTIONS
                                )
                ).build()
        MockImeSession.create(context).use { session ->
            MockSpellCheckerClient.create(context, configuration).use {
                val (_, editText) = startTestActivity()
                ctsTouchUtils.emulateTapOnViewCenter(instrumentation, null, editText)
                waitOnMainUntil({ editText.hasFocus() }, TIMEOUT)
                InputMethodVisibilityVerifier.expectImeVisible(TIMEOUT)
                session.callCommitText("match", 1)
                session.callCommitText(" ", 1)
                waitOnMainUntil({
                    findSuggestionSpanWithFlags(editText, FLAG_MISSPELLED) != null
                }, TIMEOUT)
                // Tap inside 'match'.
                emulateTapAtOffset(editText, 2)
                // Wait until the cursor moves inside 'match'.
                waitOnMainUntil({ isCursorInside(editText, 1, 4) }, TIMEOUT)
                // Verify that the suggestion is not shown.
                assertThat(uiDevice.wait(Until.gone(By.text(uniqueSuggestion)), TIMEOUT)).isTrue()
            }
        }
    }

    @Test
    fun grammarError() {
        val configuration = MockSpellCheckerConfiguration.newBuilder()
                .addSuggestionRules(
                        MockSpellCheckerProto.SuggestionRule.newBuilder()
                                .setMatch("match")
                                .addSuggestions("suggestion")
                                .setAttributes(RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR)
        ).build()
        MockImeSession.create(context).use { session ->
            MockSpellCheckerClient.create(context, configuration).use {
                val (_, editText) = startTestActivity()
                ctsTouchUtils.emulateTapOnViewCenter(instrumentation, null, editText)
                waitOnMainUntil({ editText.hasFocus() }, TIMEOUT)
                InputMethodVisibilityVerifier.expectImeVisible(TIMEOUT)
                session.callCommitText("match", 1)
                session.callCommitText(" ", 1)
                waitOnMainUntil({
                    findSuggestionSpanWithFlags(editText, FLAG_GRAMMAR_ERROR) != null
                }, TIMEOUT)
            }
        }
    }

    @Test
    fun performSpellCheck() {
        val configuration = MockSpellCheckerConfiguration.newBuilder()
                .addSuggestionRules(
                        MockSpellCheckerProto.SuggestionRule.newBuilder()
                                .setMatch("match")
                                .addSuggestions("suggestion")
                                .setAttributes(RESULT_ATTR_LOOKS_LIKE_TYPO)
                ).build()
        MockImeSession.create(context).use { session ->
            MockSpellCheckerClient.create(context, configuration).use { client ->
                val stream = session.openEventStream()
                val (_, editText) = startTestActivity()
                ctsTouchUtils.emulateTapOnViewCenter(instrumentation, null, editText)
                waitOnMainUntil({ editText.hasFocus() }, TIMEOUT)
                InputMethodVisibilityVerifier.expectImeVisible(TIMEOUT)
                session.callCommitText("match", 1)
                session.callCommitText(" ", 1)
                waitOnMainUntil({
                    findSuggestionSpanWithFlags(editText, FLAG_MISSPELLED) != null
                }, TIMEOUT)
                // The word is now in dictionary. The next spell check should remove the misspelled
                // SuggestionSpan.
                client.updateConfiguration(MockSpellCheckerConfiguration.newBuilder()
                        .addSuggestionRules(
                                MockSpellCheckerProto.SuggestionRule.newBuilder()
                                        .setMatch("match")
                                        .setAttributes(RESULT_ATTR_IN_THE_DICTIONARY)
                        ).build())
                val command = session.callPerformSpellCheck()
                expectCommand(stream, command, TIMEOUT)
                waitOnMainUntil({
                    findSuggestionSpanWithFlags(editText, FLAG_MISSPELLED) == null
                }, TIMEOUT)
            }
        }
    }

    @Test
    fun textServicesManagerApi() {
        val tsm = context.getSystemService(TextServicesManager::class.java)!!
        assertThat(tsm.isSpellCheckerEnabled).isTrue()
        val spellCheckerInfo = tsm.currentSpellCheckerInfo
        assertThat(spellCheckerInfo!!.packageName).isEqualTo(
                "com.android.cts.mockspellchecker"
        )
        assertThat(spellCheckerInfo.subtypeCount).isEqualTo(1)
        assertThat(tsm.enabledSpellCheckerInfos.size).isAtLeast(1)
        assertThat(tsm.enabledSpellCheckerInfos.map { it.getPackageName() })
                .contains("com.android.cts.mockspellchecker")
    }

    @Test
    fun newSpellCheckerSession() {
        val configuration = MockSpellCheckerConfiguration.newBuilder()
                .addSuggestionRules(
                        MockSpellCheckerProto.SuggestionRule.newBuilder()
                                .setMatch("match")
                                .addSuggestions("suggestion")
                                .setAttributes(RESULT_ATTR_LOOKS_LIKE_TYPO)
                ).build()
        // Use MockIme, in case the default IME sets android:suppressesSpellChecker="true"
        MockImeSession.create(context).use { _ ->
            MockSpellCheckerClient.create(context, configuration).use {
                val tsm = context.getSystemService(TextServicesManager::class.java)
                assertThat(tsm).isNotNull()
                val fakeListener = FakeSpellCheckerSessionListener()
                val fakeExecutor = FakeExecutor()
                val params = SpellCheckerSession.SpellCheckerSessionParams.Builder()
                        .setLocale(Locale.US)
                        .setSupportedAttributes(RESULT_ATTR_LOOKS_LIKE_TYPO)
                        .build()
                val session: SpellCheckerSession? = tsm?.newSpellCheckerSession(
                        params,
                    fakeExecutor,
                    fakeListener
                )
                assertThat(session).isNotNull()
                session?.getSentenceSuggestions(arrayOf(TextInfo("match")), 5)
                waitOnMainUntil({ fakeExecutor.runnables.size == 1 }, TIMEOUT)
                fakeExecutor.runnables[0].run()

                assertThat(fakeListener.getSentenceSuggestionsResults).hasSize(1)
                assertThat(fakeListener.getSentenceSuggestionsResults[0]).hasLength(1)
                val sentenceSuggestionsInfo = fakeListener.getSentenceSuggestionsResults[0]!![0]
                assertThat(sentenceSuggestionsInfo.suggestionsCount).isEqualTo(1)
                assertThat(sentenceSuggestionsInfo.getOffsetAt(0)).isEqualTo(0)
                assertThat(sentenceSuggestionsInfo.getLengthAt(0)).isEqualTo("match".length)
                val suggestionsInfo = sentenceSuggestionsInfo.getSuggestionsInfoAt(0)
                assertThat(suggestionsInfo.suggestionsCount).isEqualTo(1)
                assertThat(suggestionsInfo.getSuggestionAt(0)).isEqualTo("suggestion")

                assertThat(fakeListener.getSentenceSuggestionsResults).hasSize(1)
                assertThat(fakeListener.getSentenceSuggestionsCallingThreads).hasSize(1)
                assertThat(fakeListener.getSentenceSuggestionsCallingThreads[0])
                        .isEqualTo(Thread.currentThread())
            }
        }
    }

    @Test
    fun newSpellCheckerSession_implicitExecutor() {
        val configuration = MockSpellCheckerConfiguration.newBuilder()
                .addSuggestionRules(
                        MockSpellCheckerProto.SuggestionRule.newBuilder()
                                .setMatch("match")
                                .addSuggestions("suggestion")
                                .setAttributes(RESULT_ATTR_LOOKS_LIKE_TYPO)
                ).build()
        // Use MockIme, in case the default IME sets android:suppressesSpellChecker="true"
        MockImeSession.create(context).use { _ ->
            MockSpellCheckerClient.create(context, configuration).use {
                val tsm = context.getSystemService(TextServicesManager::class.java)
                assertThat(tsm).isNotNull()
                val fakeListener = FakeSpellCheckerSessionListener()
                var session: SpellCheckerSession? = null
                runOnMainSync {
                    @Suppress("ktlint:standard:comment-wrapping")
                    session = tsm?.newSpellCheckerSession(null /* bundle */, Locale.US,
                            fakeListener, false /* referToSpellCheckerLanguageSettings */)
                }
                assertThat(session).isNotNull()
                session?.getSentenceSuggestions(arrayOf(TextInfo("match")), 5)
                waitOnMainUntil({
                    fakeListener.getSentenceSuggestionsCallingThreads.size > 0
                }, TIMEOUT)
                runOnMainSync {
                    assertThat(fakeListener.getSentenceSuggestionsCallingThreads).hasSize(1)
                    assertThat(fakeListener.getSentenceSuggestionsCallingThreads[0])
                            .isEqualTo(Looper.getMainLooper().thread)
                }
            }
        }
    }

    @Test
    fun newSpellCheckerSession_extras() {
        val configuration = MockSpellCheckerConfiguration.newBuilder()
                .addSuggestionRules(
                        MockSpellCheckerProto.SuggestionRule.newBuilder()
                                .setMatch("match")
                                .addSuggestions("suggestion")
                                .setAttributes(RESULT_ATTR_LOOKS_LIKE_TYPO)
                ).build()
        // Use MockIme, in case the default IME sets android:suppressesSpellChecker="true"
        MockImeSession.create(context).use { _ ->
            MockSpellCheckerClient.create(context, configuration).use {
                val tsm = context.getSystemService(TextServicesManager::class.java)
                assertThat(tsm).isNotNull()
                val fakeListener = FakeSpellCheckerSessionListener()
                val fakeExecutor = FakeExecutor()
                // Set a prefix. MockSpellChecker will add "test_" to the spell check result.
                val extras = Bundle()
                extras.putString(EXTRAS_KEY_PREFIX, "test_")
                val params = SpellCheckerSession.SpellCheckerSessionParams.Builder()
                        .setLocale(Locale.US)
                        .setSupportedAttributes(RESULT_ATTR_LOOKS_LIKE_TYPO)
                        .setExtras(extras)
                        .build()
                val session: SpellCheckerSession? = tsm?.newSpellCheckerSession(
                        params,
                    fakeExecutor,
                    fakeListener
                )
                assertThat(session).isNotNull()
                session?.getSentenceSuggestions(arrayOf(TextInfo("match")), 5)
                waitOnMainUntil({ fakeExecutor.runnables.size == 1 }, TIMEOUT)
                fakeExecutor.runnables[0].run()

                assertThat(fakeListener.getSentenceSuggestionsResults).hasSize(1)
                assertThat(fakeListener.getSentenceSuggestionsResults[0]).hasLength(1)
                val sentenceSuggestionsInfo = fakeListener.getSentenceSuggestionsResults[0]!![0]
                assertThat(sentenceSuggestionsInfo.suggestionsCount).isEqualTo(1)
                val suggestionsInfo = sentenceSuggestionsInfo.getSuggestionsInfoAt(0)
                assertThat(suggestionsInfo.suggestionsCount).isEqualTo(1)
                assertThat(suggestionsInfo.getSuggestionAt(0)).isEqualTo("test_suggestion")
            }
        }
    }

    @Test
    fun spellCheckerSessionParamsBuilder() {
        // Locale or shouldReferToSpellCheckerLanguageSettings should be set.
        assertThrows(IllegalArgumentException::class.java) {
            SpellCheckerSession.SpellCheckerSessionParams.Builder().build()
        }

        // Test defaults.
        val localeOnly = SpellCheckerSession.SpellCheckerSessionParams.Builder()
                .setLocale(Locale.US)
                .build()
        assertThat(localeOnly.locale).isEqualTo(Locale.US)
        assertThat(localeOnly.shouldReferToSpellCheckerLanguageSettings()).isFalse()
        assertThat(localeOnly.supportedAttributes).isEqualTo(0)
        assertThat(localeOnly.extras).isNotNull()
        assertThat(localeOnly.extras.size()).isEqualTo(0)

        // Test setters.
        val extras = Bundle()
        extras.putString("key", "value")
        val params = SpellCheckerSession.SpellCheckerSessionParams.Builder()
                .setLocale(Locale.CANADA)
                .setShouldReferToSpellCheckerLanguageSettings(true)
                .setSupportedAttributes(RESULT_ATTR_LOOKS_LIKE_TYPO)
                .setExtras(extras)
                .build()
        assertThat(params.locale).isEqualTo(Locale.CANADA)
        assertThat(params.shouldReferToSpellCheckerLanguageSettings()).isTrue()
        assertThat(params.supportedAttributes).isEqualTo(RESULT_ATTR_LOOKS_LIKE_TYPO)
        // Bundle does not implement equals.
        assertThat(params.extras).isNotNull()
        assertThat(params.extras.size()).isEqualTo(1)
        assertThat(params.extras.getString("key")).isEqualTo("value")
    }

    @Test
    fun suppressesSpellChecker() {
        val configuration = MockSpellCheckerConfiguration.newBuilder()
                .addSuggestionRules(
                        MockSpellCheckerProto.SuggestionRule.newBuilder()
                                .setMatch("match")
                                .addSuggestions("suggestion")
                                .setAttributes(RESULT_ATTR_LOOKS_LIKE_TYPO)
                ).build()
        // SpellCheckingIme should have android:suppressesSpellChecker="true"
        ImeSession(SPELL_CHECKING_IME_ID).use {
            assertThat(getCurrentInputMethodInfo().suppressesSpellChecker()).isTrue()

            MockSpellCheckerClient.create(context, configuration).use {
                val (activity, editText) = startTestActivity()
                ctsTouchUtils.emulateTapOnViewCenter(instrumentation, null, editText)
                val imm = activity.getSystemService(InputMethodManager::class.java)
                waitOnMainUntil({ editText.hasFocus() &&
                    imm.hasActiveInputConnection(editText) }, TIMEOUT)
                assertThat(imm?.isInputMethodSuppressingSpellChecker).isTrue()

                // SpellCheckerSession should return empty results if suppressed.
                val tsm = activity.getSystemService(TextServicesManager::class.java)
                val listener = FakeSpellCheckerSessionListener()
                var session: SpellCheckerSession? = null
                runOnMainSync {
                    session = tsm?.newSpellCheckerSession(null, Locale.US, listener, false)
                }
                assertThat(session).isNotNull()
                val suggestions: Array<SentenceSuggestionsInfo>? =
                        getSentenceSuggestions(session!!, listener, "match")
                assertThat(suggestions).isNotNull()
                assertThat(suggestions!!.size).isEqualTo(0)
            }
        }
    }

    @Test
    fun suppressesSpellChecker_false() {
        MockImeSession.create(context).use {
            assertThat(getCurrentInputMethodInfo().suppressesSpellChecker()).isFalse()

            val (activity, editText) = startTestActivity()
            ctsTouchUtils.emulateTapOnViewCenter(instrumentation, null, editText)
            val imm = activity.getSystemService(InputMethodManager::class.java)
            waitOnMainUntil({ editText.hasFocus() &&
                imm.hasActiveInputConnection(editText) }, TIMEOUT)
            assertThat(imm?.isInputMethodSuppressingSpellChecker).isFalse()
        }
    }

    @Test
    fun suppressesSpellChecker_unbind() {
        val configuration = MockSpellCheckerConfiguration.newBuilder()
                .addSuggestionRules(
                        MockSpellCheckerProto.SuggestionRule.newBuilder()
                                .setMatch("match")
                                .addSuggestions("suggestion")
                                .setAttributes(RESULT_ATTR_LOOKS_LIKE_TYPO)
                ).build()
        // SpellCheckingIme should have android:suppressesSpellChecker="true"
        ImeSession(SPELL_CHECKING_IME_ID).use {
            assertThat(getCurrentInputMethodInfo().suppressesSpellChecker()).isTrue()

            MockSpellCheckerClient.create(context, configuration).use {
                val (activity, editText) = startTestActivity()
                ctsTouchUtils.emulateTapOnViewCenter(instrumentation, null, editText)
                val imm = activity.getSystemService(InputMethodManager::class.java)
                waitOnMainUntil({ editText.hasFocus() &&
                        imm.hasActiveInputConnection(editText) }, TIMEOUT)
                assertThat(imm?.isInputMethodSuppressingSpellChecker).isTrue()

                // Unbind the SpellCheckingIme. Use MockIme in case the default IME sets
                // android:suppressesSpellChecker="true"
                MockImeSession.create(context).use {
                    PollingCheck.check("Make sure the SpellCheckingIme is not selected", TIMEOUT) {
                        getCurrentInputMethodInfo().id != SPELL_CHECKING_IME_ID
                    }
                    assertThat(imm?.isInputMethodSuppressingSpellChecker).isFalse()
                }
            }
        }
    }

    @Test
    fun trailingPunctuation() {
        // Set up a rule that matches the sentence "match?" and marks it as grammar error.
        val configuration = MockSpellCheckerConfiguration.newBuilder()
                .setMatchSentence(true)
                .addSuggestionRules(
                        MockSpellCheckerProto.SuggestionRule.newBuilder()
                                .setMatch("match?")
                                .addSuggestions("suggestion.")
                                .setAttributes(RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR)
                ).build()
        MockImeSession.create(context).use { session ->
            MockSpellCheckerClient.create(context, configuration).use { _ ->
                val (_, editText) = startTestActivity()
                ctsTouchUtils.emulateTapOnViewCenter(instrumentation, null, editText)
                waitOnMainUntil({ editText.hasFocus() }, TIMEOUT)
                InputMethodVisibilityVerifier.expectImeVisible(TIMEOUT)
                session.callCommitText("match", 1)
                // The trailing punctuation "?" is also sent in the next spell check, and the
                // sentence "match?" will be marked as FLAG_GRAMMAR_ERROR according to the
                // configuration.
                session.callCommitText("?", 1)
                waitOnMainUntil({
                    findSuggestionSpanWithFlags(editText, FLAG_GRAMMAR_ERROR) != null
                }, TIMEOUT)
            }
        }
    }

    @Test
    fun newSpellCheckerSession_processPurePunctuationRequest() {
        val configuration = MockSpellCheckerConfiguration.newBuilder()
                .addSuggestionRules(
                        MockSpellCheckerProto.SuggestionRule.newBuilder()
                                .setMatch("foo")
                                .addSuggestions("suggestion")
                                .setAttributes(RESULT_ATTR_LOOKS_LIKE_TYPO)
                ).build()
        // Use MockIme, in case the default IME sets android:suppressesSpellChecker="true"
        MockImeSession.create(context).use { _ ->
            MockSpellCheckerClient.create(context, configuration).use {
                val tsm = context.getSystemService(TextServicesManager::class.java)
                assertThat(tsm).isNotNull()
                val fakeListener = FakeSpellCheckerSessionListener()
                val fakeExecutor = FakeExecutor()
                val params = SpellCheckerSession.SpellCheckerSessionParams.Builder()
                        .setLocale(Locale.US)
                        .setSupportedAttributes(RESULT_ATTR_LOOKS_LIKE_TYPO)
                        .build()
                var session: SpellCheckerSession? = tsm?.newSpellCheckerSession(
                        params,
                    fakeExecutor,
                    fakeListener
                )
                assertThat(session).isNotNull()
                session?.getSentenceSuggestions(arrayOf(TextInfo(". ")), 5)
                waitOnMainUntil({ fakeExecutor.runnables.size == 1 }, TIMEOUT)
                fakeExecutor.runnables[0].run()
                assertThat(fakeListener.getSentenceSuggestionsResults).hasSize(1)
                assertThat(fakeListener.getSentenceSuggestionsResults[0]).hasLength(1)
                assertThat(fakeListener.getSentenceSuggestionsResults[0]!![0]).isNull()
            }
        }
    }

    @Test
    fun respectSentenceBoundary() {
        // Set up two rules:
        // - Matches the sentence "Preceding text?" and marks it as grammar error.
        // - Matches the sentence "match?" and marks it as misspelled.
        val configuration = MockSpellCheckerConfiguration.newBuilder()
                .setMatchSentence(true)
                .addSuggestionRules(
                        MockSpellCheckerProto.SuggestionRule.newBuilder()
                                .setMatch("Preceding text?")
                                .addSuggestions("suggestion.")
                                .setAttributes(RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR)
                ).addSuggestionRules(
                        MockSpellCheckerProto.SuggestionRule.newBuilder()
                                .setMatch("match?")
                                .addSuggestions("suggestion.")
                                .setAttributes(RESULT_ATTR_LOOKS_LIKE_TYPO)
                ).build()
        MockImeSession.create(context).use { session ->
            MockSpellCheckerClient.create(context, configuration).use { _ ->
                val (_, editText) = startTestActivity()
                ctsTouchUtils.emulateTapOnViewCenter(instrumentation, null, editText)
                waitOnMainUntil({ editText.hasFocus() }, TIMEOUT)
                InputMethodVisibilityVerifier.expectImeVisible(TIMEOUT)
                session.callCommitText("Preceding text", 1)
                session.callCommitText("?", 1)
                waitOnMainUntil({
                    findSuggestionSpanWithFlags(editText, FLAG_GRAMMAR_ERROR) != null
                }, TIMEOUT)
                // The next spell check only contains the text after "Preceding text?". According
                // to our configuration, the sentence "match?" will be marked as FLAG_MISSPELLED.
                session.callCommitText("match", 1)
                session.callCommitText("?", 1)
                waitOnMainUntil({
                    findSuggestionSpanWithFlags(editText, FLAG_MISSPELLED) != null
                }, TIMEOUT)
            }
        }
    }

    @Test
    fun removePreviousSuggestion() {
        // Set up two rules:
        // - Matches the sentence "Wrong context word?" and marks "word" as grammar error.
        // - Matches the sentence "Correct context word?" and marks "word" as in-vocabulary.
        val configuration = MockSpellCheckerConfiguration.newBuilder()
                .setMatchSentence(true)
                .addSuggestionRules(
                        MockSpellCheckerProto.SuggestionRule.newBuilder()
                                .setMatch("Wrong context word?")
                                .addSuggestions("suggestion")
                                .setStartOffset(14)
                                .setLength(4)
                                .setAttributes(RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR)
                ).addSuggestionRules(
                        MockSpellCheckerProto.SuggestionRule.newBuilder()
                                .setMatch("Correct context word?")
                                .setStartOffset(16)
                                .setLength(4)
                                .setAttributes(RESULT_ATTR_IN_THE_DICTIONARY)
                ).build()
        MockImeSession.create(context).use { session ->
            MockSpellCheckerClient.create(context, configuration).use { _ ->
                val (_, editText) = startTestActivity()
                ctsTouchUtils.emulateTapOnViewCenter(instrumentation, null, editText)
                waitOnMainUntil({ editText.hasFocus() }, TIMEOUT)
                InputMethodVisibilityVerifier.expectImeVisible(TIMEOUT)
                session.callCommitText("Wrong context word", 1)
                session.callCommitText("?", 1)
                waitOnMainUntil({
                    findSuggestionSpanWithFlags(editText, FLAG_GRAMMAR_ERROR) != null
                }, TIMEOUT)
                // Change "Wrong" to "Correct" and then trigger spell check.
                session.callSetSelection(0, 5) // Select "Wrong"
                session.callCommitText("Correct", 1)
                session.callPerformSpellCheck()
                waitOnMainUntil({
                    findSuggestionSpanWithFlags(editText, FLAG_GRAMMAR_ERROR) == null
                }, TIMEOUT)
            }
        }
    }

    @Test
    fun ignoreInvalidSuggestions() {
        // Set up a wrong rule:
        // - Matches the sentence "Context word" and marks "word" as grammar error.
        val configuration = MockSpellCheckerConfiguration.newBuilder()
                .setMatchSentence(true)
                .addSuggestionRules(
                        MockSpellCheckerProto.SuggestionRule.newBuilder()
                                .setMatch("Context word")
                                .addSuggestions("suggestion")
                                .setStartOffset(8)
                                .setLength(5) // Should be 4
                                .setAttributes(RESULT_ATTR_LOOKS_LIKE_TYPO)
                ).build()
        MockImeSession.create(context).use { session ->
            MockSpellCheckerClient.create(context, configuration).use { _ ->
                val (_, editText) = startTestActivity()
                ctsTouchUtils.emulateTapOnViewCenter(instrumentation, null, editText)
                waitOnMainUntil({ editText.hasFocus() }, TIMEOUT)
                InputMethodVisibilityVerifier.expectImeVisible(TIMEOUT)
                session.callCommitText("Context word", 1)
                session.callPerformSpellCheck()
                try {
                    waitOnMainUntil({
                        findSuggestionSpanWithFlags(editText, RESULT_ATTR_LOOKS_LIKE_TYPO) != null
                    }, TIMEOUT)
                    fail("Invalid suggestions should be ignored")
                } catch (e: TimeoutException) {
                    // Expected.
                }
            }
        }
    }

    private fun findSuggestionSpanWithFlags(editText: EditText, flags: Int): SuggestionSpan? =
            getSuggestionSpans(editText).find { (it.flags and flags) == flags }

    private fun getSuggestionSpans(editText: EditText): Array<SuggestionSpan> {
        val editable = editText.text
        val spans = editable.getSpans(0, editable.length, SuggestionSpan::class.java)
        return spans
    }

    private fun emulateTapAtOffset(editText: EditText, offset: Int) {
        var x = 0
        var y = 0
        runOnMainSync {
            x = editText.layout.getPrimaryHorizontal(offset).toInt()
            val line = editText.layout.getLineForOffset(offset)
            y = (editText.layout.getLineTop(line) + editText.layout.getLineBottom(line)) / 2
        }
        ctsTouchUtils.emulateTapOnView(instrumentation, null, editText, x, y)
    }

    @UiThread
    private fun isCursorInside(editText: EditText, start: Int, end: Int): Boolean =
            start <= editText.selectionStart && editText.selectionEnd <= end

    private fun startTestActivity(): Pair<TestActivity, EditText> {
        var editText: EditText? = null
        val activity = TestActivity.startSync { activity: TestActivity? ->
            val layout = LinearLayout(activity)
            editText = EditText(activity)
            layout.addView(editText, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            layout
        }
        return Pair(activity, editText!!)
    }

    private fun getCurrentInputMethodInfo(): InputMethodInfo {
        val curId = Settings.Secure.getString(
            context.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD
        )
        val imm = context.getSystemService(InputMethodManager::class.java)
        val info = imm?.inputMethodList?.find { it.id == curId }
        assertThat(info).isNotNull()
        return info!!
    }

    private fun getSentenceSuggestions(
        session: SpellCheckerSession,
        listener: FakeSpellCheckerSessionListener,
        text: String
    ): Array<SentenceSuggestionsInfo>? {
        val prevSize = listener.getSentenceSuggestionsResults.size
        session.getSentenceSuggestions(arrayOf(TextInfo(text)), SUGGESTIONS_MAX_SIZE)
        waitOnMainUntil({
            listener.getSentenceSuggestionsResults.size == prevSize + 1
        }, TIMEOUT)
        return listener.getSentenceSuggestionsResults[prevSize]
    }

    private inner class ImeSession(val imeId: String) : AutoCloseable {

        init {
            SystemUtil.runCommandAndPrintOnLogcat(TAG, "ime reset")
            SystemUtil.runCommandAndPrintOnLogcat(TAG, "ime enable $imeId")
            SystemUtil.runCommandAndPrintOnLogcat(TAG, "ime set $imeId")
            PollingCheck.check("Make sure that $imeId is selected", TIMEOUT) {
                getCurrentInputMethodInfo().id == imeId
            }
        }

        override fun close() {
            SystemUtil.runCommandAndPrintOnLogcat(TAG, "ime reset")
        }
    }

    private class FakeSpellCheckerSessionListener :
            SpellCheckerSession.SpellCheckerSessionListener {
        val getSentenceSuggestionsResults = ArrayList<Array<SentenceSuggestionsInfo>?>()
        val getSentenceSuggestionsCallingThreads = ArrayList<Thread>()

        override fun onGetSuggestions(results: Array<SuggestionsInfo>?) {
            fail("Not expected")
        }

        override fun onGetSentenceSuggestions(results: Array<SentenceSuggestionsInfo>?) {
            getSentenceSuggestionsResults.add(results)
            getSentenceSuggestionsCallingThreads.add(Thread.currentThread())
        }
    }

    private class FakeExecutor : Executor {
        @get:Synchronized
        val runnables = ArrayList<Runnable>()

        @Synchronized
        override fun execute(r: Runnable) {
            runnables.add(r)
        }
    }
}
