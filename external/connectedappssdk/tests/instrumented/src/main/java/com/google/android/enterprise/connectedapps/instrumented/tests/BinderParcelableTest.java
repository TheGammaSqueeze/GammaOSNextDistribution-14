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

import static com.google.common.truth.Truth.assertThat;

import android.app.Application;
import android.os.Parcelable;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import com.google.android.enterprise.connectedapps.instrumented.utils.BlockingExceptionCallbackListener;
import com.google.android.enterprise.connectedapps.instrumented.utils.BlockingParcelableCallbackListener;
import com.google.android.enterprise.connectedapps.instrumented.utils.InstrumentedTestUtilities;
import com.google.android.enterprise.connectedapps.testapp.ParcelableContainingBinder;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileTestCrossProfileType;
import com.google.common.util.concurrent.ListenableFuture;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests regarding parcelable types which contain a binder. */
@RunWith(JUnit4.class)
public class BinderParcelableTest {
  private static final Application context = ApplicationProvider.getApplicationContext();

  private static final TestProfileConnector connector = TestProfileConnector.create(context);
  private static final InstrumentedTestUtilities utilities =
      new InstrumentedTestUtilities(context, connector);

  private final ProfileTestCrossProfileType type = ProfileTestCrossProfileType.create(connector);
  private final BlockingExceptionCallbackListener exceptionCallbackListener =
      new BlockingExceptionCallbackListener();

  private final ParcelableContainingBinder parcelableContainingBinder =
      new ParcelableContainingBinder();

  @Before
  public void setup() {
    utilities.ensureReadyForCrossProfileCalls();
  }

  @AfterClass
  public static void teardownClass() {
    utilities.ensureNoWorkProfile();
  }

  @Test
  public void parcelableContainingBinderArgumentAndReturnType_bothWork()
      throws UnavailableProfileException {
    utilities.addConnectionHolderAndWait(this);

    // Binders won't be identical
    assertThat(type.other().identityParcelableMethod(parcelableContainingBinder)).isNotNull();
  }

  @Test
  public void parcelableContainingBinderAsyncMethod_works() throws Exception {
    BlockingParcelableCallbackListener callbackListener = new BlockingParcelableCallbackListener();

    type.other()
        .asyncIdentityParcelableMethod(
            parcelableContainingBinder, callbackListener, exceptionCallbackListener);

    // Binders won't be identical
    assertThat(callbackListener.await()).isNotNull();
  }

  @Test
  public void futureParcelableContainingBinder_works() throws Exception {
    ListenableFuture<Parcelable> future =
        type.other().futureIdentityParcelableMethod(parcelableContainingBinder);

    // Binders won't be identical
    assertThat(future.get()).isNotNull();
  }
}
