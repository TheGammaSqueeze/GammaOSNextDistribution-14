/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.settings.qc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.UserManager;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public abstract class BaseSettingsQCItemTestCase {
    protected final Context mContext = spy(ApplicationProvider.getApplicationContext());

    @Mock
    protected UserManager mUm;
    @Mock
    protected DevicePolicyManager mDpm;

    @Before
    public final void baseSetUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUm);
        when(mContext.getSystemService(DevicePolicyManager.class)).thenReturn(mDpm);

        when(mUm.hasBaseUserRestriction(any(), any())).thenReturn(false);
        when(mUm.hasUserRestriction(any())).thenReturn(false);
        when(mDpm.isDeviceManaged()).thenReturn(false);
    }

    protected void setBaseUserRestriction(String restriction, boolean restricted) {
        when(mUm.hasBaseUserRestriction(eq(restriction), any())).thenReturn(restricted);
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUm);
    }

    protected void setUserRestriction(String restriction, boolean restricted) {
        when(mUm.hasUserRestriction(restriction)).thenReturn(restricted);
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUm);
    }
}
