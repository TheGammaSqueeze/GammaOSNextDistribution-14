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
package com.google.android.enterprise.connectedapps.testing;

import static com.google.android.enterprise.connectedapps.SharedTestUtilities.assertFutureHasException;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import android.content.Context;
import android.os.Build.VERSION_CODES;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.NonSimpleCallbackListenerImpl;
import com.google.android.enterprise.connectedapps.Profile;
import com.google.android.enterprise.connectedapps.TestBooleanCallbackListenerImpl;
import com.google.android.enterprise.connectedapps.TestBooleanCallbackListenerMultiImpl;
import com.google.android.enterprise.connectedapps.TestConnectionListener;
import com.google.android.enterprise.connectedapps.TestExceptionCallbackListener;
import com.google.android.enterprise.connectedapps.TestStringCallbackListenerImpl;
import com.google.android.enterprise.connectedapps.TestVoidCallbackListenerImpl;
import com.google.android.enterprise.connectedapps.annotations.CustomProfileConnector.ProfileType;
import com.google.android.enterprise.connectedapps.exceptions.ProfileRuntimeException;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import com.google.android.enterprise.connectedapps.testapp.CustomError;
import com.google.android.enterprise.connectedapps.testapp.CustomRuntimeException;
import com.google.android.enterprise.connectedapps.testapp.configuration.TestApplication;
import com.google.android.enterprise.connectedapps.testapp.connector.FakeTestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.types.FakeProfileTestCrossProfileType;
import com.google.android.enterprise.connectedapps.testapp.types.TestCrossProfileType;
import com.google.android.enterprise.connectedapps.testing.annotations.CrossProfileTest;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@CrossProfileTest(configuration = TestApplication.class)
@RunWith(RobolectricTestRunner.class)
@Config(minSdk = VERSION_CODES.O)
public class FakeCrossProfileTypeTest {

  private static final String STRING = "String";
  private static final String STRING2 = "String2";

  private final Context context = ApplicationProvider.getApplicationContext();
  private final FakeTestProfileConnector connector = new FakeTestProfileConnector(context);
  private final TestCrossProfileType personal = new TestCrossProfileType();
  private final TestCrossProfileType work = new TestCrossProfileType();

  private final TestVoidCallbackListenerImpl voidCallbackListener =
      new TestVoidCallbackListenerImpl();
  private final TestExceptionCallbackListener exceptionCallbackListener =
      new TestExceptionCallbackListener();
  private final TestStringCallbackListenerImpl stringCallbackListener =
      new TestStringCallbackListenerImpl();
  private final TestBooleanCallbackListenerImpl booleanCallbackListener =
      new TestBooleanCallbackListenerImpl();
  private final TestBooleanCallbackListenerMultiImpl booleanMultiCallbackListener =
      new TestBooleanCallbackListenerMultiImpl();
  private final NonSimpleCallbackListenerImpl nonSimpleCallbackListener =
      new NonSimpleCallbackListenerImpl();
  private final TestConnectionListener connectionListener = new TestConnectionListener();

  FakeProfileTestCrossProfileType fakeCrossProfileType =
      FakeProfileTestCrossProfileType.builder()
          .personal(personal)
          .work(work)
          .connector(connector)
          .build();

  @Before
  public void setUp() {
    connector.setRunningOnProfile(ProfileType.PERSONAL);
    connector.turnOnWorkProfile();
    connector.addConnectionHolder(this);
    connector.addConnectionListener(connectionListener);
  }

  @After
  public void teardown() {
    connector.clearConnectionHolders();
  }

  @Test
  public void personal_callsOnPersonal() throws UnavailableProfileException {
    fakeCrossProfileType.personal().voidMethod();

    assertThat(personal.voidMethodInstanceCalls).isEqualTo(1);
  }

  @Test
  public void personal_doesNotCallOnWork() throws UnavailableProfileException {
    fakeCrossProfileType.personal().voidMethod();

    assertThat(work.voidMethodInstanceCalls).isEqualTo(0);
  }

  @Test
  public void work_callsOnWork() throws UnavailableProfileException {
    fakeCrossProfileType.work().voidMethod();

    assertThat(work.voidMethodInstanceCalls).isEqualTo(1);
  }

  @Test
  public void work_doesNotCallOnPersonal() throws UnavailableProfileException {
    fakeCrossProfileType.work().voidMethod();

    assertThat(personal.voidMethodInstanceCalls).isEqualTo(0);
  }

