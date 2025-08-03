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
package com.android.cts.mockspellchecker

import android.content.ComponentName
import android.service.textservice.SpellCheckerService
import android.util.Log
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import com.android.cts.mockspellchecker.MockSpellCheckerProto.MockSpellCheckerConfiguration
import com.android.cts.mockspellchecker.MockSpellCheckerProto.SuggestionRule
import java.io.FileDescriptor
import java.io.PrintWriter

internal inline fun <T> withLog(msg: String, block: () -> T): T {
    Log.i(TAG, msg)
    return block()
}

const val EXTRAS_KEY_PREFIX = "prefix"

/** Mock Spell checker for end-to-end tests. */
class MockSpellChecker : SpellCheckerService() {

    override fun onCreate() = withLog("MockSpellChecker.onCreate") {
        super.onCreate()
    }

    override fun onDestroy() = withLog("MockSpellChecker.onDestroy") {
        super.onDestroy()
    }

    override fun dump(fd: FileDescriptor?, writer: PrintWriter?, args: Array<out String>?) {
        writer?.println("MockSpellChecker")
    }

    override fun createSession(): Session = withLog("MockSpellChecker.createSession") {
        return MockSpellCheckerSession()
    }

    private inner class MockSpellCheckerSession : SpellCheckerService.Session() {

        override fun onCreate() = withLog("MockSpellCheckerSession.onCreate") {
        }

        override fun onGetSentenceSuggestionsMultiple(
            textInfos: Array<out TextInfo>?,
            suggestionsLimit: Int
        ): Array<SentenceSuggestionsInfo> = withLog(
                "MockSpellCheckerSession.onGetSuggestionsMultiple " +
                        "${textInfos?.map { it.text }?.joinToString(":")}") {
            if (textInfos == null) return emptyArray()
            val configuration = MockSpellCheckerConfiguration.parseFrom(
                    SharedPrefsProvider.get(contentResolver, KEY_CONFIGURATION))
            if (configuration.matchSentence)
                return textInfos.map { matchSentenceSuggestion(configuration, it) }.toTypedArray()
            return super.onGetSentenceSuggestionsMultiple(textInfos, suggestionsLimit)
        }

        private fun matchSentenceSuggestion(
            configuration: MockSpellCheckerConfiguration,
            textInfo: TextInfo
        ): SentenceSuggestionsInfo {
            return configuration.suggestionRulesList.find { it.match == textInfo.text }
                    ?.let {
                        SentenceSuggestionsInfo(
                                arrayOf(suggestionsInfo(it, textInfo.cookie, textInfo.sequence)),
                                intArrayOf(if (it.hasStartOffset()) it.startOffset else 0),
                                intArrayOf(if (it.hasLength()) it.length else textInfo.text.length))
                    }
                    ?: SentenceSuggestionsInfo(emptyArray(), intArrayOf(), intArrayOf())
        }

        override fun onGetSuggestions(
            textInfo: TextInfo?,
            suggestionsLimit: Int
        ): SuggestionsInfo = withLog(
                "MockSpellCheckerSession.onGetSuggestions: ${textInfo?.text}") {
            if (textInfo == null) return emptySuggestionsInfo()
            val configuration = MockSpellCheckerConfiguration.parseFrom(
                    SharedPrefsProvider.get(contentResolver, KEY_CONFIGURATION))
            return configuration.suggestionRulesList
                    .find { it.match == textInfo.text }
                    ?.let { suggestionsInfo(it) }
                    ?: emptySuggestionsInfo()
        }

        private fun suggestionsInfo(rule: SuggestionRule): SuggestionsInfo {
            return suggestionsInfo(rule, 0, 0)
        }

        private fun suggestionsInfo(
            rule: SuggestionRule,
            cookie: Int,
            sequence: Int
        ): SuggestionsInfo {
            // Only use attrs in supportedAttributes
            val attrs = rule.attributes and supportedAttributes
            // Add prefix if it is passed in getBundle()
            val prefix = bundle.getString(EXTRAS_KEY_PREFIX)
            val suggestions = if (prefix != null) {
                rule.suggestionsList.map { prefix + it }.toTypedArray()
            } else {
                rule.suggestionsList.toTypedArray()
            }
            return SuggestionsInfo(attrs, suggestions, cookie, sequence)
        }

        private fun emptySuggestionsInfo() = SuggestionsInfo(0, arrayOf())
    }

    companion object {
        @JvmStatic
        fun getId(): String =
                ComponentName(PACKAGE, MockSpellChecker::class.java.name).flattenToShortString()
    }
}