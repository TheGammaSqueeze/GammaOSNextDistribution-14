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

import com.google.common.io.CharSource;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for SimpleFileDownloadDestination. */
@RunWith(JUnit4.class)
public final class SimpleFileDownloadDestinationTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void numExistingBytes_fileDoesNotExist() throws Exception {
    File targetFile = new File("/does/not/exist");
    File metadataFile = new File(targetFile.getParent(), targetFile.getName() + ".meta");
    SimpleFileDownloadDestination downloadDestination =
        new SimpleFileDownloadDestination(targetFile, metadataFile);

    // Perhaps a bit surprising, but java.io.File.length returns 0 for a file that does not exist
    assertThat(downloadDestination.numExistingBytes()).isEqualTo(0);
  }

  @Test
  public void numExistingBytes_fileEmpty() throws Exception {
    File targetFile = temporaryFolder.newFile();
    File metadataFile = new File(targetFile.getParent(), targetFile.getName() + ".meta");
    SimpleFileDownloadDestination downloadDestination =
        new SimpleFileDownloadDestination(targetFile, metadataFile);
    assertThat(downloadDestination.numExistingBytes()).isEqualTo(0);
  }

  @Test
  public void numExistingBytes_fileNonEmpty() throws Exception {
    File targetFile = temporaryFolder.newFile();
    File metadataFile = new File(targetFile.getParent(), targetFile.getName() + ".meta");
    SimpleFileDownloadDestination downloadDestination =
        new SimpleFileDownloadDestination(targetFile, metadataFile);
    Files.asCharSink(targetFile, UTF_8).write("Hello world");
    assertThat(downloadDestination.numExistingBytes()).isEqualTo(targetFile.length());
  }

  @Test
  public void metadata_fileDoesNotExist() throws Exception {
    File targetFile = new File("/does/not/exist");
    File metadataFile = new File(targetFile.getParent(), targetFile.getName() + ".meta");
    SimpleFileDownloadDestination downloadDestination =
        new SimpleFileDownloadDestination(targetFile, metadataFile);

    DownloadMetadata metadata = downloadDestination.readMetadata();
    assertThat(metadata).isEqualTo(DownloadMetadata.create());
  }

  @Test
  public void metadata_fileEmpty() throws Exception {
    File targetFile = temporaryFolder.newFile();
    File metadataFile = new File(targetFile.getParent(), targetFile.getName() + ".meta");
    SimpleFileDownloadDestination downloadDestination =
        new SimpleFileDownloadDestination(targetFile, metadataFile);

    DownloadMetadata metadata = downloadDestination.readMetadata();
    assertThat(metadata).isEqualTo(DownloadMetadata.create());
  }

  @Test
  public void metadata_fileNonEmpty() throws Exception {
    File targetFile = temporaryFolder.newFile();
    File metadataFile = new File(targetFile.getParent(), targetFile.getName() + ".meta");
    SimpleFileDownloadDestination downloadDestination =
        new SimpleFileDownloadDestination(targetFile, metadataFile);
    Files.asCharSink(targetFile, UTF_8).write("Hello world");

    DownloadMetadata metadata = downloadDestination.readMetadata();
    assertThat(metadata).isEqualTo(DownloadMetadata.create());
  }

  @Test
  public void openByteChannel_fileDoesNotExist() {
    File targetFile = new File("/does/not/exist");
    File metadataFile = new File(targetFile.getParent(), targetFile.getName() + ".meta");
    SimpleFileDownloadDestination downloadDestination =
        new SimpleFileDownloadDestination(targetFile, metadataFile);
    assertThrows(
        FileNotFoundException.class,
        () -> downloadDestination.openByteChannel(0L, DownloadMetadata.create()));
  }

  @Test
  public void openByteChannel_fileEmpty() throws Exception {
    File targetFile = temporaryFolder.newFile();
    File metadataFile = new File(targetFile.getParent(), targetFile.getName() + ".meta");
    SimpleFileDownloadDestination downloadDestination =
        new SimpleFileDownloadDestination(targetFile, metadataFile);
    String text = "Hello world";

    WritableByteChannel channel =
        downloadDestination.openByteChannel(0L, DownloadMetadata.create());
    assertThat(channel.isOpen()).isTrue();
    CharSource.wrap(text).asByteSource(UTF_8).copyTo(Channels.newOutputStream(channel));
    channel.close();

    assertThat(Files.asCharSource(targetFile, UTF_8).read()).isEqualTo(text);
  }

  @Test
  public void openByteChannel_fileNonEmpty() throws Exception {
    File targetFile = temporaryFolder.newFile();
    File metadataFile = new File(targetFile.getParent(), targetFile.getName() + ".meta");
    SimpleFileDownloadDestination downloadDestination =
        new SimpleFileDownloadDestination(targetFile, metadataFile);
    String text1 = "Hello world";
    Files.asCharSink(targetFile, UTF_8).write(text1);
    String text2 = "Bye world";

    WritableByteChannel channel =
        downloadDestination.openByteChannel(targetFile.length(), DownloadMetadata.create());
    assertThat(channel.isOpen()).isTrue();
    CharSource.wrap(text2).asByteSource(UTF_8).copyTo(Channels.newOutputStream(channel));
    channel.close();

    assertThat(Files.asCharSource(targetFile, UTF_8).read()).isEqualTo(text1 + text2);
  }

  @Test
  public void openByteChannel_metadata() throws Exception {
    File targetFile = temporaryFolder.newFile();
    File metadataFile = new File(targetFile.getParent(), targetFile.getName() + ".meta");
    SimpleFileDownloadDestination downloadDestination =
        new SimpleFileDownloadDestination(targetFile, metadataFile);

    String contentTag = "content_tag_abc";
    long lastModifiedTimeSeconds = 12345;
    downloadDestination
        .openByteChannel(0L, DownloadMetadata.create(contentTag, lastModifiedTimeSeconds))
        .close();

    DownloadMetadata metadata = downloadDestination.readMetadata();
    assertThat(metadata).isEqualTo(DownloadMetadata.create(contentTag, lastModifiedTimeSeconds));
  }

  @Test
  public void clear_fileNonEmpty() throws Exception {
    File targetFile = temporaryFolder.newFile();
    File metadataFile = new File(targetFile.getParent(), targetFile.getName() + ".meta");
    SimpleFileDownloadDestination downloadDestination =
        new SimpleFileDownloadDestination(targetFile, metadataFile);
    Files.asCharSink(targetFile, UTF_8).write("existing");

    WritableByteChannel channel1 =
        downloadDestination.openByteChannel(0L, DownloadMetadata.create());
    downloadDestination.clear();
    WritableByteChannel channel2 =
        downloadDestination.openByteChannel(0L, DownloadMetadata.create());

    CharSource.wrap("swallowed").asByteSource(UTF_8).copyTo(Channels.newOutputStream(channel1));
    CharSource.wrap("replacement").asByteSource(UTF_8).copyTo(Channels.newOutputStream(channel2));
    channel2.close();

    assertThat(Files.asCharSource(targetFile, UTF_8).read()).isEqualTo("replacement");
  }
}
