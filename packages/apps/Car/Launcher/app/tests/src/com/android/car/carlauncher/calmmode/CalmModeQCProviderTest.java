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

package com.android.car.carlauncher.calmmode;

import static junit.framework.Assert.assertNull;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.carlauncher.Flags;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class CalmModeQCProviderTest {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    private static final String ALLOW_LIST_PKG = "com.android.systemui";
    private CalmModeQCProvider mCalmModeQCProvider;
    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Before
    public void setUp() {
        mCalmModeQCProvider = new CalmModeQCProvider();
        ExtendedMockito.spyOn(mCalmModeQCProvider);
        ExtendedMockito.doReturn(mContext).when(mCalmModeQCProvider).getContext();
        mSetFlagsRule.enableFlags(Flags.FLAG_CALM_MODE);
    }

    @Test
    public void onCreate_allowlistSet() {
        mCalmModeQCProvider.onCreate();
        Set<String> allowlist = mCalmModeQCProvider.getAllowlistedPackages();

        assertTrue(allowlist.contains(ALLOW_LIST_PKG));
    }

    @Test
    public void onCreate_QCItemIsNotNull() {
        mCalmModeQCProvider.onCreate();

        assertNotNull(mCalmModeQCProvider.mQCItem);
    }

    @Test
    public void onBind_invalidUri_throwsException() {
        mCalmModeQCProvider.onCreate();

        assertThrows(IllegalArgumentException.class, () -> mCalmModeQCProvider.onBind(Uri.EMPTY));
    }

    @Test
    public void onBind_validUri_returnsQCItem() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CALM_MODE);

        mCalmModeQCProvider.onCreate();

        assertNotNull(mCalmModeQCProvider.onBind(CalmModeQCProvider.CALM_MODE_URI));
    }

    @Test
    public void onBind_flagOff_returnsNull() {
        mSetFlagsRule.disableFlags(Flags.FLAG_CALM_MODE);

        mCalmModeQCProvider.onCreate();

        assertNull(mCalmModeQCProvider.onBind(CalmModeQCProvider.CALM_MODE_URI));
    }
}
