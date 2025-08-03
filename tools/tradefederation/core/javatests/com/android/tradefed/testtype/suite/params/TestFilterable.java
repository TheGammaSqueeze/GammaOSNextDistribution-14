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

package com.android.tradefed.testtype.suite.params;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.ITestAnnotationFilterReceiver;

import java.util.HashSet;
import java.util.Set;

/** Class used to test filtering. */
public class TestFilterable implements IRemoteTest, ITestAnnotationFilterReceiver {

    private Set<String> mExcludeAnnotations = new HashSet<>();
    private Set<String> mIncludeAnnotations = new HashSet<>();

    @Override
    public void addIncludeAnnotation(String annotation) {
        mIncludeAnnotations.add(annotation);
    }

    @Override
    public void addExcludeAnnotation(String notAnnotation) {
        mExcludeAnnotations.add(notAnnotation);
    }

    @Override
    public Set<String> getExcludeAnnotations() {
        return mExcludeAnnotations;
    }

    @Override
    public Set<String> getIncludeAnnotations() {
        return mIncludeAnnotations;
    }

    @Override
    public void clearIncludeAnnotations() {
        mIncludeAnnotations.clear();
    }

    @Override
    public void clearExcludeAnnotations() {
        mExcludeAnnotations.clear();
    }

    @Override
    public void addAllIncludeAnnotation(Set<String> annotations) {
        mIncludeAnnotations.addAll(annotations);
    }

    @Override
    public void addAllExcludeAnnotation(Set<String> notAnnotations) {
        mExcludeAnnotations.addAll(notAnnotations);
    }

    @Override
    public void run(TestInformation testInfo, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        // ignore
    }
}
