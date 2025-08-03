/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.data.events;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.content.ComponentName;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class EventStateTest {
    @Test
    public void testBuilderAndEquals() {
        ComponentName service = new ComponentName("servicePkg", "cls");
        String taskIdentifier = "taskIdentifier";
        byte[] token = new byte[] {1};

        EventState eventState1 = new EventState.Builder()
                .setTaskIdentifier(taskIdentifier)
                .setService(service)
                .setToken(token)
                .build();

        assertEquals(eventState1.getTaskIdentifier(), taskIdentifier);
        assertEquals(eventState1.getService(), service);
        assertArrayEquals(eventState1.getToken(), token);

        EventState eventState2 = new EventState.Builder(
                token, service, taskIdentifier)
                .build();
        assertEquals(eventState1, eventState2);
        assertEquals(eventState1.hashCode(), eventState2.hashCode());
    }

    @Test
    public void testBuildTwiceThrows() {
        ComponentName service = new ComponentName("servicePkg", "cls");
        String taskIdentifier = "taskIdentifier";
        byte[] token = new byte[] {1};
        EventState.Builder builder = new EventState.Builder()
                .setTaskIdentifier(taskIdentifier)
                .setService(service)
                .setToken(token);

        builder.build();
        assertThrows(IllegalStateException.class, builder::build);
    }
}
