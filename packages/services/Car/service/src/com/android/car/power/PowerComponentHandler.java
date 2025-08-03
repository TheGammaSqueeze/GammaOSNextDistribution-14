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

package com.android.car.power;

import static android.car.hardware.power.PowerComponent.BLUETOOTH;
import static android.car.hardware.power.PowerComponent.DISPLAY;
import static android.car.hardware.power.PowerComponent.VOICE_INTERACTION;
import static android.car.hardware.power.PowerComponent.WIFI;
import static android.car.hardware.power.PowerComponentUtil.FIRST_POWER_COMPONENT;
import static android.car.hardware.power.PowerComponentUtil.INVALID_POWER_COMPONENT;
import static android.car.hardware.power.PowerComponentUtil.LAST_POWER_COMPONENT;
import static android.car.hardware.power.PowerComponentUtil.powerComponentToString;
import static android.car.hardware.power.PowerComponentUtil.toPowerComponent;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.Nullable;
import android.bluetooth.BluetoothAdapter;
import android.car.builtin.app.AppOpsManagerHelper;
import android.car.builtin.app.VoiceInteractionHelper;
import android.car.builtin.util.Slogf;
import android.car.hardware.power.CarPowerPolicy;
import android.car.hardware.power.CarPowerPolicyFilter;
import android.car.hardware.power.PowerComponent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Process;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.AtomicFile;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.proto.ProtoOutputStream;

import com.android.car.CarLog;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.internal.util.IntArray;
import com.android.car.power.CarPowerDumpProto.PowerComponentHandlerProto;
import com.android.car.power.CarPowerDumpProto.PowerComponentHandlerProto.PowerComponentToState;
import com.android.car.systeminterface.SystemInterface;
import com.android.internal.annotations.GuardedBy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Class that manages power components in the system. A power component mediator corresponding to a
 * power component is created and registered to this class. A power component mediator encapsulates
 * the function of powering on/off.
 */
public final class PowerComponentHandler {
    private static final String TAG = CarLog.tagFor(PowerComponentHandler.class);
    private static final String FORCED_OFF_COMPONENTS_FILENAME =
            "forced_off_components";

    private final Object mLock = new Object();
    private final Context mContext;
    private final SystemInterface mSystemInterface;
    private final AtomicFile mOffComponentsByUserFile;
    private final SparseArray<PowerComponentMediator> mPowerComponentMediators =
            new SparseArray<>();
    @GuardedBy("mLock")
    private final SparseBooleanArray mComponentStates =
            new SparseBooleanArray(LAST_POWER_COMPONENT - FIRST_POWER_COMPONENT + 1);
    @GuardedBy("mLock")
    private final SparseBooleanArray mComponentsOffByPolicy = new SparseBooleanArray();
    @GuardedBy("mLock")
    private final SparseBooleanArray mLastModifiedComponents = new SparseBooleanArray();
    @GuardedBy("mLock")
    private final IntArray mRegisteredComponents = new IntArray();
    private final PackageManager mPackageManager;

    // TODO(b/286303350): remove after power policy refactor is complete; only used for getting
    //                    accumulated policy, and that will be done by CPPD
    @GuardedBy("mLock")
    private String mCurrentPolicyId = "";

    PowerComponentHandler(Context context, SystemInterface systemInterface) {
        this(context, systemInterface, new AtomicFile(new File(systemInterface.getSystemCarDir(),
                FORCED_OFF_COMPONENTS_FILENAME)));
    }

    public PowerComponentHandler(Context context, SystemInterface systemInterface,
            AtomicFile componentStateFile) {
        mContext = context;
        mPackageManager = mContext.getPackageManager();
        mSystemInterface = systemInterface;
        mOffComponentsByUserFile = componentStateFile;
    }

    void init(ArrayMap<String, Integer> customComponents) {
        AppOpsManagerHelper.setTurnScreenOnAllowed(mContext, Process.myUid(),
                mContext.getOpPackageName(), /* isAllowed= */ true);
        PowerComponentMediatorFactory factory = new PowerComponentMediatorFactory();
        synchronized (mLock) {
            readUserOffComponentsLocked();
            for (int component = FIRST_POWER_COMPONENT; component <= LAST_POWER_COMPONENT;
                    component++) {
                // initialize set of known components with pre-defined components
                mRegisteredComponents.add(component);
                mComponentStates.put(component, false);
                PowerComponentMediator mediator = factory.createPowerComponent(component);
                if (mediator == null || !mediator.isComponentAvailable()) {
                    // We don't not associate a mediator with the component.
                    continue;
                }
                mPowerComponentMediators.put(component, mediator);
            }
            if (customComponents != null) {
                for (int i = 0; i < customComponents.size(); ++i)  {
                    mRegisteredComponents.add(customComponents.valueAt(i));
                }
            }
        }
    }

