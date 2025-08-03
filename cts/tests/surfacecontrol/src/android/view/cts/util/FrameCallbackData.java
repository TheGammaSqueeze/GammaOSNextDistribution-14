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

package android.view.cts.util;

public class FrameCallbackData {
    static {
        System.loadLibrary("ctssurfacecontrol_jni");
    }

    public static class FrameTimeline {
        FrameTimeline(long vsyncId, long expectedPresentTime, long deadline) {
            mVsyncId = vsyncId;
            mExpectedPresentTime = expectedPresentTime;
            mDeadline = deadline;
        }

        public long getVsyncId() {
            return mVsyncId;
        }

        public long getExpectedPresentTime() {
            return mExpectedPresentTime;
        }

        public long getDeadline() {
            return mDeadline;
        }

        private long mVsyncId;
        private long mExpectedPresentTime;
        private long mDeadline;
    }

    FrameCallbackData(
            FrameTimeline[] frameTimelines, int preferredFrameTimelineIndex) {
        mFrameTimelines = frameTimelines;
        mPreferredFrameTimelineIndex = preferredFrameTimelineIndex;
    }

    public FrameTimeline[] getFrameTimelines() {
        return mFrameTimelines;
    }

    public int getPreferredFrameTimelineIndex() {
        return mPreferredFrameTimelineIndex;
    }

    private FrameTimeline[] mFrameTimelines;
    private int mPreferredFrameTimelineIndex;

    public static native FrameCallbackData nGetFrameTimelines();
}
