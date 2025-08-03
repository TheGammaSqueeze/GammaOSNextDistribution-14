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
package android.signature.cts.tests;

import android.signature.cts.ApiPresenceChecker;
import android.signature.cts.ClassProvider;
import android.signature.cts.FailureType;
import android.signature.cts.JDiffClassDescription;
import android.signature.cts.ResultObserver;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test the error handling code used by the host tests.
 */
@RunWith(JUnit4.class)
public class FailureHandlingTest
        extends ApiPresenceCheckerTest<FailureHandlingTest.FakeApiPresenceChecker> {

    /**
     * A fake {@link ApiPresenceChecker} that always reports a failure and if that throws an
     * exception then reports another failure. This mirrors the behavior of the
     * {@link android.signature.cts.ApiComplianceChecker}.
     */
    public static class FakeApiPresenceChecker extends ApiPresenceChecker {

        public FakeApiPresenceChecker(ClassProvider provider, ResultObserver resultObserver) {
            super(provider, resultObserver);
        }

        @Override
        public void checkSignatureCompliance(JDiffClassDescription classDescription) {
            try {
                resultObserver.notifyFailure(FailureType.EXTRA_CLASS,
                        classDescription.getAbsoluteClassName(), "bad");
            } catch (Error | Exception e) {
                resultObserver.notifyFailure(
                        FailureType.CAUGHT_EXCEPTION,
                        classDescription.getAbsoluteClassName(),
                        "Exception while checking class compliance!",
                        e);
            }
        }
    }


    @Override
    protected FakeApiPresenceChecker createChecker(ResultObserver resultObserver,
            ClassProvider provider) {
        return new FakeApiPresenceChecker(provider, resultObserver);
    }

    @Test
    public void testNoFailures_DetectsFailures() {
        AssertionError e = Assert.assertThrows(AssertionError.class,
                () -> {
                    try (NoFailures observer = new NoFailures()) {
                        runWithApiChecker(observer, checker -> {
                            JDiffClassDescription description = createClass("fake");
                            checker.checkSignatureCompliance(description);
                        });
                    }
                });
        assertThat(e.getMessage(), startsWith("Unexpected test failure"));
    }

    @Test
    public void testExpectFailure_DetectsNoFailures() {
        AssertionError e = Assert.assertThrows(AssertionError.class,
                () -> {
                    try (ExpectFailure observer = new ExpectFailure(FailureType.MISMATCH_FIELD)) {
                        runWithApiChecker(observer, checker -> {
                            // Do nothing.
                        });
                    }
                });
        assertThat(e.getMessage(),
                equalTo("Expect one failure of type MISMATCH_FIELD but found 0 failures: []"));
    }

    @Test
    public void testExpectFailure_DetectsTooManyFailures() {
        AssertionError e = Assert.assertThrows(AssertionError.class,
                () -> {
                    try (ExpectFailure observer = new ExpectFailure(FailureType.MISMATCH_FIELD)) {
                        runWithApiChecker(observer, checker -> {
                            JDiffClassDescription description = createClass("fake");
                            checker.checkSignatureCompliance(description);
                            description = createClass("fake2");
                            checker.checkSignatureCompliance(description);
                        });
                    }
                });
        assertThat(e.getMessage(),
                startsWith("Expect one failure of type MISMATCH_FIELD but found 2 failures:"));
    }
}
