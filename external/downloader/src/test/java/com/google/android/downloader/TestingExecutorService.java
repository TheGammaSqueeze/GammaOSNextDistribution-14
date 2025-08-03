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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.util.concurrent.AbstractListeningExecutorService;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of {@link com.google.common.util.concurrent.ListeningExecutorService} for testing
 * purposes. It artificially delays runnables enqueued via calls to {@link #execute} to allow tests
 * to exercise async behavior.
 */
final class TestingExecutorService extends AbstractListeningExecutorService {
  private final ExecutorService delegate;
  private final Queue<Runnable> taskQueue = new ArrayDeque<>();

  private boolean paused = false;

  TestingExecutorService(ExecutorService delegate) {
    this.delegate = delegate;
  }

  @Override
  public void shutdown() {
    delegate.shutdown();
  }

  @Override
  public List<Runnable> shutdownNow() {
    return delegate.shutdownNow();
  }

  @Override
  public boolean isShutdown() {
    return delegate.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return delegate.isTerminated();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return delegate.awaitTermination(timeout, unit);
  }

  public synchronized void pause() {
    paused = true;
  }

  public synchronized void resume() {
    paused = false;
    while (!taskQueue.isEmpty()) {
      delegate.execute(checkNotNull(taskQueue.poll()));
    }
  }

  @Override
  public void execute(Runnable command) {
    if (paused) {
      taskQueue.add(command);
      return;
    }

    delegate.execute(command);
  }
}
