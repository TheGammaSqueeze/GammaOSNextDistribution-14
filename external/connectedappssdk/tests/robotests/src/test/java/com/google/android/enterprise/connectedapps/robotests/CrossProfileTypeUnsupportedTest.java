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
package com.google.android.enterprise.connectedapps.robotests;

import static com.google.android.enterprise.connectedapps.SharedTestUtilities.assertFutureHasException;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.app.Application;
import android.os.Build.VERSION_CODES;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.TestExceptionCallbackListener;
import com.google.android.enterprise.connectedapps.TestStringCallbackListenerImpl;
import com.google.android.enterprise.connectedapps.TestVoidCallbackListenerMultiImpl;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileTestCrossProfileType;
import com.google.android.enterprise.connectedapps.testapp.types.TestCrossProfileType;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for using cross-profile types on unsupported Android versions. */
@RunWith(RobolectricTestRunner.class)
@Config(maxSdk = VERSION_CODES.N_MR1)
public class CrossProfileTypeUnsupportedTest {

  private static final String STRING = "String";

  private final Application context = ApplicationProvider.getApplicationContext();
  private final TestProfileConnector testProfileConnector = TestProfileConnector.create(context);
  private final ProfileTestCrossProfileType profileTestCrossProfileType =
      ProfileTestCrossProfileType.create(testProfileConnector);
  private final TestStringCallbackListenerImpl testStringCallbackListener =
      new TestStringCallbackListenerImpl();
  private final TestVoidCallbackListenerMultiImpl testVoidCallbackListenerMulti =
      new TestVoidCallbackListenerMultiImpl();
  private final TestExceptionCallbackListener testExceptionCallbackListener =
      new TestExceptionCallbackListener();

  @Test
  public void current_synchronous_works() {
    assertThat(profileTestCrossProfileType.current().identityStringMethod(STRING))
        .isEqualTo(STRING);
  }

  @Test
  public void current_async_works() {
    profileTestCrossProfileType
        .current()
        .asyncIdentityStringMethod(STRING, testStringCallbackListener);

    assertThat(testStringCallbackListener.stringCallbackValue).isEqualTo(STRING);
  }

  @Test
  public void current_future_works() throws ExecutionException, InterruptedException {
    assertThat(
            profileTestCrossProfileType
                .current()
                .listenableFutureIdentityStringMethod(STRING)
                .get())
        .isEqualTo(STRING);
  }

  @Test
  public void other_synchronous_throwsUnavailableProfileException() {
    assertThrows(
        UnavailableProfileException.class,
        () -> profileTestCrossProfileType.other().identityStringMethod(STRING));
  }

  @Test
  public void other_async_passesUnavailableProfileException() {
    profileTestCrossProfileType
        .other()
        .asyncIdentityStringMethod(
            STRING, testStringCallbackListener, testExceptionCallbackListener);

    assertThat(testExceptionCallbackListener.lastException)
        .isInstanceOf(UnavailableProfileException.class);
  }

  @Test
  public void other_future_passesUnavailableProfileException() {
    ListenableFuture<String> future =
        profileTestCrossProfileType.other().listenableFutureIdentityStringMethod(STRING);

    assertFutureHasException(future, UnavailableProfileException.class);
  }

  @Test
  public void work_synchronous_throwsUnavailableProfileException() {
    assertThrows(
        UnavailableProfileException.class,
        () -> profileTestCrossProfileType.work().identityStringMethod(STRING));
  }

  @Test
  public void work_async_passesUnavailableProfileException() {
    profileTestCrossProfileType
        .work()
        .asyncIdentityStringMethod(
            STRING, testStringCallbackListener, testExceptionCallbackListener);

    assertThat(testExceptionCallbackListener.lastException)
        .isInstanceOf(UnavailableProfileException.class);
  }

  @Test
  public void work_future_passesUnavailableProfileException() {
    ListenableFuture<String> future =
        profileTestCrossProfileType.work().listenableFutureIdentityStringMethod(STRING);

    assertFutureHasException(future, UnavailableProfileException.class);
  }

  @Test
  public void personal_synchronous_throwsUnavailableProfileException() {
    assertThrows(
        UnavailableProfileException.class,
        () -> profileTestCrossProfileType.personal().identityStringMethod(STRING));
  }

  @Test
  public void personal_async_passesUnavailableProfileException() {
    profileTestCrossProfileType
        .personal()
        .asyncIdentityStringMethod(
            STRING, testStringCallbackListener, testExceptionCallbackListener);

    assertThat(testExceptionCallbackListener.lastException)
        .isInstanceOf(UnavailableProfileException.class);
  }

  @Test
  public void personal_future_passesUnavailableProfileException() {
    ListenableFuture<String> future =
        profileTestCrossProfileType.personal().listenableFutureIdentityStringMethod(STRING);

    assertFutureHasException(future, UnavailableProfileException.class);
  }

