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

import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;
import static java.util.concurrent.Executors.newSingleThreadExecutor;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.squareup.okhttp.Dispatcher;
import com.squareup.okhttp.OkHttpClient;
import java.util.concurrent.Executors;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for OkHttp2UrlEngine. */
@RunWith(JUnit4.class)
public class OkHttp2UrlEngineTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private MockWebServerUrlEngineTestHelper testHelper;
  private OkHttp2UrlEngine engine;
  private TestingExecutorService dispatchExecutorService;
  private ListeningExecutorService transferExecutorService;

  @Before
  public void setUp() {
    dispatchExecutorService = new TestingExecutorService(Executors.newSingleThreadExecutor());
    testHelper = new MockWebServerUrlEngineTestHelper(temporaryFolder, dispatchExecutorService);
    transferExecutorService = listeningDecorator(newSingleThreadExecutor());
    // Note: The OkHttpClient dispatcher uses the TestingExecutorService (which waits to execute
    // tasks) in order to ensure that OkHttp requests are executed in an asynchronous manner, so
    // that we can properly test request cancellation.
    OkHttpClient client = new OkHttpClient();
    client.setDispatcher(new Dispatcher(dispatchExecutorService));
    engine = new OkHttp2UrlEngine(client, transferExecutorService);
  }

  @After
  public void tearDown() throws Exception {
    testHelper.tearDown();
    transferExecutorService.shutdown();
    dispatchExecutorService.shutdown();
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
    testHelper.executeRequest_largeResponse_succeeds(engine, 1024 * 1024);
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
}
