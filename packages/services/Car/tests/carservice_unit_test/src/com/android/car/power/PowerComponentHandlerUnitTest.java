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

package com.android.car.power;

import static android.car.hardware.power.PowerComponent.AUDIO;
import static android.car.hardware.power.PowerComponent.BLUETOOTH;
import static android.car.hardware.power.PowerComponent.CELLULAR;
import static android.car.hardware.power.PowerComponent.CPU;
import static android.car.hardware.power.PowerComponent.DISPLAY;
import static android.car.hardware.power.PowerComponent.ETHERNET;
import static android.car.hardware.power.PowerComponent.INPUT;
import static android.car.hardware.power.PowerComponent.LOCATION;
import static android.car.hardware.power.PowerComponent.MEDIA;
import static android.car.hardware.power.PowerComponent.MICROPHONE;
import static android.car.hardware.power.PowerComponent.NFC;
import static android.car.hardware.power.PowerComponent.PROJECTION;
import static android.car.hardware.power.PowerComponent.TRUSTED_DEVICE_DETECTION;
import static android.car.hardware.power.PowerComponent.VISUAL_INTERACTION;
import static android.car.hardware.power.PowerComponent.VOICE_INTERACTION;
import static android.car.hardware.power.PowerComponent.WIFI;
import static android.car.hardware.power.PowerComponentUtil.INVALID_POWER_COMPONENT;

import static com.android.car.test.power.CarPowerPolicyUtil.assertPolicyIdentical;

import static com.google.common.truth.Truth.assertWithMessage;

