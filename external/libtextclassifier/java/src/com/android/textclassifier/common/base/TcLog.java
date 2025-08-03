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

package com.android.textclassifier.common.base;

import android.util.Log;

/**
 * Logging for android.view.textclassifier package.
 *
 * <p>To enable full log:
 *
 * <ul>
 *   <li>adb shell setprop log.tag.androidtc VERBOSE
 *   <li>adb shell stop && adb shell start
 * </ul>
 */
public final class TcLog {
  private static final boolean USE_TC_TAG = true;
  public static final String TAG = "androidtc";

  /** true: Enables full logging. false: Limits logging to debug level. */
  public static final boolean ENABLE_FULL_LOGGING = Log.isLoggable(TAG, Log.VERBOSE);

  private TcLog() {}

  public static void v(String tag, String msg) {
    if (ENABLE_FULL_LOGGING) {
      Log.v(getTag(tag), msg);
    }
  }

  public static void d(String tag, String msg) {
    Log.d(getTag(tag), msg);
  }

  public static void w(String tag, String msg) {
    Log.w(getTag(tag), msg);
  }

  public static void e(String tag, String msg) {
    Log.e(getTag(tag), msg);
  }

  public static void e(String tag, String msg, Throwable tr) {
    Log.e(getTag(tag), msg, tr);
  }

  private static String getTag(String customTag) {
    return USE_TC_TAG ? TAG : customTag;
  }
}
