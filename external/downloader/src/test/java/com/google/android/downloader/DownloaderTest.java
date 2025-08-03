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

import static com.google.common.truth.StreamSubject.streams;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertThrows;
import static org.mockito.AdditionalMatchers.or;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.android.downloader.Downloader.State;
import com.google.common.base.Utf8;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.ClosingFuture;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.google.testing.mockito.Mocks;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

/** Unit tests for Downloader. */
@RunWith(JUnit4.class)
public final class DownloaderTest {
  private static final int CONNECT_TIMEOUT_MS = 500;
  private static final int READ_TIMEOUT_MS = 300;
  private static final int MAX_CONCURRENT_DOWNLOADS = 1;

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Rule public Mocks mocks = new Mocks(this);
  @Rule public TestExecutorRule testExecutorRule = new TestExecutorRule(Duration.ofSeconds(2));

  @Mock Downloader.StateChangeCallback mockCallback;

  private ListeningExecutorService urlEngineExecutor;
  private ExecutorService ioExecutor;
  private MockWebServer mockWebServer;
  private DispatcherImpl dispatcher;

  @Before
  public void setUp() {
    mockWebServer = new MockWebServer();
    dispatcher = new DispatcherImpl();
    mockWebServer.setDispatcher(dispatcher);
    urlEngineExecutor =
        MoreExecutors.listeningDecorator(testExecutorRule.newSingleThreadExecutor());
    ioExecutor = testExecutorRule.newSingleThreadExecutor();
  }

  private Downloader.Builder buildDownloader() {
    return new Downloader.Builder()
        .addUrlEngine(
            ImmutableSet.of("http", "https", "file"),
            new PlatformUrlEngine(urlEngineExecutor, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS))
        .withConnectivityHandler(new AlwaysConnected())
        .withIOExecutor(ioExecutor)
        .withLogger(new FloggerDownloaderLogger())
        .withMaxConcurrentDownloads(MAX_CONCURRENT_DOWNLOADS);
  }

  @After
  public void tearDown() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  public void downloadOneFile() throws Exception {
    File destinationFile = temporaryFolder.newFile();
    File metadataFile = new File(destinationFile.getParent(), destinationFile.getName() + ".meta");
    SimpleFileDownloadDestination destination =
        new SimpleFileDownloadDestination(destinationFile, metadataFile);

    String contentTag = "content_tag_abc";
    long lastModifiedTimeSeconds = 123456789;
    dispatcher.responseForPath(
        "/foo",
        () ->
            new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody("hello world")
                .setHeader(HttpHeaders.ETAG, contentTag)
                .setHeader(
                    HttpHeaders.LAST_MODIFIED,
                    RFC_1123_DATE_TIME
                        .withLocale(Locale.US)
                        .withZone(ZoneId.of("UTC"))
                        .format(Instant.ofEpochSecond(lastModifiedTimeSeconds))));
    mockWebServer.start();

    Downloader downloader = buildDownloader().build();
    DownloadRequest request =
        downloader.newRequestBuilder(mockWebServer.url("/foo").uri(), destination).build();
    FluentFuture<DownloadResult> resultFuture = downloader.execute(request);
    assertThat(resultFuture.isDone()).isFalse();

    DownloadResult result = resultFuture.get();
    assertThat(result.bytesWritten()).isEqualTo(11); // == "hello world".length
    assertThat(Files.asCharSource(destinationFile, UTF_8).read()).isEqualTo("hello world");

    assertThat(destination.numExistingBytes()).isEqualTo(11);
    DownloadMetadata metadata = destination.readMetadata();
    assertThat(metadata).isEqualTo(DownloadMetadata.create(contentTag, lastModifiedTimeSeconds));
  }

  @Test
  public void downloadOneFile_existingContent_partialContent() throws Exception {
    File destinationFile = temporaryFolder.newFile();
    File metadataFile = new File(destinationFile.getParent(), destinationFile.getName() + ".meta");
    SimpleFileDownloadDestination destination =
        new SimpleFileDownloadDestination(destinationFile, metadataFile);
    Files.asCharSink(destinationFile, UTF_8).write("hello world");

    dispatcher.responseForPath(
        "/foo",
        () ->
            new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_PARTIAL)
                .setBody("goodbye world")
                // 24 == ("hello world" + "goodbye world").length
                .setHeader(HttpHeaders.CONTENT_RANGE, "bytes 11-24/24"));
    mockWebServer.start();

    Downloader downloader = buildDownloader().build();
    DownloadRequest request =
        downloader.newRequestBuilder(mockWebServer.url("/foo").uri(), destination).build();
    FluentFuture<DownloadResult> resultFuture = downloader.execute(request);
    assertThat(resultFuture.isDone()).isFalse();

