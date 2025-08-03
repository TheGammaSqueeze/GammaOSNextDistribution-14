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

package com.android.textclassifier.common;

import com.android.textclassifier.common.base.TcLog;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.Executors;

// TODO(licha): Revisit the configurations of thread pools
/**
 * Holder of executor singletons.
 *
 * <p>Note because we have two processes, so we may keep two copis of executors in this class.
 */
public final class TextClassifierServiceExecutors {
  private static final String TAG = "TextClassifierServiceExecutors";

  /** Returns an executor with normal priority. Used for handling client requests. */
  public static ListeningExecutorService getNormhPriorityExecutor() {
    return NormPriorityExecutorHolder.normPriorityExecutor;
  }

  /** Returns a single-thread executor with low priority. Used for internal tasks like logging. */
  public static ListeningExecutorService getLowPriorityExecutor() {
    return LowPriorityExecutorHolder.lowPriorityExecutor;
  }

  /**
   * Returns a single-thread executor with min priority. Used for downloader background processing.
   */
  public static ListeningExecutorService getDownloaderExecutor() {
    return DownloaderExecutorHolder.downloaderExecutor;
  }

  /**
   * Returns a single-thread executor with min priority for network IO ops. Currently only used by
   * model downloader service.
   */
  public static ListeningExecutorService getNetworkIOExecutor() {
    return NetworkIOExecutorHolder.networkIOExecutor;
  }

  private static class NormPriorityExecutorHolder {
    static final ListeningExecutorService normPriorityExecutor =
        init("tcs-norm-prio-executor-%d", Thread.NORM_PRIORITY, /* corePoolSize= */ 2);
  }

  private static class LowPriorityExecutorHolder {
    static final ListeningExecutorService lowPriorityExecutor =
        init("tcs-low-prio-executor-%d", Thread.NORM_PRIORITY - 1, /* corePoolSize= */ 1);
  }

  private static class DownloaderExecutorHolder {
    static final ListeningExecutorService downloaderExecutor =
        init("tcs-download-executor-%d", Thread.MIN_PRIORITY, /* corePoolSize= */ 1);
  }

  private static class NetworkIOExecutorHolder {
    static final ListeningExecutorService networkIOExecutor =
        init("tcs-network-io-executor-%d", Thread.MIN_PRIORITY, /* corePoolSize= */ 1);
  }

  private static ListeningExecutorService init(String nameFormat, int priority, int corePoolSize) {
    TcLog.v(TAG, "Creating executor: " + nameFormat);
    return MoreExecutors.listeningDecorator(
        Executors.newFixedThreadPool(
            corePoolSize,
            new ThreadFactoryBuilder()
                .setNameFormat(nameFormat)
                .setPriority(priority)
                // In Android, those uncaught exceptions will crash the whole process if not handled
                .setUncaughtExceptionHandler(
                    (thread, throwable) ->
                        TcLog.e(TAG, "Exception from executor: " + thread, throwable))
                .build()));
  }

  private TextClassifierServiceExecutors() {}
}
