/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.helloactivitywithr8;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

/**
 * A simple application compiled with R8.
 *
 * <p>Adapted from development/samples/HelloActivity.
 */
public class HelloActivityWithR8 extends Activity {

  /** Getter method that will be inlined by R8. */
  private View getView() {
    return getLayoutInflater().inflate(R.layout.hello_activity, null);
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    View view = getView();
    setContentView(view);
  }
}
