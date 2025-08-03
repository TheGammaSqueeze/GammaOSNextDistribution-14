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

import com.google.common.io.Files;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

/**
 * Basic implementation of {@link DownloadDestination} which streams the download to a {@link File}.
 *
 * <p>This implementation also keeps track of metadata in a separate file, encoded using
 * hand-written serialization. Note that this implementation isn't especially robust - concurrent
 * reads and writes could result in errors or in data corruption, and invalid/corrupt metadata will
 * result in persistent errors.
 */
public class SimpleFileDownloadDestination implements DownloadDestination {
  private final File targetFile;
  private final File metadataFile;

  public SimpleFileDownloadDestination(File targetFile, File metadataFile) {
    this.targetFile = targetFile;
    this.metadataFile = metadataFile;
  }

  @Override
  public long numExistingBytes() throws IOException {
    return targetFile.length();
  }

  @Override
  public DownloadMetadata readMetadata() throws IOException {
    return metadataFile.exists()
        ? readMetadataFromBytes(Files.toByteArray(metadataFile))
        : DownloadMetadata.create();
  }

  @Override
  public WritableByteChannel openByteChannel(long offsetBytes, DownloadMetadata metadata)
      throws IOException {
    checkState(
        offsetBytes <= targetFile.length(),
        "Opening byte channel with offset past known end of file");
    Files.write(writeMetadataToBytes(metadata), metadataFile);
    FileChannel fileChannel = new RandomAccessFile(targetFile, "rw").getChannel();
    // Seek to the requested offset, so we can append data rather than overwrite data.
    fileChannel.position(offsetBytes);
    return fileChannel;
  }

  @Override
  public void clear() throws IOException {
    if (targetFile.exists() && !targetFile.delete()) {
      throw new IOException("Failed to delete()");
    }
  }

  @Override
  public String toString() {
    return targetFile.toString();
  }

  private static DownloadMetadata readMetadataFromBytes(byte[] bytes) throws IOException {
    try (DataInputStream inputStream = new DataInputStream(new ByteArrayInputStream(bytes))) {
      String etag = inputStream.readUTF();
      long lastModifiedTimeSeconds = inputStream.readLong();
      checkState(lastModifiedTimeSeconds >= 0);
      return DownloadMetadata.create(etag, lastModifiedTimeSeconds);
    }
  }

  private static byte[] writeMetadataToBytes(DownloadMetadata metadata) throws IOException {
    try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream)) {
      dataOutputStream.writeUTF(metadata.getContentTag());
      dataOutputStream.writeLong(metadata.getLastModifiedTimeSeconds());
      dataOutputStream.flush();
      byteArrayOutputStream.flush();
      return byteArrayOutputStream.toByteArray();
    }
  }
}
