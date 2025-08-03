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
package com.google.android.enterprise.connectedapps.internal;

import android.os.Bundle;
import android.os.RemoteException;
import com.google.android.enterprise.connectedapps.ICrossProfileCallback;

/** Implementation of {@link BundleCallSender} used when passing a callback exception. */
public class CrossProfileCallbackExceptionBundleCallSender extends BundleCallSender {

  private final ICrossProfileCallback callback;

  public CrossProfileCallbackExceptionBundleCallSender(ICrossProfileCallback callback) {
    if (callback == null) {
      throw new NullPointerException("callback must not be null");
    }
    this.callback = callback;
  }

  /** Relays to {@link ICrossProfileCallback#prepareResult(long, int, int, byte[])} */
  @Override
  void prepareCall(long callId, int blockId, int totalBytes, byte[] bytes) throws RemoteException {
    callback.prepareResult(callId, blockId, totalBytes, bytes);
  }

  @Override
  void prepareBundle(long callId, int bundleId, Bundle bundle) throws RemoteException {
    callback.prepareBundle(callId, bundleId, bundle);
  }

  /**
   * Relays to {@link ICrossProfileCallback#onException(long, int, byte[])}}.
   *
   * <p>Always returns empty byte array.
   */
  @Override
  byte[] call(long callId, int blockId, byte[] bytes) throws RemoteException {
    callback.onException(callId, blockId, bytes);
    return new byte[0];
  }

  /**
   * Always throw an {@link IllegalStateException} as callbacks cannot themselves return values.
   */
  @Override
  byte[] fetchResponse(long callId, int blockId) throws RemoteException {
    throw new IllegalStateException();
  }

  /**
   * Always throw an {@link IllegalStateException} as callbacks cannot themselves return values.
   */
  @Override
  Bundle fetchResponseBundle(long callId, int bundleId) throws RemoteException {
    throw new IllegalStateException();
  }
}