import android.car.hardware.power.CarPowerPolicy;
import android.car.test.util.TemporaryFile;
import android.content.Context;
import android.util.AtomicFile;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.systeminterface.SystemInterface;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests for {@link PowerComponentHandler}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class PowerComponentHandlerUnitTest {
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    @Mock
    private SystemInterface mSystemInterface;
    private PowerComponentHandler mHandler;
    private TemporaryFile mComponentStateFile;

    @Before
    public void setUp() throws Exception {
        mComponentStateFile = new TemporaryFile("COMPONENT_STATE_FILE");
        mHandler = new PowerComponentHandler(mContext, mSystemInterface,
                new AtomicFile(mComponentStateFile.getFile()));
        mHandler.init(/* customComponents = */ null);
    }

    @Test
    public void testGetAccumulatedPolicy_firstTime() throws Exception {
        CarPowerPolicy policy = mHandler.getAccumulatedPolicy();
        CarPowerPolicy expected = new CarPowerPolicy("", new int[]{},
                new int[]{AUDIO, BLUETOOTH, CELLULAR, CPU, DISPLAY, ETHERNET, INPUT, LOCATION,
                        MEDIA, MICROPHONE, NFC, PROJECTION, TRUSTED_DEVICE_DETECTION,
                        VISUAL_INTERACTION, VOICE_INTERACTION, WIFI});

        assertPolicyIdentical(expected, policy);
    }

    @Test
    public void testApplyPowerPolicy_oneTime() throws Exception {
        CarPowerPolicy policy = new CarPowerPolicy("test_policy1", new int[]{WIFI, BLUETOOTH,
                DISPLAY, VOICE_INTERACTION}, new int[]{AUDIO});
        CarPowerPolicy expected = new CarPowerPolicy("test_policy1", new int[]{WIFI, BLUETOOTH,
                DISPLAY, VOICE_INTERACTION}, new int[]{AUDIO, CELLULAR, CPU, ETHERNET, INPUT,
                    LOCATION, MEDIA, MICROPHONE, NFC, PROJECTION, TRUSTED_DEVICE_DETECTION,
                        VISUAL_INTERACTION});

        mHandler.applyPowerPolicy(policy);

        assertPolicyIdentical(expected, mHandler.getAccumulatedPolicy());
    }

    @Test
    public void testApplyPowerPolicy_multipleTimes() throws Exception {
        CarPowerPolicy[] policies = new CarPowerPolicy[]{
                new CarPowerPolicy("test_policy1", new int[]{WIFI}, new int[]{AUDIO}),
                new CarPowerPolicy("test_policy2", new int[]{WIFI, DISPLAY}, new int[]{NFC}),
                new CarPowerPolicy("test_policy3", new int[]{CPU, INPUT}, new int[]{WIFI}),
                new CarPowerPolicy("test_policy4", new int[]{MEDIA, AUDIO}, new int[]{})};
        CarPowerPolicy expected = new CarPowerPolicy("test_policy4",
                new int[]{AUDIO, MEDIA, DISPLAY, INPUT, CPU},
                new int[]{BLUETOOTH, CELLULAR, ETHERNET, LOCATION, MICROPHONE, NFC, PROJECTION,
                        TRUSTED_DEVICE_DETECTION, VISUAL_INTERACTION, VOICE_INTERACTION, WIFI});

        for (CarPowerPolicy policy : policies) {
            mHandler.applyPowerPolicy(policy);
        }

        assertPolicyIdentical(expected, mHandler.getAccumulatedPolicy());
    }

    @Test
    public void testApplyPowerPolicy_withCustomComponents() throws Exception {
        int customComponentId = 1001;
        mHandler.registerCustomComponents(new Integer[]{customComponentId});
        CarPowerPolicy policy1 = new CarPowerPolicy("test_policy1",
                new int[]{WIFI, customComponentId}, new int[]{AUDIO});
        CarPowerPolicy policy2 = new CarPowerPolicy("test_policy2", new int[]{WIFI, DISPLAY},
                new int[]{NFC});

        mHandler.applyPowerPolicy(policy1);
        mHandler.applyPowerPolicy(policy2);

        CarPowerPolicy expected = new CarPowerPolicy("test_policy2",
                new int[]{DISPLAY, WIFI, customComponentId},
                new int[]{INPUT, MEDIA, AUDIO, BLUETOOTH, CELLULAR, ETHERNET, LOCATION, MICROPHONE,
                        NFC, PROJECTION,
                        TRUSTED_DEVICE_DETECTION, VISUAL_INTERACTION, VOICE_INTERACTION, CPU});
        assertPolicyIdentical(expected, mHandler.getAccumulatedPolicy());

        CarPowerPolicy policy3 = new CarPowerPolicy("test_policy3", new int[]{WIFI, AUDIO},
                new int[]{customComponentId});
        CarPowerPolicy policy4 = new CarPowerPolicy("test_policy4", new int[]{WIFI},
                new int[]{NFC, DISPLAY});

        mHandler.applyPowerPolicy(policy3);
        mHandler.applyPowerPolicy(policy4);

        expected = new CarPowerPolicy("test_policy4",
                new int[]{WIFI, AUDIO},
                new int[]{DISPLAY, INPUT, MEDIA, BLUETOOTH, CELLULAR, ETHERNET, LOCATION,
                        MICROPHONE, NFC, PROJECTION,
                        TRUSTED_DEVICE_DETECTION, VISUAL_INTERACTION, VOICE_INTERACTION, CPU,
                        customComponentId});
        assertPolicyIdentical(expected, mHandler.getAccumulatedPolicy());
    }

    @Test
    public void testPowerComponentMediator() {
        PowerComponentHandler.PowerComponentMediator mediator =
                new PowerComponentMediatorDefault();

        assertWithMessage("Default value for isComponentAvailable()")
                .that(mediator.isComponentAvailable()).isFalse();
        assertWithMessage("Default value for isEnabled()").that(mediator.isEnabled()).isFalse();

        mediator.setEnabled(true);

        // Default setEnabled() does nothing.
        assertWithMessage("Value after setEnabled(true)").that(mediator.isEnabled()).isFalse();
    }

    private static final class PowerComponentMediatorDefault extends
            PowerComponentHandler.PowerComponentMediator {
        PowerComponentMediatorDefault() {
            super(INVALID_POWER_COMPONENT);
        }
    }
}
