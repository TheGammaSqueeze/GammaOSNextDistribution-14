/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.car.media.ICarVolumeCallback;
import android.car.test.AbstractExpectableTestCase;
import android.os.Binder;
import android.os.RemoteException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class CarVolumeCallbackHandlerTest extends AbstractExpectableTestCase {
    private static final int ZONE_ID = 2;
    private static final int GROUP_ID = 5;
    private static final int FLAGS = 0;

    private CarVolumeCallbackHandler mHandler;
    private TestCarVolumeCallback mCallback1;
    private TestCarVolumeCallback mCallback2;

    @Before
    public void setUp() {
        mHandler = new CarVolumeCallbackHandler();
        int uid = Binder.getCallingUid();
        mCallback1 = new TestCarVolumeCallback();
        mHandler.registerCallback(mCallback1.asBinder(), uid, true);
        mCallback2 = new TestCarVolumeCallback();
        mHandler.registerCallback(mCallback2.asBinder(), uid, true);
    }

    @After
    public void tearDown() {
        int uid = Binder.getCallingUid();
        mHandler.unregisterCallback(mCallback1.asBinder(), uid);
        mHandler.unregisterCallback(mCallback2.asBinder(), uid);
    }

    @Test
    public void onVolumeGroupChange_callsAllRegisteredCallbacks() throws Exception {
        mHandler.onVolumeGroupChange(ZONE_ID, GROUP_ID, FLAGS);

        expectWithMessage("Car group volume changed for registered callback")
            .that(mCallback1.receivedGroupVolumeChanged())
            .isTrue();
        expectWithMessage("Group volume changed zoneId")
            .that(mCallback1.mZoneId).isEqualTo(ZONE_ID);
        expectWithMessage("Group volume changed  groupId")
            .that(mCallback1.mGroupId).isEqualTo(GROUP_ID);
        expectWithMessage("Group volume changed  flags")
            .that(mCallback1.mFlags).isEqualTo(FLAGS);

        expectWithMessage("Car group volume changed for registered callback")
            .that(mCallback2.receivedGroupVolumeChanged())
            .isTrue();
        expectWithMessage("Group volume changed zoneId")
            .that(mCallback2.mZoneId).isEqualTo(ZONE_ID);
        expectWithMessage("Group volume changed groupId")
            .that(mCallback2.mGroupId).isEqualTo(GROUP_ID);
        expectWithMessage("Group volume changed flags")
            .that(mCallback2.mFlags).isEqualTo(FLAGS);
    }

    @Test
    public void onVolumeGroupChange_doesNotCallUnregisteredCallbacks() throws Exception {
        int uid = Binder.getCallingUid();
        mHandler.unregisterCallback(mCallback1.asBinder(), uid);
        mHandler.onVolumeGroupChange(ZONE_ID, GROUP_ID, FLAGS);

        expectWithMessage("Car group volume changed for unregistered callback")
            .that(mCallback1.receivedGroupVolumeChanged())
            .isFalse();
        expectWithMessage("Car group volume changed for registered callback")
            .that(mCallback2.receivedGroupVolumeChanged())
            .isTrue();
    }

    @Test
    public void onVolumeGroupChange_doesNotCallDeprioritizedCallback() throws Exception {
        int uid = Binder.getCallingUid();
        mHandler.checkAndRepriotize(uid, false);
        mHandler.onVolumeGroupChange(ZONE_ID, GROUP_ID, FLAGS);

        expectWithMessage("Car group volume changed for deprioritized callback")
            .that(mCallback1.receivedGroupVolumeChanged())
            .isFalse();
    }

    @Test
    public void onVolumeGroupChange_callReprioritizedCallback() throws Exception {
        int uid = Binder.getCallingUid();
        mHandler.unregisterCallback(mCallback1.asBinder(), uid);
        mHandler.unregisterCallback(mCallback2.asBinder(), uid);
        mCallback2 = new TestCarVolumeCallback();
        mHandler.registerCallback(mCallback2.asBinder(), uid, false);
        mHandler.checkAndRepriotize(uid, true);

        mHandler.onVolumeGroupChange(ZONE_ID, GROUP_ID, FLAGS);

        expectWithMessage("Car group volume changed for reprioritized callback")
            .that(mCallback2.receivedGroupVolumeChanged())
            .isTrue();
    }

    @Test
    public void onVolumeGroupChange_doesNotCallThrows() throws Exception {
        mCallback1.setThrowFlag(/* throwFlag= */true);
        mHandler.onVolumeGroupChange(ZONE_ID, GROUP_ID, FLAGS);

        expectWithMessage("Car group volume changed for callback throws exception")
            .that(mCallback1.receivedGroupVolumeChanged())
            .isFalse();
        expectWithMessage("Car group volume changed for registered callback")
            .that(mCallback2.receivedGroupVolumeChanged())
            .isTrue();
    }

    @Test
    public void onGroupMuteChange_callsAllRegisteredCallbacks() throws Exception {
        mHandler.onGroupMuteChange(ZONE_ID, GROUP_ID, FLAGS);

        expectWithMessage("Car group mute changed for registered callback")
            .that(mCallback1.receivedGroupMuteChanged())
            .isTrue();
        expectWithMessage("Car group mute changed for registered callback")
            .that(mCallback2.receivedGroupMuteChanged())
            .isTrue();
    }

    @Test
    public void onGroupMuteChanged_doesNotCallUnregisteredCallbacks() throws Exception {
        int uid = Binder.getCallingUid();
        mHandler.unregisterCallback(mCallback1.asBinder(), uid);
        mHandler.onGroupMuteChange(ZONE_ID, GROUP_ID, FLAGS);

        expectWithMessage("Car group mute changed for unregistered callback")
            .that(mCallback1.receivedGroupMuteChanged())
            .isFalse();
        expectWithMessage("Car group mute changed for registered callback")
            .that(mCallback2.receivedGroupMuteChanged())
            .isTrue();
    }

    @Test
    public void onGroupMuteChanged_doesNotCallThrows() throws Exception {
        mCallback1.setThrowFlag(/* throwFlag= */true);
        mHandler.onGroupMuteChange(ZONE_ID, GROUP_ID, FLAGS);

        expectWithMessage("Car group mute changed for callback throws exception")
            .that(mCallback1.receivedGroupMuteChanged())
            .isFalse();
        expectWithMessage("Car group mute changed for registered callback")
            .that(mCallback2.receivedGroupMuteChanged())
            .isTrue();
    }

    @Test
    public void onMasterMuteChanged_callsAllRegisteredCallbacks() throws Exception {
        mHandler.onMasterMuteChanged(ZONE_ID, FLAGS);

        expectWithMessage("Car master mute changed for registered callback")
            .that(mCallback1.receivedMasterMuteChanged())
            .isTrue();
        expectWithMessage("Car master mute changed for registered callback")
            .that(mCallback2.receivedMasterMuteChanged())
            .isTrue();
    }

    @Test
    public void onMasterMuteChanged_doesNotCallThrows() throws Exception {
        mCallback1.setThrowFlag(/* throwFlag= */true);
        mHandler.onMasterMuteChanged(ZONE_ID, FLAGS);

        expectWithMessage("Car master mute changed for callback throws exception")
            .that(mCallback1.receivedMasterMuteChanged())
            .isFalse();
        expectWithMessage("Car master mute changed for registered callback")
            .that(mCallback2.receivedMasterMuteChanged())
            .isTrue();
    }

    private static final class TestCarVolumeCallback extends ICarVolumeCallback.Stub {
        private boolean mThrowFlag;
        private final CountDownLatch mGroupVolumeChangeLatch = new CountDownLatch(1);
        private final CountDownLatch mGroupMuteChangeLatch = new CountDownLatch(1);
        private final CountDownLatch mMasterMuteChangeLatch = new CountDownLatch(1);
        private static final long TEST_CALLBACK_TIMEOUT_MS = 100;
        int mZoneId = -1;
        int mGroupId = -1;
        int mFlags = -1;

        public void setThrowFlag(boolean throwFlag) {
            this.mThrowFlag = throwFlag;
        }

        @Override
        public void onGroupVolumeChanged(int zoneId, int groupId, int flags)
                throws RemoteException {
            if (mThrowFlag) {
                throw new RemoteException();
            }
            mZoneId = zoneId;
            mGroupId = groupId;
            mFlags = flags;
            mGroupVolumeChangeLatch.countDown();
        }

        @Override
        public void onGroupMuteChanged(int zoneId, int groupId, int flags) throws RemoteException {
            if (mThrowFlag) {
                throw new RemoteException();
            }
            mZoneId = zoneId;
            mGroupId = groupId;
            mFlags = flags;
            mGroupMuteChangeLatch.countDown();
        }

        @Override
        public void onMasterMuteChanged(int zoneId, int flags) throws RemoteException {
            if (mThrowFlag) {
                throw new RemoteException();
            }
            mZoneId = zoneId;
            mFlags = flags;
            mMasterMuteChangeLatch.countDown();
        }

        private boolean receivedGroupVolumeChanged() throws InterruptedException {
            return mGroupVolumeChangeLatch.await(TEST_CALLBACK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        private boolean receivedGroupMuteChanged() throws InterruptedException {
            return mGroupMuteChangeLatch.await(TEST_CALLBACK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        private boolean receivedMasterMuteChanged() throws InterruptedException {
            return mMasterMuteChangeLatch.await(TEST_CALLBACK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

    }
}
