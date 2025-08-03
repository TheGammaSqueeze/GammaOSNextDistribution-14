// Copyright 2021 The Android Open Source Project
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

package com.google.android.downloader;

import android.util.Log;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import javax.annotation.Nullable;

/**
 * Implementation of {@link DownloaderLogger} backed by Android's {@link Log} system. Useful for
 * contexts (e.g. AOSP) that can't take the Flogger dependency.
 */
public class AndroidDownloaderLogger implements DownloaderLogger {
  private static final String TAG = "downloader2";

  @Override
  @FormatMethod
  public void logFine(@FormatString String message, Object... args) {
    Log.d(TAG, String.format(message, args));
  }

  @Override
  @FormatMethod
  public void logInfo(@FormatString String message, Object... args) {
    Log.i(TAG, String.format(message, args));
  }

  @Override
  @FormatMethod
  public void logWarning(@FormatString String message, Object... args) {
    Log.w(TAG, String.format(message, args));
  }

  @Override
  @FormatMethod
  public void logWarning(@Nullable Throwable cause, @FormatString String message, Object... args) {
    Log.w(TAG, String.format(message, args), cause);
  }

  @Override
  @FormatMethod
  public void logError(@FormatString String message, Object... args) {
    Log.e(TAG, String.format(message, args));
  }

  @Override
  @FormatMethod
  public void logError(@Nullable Throwable cause, @FormatString String message, Object... args) {
    Log.e(TAG, String.format(message, args), cause);
  }
}
