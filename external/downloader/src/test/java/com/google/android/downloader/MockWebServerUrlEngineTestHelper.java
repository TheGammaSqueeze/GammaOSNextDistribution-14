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

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import okio.Buffer;
import org.junit.rules.TemporaryFolder;

/**
 * Helper class for executing common test behaviors for {@link UrlEngine} instances that interact
 * with the {@link MockWebServer}.
 */
class MockWebServerUrlEngineTestHelper {
  private final TemporaryFolder temporaryFolder;
  private final TestingExecutorService executorService;
  private final MockWebServer server;

  MockWebServerUrlEngineTestHelper(
      TemporaryFolder temporaryFolder, TestingExecutorService executorService) {
    this.temporaryFolder = temporaryFolder;
    this.executorService = executorService;
    server = new MockWebServer();
  }

  void tearDown() throws Exception {
    server.shutdown();
  }

  void executeRequest_normalResponse_succeeds(UrlEngine engine) throws Exception {
    String message = "foobar";
    String encoding = "text/plain";
    String path = "/path";

    server.enqueue(
        new MockResponse()
            .setBody(message)
            .setHeader(HttpHeaders.CONTENT_TYPE, encoding)
            .setResponseCode(HttpURLConnection.HTTP_OK));
    server.start();

    HttpUrl url = server.url("/path");

    File file = temporaryFolder.newFile();
    RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
    FileChannel channel = randomAccessFile.getChannel();

    UrlRequest request =
        engine
            .createRequest(url.toString())
            .addHeader(HttpHeaders.CACHE_CONTROL, "no-cache")
            .build();
    ListenableFuture<UrlResponse> responseFuture = request.send();
    UrlResponse response = responseFuture.get();
    ListenableFuture<Long> writeFuture = response.readResponseBody(channel);
    long bytesWritten = writeFuture.get();
    response.close();
    channel.close();

    assertThat(bytesWritten).isEqualTo(message.getBytes(UTF_8).length);
    assertThat(Files.asCharSource(file, UTF_8).read()).isEqualTo(message);
    assertThat(response.getResponseCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    assertThat(response.getResponseHeaders())
        .containsEntry(HttpHeaders.CONTENT_TYPE, ImmutableList.of(encoding));
    assertThat(server.getRequestCount()).isEqualTo(1);
    RecordedRequest recordedRequest = server.takeRequest();
    assertThat(recordedRequest.getHeaders().toMultimap())
        .containsEntry(HttpHeaders.CACHE_CONTROL, ImmutableList.of("no-cache"));
    assertThat(recordedRequest.getMethod()).isEqualTo("GET");
    assertThat(recordedRequest.getPath()).isEqualTo(path);
  }

  void executeRequest_responseThrottled_succeeds(UrlEngine engine) throws Exception {
    String message = "foobar";
    String encoding = "text/plain";
    String path = "/path";

    server.enqueue(
        new MockResponse()
            .setBody(message)
            .throttleBody(2, 500, TimeUnit.MILLISECONDS)
            .setHeader(HttpHeaders.CONTENT_TYPE, encoding)
            .setResponseCode(HttpURLConnection.HTTP_OK));
    server.start();

    HttpUrl url = server.url("/path");

    File file = temporaryFolder.newFile();
    RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
    FileChannel channel = randomAccessFile.getChannel();

    UrlRequest request =
        engine
            .createRequest(url.toString())
            .addHeader(HttpHeaders.CACHE_CONTROL, "no-cache")
            .build();
    ListenableFuture<UrlResponse> responseFuture = request.send();
    UrlResponse response = responseFuture.get();
    ListenableFuture<Long> writeFuture = response.readResponseBody(channel);
    long bytesWritten = writeFuture.get();
    response.close();
    channel.close();

    assertThat(bytesWritten).isEqualTo(message.getBytes(UTF_8).length);
    assertThat(Files.asCharSource(file, UTF_8).read()).isEqualTo(message);
    assertThat(response.getResponseCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    assertThat(response.getResponseHeaders())
        .containsEntry(HttpHeaders.CONTENT_TYPE, ImmutableList.of(encoding));
    assertThat(server.getRequestCount()).isEqualTo(1);
    RecordedRequest recordedRequest = server.takeRequest();
    assertThat(recordedRequest.getHeaders().toMultimap())
        .containsEntry(HttpHeaders.CACHE_CONTROL, ImmutableList.of("no-cache"));
    assertThat(recordedRequest.getMethod()).isEqualTo("GET");
    assertThat(recordedRequest.getPath()).isEqualTo(path);
  }

  void executeRequest_largeResponse_succeeds(UrlEngine engine, long bufferSizeBytes)
      throws Exception {
    Buffer bodyBuffer = new Buffer();
    for (long i = 0; i < bufferSizeBytes / 8 /* = Long.BYTES */; i++) {
      bodyBuffer.writeLong(i);
    }

    String encoding = "text/plain";
    String path = "/path";

    server.enqueue(
        new MockResponse()
            .setBody(bodyBuffer.clone())
            .setHeader(HttpHeaders.CONTENT_TYPE, encoding)
            .setResponseCode(HttpURLConnection.HTTP_OK));
    server.start();

    HttpUrl url = server.url("/path");

    File file = temporaryFolder.newFile();
    RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
    FileChannel channel = randomAccessFile.getChannel();

    UrlRequest request =
        engine
            .createRequest(url.toString())
            .addHeader(HttpHeaders.CACHE_CONTROL, "no-cache")
            .build();
    ListenableFuture<UrlResponse> responseFuture = request.send();
    UrlResponse response = responseFuture.get();
    ListenableFuture<Long> writeFuture = response.readResponseBody(channel);
    long bytesWritten = writeFuture.get();
    response.close();
    channel.close();

    assertThat(bytesWritten).isEqualTo(bodyBuffer.size());
    assertThat(Files.asByteSource(file).read()).isEqualTo(bodyBuffer.readByteArray());
    assertThat(response.getResponseCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    assertThat(response.getResponseHeaders())
        .containsEntry(HttpHeaders.CONTENT_TYPE, ImmutableList.of(encoding));
    assertThat(server.getRequestCount()).isEqualTo(1);
    RecordedRequest recordedRequest = server.takeRequest();
    assertThat(recordedRequest.getHeaders().toMultimap())
        .containsEntry(HttpHeaders.CACHE_CONTROL, ImmutableList.of("no-cache"));
    assertThat(recordedRequest.getMethod()).isEqualTo("GET");
    assertThat(recordedRequest.getPath()).isEqualTo(path);
  }

  void executeRequest_closeBeforeWrite_failsAborted(UrlEngine engine) throws Exception {
    String message = "foobar";
    String encoding = "text/plain";

    server.enqueue(
        new MockResponse()
            .setBody(message)
            .setHeader(HttpHeaders.CONTENT_TYPE, encoding)
            .setResponseCode(HttpURLConnection.HTTP_OK));
    server.start();

    HttpUrl url = server.url("/path");

    File file = temporaryFolder.newFile();
    RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
    FileChannel channel = randomAccessFile.getChannel();

    UrlRequest request = engine.createRequest(url.toString()).build();
    ListenableFuture<UrlResponse> responseFuture = request.send();
    UrlResponse response = responseFuture.get();
    response.close();

    ListenableFuture<Long> writeFuture = response.readResponseBody(channel);
    ExecutionException exception = assertThrows(ExecutionException.class, writeFuture::get);
    channel.close();

    assertThat(exception).hasCauseThat().isInstanceOf(RequestException.class);
  }

  void executeRequest_serverError_failsInternalError(UrlEngine engine) throws Exception {
    server.enqueue(new MockResponse().setResponseCode(HttpURLConnection.HTTP_INTERNAL_ERROR));
    server.start();

    HttpUrl url = server.url("/path");

    UrlRequest request = engine.createRequest(url.toString()).build();
    ListenableFuture<UrlResponse> responseFuture = request.send();

    ExecutionException exception = assertThrows(ExecutionException.class, responseFuture::get);
    assertThat(exception).hasCauseThat().isInstanceOf(RequestException.class);

    RequestException requestException = (RequestException) exception.getCause();
    assertThat(requestException.getErrorDetails().getHttpStatusCode())
        .isEqualTo(HttpURLConnection.HTTP_INTERNAL_ERROR);
  }

  void executeRequest_networkError_failsInternalError(UrlEngine engine) throws Exception {
    server.enqueue(
        new MockResponse()
            .setBody("foobar")
            .setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
            .setResponseCode(HttpURLConnection.HTTP_OK));
    server.start();

    HttpUrl url = server.url("/path");

    UrlRequest request = engine.createRequest(url.toString()).build();
    ListenableFuture<UrlResponse> responseFuture = request.send();

    ExecutionException exception = assertThrows(ExecutionException.class, responseFuture::get);
    assertThat(exception).hasCauseThat().isInstanceOf(RequestException.class);
  }

  void executeRequest_writeError_failsInternalError(UrlEngine engine) throws Exception {
    String message = "foobar";
    String errorMessage = "error message";

    server.enqueue(new MockResponse().setBody(message).setResponseCode(HttpURLConnection.HTTP_OK));
    server.start();

    HttpUrl url = server.url("/path");

    UrlRequest request = engine.createRequest(url.toString()).build();
    ListenableFuture<UrlResponse> responseFuture = request.send();
    UrlResponse response = responseFuture.get();
    ListenableFuture<Long> writeFuture =
        response.readResponseBody(new ThrowingWritableByteChannel(errorMessage));
    ExecutionException exception = assertThrows(ExecutionException.class, writeFuture::get);
    response.close();

    assertThat(response.getResponseCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    assertThat(server.getRequestCount()).isEqualTo(1);

    assertThat(exception).hasCauseThat().isInstanceOf(RequestException.class);
    RequestException requestException = (RequestException) exception.getCause();
    assertThat(requestException).hasCauseThat().isInstanceOf(IOException.class);
    assertThat(requestException).hasCauseThat().hasMessageThat().isEqualTo(errorMessage);
  }

  void executeRequest_requestCanceled_requestNeverSent(UrlEngine engine) throws Exception {
    server.enqueue(
        new MockResponse()
            .setSocketPolicy(SocketPolicy.NO_RESPONSE)
            .setResponseCode(HttpURLConnection.HTTP_OK));
    server.start();
    executorService.pause();

    HttpUrl url = server.url("/path");

    UrlRequest request = engine.createRequest(url.toString()).build();
    ListenableFuture<UrlResponse> responseFuture = request.send();

    responseFuture.cancel(true);

    executorService.resume();

    assertThrows(CancellationException.class, responseFuture::get);

    // No requests were sent to the server!
    assertThat(server.getRequestCount()).isEqualTo(0);
  }

  void executeRequest_invalidUrl_failsInvalidArgument(UrlEngine engine) {
    UrlRequest request = engine.createRequest("foo:bar").build();
    ListenableFuture<UrlResponse> responseFuture = request.send();

    ExecutionException exception = assertThrows(ExecutionException.class, responseFuture::get);
    assertThat(exception).hasCauseThat().isInstanceOf(RequestException.class);
  }

  private static class ThrowingWritableByteChannel implements WritableByteChannel {
    private final String message;

    ThrowingWritableByteChannel(String message) {
      this.message = message;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
      throw new IOException(message);
    }

    @Override
    public boolean isOpen() {
      return true;
    }

    @Override
    public void close() {}
  }
}
