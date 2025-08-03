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

import static com.google.android.enterprise.connectedapps.StringUtilities.randomString;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.os.Binder;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.TransactionTooLargeException;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class BundleCallSenderTest {

  static class TestBundleCallSender extends BundleCallSender {

    int failPrepareCalls = 0;
    int failCalls = 0;
    int failFetchResponse = 0;
    int failPrepareBundleCalls = 0;
    int failFetchResponseBundle = 0;

    private final BundleCallReceiver bundleCallReceiver = new BundleCallReceiver();

    @Override
    void prepareCall(long callId, int blockId, int totalBytes, byte[] bytes)
        throws RemoteException {
      if (failPrepareCalls-- > 0) {
        throw new TransactionTooLargeException();
      }

      bundleCallReceiver.prepareCall(callId, blockId, totalBytes, bytes);
    }

    @Override
    void prepareBundle(long callId, int bundleId, Bundle bundle) throws RemoteException {
      if (failPrepareBundleCalls-- > 0) {
        throw new TransactionTooLargeException();
      }

      bundleCallReceiver.prepareBundle(callId, bundleId, bundle);
    }

    @Override
    byte[] call(long callId, int blockId, byte[] bytes) throws RemoteException {
      if (failCalls-- > 0) {
        throw new TransactionTooLargeException();
      }

      return bundleCallReceiver.prepareResponse(
          callId, bundleCallReceiver.getPreparedCall(callId, blockId, bytes));
    }

    @Override
    byte[] fetchResponse(long callId, int blockId) throws RemoteException {
      if (failFetchResponse-- > 0) {
        throw new TransactionTooLargeException();
      }

      return bundleCallReceiver.getPreparedResponse(callId, blockId);
    }

    @Override
    Bundle fetchResponseBundle(long callId, int bundleId) throws RemoteException {
      if (failFetchResponseBundle-- > 0) {
        throw new TransactionTooLargeException();
      }

      return bundleCallReceiver.getPreparedResponseBundle(callId, bundleId);
    }
  }

  private final TestBundleCallSender bundleCallSender = new TestBundleCallSender();
  private static final String LARGE_STRING = randomString(1500000); // 3Mb
  private static final Bundle LARGE_BUNDLE = new Bundle(Bundler.class.getClassLoader());
  private final Binder binder = new Binder();
  private final Bundle bundleContainingBinder = new Bundle();

  static {
    LARGE_BUNDLE.putString("value", LARGE_STRING);
  }

  public BundleCallSenderTest() {
    bundleContainingBinder.putBinder("binder", binder);
  }

  @Test
  public void makeBundleCall_prepareCallHasError_retriesUntilSuccess()
      throws UnavailableProfileException {
    bundleCallSender.failPrepareCalls = 5;

    assertThat(bundleCallSender.makeBundleCall(LARGE_BUNDLE).getString("value"))
        .isEqualTo(LARGE_STRING);
  }

  @Test
  public void makeBundleCall_prepareCallHasError_failsAfter10Retries() {
    bundleCallSender.failPrepareCalls = 11;

    assertThrows(
        UnavailableProfileException.class, () -> bundleCallSender.makeBundleCall(LARGE_BUNDLE));
  }

  @Test
  public void makeBundleCall_callHasError_retriesUntilSuccess() throws UnavailableProfileException {
    bundleCallSender.failCalls = 5;

    assertThat(bundleCallSender.makeBundleCall(LARGE_BUNDLE).getString("value"))
        .isEqualTo(LARGE_STRING);
  }

  @Test
  public void makeBundleCall_callHasError_failsAfter10Retries() {
    bundleCallSender.failCalls = 11;

    assertThrows(
        UnavailableProfileException.class, () -> bundleCallSender.makeBundleCall(LARGE_BUNDLE));
  }

  @Test
  public void makeBundleCall_fetchResponseHasError_retriesUntilSuccess()
      throws UnavailableProfileException {
    bundleCallSender.failFetchResponse = 5;

    assertThat(bundleCallSender.makeBundleCall(LARGE_BUNDLE).getString("value"))
        .isEqualTo(LARGE_STRING);
  }

  @Test
  public void makeBundleCall_fetchResponseHasError_failsAfter10Retries() {
    bundleCallSender.failFetchResponse = 11;

    assertThrows(
        UnavailableProfileException.class, () -> bundleCallSender.makeBundleCall(LARGE_BUNDLE));
  }

  @Test
  public void makeBundleCall_bundleContainsBinder_succeeds() throws UnavailableProfileException {
    assertThat(bundleCallSender.makeBundleCall(bundleContainingBinder).getBinder("binder"))
        .isEqualTo(binder);
  }

  @Test
  public void makeBundleCall_prepareBundleHasError_retriesUntilSuccess()
      throws UnavailableProfileException {
    bundleCallSender.failPrepareBundleCalls = 5;

    assertThat(bundleCallSender.makeBundleCall(bundleContainingBinder).getBinder("binder"))
        .isEqualTo(binder);
  }

  @Test
  public void makeBundleCall_prepareBundleHasError_failsAfter10Retries()
      throws UnavailableProfileException {
    bundleCallSender.failPrepareBundleCalls = 11;

    assertThrows(
        UnavailableProfileException.class,
        () -> bundleCallSender.makeBundleCall(bundleContainingBinder));
  }

  @Test
  public void makeBundleCall_fetchResponseBundleHasError_retriesUntilSuccess()
      throws UnavailableProfileException {
    bundleCallSender.failFetchResponseBundle = 5;

    assertThat(bundleCallSender.makeBundleCall(bundleContainingBinder).getBinder("binder"))
        .isEqualTo(binder);
  }

  @Test
  public void makeBundleCall_fetchResponseBundleHasError_failsAfter10Retries()
      throws UnavailableProfileException {
    bundleCallSender.failFetchResponseBundle = 11;

    assertThrows(
        UnavailableProfileException.class,
        () -> bundleCallSender.makeBundleCall(bundleContainingBinder));
  }
}
