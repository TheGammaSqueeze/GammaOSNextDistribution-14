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

package android.car.test;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import android.annotation.Nullable;
import android.car.Car;
import android.car.CarVersion;
import android.car.PlatformVersion;
import android.car.PlatformVersionMismatchException;
import android.car.annotation.AddedInOrBefore;
import android.car.annotation.ApiRequirements;
import android.car.test.ApiCheckerRule.UnsupportedVersionTest.Behavior;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.NonApiTest;

import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Rule used to validate Car API requirements on CTS tests.
 *
 * <p>This rule is used to verify that all tests in a class:
 *
 * <ol>
 *   <li>Indicate which API / CDD is being tested.
 *   <li>Properly behave on supported and unsupported versions.
 * </ol>
 *
 * <p>For the former, the test must be annotated with either {@link ApiTest} or {@link CddTest} (in
 * which case it also needs to be annotated with {@link ApiRequirements}, otherwise the test will
 * fail (unless the rule was created with {@link Builder#disableAnnotationsCheck()}. In the case
 * of {@link ApiTest}, the rule will also assert that the underlying APIs are annotated with either
 * {@link ApiRequirements} or {@link AddedInOrBefore}.
 *
 * <p><b>Note:</b> Usually, all CTS tests should be testing public or system APIs or CDD
 * requirements. However, in the case that they don't (especially in {@code AndroidCarApiTest}),
 * they should be annotated with {@link NonApiTest}. This usage should also be justified.
 *
 * <p>For the latter, if the API declares {@link ApiRequirements}, the rule by default will make
 * sure the test behaves properly in the supported and unsupported platform versions:
 * <ol>
 *   <li>If the platform is supported, the test should pass as usual.
 *   <li>If the platform is not supported, the rule will assert that the test throws a
 *   {@link PlatformVersionMismatchException}.
 * </ol>
 *
 * <p>There are corner cases where the default rule behavior cannot be applied for the test, like:
 * <ol>
 *   <li>The test logic is too complex (or takes time) and should be simplified when running on
 *       unsupported versions.
 *   <li>The API being tested should behave differently on supported or unsupported versions.
 * </ol>
 *
 * <p>In these cases, the test should be split in 2 tests, one for the supported version and another
 * for the unsupported version, and annotated with {@link SupportedVersionTest} or
 * {@link UnsupportedVersionTest} respectively; these tests <b>MUST</b> be provided in pair (in
 * fact, these annotations take an argument pointing to the pair) and they will behave this way:
 *
 * <ol>
 *   <li>{@link SupportedVersionTest}: should pass on supported platform and will be ignored on
 *       unsupported platforms (by throwing an {@link ExpectedVersionAssumptionViolationException}).
 *   <li>{@link UnsupportedVersionTest}: by default, it will be ignored on supported platforms
 *       (by throwing an {@link ExpectedVersionAssumptionViolationException}), but can be changed
 *       to run on unsupported platforms as well (by setting its
 *       {@link UnsupportedVersionTest#behavior()} to {@link Behavior#EXPECT_PASS}.
 * </ol>
 *
 * <p>So, back to the examples above, the tests would be:
 * <pre><code>

  @Test
  @ApiTest(apis = {"com.acme.Car#foo"})
  @SupportedVersionTest(unsupportedVersionTest="testFoo_unsupported")
  public void testFoo_supported() {
     baz(); // takes a long time
     foo();
  }

  @Test
  @ApiTest(apis = {"com.acme.Car#foo"})
  @UnsupportedVersionTest(supportedVersionTest="testFoo_supported")
  public void testFoo_unsupported() {
     foo(); // should throw PlatformViolationException
  }

  @Test
  @ApiTest(apis = {"com.acme.Car#bar"})
  @SupportedVersionTest(unsupportedVersionTest="testBar_unsupported")
  public void testBar_supported() {
     assertWithMessage("bar()").that(bar()).isEqualTo("BehaviorOnSupportedPlatform");
  }

  @Test
  @ApiTest(apis = {"com.acme.Car#bar"})
  @UnsupportedVersionTest(supportedVersionTest="testBar_supported", behavior=EXPECT_PASS)
  public void testFoo_unsupported() {
     assertWithMessage("bar()").that(bar()).isEqualTo("BehaviorOnUnsupportedPlatform");
  }

 * </code></pre>
 *
 * For nested classes the following annotation should be used for methods:
 * <pre><code>
 @Test
 @ApiTest(apis = {"com.acme.Car$Inner#methodName"})
 public void testMethodName() {}

 * </code></pre>
 * For nested classes the following annotation should be used for fields:
 * <pre><code>
 @Test
 @ApiTest(apis = {"com.acme.Car.Inner#fieldName"})
 public void testFieldName() {}

 * </code></pre>
 */
public final class ApiCheckerRule implements TestRule {

    public static final String TAG = ApiCheckerRule.class.getSimpleName();

    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    private final boolean mEnforceTestApiAnnotations;
    private final boolean mEnforceApiRequirements;

    @Nullable
    private String mTestMethodName;

    /**
     * Builder.
     */
    public static final class Builder {
        private boolean mEnforceTestApiAnnotations = true;
        private boolean mEnforceApiRequirements = true;

        /**
         * Creates a new rule.
         */
        public ApiCheckerRule build() {
            return new ApiCheckerRule(this);
        }

        /**
         * Don't fail the test if the required annotations (like {@link ApiTest}) are missing.
         */
        public Builder disableAnnotationsCheck() {
            mEnforceTestApiAnnotations = false;
            return this;
        }

        /**
         * Don't fail the test if it could not infer its {@link ApiRequirements}.
         *
         * <p>Typically used on tests for built-in APIs.
         */
        public Builder disableApiRequirementsCheck() {
            mEnforceApiRequirements = false;
            return this;
        }
    }

    private ApiCheckerRule(Builder builder) {
        mEnforceTestApiAnnotations = builder.mEnforceTestApiAnnotations;
        mEnforceApiRequirements = mEnforceTestApiAnnotations
                ? builder.mEnforceApiRequirements
                : false;
    }

    /**
     * Checks whether the test is running in an environment that supports the given API.
     *
     * @param api API as defined by {@link ApiTest}.
     * @return whether the test is running in an environment that supports the
     * {@link ApiRequirements} defined in such API.
     */
    public boolean isApiSupported(String api) {
        ApiRequirements apiRequirements = getApiRequirements(api);

        if (apiRequirements == null) {
            throw new IllegalArgumentException("No @ApiRequirements on " + api);
        }

        return isSupported(apiRequirements);
    }

    /**
     * Gets the name of the test being executed.
     */
    @Nullable
    public String getTestMethodName() {
        return mTestMethodName;
    }

    private boolean isSupported(ApiRequirements apiRequirements) {
        PlatformVersion platformVersion = Car.getPlatformVersion();
        boolean isSupported = platformVersion
                .isAtLeast(apiRequirements.minPlatformVersion().get());
        if (DBG) {
            Log.d(TAG, "isSupported(" + apiRequirements + "): platformVersion=" + platformVersion
                    + ",supported=" + isSupported);
        }
        return isSupported;
    }

    private static ApiRequirements getApiRequirements(String api) {
        Member member = ApiHelper.resolve(api);
        if (member == null) {
            throw new IllegalArgumentException("API not found: " + api);
        }
        return getApiRequirements(member);
    }

    private static ApiRequirements getApiRequirements(Member member) {
        return getAnnotation(ApiRequirements.class, member);
    }

    @SuppressWarnings("deprecation")
    private static AddedInOrBefore getAddedInOrBefore(Member member) {
        return getAnnotation(AddedInOrBefore.class, member);
    }

    private static <T extends Annotation> T getAnnotation(Class<T> annotationClass, Member member) {
        if (member instanceof Field) {
            return ((Field) member).getAnnotation(annotationClass);
        }
        if (member instanceof Method) {
            return ((Method) member).getAnnotation(annotationClass);
        }
        throw new UnsupportedOperationException("Invalid member type for API: " + member);
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                base.evaluate();
            }
        };
    }

    // TODO(b/285930588):ApiCheckerRule is no longer required. But the code can be useful,
    // Currently disabling the rule. As part of the bug, more investigation is required how to
    // clean up API Checker Rule.
    public Statement applyOld(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                mTestMethodName = description.getMethodName();
                try {
                    evaluateInternal();
                } finally {
                    mTestMethodName = null;
                }
            }

            private void evaluateInternal() throws Throwable {
                if (DBG) {
                    Log.d(TAG, "evaluating " + description.getDisplayName());
                }

                // Need to do a basic version check first, as the rule could be used on ATS tests
                // running on pre-mainline versions
                if (!isPlatformSupported(description)) {
                    base.evaluate();
                    return;
                }

                // Variables below are used to validate that all ApiRequirements are compatible
                ApiTest apiTest = null;
                NonApiTest nonApiTest = null;
                ApiRequirements apiRequirementsOnApiUnderTest = null;
                IgnoreInvalidApi ignoreInvalidApi = null;

                // Optional annotations that change the behavior of the rule
                SupportedVersionTest supportedVersionTest = null;
                UnsupportedVersionTest unsupportedVersionTest = null;

                // Other relevant annotations
                @SuppressWarnings("deprecation")
                AddedInOrBefore addedInOrBefore = null;
                CddTest cddTest = null;
                ApiRequirements apiRequirementsOnTest = null; // user only with CddTest
                ApiRequirements effectiveApiRequirementsOnTest = null;

                for (Annotation annotation : description.getAnnotations()) {
                    if (DBG) {
                        Log.d(TAG, "Annotation: " + annotation);
                    }
                    if (annotation instanceof ApiTest) {
                        apiTest = (ApiTest) annotation;
                        continue;
                    }
                    if (annotation instanceof  NonApiTest) {
                        nonApiTest = (NonApiTest) annotation;
                        continue;
                    }
                    if (annotation instanceof ApiRequirements) {
                        apiRequirementsOnTest = (ApiRequirements) annotation;
                        continue;
                    }
                    if (annotation instanceof CddTest) {
                        cddTest = (CddTest) annotation;
                        continue;
                    }
                    if (annotation instanceof SupportedVersionTest) {
                        supportedVersionTest = (SupportedVersionTest) annotation;
                        continue;
                    }
                    if (annotation instanceof UnsupportedVersionTest) {
                        unsupportedVersionTest = (UnsupportedVersionTest) annotation;
                        continue;
                    }
                    if (annotation instanceof IgnoreInvalidApi) {
                        ignoreInvalidApi = (IgnoreInvalidApi) annotation;
                        continue;
                    }
                }

                if (DBG) {
                    Log.d(TAG, "Relevant annotations on test: "
                            + "ApiTest=" + apiTest
                            + " CddTest=" + cddTest
                            + " NonApiTest= " + nonApiTest
                            + " ApiRequirements=" + apiRequirementsOnTest
                            + " SupportedVersionTest=" + supportedVersionTest
                            + " UnsupportedVersionTest=" + unsupportedVersionTest
                            + " IgnoreInvalidApi=" + ignoreInvalidApi);
                }

                validateOptionalAnnotations(description.getTestClass(), description.getMethodName(),
                        supportedVersionTest, unsupportedVersionTest);

                if (apiTest == null && (cddTest != null || nonApiTest != null)) {
                    validateNonApiAnnotations(cddTest, nonApiTest, apiRequirementsOnTest);
                    effectiveApiRequirementsOnTest = apiRequirementsOnTest;
                }

                if (apiTest == null && cddTest == null && nonApiTest == null) {
                    if (mEnforceTestApiAnnotations) {
                        throw new IllegalArgumentException(
                                "Test is missing @ApiTest, @NonApiTest, or @CddTest annotation");
                    } else {
                        Log.w(TAG, "Test " + description
                                + " doesn't have @ApiTest, @NonApiTest, or @CddTest,"
                                + "but rule is not enforcing it");
                    }
                }

                if (apiTest != null) {
                    Pair<ApiRequirements, AddedInOrBefore> pair = getApiRequirementsFromApis(
                            description, apiTest, ignoreInvalidApi);
                    apiRequirementsOnApiUnderTest = pair.first;
                    if (effectiveApiRequirementsOnTest == null) {
                        // not set by CddTest
                        effectiveApiRequirementsOnTest = apiRequirementsOnApiUnderTest;
                    }
                    if (effectiveApiRequirementsOnTest == null && ignoreInvalidApi != null) {
                        effectiveApiRequirementsOnTest = apiRequirementsOnTest;
                    }
                    addedInOrBefore = pair.second;
                }

                if (DBG) {
                    Log.d(TAG, "Relevant annotations on APIs: "
                            + "ApiRequirements=" + apiRequirementsOnApiUnderTest
                            + ", AddedInOrBefore: " + addedInOrBefore);
                }

                if (apiRequirementsOnApiUnderTest != null && apiRequirementsOnTest != null) {
                    throw new IllegalArgumentException("Test cannot be annotated with both "
                            + "@ApiTest and @ApiRequirements");
                }

                if (effectiveApiRequirementsOnTest == null) {
                    if (ignoreInvalidApi != null) {
                        if (mEnforceTestApiAnnotations) {
                            throw new IllegalArgumentException("Test contains @IgnoreInvalidApi but"
                                    + " is missing @ApiRequirements");
                        } else {
                            Log.w(TAG, "Test " + description + " contains @IgnoreInvalidApi and is "
                                    + "missing @ApiRequirements, but rule is not enforcing them");
                        }
                    } else if (addedInOrBefore == null) {
                        if (mEnforceApiRequirements) {
                            throw new IllegalArgumentException("Missing @ApiRequirements "
                                    + "or @AddedInOrBefore");
                        } else {
                            Log.w(TAG, "Test " + description + " doesn't have required "
                                    + "@ApiRequirements or @AddedInOrBefore but rule is not "
                                    + "enforcing them");
                        }
                    }
                    base.evaluate();
                    return;
                }

                // Finally, run the test and assert results depending on whether it's supported or
                // not
                apply(base, description, effectiveApiRequirementsOnTest, supportedVersionTest,
                        unsupportedVersionTest);
            }
        };
    } // apply

    protected boolean isPlatformSupported(Description description) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, "Running " + description.getDisplayName() + " as-is on pre-TM platform build"
                    + " (" + Build.VERSION.SDK_INT + ")");
            return false;
        }
        if (!Car.isApiVersionAtLeast(Build.VERSION_CODES.TIRAMISU, /* minor= */ 1)) {
            Log.d(TAG, "Running " + description.getDisplayName() + " as-is on pre-TM-QPR1 Car build"
                    + " (major=" + Car.API_VERSION_MAJOR_INT
                    + ", minor=" + Car.API_VERSION_MINOR_INT + ")");
            return false;
        }
        return true;
    }

    private void validateNonApiAnnotations(CddTest cddTest, NonApiTest nonApiTest,
            ApiRequirements apiRequirements) {
        if (cddTest != null && nonApiTest != null) {
            throw new IllegalArgumentException("Test contains both " + nonApiTest.annotationType()
                    + " annotation (" + nonApiTest + ") and " + cddTest.annotationType()
                    + " annotation (" + cddTest + ")");
        }

        if (cddTest != null) {
            validateCddTestAnnotation(cddTest, apiRequirements);
            return;
        }

        if (nonApiTest != null) {
            validateNonApiTestAnnotation(nonApiTest, apiRequirements);
        }
    }

    private void validateCddTestAnnotation(CddTest cddTest, ApiRequirements apiRequirements) {
        @SuppressWarnings("deprecation")
        String deprecatedRequirement = cddTest.requirement();

        if (!TextUtils.isEmpty(deprecatedRequirement)) {
            throw new IllegalArgumentException("Test contains " + cddTest.annotationType()
                    + " annotation (" + cddTest + "), but it's using the"
                    + " deprecated 'requirement' field (value=" + deprecatedRequirement + "); it "
                    + "should use 'requirements' instead");
        }

        String[] requirements = cddTest.requirements();

        if (requirements == null || requirements.length == 0) {
            throw new IllegalArgumentException("Test contains " + cddTest.annotationType()
                    + " annotation (" + cddTest
                    + "), but it's 'requirements' field is empty (value="
                    + Arrays.toString(requirements) + ")");
        }
        for (String requirement : requirements) {
            String trimmedRequirement = requirement == null ? "" : requirement.trim();
            if (TextUtils.isEmpty(trimmedRequirement)) {
                throw new IllegalArgumentException("Test contains " + cddTest.annotationType()
                        + " annotation (" + cddTest + "), but it contains an empty requirement"
                        + "(requirements=" + Arrays.toString(requirements) + ")");
            }
        }

        // CddTest itself is valid, must have ApiRequirements
        if (apiRequirements == null) {
            throw new IllegalArgumentException("Test contains " + cddTest.annotationType()
                    + " annotation (" + cddTest + "), but it's missing @ApiRequirements)");
        }
    }

    public void validateNonApiTestAnnotation(NonApiTest nonApiTest,
            ApiRequirements apiRequirements) {
        if (apiRequirements == null) {
            throw new IllegalArgumentException("Test contains " + nonApiTest.annotationType()
                    + " annotation (" + nonApiTest + "), but it's missing @ApiRequirements)");
        }
    }

    @SuppressWarnings("deprecation")
    private Pair<ApiRequirements, AddedInOrBefore> getApiRequirementsFromApis(
            Description description, ApiTest apiTest, @Nullable IgnoreInvalidApi ignoreInvalidApi) {
        ApiRequirements firstApiRequirements = null;
        AddedInOrBefore addedInOrBefore = null;
        List<String> allApis = new ArrayList<>();
        List<ApiRequirements> allApiRequirements = new ArrayList<>();
        boolean compatibleApis = true;

        String[] apis = apiTest.apis();
        if (apis == null || apis.length == 0) {
            throw new IllegalArgumentException("empty @ApiTest annotation");
        }
        List<String> invalidApis = new ArrayList<>();
        for (String api : apis) {
            allApis.add(api);
            Member member = ApiHelper.resolve(api);
            if (member == null) {
                invalidApis.add(api);
                continue;
            }
            ApiRequirements apiRequirements = getApiRequirements(member);
            if (apiRequirements == null && addedInOrBefore == null) {
                addedInOrBefore = getAddedInOrBefore(member);
                if (DBG) {
                    Log.d(TAG, "No @ApiRequirements on " + api + "; trying "
                            + "@AddedInOrBefore instead: " + addedInOrBefore);
                }
                continue;
            }

            if (apiRequirements == null) {
                continue;
            }
            allApiRequirements.add(apiRequirements);
            if (firstApiRequirements == null) {
                firstApiRequirements = apiRequirements;
                continue;
            }
            // Make sure all ApiRequirements are compatible
            if (!apiRequirements.minCarVersion()
                    .equals(firstApiRequirements.minCarVersion())
                    || !apiRequirements.minPlatformVersion()
                            .equals(firstApiRequirements.minPlatformVersion())) {
                Log.w(TAG, "Found incompatible API requirement (" + apiRequirements
                        + ") on " + api + "(first ApiRequirements is "
                        + firstApiRequirements + ")");
                compatibleApis = false;
            } else {
                Log.d(TAG, "Multiple @ApiRequirements found but they're compatible");
            }
        }
        if (!invalidApis.isEmpty()) {
            if (ignoreInvalidApi != null) {
                Log.i(TAG, "Could not resolve some APIs (" + invalidApis + ") on annotation ("
                        + apiTest + "), but letting it go due to " + ignoreInvalidApi);
            } else {
                throw new IllegalArgumentException("Could not resolve some APIs ("
                        + invalidApis + ") on annotation (" + apiTest + ")");
            }
        } else if (!compatibleApis) {
            throw new IncompatibleApiRequirementsException(allApis, allApiRequirements);
        }
        return new Pair<>(firstApiRequirements, addedInOrBefore);
    }

    private void validateOptionalAnnotations(Class<?> testClass, String testMethodName,
            @Nullable SupportedVersionTest supportedVersionAnnotationOnTestMethod,
            @Nullable UnsupportedVersionTest unsupportedVersionAnnotationOnTestMethod) {
        if (unsupportedVersionAnnotationOnTestMethod != null
                && supportedVersionAnnotationOnTestMethod != null) {
            throw new IllegalArgumentException("test must be annotated with either "
                        + "supportedVersionTest or unsupportedVersionTest, not both");
        }
        if (unsupportedVersionAnnotationOnTestMethod != null) {
            validateUnsupportedVersionTest(testClass, testMethodName,
                    unsupportedVersionAnnotationOnTestMethod);
            return;
        }
        if (supportedVersionAnnotationOnTestMethod != null) {
            validateSupportedVersionTest(testClass, testMethodName,
                    supportedVersionAnnotationOnTestMethod);
            return;
        }
    }

    private void validateUnsupportedVersionTest(Class<?> testClass, String testMethodName,
            @Nullable UnsupportedVersionTest unsupportedVersionAnnotationOnTestMethod) {
        // Test class must have a counterpart supportedVersionTest
        String supportedVersionMethodName = unsupportedVersionAnnotationOnTestMethod
                .supportedVersionTest();
        if (TextUtils.isEmpty(supportedVersionMethodName)) {
            throw new IllegalArgumentException("missing supportedVersionTest on "
                    + unsupportedVersionAnnotationOnTestMethod);
        }

        Method supportedVersionMethod = null;
        Class<?>[] noParams = {};
        try {
            supportedVersionMethod = testClass.getDeclaredMethod(supportedVersionMethodName,
                    noParams);
        } catch (Exception e) {
            Log.w(TAG, "Error getting method named " + supportedVersionMethodName
                    + " on class " + testClass, e);
            throw new IllegalArgumentException("invalid supportedVersionTest on "
                    + unsupportedVersionAnnotationOnTestMethod + ": " + e);
        }
        // And it must be annotated with @SupportedVersionTest
        SupportedVersionTest supportedVersionAnnotationOnUnsupportedMethod =
                supportedVersionMethod.getAnnotation(SupportedVersionTest.class);
        if (supportedVersionAnnotationOnUnsupportedMethod == null) {
            throw new IllegalArgumentException(
                    "invalid supportedVersionTest method (" + supportedVersionMethodName
                    + " on " + unsupportedVersionAnnotationOnTestMethod
                    + ": it's not annotated with @SupportedVersionTest");
        }

        // which in turn must point to the UnsupportedVersionTest itself
        String unsupportedVersionMethodOnSupportedAnnotation =
                supportedVersionAnnotationOnUnsupportedMethod.unsupportedVersionTest();
        if (!testMethodName.equals(unsupportedVersionMethodOnSupportedAnnotation)) {
            throw new IllegalArgumentException(
                    "invalid unsupportedVersionTest on "
                            + supportedVersionAnnotationOnUnsupportedMethod
                            + " annotation on method " + supportedVersionMethodName
                            + ": it should be " + testMethodName);
        }
    }

    private void validateSupportedVersionTest(Class<?> testClass, String testMethodName,
            @Nullable SupportedVersionTest supportedVersionAnnotationOnTestMethod) {
        // Test class must have a counterpart unsupportedVersionTest
        String unsupportedVersionMethodName = supportedVersionAnnotationOnTestMethod
                .unsupportedVersionTest();
        if (TextUtils.isEmpty(unsupportedVersionMethodName)) {
            throw new IllegalArgumentException("missing unsupportedVersionTest on "
                    + supportedVersionAnnotationOnTestMethod);
        }

        Method unsupportedVersionMethod = null;
        Class<?>[] noParams = {};
        try {
            unsupportedVersionMethod = testClass.getDeclaredMethod(unsupportedVersionMethodName,
                    noParams);
        } catch (Exception e) {
            Log.w(TAG, "Error getting method named " + unsupportedVersionMethodName
                    + " on class " + testClass, e);
            throw new IllegalArgumentException("invalid supportedVersionTest on "
                    + supportedVersionAnnotationOnTestMethod + ": " + e);
        }
        // And it must be annotated with @UnupportedVersionTest
        UnsupportedVersionTest unsupportedVersionAnnotationOnUnsupportedMethod =
                unsupportedVersionMethod.getAnnotation(UnsupportedVersionTest.class);
        if (unsupportedVersionAnnotationOnUnsupportedMethod == null) {
            throw new IllegalArgumentException(
                    "invalid supportedVersionTest method (" + unsupportedVersionMethodName
                    + " on " + supportedVersionAnnotationOnTestMethod
                    + ": it's not annotated with @UnsupportedVersionTest");
        }

        // which in turn must point to the UnsupportedVersionTest itself
        String supportedVersionMethodOnSupportedAnnotation =
                unsupportedVersionAnnotationOnUnsupportedMethod.supportedVersionTest();
        if (!testMethodName.equals(supportedVersionMethodOnSupportedAnnotation)) {
            throw new IllegalArgumentException(
                    "invalid supportedVersionTest on "
                            + unsupportedVersionAnnotationOnUnsupportedMethod
                            + " annotation on method " + unsupportedVersionMethodName
                            + ": it should be " + testMethodName);
        }
    }

    private void apply(Statement base, Description description,
            @Nullable ApiRequirements apiRequirements,
            @Nullable SupportedVersionTest supportedVersionTest,
            @Nullable UnsupportedVersionTest unsupportedVersionTest)
            throws Throwable {
        if (DBG) {
            Log.d(TAG, "Applying rule using ApiRequirements=" + apiRequirements);
        }
        if (apiRequirements == null) {
            Log.w(TAG, "No @ApiRequirements on " + description.getDisplayName()
                    + " (most likely it's annotated with @AddedInOrBefore), running it always");
            base.evaluate();
            return;
        }
        if (isSupported(apiRequirements)) {
            applyOnSupportedVersion(base, description, apiRequirements, unsupportedVersionTest);
            return;
        }

        applyOnUnsupportedVersion(base, description, apiRequirements, supportedVersionTest,
                unsupportedVersionTest);
    }

    private void applyOnSupportedVersion(Statement base, Description description,
            ApiRequirements apiRequirements,
            @Nullable UnsupportedVersionTest unsupportedVersionTest)
            throws Throwable {
        if (unsupportedVersionTest == null) {
            if (DBG) {
                Log.d(TAG, "Car / Platform combo is supported, running "
                        + description.getDisplayName());
            }
            base.evaluate();
            return;
        }

        Log.i(TAG, "Car / Platform combo IS supported, but ignoring "
                + description.getDisplayName() + " because it's annotated with "
                + unsupportedVersionTest);

        throw new ExpectedVersionAssumptionViolationException(unsupportedVersionTest,
                Car.getCarVersion(), Car.getPlatformVersion(), apiRequirements);
    }

    private void applyOnUnsupportedVersion(Statement base, Description description,
            ApiRequirements apiRequirements,  @Nullable SupportedVersionTest supportedVersionTest,
            @Nullable UnsupportedVersionTest unsupportedVersionTest)
            throws Throwable {
        Behavior behavior = unsupportedVersionTest == null ? null
                : unsupportedVersionTest.behavior();
        if (supportedVersionTest == null && !Behavior.EXPECT_PASS.equals(behavior)) {
            Log.i(TAG, "Car / Platform combo is NOT supported, running "
                    + description.getDisplayName() + " but expecting "
                          + "PlatformVersionMismatchException");
            try {
                base.evaluate();
                throw new PlatformVersionMismatchExceptionNotThrownException(
                        Car.getCarVersion(), Car.getPlatformVersion(), apiRequirements);
            } catch (PlatformVersionMismatchException e) {
                if (DBG) {
                    Log.d(TAG, "Exception thrown as expected: " + e);
                }
            }
            return;
        }

        if (supportedVersionTest != null) {
            Log.i(TAG, "Car / Platform combo is NOT supported, but ignoring "
                    + description.getDisplayName() + " because it's annotated with "
                    + supportedVersionTest);

            throw new ExpectedVersionAssumptionViolationException(supportedVersionTest,
                    Car.getCarVersion(), Car.getPlatformVersion(), apiRequirements);
        }

        // At this point, it's annotated with RUN_ALWAYS
        Log.i(TAG, "Car / Platform combo is NOT supported but running anyways becaucase test is"
                + " annotated with " + unsupportedVersionTest);
        base.evaluate();
    }

    /**
     * Defines the behavior of a test when it's run in an unsupported device (when it's run in a
     * supported device, the rule will throw a {@link ExpectedVersionAssumptionViolationException}
     * exception).
     *
     * <p>Without this annotation, a test is expected to throw a
     * {@link PlatformVersionMismatchException} when running in an unsupported version.
     *
     * <p><b>Note: </b>a test annotated with this annotation <b>MUST</b> have a counterpart test
     * annotated with {@link SupportedVersionTest}.
     */
    @Retention(RUNTIME)
    @Target({TYPE, METHOD})
    public @interface UnsupportedVersionTest {

        /**
         * Name of the counterpart test should be run on supported versions; such test must be
         * annoted with {@link SupportedVersionTest}, whith its {@code unsupportedVersionTest}
         * value point to the test being annotated with this annotation.
         */
        String supportedVersionTest();

        /**
         * Behavior of the test when it's run on unsupported versions.
         */
        Behavior behavior() default Behavior.EXPECT_THROWS_VERSION_MISMATCH_EXCEPTION;

        @SuppressWarnings("Enum")
        enum Behavior {
            /**
             * Rule will run the test and assert it throws a
             * {@link PlatformVersionMismatchException}.
             */
            EXPECT_THROWS_VERSION_MISMATCH_EXCEPTION,

            /** Rule will run the test and assume it will pass.*/
            EXPECT_PASS
        }
    }

    /**
     * Defines a test to be a counterpart of a test annotated with {@link UnsupportedVersionTest}.
     *
     * <p>Such test will be run as usual on supported devices, but will throw a
     * {@link ExpectedVersionAssumptionViolationException} when running on unsupported devices.
     *
     */
    @Retention(RUNTIME)
    @Target({TYPE, METHOD})
    public @interface SupportedVersionTest {

        /**
         * Name of the counterpart test should be run on unsupported versions; such test must be
         * annoted with {@link UnsupportedVersionTest}, whith its {@code supportedVersionTest}
         * value point to the test being annotated with this annotation.
         */
        String unsupportedVersionTest();

    }

    /***
     * Tells the rule to ignore an invalid API passed to {@link ApiTest}.
     *
     * <p>Should be used in cases where the API is being indirectly tested (for example, through a
     * shell command) and hence is not available in the test's classpath.
     *
     * <p>Should be used in conjunction with {@link ApiRequirements}.
     *
     */
    @Retention(RUNTIME)
    @Target({TYPE, METHOD})
    public @interface IgnoreInvalidApi {

        /**
         * Reason why the invalid API should be ignored.
         */
        String reason();
    }

    public static final class ExpectedVersionAssumptionViolationException
            extends AssumptionViolatedException {

        private static final long serialVersionUID = 1L;

        private final CarVersion mCarVersion;
        private final PlatformVersion mPlatformVersion;
        private final ApiRequirements mApiRequirements;

        ExpectedVersionAssumptionViolationException(Annotation annotation, CarVersion carVersion,
                PlatformVersion platformVersion, ApiRequirements apiRequirements) {
            super("Test annotated with @" + annotation.annotationType().getCanonicalName()
                    + " when running on unsupported platform: CarVersion=" + carVersion
                    + ", PlatformVersion=" + platformVersion
                    + ", ApiRequirements=" + apiRequirements);

            mCarVersion = carVersion;
            mPlatformVersion = platformVersion;
            mApiRequirements = apiRequirements;
        }

        public CarVersion getCarVersion() {
            return mCarVersion;
        }

        public PlatformVersion getPlatformVersion() {
            return mPlatformVersion;
        }

        public ApiRequirements getApiRequirements() {
            return mApiRequirements;
        }
    }

    public static final class PlatformVersionMismatchExceptionNotThrownException
            extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        private final CarVersion mCarVersion;
        private final PlatformVersion mPlatformVersion;
        private final ApiRequirements mApiRequirements;

        PlatformVersionMismatchExceptionNotThrownException(CarVersion carVersion,
                PlatformVersion platformVersion, ApiRequirements apiRequirements) {
            super("Test should throw " + PlatformVersionMismatchException.class.getSimpleName()
                    + " when running on unsupported platform: CarVersion=" + carVersion
                    + ", PlatformVersion=" + platformVersion
                    + ", ApiRequirements=" + apiRequirements);

            mCarVersion = carVersion;
            mPlatformVersion = platformVersion;
            mApiRequirements = apiRequirements;
        }

        public CarVersion getCarVersion() {
            return mCarVersion;
        }

        public PlatformVersion getPlatformVersion() {
            return mPlatformVersion;
        }

        public ApiRequirements getApiRequirements() {
            return mApiRequirements;
        }
    }

    public static final class IncompatibleApiRequirementsException
            extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        private final List<String> mApis;
        private final List<ApiRequirements> mApiRequirements;

        IncompatibleApiRequirementsException(List<String> apis,
                List<ApiRequirements> apiRequirements) {
            super("Incompatible API requirements (apis=" + apis + ", apiRequirements="
                    + apiRequirements + ") on test, consider splitting it into multiple methods");

            mApis = apis;
            mApiRequirements = apiRequirements;
        }

        public List<String> getApis() {
            return mApis;
        }

        public List<ApiRequirements> getApiRequirements() {
            return mApiRequirements;
        }
    }
}
