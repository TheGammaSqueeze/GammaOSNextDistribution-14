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

package com.android.car.setupwizardlib.util;

import static com.android.car.setupwizardlib.util.FeatureResolver.VALUE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;

import com.android.car.setupwizardlib.robolectric.BaseRobolectricTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Constructor;

@RunWith(RobolectricTestRunner.class)
public class FeatureResolverTest extends BaseRobolectricTest {

    @Mock
    private ContentResolver mContentResolver;

    private FeatureResolver mFeatureResolver;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        Constructor<FeatureResolver> constructor = FeatureResolver.class
                .getDeclaredConstructor(Context.class);
        constructor.setAccessible(true);
        mContext = spy(ApplicationProvider.getApplicationContext());
        mFeatureResolver = constructor.newInstance(mContext);
        doReturn(mContentResolver).when(mContext).getContentResolver();
    }

    @Test
    public void testFeatureResolverInstance() {
        assertThat(FeatureResolver.get(mContext)).isNotNull();
    }

    @Test
    public void testFeatureResolverSingletonInstance() {
        FeatureResolver instance = FeatureResolver.get(mContext);

        assertThat(instance).isEqualTo(FeatureResolver.get(mContext));
    }

    @Test
    public void testIsSplitNavLayoutFeatureEnabled_whenReturnsTrue() {
        Bundle bundle = new Bundle();
        bundle.putBoolean(VALUE, true);
        doReturn(bundle).when(mContentResolver).call((Uri) any(), any(), any(), any());

        boolean isSplitNavLayoutFeatureEnabled = mFeatureResolver.isSplitNavLayoutFeatureEnabled();

        assertThat(isSplitNavLayoutFeatureEnabled).isTrue();
    }

    @Test
    public void testIsSplitNavLayoutFeatureEnabled_whenReturnsFalse() {
        Bundle bundle = new Bundle();
        bundle.putBoolean(VALUE, false);
        doReturn(bundle).when(mContentResolver).call((Uri) any(), any(), any(), any());

        boolean isSplitNavLayoutFeatureEnabled = mFeatureResolver.isSplitNavLayoutFeatureEnabled();

        assertThat(isSplitNavLayoutFeatureEnabled).isFalse();
    }

    @Test
    public void testIsSplitNavLayoutFeatureEnabled_whenReturnsBundleNull() {
        doReturn(null).when(mContentResolver).call((Uri) any(), any(), any(), any());

        boolean isSplitNavLayoutFeatureEnabled = mFeatureResolver.isSplitNavLayoutFeatureEnabled();

        assertThat(isSplitNavLayoutFeatureEnabled).isFalse();
    }

    @Test
    public void testGetGModalVersion_whenVersionNumber1() {
        Bundle bundle = new Bundle();
        bundle.putInt(VALUE, 1);
        doReturn(bundle).when(mContentResolver).call((Uri) any(), any(), any(), any());

        int splitNavLayoutFeatureVersion = mFeatureResolver.getGModalVersion();

        assertThat(splitNavLayoutFeatureVersion).isEqualTo(1);
    }

    @Test
    public void testGModalVersion_whenVersionNumber0() {
        Bundle bundle = new Bundle();
        bundle.putInt(VALUE, 0);
        doReturn(bundle).when(mContentResolver).call((Uri) any(), any(), any(), any());

        int splitNavLayoutFeatureVersion = mFeatureResolver.getGModalVersion();

        assertThat(splitNavLayoutFeatureVersion).isEqualTo(0);
    }

    @Test
    public void testGetGModalVersion_whenReturnsBundleNull() {
        doReturn(null).when(mContentResolver).call((Uri) any(), any(), any(), any());

        int splitNavLayoutFeatureVersion = mFeatureResolver.getGModalVersion();

        assertThat(splitNavLayoutFeatureVersion).isEqualTo(0);
    }
}
