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
package android.signature.cts.api;

import android.signature.cts.ApiComplianceChecker;
import android.signature.cts.ApiDocumentParser;
import java.util.function.Supplier;

/**
 * Base class of the tests that check accessibility of API signatures at runtime.
 */
public class AbstractSignatureTest extends AbstractApiTest {

    private static final String TAG = AbstractSignatureTest.class.getSimpleName();

    /**
     * The name of the instrumentation option that contains the list of the current API signatures
     * that are expected to be accessible.
     */
    protected static final String EXPECTED_API_FILES_ARG = "expected-api-files";

    /**
     * The name of the instrumentation option that contains the list of the previous API signatures
     * that are expected to be accessible.
     */
    protected static final String PREVIOUS_API_FILES_ARG = "previous-api-files";

    /**
     * Supplier of the list of files specified in the instrumentation argument "base-api-files".
     */
    private static final Supplier<String[]> BASE_API_FILES =
            getSupplierOfAnOptionalCommaSeparatedListArgument("base-api-files");

    /**
     * Load the base API files into the supplied compliance checker.
     *
     * <p>Base API files are not checked by the compliance checker but may be extended by classes
     * which are checked.</p>
     *
     * @param complianceChecker the {@link ApiComplianceChecker} into which the base API will be
     *                          loaded.
     */
    protected void loadBaseClasses(ApiComplianceChecker complianceChecker) {
        ApiDocumentParser apiDocumentParser = new ApiDocumentParser(TAG);
        parseApiResourcesAsStream(apiDocumentParser, BASE_API_FILES.get())
                .forEach(complianceChecker::addBaseClass);
    }
}
