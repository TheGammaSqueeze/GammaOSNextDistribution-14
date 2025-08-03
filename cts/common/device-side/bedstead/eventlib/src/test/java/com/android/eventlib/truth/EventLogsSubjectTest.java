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

package com.android.eventlib.truth;

import static com.android.eventlib.truth.EventLogsSubject.assertThat;
import static com.android.eventlib.truth.EventLogsSubject.eventLogs;

import static com.google.common.truth.ExpectFailure.expectFailureAbout;

import android.content.Context;

import com.android.bedstead.nene.TestApis;
import com.android.eventlib.Event;
import com.android.eventlib.EventLogs;
import com.android.eventlib.events.CustomEvent;

import com.google.common.truth.ExpectFailure;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.time.Duration;

@RunWith(JUnit4.class)
public class EventLogsSubjectTest {

    public static final Context sContext = TestApis.context().instrumentedContext();

    @Before
    public void setup() {
        EventLogs.resetLogs();
    }

    @Test
    public void eventOccurred_eventDidOccur_passes() {
        CustomEvent.logger(sContext).log();

        EventLogs<CustomEvent> eventLogs =
                CustomEvent.queryPackage(sContext.getPackageName());

        assertThat(eventLogs).eventOccurred();
    }

    // We don't test the negative of eventOccurred as it has a long timeout

    @Test
    public void eventOccurredWithin_eventDidOccur_passes() {
        CustomEvent.logger(sContext).log();

        EventLogs<CustomEvent> eventLogs =
                CustomEvent.queryPackage(sContext.getPackageName());

        assertThat(eventLogs).eventOccurredWithin(Duration.ofSeconds(5));
    }

    @Test
    public void eventOccurredWithin_eventDidNotOccur_fails() {
        EventLogs<CustomEvent> eventLogs =
                CustomEvent.queryPackage(sContext.getPackageName());

        expectFailure(
                (ExpectFailure.SimpleSubjectBuilderCallback<EventLogsSubject<CustomEvent>,
                        EventLogs<CustomEvent>>) whenTesting ->
                        whenTesting.that(eventLogs).eventOccurredWithin(Duration.ofSeconds(5)));
    }

    private static <E extends Event> AssertionError expectFailure(
            ExpectFailure.SimpleSubjectBuilderCallback<EventLogsSubject<E>, EventLogs<E>> callback) {
        return expectFailureAbout(eventLogs(), callback);
    }
}
