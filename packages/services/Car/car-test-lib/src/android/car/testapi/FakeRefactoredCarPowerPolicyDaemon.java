/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.car.testapi;

import android.annotation.Nullable;
import android.automotive.powerpolicy.internal.ICarPowerPolicyDelegate;
import android.automotive.powerpolicy.internal.ICarPowerPolicyDelegateCallback;
import android.automotive.powerpolicy.internal.PowerPolicyFailureReason;
import android.automotive.powerpolicy.internal.PowerPolicyInitData;
import android.car.hardware.power.PowerComponent;
import android.car.hardware.power.PowerComponentUtil;
import android.car.test.util.TemporaryFile;
import android.frameworks.automotive.powerpolicy.CarPowerPolicy;
import android.os.FileObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.IntArray;
import android.util.Log;
import android.util.SparseArray;

import libcore.io.IoUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fake power policy daemon to be used in car service test and car service unit test when
 * refactored power policy logic is used.
 */
public final class FakeRefactoredCarPowerPolicyDaemon extends ICarPowerPolicyDelegate.Default {
    private static final String TAG = FakeRefactoredCarPowerPolicyDaemon.class.getSimpleName();

    /**
     * The power policy ID for the system power policy for all power components on; the default
     * power policy for the "ON" state.
     */
    public static final String SYSTEM_POWER_POLICY_ALL_ON = "system_power_policy_all_on";
    /**
     * The power policy ID for the system power policy for no user interaction; the default
     * power policy for silent mode and shutdown preparation states.
     */
    public static final String SYSTEM_POWER_POLICY_NO_USER_INTERACTION =
            "system_power_policy_no_user_interaction";
    /**
     * The power policy ID for the system power policy for initial on power components; the default
     * power policy for the "WAIT_FOR_VHAL" state.
     */
    public static final String SYSTEM_POWER_POLICY_INITIAL_ON = "system_power_policy_initial_on";
    /**
     * The power policy ID for the system power policy for suspend preparation.
     */
    public static final String SYSTEM_POWER_POLICY_SUSPEND_PREP =
            "system_power_policy_suspend_prep";

    private static final String POLICY_PER_STATE_GROUP_ID = "default_policy_per_state";

    private final int[] mCustomComponents;
    private final FileObserver mFileObserver;
    private final ComponentHandler mComponentHandler = new ComponentHandler();
    private final HandlerThread mHandlerThread = new HandlerThread(TAG);
    private final Map<String, CarPowerPolicy> mPolicies = new ArrayMap<>();
    private final Map<String, SparseArray<CarPowerPolicy>> mPowerPolicyGroups = new ArrayMap<>();

    private String mLastAppliedPowerPolicyId = SYSTEM_POWER_POLICY_INITIAL_ON;
    private String mLastSetPowerPolicyGroupId = POLICY_PER_STATE_GROUP_ID;

    private boolean mSilentModeOn;
    private String mPendingPowerPolicyId;
    private String mLastDefinedPolicyId;
    private Handler mHandler;
    private ICarPowerPolicyDelegateCallback mCallback;
    private TemporaryFile mFileKernelSilentMode;

    public FakeRefactoredCarPowerPolicyDaemon(@Nullable TemporaryFile fileKernelSilentMode,
            @Nullable int[] customComponents) throws Exception {
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mFileKernelSilentMode = (fileKernelSilentMode == null)
                ? new TemporaryFile("KERNEL_SILENT_MODE") : fileKernelSilentMode;
        mFileObserver = new SilentModeFileObserver(mFileKernelSilentMode.getFile(),
                FileObserver.CLOSE_WRITE);
        CarPowerPolicy policyInitialOn = createInitialOnPowerPolicy();
        CarPowerPolicy policyAllOn = createAllOnPowerPolicy();
        CarPowerPolicy policyNoUser = createNoUserPowerPolicy();
        CarPowerPolicy policySuspendPrep = createSuspendPrepPowerPolicy();
        mPolicies.put(SYSTEM_POWER_POLICY_INITIAL_ON, policyInitialOn);
        mPolicies.put(SYSTEM_POWER_POLICY_ALL_ON, policyAllOn);
        mPolicies.put(SYSTEM_POWER_POLICY_NO_USER_INTERACTION, policyNoUser);
        mPolicies.put(SYSTEM_POWER_POLICY_SUSPEND_PREP, policySuspendPrep);
        mPowerPolicyGroups.put(POLICY_PER_STATE_GROUP_ID, createPolicyGroup(
                SYSTEM_POWER_POLICY_INITIAL_ON, SYSTEM_POWER_POLICY_ALL_ON));
        mCustomComponents = Objects.requireNonNullElse(customComponents, new int[]{});
    }

