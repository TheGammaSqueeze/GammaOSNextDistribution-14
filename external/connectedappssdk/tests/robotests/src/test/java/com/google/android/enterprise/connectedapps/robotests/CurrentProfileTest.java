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

import static com.google.android.enterprise.connectedapps.SharedTestUtilities.INTERACT_ACROSS_USERS;
import static com.google.android.enterprise.connectedapps.SharedTestUtilities.assertFutureDoesNotHaveException;
import static com.google.android.enterprise.connectedapps.SharedTestUtilities.assertFutureHasException;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.app.Application;
import android.app.Service;
import android.os.Build.VERSION_CODES;
import android.os.IBinder;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.CrossProfileConnector;
import com.google.android.enterprise.connectedapps.NonSimpleCallbackListenerImpl;
import com.google.android.enterprise.connectedapps.RobolectricTestUtilities;
import com.google.android.enterprise.connectedapps.TestBooleanCallbackListenerImpl;
import com.google.android.enterprise.connectedapps.TestNotReallySerializableObjectCallbackListenerImpl;
import com.google.android.enterprise.connectedapps.TestScheduledExecutorService;
import com.google.android.enterprise.connectedapps.TestStringCallbackListenerImpl;
import com.google.android.enterprise.connectedapps.TestVoidCallbackListenerImpl;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import com.google.android.enterprise.connectedapps.testapp.CustomRuntimeException;
import com.google.android.enterprise.connectedapps.testapp.NotReallySerializableObject;
import com.google.android.enterprise.connectedapps.testapp.ParcelableObject;
import com.google.android.enterprise.connectedapps.testapp.configuration.TestApplication;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileTestCrossProfileType;
import com.google.android.enterprise.connectedapps.testapp.types.TestCrossProfileType;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(minSdk = VERSION_CODES.O)
public class CurrentProfileTest {

  private static final String STRING = "String";
  private static final String STRING2 = "String2";
  private static final ParcelableObject PARCELABLE_OBJECT = new ParcelableObject("");
  private static final NotReallySerializableObject NOT_REALLY_SERIALIZABLE_OBJECT =
      new NotReallySerializableObject(PARCELABLE_OBJECT);

  private final Application context = ApplicationProvider.getApplicationContext();
  private final TestStringCallbackListenerImpl stringCallbackListener =
      new TestStringCallbackListenerImpl();
  private final TestBooleanCallbackListenerImpl booleanCallbackListener =
      new TestBooleanCallbackListenerImpl();
  private final TestVoidCallbackListenerImpl voidCallbackListener =
      new TestVoidCallbackListenerImpl();
  private final TestScheduledExecutorService scheduledExecutorService =
      new TestScheduledExecutorService();
  private final TestProfileConnector testProfileConnector =
      TestProfileConnector.create(context, scheduledExecutorService);
  private final RobolectricTestUtilities testUtilities =
      new RobolectricTestUtilities(testProfileConnector, scheduledExecutorService);
  private final ProfileTestCrossProfileType profileTestCrossProfileType =
      ProfileTestCrossProfileType.create(testProfileConnector);
  private final TestNotReallySerializableObjectCallbackListenerImpl
      notReallySerializableObjectCallbackListener =
          new TestNotReallySerializableObjectCallbackListenerImpl();
  private final NonSimpleCallbackListenerImpl nonSimpleCallbackListener =
      new NonSimpleCallbackListenerImpl();

  @Before
  public void setUp() {
    Service profileAwareService = Robolectric.setupService(TestApplication.getService());
    testUtilities.initTests();
    IBinder binder = profileAwareService.onBind(/* intent= */ null);
    testUtilities.setBinding(binder, CrossProfileConnector.class.getName());
    testUtilities.createWorkUser();
    testUtilities.turnOnWorkProfile();
    testUtilities.setRunningOnPersonalProfile();
    testUtilities.setRequestsPermissions(INTERACT_ACROSS_USERS);
    testUtilities.grantPermissions(INTERACT_ACROSS_USERS);
    testUtilities.addDefaultConnectionHolderAndWait();
  }

  @Test
  public void current_isBound_callsMethod() throws UnavailableProfileException {
    testUtilities.turnOnWorkProfile();
    testUtilities.addDefaultConnectionHolderAndWait();

    assertThat(profileTestCrossProfileType.current().identityStringMethod(STRING))
        .isEqualTo(STRING);
  }

  @Test
  public void current_isNotBound_callsMethod() {
    testUtilities.turnOffWorkProfile();

    assertThat(profileTestCrossProfileType.current().identityStringMethod(STRING))
        .isEqualTo(STRING);
  }

  @Test
  public void current_async_isBound_callsMethod() {
    testUtilities.turnOnWorkProfile();
    testUtilities.addDefaultConnectionHolderAndWait();

    profileTestCrossProfileType.current().asyncVoidMethod(voidCallbackListener);

    assertThat(TestCrossProfileType.voidMethodCalls).isEqualTo(1);
  }

