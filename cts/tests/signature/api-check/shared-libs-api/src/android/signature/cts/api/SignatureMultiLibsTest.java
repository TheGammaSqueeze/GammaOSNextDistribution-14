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

import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.SharedLibraryInfo;
import android.signature.cts.ApiComplianceChecker;
import android.signature.cts.ApiDocumentParser;
import android.signature.cts.JDiffClassDescription;
import android.signature.cts.VirtualPath;
import android.util.Log;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.common.base.Suppliers;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.naming.TestCaseName;
import org.junit.Assume;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

/**
 * Verifies that any shared library provided by this device and for which this test has a
 * corresponding API specific file provides the expected API.
 *
 * <pre>This test relies on the AndroidManifest.xml file for the APK in which this is run having a
 * {@code <uses-library>} entry for every shared library that provides an API that is contained
 * within the shared-libs-all.api.zip supplied to this test.
 */
@RunWith(JUnitParamsRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SignatureMultiLibsTest extends AbstractSignatureTest {

    protected static final Supplier<String[]> EXPECTED_API_FILES =
            getSupplierOfAMandatoryCommaSeparatedListArgument(EXPECTED_API_FILES_ARG);

    protected static final Supplier<String[]> PREVIOUS_API_FILES =
            getSupplierOfAMandatoryCommaSeparatedListArgument(PREVIOUS_API_FILES_ARG);

    private static final String TAG = SignatureMultiLibsTest.class.getSimpleName();

    /**
     * A memoized supplier of the list of shared libraries on the device.
     */
    protected static final Supplier<Set<String>> AVAILABLE_SHARED_LIBRARIES =
            Suppliers.memoize(SignatureMultiLibsTest::retrieveActiveSharedLibraries)::get;

    private static final String SHARED_LIBRARY_LIST_RESOURCE_NAME = "shared-libs-names.txt";

    /**
     * Retrieve the names of the shared libraries that are active on the device.
     *
     * @return The set of shared library names.
     */
    private static Set<String> retrieveActiveSharedLibraries() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();

        List<SharedLibraryInfo> sharedLibraries =
                context.getPackageManager().getSharedLibraries(0);

        Set<String> sharedLibraryNames = new TreeSet<>();
        for (SharedLibraryInfo sharedLibrary : sharedLibraries) {
            String name = sharedLibrary.getName();
            sharedLibraryNames.add(name);
            Log.d(TAG, String.format("Found library: %s%n", name));
        }

        return sharedLibraryNames;
    }

    /**
     * A memoized supplier of the list of shared libraries that this can test.
     */
    protected static final Supplier<List<String>> TESTABLE_SHARED_LIBRARIES =
            Suppliers.memoize(SignatureMultiLibsTest::retrieveTestableSharedLibraries)::get;

    /**
     * Retrieve the names of the shared libraries that are testable by this test.
     *
     * @return The set of shared library names.
     */
    private static List<String> retrieveTestableSharedLibraries() {
        ClassLoader classLoader = SignatureMultiLibsTest.class.getClassLoader();
        try (InputStream is = classLoader.getResourceAsStream(SHARED_LIBRARY_LIST_RESOURCE_NAME)) {
            if (is == null) {
                throw new RuntimeException(
                        "Resource " + SHARED_LIBRARY_LIST_RESOURCE_NAME + " could not be found");
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                return reader.lines()
                        .filter(line -> !line.isEmpty())
                        .sorted()
                        .collect(Collectors.toList());
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not retrieve testable shared libraries", e);
        }
    }

    /**
     * Convert the list of testable shared libraries into a form suitable for parameterizing a test.
     */
    public Object[][] getTestableSharedLibraryParameters() {
        List<String> libraries = TESTABLE_SHARED_LIBRARIES.get();
        Object[][] params = new Object[libraries.size()][1];
        for (int i = 0; i < libraries.size(); i++) {
            String name = libraries.get(i);
            TestableLibraryParameter param = new TestableLibraryParameter(name);
            params[i][0] = param;
        }
        return params;
    }

    /**
     * Skips the test if the supplied library is unavailable on the device.
     *
     * <p>If the library is unavailable then this throws an
     * {@link org.junit.AssumptionViolatedException}.</p>
     *
     * @param library the name of the library that needs to be available.
     */
    private void skipTestIfLibraryIsUnavailable(String library) {
        Assume.assumeTrue("Shared library " + library + " is not available on this device",
                AVAILABLE_SHARED_LIBRARIES.get().contains(library));
    }

    /**
     * Return a stream of {@link JDiffClassDescription} that are expected to be provided by the
     * shared libraries which are installed on this device.
     *
     * @param apiDocumentParser the parser to use.
     * @param apiResources the list of API resource files.
     * @param library the name of the library whose APIs should be parsed.
     * @return a stream of {@link JDiffClassDescription}.
     */
    private Stream<JDiffClassDescription> parseActiveSharedLibraryApis(
            ApiDocumentParser apiDocumentParser, String[] apiResources, String library) {

        return retrieveApiResourcesAsStream(getClass().getClassLoader(), apiResources)
                .filter(path -> {
                    String apiLibraryName = getLibraryNameFromPath(path);
                    return apiLibraryName.equals(library);
                })
                .flatMap(apiDocumentParser::parseAsStream);
    }

    /**
     * Tests that each shared library's API matches its current API.
     *
     * <p>One test per shared library, checks the entire API, and then reports the complete list of
     * failures.</p>
     */
    @Test
    // Parameterize this method with the set of testable shared libraries.
    @Parameters(method = "getTestableSharedLibraryParameters")
    // The test name is the method name followed by and _ and the shared library name, with .s
    // replaced with _. e.g. testRuntimeCompatibilityWithCurrentApi_android_test_base.
    @TestCaseName("{method}_{0}")
    public void testRuntimeCompatibilityWithCurrentApi(TestableLibraryParameter parameter) {
        String library = parameter.getName();
        skipTestIfLibraryIsUnavailable(library);
        runWithTestResultObserver(mResultObserver -> {
            ApiComplianceChecker complianceChecker =
                    new ApiComplianceChecker(mResultObserver, mClassProvider);

            // Load classes from any API files that form the base which the expected APIs extend.
            loadBaseClasses(complianceChecker);

            ApiDocumentParser apiDocumentParser = new ApiDocumentParser(TAG);

            parseActiveSharedLibraryApis(apiDocumentParser, EXPECTED_API_FILES.get(), library)
                    .forEach(complianceChecker::checkSignatureCompliance);

            // After done parsing all expected API files, perform any deferred checks.
            complianceChecker.checkDeferred();
        });
    }

    /**
     * Tests that each shared library's API matches its previous APIs.
     *
     * <p>One test per shared library, checks the entire API, and then reports the complete list of
     * failures.</p>
     */
    @Test
    // Parameterize this method with the set of testable shared libraries.
    @Parameters(method = "getTestableSharedLibraryParameters")
    // The test name is the method name followed by and _ and the shared library name, with .s
    // replaced with _. e.g. testRuntimeCompatibilityWithPreviousApis_android_test_base.
    @TestCaseName("{method}_{0}")
    public void testRuntimeCompatibilityWithPreviousApis(TestableLibraryParameter parameter) {
        String library = parameter.getName();
        skipTestIfLibraryIsUnavailable(library);
        runWithTestResultObserver(mResultObserver -> {
            ApiComplianceChecker complianceChecker =
                    new ApiComplianceChecker(mResultObserver, mClassProvider);

            ApiDocumentParser apiDocumentParser = new ApiDocumentParser(TAG);

            parseActiveSharedLibraryApis(apiDocumentParser, PREVIOUS_API_FILES.get(), library)
                    .map(clazz -> clazz.setPreviousApiFlag(true))
                    .forEach(complianceChecker::checkSignatureCompliance);

            // After done parsing all expected API files, perform any deferred checks.
            complianceChecker.checkDeferred();
        });
    }

    /**
     * Get the library name from the API file's path.
     *
     * @param path the path of the API file.
     * @return the library name for the API file.
     */
    private String getLibraryNameFromPath(VirtualPath path) {
        String name = path.toString();
        return name.substring(name.lastIndexOf('/') + 1).split("-")[0];
    }

    /**
     * A wrapper around a shared library name to ensure that its string representation is suitable
     * for use in a parameterized test name, i.e. does not contain any characters that are not
     * allowed in a test name by CTS/AndroidJUnitRunner.
     */
    public static class TestableLibraryParameter {
        private final String name;
        private final String parameter;

        public TestableLibraryParameter(String name) {
            this.name = name;
            this.parameter = name.replace('.', '_');
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return parameter;
        }
    }
}
