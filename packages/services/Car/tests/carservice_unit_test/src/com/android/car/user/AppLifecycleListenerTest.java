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

package com.android.car.user;

import static com.android.car.internal.common.CommonConstants.USER_LIFECYCLE_EVENT_TYPE_STARTING;
import static com.android.car.internal.common.CommonConstants.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;
import static com.android.car.internal.common.CommonConstants.USER_LIFECYCLE_EVENT_TYPE_UNLOCKING;

import static com.google.common.truth.Truth.assertThat;

import android.car.ICarResultReceiver;
import android.car.user.CarUserManager.UserLifecycleEvent;
import android.car.user.UserLifecycleEventFilter;
import android.os.Bundle;
import android.os.UserHandle;

import com.android.car.user.AppLifecycleListener.BinderDeathCallback;

import org.junit.Test;

public final class AppLifecycleListenerTest {

    private static final UserHandle USER111 = UserHandle.of(111);
    private static final UserHandle USER112 = UserHandle.of(112);
    private static final UserLifecycleEvent USER111_STARTING_EVENT = new UserLifecycleEvent(
            USER_LIFECYCLE_EVENT_TYPE_STARTING, USER111.getIdentifier());
    private static final UserLifecycleEvent USER112_UNLOCKING_EVENT = new UserLifecycleEvent(
            USER_LIFECYCLE_EVENT_TYPE_UNLOCKING, USER112.getIdentifier());
    private static final UserLifecycleEvent USER111_TO_112_SWITCHING_EVENT = new UserLifecycleEvent(
            USER_LIFECYCLE_EVENT_TYPE_SWITCHING, USER111.getIdentifier(), USER112.getIdentifier());
    private static final UserLifecycleEventFilter STARTING_EVENT_FILTER =
            new UserLifecycleEventFilter.Builder().addEventType(USER_LIFECYCLE_EVENT_TYPE_STARTING)
                    .build();
    private static final UserLifecycleEventFilter USER111_FILTER =
            new UserLifecycleEventFilter.Builder().addUser(USER111).build();

    private static class DummyReceiver extends ICarResultReceiver.Stub {
        @Override
        public void send(int resultCode, Bundle resultData) {}
    }

    private BinderDeathCallback mDummyCallback = unused -> { /* do nothing */ };

    @Test
    public void testAppLifecycleListener_constructedWithNullFilter() {
        AppLifecycleListener listener = new AppLifecycleListener(111, "package",
                new DummyReceiver(), /* filter= */null, mDummyCallback);

        assertThat(listener.getFilters()).isNull();
    }

    @Test
    public void testAppLifecycleListener_constructedWithNonNullFilter() {
        AppLifecycleListener listener = new AppLifecycleListener(111, "package",
                new DummyReceiver(), STARTING_EVENT_FILTER, mDummyCallback);

        assertThat(listener.getFilters()).containsExactly(STARTING_EVENT_FILTER);
    }

    @Test
    public void testAddFilter_addNonNullToNonNullList() {
        AppLifecycleListener listener = new AppLifecycleListener(111, "package",
                new DummyReceiver(), STARTING_EVENT_FILTER, mDummyCallback);

        listener.addFilter(USER111_FILTER);

        assertThat(listener.getFilters()).containsExactly(STARTING_EVENT_FILTER, USER111_FILTER);
    }

    @Test
    public void testAddFilter_addNonNullToNullList() {
        AppLifecycleListener listener = new AppLifecycleListener(111, "package",
                new DummyReceiver(), /* filter= */null, mDummyCallback);

        listener.addFilter(USER111_FILTER);

        assertThat(listener.getFilters()).isNull();
    }

    @Test
    public void testAddFilter_addNullToNonNullList() {
        AppLifecycleListener listener = new AppLifecycleListener(111, "package",
                new DummyReceiver(), STARTING_EVENT_FILTER, mDummyCallback);

        listener.addFilter(null);

        assertThat(listener.getFilters()).isNull();
    }

    @Test
    public void testApplyFilters_nullList_returnsTrue() {
        AppLifecycleListener listener = new AppLifecycleListener(111, "package",
                new DummyReceiver(), /* filter= */null, mDummyCallback);

        assertThat(listener.applyFilters(USER111_STARTING_EVENT)).isTrue();
        assertThat(listener.applyFilters(USER112_UNLOCKING_EVENT)).isTrue();
        assertThat(listener.applyFilters(USER111_TO_112_SWITCHING_EVENT)).isTrue();
    }

    @Test
    public void testApplyFilters_nonNullList() {
        AppLifecycleListener listener = new AppLifecycleListener(111, "package",
                new DummyReceiver(), STARTING_EVENT_FILTER, mDummyCallback);
        listener.addFilter(USER111_FILTER);

        assertThat(listener.applyFilters(USER111_STARTING_EVENT)).isTrue();
        assertThat(listener.applyFilters(USER111_TO_112_SWITCHING_EVENT)).isTrue();
        assertThat(listener.applyFilters(USER112_UNLOCKING_EVENT)).isFalse();
    }
}
