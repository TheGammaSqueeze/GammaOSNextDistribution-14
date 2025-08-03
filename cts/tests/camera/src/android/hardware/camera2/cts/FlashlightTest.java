/*
 * Copyright 2015 The Android Open Source Project
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

package android.hardware.camera2.cts;

import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.cts.CameraTestUtils.HandlerExecutor;
import android.hardware.camera2.cts.testcases.Camera2AndroidTestCase;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.camera2.cts.helpers.StaticMetadata.CheckLevel;
import android.util.Log;
import android.os.Build;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import com.android.compatibility.common.util.PropertyUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;
import org.junit.runners.Parameterized;
import org.junit.runner.RunWith;
import org.junit.Test;

import static junit.framework.Assert.*;

/**
 * <p>Tests for flashlight API.</p>
 */

@RunWith(Parameterized.class)
public class FlashlightTest extends Camera2AndroidTestCase {
    private static final String TAG = "FlashlightTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final int TORCH_DURATION_MS = 1000;
    private static final int TORCH_TIMEOUT_MS = 3000;
    private static final int NUM_REGISTERS = 10;

    private ArrayList<String> mFlashCameraIdList;
    private ArrayList<String> mNoFlashCameraIdList;

    @Override
    public void setUp() throws Exception {
        //Use all camera ids for system camera testing since we count the number of callbacks here
        // and when mAdoptShellPerm == true, all camera ids will get callbacks.
        super.setUp(/*useAll*/true);

        // initialize the list of cameras that have a flash unit so it won't interfere with
        // flash tests.
        mFlashCameraIdList = new ArrayList<String>();
        mNoFlashCameraIdList = new ArrayList<String>();
        for (String id : getCameraIdsUnderTest()) {
            StaticMetadata info =
                    new StaticMetadata(mCameraManager.getCameraCharacteristics(id),
                                       CheckLevel.ASSERT, /*collector*/ null);
            if (info.hasFlash()) {
                mFlashCameraIdList.add(id);
            } else  {
                mNoFlashCameraIdList.add(id);
            }
        }
    }

    @Test
    public void testTurnOnTorchWithStrengthLevel() throws Exception {
        if (mNoFlashCameraIdList.size() != 0) {
            for (String id : mNoFlashCameraIdList) {
                CameraCharacteristics pc = mCameraManager.getCameraCharacteristics(id);
                assertNull(pc.get(CameraCharacteristics.FLASH_INFO_STRENGTH_DEFAULT_LEVEL));
                assertNull(pc.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL));
            }
        }

        if (mFlashCameraIdList.size() == 0)
            return;

        for (String id : mFlashCameraIdList) {
            resetTorchModeStatus(id);
        }

