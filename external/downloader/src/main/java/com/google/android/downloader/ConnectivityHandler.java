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

import com.google.common.util.concurrent.ListenableFuture;

/** Interface for dealing with connectivity checking and handling in the downloader. */
public interface ConnectivityHandler {
  /**
   * Handles the checking connectivity for the given {@link DownloadConstraints}. This method must
   * return a {@link ListenableFuture} that resolves when the constraints are satisfied.
   *
   * <p>The returned future may be resolved in failure state with a {@link
   * java.util.concurrent.TimeoutException} if too much time without sufficient connectivity has
   * been observed. The future may also be cancelled (resulting in a {@link
   * java.util.concurrent.CancellationException}) if observing connectivity changes is no longer
   * necessary. Either state will be given special treatment to retry downloads. Any other failure
   * state will be considered fatal and will fail ongoing downloads.
   */
  ListenableFuture<Void> checkConnectivity(DownloadConstraints constraints);
}
