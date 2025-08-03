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
import static com.google.common.truth.Truth.assertThat;

import android.app.Application;
import android.app.Service;
import android.os.Build.VERSION_CODES;
import android.os.IBinder;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.Profile;
import com.google.android.enterprise.connectedapps.RobolectricTestUtilities;
import com.google.android.enterprise.connectedapps.TestExceptionCallbackListener;
import com.google.android.enterprise.connectedapps.TestScheduledExecutorService;
import com.google.android.enterprise.connectedapps.TestStringCallbackListenerImpl;
import com.google.android.enterprise.connectedapps.TestStringCallbackListenerMultiImpl;
import com.google.android.enterprise.connectedapps.testapp.configuration.TestApplication;
import com.google.android.enterprise.connectedapps.testapp.connector.FakeTestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.types.FakeProfileNonInstantiableTestCrossProfileType;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileNonInstantiableTestCrossProfileType;
import com.google.android.enterprise.connectedapps.testing.annotations.CrossProfileTest;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests specific to static @CrossProfile methods.
 *
 * <p>These tests are located here rather than with e.g. {@link BothProfilesAsyncTest} etc. because
 * they will be used to also test that static methods can be used without instantiating the type.
 */
@RunWith(RobolectricTestRunner.class)
@Config(minSdk = VERSION_CODES.O)
@CrossProfileTest(configuration = TestApplication.class)
public final class StaticTest {

  private static final String STRING = "string";

  private final Application context = ApplicationProvider.getApplicationContext();
  private final TestScheduledExecutorService scheduledExecutorService =
      new TestScheduledExecutorService();
  private final TestProfileConnector testProfileConnector =
      TestProfileConnector.create(context, scheduledExecutorService);
  private final RobolectricTestUtilities testUtilities =
      new RobolectricTestUtilities(testProfileConnector, scheduledExecutorService);
  private final ProfileNonInstantiableTestCrossProfileType type =
      ProfileNonInstantiableTestCrossProfileType.create(testProfileConnector);

  private final FakeTestProfileConnector fakeConnector = new FakeTestProfileConnector(context);
  private final FakeProfileNonInstantiableTestCrossProfileType fakeType =
      FakeProfileNonInstantiableTestCrossProfileType.builder().connector(fakeConnector).build();

  private final TestStringCallbackListenerImpl stringCallbackListener =
      new TestStringCallbackListenerImpl();
  private final TestStringCallbackListenerMultiImpl stringCallbackMultiListener =
      new TestStringCallbackListenerMultiImpl();
  private final TestExceptionCallbackListener exceptionCallbackListener =
      new TestExceptionCallbackListener();

  @Before
  public void setUp() {
    Service profileAwareService = Robolectric.setupService(TestApplication.getService());
    testUtilities.initTests();
    IBinder binder = profileAwareService.onBind(/* intent= */ null);
    testUtilities.setBinding(binder, TestProfileConnector.class.getName());
    testUtilities.createWorkUser();
    testUtilities.turnOnWorkProfile();
    testUtilities.setRunningOnPersonalProfile();
    testUtilities.setRequestsPermissions(INTERACT_ACROSS_USERS);
    testUtilities.grantPermissions(INTERACT_ACROSS_USERS);
    testUtilities.addDefaultConnectionHolderAndWait();
  }

  @Test
  public void staticCrossProfileMethod_blocking_other_works() throws Exception {
    assertThat(type.other().staticIdentityStringMethod(STRING)).isEqualTo(STRING);
  }

  @Test
  public void staticCrossProfileMethod_blocking_current_works() {
    assertThat(type.current().staticIdentityStringMethod(STRING)).isEqualTo(STRING);
  }

  @Test
  public void staticCrossProfileMethod_blocking_both_works() {
    Map<Profile, String> result = type.both().staticIdentityStringMethod(STRING);

    assertThat(result).containsKey(testProfileConnector.utils().getCurrentProfile());
    assertThat(result).containsKey(testProfileConnector.utils().getOtherProfile());
  }

  @Test
  public void staticCrossProfileMethod_fake_blocking_other_works() throws Exception {
    fakeConnector.turnOnWorkProfile();
    fakeConnector.addConnectionHolder(this);

    assertThat(fakeType.other().staticIdentityStringMethod(STRING)).isEqualTo(STRING);
  }

  @Test
  public void staticCrossProfileMethod_fake_blocking_current_works() {
    assertThat(fakeType.current().staticIdentityStringMethod(STRING)).isEqualTo(STRING);
  }

