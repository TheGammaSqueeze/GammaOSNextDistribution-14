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

package com.android.bedstead.harrier;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.remotedpc.RemoteDpc;
import com.android.bedstead.testapp.TestAppProvider;

import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * A helper class to finely test behavior of {@code DeviceState} components.
 */
public final class DeviceStateTester {

    private final DeviceState mDeviceState = new DeviceState();

    /** This value can be overridden using {@code stepName()}. */
    private String mStepName = "deviceStateInternalTest";

    /**
     * Apply bedstead {@code annotations} to a dynamically generated test.
     * <p>
     * Use {@code runnable} to execute post-processing step(s) for e.g. assertions or
     * getting/setting a state.
     */
    public void apply(List<Annotation> annotations, Runnable runnable) {
        setup(annotations);
        runnable.run();
    }

    /**
     * Use this method to give a name to your test which would help in analyzing the logs.
     */
    public DeviceStateTester stepName(String name) {
        mStepName = name;
        return this;
    }

    /**
     * Teardown all shareable and non-shareable states.
     */
    public void tearDown() {
        mDeviceState.teardown();
    }

    private void setup(List<Annotation> annotations) {
        try {
            Description description =
                    Description.createTestDescription(this.getClass(), mStepName,
                            annotations.toArray(new Annotation[0]));

            statement(description).evaluate();
        } catch (Throwable e) {
            throw new NeneException("Unable to setup DeviceStateTester", e);
        }
    }

    private Statement statement(Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                mDeviceState.prepareTestState(description);
            }
        };
    }

    /** See {@link DeviceState#profileOwner()}. */
    public RemoteDpc profileOwner() {
        return mDeviceState.profileOwner();
    }

    /** See {@link DeviceState#workProfile()}. */
    public UserReference workProfile() {
        return mDeviceState.workProfile(TestApis.users().instrumented());
    }

    /** See {@link DeviceState#testApps()}. */
    public TestAppProvider testApps() {
        return mDeviceState.testApps();
    }
}
