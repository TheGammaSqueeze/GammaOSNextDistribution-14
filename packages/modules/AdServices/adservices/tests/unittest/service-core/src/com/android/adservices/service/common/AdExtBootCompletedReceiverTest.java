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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.List;

@SmallTest
public class AdExtBootCompletedReceiverTest {
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Intent sIntent = new Intent();
    private static final String TEST_PACKAGE_NAME = "test";
    private static final String AD_SERVICES_APK_PKG_SUFFIX = "android.adservices.api";
    private static final int NUM_ACTIVITIES_TO_DISABLE = 7;
    private static final int NUM_SERVICE_CLASSES_TO_DISABLE = 7;

    @Mock Flags mMockFlags;
    @Mock Context mContext;
    @Mock PackageManager mPackageManager;
    MockitoSession mSession;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);

        // Start a mockitoSession to mock static method
        mSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(PackageChangedReceiver.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();

        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        when(FlagsFactory.getFlags()).thenReturn(mMockFlags);
    }

    @After
    public void teardown() {
        mSession.finishMocking();
    }

    @Test
    public void testOnReceive_tPlus_flagOff() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        AdExtBootCompletedReceiver bootCompletedReceiver =
                Mockito.spy(new AdExtBootCompletedReceiver());
        doReturn(true).when(mMockFlags).getGlobalKillSwitch();

        bootCompletedReceiver.onReceive(sContext, sIntent);

        verify(bootCompletedReceiver, never()).registerPackagedChangedBroadcastReceivers(any());
        verify(bootCompletedReceiver, never()).updateAdExtServicesServices(any(), eq(true));
        verify(bootCompletedReceiver, atLeastOnce())
                .updateAdExtServicesActivities(any(), eq(false));
        verify(bootCompletedReceiver, atLeastOnce()).updateAdExtServicesServices(any(), eq(false));
        verify(bootCompletedReceiver, atLeastOnce())
                .unregisterPackageChangedBroadcastReceivers(any());
        verify(bootCompletedReceiver, atLeastOnce()).disableScheduledBackgroundJobs(any());
    }

    @Test
    public void testOnReceive_tPlus_flagOn() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        AdExtBootCompletedReceiver bootCompletedReceiver =
                Mockito.spy(new AdExtBootCompletedReceiver());
        doReturn(true).when(mMockFlags).getEnableBackCompat();
        doReturn(true).when(mMockFlags).getAdServicesEnabled();
        doReturn(false).when(mMockFlags).getGlobalKillSwitch();

        bootCompletedReceiver.onReceive(sContext, sIntent);
        verify(bootCompletedReceiver, never()).registerPackagedChangedBroadcastReceivers(any());
        verify(bootCompletedReceiver, never()).updateAdExtServicesServices(any(), eq(true));
        verify(bootCompletedReceiver, atLeastOnce())
                .updateAdExtServicesActivities(any(), eq(false));
        verify(bootCompletedReceiver, atLeastOnce()).updateAdExtServicesServices(any(), eq(false));
        verify(bootCompletedReceiver, atLeastOnce())
                .unregisterPackageChangedBroadcastReceivers(any());
        verify(bootCompletedReceiver, atLeastOnce()).disableScheduledBackgroundJobs(any());
    }

    @Test
    public void testOnReceive_sminus_flagsOff() {
        Assume.assumeFalse(SdkLevel.isAtLeastT());
        AdExtBootCompletedReceiver bootCompletedReceiver =
                Mockito.spy(new AdExtBootCompletedReceiver());
        doReturn(false).when(mMockFlags).getEnableBackCompat();

        bootCompletedReceiver.onReceive(sContext, sIntent);
        verify(bootCompletedReceiver, never()).registerPackagedChangedBroadcastReceivers(any());
        verify(bootCompletedReceiver, never()).updateAdExtServicesActivities(any(), eq(true));
        verify(bootCompletedReceiver, never()).updateAdExtServicesServices(any(), eq(true));

        doReturn(true).when(mMockFlags).getEnableBackCompat();
        doReturn(false).when(mMockFlags).getAdServicesEnabled();

        bootCompletedReceiver.onReceive(sContext, sIntent);
        verify(bootCompletedReceiver, never()).registerPackagedChangedBroadcastReceivers(any());
        verify(bootCompletedReceiver, never()).updateAdExtServicesActivities(any(), eq(true));
        verify(bootCompletedReceiver, never()).updateAdExtServicesServices(any(), eq(true));

        doReturn(true).when(mMockFlags).getEnableBackCompat();
        doReturn(true).when(mMockFlags).getAdServicesEnabled();
        doReturn(true).when(mMockFlags).getGlobalKillSwitch();

        bootCompletedReceiver.onReceive(sContext, sIntent);
        verify(bootCompletedReceiver, never()).registerPackagedChangedBroadcastReceivers(any());
        verify(bootCompletedReceiver, never()).updateAdExtServicesActivities(any(), eq(true));
        verify(bootCompletedReceiver, never()).updateAdExtServicesServices(any(), eq(true));
        verify(bootCompletedReceiver, never()).disableScheduledBackgroundJobs(any());
    }

    @Test
    public void testOnReceive_sminus_flagsOn() {
        Assume.assumeFalse(SdkLevel.isAtLeastT());
        AdExtBootCompletedReceiver bootCompletedReceiver =
                Mockito.spy(new AdExtBootCompletedReceiver());
        doReturn(true).when(mMockFlags).getEnableBackCompat();
        doReturn(true).when(mMockFlags).getAdServicesEnabled();
        doReturn(false).when(mMockFlags).getGlobalKillSwitch();

        bootCompletedReceiver.onReceive(sContext, sIntent);
        verify(bootCompletedReceiver).registerPackagedChangedBroadcastReceivers(any());
        verify(bootCompletedReceiver).updateAdExtServicesActivities(any(), eq(true));
        verify(bootCompletedReceiver).updateAdExtServicesServices(any(), eq(true));
        verify(bootCompletedReceiver, never()).disableScheduledBackgroundJobs(any());
    }

    @Test
    public void testRegisterReceivers() {
        Assume.assumeFalse(SdkLevel.isAtLeastT());
        AdExtBootCompletedReceiver bootCompletedReceiver = new AdExtBootCompletedReceiver();
        bootCompletedReceiver.registerPackagedChangedBroadcastReceivers(sContext);
        verify(() -> PackageChangedReceiver.enableReceiver(any(Context.class), any(Flags.class)));
    }

    @Test
    public void testUnregisterReceivers() {
        doReturn(true).when(() -> PackageChangedReceiver.disableReceiver(any(), any()));

        AdExtBootCompletedReceiver bootCompletedReceiver = new AdExtBootCompletedReceiver();
        bootCompletedReceiver.unregisterPackageChangedBroadcastReceivers(sContext);

        verify(() -> PackageChangedReceiver.disableReceiver(any(Context.class), any(Flags.class)));
    }

    @Test
    public void testEnableActivities_sminus() {
        Assume.assumeFalse(SdkLevel.isAtLeastT());
        AdExtBootCompletedReceiver bootCompletedReceiver =
                Mockito.spy(new AdExtBootCompletedReceiver());

        setCommonMocks(TEST_PACKAGE_NAME);

        // Call the method we're testing.
        bootCompletedReceiver.updateAdExtServicesActivities(mContext, true);

        verify(mPackageManager, times(7))
                .setComponentEnabledSetting(
                        any(ComponentName.class),
                        eq(PackageManager.COMPONENT_ENABLED_STATE_ENABLED),
                        eq(PackageManager.DONT_KILL_APP));
    }

    @Test
    public void testDisableActivities_tPlus() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        AdExtBootCompletedReceiver bootCompletedReceiver =
                Mockito.spy(new AdExtBootCompletedReceiver());
        setCommonMocks(TEST_PACKAGE_NAME);

        // Call the method we're testing.
        bootCompletedReceiver.updateAdExtServicesActivities(mContext, false);

        verify(mPackageManager, times(NUM_ACTIVITIES_TO_DISABLE))
                .setComponentEnabledSetting(
                        any(ComponentName.class),
                        eq(PackageManager.COMPONENT_ENABLED_STATE_DISABLED),
                        eq(PackageManager.DONT_KILL_APP));
    }

    @Test
    public void testDisableAdExtServicesServices_tPlus() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        AdExtBootCompletedReceiver bootCompletedReceiver =
                Mockito.spy(new AdExtBootCompletedReceiver());
        setCommonMocks(TEST_PACKAGE_NAME);

        // Call the method we're testing.
        bootCompletedReceiver.updateAdExtServicesServices(mContext, /* shouldEnable= */ false);

        verify(mPackageManager, times(NUM_SERVICE_CLASSES_TO_DISABLE))
                .setComponentEnabledSetting(
                        any(ComponentName.class),
                        eq(PackageManager.COMPONENT_ENABLED_STATE_DISABLED),
                        eq(PackageManager.DONT_KILL_APP));
    }

    @Test
    public void testUpdateAdExtServicesServices_sminus() {
        Assume.assumeFalse(SdkLevel.isAtLeastT());
        AdExtBootCompletedReceiver bootCompletedReceiver =
                Mockito.spy(new AdExtBootCompletedReceiver());
        setCommonMocks(TEST_PACKAGE_NAME);

        // Call the method we're testing
        bootCompletedReceiver.updateAdExtServicesServices(mContext, /* shouldEnable= */ true);

        verify(mPackageManager, times(NUM_SERVICE_CLASSES_TO_DISABLE))
                .setComponentEnabledSetting(
                        any(ComponentName.class),
                        eq(PackageManager.COMPONENT_ENABLED_STATE_ENABLED),
                        eq(PackageManager.DONT_KILL_APP));
    }

    @Test
    public void testUpdateAdExtServicesActivities_withAdServicesPackageSuffix_doesNotUpdate() {
        AdExtBootCompletedReceiver bootCompletedReceiver =
                Mockito.spy(new AdExtBootCompletedReceiver());
        setCommonMocks(AD_SERVICES_APK_PKG_SUFFIX);

        // Call the method we're testing.
        bootCompletedReceiver.updateAdExtServicesActivities(mContext, false);

        verify(mPackageManager, never())
                .setComponentEnabledSetting(
                        any(ComponentName.class),
                        eq(PackageManager.COMPONENT_ENABLED_STATE_DISABLED),
                        eq(PackageManager.DONT_KILL_APP));
    }

    @Test
    public void testDisableAdExtServicesServices_withAdServicesPackageSuffix_doesNotUpdate() {
        AdExtBootCompletedReceiver bootCompletedReceiver =
                Mockito.spy(new AdExtBootCompletedReceiver());
        setCommonMocks(AD_SERVICES_APK_PKG_SUFFIX);

        // Call the method we're testing.
        bootCompletedReceiver.updateAdExtServicesServices(mContext, /* shouldEnable= */ false);

        verify(mPackageManager, never())
                .setComponentEnabledSetting(
                        any(ComponentName.class),
                        eq(PackageManager.COMPONENT_ENABLED_STATE_DISABLED),
                        eq(PackageManager.DONT_KILL_APP));
    }

    @Test
    public void testUpdateComponents_withAdServicesPackageSuffix_throwsException() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        AdExtBootCompletedReceiver.updateComponents(
                                mContext, ImmutableList.of(), AD_SERVICES_APK_PKG_SUFFIX, false));
    }

    @Test
    public void testUpdateComponents_adServicesPackageNamePresentButNotSuffix_disablesComponent() {
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        AdExtBootCompletedReceiver.updateComponents(
                mContext,
                ImmutableList.of("test"),
                AD_SERVICES_APK_PKG_SUFFIX + TEST_PACKAGE_NAME,
                false);

        verify(mPackageManager)
                .setComponentEnabledSetting(
                        any(ComponentName.class),
                        eq(PackageManager.COMPONENT_ENABLED_STATE_DISABLED),
                        eq(PackageManager.DONT_KILL_APP));
    }

    @Test
    public void testDisableScheduledBackgroundJobs_contextNull() {
        assertThrows(
                NullPointerException.class,
                () -> new AdExtBootCompletedReceiver().disableScheduledBackgroundJobs(null));
    }

    @Test
    public void testDisableScheduledBackgroundJobs_withAdServicesPackageSuffix_doesNotUpdate() {
        AdExtBootCompletedReceiver bootCompletedReceiver =
                Mockito.spy(new AdExtBootCompletedReceiver());
        setCommonMocks(AD_SERVICES_APK_PKG_SUFFIX);

        bootCompletedReceiver.disableScheduledBackgroundJobs(mContext);
        verify(mContext, never()).getSystemService(eq(JobScheduler.class));
    }

    @Test
    public void
            testDisableScheduledBackgroundJobs_adServicesPackagePresentButNotSuffix_cancelsAllJobs() {
        AdExtBootCompletedReceiver bootCompletedReceiver =
                Mockito.spy(new AdExtBootCompletedReceiver());

        JobScheduler mockScheduler = Mockito.mock(JobScheduler.class);
        when(mContext.getSystemService(JobScheduler.class)).thenReturn(mockScheduler);
        when(mockScheduler.getAllPendingJobs()).thenReturn(getJobInfos());
        doNothing().when(mockScheduler).cancel(anyInt());
        setCommonMocks(AD_SERVICES_APK_PKG_SUFFIX + TEST_PACKAGE_NAME);

        bootCompletedReceiver.disableScheduledBackgroundJobs(mContext);
        verify(mockScheduler).cancel(1);
        verify(mockScheduler).cancel(3);
        verify(mockScheduler, never()).cancel(2);
        verify(mockScheduler, never()).cancelAll();
    }

    private static List<JobInfo> getJobInfos() {
        return List.of(
                new JobInfo.Builder(
                                1,
                                new ComponentName(
                                        TEST_PACKAGE_NAME,
                                        "com.android.adservices.service.measurement.attribution"
                                                + ".AttributionJobService"))
                        .build(),
                new JobInfo.Builder(
                                2,
                                new ComponentName(
                                        TEST_PACKAGE_NAME,
                                        "com.android.extservice.common"
                                                + ".AdServicesAppsearchDeleteSchedulerJobService"))
                        .build(),
                new JobInfo.Builder(
                                3,
                                new ComponentName(
                                        TEST_PACKAGE_NAME,
                                        "com.android.adservices.service.topics"
                                                + ".EpochJobService"))
                        .build());
    }

    @Test
    public void testDisableScheduledBackgroundJobs_cancelsAllJobs() {
        AdExtBootCompletedReceiver bootCompletedReceiver =
                Mockito.spy(new AdExtBootCompletedReceiver());

        JobScheduler mockScheduler = Mockito.mock(JobScheduler.class);
        when(mContext.getSystemService(JobScheduler.class)).thenReturn(mockScheduler);
        when(mockScheduler.getAllPendingJobs()).thenReturn(getJobInfos());
        doNothing().when(mockScheduler).cancel(anyInt());

        setCommonMocks(TEST_PACKAGE_NAME);

        bootCompletedReceiver.disableScheduledBackgroundJobs(mContext);
        verify(mockScheduler).cancel(1);
        verify(mockScheduler).cancel(3);
        verify(mockScheduler, never()).cancel(2);
        verify(mockScheduler, never()).cancelAll();
    }

    @Test
    public void testDisableScheduledBackgroundJobs_handlesException() throws Exception {
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.getPackageInfo(anyString(), anyInt()))
                .thenThrow(PackageManager.NameNotFoundException.class);

        AdExtBootCompletedReceiver bootCompletedReceiver =
                Mockito.spy(new AdExtBootCompletedReceiver());
        bootCompletedReceiver.disableScheduledBackgroundJobs(mContext);
        verify(mContext, never()).getSystemService(eq(JobScheduler.class));
    }

    @Test
    public void testClassNameMatchesExpectedValue() {
        assertWithMessage(
                        "AdExtBootCompletedReceiver class name is hard-coded in ExtServices"
                            + " BootCompletedReceiver. If the name changes, that class needs to be"
                            + " modified in unison")
                .that(AdExtBootCompletedReceiver.class.getName())
                .isEqualTo("com.android.adservices.service.common.AdExtBootCompletedReceiver");
    }

    private void setCommonMocks(String packageName) {
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mContext.getPackageName()).thenReturn(packageName);
    }
}
