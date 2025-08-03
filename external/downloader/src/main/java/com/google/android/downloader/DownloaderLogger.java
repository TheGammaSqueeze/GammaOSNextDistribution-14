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

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import javax.annotation.Nullable;

/**
 * Interface for a generic logging system, as used by the downloader. Allows the caller to configure
 * the logging implementation as needed, and avoid extra dependencies in the downloader.
 */
public interface DownloaderLogger {
  @FormatMethod
  void logFine(@FormatString String message, Object... args);

  @FormatMethod
  void logInfo(@FormatString String message, Object... args);

  @FormatMethod
  void logWarning(@FormatString String message, Object... args);

  @FormatMethod
  void logWarning(@Nullable Throwable cause, @FormatString String message, Object... args);

  @FormatMethod
  void logError(@FormatString String message, Object... args);

  @FormatMethod
  void logError(@Nullable Throwable cause, @FormatString String message, Object... args);
}
