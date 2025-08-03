/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.test.wifi.nsd;

import android.content.Context;
import android.util.Log;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class NsdInstrumentationTest {
  private static final String TAG = NsdInstrumentationTest.class.getSimpleName();
  private static int deviceIdx;
  private static Context appContext;

  @BeforeClass
  public static void setup() {
    deviceIdx =
        Integer.valueOf((String) InstrumentationRegistry.getArguments().getString("position"));
    appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
  }

  @Test
  @SmallTest
  public void testNsd() throws InterruptedException {
    NsdHelper nsdHelper = new NsdHelper(appContext);

    long startTime = System.currentTimeMillis();

    if (deviceIdx == 0) {
      // server mode
      nsdHelper.serviceTest();
    } else {
      nsdHelper.discoverTest();
    }
    // TODO: After adding the connection to end the tests on two device at the same time, execute
    // the tests repeatedly to make sure advertisement and discovery are stopped correctly.
    long duration = System.currentTimeMillis() - startTime;
    Log.d(TAG, "Duration: " + duration + " milliseconds");
  }
}
