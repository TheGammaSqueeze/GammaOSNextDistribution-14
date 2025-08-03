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
package com.google.android.enterprise.connectedapps.instrumented.tests;

import static com.google.android.enterprise.connectedapps.SharedTestUtilities.assertFutureHasException;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.app.Application;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.exceptions.ProfileRuntimeException;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import com.google.android.enterprise.connectedapps.instrumented.utils.BlockingExceptionCallbackListener;
import com.google.android.enterprise.connectedapps.instrumented.utils.InstrumentedTestUtilities;
import com.google.android.enterprise.connectedapps.testapp.NotReallySerializableObject;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileTestCrossProfileType;
import com.google.common.util.concurrent.ListenableFuture;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests regarding types which claim to be Serializable but are not.
 *
 * <p>This requires instrumented tests as there is no way to force the serialization in Robolectric
 * tests.
 */
@RunWith(JUnit4.class)
public class NotReallySerializableTest {
  private static final Application context = ApplicationProvider.getApplicationContext();

  private static final TestProfileConnector connector = TestProfileConnector.create(context);
  private static final InstrumentedTestUtilities utilities =
      new InstrumentedTestUtilities(context, connector);

  private final ProfileTestCrossProfileType type = ProfileTestCrossProfileType.create(connector);
  private final BlockingExceptionCallbackListener exceptionCallbackListener =
      new BlockingExceptionCallbackListener();

  @Before
  public void setup() {
    utilities.ensureReadyForCrossProfileCalls();
  }

  @AfterClass
  public static void teardownClass() {
    utilities.ensureNoWorkProfile();
  }

  @Test
  public void
      synchronous_serializableObjectIsNotReallySerializable_throwsProfileRuntimeException() {
    utilities.addConnectionHolderAndWait(this);

    assertThrows(
        ProfileRuntimeException.class,
        () -> type.other().returnNotReallySerializableObjectMethod());
  }

  @Test
  public void asyncMethod_serializableObjectIsNotReallySerializable_throwsException()
      throws InterruptedException {
    type.other().asyncGetNotReallySerializableObjectMethod(object -> {}, exceptionCallbackListener);

    assertThat(exceptionCallbackListener.await()).isInstanceOf(UnavailableProfileException.class);
  }

  @Test
  public void future_serializableObjectIsNotReallySerializable_throwsException() {
    ListenableFuture<NotReallySerializableObject> future =
        type.other().futureGetNotReallySerializableObjectMethod();

    assertFutureHasException(future, UnavailableProfileException.class);
  }
}
