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

import static androidx.test.InstrumentationRegistry.getInstrumentation;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.chromium.net.CronetEngine;
import org.chromium.net.NetworkException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

/** Unit tests for CronetUrlEngine. */
@RunWith(AndroidJUnit4.class)
public class CronetUrlEngineTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private MockWebServerUrlEngineTestHelper testHelper;
  private CronetUrlEngine engine;

  @Before
  public void setUp() {
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    TestingExecutorService testingExecutorService = new TestingExecutorService(executorService);
    testHelper = new MockWebServerUrlEngineTestHelper(temporaryFolder, testingExecutorService);
    CronetEngine cronetEngine =
        new CronetEngine.Builder(getInstrumentation().getTargetContext()).build();
    engine = new CronetUrlEngine(cronetEngine, executorService);
  }

  @After
  public void tearDown() throws Exception {
    testHelper.tearDown();
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
    testHelper.executeRequest_largeResponse_succeeds(engine, CronetUrlEngine.BUFFER_SIZE_BYTES * 3);
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
  public void executeRequest_invalidUrl_failsInvalidArgument() throws Exception {
    testHelper.executeRequest_invalidUrl_failsInvalidArgument(engine);
  }

  @Test
  public void executeRequest_fileUrl_requestFails() throws Exception {
    // Note: File url testing doesn't exist in MockWebServerUrlEngineTestHelper because
    // it doesn't involve the MockWebServer and doesn't apply to all UrlEngine implementations.
    String message = "foobar";
    File sourceFile = temporaryFolder.newFile();
    Files.asCharSink(sourceFile, Charsets.UTF_8).write(message);

    String url = sourceFile.toURI().toURL().toString();
    UrlRequest request = engine.createRequest(url).build();
    ListenableFuture<? extends UrlResponse> responseFuture = request.send();

    // Cronet doesn't support file URLs. Verify that the request fails with an underlying
    // error that communicates this problem.
    ExecutionException exception = assertThrows(ExecutionException.class, responseFuture::get);
    assertThat(exception).hasCauseThat().isInstanceOf(RequestException.class);
    assertThat(exception).hasCauseThat().hasCauseThat().isInstanceOf(NetworkException.class);
    assertThat(exception)
        .hasCauseThat()
        .hasCauseThat()
        .hasMessageThat()
        .contains("UNKNOWN_URL_SCHEME");
  }
}