    private static CarPowerPolicy createPolicy(
            String policyId, int[] enabledComponents, int[] disabledComponents) {
        CarPowerPolicy policy = new CarPowerPolicy();
        policy.policyId = policyId;
        policy.enabledComponents = enabledComponents;
        policy.disabledComponents = disabledComponents;
        return policy;
    }

    // Create a fake system_power_policy_initial_on
    private static CarPowerPolicy createInitialOnPowerPolicy() {
        return createPolicy(SYSTEM_POWER_POLICY_INITIAL_ON,
                new int[]{PowerComponent.AUDIO, PowerComponent.DISPLAY, PowerComponent.CPU},
                new int[]{PowerComponent.MEDIA, PowerComponent.BLUETOOTH,
                        PowerComponent.WIFI, PowerComponent.CELLULAR,
                        PowerComponent.ETHERNET, PowerComponent.PROJECTION,
                        PowerComponent.NFC, PowerComponent.INPUT,
                        PowerComponent.VOICE_INTERACTION,
                        PowerComponent.VISUAL_INTERACTION,
                        PowerComponent.TRUSTED_DEVICE_DETECTION,
                        PowerComponent.LOCATION, PowerComponent.MICROPHONE});
    }

    // Create a fake system_power_policy_all_on
    private static CarPowerPolicy createAllOnPowerPolicy() {
        return createPolicy(SYSTEM_POWER_POLICY_ALL_ON, new int[]{PowerComponent.AUDIO,
                PowerComponent.MEDIA, PowerComponent.DISPLAY, PowerComponent.BLUETOOTH,
                PowerComponent.WIFI, PowerComponent.CELLULAR, PowerComponent.ETHERNET,
                PowerComponent.PROJECTION, PowerComponent.NFC, PowerComponent.INPUT,
                PowerComponent.VOICE_INTERACTION, PowerComponent.VISUAL_INTERACTION,
                PowerComponent.TRUSTED_DEVICE_DETECTION, PowerComponent.LOCATION,
                PowerComponent.MICROPHONE, PowerComponent.CPU}, new int[]{});
    }

    // Create a fake system_power_policy_no_user_interaction
    private static CarPowerPolicy createNoUserPowerPolicy() {
        return createPolicy(SYSTEM_POWER_POLICY_NO_USER_INTERACTION,
                new int[]{
                        PowerComponent.WIFI, PowerComponent.CELLULAR, PowerComponent.ETHERNET,
                        PowerComponent.CPU, PowerComponent.TRUSTED_DEVICE_DETECTION},
                new int[]{
                        PowerComponent.AUDIO, PowerComponent.MEDIA, PowerComponent.DISPLAY,
                        PowerComponent.BLUETOOTH, PowerComponent.PROJECTION, PowerComponent.NFC,
                        PowerComponent.INPUT, PowerComponent.VOICE_INTERACTION,
                        PowerComponent.VISUAL_INTERACTION, PowerComponent.LOCATION,
                        PowerComponent.MICROPHONE});
    }

    // Create a fake system_power_policy_suspend_prep
    private static CarPowerPolicy createSuspendPrepPowerPolicy() {
        return createPolicy(SYSTEM_POWER_POLICY_SUSPEND_PREP, new int[]{}, new int[]{
                PowerComponent.AUDIO, PowerComponent.BLUETOOTH, PowerComponent.WIFI,
                PowerComponent.LOCATION, PowerComponent.MICROPHONE, PowerComponent.CPU});
    }

    private SparseArray<CarPowerPolicy> createPolicyGroup(String waitForVhalPolicyId,
            String onPolicyId) {
        SparseArray<CarPowerPolicy> policyGroup = new SparseArray<>();
        policyGroup.put(PowerState.WAIT_FOR_VHAL, mPolicies.get(waitForVhalPolicyId));
        policyGroup.put(PowerState.ON, mPolicies.get(onPolicyId));
        return policyGroup;
    }

