/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.enterprise.connectedapps;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * A test {@link ScheduledExecutorService} which supports only the methods used by the Connected
 * Apps SDK.
 *
 * <p>Use {@link #advanceTimeBy(long, TimeUnit)} for progress time. Everything is executed on
 * the calling thread.
 */
public class TestScheduledExecutorService extends AbstractExecutorService implements ScheduledExecutorService {

  private long millisPast = 0;
  private final Set<SimpleScheduledFuture<?>> executeQueue =
      Collections.newSetFromMap(new ConcurrentHashMap<>());

  public TestScheduledExecutorService() {}

  @Override
  public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
    return schedule((Callable<Void>) () -> {
      command.run();
      return null;
    }, delay, unit);
  }

  @Override
  public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
    if (executeQueue.isEmpty()) {
      millisPast = 0;
    }
    SimpleScheduledFuture<V> future =
        new SimpleScheduledFuture<>(callable, millisPast + unit.toMillis(delay));
    executeQueue.add(future);
    return future;
  }

  @Override
  public ScheduledFuture<?> scheduleAtFixedRate(
      Runnable command, long initialDelay, long period, TimeUnit unit) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(
      Runnable command, long initialDelay, long delay, TimeUnit unit) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void shutdown() {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Runnable> shutdownNow() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isShutdown() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isTerminated() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void execute(Runnable command) {
    command.run();
  }

  public void advanceTimeBy(long timeout, TimeUnit unit) throws Exception {
    advanceTimeByMillis(unit.toMillis(timeout));
  }

  private void advanceTimeByMillis(long timeoutMillis) throws Exception {
    millisPast += timeoutMillis;
    Iterator<SimpleScheduledFuture<?>> scheduledFutures = executeQueue.iterator();

    while (scheduledFutures.hasNext()) {
      SimpleScheduledFuture<?> next = scheduledFutures.next();
      if (next.getDelay(MILLISECONDS) <= millisPast) {
        scheduledFutures.remove();
        next.complete();
      }
    }
  }

  private static class SimpleScheduledFuture<T> implements ScheduledFuture<T> {

    private final Callable<T> callable;
    private final long timeoutMillis;
    private boolean isCancelled = false;
    private boolean isDone = false;
    private T value;

    public SimpleScheduledFuture(Callable<T> callable, long timeoutMillis) {
      this.callable = callable;
      this.timeoutMillis = timeoutMillis;
    }

    public void complete() throws Exception {
      if (isDone || isCancelled()) {
        return;
      }

      isDone = true;
      value = callable.call();
    }

    @Override
    public long getDelay(TimeUnit unit) {
      return unit.convert(timeoutMillis, MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
      return 0;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      isCancelled = true;
      return true;
    }

    @Override
    public boolean isCancelled() {
      return isCancelled;
    }

    @Override
    public boolean isDone() {
      return isDone;
    }

    @Override
    public T get() {
      if (!isDone) {
        throw new IllegalStateException("Not executed yet");
      }
      return value;
    }

    @Override
    public T get(long timeout, TimeUnit unit) {
      if (!isDone) {
        throw new IllegalStateException("Not executed yet");
      }
      return value;
    }
  }
}
