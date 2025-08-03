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

package android.cts.install.host;

import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.ITestInformationReceiver;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runners.Parameterized;
import org.junit.runners.model.InitializationError;
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters;
import org.junit.runners.parameterized.ParametersRunnerFactory;
import org.junit.runners.parameterized.TestWithParameters;

import java.util.List;

/**
 * Custom JUnit4 parameterized test runner that also accommodate {@link ITestInformationReceiver}
 * to support {@link BaseHostJUnit4Test#getDevice()} properly.
 */
public final class DeviceParameterized extends Parameterized implements IAbiReceiver, ITestInformationReceiver {
    private TestInformation mTestInformation;
    private List<Runner> mRunners;
    private IAbi mAbi;

    public DeviceParameterized(Class<?> klass) throws Throwable {
        super(klass);
        mRunners = super.getChildren();
    }

    @Override
    public void setTestInformation(TestInformation testInformation) {
        mTestInformation = testInformation;
        for (Runner runner: mRunners) {
            if (runner instanceof  ITestInformationReceiver) {
                ((ITestInformationReceiver)runner).setTestInformation(mTestInformation);
            }
        }
    }

    @Override
    public TestInformation getTestInformation() {
        return mTestInformation;
    }

    public static class RunnerFactory implements ParametersRunnerFactory {
        @Override
        public Runner createRunnerForTestWithParameters(TestWithParameters test)
                throws InitializationError {
            return new DeviceParameterizedRunner(test);
        }
    }

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
        for (Runner runner: mRunners) {
            if (runner instanceof  IAbiReceiver) {
                ((IAbiReceiver)runner).setAbi(mAbi);
            }
        }
    }

    @Override
    public IAbi getAbi() {
      return mAbi;
    }

    public static class DeviceParameterizedRunner
            extends BlockJUnit4ClassRunnerWithParameters implements IAbiReceiver, ITestInformationReceiver {
        private TestInformation mTestInformation;
        private IAbi mAbi;

        public DeviceParameterizedRunner(TestWithParameters test) throws InitializationError {
            super(test);
        }

        /** Overrides createTest in order to set the device. */
        @Override
        public Object createTest() throws Exception {
            Object testObj = super.createTest();
            if (testObj instanceof ITestInformationReceiver) {
                if (mTestInformation == null) {
                    throw new IllegalArgumentException("Missing test info");
                }
                ((ITestInformationReceiver) testObj).setTestInformation(mTestInformation);
            }
            if (testObj instanceof IAbiReceiver) {
                ((IAbiReceiver) testObj).setAbi(mAbi);
            }
            return testObj;
        }

        @Override
        public void setTestInformation(TestInformation testInformation) {
            mTestInformation = testInformation;
        }

        @Override
        public TestInformation getTestInformation() {
            return mTestInformation;
        }

        @Override
        public void setAbi(IAbi abi) {
          mAbi = abi;
        }

        @Override
        public IAbi getAbi() {
          return mAbi;
        }

        @Override
        public Description getDescription() {
            // Make sure it includes test class name when generating parameterized test suites.
            Description desc = Description.createSuiteDescription(getTestClass().getJavaClass());
            // Invoke super getDescription to apply filtered children
            super.getDescription().getChildren().forEach(child -> desc.addChild(child));
            return desc;
        }
    }
}