    DownloadResult result = resultFuture.get();
    assertThat(result.bytesWritten()).isEqualTo(13); // == "goodbye world".length
    assertThat(Files.asCharSource(destinationFile, UTF_8).read())
        .isEqualTo("hello worldgoodbye world");
    assertThat(mockWebServer.takeRequest().getHeader(HttpHeaders.RANGE))
        .isEqualTo("bytes=11-"); // == "hello world".length
  }

  @Test
  public void downloadOneFile_existingContent_noMetadata() throws Exception {
    File destinationFile = temporaryFolder.newFile();
    File metadataFile = new File(destinationFile.getParent(), destinationFile.getName() + ".meta");
    SimpleFileDownloadDestination destination =
        new SimpleFileDownloadDestination(destinationFile, metadataFile);
    Files.asCharSink(destinationFile, UTF_8).write("hello world");

    dispatcher.responseForPath(
        "/foo",
        () ->
            new MockResponse().setResponseCode(HttpURLConnection.HTTP_OK).setBody("goodbye world"));
    mockWebServer.start();

    Downloader downloader = buildDownloader().build();
    DownloadRequest request =
        downloader.newRequestBuilder(mockWebServer.url("/foo").uri(), destination).build();
    downloader.execute(request).get();

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getHeader(HttpHeaders.RANGE))
        .isEqualTo("bytes=11-"); // == "hello world".length
    assertThat(recordedRequest.getHeader(HttpHeaders.IF_RANGE)).isNull();
  }

  @Test
  public void downloadOneFile_existingContent_withEtag() throws Exception {
    File destinationFile = temporaryFolder.newFile();
    File metadataFile = new File(destinationFile.getParent(), destinationFile.getName() + ".meta");
    SimpleFileDownloadDestination destination =
        new SimpleFileDownloadDestination(destinationFile, metadataFile);
    String contentTag = "content_tag_abc";
    destination.openByteChannel(0L, DownloadMetadata.create(contentTag, 0L)).close();
    Files.asCharSink(destinationFile, UTF_8).write("hello world");

    dispatcher.responseForPath(
        "/foo",
        () ->
            new MockResponse().setResponseCode(HttpURLConnection.HTTP_OK).setBody("goodbye world"));
    mockWebServer.start();

    Downloader downloader = buildDownloader().build();
    DownloadRequest request =
        downloader.newRequestBuilder(mockWebServer.url("/foo").uri(), destination).build();
    downloader.execute(request).get();

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getHeader(HttpHeaders.RANGE))
        .isEqualTo("bytes=11-"); // == "hello world".length
    assertThat(recordedRequest.getHeader(HttpHeaders.IF_RANGE)).isEqualTo(contentTag);
  }

  @Test
  public void downloadOneFile_existingContent_withLastModified() throws Exception {
    File destinationFile = temporaryFolder.newFile();
    File metadataFile = new File(destinationFile.getParent(), destinationFile.getName() + ".meta");
    SimpleFileDownloadDestination destination =
        new SimpleFileDownloadDestination(destinationFile, metadataFile);
    long lastModifiedTimestampSeconds = 123456789;
    destination
        .openByteChannel(0L, DownloadMetadata.create("", lastModifiedTimestampSeconds))
        .close();
    Files.asCharSink(destinationFile, UTF_8).write("hello world");

    dispatcher.responseForPath(
        "/foo",
        () ->
            new MockResponse().setResponseCode(HttpURLConnection.HTTP_OK).setBody("goodbye world"));
    mockWebServer.start();

    Downloader downloader = buildDownloader().build();
    DownloadRequest request =
        downloader.newRequestBuilder(mockWebServer.url("/foo").uri(), destination).build();
    downloader.execute(request).get();

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getHeader(HttpHeaders.RANGE))
        .isEqualTo("bytes=11-"); // == "hello world".length
    assertThat(recordedRequest.getHeader(HttpHeaders.IF_RANGE))
        .isEqualTo(
            RFC_1123_DATE_TIME
                .withLocale(Locale.US)
                .withZone(ZoneId.of("UTC"))
                .format(Instant.ofEpochSecond(lastModifiedTimestampSeconds)));
  }

  @Test
  public void downloadOneFile_existingContent_partialContent_overwritesExistingContent()
      throws Exception {
    File destinationFile = temporaryFolder.newFile();
    File metadataFile = new File(destinationFile.getParent(), destinationFile.getName() + ".meta");
    SimpleFileDownloadDestination destination =
        new SimpleFileDownloadDestination(destinationFile, metadataFile);
    Files.asCharSink(destinationFile, UTF_8).write("hello world");

    dispatcher.responseForPath(
        "/foo",
        () ->
            new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_PARTIAL)
                .setBody("goodbye world")
                // 6 == ("hello world" - "world").length
                // 19 == ("hello world" - "world" + "goodbye world").length
                .setHeader(HttpHeaders.CONTENT_RANGE, "bytes 6-19/19"));
    mockWebServer.start();

    Downloader downloader = buildDownloader().build();
    DownloadRequest request =
        downloader.newRequestBuilder(mockWebServer.url("/foo").uri(), destination).build();
    FluentFuture<DownloadResult> resultFuture = downloader.execute(request);
    assertThat(resultFuture.isDone()).isFalse();

    DownloadResult result = resultFuture.get();
    assertThat(result.bytesWritten()).isEqualTo(13); // == "goodbye world".length
    assertThat(Files.asCharSource(destinationFile, UTF_8).read()).isEqualTo("hello goodbye world");
    assertThat(mockWebServer.takeRequest().getHeader(HttpHeaders.RANGE))
        .isEqualTo("bytes=11-"); // == "hello world".length
  }

  @Test
  public void downloadOneFile_existingContent_noServerSupport() throws Exception {
    File destinationFile = temporaryFolder.newFile();
    File metadataFile = new File(destinationFile.getParent(), destinationFile.getName() + ".meta");
    SimpleFileDownloadDestination destination =
        new SimpleFileDownloadDestination(destinationFile, metadataFile);
    Files.asCharSink(destinationFile, UTF_8).write("hello world");

    // Let the server ignore our Range header and reply with the entire resource.
    dispatcher.responseForPath(
        "/foo",
        () ->
            new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody("hello worldgoodbye world"));
    mockWebServer.start();

    Downloader downloader = buildDownloader().build();
    DownloadRequest request =
        downloader.newRequestBuilder(mockWebServer.url("/foo").uri(), destination).build();
    FluentFuture<DownloadResult> resultFuture = downloader.execute(request);
    assertThat(resultFuture.isDone()).isFalse();

    DownloadResult result = resultFuture.get();
    // 24 == ("hello world" + "goodbye world").length
    assertThat(result.bytesWritten()).isEqualTo(24);
    assertThat(Files.asCharSource(destinationFile, UTF_8).read())
        .isEqualTo("hello worldgoodbye world");
    assertThat(mockWebServer.takeRequest().getHeader(HttpHeaders.RANGE))
        .isEqualTo("bytes=11-"); // == "hello world".length
  }

  @Test
  public void downloadOneFile_rangeNotSatisfiable() throws Exception {
    File destinationFile = temporaryFolder.newFile();
    File metadataFile = new File(destinationFile.getParent(), destinationFile.getName() + ".meta");
    SimpleFileDownloadDestination destination =
        new SimpleFileDownloadDestination(destinationFile, metadataFile);

    dispatcher.responseForPath(
        "/foo", () -> new MockResponse().setResponseCode(Downloader.HTTP_RANGE_NOT_SATISFIABLE));
    mockWebServer.start();

    Downloader downloader = buildDownloader().build();
    DownloadRequest request =
        downloader.newRequestBuilder(mockWebServer.url("/foo").uri(), destination).build();
    FluentFuture<DownloadResult> resultFuture = downloader.execute(request);
    assertThat(resultFuture.isDone()).isFalse();

    DownloadResult result = resultFuture.get();
    assertThat(result.bytesWritten()).isEqualTo(0);
    assertThat(Files.asCharSource(destinationFile, UTF_8).read()).isEmpty();
  }

  @Test
  public void downloadOneFile_oAuthTokenProvider() throws Exception {
    File destinationFile = temporaryFolder.newFile();
    File metadataFile = new File(destinationFile.getParent(), destinationFile.getName() + ".meta");
    SimpleFileDownloadDestination destination =
        new SimpleFileDownloadDestination(destinationFile, metadataFile);

    dispatcher.responseForPath(
        "/foo",
        () -> new MockResponse().setResponseCode(HttpURLConnection.HTTP_OK).setBody("hello world"));
    mockWebServer.start();

    Downloader downloader = buildDownloader().build();
    DownloadRequest request =
        downloader
            .newRequestBuilder(mockWebServer.url("/foo").uri(), destination)
            .setOAuthTokenProvider(uri -> Futures.immediateFuture("test_token"))
            .build();
    FluentFuture<DownloadResult> resultFuture = downloader.execute(request);
    assertThat(resultFuture.isDone()).isFalse();

    DownloadResult result = resultFuture.get();
    assertThat(result.bytesWritten()).isEqualTo(Utf8.encodedLength("hello world"));
    assertThat(Files.asCharSource(destinationFile, UTF_8).read()).isEqualTo("hello world");
    assertThat(mockWebServer.takeRequest().getHeader(HttpHeaders.AUTHORIZATION))
        .isEqualTo("Bearer test_token");
  }

  @Test
  public void downloadOneFile_oAuthTokenProvider_nullToken() throws Exception {
    File destinationFile = temporaryFolder.newFile();
    File metadataFile = new File(destinationFile.getParent(), destinationFile.getName() + ".meta");
    SimpleFileDownloadDestination destination =
        new SimpleFileDownloadDestination(destinationFile, metadataFile);

    dispatcher.responseForPath(
        "/foo",
        () -> new MockResponse().setResponseCode(HttpURLConnection.HTTP_OK).setBody("hello world"));
    mockWebServer.start();

    Downloader downloader = buildDownloader().build();
    DownloadRequest request =
        downloader
            .newRequestBuilder(mockWebServer.url("/foo").uri(), destination)
            .setOAuthTokenProvider(uri -> Futures.immediateFuture(null))
            .build();
    FluentFuture<DownloadResult> resultFuture = downloader.execute(request);
    assertThat(resultFuture.isDone()).isFalse();

    DownloadResult result = resultFuture.get();
    assertThat(result.bytesWritten()).isEqualTo(Utf8.encodedLength("hello world"));
    assertThat(Files.asCharSource(destinationFile, UTF_8).read()).isEqualTo("hello world");
    assertThat(mockWebServer.takeRequest().getHeaders().names())
        .doesNotContain(HttpHeaders.AUTHORIZATION);
  }

  @Test
  public void downloadOneFile_notFound() throws Exception {
    File destinationFile = temporaryFolder.newFile();
    File metadataFile = new File(destinationFile.getParent(), destinationFile.getName() + ".meta");
    SimpleFileDownloadDestination destination =
        new SimpleFileDownloadDestination(destinationFile, metadataFile);

    mockWebServer.start();

    Downloader downloader = buildDownloader().build();
    downloader.registerStateChangeCallback(mockCallback, MoreExecutors.directExecutor());
    DownloadRequest request =
        downloader.newRequestBuilder(mockWebServer.url("/foo").uri(), destination).build();
    FluentFuture<DownloadResult> resultFuture = downloader.execute(request);
    assertThat(resultFuture.isDone()).isFalse();
    verify(mockCallback, atLeastOnce()).onStateChange(State.create(1, 0, 0));

    Exception exception = assertThrows(Exception.class, resultFuture::get);
    RequestException requestException = Downloader.getRequestException(exception);
    assertThat(requestException).isNotNull();

    // Flush through the I/O executor to make sure internal state has settled.
    ioExecutor.submit(() -> {}).get();

    verify(mockCallback, atLeastOnce()).onStateChange(State.create(0, 0, 0));
  }

  @Test
  public void downloadOneFile_customHeader() throws Exception {
    File destinationFile = temporaryFolder.newFile();
    File metadataFile = new File(destinationFile.getParent(), destinationFile.getName() + ".meta");
    SimpleFileDownloadDestination destination =
        new SimpleFileDownloadDestination(destinationFile, metadataFile);

    dispatcher.responseForPath(
        "/foo",
        () -> new MockResponse().setResponseCode(HttpURLConnection.HTTP_OK).setBody("hello world"));
    mockWebServer.start();

    String headerKey = "fooHeader";
    String headerValue = "fooHeaderValue";
    Downloader downloader = buildDownloader().build();
    DownloadRequest request =
        downloader
            .newRequestBuilder(mockWebServer.url("/foo").uri(), destination)
            .addHeader(headerKey, headerValue)
            .build();
    FluentFuture<DownloadResult> resultFuture = downloader.execute(request);
    assertThat(resultFuture.isDone()).isFalse();

    DownloadResult result = resultFuture.get();
    assertThat(result.bytesWritten()).isEqualTo(Utf8.encodedLength("hello world"));
    assertThat(Files.asCharSource(destinationFile, UTF_8).read()).isEqualTo("hello world");
    assertThat(mockWebServer.takeRequest().getHeader(headerKey)).isEqualTo(headerValue);
  }

  @Test
  public void downloadOneFile_fileSystem() throws Exception {
    File sourceFile = temporaryFolder.newFile();
    File destinationFile = temporaryFolder.newFile();
    File metadataFile = new File(destinationFile.getParent(), destinationFile.getName() + ".meta");
    SimpleFileDownloadDestination destination =
        new SimpleFileDownloadDestination(destinationFile, metadataFile);

    Files.asCharSink(sourceFile, UTF_8).write("hello world");

    Downloader downloader = buildDownloader().build();
    DownloadRequest request = downloader.newRequestBuilder(sourceFile.toURI(), destination).build();
    FluentFuture<DownloadResult> resultFuture = downloader.execute(request);
    assertThat(resultFuture.isDone()).isFalse();

    DownloadResult result = resultFuture.get();
    assertThat(result.bytesWritten()).isEqualTo(Utf8.encodedLength("hello world"));
    assertThat(Files.asCharSource(destinationFile, UTF_8).read()).isEqualTo("hello world");
    assertThat(mockWebServer.getRequestCount()).isEqualTo(0);
  }

  @Test
  public void downloadOneFile_fileSystem_existingContent() throws Exception {
    File sourceFile = temporaryFolder.newFile();
    File destinationFile = temporaryFolder.newFile();
    File metadataFile = new File(destinationFile.getParent(), destinationFile.getName() + ".meta");
    SimpleFileDownloadDestination destination =
        new SimpleFileDownloadDestination(destinationFile, metadataFile);

    Files.asCharSink(sourceFile, UTF_8).write("hello world");
    Files.asCharSink(destinationFile, UTF_8).write("hello");

    Downloader downloader = buildDownloader().build();
    DownloadRequest request = downloader.newRequestBuilder(sourceFile.toURI(), destination).build();
    FluentFuture<DownloadResult> resultFuture = downloader.execute(request);
    assertThat(resultFuture.isDone()).isFalse();

    DownloadResult result = resultFuture.get();
    assertThat(result.bytesWritten()).isEqualTo(Utf8.encodedLength("hello world"));
    assertThat(Files.asCharSource(destinationFile, UTF_8).read()).isEqualTo("hello world");
  }

  @Test
  public void downloadOneFile_retryAfterFailure() throws Exception {
    mockWebServer.setDispatcher(new QueueDispatcher());

    File destinationFile = temporaryFolder.newFile();
    File metadataFile = new File(destinationFile.getParent(), destinationFile.getName() + ".meta");
    SimpleFileDownloadDestination destination =
        new SimpleFileDownloadDestination(destinationFile, metadataFile);

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(HttpURLConnection.HTTP_GATEWAY_TIMEOUT));
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(HttpURLConnection.HTTP_OK).setBody("hello world"));
    mockWebServer.start();

    Downloader downloader = buildDownloader().build();
    DownloadRequest request =
        downloader.newRequestBuilder(mockWebServer.url("/foo").uri(), destination).build();
    FluentFuture<DownloadResult> resultFuture = downloader.execute(request);
    assertThat(resultFuture.isDone()).isFalse();

    DownloadResult result = resultFuture.get();
    assertThat(result.bytesWritten()).isEqualTo(Utf8.encodedLength("hello world"));
    assertThat(Files.asCharSource(destinationFile, UTF_8).read()).isEqualTo("hello world");
    assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
  }

  @Test
  public void downloadOneFile_waitForConnectivity() throws Exception {
    File destinationFile = temporaryFolder.newFile();
    File metadataFile = new File(destinationFile.getParent(), destinationFile.getName() + ".meta");
    SimpleFileDownloadDestination destination =
        new SimpleFileDownloadDestination(destinationFile, metadataFile);
    String text = "hello world";

    dispatcher.responseForPath(
        "/foo", () -> new MockResponse().setResponseCode(HttpURLConnection.HTTP_OK).setBody(text));
    mockWebServer.start();

    ManualConnectivity connectivityHandler = new ManualConnectivity();

    Downloader downloader = buildDownloader().withConnectivityHandler(connectivityHandler).build();
    downloader.registerStateChangeCallback(mockCallback, MoreExecutors.directExecutor());
    DownloadRequest request =
        downloader.newRequestBuilder(mockWebServer.url("/foo").uri(), destination).build();

    FluentFuture<DownloadResult> resultFuture = downloader.execute(request);
    assertThat(resultFuture.isDone()).isFalse();
    assertThrows(TimeoutException.class, () -> resultFuture.get(1, SECONDS));
    assertThat(mockWebServer.getRequestCount()).isEqualTo(0);

    verify(mockCallback, atLeastOnce()).onStateChange(State.create(0, 0, 1));

    connectivityHandler.setConnectivitySatisfied();
    connectivityHandler.succeed();

    DownloadResult result = resultFuture.get();
    assertThat(result.bytesWritten()).isEqualTo(Utf8.encodedLength(text));
    assertThat(Files.asCharSource(destinationFile, UTF_8).read()).isEqualTo(text);
    assertThat(mockWebServer.getRequestCount()).isEqualTo(1);

    // Flush through the I/O executor to make sure internal state has settled.
    ioExecutor.submit(() -> {}).get();

    verify(mockCallback, atLeastOnce()).onStateChange(State.create(0, 0, 0));
  }

  @Test
  public void downloadOneFile_waitForConnectivity_canceled() throws Exception {
    File destinationFile = temporaryFolder.newFile();
    File metadataFile = new File(destinationFile.getParent(), destinationFile.getName() + ".meta");
    SimpleFileDownloadDestination destination =
        new SimpleFileDownloadDestination(destinationFile, metadataFile);
    String text = "hello world";

    dispatcher.responseForPath(
        "/foo", () -> new MockResponse().setResponseCode(HttpURLConnection.HTTP_OK).setBody(text));
    mockWebServer.start();

    ManualConnectivity connectivityHandler = new ManualConnectivity();

    Downloader downloader = buildDownloader().withConnectivityHandler(connectivityHandler).build();
    downloader.registerStateChangeCallback(mockCallback, MoreExecutors.directExecutor());
    DownloadRequest request =
        downloader.newRequestBuilder(mockWebServer.url("/foo").uri(), destination).build();

    FluentFuture<DownloadResult> resultFuture = downloader.execute(request);
    assertThat(resultFuture.isDone()).isFalse();
    assertThat(mockWebServer.getRequestCount()).isEqualTo(0);

    verify(mockCallback, atLeastOnce()).onStateChange(State.create(0, 0, 1));

    resultFuture.cancel(true);

    connectivityHandler.setConnectivitySatisfied();
    connectivityHandler.succeed();

    assertThrows(CancellationException.class, resultFuture::get);
    assertThat(mockWebServer.getRequestCount()).isEqualTo(0);

    verify(mockCallback, atLeastOnce()).onStateChange(State.create(0, 0, 0));
  }

  @Test
  public void downloadOneFile_timeoutConnectivity() throws Exception {
    File destinationFile = temporaryFolder.newFile();
    File metadataFile = new File(destinationFile.getParent(), destinationFile.getName() + ".meta");
    SimpleFileDownloadDestination destination =
        new SimpleFileDownloadDestination(destinationFile, metadataFile);
    String text = "hello world";

    dispatcher.responseForPath(
        "/foo", () -> new MockResponse().setResponseCode(HttpURLConnection.HTTP_OK).setBody(text));
    mockWebServer.start();

    ManualConnectivity connectivityHandler = new ManualConnectivity();

    Downloader downloader = buildDownloader().withConnectivityHandler(connectivityHandler).build();
    downloader.registerStateChangeCallback(mockCallback, MoreExecutors.directExecutor());
    DownloadRequest request =
        downloader.newRequestBuilder(mockWebServer.url("/foo").uri(), destination).build();

    FluentFuture<DownloadResult> resultFuture = downloader.execute(request);
    assertThat(resultFuture.isDone()).isFalse();
    assertThrows(TimeoutException.class, () -> resultFuture.get(1, SECONDS));
    assertThat(mockWebServer.getRequestCount()).isEqualTo(0);

    verify(mockCallback, atLeastOnce()).onStateChange(State.create(0, 0, 1));

    connectivityHandler.setConnectivitySatisfied();
    connectivityHandler.fail(new TimeoutException());

    DownloadResult result = resultFuture.get();
    assertThat(result.bytesWritten()).isEqualTo(Utf8.encodedLength(text));
    assertThat(Files.asCharSource(destinationFile, UTF_8).read()).isEqualTo(text);
    assertThat(mockWebServer.getRequestCount()).isEqualTo(1);

    // Flush through the I/O executor to make sure internal state has settled.
    ioExecutor.submit(() -> {}).get();

    verify(mockCallback, atLeastOnce()).onStateChange(State.create(0, 0, 0));
  }

  @Test
  public void downloadTwoFiles_sequentially() throws Exception {
    File destinationFile1 = temporaryFolder.newFile();
    File destinationFile2 = temporaryFolder.newFile();
    File metadataFile1 =
        new File(destinationFile1.getParent(), destinationFile1.getName() + ".meta");
    File metadataFile2 =
        new File(destinationFile2.getParent(), destinationFile2.getName() + ".meta");
    SimpleFileDownloadDestination destination1 =
        new SimpleFileDownloadDestination(destinationFile1, metadataFile1);
    SimpleFileDownloadDestination destination2 =
        new SimpleFileDownloadDestination(destinationFile2, metadataFile2);

    dispatcher.responseForPath(
        "/foo",
        () ->
            new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBodyDelay(50, TimeUnit.MILLISECONDS)
                .setBody("hello world one"));
    dispatcher.responseForPath(
        "/bar",
        () ->
            new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBodyDelay(50, TimeUnit.MILLISECONDS)
                .setBody("hello world two"));
    mockWebServer.start();

    Downloader downloader = buildDownloader().build();
    downloader.registerStateChangeCallback(mockCallback, MoreExecutors.directExecutor());
    DownloadRequest request1 =
        downloader.newRequestBuilder(mockWebServer.url("/foo").uri(), destination1).build();
    DownloadRequest request2 =
        downloader.newRequestBuilder(mockWebServer.url("/bar").uri(), destination2).build();
    FluentFuture<DownloadResult> resultFuture1 = downloader.execute(request1);
    FluentFuture<DownloadResult> resultFuture2 = downloader.execute(request2);

    verify(mockCallback, atLeastOnce()).onStateChange(State.create(1, 1, 0));

    DownloadResult result1 = resultFuture1.get();
    DownloadResult result2 = resultFuture2.get();
    assertThat(result1.bytesWritten()).isEqualTo(15); // == "hello world one".length
    assertThat(result2.bytesWritten()).isEqualTo(15); // == "hello world two".length
    assertThat(Files.asCharSource(destinationFile1, UTF_8).read()).isEqualTo("hello world one");
    assertThat(Files.asCharSource(destinationFile2, UTF_8).read()).isEqualTo("hello world two");
    assertThat(mockWebServer.getRequestCount()).isEqualTo(2);

    // Flush through the I/O executor to make sure internal state has settled.
    ioExecutor.submit(() -> {}).get();

    verify(mockCallback, atLeastOnce()).onStateChange(State.create(0, 0, 0));
  }

  @Test
  public void downloadTwoFiles_sequentially_unregisterCallback() throws Exception {
    File destinationFile1 = temporaryFolder.newFile();
    File destinationFile2 = temporaryFolder.newFile();
    File metadataFile1 =
        new File(destinationFile1.getParent(), destinationFile1.getName() + ".meta");
    File metadataFile2 =
        new File(destinationFile2.getParent(), destinationFile2.getName() + ".meta");
    SimpleFileDownloadDestination destination1 =
        new SimpleFileDownloadDestination(destinationFile1, metadataFile1);
    SimpleFileDownloadDestination destination2 =
        new SimpleFileDownloadDestination(destinationFile2, metadataFile2);

    dispatcher.responseForPath(
        "/foo",
        () ->
            new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBodyDelay(50, TimeUnit.MILLISECONDS)
                .setBody("hello world one"));
    dispatcher.responseForPath(
        "/bar",
        () ->
            new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBodyDelay(50, TimeUnit.MILLISECONDS)
                .setBody("hello world two"));
    mockWebServer.start();

    Downloader downloader = buildDownloader().build();
    downloader.registerStateChangeCallback(mockCallback, MoreExecutors.directExecutor());

    DownloadRequest request1 =
        downloader.newRequestBuilder(mockWebServer.url("/foo").uri(), destination1).build();
    FluentFuture<DownloadResult> resultFuture1 = downloader.execute(request1);

    downloader.unregisterStateChangeCallback(mockCallback);

    DownloadRequest request2 =
        downloader.newRequestBuilder(mockWebServer.url("/bar").uri(), destination2).build();
    FluentFuture<DownloadResult> resultFuture2 = downloader.execute(request2);

    DownloadResult result1 = resultFuture1.get();
    DownloadResult result2 = resultFuture2.get();
    assertThat(result1.bytesWritten()).isEqualTo(15); // == "hello world one".length
    assertThat(result2.bytesWritten()).isEqualTo(15); // == "hello world two".length
    assertThat(Files.asCharSource(destinationFile1, UTF_8).read()).isEqualTo("hello world one");
    assertThat(Files.asCharSource(destinationFile2, UTF_8).read()).isEqualTo("hello world two");
    assertThat(mockWebServer.getRequestCount()).isEqualTo(2);

    // Only run two times, for the first request:
    // - Once for enqueueing the request
    // - Once for starting the request
    // but no more progress registered otherwise.
    verify(mockCallback, times(2)).onStateChange(any());
  }

  @Test
  public void downloadTwoFiles_concurrently() throws Exception {
    File destinationFile1 = temporaryFolder.newFile();
    File destinationFile2 = temporaryFolder.newFile();
    File metadataFile1 =
        new File(destinationFile1.getParent(), destinationFile1.getName() + ".meta");
    File metadataFile2 =
        new File(destinationFile2.getParent(), destinationFile2.getName() + ".meta");
    SimpleFileDownloadDestination destination1 =
        new SimpleFileDownloadDestination(destinationFile1, metadataFile1);
    SimpleFileDownloadDestination destination2 =
        new SimpleFileDownloadDestination(destinationFile2, metadataFile2);

    dispatcher.responseForPath(
        "/foo",
        () ->
            new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBodyDelay(50, TimeUnit.MILLISECONDS)
                .setBody("hello world one"));
    dispatcher.responseForPath(
        "/bar",
        () ->
            new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBodyDelay(50, TimeUnit.MILLISECONDS)
                .setBody("hello world two"));
    mockWebServer.start();

    Downloader downloader = buildDownloader().withMaxConcurrentDownloads(2).build();
    downloader.registerStateChangeCallback(mockCallback, MoreExecutors.directExecutor());
    DownloadRequest request1 =
        downloader.newRequestBuilder(mockWebServer.url("/foo").uri(), destination1).build();
    DownloadRequest request2 =
        downloader.newRequestBuilder(mockWebServer.url("/bar").uri(), destination2).build();
    FluentFuture<DownloadResult> resultFuture1 = downloader.execute(request1);
    FluentFuture<DownloadResult> resultFuture2 = downloader.execute(request2);
    assertThat(resultFuture1.isDone()).isFalse();
    assertThat(resultFuture2.isDone()).isFalse();

    verify(mockCallback, atLeastOnce()).onStateChange(State.create(2, 0, 0));

    DownloadResult result1 = resultFuture1.get();
    DownloadResult result2 = resultFuture2.get();
    assertThat(result1.bytesWritten()).isEqualTo(15); // == "hello world one".length
    assertThat(result2.bytesWritten()).isEqualTo(15); // == "hello world two".length
    assertThat(Files.asCharSource(destinationFile1, UTF_8).read()).isEqualTo("hello world one");
    assertThat(Files.asCharSource(destinationFile2, UTF_8).read()).isEqualTo("hello world two");
    assertThat(mockWebServer.getRequestCount()).isEqualTo(2);

    // Flush through the I/O executor to make sure internal state has settled.
    ioExecutor.submit(() -> {}).get();

    verify(mockCallback, atLeastOnce()).onStateChange(State.create(0, 0, 0));
  }

  @Test
  public void downloadThreeFiles_sequentiallyWithCancellation() throws Exception {
    File destinationFile1 = temporaryFolder.newFile();
    File destinationFile2 = temporaryFolder.newFile();
    File destinationFile3 = temporaryFolder.newFile();
    File metadataFile1 =
        new File(destinationFile1.getParent(), destinationFile1.getName() + ".meta");
    File metadataFile2 =
        new File(destinationFile2.getParent(), destinationFile2.getName() + ".meta");
    File metadataFile3 =
        new File(destinationFile3.getParent(), destinationFile3.getName() + ".meta");
    SimpleFileDownloadDestination destination1 =
        new SimpleFileDownloadDestination(destinationFile1, metadataFile1);
    SimpleFileDownloadDestination destination2 =
        new SimpleFileDownloadDestination(destinationFile2, metadataFile2);
    SimpleFileDownloadDestination destination3 =
        new SimpleFileDownloadDestination(destinationFile3, metadataFile3);

    dispatcher.responseForPath(
        "/foo",
        () ->
            new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBodyDelay(50, TimeUnit.MILLISECONDS)
                .setBody("hello world one"));
    dispatcher.responseForPath(
        "/bar",
        () ->
            new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBodyDelay(50, TimeUnit.MILLISECONDS)
                .setBody("hello world two"));
    dispatcher.responseForPath(
        "/baz",
        () ->
            new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBodyDelay(50, TimeUnit.MILLISECONDS)
                .setBody("hello world three"));
    mockWebServer.start();

    Downloader downloader = buildDownloader().build();
    downloader.registerStateChangeCallback(mockCallback, MoreExecutors.directExecutor());
    DownloadRequest request1 =
        downloader.newRequestBuilder(mockWebServer.url("/foo").uri(), destination1).build();
    DownloadRequest request2 =
        downloader.newRequestBuilder(mockWebServer.url("/bar").uri(), destination2).build();
    DownloadRequest request3 =
        downloader.newRequestBuilder(mockWebServer.url("/baz").uri(), destination3).build();
    FluentFuture<DownloadResult> resultFuture1 = downloader.execute(request1);
    FluentFuture<DownloadResult> resultFuture2 = downloader.execute(request2);
    FluentFuture<DownloadResult> resultFuture3 = downloader.execute(request3);

    verify(mockCallback, atLeastOnce()).onStateChange(State.create(1, 2, 0));

    // Cancel the first request.
    assertThat(resultFuture1.cancel(true)).isTrue();
    assertThrows(CancellationException.class, resultFuture1::get);

    // Flush through the I/O executor to make sure internal state has settled.
    ioExecutor.submit(() -> {}).get();

    // Cancellation is racy. After it happens, there is either one request in flight and one still
    // queued, or both still queued. The third request did not ignore the limit, see b/148559122.
    verify(mockCallback, atLeastOnce())
        .onStateChange(or(eq(State.create(1, 1, 0)), eq(State.create(0, 2, 0))));

    DownloadResult result2 = resultFuture2.get(10, SECONDS);
    DownloadResult result3 = resultFuture3.get(10, SECONDS);
    assertThat(result2.bytesWritten()).isEqualTo(15L); // == "hello world two".length
    assertThat(result3.bytesWritten()).isEqualTo(17L); // == "hello world three".length
    assertThat(Files.asCharSource(destinationFile2, UTF_8).read()).isEqualTo("hello world two");
    assertThat(Files.asCharSource(destinationFile3, UTF_8).read()).isEqualTo("hello world three");
    assertThat(mockWebServer.getRequestCount()).isAnyOf(2, 3);

    // Flush through the I/O executor to make sure internal state has settled.
    ioExecutor.submit(() -> {}).get();

    verify(mockCallback, atLeastOnce()).onStateChange(State.create(0, 0, 0));
  }

  @Test
  public void closingFutureDoesntLeak() throws Exception {
    TestLogHandler logHandler = new TestLogHandler();
    Logger.getLogger(ClosingFuture.class.getName()).addHandler(logHandler);

    File destinationFile1 = temporaryFolder.newFile();
    File destinationFile2 = temporaryFolder.newFile();
    File metadataFile1 =
        new File(destinationFile1.getParent(), destinationFile1.getName() + ".meta");
    File metadataFile2 =
        new File(destinationFile2.getParent(), destinationFile2.getName() + ".meta");
    SimpleFileDownloadDestination destination1 =
        new SimpleFileDownloadDestination(destinationFile1, metadataFile1);
    SimpleFileDownloadDestination destination2 =
        new SimpleFileDownloadDestination(destinationFile2, metadataFile2);

    dispatcher.responseForPath(
        "/foo1",
        () ->
            new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBodyDelay(50, TimeUnit.MILLISECONDS)
                .setBody("hello world one"));
    dispatcher.responseForPath(
        "/foo2",
        () ->
            new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBodyDelay(50, TimeUnit.MILLISECONDS)
                .setBody("hello world two"));
    mockWebServer.start();

    ManualConnectivity connectivityHandler = new ManualConnectivity();

    Downloader downloader = buildDownloader().withConnectivityHandler(connectivityHandler).build();

    DownloadRequest request1 =
        downloader.newRequestBuilder(mockWebServer.url("/foo1").uri(), destination1).build();
    URI request2Uri = mockWebServer.url("/foo2").uri();
    DownloadRequest request2 = downloader.newRequestBuilder(request2Uri, destination2).build();

    FluentFuture<DownloadResult> resultFuture1 = downloader.execute(request1);
    connectivityHandler.setConnectivitySatisfied();
    connectivityHandler.succeed();

    FluentFuture<DownloadResult> resultFuture2 = downloader.execute(request2);

    resultFuture1.get();
    resultFuture2.get();
    System.gc();

    assertAbout(streams())
        .that(logHandler.logRecords.stream().map(LogRecord::getLevel))
        .doesNotContain(Level.SEVERE);
  }

  private static class AlwaysConnected implements ConnectivityHandler {
    @Override
    public ListenableFuture<Void> checkConnectivity(DownloadConstraints constraints) {
      return Futures.immediateFuture(null);
    }
  }

  private static class ManualConnectivity implements ConnectivityHandler {
    private final List<SettableFuture<Void>> futures = new ArrayList<>();
    private boolean connectivitySatisfied = false;

    public void succeed() {
      for (SettableFuture<Void> future : futures) {
        future.set(null);
      }
      futures.clear();
    }

    public void fail(Throwable throwable) {
      for (SettableFuture<Void> future : futures) {
        future.setException(throwable);
      }
      futures.clear();
    }

    void setConnectivitySatisfied() {
      this.connectivitySatisfied = true;
    }

    @Override
    public ListenableFuture<Void> checkConnectivity(DownloadConstraints constraints) {
      if (connectivitySatisfied) {
        return Futures.immediateFuture(null);
      }
      SettableFuture<Void> future = SettableFuture.create();
      futures.add(future);
      return future;
    }
  }

  private static class TestLogHandler extends Handler {
    List<LogRecord> logRecords = new ArrayList<>();

    @Override
    public void close() {}

    @Override
    public void flush() {}

    @Override
    public void publish(LogRecord record) {
      System.err.println("Handling logrecord: " + record.getMessage());
      logRecords.add(record);
    }
  }

  private static class DispatcherImpl extends Dispatcher {
    private Map<String, Supplier<MockResponse>> responseMap = new HashMap<>();

    public void responseForPath(String path, Supplier<MockResponse> responseSupplier) {
      responseMap.put(path, responseSupplier);
    }

    @Override
    public MockResponse dispatch(RecordedRequest recordedRequest) throws InterruptedException {
      String path = recordedRequest.getPath();
      if (path == null) {
        return defaultResponse();
      }
      Supplier<MockResponse> responseSupplier = responseMap.get(recordedRequest.getPath());
      if (responseSupplier == null) {
        return defaultResponse();
      }
      return responseSupplier.get();
    }

    @Override
    public void shutdown() {
      responseMap.clear();
      super.shutdown();
    }

    private MockResponse defaultResponse() {
      return new MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_FOUND);
    }
  }
}
