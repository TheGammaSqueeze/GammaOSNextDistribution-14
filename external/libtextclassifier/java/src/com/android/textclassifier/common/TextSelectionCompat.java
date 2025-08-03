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

import android.view.textclassifier.TextClassification;
import android.view.textclassifier.TextSelection;
import androidx.annotation.RequiresApi;
import androidx.core.os.BuildCompat;
import javax.annotation.Nullable;

/** Compatibility methods for {@link TextSelection}. */
public final class TextSelectionCompat {

  public static boolean shouldIncludeTextClassification(TextSelection.Request request) {
    if (BuildCompat.isAtLeastS()) {
      return Api31Impl.shouldIncludeTextClassification(request);
    }
    return Api30Impl.shouldIncludeTextClassification(request);
  }

  public static void setTextClassification(
      TextSelection.Builder builder, @Nullable TextClassification textClassification) {
    if (BuildCompat.isAtLeastS()) {
      Api31Impl.setTextClassification(builder, textClassification);
    }
  }

  private static final class Api30Impl {

    private Api30Impl() {}

    public static boolean shouldIncludeTextClassification(TextSelection.Request request) {
      return false;
    }
  }

  @RequiresApi(31)
  private static final class Api31Impl {

    private Api31Impl() {}

    public static boolean shouldIncludeTextClassification(TextSelection.Request request) {
      return request.shouldIncludeTextClassification();
    }

    public static void setTextClassification(
        TextSelection.Builder builder, @Nullable TextClassification textClassification) {
      builder.setTextClassification(textClassification);
    }
  }

  private TextSelectionCompat() {}
}
