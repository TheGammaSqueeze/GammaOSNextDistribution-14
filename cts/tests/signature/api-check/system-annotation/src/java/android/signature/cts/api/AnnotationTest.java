/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.signature.cts.api;

import android.os.Bundle;
import android.signature.cts.AnnotationChecker;
import android.signature.cts.ApiDocumentParser;
import android.signature.cts.JDiffClassDescription;

import android.signature.cts.LogHelper;
import android.util.Log;
import androidx.test.InstrumentationRegistry;
import com.android.compatibility.common.util.DynamicConfigDeviceSide;
import com.android.compatibility.common.util.PropertyUtil;
import java.util.List;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Predicate;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Checks that parts of the device's API that are annotated (e.g. with android.annotation.SystemApi)
 * match the API definition.
 */
public class AnnotationTest extends AbstractApiTest {

    private static final String TAG = AnnotationTest.class.getSimpleName();

    private String[] mExpectedApiFiles;
    private String mAnnotationForExactMatch;

    @Override
    protected void initializeFromArgs(Bundle instrumentationArgs) throws Exception {
        mExpectedApiFiles = getCommaSeparatedListRequired(instrumentationArgs, "expected-api-files");
        mAnnotationForExactMatch = instrumentationArgs.getString("annotation-for-exact-match");
    }

    private Predicate<? super JDiffClassDescription> androidAutoClassesFilter() {
        if (getInstrumentation().getContext().getPackageManager().hasSystemFeature(
                "android.hardware.type.automotive")) {
            return clz -> true;
        } else {
            return clz -> !clz.getAbsoluteClassName().startsWith("android.car.");
        }
    }

    /**
     * Tests that the parts of the device's API that are annotated (e.g. with
     * android.annotation.SystemApi) match the API definition.
     */
    @Test
    @Ignore("b/301075649")
    public void testAnnotation() {
       AnnotationChecker.ResultFilter filter = new AnnotationChecker.ResultFilter() {
            @Override
            public boolean skip(Class<?> clazz) {
                return false;
            }

            @Override
            public boolean skip(Constructor<?> ctor) {
                return false;
            }

            @Override
            public boolean skip(Method m) {
                return false;
            }

            @Override
            public boolean skip(Field f) {
                // The R.styleable class is not part of the API because it's annotated with
                // @doconly. But the class actually exists in the runtime classpath.  To avoid
                // the mismatch, skip the check for fields from the class.
                return "android.R$styleable".equals(f.getDeclaringClass().getName());
            }
        };
        runWithTestResultObserver(resultObserver -> {
            AnnotationChecker complianceChecker = new AnnotationChecker(resultObserver,
                    mClassProvider, mAnnotationForExactMatch, filter);

            ApiDocumentParser apiDocumentParser = new ApiDocumentParser(TAG);

            parseApiResourcesAsStream(apiDocumentParser, mExpectedApiFiles)
                    .filter(androidAutoClassesFilter())
                    .forEach(complianceChecker::checkSignatureCompliance);

            // After done parsing all expected API files, perform any deferred checks.
            complianceChecker.checkDeferred();
        });
    }
}
