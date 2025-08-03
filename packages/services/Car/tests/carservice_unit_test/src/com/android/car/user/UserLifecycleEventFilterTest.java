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
import static com.android.car.internal.common.CommonConstants.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED;
import static com.android.car.internal.common.CommonConstants.USER_LIFECYCLE_EVENT_TYPE_UNLOCKING;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.ActivityManager;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.user.CarUserManager.UserLifecycleEvent;
import android.car.user.UserLifecycleEventFilter;
import android.os.UserHandle;

import org.junit.Test;

public final class UserLifecycleEventFilterTest extends AbstractExtendedMockitoTestCase {

    private static final UserHandle USER111 = UserHandle.of(111);
    private static final UserHandle USER112 = UserHandle.of(112);
    private static final UserHandle USER113 = UserHandle.of(113);
    private static final UserLifecycleEvent USER111_STARTING_EVENT = new UserLifecycleEvent(
            USER_LIFECYCLE_EVENT_TYPE_STARTING, USER111.getIdentifier());
    private static final UserLifecycleEvent USER111_UNLOCKING_EVENT = new UserLifecycleEvent(
            USER_LIFECYCLE_EVENT_TYPE_UNLOCKING, USER111.getIdentifier());
    private static final UserLifecycleEvent USER112_STARTING_EVENT = new UserLifecycleEvent(
            USER_LIFECYCLE_EVENT_TYPE_STARTING, USER112.getIdentifier());
    private static final UserLifecycleEvent USER111_TO_112_SWITCHING_EVENT = new UserLifecycleEvent(
            USER_LIFECYCLE_EVENT_TYPE_SWITCHING, USER111.getIdentifier(), USER112.getIdentifier());
    private static final UserLifecycleEvent USER112_TO_113_SWITCHING_EVENT = new UserLifecycleEvent(
            USER_LIFECYCLE_EVENT_TYPE_SWITCHING, USER112.getIdentifier(), USER113.getIdentifier());

    public UserLifecycleEventFilterTest() {
        super(NO_LOG_TAGS);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder builder) {
        builder.spyStatic(ActivityManager.class);
    }

    @Test
    public void testAddUser_nullUser_throws() {
        UserLifecycleEventFilter.Builder builder = new UserLifecycleEventFilter.Builder();

        assertThrows(NullPointerException.class, () -> builder.addUser(null));
    }

    @Test
    public void testAddUser_specialUserHandle_throws() {
        UserLifecycleEventFilter.Builder builder = new UserLifecycleEventFilter.Builder();

        assertThrows(IllegalArgumentException.class, ()-> builder.addUser(UserHandle.ALL));
        assertThrows(IllegalArgumentException.class,
                ()-> builder.addUser(UserHandle.CURRENT_OR_SELF));
        assertThrows(IllegalArgumentException.class,
                ()-> builder.addUser(UserHandle.of(UserHandle.USER_NULL)));
    }

    @Test
    public void testBuild_emptyFilter_throws() {
        UserLifecycleEventFilter.Builder builder = new UserLifecycleEventFilter.Builder();

        assertThrows(IllegalStateException.class, () -> builder.build());
    }

    @Test
    public void testBuild_duplicateEventTypesIgnored() {
        UserLifecycleEventFilter filterStarting = new UserLifecycleEventFilter.Builder()
                .addEventType(USER_LIFECYCLE_EVENT_TYPE_STARTING)
                .addEventType(USER_LIFECYCLE_EVENT_TYPE_STARTING).build();

        assertThat(filterStarting.getEventTypes()).asList()
                .containsExactly(USER_LIFECYCLE_EVENT_TYPE_STARTING);
    }

    @Test
    public void testBuild_duplicateUserIdsIgnored() {
        UserLifecycleEventFilter filterStarting = new UserLifecycleEventFilter.Builder()
                .addUser(USER111).addUser(USER111).addUser(USER113).addUser(USER111).build();

        assertThat(filterStarting.getUserIds()).asList().containsExactly(111, 113);
    }

    @Test
    public void testBuild_currentUser_notEvaluatedAtFilterCreation() {
        UserLifecycleEventFilter filterCurrentUser = new UserLifecycleEventFilter.Builder()
                .addUser(UserHandle.CURRENT).build();

        assertThat(filterCurrentUser.getEventTypes()).isNull();
        // CURRENT user is stored as a special id, and will be evaluated at filter apply() time.
        assertThat(filterCurrentUser.getUserIds()).asList()
                .containsExactly(UserHandle.CURRENT.getIdentifier());
    }

    @Test
    public void testApply_nullEvent_throws() {
        UserLifecycleEventFilter filter = new UserLifecycleEventFilter.Builder()
                .addUser(USER111).build();

        assertThrows(NullPointerException.class, () -> filter.apply(/* event= */null));
    }

