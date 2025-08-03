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

package com.android.adservices.data.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.shared.util.Clock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

public class UserProfileIdDaoSharedPreferencesImplTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final String STORAGE_NAME = "test_storage";

    SharedPreferences mSharedPreferences;
    UserProfileIdDao mUserProfileIdDao;
    @Mock private Clock mClock;

    private static final long TIME_INITIAL = 1000;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mSharedPreferences = CONTEXT.getSharedPreferences(STORAGE_NAME, Context.MODE_PRIVATE);
        mUserProfileIdDao = new UserProfileIdDaoSharedPreferencesImpl(mSharedPreferences, mClock);
        when(mClock.currentTimeMillis()).thenReturn(TIME_INITIAL);
    }

    @After
    public void teardown() {
        CONTEXT.deleteSharedPreferences(STORAGE_NAME);
    }

    @Test
    public void testSetAndReadId() {
        UUID uuid = UUID.randomUUID();

        assertNull(mUserProfileIdDao.getUserProfileId());
        mUserProfileIdDao.setUserProfileId(uuid);
        assertEquals(
                mSharedPreferences.getString(
                        UserProfileIdDaoSharedPreferencesImpl.USER_PROFILE_ID_KEY, null),
                uuid.toString());
        assertEquals(mUserProfileIdDao.getUserProfileId(), uuid);
        assertEquals(TIME_INITIAL, mUserProfileIdDao.getTimestamp());
    }

    @Test
    public void testSetId_idExist_overrideExistingId() {
        UUID uuid1 = UUID.randomUUID();

        assertNull(mUserProfileIdDao.getUserProfileId());
        mUserProfileIdDao.setUserProfileId(uuid1);
        when(mClock.currentTimeMillis()).thenReturn(TIME_INITIAL);
        assertEquals(
                mSharedPreferences.getString(
                        UserProfileIdDaoSharedPreferencesImpl.USER_PROFILE_ID_KEY, null),
                uuid1.toString());
        assertEquals(mUserProfileIdDao.getUserProfileId(), uuid1);
        assertEquals(TIME_INITIAL, mUserProfileIdDao.getTimestamp());

        UUID uuid2 = UUID.randomUUID();
        when(mClock.currentTimeMillis()).thenReturn(TIME_INITIAL + 100);
        mUserProfileIdDao.setUserProfileId(uuid2);
        assertEquals(
                mSharedPreferences.getString(
                        UserProfileIdDaoSharedPreferencesImpl.USER_PROFILE_ID_KEY, null),
                uuid2.toString());
        assertEquals(mUserProfileIdDao.getUserProfileId(), uuid2);
        assertEquals(TIME_INITIAL + 100, mUserProfileIdDao.getTimestamp());
    }

    @Test
    public void testDeleteStorage() {
        UUID uuid = UUID.randomUUID();

        assertNull(mUserProfileIdDao.getUserProfileId());
        mUserProfileIdDao.setUserProfileId(uuid);
        assertEquals(
                mSharedPreferences.getString(
                        UserProfileIdDaoSharedPreferencesImpl.USER_PROFILE_ID_KEY, null),
                uuid.toString());
        assertEquals(mUserProfileIdDao.getUserProfileId(), uuid);

        mUserProfileIdDao.deleteStorage();
        assertNull(mUserProfileIdDao.getUserProfileId());
        assertNull(
                mSharedPreferences.getString(
                        UserProfileIdDaoSharedPreferencesImpl.USER_PROFILE_ID_KEY, null));
        assertEquals(0, mUserProfileIdDao.getTimestamp());
        assertEquals(
                0,
                mSharedPreferences.getLong(
                        UserProfileIdDaoSharedPreferencesImpl
                                .USER_PROFILE_ID_CREATION_TIMESTAMP_KEY,
                        0));
    }
}
