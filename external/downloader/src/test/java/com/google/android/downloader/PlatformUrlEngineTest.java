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
import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.Executors.newSingleThreadExecutor;

import com.google.common.io.Files;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.File;
import java.net.HttpURLConnection;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for PlatformUrlEngine. */
@RunWith(JUnit4.class)
public class PlatformUrlEngineTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private MockWebServerUrlEngineTestHelper testHelper;
  private PlatformUrlEngine engine;
  private ListeningExecutorService executorService;

  @Before
  public void setUp() {
    executorService = listeningDecorator(newSingleThreadExecutor());
    testHelper =
        new MockWebServerUrlEngineTestHelper(
            temporaryFolder, new TestingExecutorService(executorService));
    engine =
        new PlatformUrlEngine(
            executorService, /* connectTimeoutMs= */ 1000, /* readTimeoutMs= */ 1000);
  }

  @After
  public void tearDown() throws Exception {
    testHelper.tearDown();
    executorService.shutdown();
  }

  @Test
  public void executeRequest_normalResponse_succeeds() throws Exception {
    testHelper.executeRequest_normalResponse_succeeds(engine);
  }

  @Test
  public void executeRequest_responseThrottled_succeeds() throws Exception {
    testHelper.executeRequest_responseThrottled_succeeds(engine);
  }

  @Test
  public void executeRequest_largeResponse_succeeds() throws Exception {
    testHelper.executeRequest_largeResponse_succeeds(
        engine, PlatformUrlEngine.BUFFER_SIZE_BYTES * 3);
  }

  @Test
  public void executeRequest_closeBeforeWrite_failsAborted() throws Exception {
    testHelper.executeRequest_closeBeforeWrite_failsAborted(engine);
  }

  @Test
  public void executeRequest_serverError_failsInternalError() throws Exception {
    testHelper.executeRequest_serverError_failsInternalError(engine);
  }

  @Test
  public void executeRequest_networkError_failsInternalError() throws Exception {
    testHelper.executeRequest_networkError_failsInternalError(engine);
  }

  @Test
  public void executeRequest_writeError_failsInternalError() throws Exception {
    testHelper.executeRequest_writeError_failsInternalError(engine);
  }

  @Test
  public void executeRequest_requestCanceled_requestNeverSent() throws Exception {
    testHelper.executeRequest_requestCanceled_requestNeverSent(engine);
  }

  @Test
  public void executeRequest_invalidUrl_failsInvalidArgument() {
    testHelper.executeRequest_invalidUrl_failsInvalidArgument(engine);
  }

  @Test
  public void executeRequest_fileUrl() throws Exception {
    // Note: File url testing doesn't exist in MockWebServerUrlEngineTestHelper because
    // it doesn't involve the MockWebServer and doesn't apply to all UrlEngine implementations.
    String message = "foobar";
    File sourceFile = temporaryFolder.newFile();
    Files.asCharSink(sourceFile, UTF_8).write(message);

    String url = sourceFile.toURI().toURL().toString();
    UrlRequest request = engine.createRequest(url).build();
    ListenableFuture<? extends UrlResponse> responseFuture = request.send();
    UrlResponse response = responseFuture.get();

    File targetFile = temporaryFolder.newFile();
    FileChannel channel = FileChannel.open(targetFile.toPath(), StandardOpenOption.WRITE);
    ListenableFuture<Long> writeFuture = response.readResponseBody(channel);
    long bytesWritten = writeFuture.get();
    response.close();
    channel.close();

    assertThat(bytesWritten).isGreaterThan(0L);
    assertThat(Files.asCharSource(targetFile, UTF_8).read()).isEqualTo(message);
    assertThat(response.getResponseCode()).isEqualTo(HttpURLConnection.HTTP_OK);
  }
}
