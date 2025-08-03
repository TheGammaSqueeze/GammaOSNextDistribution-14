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

package android.car.test.util;

import static android.car.test.mocks.JavaMockitoHelper.silentAwait;

import android.car.media.CarAudioManager;
import android.car.media.CarVolumeGroupEvent;
import android.car.media.CarVolumeGroupEventCallback;
import android.util.Log;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Test utility class to be used by CarAudioManager tests.
 */
public final class CarAudioManagerTestUtils {

    private static final String TAG = CarAudioManagerTestUtils.class.getSimpleName();

    private static final long WAIT_TIMEOUT_MS = 5_000;

    public static final class SyncCarVolumeCallback extends CarAudioManager.CarVolumeCallback {

        private final CountDownLatch mGroupVolumeChangeLatch = new CountDownLatch(1);
        private final CountDownLatch mGroupMuteChangeLatch = new CountDownLatch(1);
        private final CountDownLatch mMasterMuteChangeLatch = new CountDownLatch(1);

        public int zoneId;
        public int groupId;

        public boolean receivedGroupVolumeChanged() {
            return silentAwait(mGroupVolumeChangeLatch, WAIT_TIMEOUT_MS);
        }

        public boolean receivedGroupMuteChanged() {
            return silentAwait(mGroupMuteChangeLatch, WAIT_TIMEOUT_MS);
        }

        public boolean receivedMasterMuteChanged() {
            return silentAwait(mMasterMuteChangeLatch, WAIT_TIMEOUT_MS);
        }

        @Override
        public void onGroupVolumeChanged(int zoneId, int groupId, int flags) {
            Log.v(TAG, "onGroupVolumeChanged");
            mGroupVolumeChangeLatch.countDown();
        }

        @Override
        public void onMasterMuteChanged(int zoneId, int flags) {
            Log.v(TAG, "onMasterMuteChanged");
            mMasterMuteChangeLatch.countDown();
        }

        @Override
        public void onGroupMuteChanged(int zoneId, int groupId, int flags) {
            Log.v(TAG, "onGroupMuteChanged");
            this.zoneId = zoneId;
            this.groupId = groupId;
            mGroupMuteChangeLatch.countDown();
        }
    }

    public static final class TestCarVolumeGroupEventCallback implements
            CarVolumeGroupEventCallback {

        private CountDownLatch mVolumeGroupEventLatch = new CountDownLatch(1);
        List<CarVolumeGroupEvent> mEvents;

        public boolean receivedVolumeGroupEvents() throws InterruptedException {
            return silentAwait(mVolumeGroupEventLatch, WAIT_TIMEOUT_MS);
        }

        @Override
        public void onVolumeGroupEvent(List<CarVolumeGroupEvent> volumeGroupEvents) {
            mEvents = volumeGroupEvents;
            Log.v(TAG, "onVolumeGroupEvent events " + volumeGroupEvents);
            mVolumeGroupEventLatch.countDown();
        }
    }

    private CarAudioManagerTestUtils() {
    }
}
