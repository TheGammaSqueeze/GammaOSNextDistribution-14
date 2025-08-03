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

import static com.google.common.base.Throwables.getStackTraceAsString;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.joining;
import static org.junit.Assert.fail;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import org.junit.rules.ExternalResource;

/**
 * A {@link org.junit.rules.TestRule} that manages and provides instances of {@link
 * java.util.concurrent.Executor} and its various more specific interfaces. Takes care of shutting
 * down any started threads and executors during execution, and also collects uncaught exceptions,
 * failing the test and reporting the uncaught exception if any are found during execution.
 */
public class TestExecutorRule extends ExternalResource {
  private final Duration timeout;
  private final List<Throwable> uncaughtExceptions = new ArrayList<>();
  private final List<ExecutorService> executorServices = new ArrayList<>();
  private final ThreadFactory threadFactory =
      runnable -> {
        Thread thread = Executors.defaultThreadFactory().newThread(runnable);
        // Insert an uncaught exception handler so that that errors happening on a background
        // thread can be collected and cause test failures.
        thread.setUncaughtExceptionHandler((t, e) -> uncaughtExceptions.add(e));
        return thread;
      };

  /**
   * Constructs a new instance of this rule with the provided {@code timeout}. The timeout will be
   * used when calling {@link ExecutorService#awaitTermination} on any {@link ExecutorService}
   * instances created by this rule.
   */
  public TestExecutorRule(Duration timeout) {
    this.timeout = timeout;
  }

  /**
   * Creates a new single-threaded {@link ExecutorService} for use in tests. The executor will
   * collect any uncaught exceptions encountered during test execution, and will fail the test with
   * a detailed report of exceptions, if any are encountered. The executor will also be shut down
   * and will await termination. Failure to shutdown in time (e.g. due to a blocked thread) will
   * result in a test failure as well.
   */
  public ExecutorService newSingleThreadExecutor() {
    ExecutorService executorService = Executors.newSingleThreadExecutor(threadFactory);
    executorServices.add(executorService);
    return executorService;
  }

  /**
   * Creates a new single-threaded {@link ScheduledExecutorService} for use in tests. The executor
   * will collect any uncaught exceptions encountered during test execution, and will fail the test
   * with a detailed report of exceptions, if any are encountered. The executor will also be shut
   * down and will await termination. Failure to shutdown in time (e.g. due to a blocked thread)
   * will result in a test failure as well.
   */
  public ScheduledExecutorService newSingleThreadScheduledExecutor() {
    ScheduledExecutorService executorService =
        Executors.newSingleThreadScheduledExecutor(threadFactory);
    executorServices.add(executorService);
    return executorService;
  }

  @Override
  protected void after() {
    try {
      for (ExecutorService executorService : executorServices) {
        try {
          executorService.shutdown();
          assertThat(executorService.awaitTermination(timeout.toMillis(), MILLISECONDS)).isTrue();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          fail("Error shutting down executor service:" + e);
        } catch (Exception e) {
          fail("Error shutting down executor service:" + e);
        }
      }

      if (!uncaughtExceptions.isEmpty()) {
        String message =
            uncaughtExceptions.stream()
                .map(e -> "\n\t" + getStackTraceAsString(e).replace("\t", "\t\t"))
                .collect(joining("\n"));
        fail("Uncaught exceptions found: " + message);
      }
    } finally {
      uncaughtExceptions.clear();
      executorServices.clear();
    }
  }
}