        for (String id: mFlashCameraIdList) {
            int maxLevel = 0;
            int defaultLevel = 0;
            int minLevel = 1;
            CameraCharacteristics pc = mCameraManager.getCameraCharacteristics(id);
            if (pc.get(CameraCharacteristics.FLASH_INFO_STRENGTH_DEFAULT_LEVEL) != null) {
                defaultLevel = pc.get(CameraCharacteristics.FLASH_INFO_STRENGTH_DEFAULT_LEVEL);
            }
            if (pc.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) != null) {
                maxLevel = pc.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL);
            }
            if (maxLevel > 1) {
                assertTrue(minLevel <= defaultLevel);
                assertTrue(defaultLevel <= maxLevel);
                int torchStrength = 0;
                CameraManager.TorchCallback torchListener = mock(CameraManager.TorchCallback.class);
                mCameraManager.registerTorchCallback(torchListener, mHandler);

                mCameraManager.turnOnTorchWithStrengthLevel(id, maxLevel);
                SystemClock.sleep(TORCH_DURATION_MS);
                torchStrength = mCameraManager.getTorchStrengthLevel(id);
                assertEquals(torchStrength, maxLevel);
                // Calling with same value twice to verify onTorchStrengthLevelChanged()
                // with maxLevel value is called only once.
                mCameraManager.turnOnTorchWithStrengthLevel(id, maxLevel);
                torchStrength = mCameraManager.getTorchStrengthLevel(id);
                assertEquals(torchStrength, maxLevel);

                mCameraManager.turnOnTorchWithStrengthLevel(id, defaultLevel);
                torchStrength = mCameraManager.getTorchStrengthLevel(id);
                assertEquals(torchStrength, defaultLevel);

                mCameraManager.turnOnTorchWithStrengthLevel(id, minLevel);
                torchStrength = mCameraManager.getTorchStrengthLevel(id);
                assertEquals(torchStrength, minLevel);

                try {
                    mCameraManager.turnOnTorchWithStrengthLevel(id, 0);
                    fail("turnOnTorchWithStrengthLevel with strengthLevel = 0 must fail.");
                } catch (IllegalArgumentException e) {
                    Log.v(TAG, e.getMessage());
                }

                try {
                    mCameraManager.turnOnTorchWithStrengthLevel(id, maxLevel + 1);
                    fail("turnOnTorchWithStrengthLevel with strengthLevel" + (maxLevel + 1) + " must fail.");
                } catch (IllegalArgumentException e) {
                    Log.v(TAG, e.getMessage());
                }

                // Turn off the torch and verify if the strength level gets
                // reset to default level.
                mCameraManager.setTorchMode(id, false);
                torchStrength = mCameraManager.getTorchStrengthLevel(id);
                assertEquals(torchStrength, defaultLevel);

                // verify corrected numbers of callbacks
                verify(torchListener, timeout(TORCH_TIMEOUT_MS).
                        times(1)).onTorchModeChanged(id, true);

                verify(torchListener,timeout(TORCH_TIMEOUT_MS).
                        times(1)).onTorchStrengthLevelChanged(id, maxLevel);
                verify(torchListener,timeout(TORCH_TIMEOUT_MS).
                        times(1)).onTorchStrengthLevelChanged(id, minLevel);
                verify(torchListener,timeout(TORCH_TIMEOUT_MS).
                        times(1)).onTorchStrengthLevelChanged(id, defaultLevel);

                verify(torchListener, timeout(TORCH_TIMEOUT_MS).
                        times(2)).onTorchModeChanged(id, false);

                mCameraManager.unregisterTorchCallback(torchListener);
            } else {
                Log.i(TAG, "Torch strength level adjustment is not supported.");
            }
        }
    }


    @Test
    public void testSetTorchModeOnOff() throws Exception {
        if (mFlashCameraIdList.size() == 0)
            return;

        // reset flash status for all devices with a flash unit
        for (String id : mFlashCameraIdList) {
            resetTorchModeStatus(id);
        }

        // turn on and off torch mode one by one
        for (String id : mFlashCameraIdList) {
            CameraManager.TorchCallback torchListener = mock(CameraManager.TorchCallback.class);
            mCameraManager.registerTorchCallback(torchListener, mHandler); // should get OFF

            mCameraManager.setTorchMode(id, true); // should get ON
            SystemClock.sleep(TORCH_DURATION_MS);
            mCameraManager.setTorchMode(id, false); // should get OFF

            // verify corrected numbers of callbacks
            verify(torchListener, timeout(TORCH_TIMEOUT_MS).
                    times(2)).onTorchModeChanged(id, false);
            verify(torchListener, timeout(TORCH_TIMEOUT_MS).
                    times(mFlashCameraIdList.size() + 1)).
                    onTorchModeChanged(anyString(), eq(false));
            verify(torchListener, timeout(TORCH_TIMEOUT_MS).
                    times(1)).onTorchModeChanged(id, true);
            verify(torchListener, timeout(TORCH_TIMEOUT_MS).
                    times(1)).onTorchModeChanged(anyString(), eq(true));
            verify(torchListener, after(TORCH_TIMEOUT_MS).never()).
                    onTorchModeUnavailable(anyString());

            mCameraManager.unregisterTorchCallback(torchListener);
        }

        // turn on all torch modes at once
        if (mFlashCameraIdList.size() >= 2) {
            CameraManager.TorchCallback torchListener = mock(CameraManager.TorchCallback.class);
            mCameraManager.registerTorchCallback(torchListener, mHandler); // should get OFF.

            for (String id : mFlashCameraIdList) {
                // should get ON for this ID.
                // may get OFF for previously-on IDs.
                mCameraManager.setTorchMode(id, true);
            }

            SystemClock.sleep(TORCH_DURATION_MS);

            for (String id : mFlashCameraIdList) {
                // should get OFF if not turned off previously.
                mCameraManager.setTorchMode(id, false);
            }

            verify(torchListener, timeout(TORCH_TIMEOUT_MS).times(mFlashCameraIdList.size())).
                    onTorchModeChanged(anyString(), eq(true));
            // one more off for each id due to callback registeration.
            verify(torchListener, timeout(TORCH_TIMEOUT_MS).
                    times(mFlashCameraIdList.size() * 2)).
                    onTorchModeChanged(anyString(), eq(false));

            mCameraManager.unregisterTorchCallback(torchListener);
        }
    }

    @Test
    public void testTorchCallback() throws Exception {
        testTorchCallback(/*useExecutor*/ false);
        testTorchCallback(/*useExecutor*/ true);
    }

    private void testTorchCallback(boolean useExecutor) throws Exception {
        if (mFlashCameraIdList.size() == 0)
            return;

        final Executor executor = useExecutor ? new HandlerExecutor(mHandler) : null;
        // reset torch mode status
        for (String id : mFlashCameraIdList) {
            resetTorchModeStatus(id);
        }

        CameraManager.TorchCallback torchListener = mock(CameraManager.TorchCallback.class);

        for (int i = 0; i < NUM_REGISTERS; i++) {
            // should get OFF for all cameras with a flash unit.
            if (useExecutor) {
                mCameraManager.registerTorchCallback(executor, torchListener);
            } else {
                mCameraManager.registerTorchCallback(torchListener, mHandler);
            }
            mCameraManager.unregisterTorchCallback(torchListener);
        }

        verify(torchListener, timeout(TORCH_TIMEOUT_MS).
                times(NUM_REGISTERS * mFlashCameraIdList.size())).
                onTorchModeChanged(anyString(), eq(false));
        verify(torchListener, after(TORCH_TIMEOUT_MS).never()).
                onTorchModeChanged(anyString(), eq(true));
        verify(torchListener, after(TORCH_TIMEOUT_MS).never()).
                onTorchModeUnavailable(anyString());

        // verify passing a null handler will raise IllegalArgumentException
        try {
            mCameraManager.registerTorchCallback(torchListener, null);
            mCameraManager.unregisterTorchCallback(torchListener);
            fail("should get IllegalArgumentException due to no handler");
        } catch (IllegalArgumentException e) {
            // expected exception
        }
    }

    @Test
    @AppModeFull(reason = "PropertyUtil methods don't work for instant apps")
    public void testCameraDeviceOpenAfterTorchOn() throws Exception {
        if (mFlashCameraIdList.size() == 0)
            return;

        for (String id : mFlashCameraIdList) {
            for (String idToOpen : getCameraIdsUnderTest()) {
                resetTorchModeStatus(id);

                CameraManager.TorchCallback torchListener =
                        mock(CameraManager.TorchCallback.class);

                // this will trigger OFF for each id in mFlashCameraIdList
                mCameraManager.registerTorchCallback(torchListener, mHandler);

                verify(torchListener, timeout(TORCH_TIMEOUT_MS).times(1)).
                        onTorchModeChanged(id, /*enabled*/false);

                // this will trigger ON for id
                mCameraManager.setTorchMode(id, true);
                SystemClock.sleep(TORCH_DURATION_MS);

                verify(torchListener, timeout(TORCH_TIMEOUT_MS).times(1)).
                        onTorchModeChanged(id, true);

                // if id == idToOpen, this will trigger UNAVAILABLE.
                // this may trigger UNAVAILABLE for any other id in mFlashCameraIdList
                openDevice(idToOpen);

                try {
                    if (PropertyUtil.getVendorApiLevel() > Build.VERSION_CODES.TIRAMISU) {
                        // Opening a camera device shouldn't result in
                        // onTorchModeChanged() being called. The number of
                        // invocations should remain at 1 for both ON and OFF.
                        verify(torchListener, timeout(TORCH_TIMEOUT_MS).times(1)).
                                onTorchModeChanged(id, false);
                        verify(torchListener, after(TORCH_TIMEOUT_MS).times(1)).
                                onTorchModeChanged(id, true);
                    }

                    // if id == idToOpen, this will trigger OFF.
                    // this may trigger OFF for any other id in mFlashCameraIdList.
                    closeDevice(idToOpen);

                    // this may trigger OFF for id if not received previously.
                    mCameraManager.setTorchMode(id, false);

                    verify(torchListener, timeout(TORCH_TIMEOUT_MS).times(1)).
                            onTorchModeChanged(id, true);
                    verify(torchListener, timeout(TORCH_TIMEOUT_MS).times(1)).
                            onTorchModeChanged(anyString(), eq(true));

                    verify(torchListener, timeout(TORCH_TIMEOUT_MS).atLeast(2)).
                            onTorchModeChanged(id, false);
                    verify(torchListener, atMost(3)).onTorchModeChanged(id, false);

                    verify(torchListener, timeout(TORCH_TIMEOUT_MS).
                            atLeast(mFlashCameraIdList.size())).
                            onTorchModeChanged(anyString(), eq(false));
                    verify(torchListener, atMost(mFlashCameraIdList.size() * 2 + 1)).
                            onTorchModeChanged(anyString(), eq(false));

                    if (hasFlash(idToOpen)) {
                        verify(torchListener, timeout(TORCH_TIMEOUT_MS).times(1)).
                                onTorchModeUnavailable(idToOpen);
                    }
                    verify(torchListener, atMost(mFlashCameraIdList.size())).
                                onTorchModeUnavailable(anyString());

                    mCameraManager.unregisterTorchCallback(torchListener);
                } finally {
                    closeDevice(idToOpen);
                }
            }
        }
    }

    @Test
    public void testTorchModeExceptions() throws Exception {
        // cameraIdsToTestTorch = all available camera ID + non-existing camera id +
        //                        non-existing numeric camera id + null
        String[] cameraIdsUnderTest = getCameraIdsUnderTest();
        String[] cameraIdsToTestTorch = new String[cameraIdsUnderTest.length + 3];
        System.arraycopy(cameraIdsUnderTest, 0, cameraIdsToTestTorch, 0, cameraIdsUnderTest.length);
        cameraIdsToTestTorch[cameraIdsUnderTest.length] = generateNonexistingCameraId();
        cameraIdsToTestTorch[cameraIdsUnderTest.length + 1] = generateNonexistingNumericCameraId();

        for (String idToOpen : cameraIdsUnderTest) {
            openDevice(idToOpen);
            try {
                for (String id : cameraIdsToTestTorch) {
                    try {
                        mCameraManager.setTorchMode(id, true);
                        SystemClock.sleep(TORCH_DURATION_MS);
                        mCameraManager.setTorchMode(id, false);
                        if (!hasFlash(id)) {
                            fail("exception should be thrown when turning on torch mode of a " +
                                    "camera without a flash");
                        } else if (id.equals(idToOpen)) {
                            fail("exception should be thrown when turning on torch mode of an " +
                                    "opened camera");
                        }
                    } catch (CameraAccessException e) {
                        int reason = e.getReason();
                        if ((hasFlash(id) &&  id.equals(idToOpen) &&
                                    reason == CameraAccessException.CAMERA_IN_USE) ||
                            (hasFlash(id) && !id.equals(idToOpen) &&
                                    reason == CameraAccessException.MAX_CAMERAS_IN_USE)) {
                            continue;
                        }
                        fail("(" + id + ") not expecting: " + e.getMessage() + "reason " + reason);
                    } catch (IllegalArgumentException e) {
                        if (hasFlash(id)) {
                            fail("not expecting IllegalArgumentException");
                        }
                    }
                }
            } finally {
                closeDevice(idToOpen);
            }
        }
    }

    private boolean hasFlash(String cameraId) {
        return mFlashCameraIdList.contains(cameraId);
    }

    // make sure the torch status is off.
    private void resetTorchModeStatus(String cameraId) throws Exception {
        TorchCallbackListener torchListener = new TorchCallbackListener(cameraId);

        mCameraManager.registerTorchCallback(torchListener, mHandler);
        mCameraManager.setTorchMode(cameraId, true);
        mCameraManager.setTorchMode(cameraId, false);

        torchListener.waitOnStatusChange(TorchCallbackListener.STATUS_ON);
        torchListener.waitOnStatusChange(TorchCallbackListener.STATUS_OFF);

        mCameraManager.unregisterTorchCallback(torchListener);
    }

    private String generateNonexistingCameraId() throws Exception {
        String nonExisting = "none_existing_camera";
        String[] cameraIdsUnderTest = getCameraIdsUnderTest();
        for (String id : cameraIdsUnderTest) {
            if (Arrays.asList(cameraIdsUnderTest).contains(nonExisting)) {
                nonExisting += id;
            } else {
                break;
            }
        }
        return nonExisting;
    }

    // return a non-existing and non-negative numeric camera id.
    private String generateNonexistingNumericCameraId() throws Exception {
        // We don't rely on getCameraIdsUnderTest() to generate a non existing camera id since
        // it doesn't give us an accurate reflection of which camera ids actually
        // exist. It just tells us the ones we're testing right now.
        String[] allCameraIds = mCameraManager.getCameraIdListNoLazy();
        int[] numericCameraIds = new int[allCameraIds.length];
        int size = 0;

        for (String cameraId : allCameraIds) {
            try {
                int value = Integer.parseInt(cameraId);
                if (value >= 0) {
                    numericCameraIds[size++] = value;
                }
            } catch (Throwable e) {
                // do nothing if camera id isn't an integer
            }
        }

        if (size == 0) {
            return "0";
        }

        Arrays.sort(numericCameraIds, 0, size);
        if (numericCameraIds[0] != 0) {
            return "0";
        }

        for (int i = 0; i < size - 1; i++) {
            if (numericCameraIds[i] + 1 < numericCameraIds[i + 1]) {
                return String.valueOf(numericCameraIds[i] + 1);
            }
        }

        if (numericCameraIds[size - 1] != Integer.MAX_VALUE) {
            return String.valueOf(numericCameraIds[size - 1] + 1);
        }

        fail("cannot find a non-existing and non-negative numeric camera id");
        return null;
    }

    private final class TorchCallbackListener extends CameraManager.TorchCallback {
        private static final String TAG = "TorchCallbackListener";
        private static final int STATUS_WAIT_TIMEOUT_MS = 3000;
        private static final int QUEUE_CAPACITY = 100;

        private String mCameraId;
        private ArrayBlockingQueue<Integer> mStatusQueue =
                new ArrayBlockingQueue<Integer>(QUEUE_CAPACITY);
        private ArrayBlockingQueue<Integer> mTorchStrengthQueue =
                new ArrayBlockingQueue<Integer>(QUEUE_CAPACITY);

        public static final int STATUS_UNAVAILABLE = 0;
        public static final int STATUS_OFF = 1;
        public static final int STATUS_ON = 2;

        public TorchCallbackListener(String cameraId) {
            // only care about events for this camera id.
            mCameraId = cameraId;
        }

        public void waitOnStatusChange(int status) throws Exception {
            while (true) {
                Integer s = mStatusQueue.poll(STATUS_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (s == null) {
                    fail("waiting for status " + status + " timed out");
                } else if (s.intValue() == status) {
                    return;
                }
            }
        }

        @Override
        public void onTorchModeUnavailable(String cameraId) {
            if (cameraId.equals(mCameraId)) {
                Integer s = new Integer(STATUS_UNAVAILABLE);
                try {
                    mStatusQueue.put(s);
                } catch (Throwable e) {
                    fail(e.getMessage());
                }
            }
        }

        @Override
        public void onTorchStrengthLevelChanged(String cameraId, int newStrengthLevel) {
            if (cameraId.equals(mCameraId)) {
                try {
                    mTorchStrengthQueue.put(newStrengthLevel);
                } catch (Throwable e) {
                    fail(e.getMessage());
                }
            }
        }

        @Override
        public void onTorchModeChanged(String cameraId, boolean enabled) {
            if (cameraId.equals(mCameraId)) {
                Integer s;
                if (enabled) {
                    s = new Integer(STATUS_ON);
                } else {
                    s = new Integer(STATUS_OFF);
                }
                try {
                    mStatusQueue.put(s);
                } catch (Throwable e) {
                    fail(e.getMessage());
                }
            }
        }
    }
}
