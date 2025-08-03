/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.idea.validator;

import com.android.ide.common.rendering.api.RenderSession;
import com.android.layoutlib.bridge.android.RenderTestBase;
import com.android.layoutlib.bridge.intensive.LayoutLibTestCallback;
import com.android.layoutlib.bridge.intensive.setup.ConfigGenerator;
import com.android.layoutlib.bridge.intensive.setup.LayoutPullParser;
import com.android.layoutlib.bridge.intensive.util.SessionParamsBuilder;
import com.android.tools.idea.validator.ValidatorData.Issue;
import com.android.tools.idea.validator.ValidatorData.Level;
import com.android.tools.idea.validator.ValidatorData.Policy;
import com.android.tools.idea.validator.ValidatorData.Type;

import org.junit.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import com.google.android.apps.common.testing.accessibility.framework.uielement.DefaultCustomViewBuilderAndroid;
import com.google.android.apps.common.testing.accessibility.framework.uielement.ViewHierarchyElementAndroid;

import static com.android.tools.idea.validator.ValidatorUtil.filter;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Sanity check for a11y checks. For now it lacks checking the following:
 * - ClassNameCheck
 * - ClickableSpanCheck
 * - EditableContentDescCheck
 * - LinkPurposeUnclearCheck
 * As these require more complex UI for testing.
 *
 * It's also missing:
 * - TraversalOrderCheck
 * Because in Layoutlib test env, traversalBefore/after attributes seems to be lost. Tested on
 * studio and it seems to work ok.
 */
public class AccessibilityValidatorTests extends RenderTestBase {

    @Test
    public void testPaused() throws Exception {
        try {
            LayoutValidator.setPaused(true);
            render("a11y_test_dup_clickable_bounds.xml", session -> {
                ValidatorResult result = getRenderResult(session);
                List<Issue> dupBounds = filter(result.getIssues(), "DuplicateClickableBoundsCheck");

                /**
                 * Expects no errors since disabled. When enabled it should print
                 * the same result as {@link #testDuplicateClickableBoundsCheck}
                 */
                ExpectedLevels expectedLevels = new ExpectedLevels();
                expectedLevels.check(dupBounds);
            });
        } finally {
            LayoutValidator.setPaused(false);
        }
    }

    @Test
    public void testDuplicateClickableBoundsCheck() throws Exception {
        render("a11y_test_dup_clickable_bounds.xml", session -> {
            ValidatorResult result = getRenderResult(session);
            List<Issue> dupBounds = filter(result.getIssues(), "DuplicateClickableBoundsCheck");

            ExpectedLevels expectedLevels = new ExpectedLevels();
            expectedLevels.expectedErrors = 1;
            expectedLevels.check(dupBounds);
        });
    }

    @Test
    public void testDuplicateSpeakableTextsCheck() throws Exception {
        render("a11y_test_duplicate_speakable.xml", session -> {
            ValidatorResult result = getRenderResult(session);
            List<Issue> duplicateSpeakableTexts = filter(result.getIssues(),
                    "DuplicateSpeakableTextCheck");

            ExpectedLevels expectedLevels = new ExpectedLevels();
            expectedLevels.expectedInfos = 1;
            expectedLevels.expectedWarnings = 1;
            expectedLevels.check(duplicateSpeakableTexts);
        });
    }

    @Test
    public void testRedundantDescriptionCheck() throws Exception {
        render("a11y_test_redundant_desc.xml", session -> {
            ValidatorResult result = getRenderResult(session);
            List<Issue> redundant = filter(result.getIssues(), "RedundantDescriptionCheck");

            ExpectedLevels expectedLevels = new ExpectedLevels();
            expectedLevels.expectedVerboses = 3;
            expectedLevels.expectedWarnings = 1;
            expectedLevels.expectedFixes = 0;
            expectedLevels.check(redundant);
        });
    }

    @Test
    public void testLabelFor() throws Exception {
        render("a11y_test_speakable_text_present.xml", session -> {
            ValidatorResult result = getRenderResult(session);
            List<Issue> speakableCheck = filter(result.getIssues(), "SpeakableTextPresentCheck");

            // Post-JB MR2 support labelFor, so SpeakableTextPresentCheck does not need to find any
            // speakable text. Expected 1 verbose result saying something along the line of
            // didn't run or not important for a11y.
            ExpectedLevels expectedLevels = new ExpectedLevels();
            expectedLevels.expectedVerboses = 1;
            expectedLevels.check(speakableCheck);
        });
    }

