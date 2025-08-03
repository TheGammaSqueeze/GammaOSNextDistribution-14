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
import com.google.android.enterprise.connectedapps.internal.BundleUtilities;
import com.google.android.enterprise.connectedapps.internal.Bundler;

public class TestStringCrossProfileCallback implements ICrossProfileCallback {

  public int lastReceivedMethodIdentifier = -1;
  public String lastReceivedMethodValueParam;
  public Throwable lastReceivedException;

  @Override
  public void prepareResult(long callId, int blockId, int numBytes, byte[] params) {}

  @Override
  public void prepareBundle(long callId, int bundleId, Bundle bundle) {}

  @Override
  public void onResult(long callId, int blockId, int methodIdentifier, byte[] paramsBytes)
      throws RemoteException {
    lastReceivedMethodIdentifier = methodIdentifier;
    Parcel parcel = Parcel.obtain();
    parcel.unmarshall(paramsBytes, 0, paramsBytes.length);
    parcel.setDataPosition(0);
    Bundle params = new Bundle(Bundler.class.getClassLoader());
    params.readFromParcel(parcel);
    parcel.recycle();
    lastReceivedMethodValueParam = params.getString("value");
  }

  @Override
  public void onException(long callId, int blockId, byte[] paramsBytes) throws RemoteException {
    Parcel parcel = Parcel.obtain();
    parcel.unmarshall(paramsBytes, 0, paramsBytes.length);
    parcel.setDataPosition(0);
    Bundle bundle = new Bundle(Bundler.class.getClassLoader());
    bundle.readFromParcel(parcel);
    parcel.recycle();

    lastReceivedException = BundleUtilities.readThrowableFromBundle(bundle, "throwable");
  }

  @Override
  public IBinder asBinder() {
    return null;
  }
}
