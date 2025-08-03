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

package com.android.car.carlauncher.homescreen.audio.dialer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.car.carlauncher.homescreen.HomeCardInterface;
import com.android.car.carlauncher.homescreen.ui.CardHeader;
import com.android.car.carlauncher.homescreen.ui.DescriptiveTextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public class DialerCardPresenterTest {

    private static final CardHeader CARD_HEADER = new CardHeader("testAppName", /* appIcon = */
            null);
    private static final DescriptiveTextView CARD_CONTENT = new DescriptiveTextView(/* image = */
            null, "title", "subtitle");

    private DialerCardPresenter mPresenter;

    @Mock
    private DialerCardFragment mView;
    @Mock
    private DialerCardModel mModel;

    @Mock
    private DialerCardPresenter.OnInCallStateChangeListener mOnInCallStateChangeListener;

    private HomeCardInterface.Model.OnModelUpdateListener mOnModelUpdateListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mModel.getCardHeader()).thenReturn(CARD_HEADER);
        when(mModel.getCardContent()).thenReturn(CARD_CONTENT);
        when(mModel.hasActiveCall()).thenReturn(true);
        mPresenter = new DialerCardPresenter();
        mPresenter.setView(mView);
        mPresenter.setModel(mModel);
        mPresenter.setOnInCallStateChangeListener(mOnInCallStateChangeListener);
        mOnModelUpdateListener = mPresenter.mOnInCallModelUpdateListener;
    }

    @Test
    public void onModelUpdated_updatesFragment_hasActiveCall_callsStateChangedWithTrue() {
        when(mModel.hasActiveCall()).thenReturn(true);
        mPresenter.mHasActiveCall = false;

        mOnModelUpdateListener.onModelUpdate(mModel);

        verify(mView).updateHeaderView(CARD_HEADER);
        verify(mView).updateContentView(CARD_CONTENT);
        verify(mOnInCallStateChangeListener).onInCallStateChanged(true);
    }

    @Test
    public void onModelUpdated_updatesFragment_noActiveCall_callStateChangedWithFalse() {
        when(mModel.hasActiveCall()).thenReturn(false);
        mPresenter.mHasActiveCall = true;

        mOnModelUpdateListener.onModelUpdate(mModel);

        verify(mView).updateHeaderView(CARD_HEADER);
        verify(mView).updateContentView(CARD_CONTENT);
        verify(mOnInCallStateChangeListener).onInCallStateChanged(false);
    }

    @Test
    public void onModelUpdated_updatesFragment_noCallStateChange_doesNotCallStateChange() {
        when(mModel.hasActiveCall()).thenReturn(true);
        mPresenter.mHasActiveCall = true;

        mOnModelUpdateListener.onModelUpdate(mModel);

        verify(mView).updateHeaderView(CARD_HEADER);
        verify(mView).updateContentView(CARD_CONTENT);
        verify(mOnInCallStateChangeListener, never()).onInCallStateChanged(anyBoolean());
    }

    @Test
    public void onModelUpdated_nullHeaderAndContent_doesNotUpdateFragment() {
        when(mModel.getCardHeader()).thenReturn(null);
        when(mModel.getCardContent()).thenReturn(null);

        mOnModelUpdateListener.onModelUpdate(mModel);

        verify(mView, never()).updateContentView(any());
        verify(mView, never()).updateHeaderView(any());
    }
}
