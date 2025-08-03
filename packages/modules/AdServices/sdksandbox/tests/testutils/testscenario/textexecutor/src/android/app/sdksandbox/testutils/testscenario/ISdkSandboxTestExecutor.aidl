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

package android.app.sdksandbox.testutils.testscenario;

import java.util.List;
import android.os.Bundle;

import android.app.sdksandbox.testutils.testscenario.ISdkSandboxResultCallback;

interface ISdkSandboxTestExecutor {

    /*
     * This constant is used for optionally passing setup parameters
     * to the test sdk. This is useful for when a test author needs to
     * pass specific information to setup the sdk.
     * E.g. The test instance class
     */
    const String TEST_SETUP_PARAMS = "TEST_SETUP_PARAMS";

    /*
     * This constant is used for optionally loading a test author
     * binder. This is useful for when a test author wants their
     * SDK driven tests to invoke an event outside the test SDK.
     */
    const String TEST_AUTHOR_DEFINED_BINDER = "TEST_AUTHOR_DEFINED_BINDER";

    /**
     * Returns a list of method names that had an annotation
     * name.
     */
    List<String> retrieveAnnotatedMethods(String canonicalAnnotation);

    /**
     * Invokes a method from within the SDK.

     */
    void invokeMethod(String methodName, in Bundle params,
        in ISdkSandboxResultCallback callback);

    /**
     * Executed after each test finishes in assertSdkTestRunPasses.
     */
    void cleanOnTestFinish();
}
