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

package com.android.server.deviceconfig;

import android.content.Context;
import android.app.AlarmManager;
import android.content.ContextWrapper;
import android.provider.DeviceConfig;
import android.provider.DeviceConfig.Properties;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;

@RunWith(AndroidJUnit4.class)
public class BootNotificationCreatorTest {
    Context mockContext;
    AlarmManager mockAlarmManager;

    BootNotificationCreator bootNotificationCreator;

    @Before
    public void setUp() {
        mockAlarmManager = mock(AlarmManager.class);
        mockContext = new ContextWrapper(getInstrumentation().getTargetContext()) {
            @Override
            public Object getSystemService(String name) {
                if (name.equals(Context.ALARM_SERVICE)) {
                    return mockAlarmManager;
                } else {
                   return super.getSystemService(name);
                }
            }
        };

        Map<String, Set<String>> testAconfigFlags = new HashMap<>();
        testAconfigFlags.put("test", new HashSet<>());
        testAconfigFlags.get("test").add("flag");

        bootNotificationCreator = new BootNotificationCreator(mockContext, testAconfigFlags);
    }

    @Test
    public void testNotificationScheduledWhenAconfigFlagStaged() {
        HashMap<String, String> flags = new HashMap();
        flags.put("test*flag", "value");
        Properties properties = new Properties("staged", flags);

        bootNotificationCreator.onPropertiesChanged(properties);

        Mockito.verify(mockAlarmManager).setExact(anyInt(), anyLong(), any());
    }

    @Test
    public void testNotificationNotScheduledForNonAconfigFlag() {
        HashMap<String, String> flags = new HashMap();
        flags.put("not_aconfig*flag", "value");
        Properties properties = new Properties("staged", flags);

        bootNotificationCreator.onPropertiesChanged(properties);

        Mockito.verify(mockAlarmManager, times(0)).setExact(anyInt(), anyLong(), any());
    }
}
