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

import static com.google.common.truth.Truth.assertThat;

import com.google.android.enterprise.connectedapps.TestExceptionCallbackListener;
import com.google.android.enterprise.connectedapps.TestICrossProfileCallback;
import com.google.android.enterprise.connectedapps.TestStringCallbackListenerImpl;
import com.google.android.enterprise.connectedapps.internal.Bundler;
import com.google.android.enterprise.connectedapps.testapp.Profile_TestStringCallbackListener_Receiver;
import com.google.android.enterprise.connectedapps.testapp.Profile_TestStringCallbackListener_Sender;
import com.google.android.enterprise.connectedapps.testapp.TestStringCallbackListener;
import com.google.android.enterprise.connectedapps.testapp.types.TestCrossProfileType_Bundler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Test the generated _Sender class for {@link TestStringCallbackListener}.
 *
 * <p>This tests indirectly by creating a _Receiver instance (tested in {@link
 * CrossProfileCallbackReceiverTest}) and confirming that calls are passed through. This is because
 * the {@code methodIdentifier} is unpredictable.
 */
@RunWith(RobolectricTestRunner.class)
public class CrossProfileCallbackSenderTest {

  private static final String STRING = "String";

  private final TestExceptionCallbackListener exceptionCallback =
      new TestExceptionCallbackListener();
  private final TestStringCallbackListenerImpl callback = new TestStringCallbackListenerImpl();
  private final Bundler bundler = new TestCrossProfileType_Bundler();
  private final Profile_TestStringCallbackListener_Sender sender =
      new Profile_TestStringCallbackListener_Sender(callback, exceptionCallback, bundler);
  private final Profile_TestStringCallbackListener_Receiver receiver =
      new Profile_TestStringCallbackListener_Receiver(
          new TestICrossProfileCallback(sender), bundler);

  @Test
  public void asyncCallbackSender_routesCalls() {
    receiver.stringCallback(STRING);

    assertThat(callback.callbackMethodCalls).isEqualTo(1);
  }

  @Test
  public void internalCallbackSender_unbundlesParams() {
    receiver.stringCallback(STRING);

    assertThat(callback.stringCallbackValue).isEqualTo(STRING);
  }
}
