/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.hfpclient;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class StackEventTest {

    @Test
    public void toString_returnsInfo() {
        int type = StackEvent.EVENT_TYPE_RING_INDICATION;

        StackEvent event = new StackEvent(type);
        String expectedString = "StackEvent {type:" + StackEvent.eventTypeToString(type)
                + ", value1:" + event.valueInt + ", value2:" + event.valueInt2 + ", value3:"
                + event.valueInt3 + ", value4:" + event.valueInt4 + ", string: \""
                + event.valueString + "\"" + ", device:" + event.device + "}";

        assertThat(event.toString()).isEqualTo(expectedString);
    }

    @Test
    public void testToString_allEventFields_toStringMatchesName() throws IllegalAccessException {
        Class<StackEvent> stackEventClass = StackEvent.class;
        Field[] fields = stackEventClass.getFields();
        for (Field field : fields) {
            Class<?> t = field.getType();
            String fieldName = field.getName();
            if (fieldName.startsWith("EVENT_TYPE")) {
                if (t == int.class) {
                    int stackEventType = field.getInt(null);
                    if (fieldName.equals("EVENT_TYPE_UNKNOWN_EVENT")) {
                        assertThat(StackEvent.eventTypeToString(stackEventType)).isEqualTo(
                                "EVENT_TYPE_UNKNOWN:" + stackEventType);
                    } else {
                        String eventTypeToString = StackEvent.eventTypeToString(stackEventType);
                        assertThat(eventTypeToString).isEqualTo(fieldName);
                    }
                }
            }
        }
    }
}