  @Test
  public void staticCrossProfileMethod_fake_blocking_both_works() {
    fakeConnector.turnOnWorkProfile();
    fakeConnector.addConnectionHolder(this);

    Map<Profile, String> result = fakeType.both().staticIdentityStringMethod(STRING);

    assertThat(result).containsKey(testProfileConnector.utils().getCurrentProfile());
    assertThat(result).containsKey(testProfileConnector.utils().getOtherProfile());
  }

  @Test
  public void staticCrossProfileMethod_async_other_works() {
    type.other()
        .staticAsyncIdentityStringMethod(STRING, stringCallbackListener, exceptionCallbackListener);

    assertThat(stringCallbackListener.stringCallbackValue).isEqualTo(STRING);
  }

  @Test
  public void staticCrossProfileMethod_async_current_works() {
    type.current().staticAsyncIdentityStringMethod(STRING, stringCallbackListener);

    assertThat(stringCallbackListener.stringCallbackValue).isEqualTo(STRING);
  }

  @Test
  public void staticCrossProfileMethod_async_both_works() {
    type.both().staticAsyncIdentityStringMethod(STRING, stringCallbackMultiListener);

    Map<Profile, String> result = stringCallbackMultiListener.stringCallbackValues;
    assertThat(result).containsKey(testProfileConnector.utils().getCurrentProfile());
    assertThat(result).containsKey(testProfileConnector.utils().getOtherProfile());
  }

  @Test
  public void staticCrossProfileMethod_fake_async_other_works() {
    fakeConnector.turnOnWorkProfile();

    fakeType
        .other()
        .staticAsyncIdentityStringMethod(STRING, stringCallbackListener, exceptionCallbackListener);

    assertThat(stringCallbackListener.stringCallbackValue).isEqualTo(STRING);
  }

  @Test
  public void staticCrossProfileMethod_fake_async_current_works() {
    fakeType.current().staticAsyncIdentityStringMethod(STRING, stringCallbackListener);

    assertThat(stringCallbackListener.stringCallbackValue).isEqualTo(STRING);
  }

  @Test
  public void staticCrossProfileMethod_fake_async_both_works() {
    fakeConnector.turnOnWorkProfile();

    fakeType.both().staticAsyncIdentityStringMethod(STRING, stringCallbackMultiListener);

    Map<Profile, String> result = stringCallbackMultiListener.stringCallbackValues;
    assertThat(result).containsKey(testProfileConnector.utils().getCurrentProfile());
    assertThat(result).containsKey(testProfileConnector.utils().getOtherProfile());
  }

  @Test
  public void staticCrossProfileMethod_future_other_works() throws Exception {
    ListenableFuture<String> result = type.other().staticFutureIdentityStringMethod(STRING);

    assertThat(result.get()).isEqualTo(STRING);
  }

  @Test
  public void staticCrossProfileMethod_future_current_works() throws Exception {
    ListenableFuture<String> result = type.current().staticFutureIdentityStringMethod(STRING);

    assertThat(result.get()).isEqualTo(STRING);
  }

  @Test
  public void staticCrossProfileMethod_future_both_works() throws Exception {
    ListenableFuture<Map<Profile, String>> resultFuture =
        type.both().staticFutureIdentityStringMethod(STRING);

    Map<Profile, String> result = resultFuture.get();
    assertThat(result).containsKey(testProfileConnector.utils().getCurrentProfile());
    assertThat(result).containsKey(testProfileConnector.utils().getOtherProfile());
  }

  @Test
  public void staticCrossProfileMethod_fake_future_other_works() throws Exception {
    fakeConnector.turnOnWorkProfile();

    ListenableFuture<String> result = fakeType.other().staticFutureIdentityStringMethod(STRING);

    assertThat(result.get()).isEqualTo(STRING);
  }

  @Test
  public void staticCrossProfileMethod_fake_future_current_works() throws Exception {
    ListenableFuture<String> result = fakeType.current().staticFutureIdentityStringMethod(STRING);

    assertThat(result.get()).isEqualTo(STRING);
  }

  @Test
  public void staticCrossProfileMethod_fake_future_both_works() throws Exception {
    fakeConnector.turnOnWorkProfile();

    ListenableFuture<Map<Profile, String>> resultFuture =
        fakeType.both().staticFutureIdentityStringMethod(STRING);

    Map<Profile, String> result = resultFuture.get();
    assertThat(result).containsKey(testProfileConnector.utils().getCurrentProfile());
    assertThat(result).containsKey(testProfileConnector.utils().getOtherProfile());
  }
}
