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

import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import com.google.android.enterprise.connectedapps.internal.Bundler;

/**
 * An implementation of {@link ICrossProfileCallback} which just redirects call to a given {@link
 * LocalCallback}.
 *
 * <p>This does not support preparing results, it only supports results which fit into a single
 * block.
 */
public class TestICrossProfileCallback implements ICrossProfileCallback {
  private final LocalCallback localCallback;

  public TestICrossProfileCallback(LocalCallback localCallback) {
    this.localCallback = localCallback;
  }

  @Override
  public void prepareResult(long callId, int blockId, int numBytes, byte[] params)
      throws RemoteException {}

  @Override
  public void prepareBundle(long callId, int bundleId, Bundle bundle) {}

  @Override
  public void onResult(long callId, int blockId, int methodIdentifier, byte[] params)
      throws RemoteException {
    Parcel p = Parcel.obtain(); // Recycled in this method
    p.unmarshall(params, 0, params.length);
    p.setDataPosition(0);
    Bundle bundle = new Bundle(Bundler.class.getClassLoader());
    bundle.readFromParcel(p);
    p.recycle();
    localCallback.onResult(methodIdentifier, bundle);
  }

  @Override
  public void onException(long callId, int blockId, byte[] params) throws RemoteException {
    Parcel p = Parcel.obtain(); // Recycled in this method
    p.unmarshall(params, 0, params.length);
    p.setDataPosition(0);
    Bundle bundle = new Bundle(Bundler.class.getClassLoader());
    bundle.readFromParcel(p);
    p.recycle();
    localCallback.onException(bundle);
  }

  @Override
  public IBinder asBinder() {
    return null;
  }
}
