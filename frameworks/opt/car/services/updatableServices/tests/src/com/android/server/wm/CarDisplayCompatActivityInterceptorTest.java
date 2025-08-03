/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.wm;

import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.server.wm.CarDisplayCompatActivityInterceptor.LAUNCHED_FROM_HOST;
import static com.android.server.wm.CarDisplayCompatActivityInterceptor.PERMISSION_DISPLAY_COMPATIBILITY;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

@RunWith(AndroidJUnit4.class)
public class CarDisplayCompatActivityInterceptorTest {

    private MockitoSession mMockingSession;

    @Mock
    private Resources mMockResources;
    @Mock
    private Context mMockContext;
    @Mock
    private CarDisplayCompatScaleProviderUpdatable mMockCarDisplayCompatScaleProvider;
    @Mock
    private ActivityInterceptorInfoWrapper mMockInfo;


    private CarDisplayCompatActivityInterceptor mInterceptor;
    private ComponentName mHostActitivy = ComponentName.unflattenFromString(
            "com.displaycompathost/.StartActivity");

    @Before
    public void setUp() {
        mMockingSession = mockitoSession()
            .initMocks(this)
            .strictness(Strictness.LENIENT)
            .startMocking();

        when(mMockResources.getIdentifier(
                eq("config_defaultDisplayCompatHostActivity"), eq("string"), eq("android")
        )).thenReturn(1);
        when(mMockResources.getString(eq(1))).thenReturn(mHostActitivy.flattenToString());
        when(mMockContext.getResources()).thenReturn(mMockResources);

        mInterceptor = new CarDisplayCompatActivityInterceptor(mMockContext,
                mMockCarDisplayCompatScaleProvider);
    }

    @After
    public void tearDown() {
        // If the exception is thrown during the MockingSession setUp, mMockingSession can be null.
        if (mMockingSession != null) {
            mMockingSession.finishMocking();
        }
    }

    @Test
    public void hostActivity_isIgnored() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(mHostActitivy);

        when(mMockInfo.getIntent()).thenReturn(intent);

        ActivityInterceptResultWrapper result =
                mInterceptor.onInterceptActivityLaunch(mMockInfo);