  @Test
  public void current_runningOnPersonal_callsPersonal() {
    connector.setRunningOnProfile(ProfileType.PERSONAL);
    fakeCrossProfileType.current().voidMethod();

    assertThat(personal.voidMethodInstanceCalls).isEqualTo(1);
  }

  @Test
  public void current_runningOnWork_callsWork() {
    connector.setRunningOnProfile(ProfileType.WORK);
    fakeCrossProfileType.current().voidMethod();

    assertThat(work.voidMethodInstanceCalls).isEqualTo(1);
  }

  @Test
  public void other_runningOnPersonal_callsWork() throws UnavailableProfileException {
    connector.setRunningOnProfile(ProfileType.PERSONAL);
    fakeCrossProfileType.other().voidMethod();

    assertThat(work.voidMethodInstanceCalls).isEqualTo(1);
  }

  @Test
  public void other_runningOnWork_callsPersonal() throws UnavailableProfileException {
    connector.setRunningOnProfile(ProfileType.WORK);
    fakeCrossProfileType.other().voidMethod();

    assertThat(personal.voidMethodInstanceCalls).isEqualTo(1);
  }

  @Test
  public void both_callsBoth() {
    fakeCrossProfileType.both().voidMethod();

    assertThat(personal.voidMethodInstanceCalls).isEqualTo(1);
    assertThat(work.voidMethodInstanceCalls).isEqualTo(1);
  }

  @Test
  public void primary_callsPrimary() throws UnavailableProfileException {
    // Work is primary for TestProfileConnector
    fakeCrossProfileType.primary().voidMethod();

    assertThat(work.voidMethodInstanceCalls).isEqualTo(1);
  }

  @Test
  public void secondary_callsSecondary() throws UnavailableProfileException {
    // Work is primary for TestProfileConnector
    fakeCrossProfileType.secondary().voidMethod();

    assertThat(personal.voidMethodInstanceCalls).isEqualTo(1);
  }

  @Test
  public void suppliers_runningOnPrimary_callsPrimaryAndSecondary() {
    // Work is primary for TestProfileConnector
    connector.setRunningOnProfile(ProfileType.WORK);

    fakeCrossProfileType.suppliers().voidMethod();

    assertThat(personal.voidMethodInstanceCalls).isEqualTo(1);
    assertThat(work.voidMethodInstanceCalls).isEqualTo(1);
  }

  @Test
  public void suppliers_runningOnSecondary_onlyCallsSecondary() {
    // Work is primary for TestProfileConnector
    connector.setRunningOnProfile(ProfileType.PERSONAL);

    fakeCrossProfileType.suppliers().voidMethod();

    assertThat(personal.voidMethodInstanceCalls).isEqualTo(1);
    assertThat(work.voidMethodInstanceCalls).isEqualTo(0);
  }

  @Test
  public void profile_specifiesCurrent_callsCurrent() throws UnavailableProfileException {
    connector.setRunningOnProfile(ProfileType.PERSONAL);

    fakeCrossProfileType.profile(connector.utils().getCurrentProfile()).voidMethod();

    assertThat(personal.voidMethodInstanceCalls).isEqualTo(1);
  }

  @Test
  public void profile_specifiesOther_callsOther() throws UnavailableProfileException {
    connector.setRunningOnProfile(ProfileType.PERSONAL);

    fakeCrossProfileType.profile(connector.utils().getOtherProfile()).voidMethod();

    assertThat(work.voidMethodInstanceCalls).isEqualTo(1);
  }

  @Test
  public void profile_specifiesPersonal_callsPersonal() throws UnavailableProfileException {
    fakeCrossProfileType.profile(connector.utils().getPersonalProfile()).voidMethod();

    assertThat(personal.voidMethodInstanceCalls).isEqualTo(1);
  }

  @Test
  public void profile_specifiesWork_callsWork() throws UnavailableProfileException {
    fakeCrossProfileType.profile(connector.utils().getWorkProfile()).voidMethod();

    assertThat(work.voidMethodInstanceCalls).isEqualTo(1);
  }

  @Test
  public void profile_specifiesPrimary_callsPrimary() throws UnavailableProfileException {
    // Work is primary for TestProfileConnector
    fakeCrossProfileType.profile(connector.utils().getPrimaryProfile()).voidMethod();

    assertThat(work.voidMethodInstanceCalls).isEqualTo(1);
  }

