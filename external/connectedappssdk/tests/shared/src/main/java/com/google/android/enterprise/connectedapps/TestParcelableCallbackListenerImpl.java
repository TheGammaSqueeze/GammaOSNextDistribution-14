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
package com.google.android.enterprise.connectedapps;

import android.os.Parcelable;
import com.google.android.enterprise.connectedapps.testapp.TestParcelableCallbackListener;

public class TestParcelableCallbackListenerImpl implements TestParcelableCallbackListener {

  public int callbackMethodCalls = 0;
  public Parcelable parcelableCallbackValue;

  @Override
  public void parcelableCallback(Parcelable s) {
    callbackMethodCalls++;
    parcelableCallbackValue = s;
  }
}
