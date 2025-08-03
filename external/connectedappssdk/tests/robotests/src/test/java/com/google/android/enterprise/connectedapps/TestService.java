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
import android.os.Parcel;
import android.os.RemoteException;
import com.google.android.enterprise.connectedapps.internal.Bundler;
import com.google.android.enterprise.connectedapps.internal.ByteUtilities;
import com.google.auto.value.AutoValue;
import org.checkerframework.checker.nullness.qual.Nullable;

public class TestService extends ICrossProfileService.Stub {

  @AutoValue
  abstract static class LoggedCrossProfileMethodCall {
    abstract long getCrossProfileTypeIdentifier();

    abstract long getMethodIdentifier();

    abstract Bundle getParams();

    @Nullable
    abstract ICrossProfileCallback callback();

    static LoggedCrossProfileMethodCall create(
        long crossProfileTypeIdentifier,
        long methodIdentifier,
        Bundle params,
        ICrossProfileCallback callback) {
      return new AutoValue_TestService_LoggedCrossProfileMethodCall(
          crossProfileTypeIdentifier, methodIdentifier, params, callback);
    }
  }

  private LoggedCrossProfileMethodCall lastCall;
  private Bundle responseBundle = new Bundle(Bundler.class.getClassLoader());

  LoggedCrossProfileMethodCall lastCall() {
    return lastCall;
  }

  /** Set the bundle to be returned from a call to this service. */
  void setResponseBundle(Bundle responseBundle) {
    this.responseBundle = responseBundle;
  }

  @Override
  public void prepareCall(long callId, int blockId, int numBytes, byte[] paramsBytes) {}

  @Override
  public void prepareBundle(long callId, int blockId, Bundle bundle) {}

  @Override
  public byte[] call(
      long callId,
      int blockId,
      long crossProfileTypeIdentifier,
      int methodIdentifier,
      byte[] paramsBytes,
      ICrossProfileCallback callback)
      throws RemoteException {

    Parcel parcel = Parcel.obtain(); // Recycled by this method on next call
    parcel.unmarshall(paramsBytes, 0, paramsBytes.length);
    parcel.setDataPosition(0);
    Bundle bundle = new Bundle(Bundler.class.getClassLoader());
    bundle.readFromParcel(parcel);

    lastCall =
        LoggedCrossProfileMethodCall.create(
            crossProfileTypeIdentifier, methodIdentifier, bundle, callback);

    Parcel responseParcel = Parcel.obtain();
    responseBundle.writeToParcel(responseParcel, /* flags= */ 0);
    byte[] parcelBytes = responseParcel.marshall();
    responseParcel.recycle();

    return prepareResponse(parcelBytes);
  }

  private static byte[] prepareResponse(byte[] parcelBytes) {
    // This doesn't deal with large responses.
    return ByteUtilities.joinByteArrays(new byte[] {0}, parcelBytes);
  }

  @Override
  public byte[] fetchResponse(long callId, int blockId) {
    return null;
  }

  @Override
  public Bundle fetchResponseBundle(long callId, int bundleId) {
    return null;
  }
}
