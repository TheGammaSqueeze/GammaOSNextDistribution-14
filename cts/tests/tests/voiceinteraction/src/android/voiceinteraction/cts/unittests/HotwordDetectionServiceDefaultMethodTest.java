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

package android.voiceinteraction.cts.unittests;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertThrows;

import android.media.AudioFormat;
import android.os.ParcelFileDescriptor;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.HotwordDetectionService;
import android.voiceinteraction.cts.services.NoOpHotwordDetectionService;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link HotwordDetectionService} should have default implementation.
 */
@RunWith(AndroidJUnit4.class)
public class HotwordDetectionServiceDefaultMethodTest {

    @Test
    public void testHotwordDetectionService_onDetect_fromDsp()
            throws Throwable {
        assertThrows(UnsupportedOperationException.class,
                () -> new NoOpHotwordDetectionService().onDetect(
                        mock(AlwaysOnHotwordDetector.EventPayload.class),
                        /* timeoutMillis= */ 1000,
                        /* callback= */ null));
    }

    @Test
    public void testHotwordDetectionService_onDetect_fromExternalSource()
            throws Throwable {
        assertThrows(UnsupportedOperationException.class,
                () -> new NoOpHotwordDetectionService().onDetect(
                        mock(ParcelFileDescriptor.class),
                        new AudioFormat.Builder().build(),
                        /* options= */ null,
                        /* callback= */ null));
    }

    @Test
    public void testHotwordDetectionService_onDetect_fromMic()
            throws Throwable {
        assertThrows(UnsupportedOperationException.class,
                () -> new NoOpHotwordDetectionService().onDetect(/* callback= */ null));
    }
}
