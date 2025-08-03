/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.textclassifier.downloader;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import com.android.textclassifier.common.base.TcLog;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import javax.annotation.Nullable;

// TODO(licha): Find another way to test the service. (E.g. CtsTextClassifierService.java)
/** Test Service of IModelDownloaderService. */
public final class TestModelDownloaderService extends Service {
  private static final String TAG = "TestModelDownloaderService";

  public static final String GOOD_URI = "good_uri";
  public static final String BAD_URI = "bad_uri";
  public static final long BYTES_WRITTEN = 1L;
  public static final int DOWNLOADER_LIB_ERROR_CODE = 500;
  public static final String ERROR_MSG = "not good uri";

  public enum DownloadResult {
    SUCCEEDED,
    FAILED,
    DO_NOTHING
  }

  // Obviously this does not work when considering concurrency, but probably fine for test purpose
  private static boolean boundBefore = false;
  private static boolean boundNow = false;
  private static CountDownLatch onBindInvokedLatch = new CountDownLatch(1);
  private static CountDownLatch onUnbindInvokedLatch = new CountDownLatch(1);

  private static boolean bindSucceed = false;
  private static String expectedUrl = null;
  private static DownloadResult downloadResult = DownloadResult.SUCCEEDED;
  private static byte[] fileContent = null;

  public static boolean hasEverBeenBound() {
    return boundBefore;
  }

  public static boolean isBound() {
    return boundNow;
  }

  public static CountDownLatch getOnBindInvokedLatch() {
    return onBindInvokedLatch;
  }

  public static CountDownLatch getOnUnbindInvokedLatch() {
    return onUnbindInvokedLatch;
  }

  public static void setBindSucceed(boolean bindSucceed) {
    TestModelDownloaderService.bindSucceed = bindSucceed;
  }

  public static void setDownloadResult(
      String url, DownloadResult result, @Nullable byte[] fileContent) {
    TestModelDownloaderService.expectedUrl = url;
    TestModelDownloaderService.downloadResult = result;
    TestModelDownloaderService.fileContent = fileContent;
  }

  public static void reset() {
    boundBefore = false;
    boundNow = false;
    onBindInvokedLatch = new CountDownLatch(1);
    onUnbindInvokedLatch = new CountDownLatch(1);
    bindSucceed = false;
  }

  @Override
  public IBinder onBind(Intent intent) {
    try {
      if (bindSucceed) {
        boundBefore = true;
        boundNow = true;
        return new TestModelDownloaderServiceImpl();
      } else {
        return null;
      }
    } finally {
      onBindInvokedLatch.countDown();
    }
  }

  @Override
  public boolean onUnbind(Intent intent) {
    try {
      boundNow = false;
      return false;
    } finally {
      onUnbindInvokedLatch.countDown();
    }
  }

  private static final class TestModelDownloaderServiceImpl extends IModelDownloaderService.Stub {
    @Override
    public void download(String url, String targetFilePath, IModelDownloaderCallback callback) {
      if (expectedUrl == null || !expectedUrl.equals(url)) {
        throw new IllegalStateException("url does not match");
      }
      TcLog.d(TAG, String.format("Test Request: %s, %s, %s", url, targetFilePath, downloadResult));
      try {
        switch (downloadResult) {
          case SUCCEEDED:
            File targetFile = new File(targetFilePath);
            targetFile.createNewFile();
            Files.write(targetFile.toPath(), fileContent);
            callback.onSuccess(BYTES_WRITTEN);
            break;
          case FAILED:
            callback.onFailure(DOWNLOADER_LIB_ERROR_CODE, ERROR_MSG);
            break;
          case DO_NOTHING:
            // Do nothing
        }
      } catch (Throwable t) {
        // The test would timeout if failing to get the callback result
      }
    }
  }
}
