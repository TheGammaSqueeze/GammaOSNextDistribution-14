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

package com.android.textclassifier;

import android.os.Binder;
import android.os.Parcel;
import android.view.textclassifier.TextClassificationSessionId;

/** Utils class for helper functions to use in tests. */
public final class TestingUtils {

  /** Used a hack to create TextClassificationSessionId because its constructor is @hide. */
  public static TextClassificationSessionId createTextClassificationSessionId(String sessionId) {
    Parcel parcel = Parcel.obtain();
    parcel.writeString(sessionId);
    parcel.writeStrongBinder(new Binder());
    parcel.setDataPosition(0);
    return TextClassificationSessionId.CREATOR.createFromParcel(parcel);
  }

  private TestingUtils() {}
}
