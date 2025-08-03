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

package org.hyphonate.megaaudio.recorder;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

/**
 * Defines a super-class for apps to receive notifications of recording events. Concrete
 * subclasses need to implement the <code>handleMessage(Message)</code> method.
 */
public class JavaSinkHandler extends Handler {
    @SuppressWarnings("unused")
    private static final String TAG = JavaSinkHandler.class.getSimpleName();
    @SuppressWarnings("unused")
    private static final boolean LOG = false;

    protected JavaRecorder mRecorder;

    private AudioSink mSink = null;

    public JavaSinkHandler(JavaRecorder recorder, AudioSink sink, Looper looper) {
        super(looper);
        mRecorder = recorder;
        mSink = sink;
    }

    /**
     * Recording Event IDs.
     */
    /** Sent when recording has started */
    public static final int MSG_START = 0;
    /** Sent when a recording buffer has been filled */
    public static final int MSG_BUFFER_FILL = 1;
    /** Sent when recording has been stopped */
    public static final int MSG_STOP = 2;

    @Override
    public void handleMessage (Message msg) {
        switch (msg.what) {
            case MSG_START:
                if (mSink != null) {
                    mSink.start();
                }
                break;

            case MSG_BUFFER_FILL:
                if (mSink != null) {
                    mSink.push(mRecorder.getDataBuffer(),
                            mRecorder.getNumExchangeFrames(), mRecorder.getChannelCount());
                }
                break;

            case MSG_STOP:
                if (mSink != null) {
                    // arg1 has the number of samples from the last read.
                    mSink.stop(msg.arg1);
                }
                break;
        }
    }
}
