/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.quicksearchbox

import android.text.SpannableString
import android.text.Spanned
import android.util.Log
import com.android.quicksearchbox.util.LevenshteinDistance
import com.android.quicksearchbox.util.LevenshteinDistance.Token
import com.google.common.annotations.VisibleForTesting
import java.util.Locale

/**
 * Suggestion formatter using the Levenshtein distance (minimum edit distance) to calculate the
 * formatting.
 */
class LevenshteinSuggestionFormatter(spanFactory: TextAppearanceFactory?) :
  SuggestionFormatter(spanFactory!!) {
  @Override
  override fun formatSuggestion(query: String?, suggestion: String?): Spanned {
    var mQuery = query
    if (DBG) Log.d(TAG, "formatSuggestion('$mQuery', '$suggestion')")
    mQuery = normalizeQuery(mQuery)
    val queryTokens: Array<Token?> = tokenize(mQuery)
    val suggestionTokens: Array<Token?> = tokenize(suggestion)
    val matches = findMatches(queryTokens, suggestionTokens)
    if (DBG) {
      Log.d(TAG, "source = $queryTokens")
      Log.d(TAG, "target = $suggestionTokens")
      Log.d(TAG, "matches = $matches")
    }
    val str = SpannableString(suggestion)
    val matchesLen = matches.size
    for (i in 0 until matchesLen) {
      val t: Token? = suggestionTokens[i]
      var sourceLen = 0
      val thisMatch = matches[i]
      if (thisMatch >= 0) {
        sourceLen = queryTokens[thisMatch]!!.length
      }
      applySuggestedTextStyle(str, t!!.mStart + sourceLen, t.mEnd)
      applyQueryTextStyle(str, t.mStart, t.mStart + sourceLen)
    }
    return str
  }

  private fun normalizeQuery(query: String?): String? {
    return query?.lowercase(Locale.getDefault())
  }

  /**
   * Finds which tokens in the target match tokens in the source.
   *
   * @param source List of source tokens (i.e. user query)
   * @param target List of target tokens (i.e. suggestion)
   * @return The indices into source which target tokens correspond to. A non-negative value n at
   * position i means that target token i matches source token n. A negative value means that the
   * target token i does not match any source token.
   */
  @VisibleForTesting
  fun findMatches(source: Array<Token?>?, target: Array<Token?>): IntArray {
    val table = LevenshteinDistance(source, target)
    table.calculate()
    val targetLen = target.size
    val result = IntArray(targetLen)
    val ops: Array<LevenshteinDistance.EditOperation?> = table.targetOperations
    for (i in 0 until targetLen) {
      if (ops[i]!!.type == LevenshteinDistance.EDIT_UNCHANGED) {
        result[i] = ops[i]!!.position
      } else {
        result[i] = -1
      }
    }
    return result
  }

  @VisibleForTesting
  fun tokenize(seq: String?): Array<Token?> {
    var pos = 0
    val len: Int = seq!!.length
    val chars = seq.toCharArray()
    // There can't be more tokens than characters, make an array that is large enough
    val tokens: Array<Token?> = arrayOfNulls<Token>(len)
    var tokenCount = 0
    while (pos < len) {
      while (pos < len && (chars[pos] == ' ' || chars[pos] == '\t')) {
        pos++
      }
      val start = pos
      while (pos < len && !(chars[pos] == ' ' || chars[pos] == '\t')) {
        pos++
      }
      val end = pos
      if (start != end) {
        tokens[tokenCount++] = Token(chars, start, end)
      }
    }
    // Create a token array of the right size and return
    val ret: Array<Token?> = arrayOfNulls<Token>(tokenCount)
    System.arraycopy(tokens, 0, ret, 0, tokenCount)
    return ret
  }

  companion object {
    private const val DBG = false
    private const val TAG = "QSB.LevenshteinSuggestionFormatter"
  }
}
