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

package android.car.cts.app;

import android.app.Service;
import android.car.Car;
import android.car.hardware.power.CarPowerManager;
import android.car.test.mocks.JavaMockitoHelper;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import javax.annotation.concurrent.GuardedBy;

/**
 * To test car power:
 *
 *     <pre class="prettyprint">
 *         adb shell am start -n android.car.cts.app/.CarPowerTestService /
 *         --es power [action]
 *         action:
 *            set-listener,[listener-name],[with-completion|without-completion],[s2r|s2d]
 *            get-listener-states-results,[listener-name],[with-completion|without-completion],
 *            [s2r|s2d]
 *            clear-listener
 *     </pre>
 */
public final class CarPowerTestService extends Service {
    private static final long WAIT_TIMEOUT_MS = 5_000;
    private static final int RESULT_LOG_SIZE = 4096;
    private static final String TAG = CarPowerTestService.class.getSimpleName();
    private static final String CMD_IDENTIFIER = "power";
    private static final String CMD_SET_LISTENER = "set-listener";
    private static final String CMD_GET_LISTENER_STATES_RESULTS = "get-listener-states-results";
    private static final String CMD_CLEAR_LISTENER = "clear-listener";
    private static final List<Integer> EXPECTED_STATES_S2R = List.of(
            CarPowerManager.STATE_PRE_SHUTDOWN_PREPARE,
            CarPowerManager.STATE_SHUTDOWN_PREPARE,
            CarPowerManager.STATE_SUSPEND_ENTER,
            CarPowerManager.STATE_POST_SUSPEND_ENTER
    );
    private static final List<Integer> EXPECTED_STATES_S2D = List.of(
            CarPowerManager.STATE_PRE_SHUTDOWN_PREPARE,
            CarPowerManager.STATE_SHUTDOWN_PREPARE,
            CarPowerManager.STATE_HIBERNATION_ENTER,
            CarPowerManager.STATE_POST_HIBERNATION_ENTER
    );
    private static final Set<Integer> FUTURE_ALLOWING_STATES = Set.of(
            CarPowerManager.STATE_PRE_SHUTDOWN_PREPARE,
            CarPowerManager.STATE_SHUTDOWN_PREPARE,
            CarPowerManager.STATE_SHUTDOWN_ENTER,
            CarPowerManager.STATE_SUSPEND_ENTER,
            CarPowerManager.STATE_HIBERNATION_ENTER,
            CarPowerManager.STATE_POST_SHUTDOWN_ENTER,
            CarPowerManager.STATE_POST_SUSPEND_ENTER,
            CarPowerManager.STATE_POST_HIBERNATION_ENTER
    );

    private final Object mLock = new Object();

    private final StringWriter mResultBuf = new StringWriter(RESULT_LOG_SIZE);

    private Car mCarApi;
    @GuardedBy("mLock")
    private CarPowerManager mCarPowerManager;
    @GuardedBy("mLock")
    private ArrayMap<String, WaitablePowerStateListener> mListeners = new ArrayMap<>();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void initManagers(Car car, boolean ready) {
        synchronized (mLock) {
            if (ready) {
                mCarPowerManager = (CarPowerManager) car.getCarManager(
                        Car.POWER_SERVICE);
                Log.i(TAG, "initManagers() completed");
            } else {
                mCarPowerManager = null;
                Log.wtf(TAG, "initManagers() set to be null");
            }
        }
    }