  @Test
  public void current_synchronous_isBound_automaticConnectionManagement_callsMethod() {
    testUtilities.turnOnWorkProfile();
    testProfileConnector.clearConnectionHolders();
    ListenableFuture<Void> ignored =
        profileTestCrossProfileType.other().listenableFutureVoidMethod(); // Causes it to bind

    assertThat(profileTestCrossProfileType.current().identityStringMethod(STRING))
        .isEqualTo(STRING);
  }

  @Test
  public void current_async_isNotBound_callsMethod() {
    testUtilities.turnOffWorkProfile();

    profileTestCrossProfileType.current().asyncVoidMethod(voidCallbackListener);

    assertThat(TestCrossProfileType.voidMethodCalls).isEqualTo(1);
  }

  @Test // This behaviour is expected right now but will change
  public void current_async_blockingMethod_blocks() {
    profileTestCrossProfileType
        .current()
        .asyncVoidMethodWithDelay(voidCallbackListener, /* secondsDelay= */ 5);

    assertThat(voidCallbackListener.callbackMethodCalls).isEqualTo(1);
  }

  @Test
  public void current_async_firesCallback() {
    profileTestCrossProfileType.current().asyncVoidMethod(voidCallbackListener);

    assertThat(voidCallbackListener.callbackMethodCalls).isEqualTo(1);
  }

  @Test
  public void current_async_passesParametersCorrectly() {
    profileTestCrossProfileType.current().asyncIdentityStringMethod(STRING, stringCallbackListener);

    assertThat(stringCallbackListener.stringCallbackValue).isEqualTo(STRING);
  }

  @Test
  public void current_async_nonblockingMethod_doesNotBlock() {
    profileTestCrossProfileType
        .current()
        .asyncVoidMethodWithNonBlockingDelay(voidCallbackListener, /* secondsDelay= */ 5);

    assertThat(voidCallbackListener.callbackMethodCalls).isEqualTo(0);
  }

  @Test
  public void current_async_nonblockingMethod_doesCallback() {
    profileTestCrossProfileType
        .current()
        .asyncVoidMethodWithNonBlockingDelay(voidCallbackListener, /* secondsDelay= */ 5);
    testUtilities.advanceTimeBySeconds(10);

    assertThat(voidCallbackListener.callbackMethodCalls).isEqualTo(1);
  }

  @Test
  public void current_listenableFuture_isBound_callsMethod()
      throws ExecutionException, InterruptedException {
    testUtilities.turnOnWorkProfile();
    testUtilities.addDefaultConnectionHolderAndWait();

    profileTestCrossProfileType.current().listenableFutureVoidMethod().get();

    assertThat(TestCrossProfileType.voidMethodCalls).isEqualTo(1);
  }

  @Test
  public void current_listenableFuture_isNotBound_callsMethod()
      throws ExecutionException, InterruptedException {
    testUtilities.turnOffWorkProfile();

    profileTestCrossProfileType.current().listenableFutureVoidMethod().get();

    assertThat(TestCrossProfileType.voidMethodCalls).isEqualTo(1);
  }

  @Test // This behaviour is expected right now but will change
  public void current_listenableFuture_blockingMethod_blocks() {
    ListenableFuture<Void> voidFuture =
        profileTestCrossProfileType
            .current()
            .listenableFutureVoidMethodWithDelay(/* secondsDelay= */ 5);

    assertThat(voidFuture.isDone()).isTrue();
  }

  @Test
  public void current_listenableFuture_setsFuture()
      throws ExecutionException, InterruptedException {

    // This would throw an exception if it wasn't set
    assertThat(profileTestCrossProfileType.current().listenableFutureVoidMethod().get()).isNull();
  }

  @Test
  public void current_listenableFuture_setsException_isSet() {
    assertFutureHasException(
        profileTestCrossProfileType
            .current()
            .listenableFutureVoidMethodWhichSetsIllegalStateException(),
        IllegalStateException.class);
  }

  @Test
  public void current_listenableFuture_passesParametersCorrectly()
      throws ExecutionException, InterruptedException {
    ListenableFuture<String> future =
        profileTestCrossProfileType.current().listenableFutureIdentityStringMethod(STRING);

    assertThat(future.get()).isEqualTo(STRING);
  }

  @Test
  public void current_listenableFuture_nonblockingMethod_doesNotBlock() {
    ListenableFuture<Void> future =
        profileTestCrossProfileType
            .current()
            .listenableFutureVoidMethodWithNonBlockingDelay(/* secondsDelay= */ 5);

    assertThat(future.isDone()).isFalse();
  }

  @Test
  public void current_listenableFuture_nonblockingMethod_doesCallback() {
    ListenableFuture<Void> future =
        profileTestCrossProfileType
            .current()
            .listenableFutureVoidMethodWithNonBlockingDelay(/* secondsDelay= */ 5);
    testUtilities.advanceTimeBySeconds(10);

    assertThat(future.isDone()).isTrue();
  }

