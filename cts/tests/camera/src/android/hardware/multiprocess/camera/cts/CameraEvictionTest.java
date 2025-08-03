/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.hardware.multiprocess.camera.cts;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static org.mockito.Mockito.*;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.cts.CameraTestUtils.HandlerExecutor;
import android.hardware.cts.CameraCtsActivity;
import android.os.Handler;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.server.wm.NestedShellPermission;
import android.server.wm.TestTaskOrganizer;
import android.server.wm.WindowManagerStateHelper;
import android.test.ActivityInstrumentationTestCase2;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;

import androidx.test.InstrumentationRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;

/**
 * Tests for multi-process camera usage behavior.
 */
public class CameraEvictionTest extends ActivityInstrumentationTestCase2<CameraCtsActivity> {

    public static final String TAG = "CameraEvictionTest";

    private static final int OPEN_TIMEOUT = 2000; // Timeout for camera to open (ms).
    private static final int SETUP_TIMEOUT = 5000; // Remote camera setup timeout (ms).
    private static final int EVICTION_TIMEOUT = 1000; // Remote camera eviction timeout (ms).
    private static final int WAIT_TIME = 2000; // Time to wait for process to launch (ms).
    private static final int UI_TIMEOUT = 10000; // Time to wait for UI event before timeout (ms).
    // Time to wait for onCameraAccessPrioritiesChanged (ms).
    private static final int CAMERA_ACCESS_TIMEOUT = 2000;

    // CACHED_APP_MAX_ADJ - FG oom score
    private static final int CACHED_APP_VS_FG_OOM_DELTA = 999;
    ErrorLoggingService.ErrorServiceConnection mErrorServiceConnection;

    private ActivityManager mActivityManager;
    private Context mContext;
    private Camera mCamera;
    private CameraDevice mCameraDevice;
    private UiAutomation mUiAutomation;
    private final Object mLock = new Object();
    private boolean mCompleted = false;
    private int mProcessPid = -1;
    private WindowManagerStateHelper mWmState = new WindowManagerStateHelper();
    private TestTaskOrganizer mTaskOrganizer;

    /** Load jni on initialization */
    static {
        System.loadLibrary("ctscamera2_jni");
    }

    private static native long initializeAvailabilityCallbacksNative();
    private static native int getAccessCallbacksCountAndResetNative(long context);
    private static native long releaseAvailabilityCallbacksNative(long context);

    public CameraEvictionTest() {
        super(CameraCtsActivity.class);
    }

    public static class StateCallbackImpl extends CameraDevice.StateCallback {
        CameraDevice mCameraDevice;

        public StateCallbackImpl() {
            super();
        }

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            synchronized(this) {
                mCameraDevice = cameraDevice;
            }
            Log.i(TAG, "CameraDevice onOpened called for main CTS test process.");
        }

        @Override
        public void onClosed(CameraDevice camera) {
            super.onClosed(camera);
            synchronized(this) {
                mCameraDevice = null;
            }
            Log.i(TAG, "CameraDevice onClosed called for main CTS test process.");
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            synchronized(this) {
                mCameraDevice = null;
            }
            Log.i(TAG, "CameraDevice onDisconnected called for main CTS test process.");

        }

        @Override
        public void onError(CameraDevice cameraDevice, int i) {
            Log.i(TAG, "CameraDevice onError called for main CTS test process with error " +
                    "code: " + i);
        }

        public synchronized CameraDevice getCameraDevice() {
            return mCameraDevice;
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mCompleted = false;
        getActivity();
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mContext = InstrumentationRegistry.getTargetContext();
        System.setProperty("dexmaker.dexcache", mContext.getCacheDir().toString());
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        mErrorServiceConnection = new ErrorLoggingService.ErrorServiceConnection(mContext);
        mErrorServiceConnection.start();
        NestedShellPermission.run(() -> {
            mTaskOrganizer = new TestTaskOrganizer();
        });
    }

    @Override
    protected void tearDown() throws Exception {
        if (mProcessPid != -1) {
            android.os.Process.killProcess(mProcessPid);
            mProcessPid = -1;
        }
        if (mErrorServiceConnection != null) {
            mErrorServiceConnection.stop();
            mErrorServiceConnection = null;
        }
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        mContext = null;
        mActivityManager = null;
        mTaskOrganizer.unregisterOrganizerIfNeeded();
        super.tearDown();
    }

