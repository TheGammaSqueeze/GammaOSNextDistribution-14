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

package com.android.media.audiotestharness.server.javasound;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import com.android.media.audiotestharness.proto.AudioDeviceOuterClass;
import com.android.media.audiotestharness.proto.AudioFormatOuterClass;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.concurrent.ExecutorService;

import javax.sound.sampled.TargetDataLine;

@RunWith(JUnit4.class)
public class JavaAudioCapturerFactoryTests {

    @Rule public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock TargetDataLine mTargetDataLine;

    @Mock ExecutorService mExecutorService;

    JavaAudioCapturerFactory mJavaAudioCapturerFactory;

    @Before
    public void setUp() throws Exception {
        mJavaAudioCapturerFactory = new JavaAudioCapturerFactory(mExecutorService);
        when(mTargetDataLine.isOpen()).thenReturn(true);
    }

    @Test
    public void build_returnsNonNullForValidParameters() throws Exception {
        assertNotNull(
                mJavaAudioCapturerFactory.build(
                        AudioDeviceOuterClass.AudioDevice.getDefaultInstance(),
                        AudioFormatOuterClass.AudioFormat.getDefaultInstance(),
                        mTargetDataLine));
    }

    @Test(expected = NullPointerException.class)
    public void build_throwsNullPointerException_nullAudioDevice() throws Exception {
        mJavaAudioCapturerFactory.build(
                /* audioDevice= */ null,
                AudioFormatOuterClass.AudioFormat.getDefaultInstance(),
                mTargetDataLine);
    }

    @Test(expected = NullPointerException.class)
    public void build_throwsNullPointerException_nullAudioFormat() throws Exception {
        mJavaAudioCapturerFactory.build(
                AudioDeviceOuterClass.AudioDevice.getDefaultInstance(),
                /* audioFormat= */ null,
                mTargetDataLine);
    }

    @Test(expected = NullPointerException.class)
    public void build_throwsNullPointerException_nullTargetDataLine() throws Exception {
        mJavaAudioCapturerFactory.build(
                AudioDeviceOuterClass.AudioDevice.getDefaultInstance(),
                AudioFormatOuterClass.AudioFormat.getDefaultInstance(),
                /* targetDataLine= */ null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void build_throwsIllegalArgumentException_closedTargetDataLine() throws Exception {
        when(mTargetDataLine.isOpen()).thenReturn(false);
        mJavaAudioCapturerFactory.build(
                AudioDeviceOuterClass.AudioDevice.getDefaultInstance(),
                AudioFormatOuterClass.AudioFormat.getDefaultInstance(),
                mTargetDataLine);
    }
}
