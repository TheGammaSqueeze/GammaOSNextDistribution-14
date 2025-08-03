/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.contentcaptureservice.cts;

import static android.contentcaptureservice.cts.Assertions.assertRightActivity;
import static android.contentcaptureservice.cts.Assertions.getViewLevelEvents;
import static android.contentcaptureservice.cts.Helper.sContext;

import static com.google.common.truth.Truth.assertThat;

import android.app.PendingIntent;
import android.content.Intent;
import android.contentcaptureservice.cts.CtsContentCaptureService.Session;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import org.junit.After;
import org.junit.Test;

@AppModeFull(reason = "BlankWithTitleActivityTest is enough")
public class AppKilledTest extends AbstractContentCaptureIntegrationActivityLessTest {

    BlockingBroadcastReceiver mReceiver;

    @Test
    public void testDoIt() throws Exception {
        final CtsContentCaptureService service = enableService();
        startOutOfProcessActivity();
        OutOfProcessActivity.killOutOfProcessActivity();

        // wait Activity started
        mReceiver.awaitForBroadcast();

        final Session session = service.getOnlyFinishedSession();
        Log.v(mTag, "session id: " + session.id);
        assertRightActivity(session, session.id, OutOfProcessActivity.COMPONENT_NAME);
        assertThat(getViewLevelEvents(session.getEvents())).isEmpty();
    }

    @After
    public void cleanup() throws Exception {
        if (mReceiver != null) {
            mReceiver.unregisterQuietly();
        }
    }

    private void startOutOfProcessActivity() {
        final String actionActivityStart =
                "ACTION_ACTIVITY_ON_START_" + SystemClock.uptimeMillis();
        mReceiver = new BlockingBroadcastReceiver(sContext,
                actionActivityStart);
        mReceiver.register();
        final PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        sContext,
                        0,
                        new Intent(actionActivityStart),
                        PendingIntent.FLAG_IMMUTABLE);
        OutOfProcessActivity.launchOutOfProcessActivity(pendingIntent,
                OutOfProcessActivity.ACTION_KILL_APP_TEST,
                OutOfProcessActivity.EXTRA_ON_START_BROADCAST);
    }
}