    private void initCarApi() {
        if (mCarApi != null && mCarApi.isConnected()) {
            mCarApi.disconnect();
        }
        mCarApi = Car.createCar(/* context= */ this, /* handler= */ null,
                /* waitTimeoutMs= */ Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                /* statusChangeListener= */ (Car car, boolean ready) -> {
                    initManagers(car, ready);
                });
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate");
        super.onCreate();
        initCarApi();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");
        super.onStartCommand(intent, flags, startId);
        Bundle extras = intent.getExtras();
        if (extras == null) {
            Log.i(TAG, "onStartCommand(): empty extras");
            return START_NOT_STICKY;
        }

        try {
            parseCommandAndExecute(extras);
        } catch (Exception e) {
            Log.e(TAG, "onStartCommand(): failed to handle cmd", e);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        if (mCarApi != null) {
            mCarApi.disconnect();
        }
        super.onDestroy();
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        Log.i(TAG, "Dumping mResultBuf: " + mResultBuf);
        writer.println(mResultBuf);
    }

    @GuardedBy("mLock")
    private void setListenerWithoutCompletionLocked(String listenerName, int expectedStatesSize) {
        WaitablePowerStateListenerWithoutCompletion listener =
                new WaitablePowerStateListenerWithoutCompletion(expectedStatesSize);
        mListeners.put(listenerName, listener);
    }

    @GuardedBy("mLock")
    private void setListenerWithCompletionLocked(String listenerName, int expectedStatesSize) {
        WaitablePowerStateListenerWithCompletion listener =
                new WaitablePowerStateListenerWithCompletion(
                        expectedStatesSize, FUTURE_ALLOWING_STATES);
        mListeners.put(listenerName, listener);
    }

    private boolean listenerStatesMatchExpected(WaitablePowerStateListener listener,
            List<Integer> expectedStates) throws InterruptedException {
        List<Integer> observedStates = listener.await();
        Log.i(TAG, "observedStates: \n" + observedStates);
        return observedStates.equals(expectedStates);
    }

    private boolean isListenerWithCompletion(String completionType) throws
            IllegalArgumentException {
        if (completionType.equals("with-completion")) {
            return true;
        } else if (completionType.equals("without-completion")) {
            return false;
        }
        throw new IllegalArgumentException("Completion type parameter must be 'with-completion' or "
                + "'without-completion'");
    }

    private List<Integer> getListenerExpectedStates(String suspendType) throws
            IllegalArgumentException {
        if (suspendType.equals("s2r")) {
            return EXPECTED_STATES_S2R;
        } else if (suspendType.equals("s2d")) {
            return EXPECTED_STATES_S2D;
        }
        throw new IllegalArgumentException("Suspend type parameter must be 's2r' or 's2d'");
    }

    private void parseCommandAndExecute(Bundle extras) {
        String commandString = extras.getString(CMD_IDENTIFIER);
        if (TextUtils.isEmpty(commandString)) {
            Log.i(TAG, "empty power test command");
            return;
        }
        Log.i(TAG, "parseCommandAndExecute with: " + commandString);

        String[] tokens = commandString.split(",");
        switch(tokens[0]) {
            case CMD_SET_LISTENER:
                if (tokens.length != 4) {
                    Log.i(TAG, "incorrect set-listener command format: " + commandString
                            + ", should be set-listener,[listener-name],"
                            + "[with-completion|without-completion],[s2r|s2d]");
                    break;
                }

                String completionType = tokens[2];
                Log.i(TAG, "Set listener command completion type: " + completionType);
                boolean withCompletion;
                try {
                    withCompletion = isListenerWithCompletion(completionType);
                } catch (IllegalArgumentException e) {
                    Log.i(TAG, e.getMessage());
                    break;
                }

                String suspendType = tokens[3];
                Log.i(TAG, "Set listener command suspend type: " + suspendType);
                int expectedStatesSize;
                try {
                    expectedStatesSize = getListenerExpectedStates(suspendType).size();
                } catch (IllegalArgumentException e) {
                    Log.i(TAG, e.getMessage());
                    break;
                }

                String listenerName = tokens[1];
                Log.i(TAG, "Set listener command listener name: " + listenerName);
                synchronized (mLock) {
                    if (mListeners.containsKey(listenerName)) {
                        Log.i(TAG, "there is already a with completion listener registered "
                                + "with name " + listenerName);
                        break;
                    }
                    if (withCompletion) {
                        setListenerWithCompletionLocked(listenerName, expectedStatesSize);
                    } else {
                        setListenerWithoutCompletionLocked(listenerName, expectedStatesSize);
                    }
                }
                Log.i(TAG, "Listener set for " + listenerName);
                break;
            case CMD_GET_LISTENER_STATES_RESULTS:
                if (tokens.length != 4) {
                    Log.i(TAG, "incorrect get-listener-states-results command format: "
                            + commandString + ", should be get-listener-states-results,"
                            + "[listener-name],[with-completion|without-completion],[s2r|s2d]");
                    break;
                }

                listenerName = tokens[1];
                Log.i(TAG, "Get listener command get listener by name: " + listenerName);
                WaitablePowerStateListener listener;
                synchronized (mLock) {
                    if (mListeners.containsKey(listenerName)) {
                        listener = mListeners.get(listenerName);
                    } else {
                        Log.i(TAG, "there is no listener registered with name " + listenerName);
                        break;
                    }
                }

                completionType = tokens[2];
                Log.i(TAG, "Get listener command completion type: " + completionType);
                try {
                    withCompletion = isListenerWithCompletion(completionType);
                } catch (IllegalArgumentException e) {
                    Log.i(TAG, e.getMessage());
                    break;
                }

                suspendType = tokens[3];
                Log.i(TAG, "Get listener command suspend type: " + suspendType);
                List<Integer> expectedStates;
                try {
                    expectedStates = getListenerExpectedStates(suspendType);
                } catch (IllegalArgumentException e) {
                    Log.i(TAG, e.getMessage());
                    break;
                }
                Log.i(TAG, "expectedStates: " + expectedStates);

                try {
                    boolean statesMatchExpected = listenerStatesMatchExpected(listener,
                            expectedStates);
                    if (withCompletion) {
                        WaitablePowerStateListenerWithCompletion listenerWithCompletion =
                                ((WaitablePowerStateListenerWithCompletion) listener);
                        boolean futureIsValid =
                                listenerWithCompletion.completablePowerStateChangeFutureIsValid();
                        statesMatchExpected = statesMatchExpected && futureIsValid;
                    }
                    Log.i(TAG, "statesMatchExpected: " + statesMatchExpected);
                    mResultBuf.write(String.valueOf(statesMatchExpected));
                } catch (InterruptedException e) {
                    Log.i(TAG, "Getting listener states timed out");
                    break;
                }
                break;
            case CMD_CLEAR_LISTENER:
                synchronized (mLock) {
                    mCarPowerManager.clearListener();
                }
                Log.i(TAG, "Listener cleared");
                break;
            default:
                throw new IllegalArgumentException("invalid power test command: " + commandString);
        }
    }

    private class WaitablePowerStateListener {
        private final int mInitialCount;
        protected final CountDownLatch mLatch;
        protected final CarPowerManager mPowerManager;
        protected List<Integer> mReceivedStates = new ArrayList<Integer>();

        WaitablePowerStateListener(int initialCount) {
            mLatch = new CountDownLatch(initialCount);
            mInitialCount = initialCount;
            synchronized (mLock) {
                mPowerManager = mCarPowerManager;
            }
        }

        List<Integer> await() throws InterruptedException {
            JavaMockitoHelper.await(mLatch, WAIT_TIMEOUT_MS);
            return mReceivedStates.subList(0, mInitialCount);
        }
    }

    private final class WaitablePowerStateListenerWithoutCompletion extends
            WaitablePowerStateListener{
        WaitablePowerStateListenerWithoutCompletion(int initialCount) {
            super(initialCount);
            mPowerManager.setListener(getMainExecutor(),
                    (state) -> {
                        mReceivedStates.add(state);
                        mLatch.countDown();
                        Log.i(TAG, "Listener observed state: " + state + ", received "
                                + "states: " + mReceivedStates + ", mLatch count:"
                                + mLatch.getCount());
                    });
            Log.i(TAG, "Listener without completion set");
        }
    }

    private final class WaitablePowerStateListenerWithCompletion extends
            WaitablePowerStateListener {
        private final ArrayMap<Integer, String> mInvalidFutureMap = new ArrayMap<>();
        private final Set<Integer> mFutureAllowingStates;

        WaitablePowerStateListenerWithCompletion(int initialCount,
                Set<Integer> futureAllowingStates) {
            super(initialCount);
            mFutureAllowingStates = futureAllowingStates;
            mPowerManager.setListenerWithCompletion(getMainExecutor(),
                    (state, future) -> {
                        mReceivedStates.add(state);
                        if (mFutureAllowingStates.contains(state)) {
                            if (future == null) {
                                mInvalidFutureMap.put(state, "CompletablePowerStateChangeFuture for"
                                                + " state(" + state + ") must not be null");
                            } else {
                                future.complete();
                            }
                        } else {
                            if (future != null) {
                                mInvalidFutureMap.put(state, "CompletablePowerStateChangeFuture for"
                                        + " state(" + state + ") must be null");
                            }
                        }
                        mLatch.countDown();
                    });
            Log.i(TAG, "Listener with completion set");
        }

        boolean completablePowerStateChangeFutureIsValid() {
            if (!mInvalidFutureMap.isEmpty()) {
                Log.i(TAG, "Wrong CompletablePowerStateChangeFuture(s) is(are) passed to the "
                        + "listener: " + mInvalidFutureMap);
                return false;
            }
            return true;
        }
    }
}
