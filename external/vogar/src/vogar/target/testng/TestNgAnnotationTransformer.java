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
package vogar.target.testng;

import org.testng.IAnnotationTransformer;
import org.testng.annotations.ITestAnnotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link IAnnotationTransformer} that:
 *
 * <ul>
 *   <li>Handles vogar {@code --timeout} option and overrides test run timeout if necessary.
 *   <li>Skips past (discards) all tests up to and including one that failed previously causing the
 *       process to exit.
 *       <p>If {@link #skipReference} has a null value then this lets all tests run. Otherwise, this
 *       filters out all tests up to and including the one whose name matches the value in the
 *       reference at which point the reference is set to null, so all following tests are kept.
 *       This class is a TestNG-version of {@link vogar.target.SkipPastFilter} which handles {@code
 *       --skipPast} VM flag the same way.
 * </ul>
 *
 * @see vogar.Vogar
 */
public class TestNgAnnotationTransformer implements IAnnotationTransformer {

    private final long timeoutMillis;
    private final AtomicReference<String> skipReference;
    private final String qualification;
    private final HashSet<String> args;

    public TestNgAnnotationTransformer(
            int timeoutSeconds,
            AtomicReference<String> skipReference,
            String qualification,
            String[] vogarArgs) {
        this.timeoutMillis = 1000L * timeoutSeconds;
        this.skipReference = skipReference;
        this.qualification = qualification;
        this.args = new HashSet<>();
        this.args.addAll(List.of(vogarArgs));
    }

    @Override
    public void transform(
            ITestAnnotation annotation,
            Class testClass,
            Constructor testConstructor,
            Method testMethod) {
        // Use the least timeout between vogar --timeout and @Test(timeout = N) annotation.
        if (timeoutMillis > 0) {
            long testTimeout = annotation.getTimeOut();
            if (testTimeout > timeoutMillis) {
                annotation.setTimeOut(timeoutMillis);
            }
        }

        // Skip up to and including given test name if --skipPast provided.
        final String skipPast = skipReference.get();
        if (skipPast != null && testClass == null && testMethod != null) {
            String name = testMethod.getName();
            if (skipPast.equals(name)) {
                skipReference.set(null);
            }
            annotation.setEnabled(false);
        }

        // Disable all but specified tests if qualified test name provided (package.class#method).
        if (qualification != null && testMethod != null) {
            if (!qualification.equals(testMethod.getName())) {
                annotation.setEnabled(false);
            }
        }
        if (!args.isEmpty() && testMethod != null) {
            if (!args.contains(testMethod.getName())) {
                annotation.setEnabled(false);
            }
        }
    }
}
