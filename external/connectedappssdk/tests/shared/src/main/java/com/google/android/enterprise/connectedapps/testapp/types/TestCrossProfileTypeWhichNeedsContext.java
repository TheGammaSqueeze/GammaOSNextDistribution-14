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
package com.google.android.enterprise.connectedapps.testapp.types;

import static com.google.common.util.concurrent.Futures.immediateFuture;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.google.android.enterprise.connectedapps.annotations.CrossProfile;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import com.google.android.enterprise.connectedapps.testapp.ConnectorSingleton;
import com.google.android.enterprise.connectedapps.testapp.TestStringCallbackListener;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

@CrossProfile(connector = TestProfileConnector.class)
public class TestCrossProfileTypeWhichNeedsContext {

  private static final int TEN_SECONDS = 10000;

  public static int voidMethodCalls = 0;

  private final Context context;

  private final ProfileTestCrossProfileType profileTestCrossProfileType;

  public TestCrossProfileTypeWhichNeedsContext(Context context) {
    this.context = context;
    this.profileTestCrossProfileType =
        ProfileTestCrossProfileType.create(ConnectorSingleton.getConnector(context));
  }

  @CrossProfile
  public ListenableFuture<Void> listenableFutureMethodWhichNeverSetsTheValue() {
    return SettableFuture.create();
  }

  @CrossProfile
  public void asyncMethodWhichNeverCallsBack(TestStringCallbackListener callback) {}

  @CrossProfile
  public void voidMethod() {
    voidMethodCalls += 1;
  }

  @CrossProfile
  public void connectToOtherProfile() {
    // This, when called cross-profile, causes the other profile to create a connection back to the
    // original profile
    try {
      ConnectorSingleton.getConnector(context).connect(this);
    } catch (UnavailableProfileException e) {
      throw new IllegalStateException("Error connecting", e);
    }
  }

  @CrossProfile
  public boolean isConnectedToOtherProfile() {
    return ConnectorSingleton.getConnector(context).isConnected();
  }

  @CrossProfile
  public String methodWhichCallsIdentityStringMethodOnOtherProfile(String s) {
    try {
      return profileTestCrossProfileType.other().identityStringMethod(s);
    } catch (UnavailableProfileException e) {
      throw new RuntimeException("Cannot call back to other profile", e);
    }
  }

  @CrossProfile
  public void asyncMethodWhichCallsIdentityStringMethodOnOtherProfile(
      String s, TestStringCallbackListener callback) {
    profileTestCrossProfileType
        .other()
        .asyncIdentityStringMethod(
            s,
            callback,
            throwable -> {
              throw new RuntimeException(throwable);
            });
  }

  @CrossProfile
  public ListenableFuture<String>
      listenableFutureMethodWhichCallsIdentityStringMethodOnOtherProfile(String s) {
    return profileTestCrossProfileType.other().listenableFutureIdentityStringMethod(s);
  }

  @CrossProfile
  public String identityStringMethodWhichDelays10SecondsOnWorkProfile(String s) {
    if (ConnectorSingleton.getConnector(context).utils().runningOnWork()) {
      try {
        Thread.sleep(TEN_SECONDS);
      } catch (InterruptedException e) {
        throw new RuntimeException("Sleep interrupted", e);
      }
    }
    return s;
  }

  @CrossProfile
  public void asyncIdentityStringMethodWhichDelays10SecondsOnWorkProfile(
      String s, TestStringCallbackListener callback) {
    if (ConnectorSingleton.getConnector(context).utils().runningOnWork()) {
      new Handler(Looper.getMainLooper())
          .postDelayed(
              () -> {
                callback.stringCallback(s);
              },
              TEN_SECONDS);
    } else {
      callback.stringCallback(s);
    }
  }

  @CrossProfile
  public ListenableFuture<String> futureIdentityStringMethodWhichDelays10SecondsOnWorkProfile(
      String s) {
    if (ConnectorSingleton.getConnector(context).utils().runningOnWork()) {
      SettableFuture<String> future = SettableFuture.create();
      new Handler(Looper.getMainLooper())
          .postDelayed(
              () -> {
                future.set(s);
              },
              TEN_SECONDS);
      return future;
    }

    return immediateFuture(s);
  }

  @CrossProfile
  public int getUserId() {
    return android.os.Process.myUid() / 100000;
  }

  @CrossProfile
  public int getOtherUserId() {
    try {
      return profileTestCrossProfileType.other().getUserId();
    } catch (UnavailableProfileException e) {
      throw new RuntimeException("Cannot call back to other profile", e);
    }
  }
}
