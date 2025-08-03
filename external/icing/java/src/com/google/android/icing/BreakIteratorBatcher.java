// Copyright (C) 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.icing;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A simple wrapper around BreakIterator that allows batching of multiple BreakIterator#next calls
 * to reduce the number of necessary reverse jni calls.
 *
 * <p>Example: The text "我每天走路去上班。" has a length of 9 bytes in UTF-16 and a length of 27 bytes in
 * UTF-8. The text should be broken up into the following six terms when properly segmented: "我",
 * "每天", "走路", "去", "上班", "。"
 *
 * <pre>{@code
 * BreakIteratorBatcher brkItrBatcher = new BreakIteratorBatcher(Locale.US);
 * brkItrBatcher.setText("我每天走路去上班。");
 * int[] utf16Boundaries = brkItrBatcher.next(5);
 * assertThat(utf16Boundaries).asList().containsExactly(1, 3, 5, 6, 8);
 * utf16Boundaries = brkItrBatcher.next(5);
 * assertThat(utf16Boundaries).asList().containsExactly(9);
 * }</pre>
 */
public class BreakIteratorBatcher {

  private final BreakIterator iterator;

  public BreakIteratorBatcher(Locale locale) {
    this.iterator = BreakIterator.getWordInstance(locale);
  }

  /* Direct calls to BreakIterator */
  public void setText(String text) {
    iterator.setText(text);
  }

  public int first() {
    return iterator.first();
  }

  public int preceding(int utf16Offset) {
    return iterator.preceding(utf16Offset);
  }

  public int following(int utf16Offset) {
    return iterator.following(utf16Offset);
  }

  /**
   * Batched version of next. Returns an array of ints of up to size batchSize, reflecting the
   * return values of batchSize successful calls to BreakIterator.next. If the BreakIterator reaches
   * the end of the text (returns BreakIterator#DONE), then only the results of the previous calls
   * in that batch will be returned.
   */
  public int[] next(int batchSize) {
    List<Integer> breakIndices = new ArrayList<>(batchSize);
    for (int i = 0; i < batchSize; ++i) {
      int boundary = iterator.next();
      if (boundary == BreakIterator.DONE) {
        break;
      }
      breakIndices.add(boundary);
    }
    int[] breakIndicesArray = new int[breakIndices.size()];
    for (int i = 0; i < breakIndices.size(); ++i) {
      breakIndicesArray[i] = breakIndices.get(i);
    }
    return breakIndicesArray;
  }
}
