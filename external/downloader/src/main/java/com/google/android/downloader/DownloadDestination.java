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

import java.io.IOException;
import java.nio.channels.WritableByteChannel;

/**
 * An abstract "sink" for the stream of bytes produced by a download. A common implementation is
 * {@link ProtoFileDownloadDestination}, which streams the downloaded bytes to a writeable file on
 * disk, and serializes {@link DownloadMetadata} as a protocol buffer.
 *
 * <p>Persistent destinations may support partial download resumption by implementing {@link
 * #numExistingBytes} to return a non-zero value.
 */
public interface DownloadDestination {
  /**
   * How many bytes are already present for the destination. Used to avoid re-downloading data by
   * using HTTP range downloads.
   */
  long numExistingBytes() throws IOException;

  /**
   * Reads metadata for the destination. The metadata is used to set request headers as appropriate
   * to support range downloads and content-aware requests.
   *
   * <p>Note that this operation likely needs to read persistent state from disk to retrieve the
   * metadata (e.g. a database read). An {@link IOException} may be thrown if an error is
   * encountered. This method will always be called on the {@link java.util.concurrent.Executor}
   * that is supplied to the downloader via {@link Downloader.Builder#withIOExecutor}.
   */
  DownloadMetadata readMetadata() throws IOException;

  /**
   * Opens the byte channel to write the download data to, with server-provided metadata to control
   * how the destination should be initialized and stored. The downloader will close the channel
   * when the download is complete.
   *
   * <p>The returned {@link WritableByteChannel} must have its {@code position} set to {@code
   * byteOffset}. The underlying storage mechanism should also persist the metadata message, so that
   * future calls to {@link #readMetadata} represent those values. Failure to do so may result in
   * data corruption.
   *
   * @param byteOffset initial byte position for the returned channel.
   * @param metadata metadata to configure the destination with
   * @throws IllegalArgumentException if {@code byteOffset} is outside of range [0, {@link
   *     #numExistingBytes}]
   */
  WritableByteChannel openByteChannel(long byteOffset, DownloadMetadata metadata)
      throws IOException;

  /**
   * Erases any existing bytes stored at this destination.
   *
   * <p>Implementations can either invalidate any channels previously returned by {@link
   * #openByteChannel} or have them silently drop subsequent writes.
   */
  void clear() throws IOException;
}
