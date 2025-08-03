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

package android.device.collectors;

import android.device.collectors.annotations.OptionClass;
import android.os.Bundle;
import androidx.annotation.VisibleForTesting;
import com.android.helpers.PinnerHelper;

@OptionClass(alias = "pinner-collector")
public class PinnerSnapshotListener extends BaseCollectionListener<String> {
  private PinnerHelper mPinnerHelper = new PinnerHelper();

  public PinnerSnapshotListener() { createHelperInstance(mPinnerHelper); }

  /**
   * Constructor to simulate receiving the instrumentation arguments. Should not
   * be used except for testing.
   */
  @VisibleForTesting
  public PinnerSnapshotListener(Bundle args, PinnerHelper helper) {
    super(args, helper);
    mPinnerHelper = helper;
    createHelperInstance(mPinnerHelper);
  }
}