  @Test
  public void primary_synchronous_throwsUnavailableProfileException() {
    assertThrows(
        UnavailableProfileException.class,
        () -> profileTestCrossProfileType.primary().identityStringMethod(STRING));
  }

  @Test
  public void primary_async_passesUnavailableProfileException() {
    profileTestCrossProfileType
        .primary()
        .asyncIdentityStringMethod(
            STRING, testStringCallbackListener, testExceptionCallbackListener);

    assertThat(testExceptionCallbackListener.lastException)
        .isInstanceOf(UnavailableProfileException.class);
  }

  @Test
  public void primary_future_passesUnavailableProfileException() {
    ListenableFuture<String> future =
        profileTestCrossProfileType.primary().listenableFutureIdentityStringMethod(STRING);

    assertFutureHasException(future, UnavailableProfileException.class);
  }

  @Test
  public void secondary_synchronous_throwsUnavailableProfileException() {
    assertThrows(
        UnavailableProfileException.class,
        () -> profileTestCrossProfileType.secondary().identityStringMethod(STRING));
  }

  @Test
  public void secondary_async_passesUnavailableProfileException() {
    profileTestCrossProfileType
        .secondary()
        .asyncIdentityStringMethod(
            STRING, testStringCallbackListener, testExceptionCallbackListener);

    assertThat(testExceptionCallbackListener.lastException)
        .isInstanceOf(UnavailableProfileException.class);
  }

  @Test
  public void secondary_future_passesUnavailableProfileException() {
    ListenableFuture<String> future =
        profileTestCrossProfileType.secondary().listenableFutureIdentityStringMethod(STRING);

    assertFutureHasException(future, UnavailableProfileException.class);
  }

  @Test
  public void profiles_synchronous_callsNothing() {
    TestCrossProfileType.voidMethodCalls = 0;

    profileTestCrossProfileType
        .profiles(testProfileConnector.utils().getCurrentProfile())
        .voidMethod();

    assertThat(TestCrossProfileType.voidMethodCalls).isEqualTo(0);
  }

  @Test
  public void profiles_async_callsNothing() {
    TestCrossProfileType.voidMethodCalls = 0;

    profileTestCrossProfileType
        .profiles(testProfileConnector.utils().getCurrentProfile())
        .asyncVoidMethod(testVoidCallbackListenerMulti);

    assertThat(TestCrossProfileType.voidMethodCalls).isEqualTo(0);
  }

  @Test
  public void profiles_future_callsNothing() throws ExecutionException, InterruptedException {
    TestCrossProfileType.voidMethodCalls = 0;

    profileTestCrossProfileType
        .profiles(testProfileConnector.utils().getCurrentProfile())
        .listenableFutureVoidMethod()
        .get();

    assertThat(TestCrossProfileType.voidMethodCalls).isEqualTo(0);
  }

  @Test
  public void both_synchronous_callsCurrentProfileOnce() {
    TestCrossProfileType.voidMethodCalls = 0;

    profileTestCrossProfileType.both().voidMethod();

    assertThat(TestCrossProfileType.voidMethodCalls).isEqualTo(1);
  }

  @Test
  public void both_async_callsCurrentProfileOnce() {
    TestCrossProfileType.voidMethodCalls = 0;

    profileTestCrossProfileType.both().asyncVoidMethod(testVoidCallbackListenerMulti);

    assertThat(TestCrossProfileType.voidMethodCalls).isEqualTo(1);
  }

  @Test
  public void both_future_callsCurrentProfileOnce()
      throws ExecutionException, InterruptedException {
    TestCrossProfileType.voidMethodCalls = 0;

    profileTestCrossProfileType.both().listenableFutureVoidMethod().get();

    assertThat(TestCrossProfileType.voidMethodCalls).isEqualTo(1);
  }

  @Test
  public void suppliers_synchronous_callsCurrentProfileOnce() {
    TestCrossProfileType.voidMethodCalls = 0;

    profileTestCrossProfileType.suppliers().voidMethod();

    assertThat(TestCrossProfileType.voidMethodCalls).isEqualTo(1);
  }

  @Test
  public void suppliers_async_callsCurrentProfileOnce() {
    TestCrossProfileType.voidMethodCalls = 0;

    profileTestCrossProfileType.suppliers().asyncVoidMethod(testVoidCallbackListenerMulti);

    assertThat(TestCrossProfileType.voidMethodCalls).isEqualTo(1);
  }

  @Test
  public void suppliers_future_callsCurrentProfileOnce()
      throws ExecutionException, InterruptedException {
    TestCrossProfileType.voidMethodCalls = 0;

    profileTestCrossProfileType.suppliers().listenableFutureVoidMethod().get();

    assertThat(TestCrossProfileType.voidMethodCalls).isEqualTo(1);
  }
}