    @Override
    public PowerPolicyInitData notifyCarServiceReady(ICarPowerPolicyDelegateCallback callback) {
        Log.i(TAG, "Fake refactored CPPD was notified that car service is ready");
        mCallback = callback;
        PowerPolicyInitData initData = new PowerPolicyInitData();
        initData.currentPowerPolicy = mPolicies.get(mLastAppliedPowerPolicyId);
        initData.registeredPolicies = new CarPowerPolicy[]{
                mPolicies.get(SYSTEM_POWER_POLICY_INITIAL_ON),
                mPolicies.get(SYSTEM_POWER_POLICY_ALL_ON),
                mPolicies.get(SYSTEM_POWER_POLICY_NO_USER_INTERACTION),
                mPolicies.get(SYSTEM_POWER_POLICY_SUSPEND_PREP)};
        initData.registeredCustomComponents = mCustomComponents;
        mComponentHandler.applyPolicy(mPolicies.get(mLastAppliedPowerPolicyId));
        return initData;
    }

    @Override
    public void applyPowerPolicyAsync(int requestId, String policyId, boolean force)
            throws RemoteException {
        Log.i(TAG, "Fake refactored CPPD is attempting to apply power policy " + policyId);
        if (mCallback == null) {
            throw new IllegalStateException("Fake refactored CPPD callback is null, was "
                    + "notifyCarServiceReady() called?");
        }
        mLastAppliedPowerPolicyId = policyId;
        CarPowerPolicy currentPolicy = mPolicies.get(policyId);
        if (currentPolicy == null) {
            throw new IllegalArgumentException("Power policy " + policyId + " is invalid");
        }
        mComponentHandler.applyPolicy(currentPolicy);
        CarPowerPolicy accumulatedPolicy = mComponentHandler.getAccumulatedPolicy(policyId);
        mCallback.updatePowerComponents(accumulatedPolicy);
        mCallback.onApplyPowerPolicySucceeded(requestId, accumulatedPolicy);
    }

    @Override
    public void applyPowerPolicyPerPowerStateChangeAsync(int requestId, int state)
            throws RemoteException {
        CarPowerPolicy policy = mPowerPolicyGroups.get(mLastSetPowerPolicyGroupId).get(state);
        if (policy == null) {
            throw new IllegalArgumentException("No default policy defined for state " + state);
        }
        if (mSilentModeOn) {
            mPendingPowerPolicyId = policy.policyId;
            Log.d(TAG, "Silent mode is on, so cannot apply power policy for state " + state
                    + ", setting pending power policy to " + mPendingPowerPolicyId);
            mCallback.onApplyPowerPolicyFailed(requestId,
                    PowerPolicyFailureReason.POWER_POLICY_FAILURE_CANNOT_OVERRIDE);
            return;
        }
        mHandler.post(() -> {
            try {
                mCallback.onApplyPowerPolicySucceeded(requestId, policy);
                mCallback.updatePowerComponents(policy);
                mLastAppliedPowerPolicyId = policy.policyId;
                mComponentHandler.applyPolicy(policy);
            } catch (Exception e) {
                Log.w(TAG, "Cannot call onApplyPowerPolicySucceeded", e);
            }
        });
    }

    private void applyPowerPolicyInternal(String policyId, String errMsg) {
        mComponentHandler.applyPolicy(mPolicies.get(policyId));
        CarPowerPolicy accumulatedPolicy = mComponentHandler.getAccumulatedPolicy(policyId);
        try {
            mCallback.onPowerPolicyChanged(accumulatedPolicy);
            mCallback.updatePowerComponents(accumulatedPolicy);
            mLastAppliedPowerPolicyId = policyId;
        } catch (RemoteException e) {
            Log.d(TAG, errMsg, e);
        }
    }

    @Override
    public void setPowerPolicyGroup(String policyGroupId) {
        if (mPowerPolicyGroups.get(policyGroupId) == null) {
            throw new IllegalArgumentException("Policy group " + policyGroupId + " undefined");
        }
    }

    private int[] convertIntIterableToArray(Iterable<Integer> iterable) {
        List<Integer> list = new ArrayList<>();
        iterable.forEach(list::add);
        return list.stream().mapToInt(i->i).toArray();
    }

    @Override
    public void notifyPowerPolicyDefinition(String policyId, String[] enabledComponents,
                                            String[] disabledComponents) {
        mLastDefinedPolicyId = policyId;
        CarPowerPolicy policy = new CarPowerPolicy();
        policy.policyId = policyId;
        policy.enabledComponents = convertIntIterableToArray(
                PowerComponentUtil.toPowerComponents(List.of(enabledComponents),
                        /* prefix= */ false));
        policy.disabledComponents = convertIntIterableToArray(
                PowerComponentUtil.toPowerComponents(List.of(disabledComponents),
                        /* prefix= */ false));
        mPolicies.put(policyId, policy);
    }

