/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.ambientcontext.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.PendingIntent;
import android.app.ambientcontext.AmbientContextEvent;
import android.app.ambientcontext.AmbientContextEventRequest;
import android.app.ambientcontext.AmbientContextManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.PersistableBundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.RequiredServiceRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Test the AmbientContextManager API. Run with "atest CtsAmbientContextServiceTestCases".
 */
@RunWith(AndroidJUnit4.class)
public class AmbientContextManagerTest {
    @Rule
    public final RequiredServiceRule mRequiredServiceRule =
            new RequiredServiceRule(Context.AMBIENT_CONTEXT_SERVICE);

    private Context mContext;
    private AmbientContextManager mAmbientContextManager;

    @Before
    public void setUp() throws Exception {
        mContext = getInstrumentation().getContext();
        mAmbientContextManager = (AmbientContextManager) mContext.getSystemService(
                Context.AMBIENT_CONTEXT_SERVICE);
    }

    @Test
    public void testGetEventsFromIntent() {
        Intent intent = new Intent();
        ArrayList<AmbientContextEvent> events = new ArrayList<>();
        int eventCough = AmbientContextEvent.EVENT_COUGH;
        Instant start = Instant.ofEpochMilli(1000L);
        Instant end = Instant.ofEpochMilli(3000L);
        int levelHigh = AmbientContextEvent.LEVEL_HIGH;
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(AmbientContextEvent.KEY_VENDOR_WEARABLE_EVENT_NAME, "test_event");
        AmbientContextEvent expectedEvent = new AmbientContextEvent.Builder()
                .setEventType(eventCough)
                .setStartTime(start)
                .setEndTime(end)
                .setConfidenceLevel(levelHigh)
                .setDensityLevel(levelHigh)
                .setVendorData(bundle)
                .build();
        events.add(expectedEvent);
        intent.putExtra(AmbientContextManager.EXTRA_AMBIENT_CONTEXT_EVENTS, events);

        List<AmbientContextEvent> eventsFromIntent = AmbientContextManager.getEventsFromIntent(
                intent);
        assertEquals(1, eventsFromIntent.size());

        AmbientContextEvent actualEvent = eventsFromIntent.get(0);
        assertEquals(eventCough, actualEvent.getEventType());
        assertEquals(start, actualEvent.getStartTime());
        assertEquals(end, actualEvent.getEndTime());
        assertEquals(levelHigh, actualEvent.getConfidenceLevel());
        assertEquals(levelHigh, actualEvent.getDensityLevel());
        assertEquals("test_event", actualEvent.getVendorData().getString(
                AmbientContextEvent.KEY_VENDOR_WEARABLE_EVENT_NAME));
    }

    @Test
    public void testQueryStatus_noPermission() {
        assertEquals(PackageManager.PERMISSION_DENIED, mContext.checkCallingOrSelfPermission(
                Manifest.permission.ACCESS_AMBIENT_CONTEXT_EVENT));

        int[] eventsArray = new int[] {AmbientContextEvent.EVENT_COUGH};
        Set<Integer> eventTypes = Arrays.stream(eventsArray).boxed().collect(
                Collectors.toSet());
        try {
            mAmbientContextManager.queryAmbientContextServiceStatus(eventTypes,
                    null, null);
            fail("Expected SecurityException for an app not holding"
                    + " ACCESS_AMBIENT_CONTEXT permission.");
        } catch (SecurityException e) {
            // Exception expected
        }
    }

    @Test
    public void testStartConsentActivity_noPermission() {
        int[] eventsArray = new int[] {AmbientContextEvent.EVENT_COUGH};
        Set<Integer> eventTypes = Arrays.stream(eventsArray).boxed().collect(
                Collectors.toSet());
        try {
            mAmbientContextManager.startConsentActivity(eventTypes);
            fail("Expected SecurityException for an app not holding"
                    + " ACCESS_AMBIENT_CONTEXT permission.");
        } catch (SecurityException e) {
            // Exception expected
        }
    }

    @Test
    public void testRegisterObserver_immmutablePendingIntent() {
        PersistableBundle bundle = new PersistableBundle();
        String optionKey = "TestOption";
        bundle.putBoolean(optionKey, false);
        AmbientContextEventRequest request = new AmbientContextEventRequest.Builder()
                .addEventType(AmbientContextEvent.EVENT_COUGH)
                .setOptions(bundle)
                .build();
        assertFalse(request.getOptions().getBoolean(optionKey));

        Intent intent = new Intent();
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                mContext, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        try {
            mAmbientContextManager.registerObserver(request, pendingIntent, null, null);
            fail("Expected IllegalArgumentException for a immutable PendingIntent.");
        } catch (IllegalArgumentException e) {
            // Exception expected
        }
    }

    @Test
    public void testRegisterObserver_noPermission() {
        AmbientContextEventRequest request = new AmbientContextEventRequest.Builder()
                .addEventType(AmbientContextEvent.EVENT_COUGH)
                .build();
        Intent intent = new Intent().setPackage(mContext.getPackageName());
        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(mContext, 0, intent, PendingIntent.FLAG_MUTABLE);
        try {
            mAmbientContextManager.registerObserver(request, pendingIntent, null, null);
            fail("Expected SecurityException for an app not holding"
                    + " ACCESS_AMBIENT_CONTEXT permission.");
        } catch (SecurityException e) {
            // Exception expected
        }
    }

    @Test
    public void testUnregisterObserver_noPermission() {
        try {
            mAmbientContextManager.unregisterObserver();
            fail("Expected SecurityException for an app not holding"
                    + " ACCESS_AMBIENT_CONTEXT permission.");
        } catch (SecurityException e) {
            // Exception expected
        }
    }
}
