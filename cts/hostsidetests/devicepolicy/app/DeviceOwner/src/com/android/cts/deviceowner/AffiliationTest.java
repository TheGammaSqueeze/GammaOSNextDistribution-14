/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.cts.deviceowner;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.annotation.UserIdInt;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public final class AffiliationTest {

    private static final String TAG = AffiliationTest.class.getSimpleName();

    private DevicePolicyManager mDevicePolicyManager;
    private ComponentName mAdminComponent;


    private @UserIdInt int mUserId;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getContext();
        mUserId = context.getUserId();
        mDevicePolicyManager = context.getSystemService(DevicePolicyManager.class);
        mAdminComponent = BasicAdminReceiver.getComponentName(context);
        Log.d(TAG, "setUp(): userId=" + mUserId + ", admin=" + mAdminComponent);
    }

    @Test
    public void testSetAffiliationId_null() throws Exception {
        try {
            Log.d(TAG, "setAffiliationIds(null)");
            mDevicePolicyManager.setAffiliationIds(mAdminComponent, null);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            // Expected
        }
    }

    @Test
    public void testSetAffiliationId_containsEmptyString() throws Exception {
        try {
            Log.d(TAG, "setAffiliationIds(empty)");
            mDevicePolicyManager.setAffiliationIds(mAdminComponent, Collections.singleton(null));
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            // Expected
        }
    }

    @Test
    public void testSetAffiliationId1() throws Exception {
        setAffiliationIds(Collections.singleton("id.number.1"));
    }

    @Test
    public void testSetAffiliationId2() throws Exception {
        setAffiliationIds(Collections.singleton("id.number.2"));
    }

    private void setAffiliationIds(Set<String> ids) throws Exception {
        try {
            Log.d(TAG, "setAffiliationIds(" + ids + ") on user " + mUserId);
            mDevicePolicyManager.setAffiliationIds(mAdminComponent, ids);
            Set<String> setIds = mDevicePolicyManager.getAffiliationIds(mAdminComponent);
            Log.d(TAG, "getAffiliationIds(): " + setIds);
            assertWithMessage("affiliationIds on user %s", mUserId).that(setIds)
                    .containsExactlyElementsIn(ids);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set affiliation ids (" + ids + ")", e);
            throw e;
        }
    }
}
