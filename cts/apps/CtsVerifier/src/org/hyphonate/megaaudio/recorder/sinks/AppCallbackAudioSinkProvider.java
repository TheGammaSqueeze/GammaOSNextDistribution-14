/*
 * Copyright 2020 The Android Open Source Project
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
package org.hyphonate.megaaudio.recorder.sinks;

import org.hyphonate.megaaudio.recorder.AudioSink;
import org.hyphonate.megaaudio.recorder.AudioSinkProvider;
import org.hyphonate.megaaudio.recorder.NativeAudioSink;

public class AppCallbackAudioSinkProvider implements AudioSinkProvider {
    private AppCallback mCallbackObj;
    private long mOboeSinkObj;

    public AppCallbackAudioSinkProvider(AppCallback callback) {
        mCallbackObj = callback;
    }

    public AudioSink allocJavaSink() {
        return new AppCallbackAudioSink(mCallbackObj);
        // return allocNativeSink();
    }

    @Override
    public NativeAudioSink allocNativeSink() {
        return new NativeAudioSink(mOboeSinkObj = allocOboeSinkN(mCallbackObj));
    }

    private native long allocOboeSinkN(AppCallback callbackObj);

    public void releaseJNIResources() {
        releaseJNIResourcesN(mOboeSinkObj);
    }

    private native void releaseJNIResourcesN(long oboeSink);
}