  @Test
  public void profile_specifiesSecondary_callsSecondary() throws UnavailableProfileException {
    // Work is primary for TestProfileConnector
    fakeCrossProfileType.profile(connector.utils().getSecondaryProfile()).voidMethod();

    assertThat(personal.voidMethodInstanceCalls).isEqualTo(1);
  }

  @Test
  public void profiles_callsSpecifiedProfiles() {
    fakeCrossProfileType
        .profiles(connector.utils().getPersonalProfile(), connector.utils().getWorkProfile())
        .voidMethod();

    assertThat(personal.voidMethodInstanceCalls).isEqualTo(1);
    assertThat(work.voidMethodInstanceCalls).isEqualTo(1);
  }

  @Test
  public void build_noPersonalSpecified_throwsIllegalStateException() {
    assertThrows(
        IllegalStateException.class,
        () -> FakeProfileTestCrossProfileType.builder().connector(connector).work(work).build());
  }

  @Test
  public void build_noWorkSpecified_throwsIllegalStateException() {
    assertThrows(
        IllegalStateException.class,
        () ->
            FakeProfileTestCrossProfileType.builder()
                .connector(connector)
                .personal(personal)
                .build());
  }

  @Test
  public void build_noConnectorSpecified_throwsIllegalStateException() {
    assertThrows(
        IllegalStateException.class,
        () -> FakeProfileTestCrossProfileType.builder().personal(personal).work(work).build());
  }

  @Test
  public void blockingCall_workProfileNotConnected_throwsUnavailableProfileException() {
    connector.disconnect();

    assertThrows(UnavailableProfileException.class, () -> fakeCrossProfileType.work().voidMethod());
  }

  @Test
  public void blockingCall_noConnectionHolders_throwsUnavailableProfileException()
      throws Exception {
    connector.clearConnectionHolders();
    connector.turnOnWorkProfile();
    fakeCrossProfileType.other().listenableFutureVoidMethod().get(); // Force connection

    assertThrows(
        UnavailableProfileException.class, () -> fakeCrossProfileType.other().voidMethod());
  }

  @Test
  public void asyncCall_workProfileAvailableButNotConnected_works() {
    connector.turnOnWorkProfile();
    connector.disconnect();

    fakeCrossProfileType.work().asyncVoidMethod(voidCallbackListener, exceptionCallbackListener);

    assertThat(voidCallbackListener.callbackMethodCalls).isEqualTo(1);
    assertThat(exceptionCallbackListener.lastException()).isNull();
  }

  @Test
  public void asyncCall_notConnected_connects() {
    connector.turnOnWorkProfile();
    connector.disconnect();
    connectionListener.resetConnectionChangedCount();

    fakeCrossProfileType.work().asyncVoidMethod(voidCallbackListener, exceptionCallbackListener);

    assertThat(connectionListener.connectionChangedCount()).isEqualTo(1);
    assertThat(connector.isConnected()).isTrue();
  }

  @Test
  public void asyncCall_workProfileUnavailable_callsWithUnavailableProfileException() {
    connector.removeWorkProfile();

    fakeCrossProfileType.work().asyncVoidMethod(voidCallbackListener, exceptionCallbackListener);

    assertThat(voidCallbackListener.callbackMethodCalls).isEqualTo(0);
    assertThat(exceptionCallbackListener.lastException())
        .isInstanceOf(UnavailableProfileException.class);
  }

  @Test
  public void futureCall_workProfileAvailableButNotConnected_works()
      throws ExecutionException, InterruptedException {
    connector.turnOnWorkProfile();
    connector.disconnect();

    ListenableFuture<Void> future = fakeCrossProfileType.work().listenableFutureVoidMethod();

    assertThat(future.get()).isNull();
  }

  @Test
  public void futureCall_notConnected_connects() {
    connector.turnOnWorkProfile();
    connector.disconnect();
    connectionListener.resetConnectionChangedCount();

    ListenableFuture<Void> unusedFuture = fakeCrossProfileType.work().listenableFutureVoidMethod();

    assertThat(connectionListener.connectionChangedCount()).isEqualTo(1);
    assertThat(connector.isConnected()).isTrue();
  }