    @Test
    public void testImportantForAccessibility() throws Exception {
        render("a11y_test_speakable_text_present2.xml", session -> {
            ValidatorResult result = getRenderResult(session);
            List<Issue> speakableCheck = filter(result.getIssues(), "SpeakableTextPresentCheck");

            // Post-JB MR2 support importantForAccessibility, so SpeakableTextPresentCheck
            // does not need to find any speakable text. Expected 2 verbose results.
            ExpectedLevels expectedLevels = new ExpectedLevels();
            expectedLevels.expectedVerboses = 2;
            expectedLevels.check(speakableCheck);
        });
    }

    @Test
    public void testSpeakableTextPresentCheck() throws Exception {
        render("a11y_test_speakable_text_present3.xml", session -> {
            ValidatorResult result = getRenderResult(session);
            List<Issue> speakableCheck = filter(result.getIssues(), "SpeakableTextPresentCheck");

            ExpectedLevels expectedLevels = new ExpectedLevels();
            expectedLevels.expectedVerboses = 1;
            expectedLevels.expectedErrors = 1;
            expectedLevels.expectedFixes = 1;
            expectedLevels.check(speakableCheck);

            // Make sure no other errors in the system.
            speakableCheck = filter(speakableCheck, EnumSet.of(Level.ERROR));
            assertEquals(1, speakableCheck.size());
            List<Issue> allErrors = filter(
                    result.getIssues(), EnumSet.of(Level.ERROR, Level.WARNING, Level.INFO));
            checkEquals(speakableCheck, allErrors);
        });
    }

    @Test
    public void testTextContrastCheck() throws Exception {
        render("a11y_test_text_contrast.xml", session -> {
            ValidatorResult result = getRenderResult(session);
            List<Issue> textContrast = filter(result.getIssues(), "TextContrastCheck");

            // ATF doesn't count alpha values unless image is passed.
            ExpectedLevels expectedLevels = new ExpectedLevels();
            expectedLevels.expectedErrors = 4;
            expectedLevels.expectedVerboses = 1;
            expectedLevels.expectedFixes = 4;
            expectedLevels.check(textContrast);

            // Make sure no other errors in the system.
            textContrast = filter(textContrast, EnumSet.of(Level.ERROR));
            List<Issue> filtered = filter(result.getIssues(), EnumSet.of(Level.ERROR));
            checkEquals(filtered, textContrast);
        });
    }

    /* TODO: {@link LayoutValidator::obtainCharacterLocations is false by default for now }*/
    @Test
    public void testSwitchTextContrastCheck() throws Exception {
        render("a11y_test_switch_text_contrast.xml", session -> {
            ValidatorResult result = getRenderResult(session);
            List<Issue> textContrast = filter(result.getIssues(), "TextContrastCheck");

            // ATF doesn't count alpha values in a Switch unless image is passed and the character
            // locations are available.
            ExpectedLevels expectedLevels = new ExpectedLevels();
            expectedLevels.expectedErrors = 0;
            expectedLevels.expectedWarnings = 1; // True only if character locations are available.
            expectedLevels.expectedVerboses = 2;
            expectedLevels.expectedFixes = 1;
            expectedLevels.check(textContrast);

            // Make sure no other errors in the system.
            textContrast = filter(textContrast, EnumSet.of(Level.ERROR));
            List<Issue> filtered = filter(result.getIssues(), EnumSet.of(Level.ERROR));
            checkEquals(filtered, textContrast);
        });
    }

    @Test
    public void testTextContrastCheckNoImage() throws Exception {
        render("a11y_test_text_contrast.xml", session -> {
            ValidatorResult result = getRenderResult(session);
            List<Issue> textContrast = filter(result.getIssues(), "TextContrastCheck");

            // ATF doesn't count alpha values unless image is passed.
            ExpectedLevels expectedLevels = new ExpectedLevels();
            expectedLevels.expectedErrors = 4;
            expectedLevels.expectedVerboses = 1;
            expectedLevels.expectedFixes = 4;
            expectedLevels.check(textContrast);

            // Make sure no other errors in the system.
            textContrast = filter(textContrast, EnumSet.of(Level.ERROR));
            List<Issue> filtered = filter(result.getIssues(), EnumSet.of(Level.ERROR));
            checkEquals(filtered, textContrast);
        }, false);
    }

