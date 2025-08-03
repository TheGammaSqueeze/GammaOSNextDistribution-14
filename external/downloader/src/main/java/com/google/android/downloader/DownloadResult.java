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

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;

/**
 * Value representing the result of a {@link DownloadRequest}. Contains information about the
 * completed download.
 *
 * <p>Note that this is only used to represent a successful download. A failed download will be
 * represented as a failed {@link java.util.concurrent.Future} as returned by {@link
 * Downloader#execute}, with the exception containing more information about the failure.
 */
@AutoValue
public abstract class DownloadResult {

  /** Returns the number of bytes written to this result. */
  public abstract long bytesWritten();

  @VisibleForTesting // Package-private outside of testing use
  public static DownloadResult create(long bytesWritten) {
    return new AutoValue_DownloadResult(bytesWritten);
  }
}
