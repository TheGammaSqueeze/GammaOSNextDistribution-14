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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Predicates.instanceOf;
import static com.google.common.base.Throwables.getCausalChain;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.ClosingFuture;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.channels.WritableByteChannel;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * In-process download system. Provides a light-weight mechanism to download resources over the
 * network. Downloader includes the following features:
 *
 * <ul>
 *   <li>Configurable choice of network stack, with several defaults available out of the box.
 *   <li>Fully asynchronous behavior, allowing downloads to progress and complete without blocking
 *       (assuming the underlying network stack can avoid blocking).
 *   <li>Support for HTTP range requests, allowing partial downloads to resume mid-way through, and
 *       avoiding redownloading of fully-downloaded requests.
 *   <li>Detection of network interruptions, and a configurable way to retry requests that have
 *       failed after losing connectivity.
 * </ul>
 *
 * <p>Note that because this library performs downloads in-process, it is subject to having
 * downloads stall or abort when the app is suspended or killed. Download requests only live in
 * memory, and thus are lost when the process ends. Library users should persist the set of
 * downloads to be executed in persistent storage, such as a SQLite database, and run the downloads
 * in the context of a persistent operation (either a foreground service or via Android's {@link
 * android.app.job.JobScheduler} mechanism).
 *
 * <p>This is intended as a functional (but not drop-in) replacement for Android's {@link
 * android.app.DownloadManager}, which has a number of issues:
 *
 * <ul>
 *   <li>It relies on the network stack built into Android, and thus cannot be patched to receive
 *       updates. For older versions of Android, that means the DownloadManager is vulnerable to
 *       issues in the network stack, such as b/18432707, which can result in MITM attacks.
 *   <li>When downloading to external storage on older versions of Android (via {@link
 *       android.app.DownloadManager.Request#setDestinationInExternalFilesDir} or {@link
 *       android.app.DownloadManager.Request#setDestinationInExternalPublicDir}), downloads may
 *       exceed the maximum size of the download directory (see b/22605830).
 *   <li>Downloads may pause and never resume again without external interference (b/18110151)
 *   <li>The DownloadManager may lose track of some files (b/18265497)
 * </ul>
 *
 * <p>This library mitigates these issues by performing downloads in-process over the app-provided
 * network stack, thus ensuring that an up-to-date network stack is used, and handing off storage
 * management directly to the app without any additional restrictions.
 */
public class Downloader {
  @VisibleForTesting static final int HTTP_RANGE_NOT_SATISFIABLE = 416;
  @VisibleForTesting static final int HTTP_PARTIAL_CONTENT = 206;

  private static final ImmutableSet<String> SCHEMES_REQUIRING_CONNECTIVITY =
      ImmutableSet.of("http", "https");

  private static final Pattern CONTENT_RANGE_HEADER_PATTERN =
      Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+|\\*)");

  @GuardedBy("SIMPLE_DATE_FORMAT_LOCK")
  private static final SimpleDateFormat RFC_1123_FORMATTER;

  private static final Object SIMPLE_DATE_FORMAT_LOCK = new Object();

  static {
    synchronized (SIMPLE_DATE_FORMAT_LOCK) {
      RFC_1123_FORMATTER = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
      RFC_1123_FORMATTER.setTimeZone(TimeZone.getTimeZone("UTC"));
    }
  }

  /** Builder for configuring and constructing an instance of the Downloader. */
  public static class Builder {
    private final Map<String, UrlEngine> urlEngineMap = new HashMap<>();
    private Executor ioExecutor;
    private DownloaderLogger logger;
    private ConnectivityHandler connectivityHandler;
    private int maxConcurrentDownloads = 3;

    /**
     * Creates a downloader builder.
     *
     * <p>Note that all parameters are required except for {@link #withMaxConcurrentDownloads}.
     */
    public Builder() {}

    /**
     * Specifies the executor to use internally for I/O.
     *
     * <p>I/O operations can block so don't use a direct executor or one that runs on the main
     * thread.
     */
    public Builder withIOExecutor(Executor ioExecutor) {
      this.ioExecutor = ioExecutor;
      return this;
    }

    /**
     * Sets the {@link ConnectivityHandler} to use in order to determine if connectivity is
     * sufficient, and if not, how to handle it.
     */
    public Builder withConnectivityHandler(ConnectivityHandler connectivityHandler) {
      this.connectivityHandler = connectivityHandler;
      return this;
    }

    /**
     * Limits the number of downloads in flight at a time. If a download request arrives that would
     * exceed this limit, it will be queued until one already in flight completes. Beware that a
     * download that is waiting for connectivity requirements is still considered to be in flight,
     * so it is possible to saturate the downloader with requests waiting on connectivity
     * requirements if the number of concurrent downloads isn't set high enough.
     *
     * <p>Note that other factors might restrict download concurrency even further, for instance the
     * number of threads on the I/O executor when using a blocking engine.
     */
    public Builder withMaxConcurrentDownloads(int maxConcurrentDownloads) {
      checkArgument(maxConcurrentDownloads > 0);
      this.maxConcurrentDownloads = maxConcurrentDownloads;
      return this;
    }

    /**
     * Adds an {@link UrlEngine} to handle network requests for the given URL scheme. Note that the
     * engine must support the provided scheme, and only one engine may ever be registered for a
     * specific URL scheme. An {@link IllegalArgumentException} will be thrown if the engine doesn't
     * support the scheme or if an engine is already registered for the scheme.
     */
    public Builder addUrlEngine(String scheme, UrlEngine urlEngine) {
      checkArgument(
          urlEngine.supportedSchemes().contains(scheme),
          "Provided UrlEngine must support URL scheme: %s",
          scheme);
      checkArgument(
          !urlEngineMap.containsKey(scheme),
          "Requested scheme already has a UrlEngine registered: %s",
          scheme);
      urlEngineMap.put(scheme, urlEngine);
      return this;
    }

    /**
     * Adds an {@link UrlEngine} to handle network requests for the given URL schemes. Note that the
     * engine must support the provided schemes, and only one engine may ever be registered for a
     * specific URL scheme. An error will be thrown if the engine doesn't support the schemes or if
     * an engine is already registered for the schemes.
     */
    public Builder addUrlEngine(Iterable<String> schemes, UrlEngine urlEngine) {
      for (String scheme : schemes) {
        addUrlEngine(scheme, urlEngine);
      }
      return this;
    }

    /** Sets the {@link DownloaderLogger} to use for logging in this downloader instance. */
    public Builder withLogger(DownloaderLogger logger) {
      this.logger = logger;
      return this;
    }

    public Downloader build() {
      return new Downloader(this);
    }
  }

  /**
   * Value class for capturing a state snapshot of the downloader, for use in the state callbacks
   * that can be registered via {@link #registerStateChangeCallback}.
   */
  @AutoValue
  public abstract static class State {
    /**
     * Returns the current number of downloads currently in flight, i.e. the number of downloads
     * that are concurrently executing, or attempting to make progress on their underlying network
     * stack. Note that this number should be in the range of [0, maxConcurrentDownloads), as
     * configured by {@link Builder#withMaxConcurrentDownloads}.
     */
    public abstract int getNumDownloadsInFlight();

    /**
     * Returns the number of downloads that have been requested via {@link #execute} but not yet
     * started. They may not have been started due to internal asynchronous code, due to waiting on
     * connectivity requirements, or due to the limit enforced by {@code maxConcurrentDownloads}.
     */
    public abstract int getNumQueuedDownloads();

    /**
     * Returns the current number of downloads that are waiting for sufficient connectivity
     * conditions to be met. These downloads will start running once the device network conditions
     * change (e.g. connects to WiFi), if there is enough space as determined by {@code
     * maxConcurrentDownloads}.
     *
     * <p>Note that this number should be in the range of [0, numQueuedDownloads], as a download
     * pending connectivity is necessarily queued. However, a download may also be queued due to
     * {@code maxConcurrentDownloads}.
     */
    public abstract int getNumDownloadsPendingConnectivity();

    /** Creates an instance of the state object. Internal-only. */
    @VisibleForTesting
    public static State create(
        int numDownloadsInFlight, int numQueuedDownloads, int numDownloadsPendingConnectivity) {
      return new AutoValue_Downloader_State(
          numDownloadsInFlight, numQueuedDownloads, numDownloadsPendingConnectivity);
    }
  }

  /**
   * Functional callback interface for observing changes to Downloader {@link State}. Used with
   * {@link #registerStateChangeCallback} and {@link #unregisterStateChangeCallback}.
   *
   * <p>Note that the callbacks could just use {@code java.util.function.Consumer} in contexts that
   * support the Java 8 SDK, and {@code com.google.common.base.Receiver} in contexts that don't but
   * have access to Guava APIs that aren't made public.
   */
  public interface StateChangeCallback {
    void onStateChange(State state);
  }

  private final ImmutableMap<String, UrlEngine> urlEngineMap;
  private final Executor ioExecutor;
  private final DownloaderLogger logger;
  private final ConnectivityHandler connectivityHandler;
  private final int maxConcurrentDownloads;

  @GuardedBy("lock")
  private final IdentityHashMap<StateChangeCallback, Executor> stateCallbackMap =
      new IdentityHashMap<>();

  // TODO: Consider using a PriorityQueue here instead, so that queued downloads can
  // retain the order in the queue as they get added/removed due to waiting on connectivity.
  @GuardedBy("lock")
  private final Queue<QueuedDownload> queuedDownloads = new ArrayDeque<>();

  @GuardedBy("lock")
  private final List<FluentFuture<DownloadResult>> unresolvedFutures = new ArrayList<>();

  private final Object lock = new Object();

  @GuardedBy("lock")
  private int numDownloadsInFlight = 0;

  @GuardedBy("lock")
  private int numDownloadsPendingConnectivity = 0;

  private Downloader(Builder builder) {
    ImmutableMap<String, UrlEngine> urlEngineMap = ImmutableMap.copyOf(builder.urlEngineMap);
    checkArgument(!urlEngineMap.isEmpty(), "Must have at least one UrlEngine");
    checkArgument(builder.ioExecutor != null, "Must set a callback executor");
    checkArgument(builder.logger != null, "Must set a logger");
    checkArgument(builder.connectivityHandler != null, "Must set a connectivity handler");

    this.urlEngineMap = urlEngineMap;
    this.ioExecutor = builder.ioExecutor;
    this.logger = builder.logger;
    this.connectivityHandler = builder.connectivityHandler;
    this.maxConcurrentDownloads = builder.maxConcurrentDownloads;
  }

  /**
   * Creates a new {@link DownloadRequest.Builder} for the given {@link URI} and {@link
   * DownloadDestination}. The builder may be used to further customize the request. To execute the
   * request, pass the built request to {@link #execute}.
   */
  @CheckReturnValue
  public DownloadRequest.Builder newRequestBuilder(URI uri, DownloadDestination destination) {
    return DownloadRequest.newBuilder()
        .setDestination(destination)
        .setUri(uri)
        .setDownloadConstraints(DownloadConstraints.NETWORK_CONNECTED);
  }

  /**
   * Executes the provided request. The request will be handled by the underlying {@link UrlEngine}
   * and the result is streamed to the {@link DownloadDestination} created for the request. The
   * download result is provided asynchronously as a {@link FluentFuture} that resolves when the
   * download is complete.
   *
   * <p>The download can be cancelled by calling {@link FluentFuture#cancel} on the future instance
   * returned by this method. Cancellation is best-effort and does not guarantee that the download
   * will stop immediately, as it is impossible to stop a thread that is in the middle of reading
   * bytes off the network.
   *
   * <p>Note that this method is not idempotent! The downloader does not attempt to merge/de-dupe
   * incoming requests, even if the same exact request is executed twice. The calling code needs to
   * be careful to manage its downloads in such a way that duplicated downloads don't occur.
   *
   * <p>TODO: Document what types of exceptions can be set on the returned future, and how clients
   * are expected to handle them.
   */
  @CheckReturnValue
  public FluentFuture<DownloadResult> execute(DownloadRequest request) {
    FluentFuture<DownloadResult> resultFuture;

    synchronized (lock) {
      ClosingFuture<DownloadResult> closingFuture = enqueueRequest(request);
      closingFuture.statusFuture().addListener(() -> onDownloadComplete(request), ioExecutor);

      resultFuture = closingFuture.finishToFuture();
      unresolvedFutures.add(resultFuture);
      resultFuture.addListener(
          () -> {
            synchronized (lock) {
              unresolvedFutures.remove(resultFuture);
            }
          },
          MoreExecutors.directExecutor());
    }

    logger.logFine("New request enqueued, running queue: %s", request.uri());
    maybeRunQueuedDownloads();
    return resultFuture;
  }

  /**
   * Cancels <strong>all</strong> ongoing downloads. This will result in all unresolved {@link
   * FluentFuture} instances returned by {@link #execute} to be cancelled immediately and have their
   * error callbacks invoked.
   *
   * <p>Because implementations of {@link FluentFuture} allow callbacks to be garbage collected
   * after the future is resolved, calling {@code cancelAll} is an effective way to avoid having the
   * Downloader leak memory after it is logically no longer needed, e.g. if it is only used from an
   * Android {@link android.app.Activity}, and that Activity is destroyed.
   *
   * <p>However, in general the Downloader should be run from a process-level context, e.g. in an
   * Android {@link android.app.Service}, so that the downloader doesn't implicitly hold on to
   * UI-scoped objects.
   */
  public void cancelAll() {
    List<FluentFuture<DownloadResult>> unresolvedFuturesCopy;

    synchronized (lock) {
      // Copy the set of unresolved futures to a local variable to avoid hitting
      // ConcurrentModificationExceptions, which could happen since canceling the future may
      // trigger the future callback in execute() that removes the future from the set of
      // unresolved futures.
      unresolvedFuturesCopy = new ArrayList<>(unresolvedFutures);
      unresolvedFutures.clear();
    }

    for (FluentFuture<DownloadResult> unresolvedFuture : unresolvedFuturesCopy) {
      unresolvedFuture.cancel(true);
    }
  }

  /**
   * Registers an {@link StateChangeCallback} with this downloader instance, to be run when various
   * state changes occur. The callback will be executed on the provided {@link Executor}. Use {@link
   * #unregisterStateChangeCallback} to unregister a previously registered callback. The callback is
   * invoked when the following state transitions occur:
   *
   * <ul>
   *   <li>A download was requested via a call to {@link #execute}
   *   <li>A download completed
   *   <li>A download started waiting for its connectivity constraints to be satisfied
   *   <li>A download stopped waiting for its connectivity constraints to be satisfied
   * </ul>
   *
   * <p>A newly registered callback instance will only called for state changes that are triggered
   * after the registration; there is no mechanism to replay previous state changes on the callback.
   *
   * <p>Registering the same callback multiple times has no effect, except that it will overwrite
   * the {@link Executor} that was used in a previous registration call.
   *
   * <p>Invocation of the callbacks will be internally serialized to avoid concurrent invocations of
   * the callback with possibly conflicting state. A new invocation of the callback will not start
   * running until the previous one has completed. If multiple callbacks are being registered that
   * must be synchronized with each other, then the caller must take care to coordinate this
   * externally, such as locking in the callbacks or using an executor that guarantees
   * serialization, such as {@link MoreExecutors#newSequentialExecutor}.
   *
   * <p>Warning: Do not use {@link MoreExecutors#directExecutor} or similar executor mechanisms, as
   * doing so can easily lead to a deadlock, since the internal downloader lock is held while
   * scheduling the callbacks on the provided executor.
   */
  public void registerStateChangeCallback(StateChangeCallback callback, Executor executor) {
    synchronized (lock) {
      stateCallbackMap.put(callback, MoreExecutors.newSequentialExecutor(executor));
    }
  }

  /**
   * Unregisters a previously registered {@link StateChangeCallback} with this downloader instance.
   *
   * <p>Attempting to unregister a callback that was never registered is a no-op.
   */
  public void unregisterStateChangeCallback(StateChangeCallback callback) {
    synchronized (lock) {
      stateCallbackMap.remove(callback);
    }
  }

  private void onDownloadComplete(DownloadRequest request) {
    synchronized (lock) {
      // The number of downloads should be well-balanced and this case should never
      // trigger, so this is just a defensive check.
      checkState(
          numDownloadsInFlight >= 0, "Encountered < 0 downloads in flight, this shouldn't happen");
    }

    logger.logFine("Download complete, running queued downloads: %s", request.uri());
    maybeRunQueuedDownloads();
  }

  private void maybeRunQueuedDownloads() {
    // Loop until we run out of download slots or out of queued downloads. It should be impossible
    // for this to loop forever. Also, note that the synchronized block is inside the loop, since
    // the outer loop conditions don't touch shared mutable state.
    while (true) {
      synchronized (lock) {
        if (numDownloadsInFlight >= maxConcurrentDownloads) {
          logger.logInfo("Exceeded max concurrent downloads, not running another queued request");
          return;
        }

        QueuedDownload queuedDownload = queuedDownloads.poll();
        if (queuedDownload == null) {
          return;
        }

        DownloadRequest request = queuedDownload.request();
        ListenableFuture<Void> connectivityFuture = checkConnectivity(request);
        if (connectivityFuture.isDone()) {
          logger.logFine("Connectivity satisfied; running request. uri=%s", request.uri());
          numDownloadsInFlight++;
          ListenableFuture<?> statusFuture = queuedDownload.resultFuture().statusFuture();
          statusFuture.addListener(
              () -> {
                synchronized (lock) {
                  numDownloadsInFlight--;

                  // One less download in flight, let the state change listeners know.
                  runStateCallbacks();
                }

                // A download slot was just freed up, run queued downloads again.
                logger.logFine(
                    "Queued download completed, running queued downloads: %s", request.uri());
                maybeRunQueuedDownloads();
              },
              ioExecutor);

          // A new download is about to be in flight, let state callbacks know about the
          // state change.
          runStateCallbacks();
          queuedDownload.task().run();
        } else {
          logger.logInfo("Waiting on connectivity for request: uri=%s", request.uri());
          handlePendingConnectivity(connectivityFuture, queuedDownload);
        }
      }
    }
  }

  @GuardedBy("lock")
  private void handlePendingConnectivity(
      ListenableFuture<Void> connectivityFuture, QueuedDownload queuedDownload) {
    // Keep track of the number of requests waiting.
    numDownloadsPendingConnectivity++;
    connectivityFuture.addListener(
        () -> {
          synchronized (lock) {
            numDownloadsPendingConnectivity--;

            // Let the listeners know we are no longer waiting.
            runStateCallbacks();
          }
        },
        MoreExecutors.directExecutor());

    // Let the listeners know we're waiting.
    runStateCallbacks();

    Futures.addCallback(
        connectivityFuture,
        new FutureCallback<Void>() {
          @Override
          public void onSuccess(Void result1) {
            logger.logInfo("Connectivity changed, running queued requests");
            requeue(queuedDownload);
          }

          @Override
          public void onFailure(Throwable t) {
            if (t instanceof TimeoutException) {
              logger.logInfo("Timed out waiting for connectivity change");
              requeue(queuedDownload);
            } else if (t instanceof CancellationException) {
              logger.logFine("Connectivity future cancelled, running queued downloads");
              maybeRunQueuedDownloads();
            } else {
              logger.logError(t, "Error observing connectivity changes");
              cancelAll();
            }
          }
        },
        ioExecutor);

    // Run state callbacks and cancel the connectivity future when the result task resolves.
    // It doesn't matter if it succeeded or failed, either way it means we no longer need to wait
    // for connectivity.
    queuedDownload
        .task()
        .addListener(
            () -> {
              synchronized (lock) {
                logger.logInfo("Queued task completed, cancelling connectivity check");
                runStateCallbacks();
                connectivityFuture.cancel(false);
              }
            },
            MoreExecutors.directExecutor());
  }

  private void requeue(QueuedDownload queuedDownload) {
    synchronized (lock) {
      addToQueue(queuedDownload);
    }

    logger.logInfo(
        "Requeuing download after connectivity change: %s", queuedDownload.request().uri());
    maybeRunQueuedDownloads();
  }

  private ClosingFuture<DownloadResult> enqueueRequest(DownloadRequest request) {
    synchronized (lock) {
      ListenableFutureTask<Void> task = ListenableFutureTask.create(() -> null);
      // When the task runs (i.e. is taken off the queue and is explicitly run), all pre-flight
      // checks should have been made, so at that point the request is send to the underlying
      // network stack for execution.
      ClosingFuture<DownloadResult> resultFuture =
          ClosingFuture.from(task)
              .transformAsync((closer, result) -> runRequest(request), ioExecutor);
      addToQueue(QueuedDownload.create(request, task, resultFuture));
      return resultFuture;
    }
  }

  @GuardedBy("lock")
  private void addToQueue(QueuedDownload queuedDownload) {
    queuedDownloads.add(queuedDownload);

    // Make sure that when the task completes, the queued download is definitely removed
    // from the queue. This is necessary to be robust in the face of cancellation, as
    // canceled tasks may not get removed from the queue otherwise.
    queuedDownload
        .task()
        .addListener(
            () -> {
              synchronized (lock) {
                if (queuedDownloads.remove(queuedDownload)) {
                  // If the queued download was actually removed, update the state callbacks
                  // to reflect the state change.
                  runStateCallbacks();
                }
              }
            },
            MoreExecutors.directExecutor());

    // Now that a new request is on the queue, run the state callbacks.
    runStateCallbacks();
  }

  private ClosingFuture<DownloadResult> runRequest(DownloadRequest request) throws IOException {
    URI uri = request.uri();
    UrlEngine urlEngine = checkNotNull(urlEngineMap.get(uri.getScheme()));
    UrlRequest.Builder urlRequestBuilder = urlEngine.createRequest(uri.toString());
    for (Map.Entry<String, String> entry : request.headers().entries()) {
      urlRequestBuilder.addHeader(entry.getKey(), entry.getValue());
    }

    long numExistingBytes = request.destination().numExistingBytes();
    if (numExistingBytes > 0) {
      logger.logInfo(
          "Existing bytes found. numExistingBytes=%d, uri=%s", numExistingBytes, request.uri());
      urlRequestBuilder.addHeader(HttpHeaders.RANGE, "bytes=" + numExistingBytes + "-");

      DownloadMetadata destinationMetadata = request.destination().readMetadata();
      String contentTag = destinationMetadata.getContentTag();
      long lastModifiedTimeSeconds = destinationMetadata.getLastModifiedTimeSeconds();
      if (!contentTag.isEmpty()) {
        urlRequestBuilder.addHeader(HttpHeaders.IF_RANGE, contentTag);
      } else if (lastModifiedTimeSeconds > 0) {
        urlRequestBuilder.addHeader(
            HttpHeaders.IF_RANGE, formatRfc1123Date(lastModifiedTimeSeconds));
      } else {
        // TODO: This should probably just clear the destination and remove the Range
        // header so there's no chance of data corruption. Leaving this as-is for now to
        // keep supporting range requests for offline maps.
        logger.logWarning(
            "Sending range request without If-Range header, due to missing destination "
                + "metadata. Data corruption is possible.");
      }
    }

    ListenableFuture<UrlRequest> urlRequestFuture;
    OAuthTokenProvider oAuthTokenProvider = request.oAuthTokenProvider();
    if (oAuthTokenProvider != null) {
      urlRequestFuture =
          Futures.transform(
              oAuthTokenProvider.provideOAuthToken(uri),
              authToken -> {
                if (authToken != null) {
                  urlRequestBuilder.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + authToken);
                }
                return urlRequestBuilder.build();
              },
              ioExecutor);
    } else {
      urlRequestFuture = Futures.immediateFuture(urlRequestBuilder.build());
    }

    return ClosingFuture.from(urlRequestFuture)
        .transform((closer, urlRequest) -> checkNotNull(urlRequest).send(), ioExecutor)
        .transformAsync(
            (closer, responseFuture) -> completeRequest(request, checkNotNull(responseFuture)),
            ioExecutor);
  }

  /**
   * @param request the original request that triggered this call
   * @param responseFuture the {@link UrlResponse}, provided asynchronously via a {@link
   *     ListenableFuture}
   */
  private ClosingFuture<DownloadResult> completeRequest(
      DownloadRequest request, ListenableFuture<UrlResponse> responseFuture) {
    return ClosingFuture.from(responseFuture)
        .transformAsync(
            (closer, urlResponse) -> {
              checkNotNull(urlResponse);
              // We want to close the response regardless of whether we succeed or fail.
              closer.eventuallyClose(urlResponse, ioExecutor);
              logger.logFine(
                  "Got URL response, starting to read response body. uri=%s", request.uri());
              DownloadDestination destination = request.destination();
              if (request.headers().containsKey(HttpHeaders.RANGE)
                  && checkNotNull(urlResponse).getResponseCode() != HTTP_PARTIAL_CONTENT) {
                logger.logFine("Clearing %s as our range request wasn't honored", destination);
                destination.clear();
              }
              WritableByteChannel byteChannel =
                  destination.openByteChannel(
                      parseResponseStartOffset(urlResponse), parseResponseMetadata(urlResponse));
              closer.eventuallyClose(byteChannel, ioExecutor);
              return ClosingFuture.from(checkNotNull(urlResponse).readResponseBody(byteChannel));
            },
            ioExecutor)
        .catching(
            RequestException.class,
            (closer, requestException) -> {
              if (checkNotNull(requestException).getErrorDetails().getHttpStatusCode()
                  == HTTP_RANGE_NOT_SATISFIABLE) {
                // This is a bit of a special edge case. Encountering this error means the server
                // rejected our HTTP range request because it was outside the range of available
                // bytes. This may well mean that the request was malformed (e.g. data on disk was
                // corrupted and the existing file size ended up larger than what exists on the
                // server). But the more common cause for this error is that the entire file was
                // in fact already downloaded, so the requested range would cover 0 bytes, which
                // the server interprets as not satisfiable. To mitigate that case we simply return
                // a success state here by indicating 0 bytes were downloaded.

                // This isn't exactly ideal, as it means that a potential class of errors will
                // go unnoticed. A better solution might find a way to distinguish between the
                // common, file-already-downloaded case and the file corrupted case. This solution
                // is put in place mainly to retain parity with the older downloader implementation.
                return 0L;
              } else {
                throw new DownloadException(requestException);
              }
            },
            ioExecutor)
        .transform(
            (closer, bytesWritten) -> {
              logger.logFine(
                  "Response body written. bytesWritten=%d, uri=%s",
                  checkNotNull(bytesWritten), request.uri());
              return DownloadResult.create(checkNotNull(bytesWritten));
            },
            ioExecutor)
        .catchingAsync(
            Exception.class,
            (closer, exception) -> {
              ClosingFuture<DownloadResult> result;
              synchronized (lock) {
                logger.logWarning(
                    exception, "Error reading download result. uri=%s", request.uri());
                RequestException requestException = getRequestException(exception);
                if (requestException != null
                    && requestException.getErrorDetails().isRetryableAsIs()) {
                  // Retry the request by just re-enqueueing it. Note that we also need to
                  // call maybeRunQueuedRequest to keep the queue moving. Also, in this particular
                  // case we need to decrement the in-flight downloads count, as we are taking a
                  // previously in-flight download and putting it back in the queue without
                  // resolving the underlying result future.
                  numDownloadsInFlight--;
                  result = enqueueRequest(request);
                } else {
                  throw new DownloadException(exception);
                }
              }

              logger.logInfo("Running queued downloads after handling request exception");
              maybeRunQueuedDownloads();
              return result;
            },
            ioExecutor);
  }

  @GuardedBy("lock")
  private ListenableFuture<Void> checkConnectivity(DownloadRequest request) {
    if (!SCHEMES_REQUIRING_CONNECTIVITY.contains(request.uri().getScheme())) {
      return Futures.immediateVoidFuture();
    }

    return connectivityHandler.checkConnectivity(request.downloadConstraints());
  }

  @GuardedBy("lock")
  private void runStateCallbacks() {
    State state =
        State.create(numDownloadsInFlight, queuedDownloads.size(), numDownloadsPendingConnectivity);
    for (Map.Entry<StateChangeCallback, Executor> callbackEntry : stateCallbackMap.entrySet()) {
      callbackEntry.getValue().execute(() -> callbackEntry.getKey().onStateChange(state));
    }
  }

  private static String formatRfc1123Date(long unixTimeSeconds) {
    synchronized (SIMPLE_DATE_FORMAT_LOCK) {
      return RFC_1123_FORMATTER.format(new Date(SECONDS.toMillis(unixTimeSeconds)));
    }
  }

  private static long parseResponseStartOffset(UrlResponse response) throws DownloadException {
    if (response.getResponseCode() != HttpURLConnection.HTTP_PARTIAL) {
      return 0;
    }

    List<String> contentRangeHeaders = response.getResponseHeaders().get(HttpHeaders.CONTENT_RANGE);

    checkDownloadState(
        contentRangeHeaders != null && !contentRangeHeaders.isEmpty(),
        "Host returned 206/PARTIAL response code but didn't provide a "
            + "'Content-Range' response header");
    String contentRangeHeader = checkNotNull(contentRangeHeaders).get(0);
    Matcher matcher = CONTENT_RANGE_HEADER_PATTERN.matcher(contentRangeHeader);
    checkDownloadState(
        matcher.matches() && matcher.groupCount() > 0,
        "Content-Range response header didn't match expected pattern. " + "Was '%s', expected '%s'",
        contentRangeHeader,
        CONTENT_RANGE_HEADER_PATTERN.pattern());
    return Long.parseLong(checkNotNull(matcher.group(1)));
  }

  private static DownloadMetadata parseResponseMetadata(UrlResponse response)
      throws DownloadException {
    String contentTag = parseResponseContentTag(response);
    long lastModifiedTimeSeconds = parseResponseModifiedTime(response);
    return DownloadMetadata.create(contentTag, lastModifiedTimeSeconds);
  }

  private static long parseResponseModifiedTime(UrlResponse response) throws DownloadException {
    List<String> lastModifiedHeaders = response.getResponseHeaders().get(HttpHeaders.LAST_MODIFIED);
    if (lastModifiedHeaders == null || lastModifiedHeaders.isEmpty()) {
      return 0L;
    }

    String lastModifiedHeader = lastModifiedHeaders.get(0);
    Date date;

    try {
      synchronized (SIMPLE_DATE_FORMAT_LOCK) {
        date = RFC_1123_FORMATTER.parse(lastModifiedHeader);
      }
      if (date == null) {
        throw new DownloadException("Invalid Last-Modified header: " + lastModifiedHeader);
      }
    } catch (ParseException e) {
      throw new DownloadException("Invalid Last-Modified header: " + lastModifiedHeader, e);
    }

    return MILLISECONDS.toSeconds(date.getTime());
  }

  private static String parseResponseContentTag(UrlResponse response) {
    List<String> contentTagHeaders = response.getResponseHeaders().get(HttpHeaders.ETAG);
    if (contentTagHeaders == null || contentTagHeaders.isEmpty()) {
      return "";
    }

    return contentTagHeaders.get(0);
  }

  @AutoValue
  abstract static class QueuedDownload {
    abstract DownloadRequest request();

    abstract ListenableFutureTask<?> task();

    abstract ClosingFuture<DownloadResult> resultFuture();

    static QueuedDownload create(
        DownloadRequest request,
        ListenableFutureTask<?> task,
        ClosingFuture<DownloadResult> resultFuture) {
      return new AutoValue_Downloader_QueuedDownload(request, task, resultFuture);
    }
  }

  @VisibleForTesting
  @Nullable
  static RequestException getRequestException(@Nullable Throwable throwable) {
    if (throwable == null) {
      return null;
    } else {
      return (RequestException)
          Iterables.find(
              getCausalChain(throwable),
              instanceOf(RequestException.class),
              /* defaultValue= */ null);
    }
  }

  @FormatMethod
  private static void checkDownloadState(
      boolean state, @FormatString String message, Object... args) throws DownloadException {
    if (!state) {
      throw new DownloadException(String.format(message, args));
    }
  }
}
