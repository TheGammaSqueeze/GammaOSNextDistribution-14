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

package android.signature.cts.tests;

import android.signature.cts.AnnotationChecker;
import android.signature.cts.ClassProvider;
import android.signature.cts.ExpectedFailuresFilter;
import android.signature.cts.FailureType;
import android.signature.cts.JDiffClassDescription;
import android.signature.cts.ResultObserver;
import android.signature.cts.tests.data.ApiAnnotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test class for {@link ExpectedFailuresFilter} when run with {@link AnnotationChecker}.
 */
@RunWith(JUnit4.class)
public class ExpectedFailuresFilterAnnotationCheckerTest
    extends ApiPresenceCheckerTest<AnnotationChecker> {

    @Override
    protected AnnotationChecker createChecker(ResultObserver resultObserver,
            ClassProvider provider) {
        return new AnnotationChecker(resultObserver, provider, ApiAnnotation.class.getName(), null);
    }

    @Test
    public void testIgnoreExpectedFailures_TestPasses() {
        try (NoFailures observer = new NoFailures()) {

            ResultObserver filter = new ExpectedFailuresFilter(observer, Arrays.asList(
                    "extra_class:android.signature.cts.tests.data.ForciblyPublicizedPrivateClass",
                    "extra_constructor:public android.signature.cts.tests.data.SystemApiClass()",
                    "extra_method:public void android.signature.cts.tests.data.SystemApiClass.apiMethod()",
                    "extra_field:public boolean android.signature.cts.tests.data.SystemApiClass.apiField"
            ));

            // Define the API that is expected to be provided by the SystemApiClass. Omitted members
            // are actually provided by the SytstemApiClass definition and so will result in an
            // extra_... error.
            JDiffClassDescription clz = createClass("SystemApiClass");
            // (omitted) addConstructor(clz);
            // (omitted) addPublicVoidMethod(clz, "apiMethod");
            // (omitted) addPublicBooleanField(clz, "apiField");

            checkSignatureCompliance(clz, filter,
                    "android.signature.cts.tests.data.PublicApiClass");
            // Note that ForciblyPublicizedPrivateClass is now included in the runtime classes
        }
    }

    @Test
    public void testIgnoreExpectedFailures_TestStillFails() {
        try (ExpectFailure observer = new ExpectFailure(FailureType.MISSING_ANNOTATION)) {
            ResultObserver filter = new ExpectedFailuresFilter(observer, Arrays.asList(
                    "extra_method:public void android.signature.cts.tests.data.SystemApiClass.apiMethod()",
                    "extra_field:public boolean android.signature.cts.tests.data.SystemApiClass.apiField"
            ));

            // Define the API that is expected to be provided by the SystemApiClass. Omitted members
            // are actually provided by the SytstemApiClass definition and so will result in an
            // extra_... error.
            JDiffClassDescription clz = createClass("SystemApiClass");
            addConstructor(clz);
            // (omitted) addPublicVoidMethod(clz, "apiMethod");
            // (omitted) addPublicBooleanField(clz, "apiField");

            checkSignatureCompliance(clz, filter,
                    "android.signature.cts.tests.data.PublicApiClass",
                    "android.signature.cts.tests.data.ForciblyPublicizedPrivateClass");
        }
    }
}
