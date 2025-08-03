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

import com.google.android.enterprise.connectedapps.testapp.NonSimpleCallbackListener;

public class NonSimpleCallbackListenerImpl implements NonSimpleCallbackListener {

  public int callbackMethodCalls = 0;
  public String string1CallbackValue;
  public String string2CallbackValue;
  public String string3CallbackValue;
  public String string4CallbackValue;

  @Override
  public void callback(String string1, String string2) {
    string1CallbackValue = string1;
    string2CallbackValue = string2;
    callbackMethodCalls++;
  }

  @Override
  public void callback2(String string3, String string4) {
    string3CallbackValue = string3;
    string4CallbackValue = string4;
    callbackMethodCalls++;
  }
}