  @Test
  public void futureCall_workProfileUnavailable_setsUnavailableProfileException() {
    connector.removeWorkProfile();

    ListenableFuture<Void> future = fakeCrossProfileType.work().listenableFutureVoidMethod();

    assertFutureHasException(future, UnavailableProfileException.class);
  }

  @Test
  public void blockingCallOnBoth_notConnected_onlyCallsOnCurrent() {
    connector.setRunningOnProfile(ProfileType.PERSONAL);
    connector.turnOnWorkProfile();
    connector.disconnect();

    fakeCrossProfileType.both().voidMethod();

    assertThat(personal.voidMethodInstanceCalls).isEqualTo(1);
    assertThat(work.voidMethodInstanceCalls).isEqualTo(0);
  }

  @Test
  public void asyncCallOnBoth_notAvailable_onlyCallsOnCurrent() {
    connector.setRunningOnProfile(ProfileType.PERSONAL);
    connector.turnOffWorkProfile();

    fakeCrossProfileType
        .both()
        .asyncVoidMethod(
            () -> {
              // Ignored
            });

    assertThat(personal.voidMethodInstanceCalls).isEqualTo(1);
    assertThat(work.voidMethodInstanceCalls).isEqualTo(0);
  }

  @Test
  public void futureCallOnBoth_notAvailable_onlyCallsOnCurrent() {
    connector.setRunningOnProfile(ProfileType.PERSONAL);
    connector.turnOffWorkProfile();

    ListenableFuture<Map<Profile, Void>> unusedFuture =
        fakeCrossProfileType.both().listenableFutureVoidMethod();

    assertThat(personal.voidMethodInstanceCalls).isEqualTo(1);
    assertThat(work.voidMethodInstanceCalls).isEqualTo(0);
  }

  @Test
  public void current_synchronous_throwsRuntimeException_runtimeExceptionIsThrown() {
    assertThrows(
        CustomRuntimeException.class,
        () -> {
          fakeCrossProfileType.current().methodWhichThrowsRuntimeException();
        });
  }

  @Test
  public void current_synchronous_throwsError_errorIsThrown() {
    assertThrows(CustomError.class, () -> fakeCrossProfileType.current().methodWhichThrowsError());
  }

