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

import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.WritableByteChannel;

/** Package-local utilities for facilitating common I/O operations across engine implementations. */
final class IOUtil {

  private IOUtil() {}

  /**
   * Validates the given channel as a valid byte sink for the UrlEngine library. Specifically, it
   * checks that the given channel is open, and if it is a {@link SelectableChannel}, then it must
   * be in blocking mode.
   *
   * <p>This is to guard against having a sink that refuses write operations in a non-blocking
   * manner by returning from {@link WritableByteChannel#write} immediately with a return value of
   * 0, indicating no bytes were written. That would be problematic for the UrlEngine library
   * because then threads will spin constantly trying to stream bytes from the source to the sink,
   * saturating the CPU in the process.
   *
   * <p>TODO: Figure out a more robust way to handle this. Maybe writing implement proper support
   * for selectable channels?
   */
  static void validateChannel(WritableByteChannel channel) {
    if (channel instanceof SelectableChannel) {
      SelectableChannel selectableChannel = (SelectableChannel) channel;
      checkState(
          selectableChannel.isBlocking(),
          "Target channels used by UrlEngine must be in blocking mode to ensure "
              + "writes happen correctly; call SelectableChannel#configureBlocking(true).");
    }

    checkState(channel.isOpen());
  }

  static long blockingWrite(ByteBuffer source, WritableByteChannel target) throws IOException {
    long written = 0;
    while (source.hasRemaining()) {
      written += target.write(source);
    }
    return written;
  }
}
