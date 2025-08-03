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

package android.media.cts;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.AssumptionViolatedException;
import static org.junit.Assert.assertTrue;
/**
 * MediaCodecAsyncHelper class
 */
public class MediaCodecAsyncHelper {
    private static final String TAG = "MediaCodecAsyncHelper";
    // The test should fail if the codec never produces output frames for the truncated input.
    // Time out processing, as we have no way to query whether the decoder will produce output.
    private static final int TIMEOUT_MS = 60000;  // 1 minute
    public static void runThread(Consumer<Boolean> consumer, boolean secure)
            throws InterruptedException {
        Thread thread = new Thread(new Runnable() {
            public void run() {
                consumer.accept(secure);
            }
        });
        final AtomicReference<Throwable> throwable = new AtomicReference<>();
        thread.setUncaughtExceptionHandler((Thread t, Throwable e) -> {
            throwable.set(e);
        });
        thread.start();
        thread.join(TIMEOUT_MS);
        Throwable t = throwable.get();
        if (t != null) {
            if (t instanceof AssumptionViolatedException ) {
                throw new AssumptionViolatedException("Assumption Violated", t);
            } else {
                throw new RuntimeException("Test failed", t);
            }
        }
    }

    public static interface InputSlotListener {
        public void onInputSlot(MediaCodec codec, int index) throws Exception;
    }

    public static interface OutputSlotListener {
        // Returns true if End of Stream is met
        public boolean onOutputSlot(MediaCodec codec, int index,
            MediaCodec.BufferInfo info) throws Exception;
    }

    public static class SlotEvent {
        public SlotEvent(boolean input, int index) {
            this.input = input;
            this.index = index;
            this.info = null;
        }
        public SlotEvent(boolean input, int index, MediaCodec.BufferInfo info) {
            this.input = input;
            this.index = index;
            this.info = info;
        }
        public final boolean input;
        public final int index;
        public final MediaCodec.BufferInfo info;
    }

    public static String getDecoderForType(String mime, boolean secure) {
        String firstDecoderName = null;
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
        MediaCodecInfo[] codecInfos = codecList.getCodecInfos();
        for (MediaCodecInfo info : codecInfos) {
            if (info.isEncoder()) {
                continue;
            }
            String capability = MediaCodecInfo.CodecCapabilities.FEATURE_AdaptivePlayback;
            if (secure) {
                capability = MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback;
            }
            String[] supportedTypes = info.getSupportedTypes();
            for (int j = 0; j < supportedTypes.length; ++j) {
                if (supportedTypes[j].equalsIgnoreCase(mime)) {
                    if (info.getCapabilitiesForType(mime).isFeatureSupported(
                            capability)) {
                        return info.getName();
                    } else if (secure && info.getName().endsWith(".secure")) {
                        return info.getName();
                    } else if (firstDecoderName == null) {
                        firstDecoderName = info.getName();
                    }
                }
            }
        }
        if (secure) {
          return null;
        }
        return firstDecoderName;
    }
}