  @Test
  public void other_synchronous_throwsRuntimeException_exceptionIsWrapped()
      throws UnavailableProfileException {
    try {
      fakeCrossProfileType.other().methodWhichThrowsRuntimeException();
      fail();
    } catch (ProfileRuntimeException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CustomRuntimeException.class);
    }
  }

  @Test
  public void other_synchronous_throwsError_exceptionIsWrapped()
      throws UnavailableProfileException {
    try {
      fakeCrossProfileType.other().methodWhichThrowsError();
      fail();
    } catch (ProfileRuntimeException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CustomError.class);
    }
  }

  @Test
  public void current_async_throwsRuntimeException_runtimeExceptionIsThrown() {
    assertThrows(
        CustomRuntimeException.class,
        () -> {
          fakeCrossProfileType
              .current()
              .asyncStringMethodWhichThrowsRuntimeException(/* callback= */ null);
        });
  }

  @Test
  public void current_async_throwsError_errorIsThrown() {
    assertThrows(
        CustomError.class,
        () ->
            fakeCrossProfileType.current().asyncStringMethodWhichThrowsError(/* callback= */ null));
  }

  @Test
  public void other_async_throwsRuntimeException_exceptionIsWrapped() {
    try {
      fakeCrossProfileType
          .other()
          .asyncStringMethodWhichThrowsRuntimeException(
              /* callback= */ null, /* exceptionCallback= */ null);
      fail();
    } catch (ProfileRuntimeException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CustomRuntimeException.class);
    }
  }

  @Test
  public void other_async_throwsError_errorIsWrapped() {
    try {
      fakeCrossProfileType
          .other()
          .asyncStringMethodWhichThrowsError(
              /* callback= */ null, /* exceptionCallback= */ null);
      fail();
    } catch (ProfileRuntimeException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CustomError.class);
    }
  }

  @Test
  public void current_future_throwsRuntimeException_runtimeExceptionIsThrown() {
    assertThrows(
        CustomRuntimeException.class,
        () -> {
          fakeCrossProfileType.current().listenableFutureVoidMethodWhichThrowsRuntimeException();
        });
  }

  @Test
  public void current_future_throwsError_errorIsThrown() {
    assertThrows(
        CustomError.class,
        () -> fakeCrossProfileType.current().listenableFutureVoidMethodWhichThrowsError());
  }

  @Test
  public void other_future_throwsRuntimeException_exceptionIsWrapped() {
    try {
      fakeCrossProfileType.other().listenableFutureVoidMethodWhichThrowsRuntimeException();
      fail();
    } catch (ProfileRuntimeException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CustomRuntimeException.class);
    }
  }

  @Test
  public void other_future_throwsError_errorIsWrapped() {
    try {
      fakeCrossProfileType.other().listenableFutureVoidMethodWhichThrowsError();
      fail();
    } catch (ProfileRuntimeException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CustomError.class);
    }
  }

  @Test
  public void both_synchronous_throwsRuntimeException_exceptionIsThrown() {
    // Which one is thrown when both throw exceptions is not specified
    try {
      fakeCrossProfileType.both().methodWhichThrowsRuntimeException();
      fail();
    } catch (CustomRuntimeException expected) {

    } catch (ProfileRuntimeException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CustomRuntimeException.class);
    }
  }

  @Test
  public void both_synchronous_throwsError_errorIsThrown() {
    // Which one is thrown when both throw exceptions is not specified
    try {
      fakeCrossProfileType.both().methodWhichThrowsError();
      fail();
    } catch (CustomError expected) {

    } catch (ProfileRuntimeException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CustomError.class);
    }
  }

  @Test
  public void both_async_throwsRuntimeException_exceptionIsThrown() {
    // Which one is thrown when both throw exceptions is not specified
    try {
      fakeCrossProfileType
          .both()
          .asyncStringMethodWhichThrowsRuntimeException(/* callback= */ null);
      fail();
    } catch (CustomRuntimeException expected) {

    } catch (ProfileRuntimeException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CustomRuntimeException.class);
    }
  }

  @Test
  public void both_async_throwsError_errorIsThrown() {
    // Which one is thrown when both throw exceptions is not specified
    try {
      fakeCrossProfileType
          .both()
          .asyncStringMethodWhichThrowsError(/* callback= */ null);
      fail();
    } catch (CustomError expected) {

    } catch (ProfileRuntimeException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CustomError.class);
    }
  }

  @Test
  public void both_future_throwsRuntimeException_exceptionIsThrown() {
    // Which one is thrown when both throw exceptions is not specified
    try {
      fakeCrossProfileType.both().listenableFutureVoidMethodWhichThrowsRuntimeException();
      fail();
    } catch (CustomRuntimeException expected) {

    } catch (ProfileRuntimeException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CustomRuntimeException.class);
    }
  }

  @Test
  public void both_future_throwsError_errorIsThrown() {
    // Which one is thrown when both throw exceptions is not specified
    try {
      fakeCrossProfileType.both().listenableFutureVoidMethodWhichThrowsError();
      fail();
    } catch (CustomError expected) {

    } catch (ProfileRuntimeException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CustomError.class);
    }
  }

  @Test
  public void ifAvailable_synchronous_notConnected_returnsDefaultValue() {
    connector.disconnect();

    assertThat(
            fakeCrossProfileType
                .other()
                .ifAvailable()
                .identityStringMethod(STRING, /* defaultValue= */ STRING2))
        .isEqualTo(STRING2);
  }

  @Test
  public void ifAvailable_synchronousVoid_notConnected_doesNotCallMethod() {
    connector.setRunningOnProfile(ProfileType.PERSONAL);
    connector.disconnect();

    fakeCrossProfileType.other().ifAvailable().voidMethod();

    assertThat(work.voidMethodInstanceCalls).isEqualTo(0);
  }

  @Test
  public void ifAvailable_synchronous_connected_returnsCorrectValue() {
    connector.addConnectionHolder(this);

    assertThat(
            fakeCrossProfileType
                .other()
                .ifAvailable()
                .identityStringMethod(STRING, /* defaultValue= */ STRING2))
        .isEqualTo(STRING);
  }

  @Test
  public void ifAvailable_synchronousVoid_connected_callsMethod() {
    connector.addConnectionHolder(this);
    connector.setRunningOnProfile(ProfileType.PERSONAL);
    fakeCrossProfileType.other().ifAvailable().voidMethod();

    assertThat(work.voidMethodInstanceCalls).isEqualTo(1);
  }

  @Test
  public void ifAvailable_callback_notAvailable_returnsDefaultValue() {
    connector.turnOffWorkProfile();

    fakeCrossProfileType
        .other()
        .ifAvailable()
        .asyncIdentityStringMethod(STRING, stringCallbackListener, /* defaultValue= */ STRING2);

    assertThat(stringCallbackListener.stringCallbackValue).isEqualTo(STRING2);
  }

  @Test
  public void ifAvailable_voidCallback_notAvailable_callsback() {
    connector.turnOffWorkProfile();

    fakeCrossProfileType.other().ifAvailable().asyncVoidMethod(voidCallbackListener);

    assertThat(voidCallbackListener.callbackMethodCalls).isEqualTo(1);
  }

  @Test
  public void ifAvailable_callback_available_returnsCorrectValue() {
    connector.turnOnWorkProfile();

    fakeCrossProfileType
        .other()
        .ifAvailable()
        .asyncIdentityStringMethod(STRING, stringCallbackListener, /* defaultValue= */ STRING2);

    assertThat(stringCallbackListener.stringCallbackValue).isEqualTo(STRING);
  }

  @Test
  public void ifAvailable_voidCallback_available_callsMethod() {
    connector.turnOnWorkProfile();

    fakeCrossProfileType.other().ifAvailable().asyncVoidMethod(voidCallbackListener);

    assertThat(voidCallbackListener.callbackMethodCalls).isEqualTo(1);
  }

  @Test
  public void ifAvailable_future_available_returnsCorrectValue()
      throws ExecutionException, InterruptedException {
    connector.turnOnWorkProfile();

    ListenableFuture<String> future =
        fakeCrossProfileType
            .other()
            .ifAvailable()
            .listenableFutureIdentityStringMethod(STRING, STRING2);

    assertThat(future.get()).isEqualTo(STRING);
  }

  @Test
  public void ifAvailable_future_notAvailable_returnsDefaultValue()
      throws ExecutionException, InterruptedException {
    connector.turnOffWorkProfile();

    ListenableFuture<String> future =
        fakeCrossProfileType
            .other()
            .ifAvailable()
            .listenableFutureIdentityStringMethod(STRING, STRING2);

    assertThat(future.get()).isEqualTo(STRING2);
  }

  @Test
  public void contextArgument_synchronous_currentProfile_works() {
    assertThat(fakeCrossProfileType.current().isContextArgumentPassed()).isTrue();
  }

  @Test
  public void contextArgument_synchronous_otherProfile_works() throws UnavailableProfileException {
    assertThat(fakeCrossProfileType.other().isContextArgumentPassed()).isTrue();
  }

  @Test
  public void contextArgument_synchronous_both_works() {
    Map<Profile, Boolean> result = fakeCrossProfileType.both().isContextArgumentPassed();

    assertThat(result.get(connector.utils().getCurrentProfile())).isTrue();
    assertThat(result.get(connector.utils().getOtherProfile())).isTrue();
  }

  @Test
  public void contextArgument_async_currentProfile_works() {
    fakeCrossProfileType.current().asyncIsContextArgumentPassed(booleanCallbackListener);

    assertThat(booleanCallbackListener.booleanCallbackValue).isTrue();
  }

  @Test
  public void contextArgument_async_otherProfile_works() {
    fakeCrossProfileType
        .other()
        .asyncIsContextArgumentPassed(booleanCallbackListener, exceptionCallbackListener);

    assertThat(booleanCallbackListener.booleanCallbackValue).isTrue();
  }

  @Test
  public void contextArgument_async_both_works() {
    fakeCrossProfileType.both().asyncIsContextArgumentPassed(booleanMultiCallbackListener);

    Map<Profile, Boolean> result = booleanMultiCallbackListener.booleanCallbackValues;
    assertThat(result.get(connector.utils().getCurrentProfile())).isTrue();
    assertThat(result.get(connector.utils().getOtherProfile())).isTrue();
  }

  @Test
  public void contextArgument_future_currentProfile_works() throws Exception {
    ListenableFuture<Boolean> future =
        fakeCrossProfileType.current().futureIsContextArgumentPassed();

    assertThat(future.get()).isTrue();
  }

  @Test
  public void contextArgument_future_otherProfile_works() throws Exception {
    ListenableFuture<Boolean> future = fakeCrossProfileType.other().futureIsContextArgumentPassed();

    assertThat(future.get()).isTrue();
  }

  @Test
  public void contextArgument_future_both_works() throws Exception {
    ListenableFuture<Map<Profile, Boolean>> resultFuture =
        fakeCrossProfileType.both().futureIsContextArgumentPassed();

    Map<Profile, Boolean> result = resultFuture.get();
    assertThat(result.get(connector.utils().getCurrentProfile())).isTrue();
    assertThat(result.get(connector.utils().getOtherProfile())).isTrue();
  }

  @Test
  public void current_synchronous_declaresButDoesNotThrowException_works() throws Exception {
    assertThat(
            fakeCrossProfileType
                .current()
                .identityStringMethodDeclaresButDoesNotThrowIOException(STRING))
        .isEqualTo(STRING);
  }

  @Test
  public void current_synchronous_throwsException_works() {
    assertThrows(
        IOException.class,
        () ->
            fakeCrossProfileType
                .current()
                .identityStringMethodThrowsIOException(STRING));
  }

  @Test
  public void current_synchronous_declaresMultipleExceptions_throwsException_works() {
    assertThrows(
        SQLException.class,
        () ->
            fakeCrossProfileType
                .current()
                .identityStringMethodDeclaresIOExceptionThrowsSQLException(
                    STRING));
  }

  @Test
  public void other_synchronous_declaresButDoesNotThrowException_works() throws Exception {
    assertThat(
            fakeCrossProfileType
                .other()
                .identityStringMethodDeclaresButDoesNotThrowIOException(STRING))
        .isEqualTo(STRING);
  }

  @Test
  public void other_synchronous_throwsException_works() {
    assertThrows(
        IOException.class,
        () ->
            fakeCrossProfileType
                .other()
                .identityStringMethodThrowsIOException(STRING));
  }

  @Test
  public void other_synchronous_declaresMultipleExceptions_throwsException_works() {
    assertThrows(
        SQLException.class,
        () ->
            fakeCrossProfileType
                .other()
                .identityStringMethodDeclaresIOExceptionThrowsSQLException(STRING));
  }

  @Test
  public void current_async_nonSimpleCallback_works() {
    nonSimpleCallbackListener.callbackMethodCalls = 0;
    fakeCrossProfileType
        .current()
        .asyncMethodWithNonSimpleCallback(nonSimpleCallbackListener, STRING, STRING2);

    assertThat(nonSimpleCallbackListener.callbackMethodCalls).isEqualTo(1);
    assertThat(nonSimpleCallbackListener.string1CallbackValue).isEqualTo(STRING);
    assertThat(nonSimpleCallbackListener.string2CallbackValue).isEqualTo(STRING2);
  }

  @Test
  public void other_async_nonSimpleCallback_works() {
    nonSimpleCallbackListener.callbackMethodCalls = 0;
    fakeCrossProfileType
        .other()
        .asyncMethodWithNonSimpleCallback(
            nonSimpleCallbackListener, STRING, STRING2, exceptionCallbackListener);

    assertThat(nonSimpleCallbackListener.callbackMethodCalls).isEqualTo(1);
    assertThat(nonSimpleCallbackListener.string1CallbackValue).isEqualTo(STRING);
    assertThat(nonSimpleCallbackListener.string2CallbackValue).isEqualTo(STRING2);
  }

  @Test
  public void current_async_nonSimpleCallback_secondMethod_works() {
    fakeCrossProfileType
        .current()
        .asyncMethodWithNonSimpleCallbackCallsSecondMethod(
            nonSimpleCallbackListener, STRING, STRING2);

    assertThat(nonSimpleCallbackListener.string3CallbackValue).isEqualTo(STRING);
    assertThat(nonSimpleCallbackListener.string4CallbackValue).isEqualTo(STRING2);
  }

  @Test
  public void other_async_nonSimpleCallback_secondMethod_works() {
    fakeCrossProfileType
        .other()
        .asyncMethodWithNonSimpleCallbackCallsSecondMethod(
            nonSimpleCallbackListener, STRING, STRING2, exceptionCallbackListener);

    assertThat(nonSimpleCallbackListener.string3CallbackValue).isEqualTo(STRING);
    assertThat(nonSimpleCallbackListener.string4CallbackValue).isEqualTo(STRING2);
  }
}
