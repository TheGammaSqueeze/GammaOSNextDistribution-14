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

package com.android.car.audio;

import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.Context;
import android.media.AudioManager;
import android.media.AudioManager.VolumeGroupCallback;

import androidx.test.core.app.ApplicationProvider;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.Executor;

@RunWith(MockitoJUnitRunner.class)
public final class CoreAudioVolumeGroupCallbackTest  extends AbstractExtendedMockitoTestCase {
    private static final String TAG = CoreAudioVolumeGroupCallbackTest.class.getSimpleName();

    private static final int VALID_VOLUME_GROUP_ID = 77;
    private static final int INVALID_VOLUME_GROUP_ID = 666;
    private static final String VALID_VOLUME_GROUP_NAME = "valid_group";

    private static final int TEST_EXPECTED_FLAGS =
            AudioManager.FLAG_FROM_KEY | AudioManager.FLAG_SHOW_UI;

    private Context mContext;

    @Mock
    AudioManager mMockAudioManager;
    @Mock
    CarVolumeInfoWrapper mMockVolumeInfoWrapper;

    ArgumentCaptor<VolumeGroupCallback> mVolumeGroupCallbackObserver =
            ArgumentCaptor.forClass(VolumeGroupCallback.class);

    private CoreAudioVolumeGroupCallback mCoreAudioVolumeGroupCallback;

    public CoreAudioVolumeGroupCallbackTest() {
        super(CoreAudioVolumeGroupCallback.TAG);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(CoreAudioHelper.class);
    }

    @Rule
    public final Expect expect = Expect.create();

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();

        mCoreAudioVolumeGroupCallback =
                new CoreAudioVolumeGroupCallback(mMockVolumeInfoWrapper, mMockAudioManager);
    }

    @Test
    public void registerVolumeGroupCallbackToAudioManager_withNullExecutor_fails() {
        String npeMessage = "executor must not be null";
        doThrow(new NullPointerException(npeMessage)).when(mMockAudioManager)
                .registerVolumeGroupCallback(eq(null), any());

        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                mCoreAudioVolumeGroupCallback.init(/* executor= */ null));

        expect.withMessage("register VolumeGroupCallback with null executor")
                .that(thrown).hasMessageThat().contains(npeMessage);
    }

    @Test
    public void registerVolumeGroupCallbackToAudioManager_success() {
        Executor executor = mContext.getMainExecutor();
        mCoreAudioVolumeGroupCallback.init(mContext.getMainExecutor());

        verify(mMockAudioManager).registerVolumeGroupCallback(eq(executor),
                eq(mCoreAudioVolumeGroupCallback));
    }

    @Test
    public void unregisterVolumeGroupCallbackToAudioManager_success() {
        mCoreAudioVolumeGroupCallback.init(mContext.getMainExecutor());

        mCoreAudioVolumeGroupCallback.release();

        verify(mMockAudioManager).unregisterVolumeGroupCallback(eq(mCoreAudioVolumeGroupCallback));
    }

    @Test
    public void instantiate_withNullAudioManager_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                 new CoreAudioVolumeGroupCallback(mMockVolumeInfoWrapper,
                         /* audioManager= */ null));
        expect.withMessage("Car AudioVolumeGroup Callback Construction")
                .that(thrown).hasMessageThat().contains("AudioManager cannot be null");
    }

    @Test
    public void instantiate_withNullVolumeInfoWrapper_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                new CoreAudioVolumeGroupCallback(/* carVolumeInfoWrapper= */ null,
                        mMockAudioManager));
        expect.withMessage("Car AudioVolumeGroup Callback Construction")
                .that(thrown).hasMessageThat().contains("CarVolumeInfoWrapper cannot be null");
    }

    @Test
    public void onAudioVolumeGroupChanged_withValidGroupId_dispatchesVolumeChange() {
        Executor executor = mContext.getMainExecutor();
        mCoreAudioVolumeGroupCallback.init(mContext.getMainExecutor());
        verify(mMockAudioManager).registerVolumeGroupCallback(
                eq(executor), mVolumeGroupCallbackObserver.capture());

        doReturn(VALID_VOLUME_GROUP_NAME)
                .when(() -> CoreAudioHelper.getVolumeGroupNameFromCoreId(
                        eq(VALID_VOLUME_GROUP_ID)));

        mVolumeGroupCallbackObserver.getValue().onAudioVolumeGroupChanged(
                VALID_VOLUME_GROUP_ID, TEST_EXPECTED_FLAGS);

        verify(mMockVolumeInfoWrapper).onAudioVolumeGroupChanged(PRIMARY_AUDIO_ZONE,
                VALID_VOLUME_GROUP_NAME, TEST_EXPECTED_FLAGS);
    }

    @Test
    public void onAudioVolumeGroupChanged_withValidGroupId_bailsOut() {
        Executor executor = mContext.getMainExecutor();
        mCoreAudioVolumeGroupCallback.init(mContext.getMainExecutor());
        verify(mMockAudioManager).registerVolumeGroupCallback(
                eq(executor), mVolumeGroupCallbackObserver.capture());

        doReturn(null)
                .when(() -> CoreAudioHelper.getVolumeGroupNameFromCoreId(
                        eq(INVALID_VOLUME_GROUP_ID)));

        mVolumeGroupCallbackObserver.getValue().onAudioVolumeGroupChanged(
                INVALID_VOLUME_GROUP_ID, TEST_EXPECTED_FLAGS);

        verify(mMockVolumeInfoWrapper, never()).onAudioVolumeGroupChanged(
                anyInt(), any(), anyInt());
    }
}