        assertThat(result).isNull();
    }

    @Test
    public void nonDisplayCompatActivity_isIgnored() {
        ComponentName nonDisplayCompatActivity =
                ComponentName.unflattenFromString("com.test/.NonDisplayCompatActivity_isIgnored");
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(nonDisplayCompatActivity);
        when(mMockCarDisplayCompatScaleProvider
                .requiresDisplayCompat(eq(nonDisplayCompatActivity.getPackageName()),
                        any(int.class)))
                .thenReturn(false);

        when(mMockInfo.getIntent()).thenReturn(intent);

        ActivityInterceptResultWrapper result =
                mInterceptor.onInterceptActivityLaunch(mMockInfo);

        assertThat(result).isNull();
    }

    @Test
    public void displayCompatActivity_launchedFromHost_isIgnored() {
        ComponentName displayCompatActivity =
                ComponentName.unflattenFromString("com.displaycompatapp/.DisplayCompatActivity");
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(displayCompatActivity);
        when(mMockCarDisplayCompatScaleProvider
                .requiresDisplayCompat(eq(displayCompatActivity.getPackageName()), any(int.class)))
                .thenReturn(true);

        intent.putExtra(LAUNCHED_FROM_HOST, true);
        when(mMockInfo.getIntent()).thenReturn(intent);

        when(mMockInfo.getCallingPackage()).thenReturn("com.displaycompatapp");
        when(mMockInfo.getCallingPid()).thenReturn(1);
        when(mMockInfo.getCallingUid()).thenReturn(2);
        when(mMockContext.checkPermission(PERMISSION_DISPLAY_COMPATIBILITY, 1, 2))
                .thenReturn(PERMISSION_GRANTED);

        ActivityInterceptResultWrapper result =
                mInterceptor.onInterceptActivityLaunch(mMockInfo);

        assertThat(result).isNull();
    }

    @Test
    public void displayCompatActivity_returnsHost() {
        ComponentName displayCompatActivity =
                ComponentName.unflattenFromString("com.displaycompatapp/.DisplayCompatActivity");
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(displayCompatActivity);
        when(mMockCarDisplayCompatScaleProvider
                .requiresDisplayCompat(eq(displayCompatActivity.getPackageName()), any(int.class)))
                .thenReturn(true);

        when(mMockInfo.getIntent()).thenReturn(intent);

        ActivityInterceptResultWrapper result =
                mInterceptor.onInterceptActivityLaunch(mMockInfo);

        assertThat(result).isNotNull();
        assertThat(result.getInterceptResult()).isNotNull();
        assertThat(result.getInterceptResult().getIntent()).isNotNull();
        assertThat(result.getInterceptResult().getIntent().getComponent()).isEqualTo(mHostActitivy);
        Intent launchIntent = (Intent) result.getInterceptResult().getIntent()
                .getExtra(Intent.EXTRA_INTENT);
        assertThat(launchIntent).isNotNull();
    }

    @Test
    public void displayCompatActivity_launchedFromDisplayCompatApp_returnsHost() {
        ComponentName displayCompatActivity =
                ComponentName.unflattenFromString("com.displaycompatapp/.DisplayCompatActivity");
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(displayCompatActivity);
        when(mMockCarDisplayCompatScaleProvider
                .requiresDisplayCompat(eq(displayCompatActivity.getPackageName()), any(int.class)))
                .thenReturn(true);
        when(mMockCarDisplayCompatScaleProvider
                .requiresDisplayCompat(eq("com.displaycompatapp"), any(int.class)))
                .thenReturn(true);
        when(mMockInfo.getCallingPackage()).thenReturn("com.displaycompatapp");

        when(mMockInfo.getIntent()).thenReturn(intent);

        ActivityInterceptResultWrapper result =
                mInterceptor.onInterceptActivityLaunch(mMockInfo);

        assertThat(result).isNotNull();
        assertThat(result.getInterceptResult()).isNotNull();
        assertThat(result.getInterceptResult().getIntent()).isNotNull();
        assertThat(result.getInterceptResult().getIntent().getComponent()).isEqualTo(mHostActitivy);
        Intent launchIntent = (Intent) result.getInterceptResult().getIntent()
                .getExtra(Intent.EXTRA_INTENT);
        assertThat(launchIntent).isNotNull();
    }

    @Test
    public void displayCompatActivity_noPermission_returnsHost() {
        ComponentName displayCompatActivity =
                ComponentName.unflattenFromString("com.displaycompatapp/.DisplayCompatActivity");
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.putExtra(LAUNCHED_FROM_HOST, true);
        intent.setComponent(displayCompatActivity);
        when(mMockInfo.getIntent()).thenReturn(intent);
        when(mMockCarDisplayCompatScaleProvider
                .requiresDisplayCompat(eq(displayCompatActivity.getPackageName()), any(int.class)))
                .thenReturn(true);

        when(mMockInfo.getCallingPackage()).thenReturn("com.displaycompatapp");
        when(mMockInfo.getCallingPid()).thenReturn(1);
        when(mMockInfo.getCallingUid()).thenReturn(2);
        when(mMockContext.checkPermission(PERMISSION_DISPLAY_COMPATIBILITY, 1, 2))
                .thenReturn(PERMISSION_DENIED);

        ActivityInterceptResultWrapper result =
                mInterceptor.onInterceptActivityLaunch(mMockInfo);

        assertThat(result).isNotNull();
        assertThat(result.getInterceptResult()).isNotNull();
        assertThat(result.getInterceptResult().getIntent()).isNotNull();
        assertThat(result.getInterceptResult().getIntent().getComponent()).isEqualTo(mHostActitivy);
        Intent launchIntent = (Intent) result.getInterceptResult().getIntent()
                .getExtra(Intent.EXTRA_INTENT);
        assertThat(launchIntent).isNotNull();
    }
}
