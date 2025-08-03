/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tradefed.device.cloud;

import com.android.ddmlib.IDevice;
import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.DynamicRemoteFileResolver;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.OptionCopier;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IDeviceMonitor;
import com.android.tradefed.device.IDeviceStateMonitor;
import com.android.tradefed.device.TestDevice;
import com.android.tradefed.device.TestDeviceOptions;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestLoggerReceiver;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.MultiMap;


/**
 * A device running inside a virtual machine that we manage remotely via a Tradefed instance inside
 * the VM.
 */
public class ManagedRemoteDevice extends TestDevice implements ITestLoggerReceiver {

    private TestDeviceOptions mCopiedOptions;
    private IConfiguration mValidationConfig;

    /**
     * Creates a {@link ManagedRemoteDevice}.
     *
     * @param device the associated {@link IDevice}
     * @param stateMonitor the {@link IDeviceStateMonitor} mechanism to use
     * @param allocationMonitor the {@link IDeviceMonitor} to inform of allocation state changes.
     */
    public ManagedRemoteDevice(
            IDevice device, IDeviceStateMonitor stateMonitor, IDeviceMonitor allocationMonitor) {
        super(device, stateMonitor, allocationMonitor);
    }

    @Override
    public void preInvocationSetup(IBuildInfo info, MultiMap<String, String> attributes)
            throws TargetSetupError, DeviceNotAvailableException {
        mCopiedOptions = null;
        super.preInvocationSetup(info, attributes);
    }

    /** {@inheritDoc} */
    @Override
    public void postInvocationTearDown(Throwable exception) {
        // Reset the internal variable
        mCopiedOptions = null;
        if (mValidationConfig != null) {
            mValidationConfig.cleanConfigurationData();
            mValidationConfig = null;
        }
        // Ensure parent postInvocationTearDown is always called.
        super.postInvocationTearDown(exception);
    }

    /**
     * Override the base getter to be able to resolve dynamic options before attempting to do the
     * remote setup.
     */
    @Override
    public TestDeviceOptions getOptions() {
        if (mCopiedOptions == null) {
            CLog.d("Copying TestDeviceOptions for dynamic configs.");
            mCopiedOptions = new TestDeviceOptions();
            TestDeviceOptions options = super.getOptions();
            OptionCopier.copyOptionsNoThrow(options, mCopiedOptions);
            mValidationConfig = new Configuration("validation", "validation");
            mValidationConfig.setDeviceOptions(mCopiedOptions);
            try {
                mValidationConfig.resolveDynamicOptions(new DynamicRemoteFileResolver());
            } catch (BuildRetrievalError | ConfigurationException e) {
                throw new RuntimeException(e);
            }
        }
        return mCopiedOptions;
    }
}
