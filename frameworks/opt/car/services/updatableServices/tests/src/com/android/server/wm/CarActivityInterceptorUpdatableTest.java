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

package com.android.server.wm;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.view.Display;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CarActivityInterceptorUpdatableTest {
    private static final int DEFAULT_CURRENT_USER_ID = 112;
    private static final int PASSENGER_USER_ID = 198;
    private static final int DISPLAY_ID_1 = 0;
    private static final int DISPLAY_ID_2 = 2;
    private CarActivityInterceptorUpdatableImpl mInterceptor;
    private MockitoSession mMockingSession;
    private WindowContainer.RemoteToken mRootTaskToken1;
    private WindowContainer.RemoteToken mRootTaskToken2;

    @Mock
    private Task mWindowContainer1;
    @Mock
    private Task mWindowContainer2;

    @Mock
    private DisplayContent mDisplayContent1;
    @Mock
    private DisplayContent mDisplayContent2;

    @Mock
    private Display mDisplay1;
    @Mock
    private Display mDisplay2;

    @Mock
    private TaskDisplayArea mTda1;
    @Mock
    private TaskDisplayArea mTda2;
    @Mock
    private CarActivityInterceptorUpdatable mMockInterceptor;
    @Mock
    private ActivityInterceptorInfoWrapper mMockInfo;

    private final CarActivityInterceptorInterface mCarActivityInterceptorInterface =
            new CarActivityInterceptorInterface() {
                @Override
                public int getUserAssignedToDisplay(int displayId) {
                    return DEFAULT_CURRENT_USER_ID;
                }

                @Override
                public int getMainDisplayAssignedToUser(int userId) {
                    return 0;
                }
            };

    @Before
    public void setUp() {
        mMockingSession = mockitoSession()
                .initMocks(this)
                .strictness(Strictness.LENIENT)
                .startMocking();

        mTda1.mDisplayContent = mDisplayContent1;
        when(mDisplayContent1.getDisplay()).thenReturn(mDisplay1);
        when(mTda1.getDisplayId()).thenReturn(DISPLAY_ID_1);
        when(mDisplay1.getDisplayId()).thenReturn(DISPLAY_ID_1);

        mRootTaskToken1 = new WindowContainer.RemoteToken(mWindowContainer1);
        mWindowContainer1.mRemoteToken = mRootTaskToken1;
        when(mWindowContainer1.getTaskDisplayArea()).thenReturn(mTda1);

        when(mDisplayContent2.getDisplay()).thenReturn(mDisplay2);
        when(mTda2.getDisplayId()).thenReturn(DISPLAY_ID_2);
        when(mDisplay2.getDisplayId()).thenReturn(DISPLAY_ID_2);
        mTda2.mDisplayContent = mDisplayContent2;

        mRootTaskToken2 = new WindowContainer.RemoteToken(mWindowContainer2);
        when(mWindowContainer2.getTaskDisplayArea()).thenReturn(mTda2);
        mWindowContainer2.mRemoteToken = mRootTaskToken2;

        mInterceptor = new CarActivityInterceptorUpdatableImpl(mCarActivityInterceptorInterface);
    }

    @After
    public void tearDown() {
        // If the exception is thrown during the MockingSession setUp, mMockingSession can be null.
        if (mMockingSession != null) {
            mMockingSession.finishMocking();
        }
    }

    private ActivityInterceptorInfoWrapper createActivityInterceptorInfo(String packageName,
            String activityName, Intent intent, ActivityOptions options, int userId) {
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.packageName = packageName;
        activityInfo.name = activityName;
        ActivityInterceptorCallback.ActivityInterceptorInfo.Builder builder =
                new ActivityInterceptorCallback.ActivityInterceptorInfo.Builder(
                        /* callingUId= */ 0, /* callingPid= */ 0, /* realCallingUid= */ 0,
                        /* realCallingPid= */ 0, /* userId= */ userId, intent,
                        new ResolveInfo(), activityInfo);
        builder.setCheckedOptions(options);
        return ActivityInterceptorInfoWrapper.create(builder.build());
    }

    private ActivityInterceptorInfoWrapper createActivityInterceptorInfoWithCustomIntent(
            String packageName, String activityName, Intent intent) {
        return createActivityInterceptorInfo(packageName, activityName, intent,
                ActivityOptions.makeBasic(), DEFAULT_CURRENT_USER_ID);
    }

    private ActivityInterceptorInfoWrapper createActivityInterceptorInfoWithCustomIntent(
            String packageName, String activityName, Intent intent, int userId) {
        return createActivityInterceptorInfo(packageName, activityName, intent,
                ActivityOptions.makeBasic(), userId);
    }

    private ActivityInterceptorInfoWrapper createActivityInterceptorInfoWithMainIntent(
            String packageName, String activityName) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(ComponentName.unflattenFromString(packageName + "/" + activityName));
        return createActivityInterceptorInfoWithCustomIntent(packageName, activityName, intent);
    }

    private ActivityInterceptorInfoWrapper createActivityInterceptorInfoWithMainIntent(
            String packageName, String activityName, int userId) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(ComponentName.unflattenFromString(packageName + "/" + activityName));
        return createActivityInterceptorInfoWithCustomIntent(packageName, activityName, intent,
                userId);
    }

    private ActivityInterceptorInfoWrapper createActivityInterceptorInfoWithMainIntent(
            String packageName, String activityName, ActivityOptions options) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(ComponentName.unflattenFromString(packageName + "/" + activityName));
        return createActivityInterceptorInfo(packageName, activityName, intent, options,
                DEFAULT_CURRENT_USER_ID);
    }

    @Test
    public void interceptActivityLaunch_nullIntent_returnsNull() {
        ActivityInterceptorInfoWrapper info =
                createActivityInterceptorInfoWithCustomIntent("com.example.app3",
                        "com.example.app3.MainActivity", /* intent= */ null);

        ActivityInterceptResultWrapper result =
                mInterceptor.onInterceptActivityLaunch(info);

        assertThat(result).isNull();
    }

    @Test
    public void interceptActivityLaunch_unknownActivity_returnsNull() {
        List<ComponentName> activities = List.of(
                ComponentName.unflattenFromString("com.example.app/com.example.app.MainActivity"),
                ComponentName.unflattenFromString("com.example.app2/com.example.app2.MainActivity")
        );
        mInterceptor.setPersistentActivityOnRootTask(activities, mRootTaskToken1);
        ActivityInterceptorInfoWrapper info =
                createActivityInterceptorInfoWithMainIntent("com.example.app3",
                        "com.example.app3.MainActivity");

        ActivityInterceptResultWrapper result =
                mInterceptor.onInterceptActivityLaunch(info);

        assertThat(result).isNull();
    }

    @Test
    public void interceptActivityLaunch_nullOptions_persistedActivity_setsLaunchRootTask() {
        List<ComponentName> activities = List.of(
                ComponentName.unflattenFromString("com.example.app/com.example.app.MainActivity"),
                ComponentName.unflattenFromString("com.example.app2/com.example.app2.MainActivity")
        );
        mInterceptor.setPersistentActivityOnRootTask(activities, mRootTaskToken1);
        ActivityInterceptorInfoWrapper info =
                createActivityInterceptorInfoWithMainIntent(activities.get(0).getPackageName(),
                        activities.get(0).getClassName(), /* options= */ null);

        ActivityInterceptResultWrapper result =
                mInterceptor.onInterceptActivityLaunch(info);

        assertThat(result).isNotNull();
        assertThat(result.getInterceptResult().getActivityOptions().getLaunchRootTask())
                .isEqualTo(WindowContainer.fromBinder(mRootTaskToken1)
                        .mRemoteToken.toWindowContainerToken());
    }

    @Test
    public void interceptActivityLaunch_persistedActivity_differentLaunchDisplayId_returnsNull() {
        List<ComponentName> activities = List.of(
                ComponentName.unflattenFromString("com.example.app/com.example.app.MainActivity")
        );
        mInterceptor.setPersistentActivityOnRootTask(activities, mRootTaskToken2);
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(DISPLAY_ID_1);

        ActivityInterceptorInfoWrapper info =
                createActivityInterceptorInfoWithMainIntent(activities.get(0).getPackageName(),
                        activities.get(0).getClassName(), /* options= */ options);

        ActivityInterceptResultWrapper result =
                mInterceptor.onInterceptActivityLaunch(info);
        assertThat(result).isNull();
    }

    @Test
    public void interceptActivityLaunch_persistedActivity_setsLaunchRootTask() {
        List<ComponentName> activities = List.of(
                ComponentName.unflattenFromString("com.example.app/com.example.app.MainActivity"),
                ComponentName.unflattenFromString("com.example.app2/com.example.app2.MainActivity")
        );
        mInterceptor.setPersistentActivityOnRootTask(activities, mRootTaskToken1);
        ActivityInterceptorInfoWrapper info =
                createActivityInterceptorInfoWithMainIntent(activities.get(0).getPackageName(),
                        activities.get(0).getClassName());

        ActivityInterceptResultWrapper result =
                mInterceptor.onInterceptActivityLaunch(info);

        assertThat(result).isNotNull();
        assertThat(result.getInterceptResult().getActivityOptions().getLaunchRootTask())
                .isEqualTo(WindowContainer.fromBinder(mRootTaskToken1)
                        .mRemoteToken.toWindowContainerToken());
    }

    @Test
    public void interceptActivityLaunch_persistedActivity_differentUser_doesNothing() {
        List<ComponentName> activities = List.of(
                ComponentName.unflattenFromString("com.example.app/com.example.app.MainActivity"),
                ComponentName.unflattenFromString("com.example.app2/com.example.app2.MainActivity")
        );
        mInterceptor.setPersistentActivityOnRootTask(activities, mRootTaskToken1);
        ActivityInterceptorInfoWrapper info =
                createActivityInterceptorInfoWithMainIntent(activities.get(0).getPackageName(),
                        activities.get(0).getClassName(), /* userId= */ PASSENGER_USER_ID);

        ActivityInterceptResultWrapper result =
                mInterceptor.onInterceptActivityLaunch(info);

        assertThat(result).isNull();
    }

    @Test
    public void setPersistentActivity_nullLaunchRootTask_removesAssociation() {
        List<ComponentName> activities1 = List.of(
                ComponentName.unflattenFromString("com.example.app/com.example.app.MainActivity"),
                ComponentName.unflattenFromString("com.example.app2/com.example.app2.MainActivity")
        );
        List<ComponentName> activities2 = List.of(
                ComponentName.unflattenFromString("com.example.app3/com.example.app3.MainActivity"),
                ComponentName.unflattenFromString("com.example.app4/com.example.app4.MainActivity")
        );
        mInterceptor.setPersistentActivityOnRootTask(activities1, mRootTaskToken1);
        mInterceptor.setPersistentActivityOnRootTask(activities2, mRootTaskToken2);
        ActivityInterceptorInfoWrapper info =
                createActivityInterceptorInfoWithMainIntent(activities1.get(0).getPackageName(),
                        activities1.get(0).getClassName());

        mInterceptor.setPersistentActivityOnRootTask(activities1, null);

        ActivityInterceptResultWrapper result = mInterceptor.onInterceptActivityLaunch(info);
        assertThat(result).isNull();
        assertThat(mInterceptor.getActivityToRootTaskMap()).containsExactly(
                activities2.get(0), mRootTaskToken2,
                activities2.get(1), mRootTaskToken2
        );
    }

    @Test
    public void registerInterceptor_works() {
        when(mMockInfo.getIntent()).thenReturn(new Intent(Intent.ACTION_MAIN));
        mInterceptor.registerInterceptor(0, mMockInterceptor);
        mInterceptor.onInterceptActivityLaunch(mMockInfo);
        verify(mMockInterceptor, times(1)).onInterceptActivityLaunch(eq(mMockInfo));
    }

    @Test
    public void unregisterInterceptor_works() {
        when(mMockInfo.getIntent()).thenReturn(new Intent(Intent.ACTION_MAIN));
        mInterceptor.registerInterceptor(0, mMockInterceptor);
        mInterceptor.onInterceptActivityLaunch(mMockInfo);

        mInterceptor.unregisterInterceptor(0);
        mInterceptor.onInterceptActivityLaunch(mMockInfo);
        verify(mMockInterceptor, times(1)).onInterceptActivityLaunch(eq(mMockInfo));
    }

    @Test
    public void registerInterceptor_respectsOrder() {
        when(mMockInfo.getIntent()).thenReturn(new Intent(Intent.ACTION_MAIN));
        CarActivityInterceptorUpdatable interceptor1 = mock(CarActivityInterceptorUpdatable.class);
        CarActivityInterceptorUpdatable interceptor2 = mock(CarActivityInterceptorUpdatable.class);

        mInterceptor.registerInterceptor(0, interceptor1);
        mInterceptor.registerInterceptor(1, interceptor2);
        mInterceptor.onInterceptActivityLaunch(mMockInfo);

        InOrder inOrder = inOrder(interceptor1, interceptor2);
        inOrder.verify(interceptor1).onInterceptActivityLaunch(mMockInfo);
        inOrder.verify(interceptor2).onInterceptActivityLaunch(mMockInfo);
    }

    @Test
    public void registerInterceptor_existingIndex_throwsException() throws Exception {
        mInterceptor.registerInterceptor(0, mMockInterceptor);
        var thrown =
                assertThrows(IllegalArgumentException.class,
                        () -> mInterceptor.registerInterceptor(0, mMockInterceptor));
        assertThat(thrown).hasMessageThat()
                .contains("Another interceptor is registered at index: 0");
    }

    @Test
    public void unregisterInterceptor_throwsException() {
        var thrown =
                assertThrows(IllegalArgumentException.class,
                        () -> mInterceptor.unregisterInterceptor(0));
        assertThat(thrown).hasMessageThat()
                .contains("No interceptor is registered at index: 0");
    }
}