    @Test
    public void testApply_eventTypesOnly_returnsTrue() {
        UserLifecycleEventFilter filterStartingOrUnlocking = new UserLifecycleEventFilter.Builder()
                .addEventType(USER_LIFECYCLE_EVENT_TYPE_STARTING)
                .addEventType(USER_LIFECYCLE_EVENT_TYPE_UNLOCKING).build();

        assertThat(filterStartingOrUnlocking.apply(USER111_STARTING_EVENT)).isTrue();
        assertThat(filterStartingOrUnlocking.apply(USER111_UNLOCKING_EVENT)).isTrue();
    }

    @Test
    public void testApply_eventTypesOnly_returnsFalse() {
        UserLifecycleEventFilter filterStartingOrUnlocking = new UserLifecycleEventFilter.Builder()
                .addEventType(USER_LIFECYCLE_EVENT_TYPE_STARTING)
                .addEventType(USER_LIFECYCLE_EVENT_TYPE_UNLOCKING).build();

        assertThat(filterStartingOrUnlocking.apply(USER111_TO_112_SWITCHING_EVENT)).isFalse();
    }

    @Test
    public void testApply_userIdMatches_returnsTrue() {
        UserLifecycleEventFilter filterUser111 = new UserLifecycleEventFilter.Builder()
                .addUser(USER111).build();

        assertThat(filterUser111.apply(USER111_UNLOCKING_EVENT)).isTrue();
    }

    @Test
    public void testApply_previousUserIdMatches_returnsTrue() {
        UserLifecycleEventFilter filterUser112 = new UserLifecycleEventFilter.Builder()
                .addUser(USER112).build();

        // The switching event's getPreviousUserId() matches.
        assertThat(filterUser112.apply(USER112_TO_113_SWITCHING_EVENT)).isTrue();
    }

    @Test
    public void testApply_userIdsDoNotMatch_returnsFalse() {
        UserLifecycleEventFilter filterUser112Or113 = new UserLifecycleEventFilter.Builder()
                .addUser(USER112).addUser(USER113).build();

        assertThat(filterUser112Or113.apply(USER111_STARTING_EVENT)).isFalse();
    }

    @Test
    public void testApply_bothEventTypesAndUserIdsMatch_returnsTrue() {
        UserLifecycleEventFilter filterUnlocking = new UserLifecycleEventFilter.Builder()
                .addEventType(USER_LIFECYCLE_EVENT_TYPE_UNLOCKING)
                .addUser(USER111).build();

        assertThat(filterUnlocking.apply(USER111_UNLOCKING_EVENT)).isTrue();
    }

    @Test
    public void testApply_eventTypeDoesNotMatch_returnsFalse() {
        UserLifecycleEventFilter filterUnlocked = new UserLifecycleEventFilter.Builder()
                .addEventType(USER_LIFECYCLE_EVENT_TYPE_UNLOCKED)
                .addUser(USER111).build();

        assertThat(filterUnlocked.apply(USER111_UNLOCKING_EVENT)).isFalse();
    }

    @Test
    public void testApply_userIdDoesNotMatch_returnsFalse() {
        UserLifecycleEventFilter filterStarting = new UserLifecycleEventFilter.Builder()
                .addEventType(USER_LIFECYCLE_EVENT_TYPE_STARTING)
                .addUser(USER112).build();

        assertThat(filterStarting.apply(USER111_STARTING_EVENT)).isFalse();
    }

    @Test
    public void testApply_currentUserFilter() {
        mockGetCurrentUser(USER112.getIdentifier());
        UserLifecycleEventFilter filterCurrentUser = new UserLifecycleEventFilter.Builder()
                .addUser(UserHandle.CURRENT).build();

        assertThat(filterCurrentUser.apply(USER111_TO_112_SWITCHING_EVENT)).isTrue();
        assertThat(filterCurrentUser.apply(USER112_TO_113_SWITCHING_EVENT)).isTrue();
    }

    @Test
    public void testApply_currentUserFilter_evaluatedAtFilterApplyTime() {
        // Current user is 111 at the filter creation time.
        mockGetCurrentUser(USER111.getIdentifier());
        UserLifecycleEventFilter filterCurrentUser = new UserLifecycleEventFilter.Builder()
                .addUser(UserHandle.CURRENT).build();
        // Current user will be 112 when the event occurs.
        mockGetCurrentUser(USER112.getIdentifier());

        assertThat(filterCurrentUser.apply(USER111_STARTING_EVENT)).isFalse();
        assertThat(filterCurrentUser.apply(USER112_STARTING_EVENT)).isTrue();
    }
}
