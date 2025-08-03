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
package com.google.android.enterprise.connectedapps.instrumented.utils;

import static java.util.Collections.singleton;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/** A {@link BroadcastReceiver} which can block until a broadcast is received. */
public class BlockingBroadcastReceiver extends BroadcastReceiver {
  private static final int DEFAULT_TIMEOUT_SECONDS = 30;

  private final BlockingQueue<Intent> blockingQueue;
  private final Collection<String> expectedActions;
  private final Context context;

  public BlockingBroadcastReceiver(Context context, Collection<String> expectedActions) {
    this.context = context;
    this.expectedActions = expectedActions;
    blockingQueue = new ArrayBlockingQueue<>(/* capacity= */ 10);
  }

  public BlockingBroadcastReceiver(Context context, String expectedAction) {
    this(context, singleton(expectedAction));
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    if (expectedActions.contains(intent.getAction())) {
      blockingQueue.add(intent);
    }
  }

  /** Call before making the call which should trigger the broadcast. */
  public void register() {
    for (String expectedAction : expectedActions) {
      context.registerReceiver(this, new IntentFilter(expectedAction));
    }
  }

  /**
   * Wait until the broadcast and return the received broadcast intent. {@code null} is returned if
   * no broadcast with expected action is received within 10 seconds.
   */
  public Intent awaitForBroadcast() {
    return awaitForBroadcast(DEFAULT_TIMEOUT_SECONDS * 1000);
  }

  /**
   * Wait until the broadcast and return the received broadcast intent. {@code null} is returned if
   * no broadcast with expected action is received within the given timeout.
   */
  public Intent awaitForBroadcast(long timeoutMillis) {
    try {
      return blockingQueue.poll(timeoutMillis, MILLISECONDS);
    } catch (InterruptedException e) {
      throw new AssertionError("Awaiting broadcast interrupted", e);
    }
  }

  public void unregisterQuietly() {
    try {
      context.unregisterReceiver(this);
    } catch (RuntimeException ex) {
      // ignore issues unregistering
    }
  }
}