    CarPowerPolicy getAccumulatedPolicy() {
        synchronized (mLock) {
            int enabledComponentsCount = 0;
            int disabledComponentsCount = 0;
            for (int i = 0; i < mRegisteredComponents.size(); ++i) {
                if (mComponentStates.get(mRegisteredComponents.get(i), /* valueIfKeyNotFound= */
                        false)) {
                    enabledComponentsCount++;
                } else {
                    disabledComponentsCount++;
                }
            }
            int[] enabledComponents = new int[enabledComponentsCount];
            int[] disabledComponents = new int[disabledComponentsCount];
            int enabledIndex = 0;
            int disabledIndex = 0;
            for (int i = 0; i < mRegisteredComponents.size(); ++i) {
                int component = mRegisteredComponents.get(i);
                if (mComponentStates.get(component, /* valueIfKeyNotFound= */ false)) {
                    enabledComponents[enabledIndex++] = component;
                } else {
                    disabledComponents[disabledIndex++] = component;
                }
            }
            return new CarPowerPolicy(mCurrentPolicyId, enabledComponents, disabledComponents);
        }
    }

    /**
     * Applies the given policy considering user setting.
     *
     * <p> If a component is the policy is not applied due to user setting, it is not notified to
     * listeners.
     */
    void applyPowerPolicy(CarPowerPolicy policy) {
        int[] enabledComponents = policy.getEnabledComponents();
        int[] disabledComponents = policy.getDisabledComponents();
        synchronized (mLock) {
            mLastModifiedComponents.clear();
            for (int i = 0; i < enabledComponents.length; i++) {
                int component = enabledComponents[i];
                if (mRegisteredComponents.indexOf(component) == -1) {
                    throw new IllegalStateException(
                            "Component with id " + component + " is not registered");
                }
                if (setComponentEnabledLocked(component, /* enabled= */ true)) {
                    mLastModifiedComponents.put(component, /* value= */ true);
                }
            }
            for (int i = 0; i < disabledComponents.length; i++) {
                int component = disabledComponents[i];
                if (mRegisteredComponents.indexOf(component) == -1) {
                    throw new IllegalStateException(
                            "Component with id " + component + " is not registered");
                }
                if (setComponentEnabledLocked(component, /* enabled= */ false)) {
                    mLastModifiedComponents.put(component, /* value= */ true);
                }
            }
            mCurrentPolicyId = policy.getPolicyId();
        }
    }

