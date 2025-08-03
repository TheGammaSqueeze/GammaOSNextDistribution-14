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

package com.google.android.downloader.example;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import com.google.android.downloader.AndroidConnectivityHandler;
import com.google.android.downloader.CronetUrlEngine;
import com.google.android.downloader.DownloadRequest;
import com.google.android.downloader.DownloadResult;
import com.google.android.downloader.Downloader;
import com.google.android.downloader.FloggerDownloaderLogger;
import com.google.android.downloader.OkHttp2UrlEngine;
import com.google.android.downloader.OkHttp3UrlEngine;
import com.google.android.downloader.PlatformUrlEngine;
import com.google.android.downloader.ProtoFileDownloadDestination;
import com.google.android.downloader.UrlEngine;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.squareup.okhttp.OkHttpClient;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.chromium.net.CronetEngine;

/** {@link android.app.Activity} instance for the downloader test app. */
public class DownloaderTestActivity extends AppCompatActivity {
  private static final ImmutableSet<String> URLS =
      ImmutableSet.of(
          "https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb",
          "https://dl.google.com/dl/googletv-eureka/beta-channel/"
              + "ota.103344.tz_beta-channel.joplin-b1.5f2ce3bb4bf4d25ebfdcee97fcce42e32419b06f.zip",
          "https://ssl.gstatic.com/allo/stickers/pack-8/v1/xxxhdpi/all.zip",
          "https://ssl.gstatic.com/playatoms/apk_dna/testdata/20171026/shared_libs.zip");

  @Nullable private Downloader downloader = null;
  private ListeningExecutorService transportExecutorService;
  private ExecutorService callbackExecutor;
  private Executor uiExecutor;
  private ScheduledExecutorService scheduledExecutorService;
  private File targetDirectory;
  private TextView textView;
  private Spinner networkSelector;
  private final Map<String, UrlEngine> connectionFactoryMap = new HashMap<>();

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.test_app_main_layout);

    targetDirectory = getFilesDir();
    transportExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(5));
    callbackExecutor = Executors.newSingleThreadExecutor();
    scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    connectionFactoryMap.put("platform", new PlatformUrlEngine(transportExecutorService, 500, 500));

    okhttp3.OkHttpClient okHttp3Client =
        new okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, SECONDS)
            .readTimeout(30, SECONDS)
            .writeTimeout(30, SECONDS)
            .build();
    connectionFactoryMap.put(
        "okhttp3", new OkHttp3UrlEngine(okHttp3Client, transportExecutorService));

    OkHttpClient okHttp2Client = new OkHttpClient();
    okHttp2Client.setConnectTimeout(30, SECONDS);
    okHttp2Client.setReadTimeout(30, SECONDS);
    okHttp2Client.setWriteTimeout(30, SECONDS);
    connectionFactoryMap.put(
        "okhttp2", new OkHttp2UrlEngine(okHttp2Client, transportExecutorService));

    CronetEngine cronetEngine = new CronetEngine.Builder(getApplicationContext()).build();
    connectionFactoryMap.put("cronet", new CronetUrlEngine(cronetEngine, transportExecutorService));

    uiExecutor = this::runOnUiThread;
  }

  private Downloader.Builder createDownloaderBuilder() {
    return new Downloader.Builder()
        .withIOExecutor(callbackExecutor)
        .withLogger(new FloggerDownloaderLogger())
        .withConnectivityHandler(
            new AndroidConnectivityHandler(
                /* context= */ this, scheduledExecutorService, /* timeoutMillis=*/ 10000L));
  }

  @Override
  protected void onStart() {
    super.onStart();

    Button startButton = findViewById(R.id.start_button);
    startButton.setOnClickListener(view -> startDownloads());

    Button cancelButton = findViewById(R.id.cancel_button);
    cancelButton.setOnClickListener(view -> cancelDownloads());

    Button clearButton = findViewById(R.id.clear_button);
    clearButton.setOnClickListener(view -> clearDownloads());

    textView = findViewById(R.id.log_text);
    networkSelector = findViewById(R.id.network_select);

    ArrayAdapter<String> adapter =
        new ArrayAdapter<>(
            this,
            android.R.layout.simple_list_item_1,
            connectionFactoryMap.keySet().toArray(new String[] {}));
    networkSelector.setAdapter(adapter);
  }

  @Override
  protected void onDestroy() {
    cancelDownloads();
    transportExecutorService.shutdown();
    callbackExecutor.shutdown();
    scheduledExecutorService.shutdown();
    super.onDestroy();
  }

  private void startDownloads() {
    Object selectedItem = networkSelector.getSelectedItem();
    if (selectedItem == null) {
      appendLog("No network selected");
      return;
    }

    String networkKey = selectedItem.toString();
    Downloader downloader =
        createDownloaderBuilder()
            .addUrlEngine(ImmutableSet.of("http", "https"), connectionFactoryMap.get(networkKey))
            .build();
    this.downloader = downloader;

    downloader.registerStateChangeCallback(
        state ->
            appendLog(
                String.format(
                    Locale.ROOT,
                    "State callback, inFlight=%d, pendingConnectivity=%d, queued=%d",
                    state.getNumDownloadsInFlight(),
                    state.getNumDownloadsPendingConnectivity(),
                    state.getNumQueuedDownloads())),
        uiExecutor);

    appendLog("Downloading with network: " + networkKey);

    List<ListenableFuture<DownloadResult>> downloads = new ArrayList<>();
    for (String url : URLS) {
      appendLog("Downloading url: " + url);
      String fileName = "file" + Hashing.murmur3_128().hashString(url, UTF_8);
      URI uri = URI.create(url);
      DownloadRequest request =
          downloader
              .newRequestBuilder(
                  uri,
                  new ProtoFileDownloadDestination(
                      new File(targetDirectory, fileName),
                      new File(targetDirectory, fileName + ".metadata")))
              .build();
      FluentFuture<DownloadResult> resultFuture = downloader.execute(request);

      // !!! WARNING WARNING WARNING !!!
      // This code will leak the activity context into the downloader's innards and thus cause the
      // activity to outlive is intended lifecycle. This code should live in its own app-level class
      // that avoids references to any UI contexts (e.g. activities, fragments, views).

      // TODO: Refactor the test app to avoid this caveat.

      downloads.add(
          resultFuture
              .transform(
                  result -> {
                    appendLog("Downloaded url. Bytes written: " + result.bytesWritten());
                    return result;
                  },
                  uiExecutor)
              .catching(
                  Throwable.class,
                  throwable -> {
                    appendLog(
                        "Failed to download, uri: "
                            + request.uri()
                            + " error: "
                            + throwable.getMessage());
                    return null;
                  },
                  uiExecutor));
    }

    Futures.addCallback(
        Futures.whenAllComplete(downloads).call(() -> null, uiExecutor),
        new FutureCallback<Void>() {
          @Override
          public void onSuccess(Void result) {
            appendLog("Download success");
          }

          @Override
          public void onFailure(Throwable t) {
            appendLog("Error downloading");
          }
        },
        uiExecutor);
  }

  private void cancelDownloads() {
    Downloader downloader = this.downloader;
    if (downloader != null) {
      downloader.cancelAll();
    }
  }

  private void clearDownloads() {
    for (File file : targetDirectory.listFiles()) {
      file.delete();
    }
    textView.setText("");
  }

  private void appendLog(String msg) {
    String existingText = textView.getText().toString();
    existingText = msg + "\n" + existingText;
    textView.setText(existingText);
  }
}
