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

import com.google.android.enterprise.connectedapps.TestStringCrossProfileCallback;
import com.google.android.enterprise.connectedapps.internal.Bundler;
import com.google.android.enterprise.connectedapps.testapp.Profile_TestStringCallbackListener_Receiver;
import com.google.android.enterprise.connectedapps.testapp.types.TestCrossProfileType_Bundler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class CrossProfileCallbackReceiverTest {

  private static final String STRING = "String";

  private final TestStringCrossProfileCallback callback = new TestStringCrossProfileCallback();
  private final Bundler bundler = new TestCrossProfileType_Bundler();
  private final Profile_TestStringCallbackListener_Receiver receiver =
      new Profile_TestStringCallbackListener_Receiver(callback, bundler);

  @Test
  public void asyncCallbackListenerReceiver_calls() {
    receiver.stringCallback(STRING);

    assertThat(callback.lastReceivedMethodIdentifier).isNotEqualTo(-1); // Has been called
  }

  @Test
  public void asyncCallbackListenerReceiver_bundlesParams() {
    receiver.stringCallback(STRING);

    assertThat(callback.lastReceivedMethodValueParam).isEqualTo(STRING);
  }
}
