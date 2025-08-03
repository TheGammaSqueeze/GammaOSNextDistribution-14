/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.car.cts.builtin;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.ITestInformationReceiver;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.AssumptionViolatedException;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Base class for all car mainline builtin API host test cases.
 */
// NOTE: must be public because of @Rules
public abstract class CarBuiltinApiHostCtsBase extends BaseHostJUnit4Test {

    @Rule
    public final RequiredFeatureRule mHasAutomotiveRule = new RequiredFeatureRule(this,
            "android.hardware.type.automotive");


    protected static final class RequiredFeatureRule implements TestRule {

        private final ITestInformationReceiver mReceiver;
        private final String mFeature;

        RequiredFeatureRule(ITestInformationReceiver receiver, String feature) {
            mReceiver = receiver;
            mFeature = feature;
        }

        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {

                @Override
                public void evaluate() throws Throwable {
                    boolean hasFeature = false;
                    try {
                        hasFeature = mReceiver.getTestInformation().getDevice()
                                .hasFeature(mFeature);
                    } catch (DeviceNotAvailableException e) {
                        CLog.e("Could not check if device has feature %s: %e", mFeature, e);
                        return;
                    }

                    if (!hasFeature) {
                        CLog.d("skipping %s#%s"
                                + " because device does not have feature '%s'",
                                description.getClassName(), description.getMethodName(), mFeature);
                        throw new AssumptionViolatedException("Device does not have feature '"
                                + mFeature + "'");
                    }
                    base.evaluate();
                }
            };
        }

        @Override
        public String toString() {
            return "RequiredFeatureRule[" + mFeature + "]";
        }
    }
}
