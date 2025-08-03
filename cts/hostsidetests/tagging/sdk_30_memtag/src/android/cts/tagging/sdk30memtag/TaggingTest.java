/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.cts.tagging.sdk30memtag;

import static org.junit.Assert.assertEquals;

import androidx.test.filters.SmallTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.runner.RunWith;
import org.junit.Rule;
import org.junit.Test;

import android.content.ComponentName;
import android.cts.tagging.TestingService;
import android.cts.tagging.ServiceRunnerActivity;
import static android.cts.tagging.Constants.*;

import com.android.compatibility.common.util.DropBoxReceiver;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class TaggingTest {
    @Rule
    public ActivityTestRule<ServiceRunnerActivity> mServiceRunnerActivityRule = new ActivityTestRule<>(
        ServiceRunnerActivity.class, false /*initialTouchMode*/, false /*launchActivity*/);

    @Test
    public void testMemtagOffService() throws Exception {
      ServiceRunnerActivity activity = mServiceRunnerActivityRule.launchActivity(null);
      activity.runService(MemtagOffService.class);
      assertEquals(TAGGING_MODE_OFF, activity.getResult());
    }

    @Test
    public void testMemtagOffIsolatedService() throws Exception {
      ServiceRunnerActivity activity = mServiceRunnerActivityRule.launchActivity(null);
      activity.runService(MemtagOffIsolatedService.class);
      assertEquals(TAGGING_MODE_OFF, activity.getResult());
    }

    @Test
    public void testMemtagOffAppZygoteService() throws Exception {
      ServiceRunnerActivity activity = mServiceRunnerActivityRule.launchActivity(null);
      activity.runService(MemtagOffAppZygoteService.class);
      assertEquals(TAGGING_MODE_OFF, activity.getResult());
    }

    @Test
    public void testExportedMemtagSyncService() throws Exception {
      ServiceRunnerActivity activity = mServiceRunnerActivityRule.launchActivity(null);
      activity.runExternalService(new ComponentName(
          "android.cts.tagging.sdk30", "android.cts.tagging.sdk30.ExportedMemtagSyncService"));
      assertEquals(TAGGING_MODE_SYNC, activity.getResult());
    }

    @Test
    public void testExportedMemtagOffService() throws Exception {
      ServiceRunnerActivity activity = mServiceRunnerActivityRule.launchActivity(null);
      activity.runExternalService(new ComponentName(
          "android.cts.tagging.sdk30", "android.cts.tagging.sdk30.ExportedMemtagOffService"));
      assertEquals(TAGGING_MODE_OFF, activity.getResult());
    }

    // Call MemtagOffService but expect it to run in Sync mode. Used to test compat feature
    // override in the hostside test.
    @Test
    public void testExportedMemtagOffService_expectSync() throws Exception {
      ServiceRunnerActivity activity = mServiceRunnerActivityRule.launchActivity(null);
      activity.runExternalService(new ComponentName(
          "android.cts.tagging.sdk30", "android.cts.tagging.sdk30.ExportedMemtagOffService"));
      assertEquals(TAGGING_MODE_SYNC, activity.getResult());
    }

    @Test
    public void testExportedMemtagSyncAppZygoteService() throws Exception {
      ServiceRunnerActivity activity = mServiceRunnerActivityRule.launchActivity(null);
      activity.runExternalService(new ComponentName(
          "android.cts.tagging.sdk30", "android.cts.tagging.sdk30.ExportedMemtagSyncAppZygoteService"));
      assertEquals(TAGGING_MODE_SYNC, activity.getResult());
    }

    @Test
    public void testExportedMemtagOffAppZygoteService() throws Exception {
      ServiceRunnerActivity activity = mServiceRunnerActivityRule.launchActivity(null);
      activity.runExternalService(new ComponentName(
          "android.cts.tagging.sdk30", "android.cts.tagging.sdk30.ExportedMemtagOffAppZygoteService"));
      assertEquals(TAGGING_MODE_OFF, activity.getResult());
    }
}
