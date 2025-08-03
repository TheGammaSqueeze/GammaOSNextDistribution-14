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

/**
 * Value interface for representing download metadata in-memory. Note that this is generalized so
 * that the exact serialization mechanism used by a {@link DownloadDestination} implementation can
 * be hidden from the downloader, as there are contexts where for example using a protocol buffer
 * isn't desirable for dependency purposes.
 */
@AutoValue
public abstract class DownloadMetadata {
  /** The HTTP content tag of the download, or the empty string if none was returned. */
  public abstract String getContentTag();

  /**
   * The last modification timestamp of the download, in seconds since the UNIX epoch, or 0 if none
   * was returned.
   */
  public abstract long getLastModifiedTimeSeconds();

  /** Creates an empty instance of {@link DownloadMetadata}. */
  public static DownloadMetadata create() {
    return new AutoValue_DownloadMetadata("", 0L);
  }

  /**
   * Creates an instance of {@link DownloadMetadata} with the provided content tag and modified
   * timestamp.
   */
  public static DownloadMetadata create(String contentTag, long lastModifiedTimeSeconds) {
    return new AutoValue_DownloadMetadata(contentTag, lastModifiedTimeSeconds);
  }
}