  @Test
  public void current_listenableFuture_doesNotTimeout() {
    ListenableFuture<Void> future =
        profileTestCrossProfileType.current().listenableFutureMethodWhichNeverSetsTheValue();
    testUtilities.advanceTimeBySeconds(10);

    assertFutureDoesNotHaveException(future, UnavailableProfileException.class);
  }

  @Test
  public void current_async_doesNotTimeout() {
    profileTestCrossProfileType.current().asyncMethodWhichNeverCallsBack(stringCallbackListener);
    testUtilities.advanceTimeBySeconds(100);

    assertThat(stringCallbackListener.callbackMethodCalls).isEqualTo(0);
  }

  @Test
  public void current_serializableObjectIsNotReallySerializable_works() {
    assertThat(
            profileTestCrossProfileType.current()
                .identityNotReallySerializableObjectMethod(NOT_REALLY_SERIALIZABLE_OBJECT))
        .isEqualTo(NOT_REALLY_SERIALIZABLE_OBJECT);
  }

  @Test
  public void current_async_serializableObjectIsNotReallySerializable_works() {
    profileTestCrossProfileType.current()
        .asyncGetNotReallySerializableObjectMethod(notReallySerializableObjectCallbackListener);

    assertThat(notReallySerializableObjectCallbackListener.notReallySerializableObjectCallbackValue)
        .isNotNull();
  }

  @Test
  public void current_future_serializableObjectIsNotReallySerializable_works()
      throws ExecutionException, InterruptedException {
    ListenableFuture<NotReallySerializableObject> future =
        profileTestCrossProfileType.current().futureGetNotReallySerializableObjectMethod();

    assertThat(future.get()).isNotNull();
  }

  @Test
  public void current_synchronous_throwsException_throwsOriginalException() {
    assertThrows(
        CustomRuntimeException.class,
        () -> profileTestCrossProfileType.current().methodWhichThrowsRuntimeException());
  }

  @Test
  public void current_async_throwsException_throwsOriginalException() {
    assertThrows(
        CustomRuntimeException.class,
        () ->
            profileTestCrossProfileType
                .current()
                .asyncStringMethodWhichThrowsRuntimeException(stringCallbackListener));
  }

  @Test
  public void current_future_throwsException_throwsOriginalException() {
    assertThrows(
        CustomRuntimeException.class,
        () ->
            profileTestCrossProfileType
                .current()
                .listenableFutureVoidMethodWhichThrowsRuntimeException());
  }

  @Test
  public void current_synchronous_contextArgument_works() {
    assertThat(profileTestCrossProfileType.current().isContextArgumentPassed()).isTrue();
  }

  @Test
  public void current_async_contextArgument_works() {
    profileTestCrossProfileType.current().asyncIsContextArgumentPassed(booleanCallbackListener);

    assertThat(booleanCallbackListener.booleanCallbackValue).isTrue();
  }

  @Test
  public void current_future_contextArgument_works() throws Exception {
    ListenableFuture<Boolean> result =
        profileTestCrossProfileType.current().futureIsContextArgumentPassed();

    assertThat(result.get()).isTrue();
  }

  @Test
  public void current_synchronous_declaresButDoesNotThrowException_works() throws Exception {
    assertThat(
            profileTestCrossProfileType
                .current()
                .identityStringMethodDeclaresButDoesNotThrowIOException(STRING))
        .isEqualTo(STRING);
  }

  @Test
  public void current_synchronous_throwsException_works() {
    assertThrows(
        IOException.class,
        () ->
            profileTestCrossProfileType
                .current()
                .identityStringMethodThrowsIOException(STRING));
  }

  @Test
  public void current_synchronous_declaresMultipleExceptions_throwsException_works() {
    assertThrows(
        SQLException.class,
        () ->
            profileTestCrossProfileType
                .current()
                .identityStringMethodDeclaresIOExceptionThrowsSQLException(STRING));
  }

  @Test
  public void current_async_nonSimpleCallback_works() {
    nonSimpleCallbackListener.callbackMethodCalls = 0;
    profileTestCrossProfileType
        .current()
        .asyncMethodWithNonSimpleCallback(nonSimpleCallbackListener, STRING, STRING2);

    assertThat(nonSimpleCallbackListener.callbackMethodCalls).isEqualTo(1);
    assertThat(nonSimpleCallbackListener.string1CallbackValue).isEqualTo(STRING);
    assertThat(nonSimpleCallbackListener.string2CallbackValue).isEqualTo(STRING2);
  }

  @Test
  public void current_async_nonSimpleCallback_secondMethod_works() {
    profileTestCrossProfileType
        .current()
        .asyncMethodWithNonSimpleCallbackCallsSecondMethod(
            nonSimpleCallbackListener, STRING, STRING2);

    assertThat(nonSimpleCallbackListener.string3CallbackValue).isEqualTo(STRING);
    assertThat(nonSimpleCallbackListener.string4CallbackValue).isEqualTo(STRING2);
  }
}