    @Override
    public void notifyPowerPolicyGroupDefinition(
            String policyGroupId, String[] powerPolicyPerState) {
        String waitForVhalPolicyId = powerPolicyPerState[0];
        if (mPolicies.get(waitForVhalPolicyId) == null) {
            throw new IllegalArgumentException(
                    "No registered policy with ID " + waitForVhalPolicyId);
        }
        String onPolicyId = powerPolicyPerState[1];
        if (mPolicies.get(onPolicyId) == null) {
            throw new IllegalArgumentException(
                    "No registered policy with ID " + onPolicyId);
        }
        mPowerPolicyGroups.put(
                policyGroupId, createPolicyGroup(waitForVhalPolicyId, onPolicyId));
        SparseArray<CarPowerPolicy> policyGroup = new SparseArray<>();
        policyGroup.put(PowerState.WAIT_FOR_VHAL, mPolicies.get(waitForVhalPolicyId));
        policyGroup.put(PowerState.ON, mPolicies.get(onPolicyId));
        mPowerPolicyGroups.put(policyGroupId, policyGroup);
    }

    public String getLastAppliedPowerPolicyId() {
        return mLastAppliedPowerPolicyId;
    }

    public String getLastDefinedPolicyId() {
        return mLastDefinedPolicyId;
    }

    /**
     * Begin observing the silent mode file for changes in silent mode state. Should be called
     * at the beginning of a test case that involves silent mode and power policy.
     */
    public void silentModeFileObserverStartWatching() {
        mFileObserver.startWatching();
    }

    /**
     * Stop observing the silent mode file. Should be called at the end of a test case where
     * {@link #silentModeFileObserverStopWatching()} was called.
     */
    public void silentModeFileObserverStopWatching() {
        mFileObserver.stopWatching();
    }

    @Override
    public int getInterfaceVersion() {
        return ICarPowerPolicyDelegate.VERSION;
    }

    @Override
    public String getInterfaceHash() {
        return ICarPowerPolicyDelegate.HASH;
    }

    private final class SilentModeFileObserver extends FileObserver {
        SilentModeFileObserver(File file, int mask) {
            super(file, mask);
        }

        @Override
        public void onEvent(int event, @Nullable String path) {
            boolean silent;
            try {
                String contents = IoUtils.readFileAsString(
                        mFileKernelSilentMode.getPath().toString()).trim();
                silent = contents.equals("1");
            } catch (Exception e) {
                Log.w(TAG, "Fake CPPD couldn't read kernel silent mode file");
                return;
            }
            Log.d(TAG, "Fake CPPD observed change in silent mode kernel file, silent = "
                    + silent);
            if (silent) {
                mSilentModeOn = true;
                Log.d(TAG, "Fake CPPD attempting to apply silent power policy");
                applyPowerPolicyInternal(SYSTEM_POWER_POLICY_NO_USER_INTERACTION,
                        "Fake CPPD failed to apply silent power policy");
            } else {
                mSilentModeOn = false;
                Log.d(TAG, "Fake CPPD attempting to apply pending power policy");
                if (mPendingPowerPolicyId != null) {
                    applyPowerPolicyInternal(mPendingPowerPolicyId,
                            "Fake CPPD failed to apply pending power policy");
                }
            }
        }
    }

    private static final class ComponentHandler {
        private final IntArray mEnabledComponents = new IntArray();
        private final IntArray mDisabledComponents = new IntArray();

        CarPowerPolicy getAccumulatedPolicy(String policyId) {
            CarPowerPolicy policy = new CarPowerPolicy();
            policy.policyId = policyId;
            policy.enabledComponents = mEnabledComponents.toArray();
            policy.disabledComponents = mDisabledComponents.toArray();
            return policy;
        }

        void applyPolicy(CarPowerPolicy policy) {
            int[] enabledComponents = policy.enabledComponents;
            for (int i = 0; i < enabledComponents.length; i++) {
                int component = enabledComponents[i];
                if (!mEnabledComponents.contains(component)) {
                    if (mDisabledComponents.contains(component)) {
                        mDisabledComponents.remove(mDisabledComponents.indexOf(component));
                    }
                    mEnabledComponents.add(component);
                }
            }
            int[] disabledComponents = policy.disabledComponents;
            for (int i = 0; i < disabledComponents.length; i++) {
                int component = disabledComponents[i];
                if (!mDisabledComponents.contains(component)) {
                    if (mEnabledComponents.contains(component)) {
                        mEnabledComponents.remove(mEnabledComponents.indexOf(component));
                    }
                    mDisabledComponents.add(component);
                }
            }
        }
    }
}
