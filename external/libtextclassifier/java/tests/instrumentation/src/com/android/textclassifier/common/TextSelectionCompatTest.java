/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.textclassifier.common;

import static com.google.common.truth.Truth.assertThat;

import android.view.textclassifier.TextClassification;
import android.view.textclassifier.TextSelection;
import androidx.test.filters.SdkSuppress;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TextSelectionCompatTest {

  @SdkSuppress(minSdkVersion = 30)
  @Test
  public void shouldIncludeTextClassification_negative() {
    TextSelection.Request request =
        new TextSelection.Request.Builder("text", /*startIndex=*/ 0, /*endIndex=*/ 1).build();

    assertThat(TextSelectionCompat.shouldIncludeTextClassification(request)).isFalse();
  }

  @SdkSuppress(minSdkVersion = 31, codeName = "S")
  @Test
  public void shouldIncludeTextClassification_positive() {
    TextSelection.Request request =
        new TextSelection.Request.Builder("text", /*startIndex=*/ 0, /*endIndex=*/ 1)
            .setIncludeTextClassification(true)
            .build();

    assertThat(TextSelectionCompat.shouldIncludeTextClassification(request)).isTrue();
  }

  @SdkSuppress(minSdkVersion = 30, maxSdkVersion = 30)
  @Test
  public void setTextClassification_api30() {
    TextSelection.Builder selectionBuilder =
        new TextSelection.Builder(/*startIndex=*/ 0, /*endIndex=*/ 1);

    // This should not crash.
    TextSelectionCompat.setTextClassification(selectionBuilder, null);
  }

  @SdkSuppress(minSdkVersion = 31)
  @Test
  public void setTextClassification_api31() {
    TextSelection.Builder selectionBuilder =
        new TextSelection.Builder(/*startIndex=*/ 0, /*endIndex=*/ 1);
    TextClassification classification = new TextClassification.Builder().setText("text").build();

    TextSelectionCompat.setTextClassification(selectionBuilder, classification);

    assertThat(selectionBuilder.build().getTextClassification()).isSameInstanceAs(classification);
  }
}