    /**
     * Test basic eviction scenarios for the Camera1 API.
     */
    public void testCamera1ActivityEviction() throws Throwable {
        testAPI1ActivityEviction(Camera1Activity.class, "camera1ActivityProcess");
    }

    public void testBasicCamera2ActivityEviction() throws Throwable {
        testBasicCamera2ActivityEvictionInternal(/*lowerPriority*/ false);
    }

    public void testBasicCamera2ActivityEvictionOomScoreOffset() throws Throwable {
        testBasicCamera2ActivityEvictionInternal(/*lowerPriority*/ true);
    }
    /**
     * Test basic eviction scenarios for the Camera2 API.
     */
    private void testBasicCamera2ActivityEvictionInternal(boolean lowerPriority) throws Throwable {
        UiAutomation uiAutomation = null;
        if (lowerPriority && mUiAutomation != null) {
            mUiAutomation.adoptShellPermissionIdentity();
        }
        CameraManager manager = mContext.getSystemService(CameraManager.class);
        assertNotNull("Unable to get CameraManager service!", manager);
        String[] cameraIds = manager.getCameraIdListNoLazy();

        if (cameraIds.length == 0) {
            Log.i(TAG, "Skipping testBasicCamera2ActivityEviction, device has no cameras.");
            return;
        }

        assertTrue("Context has no main looper!", mContext.getMainLooper() != null);

        // Setup camera manager
        String chosenCamera = cameraIds[0];
        Handler cameraHandler = new Handler(mContext.getMainLooper());
        final CameraManager.AvailabilityCallback mockAvailCb =
                mock(CameraManager.AvailabilityCallback.class);

        manager.registerAvailabilityCallback(mockAvailCb, cameraHandler);

        Thread.sleep(WAIT_TIME);

        verify(mockAvailCb, times(1)).onCameraAvailable(chosenCamera);
        verify(mockAvailCb, never()).onCameraUnavailable(chosenCamera);

        // Setup camera device
        final CameraDevice.StateCallback spyStateCb = spy(new StateCallbackImpl());
        manager.openCamera(chosenCamera, spyStateCb, cameraHandler);

        verify(spyStateCb, timeout(OPEN_TIMEOUT).times(1)).onOpened(any(CameraDevice.class));
        verify(spyStateCb, never()).onClosed(any(CameraDevice.class));
        verify(spyStateCb, never()).onDisconnected(any(CameraDevice.class));
        verify(spyStateCb, never()).onError(any(CameraDevice.class), anyInt());

        // Open camera from remote process
        startRemoteProcess(Camera2Activity.class, "camera2ActivityProcess");

        // Verify that the remote camera was opened correctly
        List<ErrorLoggingService.LogEvent> allEvents  = mErrorServiceConnection.getLog(SETUP_TIMEOUT,
                TestConstants.EVENT_CAMERA_CONNECT);
        assertNotNull("Camera device not setup in remote process!", allEvents);

        // Filter out relevant events for other camera devices
        ArrayList<ErrorLoggingService.LogEvent> events = new ArrayList<>();
        for (ErrorLoggingService.LogEvent e : allEvents) {
            int eventTag = e.getEvent();
            if (eventTag == TestConstants.EVENT_CAMERA_UNAVAILABLE ||
                    eventTag == TestConstants.EVENT_CAMERA_CONNECT ||
                    eventTag == TestConstants.EVENT_CAMERA_AVAILABLE) {
                if (!Objects.equals(e.getLogText(), chosenCamera)) {
                    continue;
                }
            }
            events.add(e);
        }
        int[] eventList = new int[events.size()];
        int eventIdx = 0;
        for (ErrorLoggingService.LogEvent e : events) {
            eventList[eventIdx++] = e.getEvent();
        }
        String[] actualEvents = TestConstants.convertToStringArray(eventList);
        String[] expectedEvents = new String[] { TestConstants.EVENT_ACTIVITY_RESUMED_STR,
                TestConstants.EVENT_CAMERA_UNAVAILABLE_STR,
                TestConstants.EVENT_CAMERA_CONNECT_STR };
        String[] ignoredEvents = new String[] { TestConstants.EVENT_CAMERA_AVAILABLE_STR,
                TestConstants.EVENT_CAMERA_UNAVAILABLE_STR };
        assertOrderedEvents(actualEvents, expectedEvents, ignoredEvents);

        // Verify that the local camera was evicted properly
        verify(spyStateCb, times(1)).onDisconnected(any(CameraDevice.class));
        verify(spyStateCb, never()).onClosed(any(CameraDevice.class));
        verify(spyStateCb, never()).onError(any(CameraDevice.class), anyInt());
        verify(spyStateCb, times(1)).onOpened(any(CameraDevice.class));

        // Verify that we can no longer open the camera, as it is held by a higher priority process
       try {
            if (!lowerPriority) {
                manager.openCamera(chosenCamera, spyStateCb, cameraHandler);
            } else {
                // Go to top again, try getting hold of camera with priority lowered, we should get
                // an exception
                Executor cameraExecutor = new HandlerExecutor(cameraHandler);
                forceCtsActivityToTop();
                manager.openCamera(chosenCamera, CACHED_APP_VS_FG_OOM_DELTA, cameraExecutor,
                        spyStateCb);
            }
            fail("Didn't receive exception when trying to open camera held by higher priority " +
                    "process.");
        } catch(CameraAccessException e) {
            assertTrue("Received incorrect camera exception when opening camera: " + e,
                    e.getReason() == CameraAccessException.CAMERA_IN_USE);
        }

        // Verify that attempting to open the camera didn't cause anything weird to happen in the
        // other process.
        List<ErrorLoggingService.LogEvent> eventList2 = null;
        boolean timeoutExceptionHit = false;
        try {
            eventList2 = mErrorServiceConnection.getLog(EVICTION_TIMEOUT);
        } catch (TimeoutException e) {
            timeoutExceptionHit = true;
        }

        assertNone("Remote camera service received invalid events: ", eventList2);
        assertTrue("Remote camera service exited early", timeoutExceptionHit);
        android.os.Process.killProcess(mProcessPid);
        mProcessPid = -1;
        forceCtsActivityToTop();
        if (lowerPriority && mUiAutomation != null) {
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests that a client without SYSTEM_CAMERA permissions receives a security exception when
     * trying to modify the oom score for camera framework.
     */
    public void testCamera2OomScoreOffsetPermissions() throws Throwable {
        CameraManager manager = mContext.getSystemService(CameraManager.class);
        assertNotNull("Unable to get CameraManager service!", manager);
        String[] cameraIds = manager.getCameraIdListNoLazy();

        if (cameraIds.length == 0) {
            Log.i(TAG, "Skipping testBasicCamera2OomScoreOffsetPermissions, no cameras present.");
            return;
        }

        assertTrue("Context has no main looper!", mContext.getMainLooper() != null);
        for (String cameraId : cameraIds) {
            // Setup camera manager
            Handler cameraHandler = new Handler(mContext.getMainLooper());
            final CameraManager.AvailabilityCallback mockAvailCb =
                    mock(CameraManager.AvailabilityCallback.class);

            final CameraDevice.StateCallback spyStateCb = spy(new StateCallbackImpl());
            manager.registerAvailabilityCallback(mockAvailCb, cameraHandler);

            Thread.sleep(WAIT_TIME);

            verify(mockAvailCb, times(1)).onCameraAvailable(cameraId);
            verify(mockAvailCb, never()).onCameraUnavailable(cameraId);

            try {
                // Go to top again, try getting hold of camera with priority lowered, we should get
                // an exception
                Executor cameraExecutor = new HandlerExecutor(cameraHandler);
                manager.openCamera(cameraId, CACHED_APP_VS_FG_OOM_DELTA, cameraExecutor,
                        spyStateCb);
                fail("Didn't receive security exception when trying to open camera with modifed" +
                    "oom score without SYSTEM_CAMERA permissions");
            } catch(SecurityException e) {
                // fine
            }
        }
    }

    private void injectTapEvent(int x, int y) {
        long systemClock = SystemClock.uptimeMillis();

        final int motionEventTimeDeltaMs = 100;
        MotionEvent downEvent = MotionEvent.obtain(systemClock, systemClock,
                (int) MotionEvent.ACTION_DOWN, x, y, 0);
        downEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        assertTrue("Failed to inject downEvent.", mUiAutomation.injectInputEvent(downEvent, true));

        MotionEvent upEvent = MotionEvent.obtain(systemClock,
                systemClock + motionEventTimeDeltaMs, (int) MotionEvent.ACTION_UP,
                x, y, 0);
        upEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        assertTrue("Failed to inject upEvent.", mUiAutomation.injectInputEvent(upEvent, true));
    }

    /**
     * Return a Map of eventTag -> number of times encountered
     */
    private Map<Integer, Integer> getEventTagCountMap(List<ErrorLoggingService.LogEvent> events) {
        ArrayMap<Integer, Integer> eventTagCountMap = new ArrayMap<>();
        for (ErrorLoggingService.LogEvent e : events) {
            int eventTag = e.getEvent();
            if (!eventTagCountMap.containsKey(eventTag)) {
                eventTagCountMap.put(eventTag, 1);
            } else {
                eventTagCountMap.put(eventTag, eventTagCountMap.get(eventTag) + 1);
            }
        }
        return eventTagCountMap;
    }

    /**
     * Test camera availability access callback in split window mode.
     */
    @AppModeFull(reason = "TestTaskOrganizer.putTaskInSplitPrimary, .putTaskInSplitSecondary")
    public void testCamera2AccessCallbackInSplitMode() throws Throwable {
        if (!ActivityTaskManager.supportsSplitScreenMultiWindow(getActivity())) {
            return;
        }

        final int permissionCallbackTimeoutMs = 3000;
        CameraManager manager = mContext.getSystemService(CameraManager.class);
        assertNotNull("Unable to get CameraManager service!", manager);
        String[] cameraIds = manager.getCameraIdListNoLazy();

        if (cameraIds.length == 0) {
            Log.i(TAG, "Skipping testCamera2AccessCallback, device has no cameras.");
            return;
        }

        startRemoteProcess(Camera2Activity.class, "camera2ActivityProcess",
                true /*splitScreen*/);

        // Verify that the remote camera did open as expected
        List<ErrorLoggingService.LogEvent> allEvents = mErrorServiceConnection.getLog(SETUP_TIMEOUT,
                TestConstants.EVENT_CAMERA_CONNECT);
        assertNotNull("Camera device not setup in remote process!", allEvents);

        int activityResumed = 0;
        boolean cameraConnected = false;
        boolean activityPaused = false;

        Map<Integer, Integer> eventTagCountMap = getEventTagCountMap(allEvents);
        for (int eventTag : eventTagCountMap.keySet()) {
            if (eventTag == TestConstants.EVENT_ACTIVITY_RESUMED) {
                activityResumed += eventTagCountMap.get(eventTag);
            }
        }
        activityPaused = eventTagCountMap.containsKey(TestConstants.EVENT_ACTIVITY_PAUSED);
        cameraConnected = eventTagCountMap.containsKey(TestConstants.EVENT_CAMERA_CONNECT);
        assertTrue("Remote activity never resumed!", activityResumed > 0);
        assertTrue("Camera device not setup in remote process!", cameraConnected);

        Rect firstBounds = mTaskOrganizer.getPrimaryTaskBounds();
        Rect secondBounds = mTaskOrganizer.getSecondaryTaskBounds();

        Log.v(TAG, "Split bounds: (" + firstBounds.left + ", " + firstBounds.top + ", "
                + firstBounds.right + ", " + firstBounds.bottom + "), ("
                + secondBounds.left + ", " + secondBounds.top + ", "
                + secondBounds.right + ", " + secondBounds.bottom + ")");

        // Both the remote activity and the in-process activity will go through a pause-resume cycle
        // which we're not interested in testing. Wait until the end of it before expecting
        // onCameraAccessPrioritiesChanged events.
        if (!activityPaused) {
            allEvents = mErrorServiceConnection.getLog(SETUP_TIMEOUT,
                    TestConstants.EVENT_ACTIVITY_PAUSED);
            assertNotNull("Remote activity not paused!", allEvents);
            eventTagCountMap = getEventTagCountMap(allEvents);
            for (int eventTag : eventTagCountMap.keySet()) {
                if (eventTag == TestConstants.EVENT_ACTIVITY_RESUMED) {
                    activityResumed += eventTagCountMap.get(eventTag);
                }
            }
            activityPaused = eventTagCountMap.containsKey(TestConstants.EVENT_ACTIVITY_PAUSED);
        }

        assertTrue(activityPaused);

        if (activityResumed < 2) {
            allEvents = mErrorServiceConnection.getLog(SETUP_TIMEOUT,
                    TestConstants.EVENT_ACTIVITY_RESUMED);
            assertNotNull("Remote activity not resumed after pause!", allEvents);
            eventTagCountMap = getEventTagCountMap(allEvents);
            for (int eventTag : eventTagCountMap.keySet()) {
                if (eventTag == TestConstants.EVENT_ACTIVITY_RESUMED) {
                    activityResumed += eventTagCountMap.get(eventTag);
                }
            }
        }

        assertEquals(2, activityResumed);

        Set<Integer> expectedEventsPrimary = new ArraySet<>();
        expectedEventsPrimary.add(TestConstants.EVENT_CAMERA_ACCESS_PRIORITIES_CHANGED);
        expectedEventsPrimary.add(TestConstants.EVENT_ACTIVITY_TOP_RESUMED_FALSE);

        Set<Integer> expectedEventsSecondary = new ArraySet<>();
        expectedEventsSecondary.add(TestConstants.EVENT_CAMERA_ACCESS_PRIORITIES_CHANGED);
        expectedEventsSecondary.add(TestConstants.EVENT_ACTIVITY_TOP_RESUMED_TRUE);

        // Priorities are also expected to change when a second activity only gains or loses focus
        // while running in split screen mode.
        injectTapEvent(firstBounds.centerX(), firstBounds.centerY());
        allEvents = mErrorServiceConnection.getLog(CAMERA_ACCESS_TIMEOUT, expectedEventsPrimary);

        // Run many iterations to make sure there are no negatives. Limit this to 15 seconds.
        long begin = System.currentTimeMillis();
        final int maxIterations = 100;
        final long timeLimitMs = 15000;
        for (int i = 0; i < maxIterations && System.currentTimeMillis() - begin < timeLimitMs;
                i++) {
            injectTapEvent(secondBounds.centerX(), secondBounds.centerY());
            allEvents = mErrorServiceConnection.getLog(CAMERA_ACCESS_TIMEOUT,
                    expectedEventsSecondary);
            assertNotNull(allEvents);
            eventTagCountMap = getEventTagCountMap(allEvents);
            assertTrue(eventTagCountMap.containsKey(
                    TestConstants.EVENT_CAMERA_ACCESS_PRIORITIES_CHANGED));
            assertTrue(eventTagCountMap.containsKey(
                    TestConstants.EVENT_ACTIVITY_TOP_RESUMED_TRUE));
            assertFalse(eventTagCountMap.containsKey(
                    TestConstants.EVENT_ACTIVITY_TOP_RESUMED_FALSE));

            injectTapEvent(firstBounds.centerX(), firstBounds.centerY());
            allEvents = mErrorServiceConnection.getLog(CAMERA_ACCESS_TIMEOUT,
                    expectedEventsPrimary);
            assertNotNull(allEvents);
            eventTagCountMap = getEventTagCountMap(allEvents);
            assertTrue(eventTagCountMap.containsKey(
                    TestConstants.EVENT_CAMERA_ACCESS_PRIORITIES_CHANGED));
            assertTrue(eventTagCountMap.containsKey(
                    TestConstants.EVENT_ACTIVITY_TOP_RESUMED_FALSE));
            assertFalse(eventTagCountMap.containsKey(
                    TestConstants.EVENT_ACTIVITY_TOP_RESUMED_TRUE));
        }
    }

    /**
     * Test camera availability access callback.
     */
    public void testCamera2AccessCallback() throws Throwable {
        int PERMISSION_CALLBACK_TIMEOUT_MS = 2000;
        CameraManager manager = mContext.getSystemService(CameraManager.class);
        assertNotNull("Unable to get CameraManager service!", manager);
        String[] cameraIds = manager.getCameraIdListNoLazy();

        if (cameraIds.length == 0) {
            Log.i(TAG, "Skipping testCamera2AccessCallback, device has no cameras.");
            return;
        }

        assertTrue("Context has no main looper!", mContext.getMainLooper() != null);

        // Setup camera manager
        Handler cameraHandler = new Handler(mContext.getMainLooper());

        final CameraManager.AvailabilityCallback mockAvailCb =
                mock(CameraManager.AvailabilityCallback.class);
        manager.registerAvailabilityCallback(mockAvailCb, cameraHandler);

        // Remove current task from top of stack. This will impact the camera access
        // priorities.
        getActivity().moveTaskToBack(/*nonRoot*/true);

        verify(mockAvailCb, timeout(
                PERMISSION_CALLBACK_TIMEOUT_MS).atLeastOnce()).onCameraAccessPrioritiesChanged();

        forceCtsActivityToTop();

        verify(mockAvailCb, timeout(
                PERMISSION_CALLBACK_TIMEOUT_MS).atLeastOnce()).onCameraAccessPrioritiesChanged();
    }

    /**
     * Test native camera availability access callback.
     */
    public void testCamera2NativeAccessCallback() throws Throwable {
        int PERMISSION_CALLBACK_TIMEOUT_MS = 2000;
        CameraManager manager = mContext.getSystemService(CameraManager.class);
        assertNotNull("Unable to get CameraManager service!", manager);
        String[] cameraIds = manager.getCameraIdListNoLazy();

        if (cameraIds.length == 0) {
            Log.i(TAG, "Skipping testBasicCamera2AccessCallback, device has no cameras.");
            return;
        }

        // Setup camera manager
        long context = 0;
        try {
            context = initializeAvailabilityCallbacksNative();
            assertTrue("Failed to initialize native availability callbacks", (context != 0));

            // Remove current task from top of stack. This will impact the camera access
            // pririorties.
            getActivity().moveTaskToBack(/*nonRoot*/true);

            Thread.sleep(PERMISSION_CALLBACK_TIMEOUT_MS);
            assertTrue("No camera permission access changed callback received",
                    (getAccessCallbacksCountAndResetNative(context) > 0));

            forceCtsActivityToTop();

            assertTrue("No camera permission access changed callback received",
                    (getAccessCallbacksCountAndResetNative(context) > 0));
        } finally {
            if (context != 0) {
                releaseAvailabilityCallbacksNative(context);
            }
        }
    }

    /**
     * Test basic eviction scenarios for camera used in MediaRecoder
     */
    public void testMediaRecorderCameraActivityEviction() throws Throwable {
        testAPI1ActivityEviction(MediaRecorderCameraActivity.class,
                "mediaRecorderCameraActivityProcess");
    }

    /**
     * Test basic eviction scenarios for Camera1 API.
     *
     * This test will open camera, create a higher priority process to run the specified activity,
     * open camera again, and verify the right clients are evicted.
     *
     * @param activityKlass An activity to run in a higher priority process.
     * @param processName The process name.
     */
    private void testAPI1ActivityEviction (java.lang.Class<?> activityKlass, String processName)
            throws Throwable {
        // Open a camera1 client in the main CTS process's activity
        final Camera.ErrorCallback mockErrorCb1 = mock(Camera.ErrorCallback.class);
        final boolean[] skip = {false};
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Open camera
                mCamera = Camera.open();
                if (mCamera == null) {
                    skip[0] = true;
                } else {
                    mCamera.setErrorCallback(mockErrorCb1);
                }
                notifyFromUI();
            }
        });
        waitForUI();

