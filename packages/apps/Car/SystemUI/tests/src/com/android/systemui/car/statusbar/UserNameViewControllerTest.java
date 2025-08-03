/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.car.statusbar;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.graphics.drawable.Drawable;
import android.os.UserManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.test.filters.SmallTest;

import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.settings.UserTracker;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class UserNameViewControllerTest extends SysuiTestCase {
    private final UserInfo mUserInfo1 =
            new UserInfo(/* id= */ 0, /* name= */ "User 1", /* flags= */ 0);
    private final UserInfo mUserInfo2 =
            new UserInfo(/* id= */ 1, /* name= */ "User 2", /* flags= */ 0);

    private TextView mTextView;
    private ImageView mImageView;
    private View mView;
    private UserNameViewController mUserNameViewController;

    @Mock
    private UserTracker mUserTracker;
    @Mock
    private UserManager mUserManager;
    @Mock
    private Drawable mDrawable;
    @Mock
    private BroadcastDispatcher mBroadcastDispatcher;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mUserManager.getUserInfo(mUserInfo1.id)).thenReturn(mUserInfo1);
        when(mUserManager.getUserInfo(mUserInfo2.id)).thenReturn(mUserInfo2);

        mUserNameViewController = new UserNameViewController(getContext(), mUserTracker,
                mUserManager, mBroadcastDispatcher);
        spyOn(mUserNameViewController.mUserIconProvider);
        spyOn(mUserNameViewController.mUserNameViews);
        spyOn(mUserNameViewController.mUserIconViews);

        mView = new View(getContext());
        spyOn(mView);

        mTextView = new TextView(getContext());
        mTextView.setId(R.id.user_name_text);
        mImageView = new ImageView(getContext());
        spyOn(mImageView);

        doReturn(mTextView).when(mView).findViewById(eq(R.id.user_name_text));
        doReturn(mImageView).when(mView).findViewById(eq(R.id.user_icon));
    }

    @Test
    public void addUserNameViewToController_withTextView_updatesUserNameView() {
        when(mUserTracker.getUserId()).thenReturn(mUserInfo1.id);

        mUserNameViewController.addUserNameView(mTextView);

        assertEquals(mTextView.getText(), mUserInfo1.name);
    }

    @Test
    public void addUserNameViewToController_withTextAndImageView_updatesViews() {
        when(mUserTracker.getUserId()).thenReturn(mUserInfo1.id);
        when(mUserNameViewController.mUserIconProvider.getRoundedUserIcon(mUserInfo1, mContext))
                .thenReturn(mDrawable);

        mUserNameViewController.addUserNameView(mView);

        verify(mUserNameViewController.mUserNameViews).add(eq(mTextView));
        verify(mUserNameViewController.mUserIconViews).add(eq(mImageView));
        verify(mUserNameViewController.mUserIconProvider).setRoundedUserIcon(eq(mUserInfo1), any());
        verify(mImageView).setImageDrawable(eq(mDrawable));
    }

    @Test
    public void addUserNameViewToController_withNoTextView_doesNotUpdate() {
        View nullView = new View(getContext());

        mUserNameViewController.addUserNameView(nullView);

        assertEquals(mTextView.getText(), "");
        verify(mUserTracker, never()).addCallback(any(), any());
        verifyZeroInteractions(mUserManager);
    }

    @Test
    public void removeUserNameViewToController_textViewAndImageViewAdded_removeViews() {
        when(mUserTracker.getUserId()).thenReturn(mUserInfo1.id);
        when(mUserNameViewController.mUserIconProvider.getRoundedUserIcon(mUserInfo1, mContext))
                .thenReturn(mDrawable);
        mUserNameViewController.addUserNameView(mView);
        mUserNameViewController.removeUserNameView(mView);

        verify(mUserNameViewController.mUserNameViews).remove(eq(mTextView));
        verify(mUserNameViewController.mUserIconViews).remove(eq(mImageView));
    }

    @Test
    public void removeAll_withNoRegisteredListener_doesNotUnregister() {
        mUserNameViewController.removeAll();

        verifyZeroInteractions(mUserTracker);
        verifyZeroInteractions(mBroadcastDispatcher);
    }

    @Test
    public void userLifecycleListener_onUserSwitchLifecycleEvent_updatesUserNameView() {
        ArgumentCaptor<UserTracker.Callback> userTrackerCallbackCaptor =
                ArgumentCaptor.forClass(UserTracker.Callback.class);
        when(mUserTracker.getUserId()).thenReturn(mUserInfo1.id);
        // Add the initial TextView, which registers the UserLifecycleListener
        mUserNameViewController.addUserNameView(mTextView);
        assertEquals(mTextView.getText(), mUserInfo1.name);
        verify(mUserTracker).addCallback(userTrackerCallbackCaptor.capture(), any());

        userTrackerCallbackCaptor.getValue().onUserChanged(mUserInfo2.id, mContext);

        assertEquals(mTextView.getText(), mUserInfo2.name);
    }

    @Test
    public void userInfoChangedBroadcast_withUserNameViewInitialized_updatesUserNameView() {
        ArgumentCaptor<BroadcastReceiver> broadcastReceiverArgumentCaptor = ArgumentCaptor.forClass(
                BroadcastReceiver.class);
        when(mUserTracker.getUserId()).thenReturn(mUserInfo1.id);
        mUserNameViewController.addUserNameView(mTextView);
        assertEquals(mTextView.getText(), mUserInfo1.name);
        verify(mBroadcastDispatcher).registerReceiver(broadcastReceiverArgumentCaptor.capture(),
                any(), any(), any());

        reset(mUserTracker);
        when(mUserTracker.getUserId()).thenReturn(mUserInfo2.id);
        broadcastReceiverArgumentCaptor.getValue().onReceive(getContext(),
                new Intent(Intent.ACTION_USER_INFO_CHANGED));

        assertEquals(mTextView.getText(), mUserInfo2.name);
        verify(mUserTracker).getUserId();
    }

    @Test
    public void userInfoChangedBroadcast_whenUserNameIsSame_notSetRoundedUserIcon() {
        ArgumentCaptor<BroadcastReceiver> broadcastReceiverArgumentCaptor = ArgumentCaptor.forClass(
                BroadcastReceiver.class);
        when(mUserTracker.getUserId()).thenReturn(mUserInfo1.id);
        mUserNameViewController.addUserNameView(mView);
        assertEquals(mTextView.getText(), mUserInfo1.name);
        verify(mBroadcastDispatcher).registerReceiver(broadcastReceiverArgumentCaptor.capture(),
                any(), any(), any());
        reset(mUserNameViewController.mUserIconProvider);

        broadcastReceiverArgumentCaptor.getValue().onReceive(getContext(),
                new Intent(Intent.ACTION_USER_INFO_CHANGED));

        verify(mUserNameViewController.mUserIconProvider, never())
                .setRoundedUserIcon(eq(mUserInfo1), any());
    }

    @Test
    public void userInfoChangedBroadcast_whenUserNameIsChanged_setRoundedUserIcon() {
        ArgumentCaptor<BroadcastReceiver> broadcastReceiverArgumentCaptor = ArgumentCaptor.forClass(
                BroadcastReceiver.class);
        when(mUserTracker.getUserId()).thenReturn(mUserInfo1.id);
        mUserNameViewController.addUserNameView(mView);
        assertEquals(mTextView.getText(), mUserInfo1.name);
        verify(mBroadcastDispatcher).registerReceiver(broadcastReceiverArgumentCaptor.capture(),
                any(), any(), any());
        reset(mUserNameViewController.mUserIconProvider);
        mUserInfo1.name = "User X";

        broadcastReceiverArgumentCaptor.getValue().onReceive(getContext(),
                new Intent(Intent.ACTION_USER_INFO_CHANGED));

        verify(mUserNameViewController.mUserIconProvider).setRoundedUserIcon(eq(mUserInfo1), any());
    }
}
