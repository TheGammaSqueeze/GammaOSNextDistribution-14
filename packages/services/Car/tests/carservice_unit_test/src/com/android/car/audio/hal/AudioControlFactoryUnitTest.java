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

package com.android.car.audio.hal;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.os.IBinder;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public final class AudioControlFactoryUnitTest extends AbstractExtendedMockitoTestCase {
    private static final String TAG = AudioControlFactoryUnitTest.class.getSimpleName();

    @Mock
    private IBinder mBinder;

    @Mock
    private android.hardware.automotive.audiocontrol.V2_0.IAudioControl mIAudioControlV2;

    @Mock
    private android.hardware.automotive.audiocontrol.V1_0.IAudioControl mIAudioControlV1;

    public AudioControlFactoryUnitTest() {
        super(AudioControlFactory.TAG);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(AudioControlWrapperAidl.class)
                .spyStatic(AudioControlWrapperV2.class)
                .spyStatic(AudioControlWrapperV1.class);
    }

    @Test
    public void newAudioControl_forAudioControlWrapperAIDL_returnsInstance() {
        doReturn(mBinder).when(AudioControlWrapperAidl::getService);
        doReturn(null).when(AudioControlWrapperV1::getService);
        doReturn(null).when(AudioControlWrapperV2::getService);

        AudioControlWrapper wrapper = AudioControlFactory.newAudioControl();

        assertThat(wrapper).isNotNull();
        assertThat(wrapper).isInstanceOf(AudioControlWrapperAidl.class);
    }

    @Test
    public void newAudioControl_forAudioControlWrapperV2_returnsInstance() {
        doReturn(null).when(AudioControlWrapperAidl::getService);
        doReturn(null).when(AudioControlWrapperV1::getService);
        doReturn(mIAudioControlV2).when(AudioControlWrapperV2::getService);

        AudioControlWrapper wrapper = AudioControlFactory.newAudioControl();

        assertThat(wrapper).isNotNull();
        assertThat(wrapper).isInstanceOf(AudioControlWrapperV2.class);
    }

    @Test
    public void newAudioControl_forAudioControlWrapperV1_returnsInstance() {
        doReturn(null).when(AudioControlWrapperAidl::getService);
        doReturn(mIAudioControlV1).when(AudioControlWrapperV1::getService);
        doReturn(null).when(AudioControlWrapperV2::getService);

        AudioControlWrapper wrapper = AudioControlFactory.newAudioControl();

        assertThat(wrapper).isNotNull();
        assertThat(wrapper).isInstanceOf(AudioControlWrapperV1.class);
    }

    @Test
    public void newAudioControl_forNullAudioControlWrappers_fails() {
        doReturn(null).when(AudioControlWrapperAidl::getService);
        doReturn(null).when(AudioControlWrapperV1::getService);
        doReturn(null).when(AudioControlWrapperV2::getService);

        assertThrows(IllegalStateException.class, AudioControlFactory::newAudioControl);
    }
}