        if (skip[0]) {
            Log.i(TAG, "Skipping testCamera1ActivityEviction, device has no cameras.");
            return;
        }

        verifyZeroInteractions(mockErrorCb1);

        startRemoteProcess(activityKlass, processName);

        // Make sure camera was setup correctly in remote activity
        List<ErrorLoggingService.LogEvent> events = null;
        try {
            events = mErrorServiceConnection.getLog(SETUP_TIMEOUT,
                    TestConstants.EVENT_CAMERA_CONNECT);
        } finally {
            if (events != null) assertOnly(TestConstants.EVENT_CAMERA_CONNECT, events);
        }

        Thread.sleep(WAIT_TIME);

        // Ensure UI thread has a chance to process callbacks.
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.i("CTS", "Did something on UI thread.");
                notifyFromUI();
            }
        });
        waitForUI();

        // Make sure we received correct callback in error listener, and nothing else
        verify(mockErrorCb1, only()).onError(eq(Camera.CAMERA_ERROR_EVICTED), isA(Camera.class));
        mCamera = null;

        // Try to open the camera again (even though other TOP process holds the camera).
        final boolean[] pass = {false};
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Open camera
                try {
                    mCamera = Camera.open();
                } catch (RuntimeException e) {
                    pass[0] = true;
                }
                notifyFromUI();
            }
        });
        waitForUI();

        assertTrue("Did not receive exception when opening camera while camera is held by a" +
                " higher priority client process.", pass[0]);

        // Verify that attempting to open the camera didn't cause anything weird to happen in the
        // other process.
        List<ErrorLoggingService.LogEvent> eventList2 = null;
        boolean timeoutExceptionHit = false;
        try {
            eventList2 = mErrorServiceConnection.getLog(EVICTION_TIMEOUT);
        } catch (TimeoutException e) {
            timeoutExceptionHit = true;
        }

        assertNone("Remote camera service received invalid events: ", eventList2);
        assertTrue("Remote camera service exited early", timeoutExceptionHit);
        android.os.Process.killProcess(mProcessPid);
        mProcessPid = -1;
        forceCtsActivityToTop();
    }

    /**
     * Ensure the CTS activity becomes foreground again instead of launcher.
     */
    private void forceCtsActivityToTop() throws InterruptedException {
        Thread.sleep(WAIT_TIME);
        Activity a = getActivity();
        Intent activityIntent = new Intent(a, CameraCtsActivity.class);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        a.startActivity(activityIntent);
        Thread.sleep(WAIT_TIME);
    }

    /**
     * Block until UI thread calls {@link #notifyFromUI()}.
     * @throws InterruptedException
     */
    private void waitForUI() throws InterruptedException {
        synchronized(mLock) {
            if (mCompleted) return;
            while (!mCompleted) {
                mLock.wait();
            }
            mCompleted = false;
        }
    }

    /**
     * Wake up any threads waiting in calls to {@link #waitForUI()}.
     */
    private void notifyFromUI() {
        synchronized (mLock) {
            mCompleted = true;
            mLock.notifyAll();
        }
    }

    /**
     * Return the PID for the process with the given name in the given list of process info.
     *
     * @param processName the name of the process who's PID to return.
     * @param list a list of {@link ActivityManager.RunningAppProcessInfo} to check.
     * @return the PID of the given process, or -1 if it was not included in the list.
     */
    private static int getPid(String processName,
                              List<ActivityManager.RunningAppProcessInfo> list) {
        for (ActivityManager.RunningAppProcessInfo rai : list) {
            if (processName.equals(rai.processName))
                return rai.pid;
        }
        return -1;
    }

    /**
     * Start an activity of the given class running in a remote process with the given name.
     *
     * @param klass the class of the {@link android.app.Activity} to start.
     * @param processName the remote activity name.
     * @throws InterruptedException
     */
    public void startRemoteProcess(java.lang.Class<?> klass, String processName)
            throws InterruptedException {
        startRemoteProcess(klass, processName, false /*splitScreen*/);
    }

    /**
     * Start an activity of the given class running in a remote process with the given name.
     *
     * @param klass the class of the {@link android.app.Activity} to start.
     * @param processName the remote activity name.
     * @param splitScreen Start new activity in split screen mode.
     * @throws InterruptedException
     */
    public void startRemoteProcess(java.lang.Class<?> klass, String processName,
            boolean splitScreen) throws InterruptedException {
        // Ensure no running activity process with same name
        Activity a = getActivity();
        String cameraActivityName = a.getPackageName() + ":" + processName;
        List<ActivityManager.RunningAppProcessInfo> list =
                mActivityManager.getRunningAppProcesses();
        assertEquals("Activity " + cameraActivityName + " already running.",
                -1, getPid(cameraActivityName, list));

        // Start activity in a new top foreground process
        if (splitScreen) {
            // startActivity(intent) doesn't work with TestTaskOrganizer's split screen,
            // have to go through shell command.
            // Also, android:exported must be true for this to work, see:
            // https://developer.android.com/guide/topics/manifest/activity-element#exported
            runShellCommand("am start %s/%s", a.getPackageName(), klass.getName());
            ComponentName secondActivityComponent = new ComponentName(
                    a.getPackageName(), klass.getName());
            mWmState.waitForValidState(secondActivityComponent);
            int taskId = mWmState.getTaskByActivity(secondActivityComponent)
                    .getTaskId();

            // Requires @AppModeFull.
            mTaskOrganizer.putTaskInSplitPrimary(a.getTaskId());
            ComponentName primaryActivityComponent = new ComponentName(
                    a.getPackageName(), a.getClass().getName());
            mWmState.waitForValidState(primaryActivityComponent);

            // The taskAffinity of the secondary activity must be differ with the taskAffinity
            // of the primary activity, otherwise it will replace the primary activity instead.
            mTaskOrganizer.putTaskInSplitSecondary(taskId);
            mWmState.waitForValidState(secondActivityComponent);
        } else {
            Intent activityIntent = new Intent(a, klass);
            activityIntent.putExtra(TestConstants.EXTRA_IGNORE_CAMERA_ACCESS, true);
            activityIntent.putExtra(TestConstants.EXTRA_IGNORE_TOP_ACTIVITY_RESUMED, true);
            activityIntent.putExtra(TestConstants.EXTRA_IGNORE_ACTIVITY_PAUSED, true);
            a.startActivity(activityIntent);
            Thread.sleep(WAIT_TIME);
        }

        // Fail if activity isn't running
        list = mActivityManager.getRunningAppProcesses();
        mProcessPid = getPid(cameraActivityName, list);
        assertTrue("Activity " + cameraActivityName + " not found in list of running app "
                + "processes.", -1 != mProcessPid);
    }

    /**
     * Assert that there is only one event of the given type in the event list.
     *
     * @param event event type to check for.
     * @param events {@link List} of events.
     */
    public static void assertOnly(int event, List<ErrorLoggingService.LogEvent> events) {
        assertTrue("Remote camera activity never received event: " + event, events != null);
        for (ErrorLoggingService.LogEvent e : events) {
            assertFalse("Remote camera activity received invalid event (" + e +
                    ") while waiting for event: " + event,
                    e.getEvent() < 0 || e.getEvent() != event);
        }
        assertTrue("Remote camera activity never received event: " + event, events.size() >= 1);
        assertTrue("Remote camera activity received too many " + event + " events, received: " +
                events.size(), events.size() == 1);
    }

    /**
     * Assert there were no logEvents in the given list.
     *
     * @param msg message to show on assertion failure.
     * @param events {@link List} of events.
     */
    public static void assertNone(String msg, List<ErrorLoggingService.LogEvent> events) {
        if (events == null) return;
        StringBuilder builder = new StringBuilder(msg + "\n");
        for (ErrorLoggingService.LogEvent e : events) {
            builder.append(e).append("\n");
        }
        assertTrue(builder.toString(), events.isEmpty());
    }

    /**
     * Assert array is null or empty.
     *
     * @param array array to check.
     */
    public static <T> void assertNotEmpty(T[] array) {
        assertNotNull("Array is null.", array);
        assertFalse("Array is empty: " + Arrays.toString(array), array.length == 0);
    }

    /**
     * Given an 'actual' array of objects, check that the objects given in the 'expected'
     * array are also present in the 'actual' array in the same order.  Objects in the 'actual'
     * array that are not in the 'expected' array are skipped and ignored if they are given
     * in the 'ignored' array, otherwise this assertion will fail.
     *
     * @param actual the ordered array of objects to check.
     * @param expected the ordered array of expected objects.
     * @param ignored the array of objects that will be ignored if present in actual,
     *                but not in expected (or are out of order).
     * @param <T>
     */
    public static <T> void assertOrderedEvents(T[] actual, T[] expected, T[] ignored) {
        assertNotNull("List of actual events is null.", actual);
        assertNotNull("List of expected events is null.", expected);
        assertNotNull("List of ignored events is null.", ignored);

        int expIndex = 0;
        int index = 0;
        for (T i : actual) {
            // If explicitly expected, move to next
            if (expIndex < expected.length && Objects.equals(i, expected[expIndex])) {
                expIndex++;
                continue;
            }

            // Fail if not ignored
            boolean canIgnore = false;
            for (T j : ignored) {
                if (Objects.equals(i, j)) {
                    canIgnore = true;
                    break;
                }

            }

            // Fail if not ignored.
            assertTrue("Event at index " + index + " in actual array " +
                    Arrays.toString(actual) + " was unexpected: expected array was " +
                    Arrays.toString(expected) + ", ignored array was: " +
                    Arrays.toString(ignored), canIgnore);
            index++;
        }
        assertTrue("Only had " + expIndex + " of " + expected.length +
                " expected objects in array " + Arrays.toString(actual) + ", expected was " +
                Arrays.toString(expected), expIndex == expected.length);
    }
}