    boolean isComponentChanged(CarPowerPolicyFilter filter) {
        synchronized (mLock) {
            int[] components = filter.getComponents();
            for (int i = 0; i < components.length; i++) {
                if (mLastModifiedComponents.get(components[i], false)) {
                    return true;
                }
            }
            return false;
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dump(IndentingPrintWriter writer) {
        synchronized (mLock) {
            writer.println("Power components state:");
            writer.increaseIndent();
            for (int i = 0; i < mRegisteredComponents.size(); ++i) {
                int component = mRegisteredComponents.get(i);
                writer.printf("%s: %s\n", powerComponentToString(component),
                        mComponentStates.get(component, /* valueIfKeyNotFound= */ false)
                                ? "on" : "off");
            }
            writer.decreaseIndent();
            writer.println("Components powered off by power policy:");
            writer.increaseIndent();
            for (int i = 0; i < mComponentsOffByPolicy.size(); i++) {
                writer.println(powerComponentToString(mComponentsOffByPolicy.keyAt(i)));
            }
            writer.decreaseIndent();
            writer.print("Components changed by the last policy: ");
            writer.increaseIndent();
            for (int i = 0; i < mLastModifiedComponents.size(); i++) {
                if (i > 0) writer.print(", ");
                writer.print(powerComponentToString(mLastModifiedComponents.keyAt(i)));
            }
            writer.println();
            writer.decreaseIndent();
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dumpProto(ProtoOutputStream proto) {
        synchronized (mLock) {
            long powerComponentHandlerToken = proto.start(
                    CarPowerDumpProto.POWER_COMPONENT_HANDLER);

            for (int i = 0; i < mRegisteredComponents.size(); ++i) {
                long powerComponentStateMappingToken = proto.start(
                        PowerComponentHandlerProto.POWER_COMPONENT_STATE_MAPPINGS);
                int component = mRegisteredComponents.get(i);
                proto.write(
                        PowerComponentToState.POWER_COMPONENT, powerComponentToString(component));
                proto.write(PowerComponentToState.STATE, mComponentStates.get(
                        component, /* valueIfKeyNotFound= */ false));
                proto.end(powerComponentStateMappingToken);
            }

            for (int i = 0; i < mComponentsOffByPolicy.size(); i++) {
                proto.write(PowerComponentHandlerProto.COMPONENTS_OFF_BY_POLICY,
                        powerComponentToString(mComponentsOffByPolicy.keyAt(i)));
            }

            StringBuilder lastModifiedComponents = new StringBuilder();
            for (int i = 0; i < mLastModifiedComponents.size(); i++) {
                if (i > 0) lastModifiedComponents.append(", ");
                lastModifiedComponents.append(
                        powerComponentToString(mLastModifiedComponents.keyAt(i)));
            }
            proto.write(PowerComponentHandlerProto.LAST_MODIFIED_COMPONENTS,
                    lastModifiedComponents.toString());

            proto.end(powerComponentHandlerToken);
        }
    }

    /**
     * Modifies power component's state, considering user setting.
     *
     * @return {@code true} if power state is changed. Otherwise, {@code false}
     */
    @GuardedBy("mLock")
    private boolean setComponentEnabledLocked(int component, boolean enabled) {
        int componentIndex = mComponentStates.indexOfKey(component); // check if component exists
        boolean oldState = mComponentStates.get(component, /* valueIfKeyNotFound= */ false);
        // If components is not in mComponentStates and enabled is false, oldState will be false,
        // as result function will return false without adding component to mComponentStates
        if (oldState == enabled && componentIndex >= 0) {
            return false;
        }

        mComponentStates.put(component, enabled);

        PowerComponentMediator mediator = mPowerComponentMediators.get(component);
        if (mediator == null) {
            return true;
        }

        boolean needPowerChange = false;
        if (mediator.isUserControllable()) {
            if (!enabled && mediator.isEnabled()) {
                mComponentsOffByPolicy.put(component, /* value= */ true);
                needPowerChange = true;
            }
            if (enabled && mComponentsOffByPolicy.get(component, /* valueIfKeyNotFound= */ false)) {
                mComponentsOffByPolicy.delete(component);
                needPowerChange = true;
            }
            if (needPowerChange) {
                writeUserOffComponentsLocked();
            }
        } else {
            needPowerChange = true;
        }

        if (needPowerChange) {
            mediator.setEnabled(enabled);
        }
        return true;
    }

    @GuardedBy("mLock")
    private void readUserOffComponentsLocked() {
        boolean invalid = false;
        mComponentsOffByPolicy.clear();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(mOffComponentsByUserFile.openRead(),
                        StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int component = toPowerComponent(line.trim(), /* prefix= */ false);
                if (component == INVALID_POWER_COMPONENT) {
                    invalid = true;
                    break;
                }
                mComponentsOffByPolicy.put(component, /* value= */ true);
            }
        } catch (FileNotFoundException e) {
            // Behave as if there are no forced-off components.
            return;
        } catch (IOException e) {
            Slogf.w(TAG, "Failed to read %s: %s", FORCED_OFF_COMPONENTS_FILENAME, e);
            return;
        }
        if (invalid) {
            mOffComponentsByUserFile.delete();
        }
    }

    private void writeUserOffComponentsLocked() {
        FileOutputStream fos;
        try {
            fos = mOffComponentsByUserFile.startWrite();
        } catch (IOException e) {
            Slogf.e(TAG, e, "Cannot create %s", FORCED_OFF_COMPONENTS_FILENAME);
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {
            synchronized (mLock) {
                for (int i = 0; i < mComponentsOffByPolicy.size(); i++) {
                    if (!mComponentsOffByPolicy.valueAt(i)) {
                        continue;
                    }
                    writer.write(powerComponentToString(mComponentsOffByPolicy.keyAt(i)));
                    writer.newLine();
                }
            }
            writer.flush();
            mOffComponentsByUserFile.finishWrite(fos);
        } catch (IOException e) {
            mOffComponentsByUserFile.failWrite(fos);
            Slogf.e(TAG, e, "Writing %s failed", FORCED_OFF_COMPONENTS_FILENAME);
        }
    }

    /**
     * Method to be used from tests and when policy is defined through command line
     */
    public void registerCustomComponents(Integer[] components) {
        synchronized (mLock) {
            for (int i = 0; i < components.length; i++) {
                int componentId = components[i];
                // Add only new components
                if (mRegisteredComponents.indexOf(componentId) == -1) {
                    mRegisteredComponents.add(componentId);
                }
            }
        }
    }

    abstract static class PowerComponentMediator {
        protected int mComponentId;

        PowerComponentMediator(int component) {
            mComponentId = component;
        }

        public boolean isComponentAvailable() {
            return false;
        }

        public boolean isUserControllable() {
            return false;
        }

        public boolean isEnabled() {
            return false;
        }

        public void setEnabled(boolean enabled) {}
    }

    // TODO(b/178824607): Check if power policy can turn on/off display as quickly as the existing
    // implementation.
    private final class DisplayPowerComponentMediator extends PowerComponentMediator {
        DisplayPowerComponentMediator() {
            super(DISPLAY);
        }

        @Override
        public boolean isComponentAvailable() {
            // It is assumed that display is supported in all vehicles.
            return true;
        }

        @Override
        public boolean isEnabled() {
            return mSystemInterface.isAnyDisplayEnabled();
        }

        @Override
        public void setEnabled(boolean enabled) {
            mSystemInterface.setAllDisplayState(enabled);
            Slogf.d(TAG, "Display power component is %s", enabled ? "on" : "off");
        }
    }

    private final class WifiPowerComponentMediator extends PowerComponentMediator {
        private final WifiManager mWifiManager;

        WifiPowerComponentMediator() {
            super(WIFI);
            mWifiManager = mContext.getSystemService(WifiManager.class);
        }

        @Override
        public boolean isComponentAvailable() {
            return mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI);
        }

        @Override
        public boolean isUserControllable() {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return mWifiManager.isWifiEnabled();
        }

        @Override
        public void setEnabled(boolean enabled) {
            mWifiManager.setWifiEnabled(enabled);
            Slogf.d(TAG, "Wifi power component is %s", enabled ? "on" : "off");
        }
    }

    private final class VoiceInteractionPowerComponentMediator extends PowerComponentMediator {

        private boolean mIsEnabled = true;

        VoiceInteractionPowerComponentMediator() {
            super(VOICE_INTERACTION);
        }

        @Override
        public boolean isComponentAvailable() {
            return VoiceInteractionHelper.isAvailable();
        }

        @Override
        public boolean isEnabled() {
            return mIsEnabled;
        }

        @Override
        public void setEnabled(boolean enabled) {
            try {
                VoiceInteractionHelper.setEnabled(enabled);
                mIsEnabled = enabled;
                Slogf.d(TAG, "Voice Interaction power component is %s", enabled ? "on" : "off");
            } catch (RemoteException e) {
                Slogf.w(TAG, e, "VoiceInteractionHelper.setEnabled(%b) failed", enabled);
            }
        }
    }

    private final class BluetoothPowerComponentMediator extends PowerComponentMediator {
        private final BluetoothAdapter mBluetoothAdapter;

        BluetoothPowerComponentMediator() {
            super(BLUETOOTH);
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        @Override
        public boolean isComponentAvailable() {
            return mPackageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
        }

        @Override
        public boolean isUserControllable() {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return mBluetoothAdapter.isEnabled();
        }

        @Override
        public void setEnabled(boolean enabled) {
            // No op
            Slogf.w(TAG, "Bluetooth power is controlled by "
                    + "com.android.car.BluetoothPowerPolicy");
        }
    }

    private final class PowerComponentMediatorFactory {
        @Nullable
        PowerComponentMediator createPowerComponent(int component) {
            switch (component) {
                case PowerComponent.AUDIO:
                    // We don't control audio in framework level, because audio is enabled or
                    // disabled in audio HAL according to the current power policy.
                    return null;
                case PowerComponent.MEDIA:
                    return null;
                case PowerComponent.DISPLAY:
                    return new DisplayPowerComponentMediator();
                case PowerComponent.WIFI:
                    return new WifiPowerComponentMediator();
                case PowerComponent.CELLULAR:
                    return null;
                case PowerComponent.ETHERNET:
                    return null;
                case PowerComponent.PROJECTION:
                    return null;
                case PowerComponent.NFC:
                    return null;
                case PowerComponent.INPUT:
                    return null;
                case PowerComponent.VOICE_INTERACTION:
                    return new VoiceInteractionPowerComponentMediator();
                case PowerComponent.VISUAL_INTERACTION:
                    return null;
                case PowerComponent.TRUSTED_DEVICE_DETECTION:
                    return null;
                case PowerComponent.MICROPHONE:
                    // We don't control microphone in framework level, because microphone is enabled
                    // or disabled in audio HAL according to the current power policy.
                    return null;
                case PowerComponent.BLUETOOTH:
                    // com.android.car.BluetoothDeviceConnectionPolicy handles power state change.
                    // So, bluetooth mediator doesn't directly turn on/off BT, but it changes policy
                    // behavior, considering user intervetion.
                    return new BluetoothPowerComponentMediator();
                case PowerComponent.LOCATION:
                    // GNSS HAL handles power state change.
                    return null;
                case PowerComponent.CPU:
                    return null;
                default:
                    Slogf.w(TAG, "Unknown component(%d)", component);
                    return null;
            }
        }
    }
}
