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

package com.android.car.carlauncher.homescreen.audio.media;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.car.carlauncher.homescreen.HomeCardInterface;
import com.android.car.carlauncher.homescreen.audio.MediaViewModel;
import com.android.car.carlauncher.homescreen.ui.CardHeader;
import com.android.car.carlauncher.homescreen.ui.DescriptiveTextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public class MediaCardPresenterTest {

    private static final CardHeader CARD_HEADER = new CardHeader("testAppName", /* appIcon = */
            null);
    private static final DescriptiveTextView CARD_CONTENT = new DescriptiveTextView(/* image = */
            null, "title", "subtitle");

    private MediaCardPresenter mPresenter;

    @Mock
    private MediaCardFragment mView;
    @Mock
    private MediaViewModel mModel;

    private HomeCardInterface.Model.OnModelUpdateListener mOnModelUpdateListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mModel.getCardHeader()).thenReturn(CARD_HEADER);
        when(mModel.getCardContent()).thenReturn(CARD_CONTENT);
        mPresenter = new MediaCardPresenter();
        mPresenter.setView(mView);
        mPresenter.setModel(mModel);
        mOnModelUpdateListener = mPresenter.mOnMediaModelUpdateListener;
    }

    @Test
    public void onModelUpdated_updatesFragment() {
        mOnModelUpdateListener.onModelUpdate(mModel);

        verify(mView).updateHeaderView(CARD_HEADER);
        verify(mView).updateContentView(CARD_CONTENT);
    }

    @Test
    public void onModelUpdated_nullHeaderAndContent_doesNotUpdateFragment() {
        when(mModel.getCardHeader()).thenReturn(null);
        when(mModel.getCardContent()).thenReturn(null);

        mOnModelUpdateListener.onModelUpdate(mModel);

        verify(mView, never()).updateContentView(any());
        verify(mView, never()).updateHeaderView(any());
    }

    @Test
    public void onModelUpdated_activePhoneCall_doesNotUpdateFragment() {
        //mimic active phone call in presenter
        mPresenter.setShowMedia(false);

        // send MediaModel update during ongoing call
        mOnModelUpdateListener.onModelUpdate(mModel);

        //verify call
        verify(mView, never()).updateHeaderView(any());
        verify(mView, never()).updateContentView(any());
    }
}
