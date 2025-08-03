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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.common.io.Files;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.StandardOpenOption;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for IOUtil. */
@RunWith(JUnit4.class)
public class IOUtilTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void validateChannel_nonBlocking() throws Exception {
    SocketChannel channel = SocketChannel.open();
    channel.configureBlocking(false);
    assertThrows(IllegalStateException.class, () -> IOUtil.validateChannel(channel));
  }

  @Test
  public void validateChannel_notOpen() throws Exception {
    File testFile = temporaryFolder.newFile();
    FileChannel channel = FileChannel.open(testFile.toPath());
    channel.close();

    assertThrows(IllegalStateException.class, () -> IOUtil.validateChannel(channel));
  }

  @Test
  public void validateChannel_valid() throws Exception {
    File testFile = temporaryFolder.newFile();
    FileChannel channel = FileChannel.open(testFile.toPath());
    IOUtil.validateChannel(channel);
  }

  @Test
  public void blockingWrite() throws Exception {
    String message = "hello world";
    File testFile = temporaryFolder.newFile();

    FileChannel channel = FileChannel.open(testFile.toPath(), StandardOpenOption.WRITE);
    ByteBuffer buffer = ByteBuffer.wrap(message.getBytes(UTF_8));
    IOUtil.blockingWrite(buffer, channel);
    channel.close();

    assertThat(Files.asCharSource(testFile, UTF_8).read()).isEqualTo(message);
  }
}
