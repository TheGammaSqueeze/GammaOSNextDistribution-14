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

package com.android.adservices.service.common;

import static com.android.adservices.service.common.UserProfileIdManager.MILLISECONDS_IN_DAY;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.android.adservices.data.common.UserProfileIdDao;
import com.android.adservices.shared.util.Clock;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class UserProfileIdManagerTest {

    @Mock private UserProfileIdDao mUserProfileIdDao;
    private UserProfileIdManager mUserProfileIdManager;
    @Mock private Clock mClock;
    private static final long TIME_INITIAL = 1000;
    private static final long TIME_BEFORE_A_DAY = 1010;
    private static final long TIME_AFTER_A_DAY = TIME_INITIAL + MILLISECONDS_IN_DAY;

    @Before
    public void setup() {
        mUserProfileIdManager = new UserProfileIdManager(mUserProfileIdDao, mClock);
        when(mClock.currentTimeMillis()).thenReturn(TIME_INITIAL);
    }

    @Test
    public void testGetOrCreateId_idNotExist_CreateNewId() {
        UUID uuid = mUserProfileIdManager.getOrCreateId();
        verify(mUserProfileIdDao).getUserProfileId();
        verify(mUserProfileIdDao).setUserProfileId(uuid);
        verifyNoMoreInteractions(mUserProfileIdDao);
    }

    @Test
    public void testGetOrCreateId_idExist_returnId() {
        UUID uuid = UUID.randomUUID();
        when(mUserProfileIdDao.getUserProfileId()).thenReturn(uuid);
        UUID result = mUserProfileIdManager.getOrCreateId();
        Assert.assertEquals(uuid, result);
        verify(mUserProfileIdDao).getUserProfileId();
        verifyNoMoreInteractions(mUserProfileIdDao);
    }

    @Test
    public void testDeleteId_aDayHasElapsed_deleted() {
        when(mUserProfileIdDao.getTimestamp()).thenReturn(TIME_INITIAL);
        when(mClock.currentTimeMillis()).thenReturn(TIME_AFTER_A_DAY);
        mUserProfileIdManager.deleteId();
        verify(mUserProfileIdDao).getTimestamp();
        verify(mUserProfileIdDao).deleteStorage();
        verifyNoMoreInteractions(mUserProfileIdDao);
    }

    @Test
    public void testDeleteId_aDayHasNotElapsed_notDeleted() {
        when(mUserProfileIdDao.getTimestamp()).thenReturn(TIME_INITIAL);
        when(mClock.currentTimeMillis()).thenReturn(TIME_BEFORE_A_DAY);
        mUserProfileIdManager.deleteId();
        verify(mUserProfileIdDao).getTimestamp();
        verifyNoMoreInteractions(mUserProfileIdDao);
    }
}
