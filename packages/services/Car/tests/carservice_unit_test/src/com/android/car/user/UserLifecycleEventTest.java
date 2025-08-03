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
package com.android.car.user;

import static com.google.common.truth.Truth.assertThat;

import android.car.user.CarUserManager.UserLifecycleEvent;
import android.os.UserHandle;

import org.junit.Test;

public final class UserLifecycleEventTest {

    private static final int EVENT_TYPE = 42;
    private static final int FROM_USER_ID = 10;
    private static final int TO_USER_ID = 20;

    @Test
    public void testFullConstructor() {
        UserLifecycleEvent event = new UserLifecycleEvent(EVENT_TYPE, FROM_USER_ID, TO_USER_ID);

        assertThat(event.getEventType()).isEqualTo(EVENT_TYPE);
        assertThat(event.getUserId()).isEqualTo(TO_USER_ID);
        assertThat(event.getUserHandle().getIdentifier()).isEqualTo(TO_USER_ID);
        assertThat(event.getPreviousUserId()).isEqualTo(FROM_USER_ID);
        assertThat(event.getPreviousUserHandle().getIdentifier()).isEqualTo(FROM_USER_ID);
    }

    @Test
    public void testAlternativeConstructor() {
        UserLifecycleEvent event = new UserLifecycleEvent(EVENT_TYPE, TO_USER_ID);

        assertThat(event.getEventType()).isEqualTo(EVENT_TYPE);
        assertThat(event.getUserId()).isEqualTo(TO_USER_ID);
        assertThat(event.getUserHandle().getIdentifier()).isEqualTo(TO_USER_ID);
        assertThat(event.getPreviousUserId()).isEqualTo(UserHandle.USER_NULL);
        assertThat(event.getPreviousUserHandle()).isNull();
    }

    @Test
    public void testEquals_true() {
        UserLifecycleEvent event1 = new UserLifecycleEvent(EVENT_TYPE, FROM_USER_ID, TO_USER_ID);
        UserLifecycleEvent event2 = new UserLifecycleEvent(EVENT_TYPE, FROM_USER_ID, TO_USER_ID);

        assertThat(event1.equals(event2)).isTrue();
    }

    @Test
    public void testEquals_differentEventType_false() {
        UserLifecycleEvent event1 = new UserLifecycleEvent(EVENT_TYPE, FROM_USER_ID, TO_USER_ID);
        UserLifecycleEvent event2 = new UserLifecycleEvent(EVENT_TYPE + 1,
                FROM_USER_ID, TO_USER_ID);

        assertThat(event1.equals(event2)).isFalse();
    }

    @Test
    public void testEquals_differentFromUserId_false() {
        UserLifecycleEvent event1 = new UserLifecycleEvent(EVENT_TYPE, FROM_USER_ID, TO_USER_ID);
        UserLifecycleEvent event2 = new UserLifecycleEvent(EVENT_TYPE,
                FROM_USER_ID + 1, TO_USER_ID);

        assertThat(event1.equals(event2)).isFalse();
    }

    @Test
    public void testEquals_differentToUserId_false() {
        UserLifecycleEvent event1 = new UserLifecycleEvent(EVENT_TYPE, FROM_USER_ID, TO_USER_ID);
        UserLifecycleEvent event2 = new UserLifecycleEvent(EVENT_TYPE,
                FROM_USER_ID, TO_USER_ID + 1);

        assertThat(event1.equals(event2)).isFalse();
    }

    @Test
    public void testEquals_null_false() {
        UserLifecycleEvent event1 = new UserLifecycleEvent(EVENT_TYPE, FROM_USER_ID, TO_USER_ID);

        assertThat(event1.equals(null)).isFalse();
    }

    @Test
    public void testHashCode() {
        UserLifecycleEvent event1 = new UserLifecycleEvent(EVENT_TYPE, FROM_USER_ID, TO_USER_ID);
        UserLifecycleEvent event2 = new UserLifecycleEvent(EVENT_TYPE, FROM_USER_ID, TO_USER_ID);

        assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
    }
}
