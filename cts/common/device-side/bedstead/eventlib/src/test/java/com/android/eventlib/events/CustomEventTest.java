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

package com.android.eventlib.events;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import com.android.bedstead.nene.TestApis;
import com.android.eventlib.EventLogs;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CustomEventTest {

    // TODO: We need a standard pattern for testing that events log correctly cross-process
    // (when within the process serialization never happens)

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final String TAG_1 = "TAG_1";
    private static final String TAG_2 = "TAG_2";
    private static final String DATA_1 = "DATA_1";
    private static final String DATA_2 = "DATA_2";

    // Not used for Events
    private static final String FIELD_NAME = "";

    @Before
    public void setUp() {
        EventLogs.resetLogs();
    }

    @Test
    public void whereTag_works() {
        CustomEvent.logger(sContext)
                .setTag(TAG_1)
                .log();

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(sContext.getPackageName())
                .whereTag().isEqualTo(TAG_1);

        assertThat(eventLogs.poll().tag()).isEqualTo(TAG_1);
    }

    @Test
    public void whereTag_skipsNonMatching() {
        CustomEvent.logger(sContext)
                .setTag(TAG_1)
                .log();
        CustomEvent.logger(sContext)
                .setTag(TAG_2)
                .log();

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(sContext.getPackageName())
                .whereTag().isEqualTo(TAG_2);

        assertThat(eventLogs.poll().tag()).isEqualTo(TAG_2);
    }

    @Test
    public void whereData_works() {
        CustomEvent.logger(sContext)
                .setData(DATA_1)
                .log();

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(sContext.getPackageName())
                .whereData().isEqualTo(DATA_1);

        assertThat(eventLogs.poll().data()).isEqualTo(DATA_1);
    }

    @Test
    public void whereData_skipsNonMatching() {
        CustomEvent.logger(sContext)
                .setData(DATA_1)
                .log();
        CustomEvent.logger(sContext)
                .setData(DATA_2)
                .log();

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(sContext.getPackageName())
                .whereData().isEqualTo(DATA_2);

        assertThat(eventLogs.poll().data()).isEqualTo(DATA_2);
    }

    @Test
    public void describeQuery_onlyIncludingPackage_isCorrect() {
        CustomEvent.CustomEventQuery customEvent = CustomEvent.queryPackage("PACKAGE");

        assertThat(customEvent.describeQuery(FIELD_NAME))
                .isEqualTo("{type=CustomEvent, packageName=PACKAGE}");
    }

    @Test
    public void describeQuery_includesTag_isCorrect() {
        CustomEvent.CustomEventQuery customEvent = CustomEvent.queryPackage("PACKAGE")
                .whereTag().isEqualTo("TAG");

        assertThat(customEvent.describeQuery(FIELD_NAME))
                .isEqualTo("{type=CustomEvent, packageName=PACKAGE, tag=\"TAG\"}");
    }

    @Test
    public void describeQuery_includesData_isCorrect() {
        CustomEvent.CustomEventQuery customEvent = CustomEvent.queryPackage("PACKAGE")
                .whereData().isEqualTo("DATA");

        assertThat(customEvent.describeQuery(FIELD_NAME))
                .isEqualTo("{type=CustomEvent, packageName=PACKAGE, data=DATA}");
    }

    @Test
    public void describeQuery_includesAllOptions_isCorrect() {
        CustomEvent.CustomEventQuery customEvent = CustomEvent.queryPackage("PACKAGE")
                .whereTag().isEqualTo("TAG")
                .whereData().isEqualTo("DATA");

        assertThat(customEvent.describeQuery(FIELD_NAME))
                .isEqualTo("{type=CustomEvent, packageName=PACKAGE, tag=\"TAG\", data=DATA}");
    }
}