    @Test
    public void testImageContrastCheck() throws Exception {
        render("a11y_test_image_contrast.xml", session -> {
            ValidatorResult result = getRenderResult(session);
            List<Issue> imageContrast = filter(result.getIssues(), "ImageContrastCheck");

            ExpectedLevels expectedLevels = new ExpectedLevels();
            expectedLevels.expectedWarnings = 1;
            expectedLevels.expectedVerboses = 1;
            expectedLevels.check(imageContrast);

            // Make sure no other errors in the system.
            imageContrast = filter(imageContrast, EnumSet.of(Level.ERROR, Level.WARNING));
            List<Issue> filtered = filter(result.getIssues(), EnumSet.of(Level.ERROR, Level.WARNING));
            checkEquals(filtered, imageContrast);
        });
    }

    @Test
    public void testClassLoaderOverride() throws Exception {
        final boolean[] overriddenClassLoaderCalled = {false};

        // testAndroid will fail to find class - so to trigger LayoutlibCallback
        DefaultCustomViewBuilderAndroid testAndroid = new DefaultCustomViewBuilderAndroid() {
            @Override
            public Class<?> getClassByName(
                    ViewHierarchyElementAndroid view, String className) {
                return null;
            }
        };
        // Callback when CustomViewBuilderAndroid fails.
        LayoutLibTestCallback testCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader) {
                    @Override
                    public Class<?> findClass(String name) throws ClassNotFoundException {
                        if (name.contains("ImageView")) {
                            // Make sure one of the view (ImageView) passes thru here
                            overriddenClassLoaderCalled[0] = true;
                        }
                        return mDefaultClassLoader.loadClass(name);
                    }
                };
        try {
            ValidatorUtil.sDefaultCustomViewBuilderAndroid = testAndroid;
            render("a11y_test_image_contrast.xml", session -> {
                ValidatorResult result = getRenderResult(session);
                List<Issue> imageContrast = filter(result.getIssues(), "ImageContrastCheck");

                ExpectedLevels expectedLevels = new ExpectedLevels();
                expectedLevels.expectedWarnings = 1;
                expectedLevels.expectedVerboses = 1;
                expectedLevels.check(imageContrast);

                // Ensure that the check went thru the overridden class loader.
                assertTrue(overriddenClassLoaderCalled[0]);
            }, true, testCallback);
        } finally {
            ValidatorUtil.sDefaultCustomViewBuilderAndroid = new DefaultCustomViewBuilderAndroid();
        }
    }

    @Test
    public void testImageContrastCheckNoImage() throws Exception {
        render("a11y_test_image_contrast.xml", session -> {
            ValidatorResult result = getRenderResult(session);
            List<Issue> imageContrast = filter(result.getIssues(), "ImageContrastCheck");

            ExpectedLevels expectedLevels = new ExpectedLevels();
            expectedLevels.expectedVerboses = 3;
            expectedLevels.check(imageContrast);

            // Make sure no other errors in the system.
            imageContrast = filter(imageContrast, EnumSet.of(Level.ERROR, Level.WARNING));
            List<Issue> filtered = filter(result.getIssues(), EnumSet.of(Level.ERROR, Level.WARNING));
            checkEquals(filtered, imageContrast);
        }, false);
    }

    @Test
    public void testTouchTargetSizeCheck() throws Exception {
        render("a11y_test_touch_target_size.xml", session -> {
            ValidatorResult result = getRenderResult(session);
            List<Issue> targetSizes = filter(result.getIssues(), "TouchTargetSizeCheck");

            ExpectedLevels expectedLevels = new ExpectedLevels();
            expectedLevels.expectedErrors = 5;
            expectedLevels.expectedVerboses = 1;
            expectedLevels.expectedFixes = 5;
            expectedLevels.check(targetSizes);

            // Make sure no other errors in the system.
            targetSizes = filter(targetSizes, EnumSet.of(Level.ERROR));
            List<Issue> filtered = filter(result.getIssues(), EnumSet.of(Level.ERROR));
            checkEquals(filtered, targetSizes);
        });
    }

    @Test
    public void testCharacterLocationArgMaxLength() throws Exception {
        render("justified_none.xml", session -> {
            ValidatorHierarchy hierarchy = (ValidatorHierarchy)session.getValidationData();

            List<ViewHierarchyElementAndroid> textViews =
                    hierarchy.mView.getActiveWindow().getAllViews().stream().filter(view->
                            (view.getClassName() != null &&
                                    view.getClassName().toString().contains("TextView"))).collect(
                            Collectors.toList());

            // The text of the only TextView is very long (more than 1000 characters), but
            // only 100 text character locations are retrieved because
            // setCharacterLocationArgMaxLength method works as expected.
            assertEquals(textViews.size(), 1);
            assertEquals(textViews.get(0).getTextCharacterLocations().size(),
                    ValidatorUtil.CHARACTER_LOCATION_ARG_MAX_LENGTH - 1);
        });
    }

    private void checkEquals(List<Issue> list1, List<Issue> list2) {
        assertEquals(list1.size(), list2.size());
        for (int i = 0; i < list1.size(); i++) {
            assertEquals(list1.get(i), list2.get(i));
        }
    }

    private ValidatorResult getRenderResult(RenderSession session) {
        Object validationData = session.getValidationData();
        assertTrue(validationData instanceof ValidatorHierarchy);

        ValidatorResult result = ValidatorUtil.generateResults(LayoutValidator.DEFAULT_POLICY,
                (ValidatorHierarchy) validationData);
        return result;
    }
    private void render(String fileName, RenderSessionListener verifier) throws Exception {
        render(fileName, verifier, true);
    }

    private void render(
            String fileName,
            RenderSessionListener verifier,
            boolean enableImageCheck) throws Exception {
        render(
                fileName,
                verifier,
                enableImageCheck,
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader));
    }

    private void render(
            String fileName,
            RenderSessionListener verifier,
            boolean enableImageCheck,
            LayoutLibTestCallback layoutLibCallback) throws Exception {
        LayoutValidator.updatePolicy(new Policy(
                EnumSet.of(Type.ACCESSIBILITY, Type.RENDER),
                EnumSet.of(Level.ERROR, Level.WARNING, Level.INFO, Level.VERBOSE)));
        LayoutValidator.setObtainCharacterLocations(true);

        LayoutPullParser parser = createParserFromPath(fileName);
        layoutLibCallback.initResources();
        SessionParamsBuilder params = getSessionParamsBuilder()
                .setParser(parser)
                .setConfigGenerator(ConfigGenerator.NEXUS_5)
                .setCallback(layoutLibCallback)
                .disableDecoration()
                .enableLayoutValidation();

        if (enableImageCheck) {
            params.enableLayoutValidationImageCheck();
        }

        render(sBridge, params.build(), -1, verifier);
    }

    /**
     * Helper class that checks the list of issues..
     */
    private static class ExpectedLevels {
        // Number of errors expected
        public int expectedErrors = 0;
        // Number of warnings expected
        public int expectedWarnings = 0;
        // Number of infos expected
        public int expectedInfos = 0;
        // Number of verboses expected
        public int expectedVerboses = 0;
        // Number of fixes expected
        public int expectedFixes = 0;

        public void check(List<Issue> issues) {
            int errors = 0;
            int warnings = 0;
            int infos = 0;
            int verboses = 0;
            int fixes = 0;

            for (Issue issue : issues) {
                switch (issue.mLevel) {
                    case ERROR:
                        errors++;
                        break;
                    case WARNING:
                        warnings++;
                        break;
                    case INFO:
                        infos++;
                        break;
                    case VERBOSE:
                        verboses++;
                        break;
                }

                if (issue.mFix != null) {
                    fixes ++;
                }
            }

            assertEquals("Number of expected errors", expectedErrors, errors);
            assertEquals("Number of expected warnings",expectedWarnings, warnings);
            assertEquals("Number of expected infos", expectedInfos, infos);
            assertEquals("Number of expected verboses", expectedVerboses, verboses);
            assertEquals("Number of expected fixes", expectedFixes, fixes);

            int size = expectedErrors + expectedWarnings + expectedInfos + expectedVerboses;
            assertEquals("expected size", size, issues.size());
        }
    };
}
