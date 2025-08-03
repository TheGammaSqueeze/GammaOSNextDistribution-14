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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.testing.mockito.Mocks;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

/** Unit tests for DataUrlEngine. */
@RunWith(JUnit4.class)
public class DataUrlEngineTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Rule public Mocks mocks = new Mocks(this);

  @Mock WritableByteChannel mockByteChannel;

  private DataUrlEngine engine;

  @Before
  public void setUp() {
    engine = new DataUrlEngine(MoreExecutors.newDirectExecutorService());
  }

  @Test
  public void executeRequest() throws Exception {
    File file = temporaryFolder.newFile();
    FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.WRITE);

    UrlRequest request = engine.createRequest("data:text/plain;base64,Zm9vYmFy").build();
    UrlResponse response = request.send().get();
    long bytesWritten = response.readResponseBody(channel).get();
    response.close();
    channel.close();

    assertThat(bytesWritten).isGreaterThan(0L);
    assertThat(Files.asCharSource(file, UTF_8).read()).isEqualTo("foobar");
    assertThat(response.getResponseCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    assertThat(response.getResponseHeaders())
        .containsExactly(HttpHeaders.CONTENT_TYPE, ImmutableList.of("text/plain"));
  }

  @Test
  public void executeRequest_invalidDataUrl() throws Exception {
    UrlRequest request = engine.createRequest("data:text/plain;base64,foobar*").build();
    ListenableFuture<? extends UrlResponse> responseFuture = request.send();

    ExecutionException exception = assertThrows(ExecutionException.class, responseFuture::get);
    assertThat(exception).hasCauseThat().isInstanceOf(RequestException.class);
  }

  @Test
  public void executeRequest_writeError() throws Exception {
    when(mockByteChannel.isOpen()).thenReturn(true);
    when(mockByteChannel.write(any(ByteBuffer.class))).thenThrow(new IOException());

    UrlRequest request = engine.createRequest("data:text/plain;base64,Zm9vYmFy").build();
    UrlResponse response = request.send().get();
    ListenableFuture<Long> writeFuture = response.readResponseBody(mockByteChannel);

    ExecutionException exception = assertThrows(ExecutionException.class, writeFuture::get);
    assertThat(exception).hasCauseThat().isInstanceOf(RequestException.class);

    response.close();

    assertThat(response.getResponseCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    assertThat(response.getResponseHeaders())
        .containsExactly(HttpHeaders.CONTENT_TYPE, ImmutableList.of("text/plain"));
  }
}
