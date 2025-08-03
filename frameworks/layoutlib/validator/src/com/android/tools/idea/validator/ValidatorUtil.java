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

import com.android.tools.idea.validator.ValidatorData.CompoundFix;
import com.android.tools.idea.validator.ValidatorData.Issue;
import com.android.tools.idea.validator.ValidatorData.Issue.IssueBuilder;
import com.android.tools.idea.validator.ValidatorData.Level;
import com.android.tools.idea.validator.ValidatorData.RemoveViewAttributeFix;
import com.android.tools.idea.validator.ValidatorData.SetViewAttributeFix;
import com.android.tools.idea.validator.ValidatorData.Type;
import com.android.tools.idea.validator.ValidatorResult.Builder;
import com.android.tools.idea.validator.hierarchy.CustomHierarchyHelper;
import com.android.tools.layoutlib.annotations.NotNull;
import com.android.tools.layoutlib.annotations.Nullable;

import android.view.View;

import java.awt.image.BufferedImage;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheck;
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset;
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType;
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityHierarchyCheck;
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityHierarchyCheckResult;
import com.google.android.apps.common.testing.accessibility.framework.Parameters;
import com.google.android.apps.common.testing.accessibility.framework.checks.EditableContentDescCheck;
import com.google.android.apps.common.testing.accessibility.framework.checks.SpeakableTextPresentCheck;
import com.google.android.apps.common.testing.accessibility.framework.checks.TextContrastCheck;
import com.google.android.apps.common.testing.accessibility.framework.checks.TouchTargetSizeCheck;
import com.google.android.apps.common.testing.accessibility.framework.strings.StringManager;
import com.google.android.apps.common.testing.accessibility.framework.suggestions.CompoundFixSuggestions;
import com.google.android.apps.common.testing.accessibility.framework.suggestions.FixSuggestion;
import com.google.android.apps.common.testing.accessibility.framework.suggestions.FixSuggestionPreset;
import com.google.android.apps.common.testing.accessibility.framework.suggestions.RemoveViewAttributeFixSuggestion;
import com.google.android.apps.common.testing.accessibility.framework.suggestions.SetViewAttributeFixSuggestion;
import com.google.android.apps.common.testing.accessibility.framework.suggestions.ViewAttribute;
import com.google.android.apps.common.testing.accessibility.framework.uielement.AccessibilityHierarchy;
import com.google.android.apps.common.testing.accessibility.framework.uielement.AccessibilityHierarchyAndroid;
import com.google.android.apps.common.testing.accessibility.framework.uielement.CustomViewBuilderAndroid;
import com.google.android.apps.common.testing.accessibility.framework.uielement.DefaultCustomViewBuilderAndroid;
import com.google.android.apps.common.testing.accessibility.framework.uielement.ViewHierarchyElementAndroid;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class ValidatorUtil {

    static {
        /**
         * Overriding default ResourceBundle ATF uses. ATF would use generic Java resources
         * instead of Android's .xml.
         *
         * By default ATF generates ResourceBundle to support Android specific env/ classloader,
         * which is quite different from Layoutlib, which supports multiple classloader depending
         * on env (testing vs in studio).
         *
         * To support ATF in Layoutlib, easiest way is to convert resources from Android xml to
         * generic Java resources (strings.properties), and have the default ResourceBundle ATF
         * uses be redirected.
         */
        StringManager.setResourceBundleProvider(locale -> ResourceBundle.getBundle("strings"));
    }

    // Visible for testing.
    protected static DefaultCustomViewBuilderAndroid sDefaultCustomViewBuilderAndroid =
            new DefaultCustomViewBuilderAndroid();

    /**
     * Fixes could be only provided for a {@link AccessibilityHierarchyCheckResult}s generated by
     * a predefined set of {@link AccessibilityHierarchyCheck}s.
     */
    private final static ImmutableSet<Class<? extends AccessibilityHierarchyCheck>>
            sAllowedCheckResultClassSet4Fix = ImmutableSet.of(SpeakableTextPresentCheck.class,
            TextContrastCheck.class, TouchTargetSizeCheck.class,  EditableContentDescCheck.class);

    /**
     * The maximum allowed length of the requested text location data is used to avoid the
     * performance issue caused by obtaining character location data for a view with a long text.
     */
    public static final int CHARACTER_LOCATION_ARG_MAX_LENGTH = 100;

    /**
     * @param policy policy to apply for the hierarchy
     * @param view root view to build hierarchy from
     * @param image screenshot image that matches the view
     * @param scaleX scaling done via layoutlib in x coord
     * @param scaleY scaling done via layoutlib in y coord
     * @return The hierarchical data required for running the ATF checks.
     */
    public static ValidatorHierarchy buildHierarchy(
            @NotNull ValidatorData.Policy policy,
            @NotNull View view,
            @Nullable BufferedImage image,
            float scaleX,
            float scaleY) {
        ValidatorHierarchy hierarchy = new ValidatorHierarchy();
        if (!policy.mTypes.contains(Type.ACCESSIBILITY)) {
            return hierarchy;
        }

        ValidatorResult.Builder builder = new ValidatorResult.Builder();
        @Nullable Parameters parameters = null;
        builder.mMetric.startHierarchyCreationTimer();
        try {
            hierarchy.mView = AccessibilityHierarchyAndroid
                    .newBuilder(view)
                    .enableViewOverlay()
                    .setViewOriginMap(builder.mSrcMap)
                    .setNodeInfoOriginMap(builder.mNodeInfoMap)
                    .setObtainCharacterLocations(LayoutValidator.obtainCharacterLocations())
                    .setCharacterLocationArgMaxLength(CHARACTER_LOCATION_ARG_MAX_LENGTH)
                    .setCustomViewBuilder(new CustomViewBuilderAndroid() {
                        @Override
                        public Class<?> getClassByName(
                                ViewHierarchyElementAndroid viewHierarchyElementAndroid,
                                String className) {
                            Class<?> toReturn = sDefaultCustomViewBuilderAndroid.getClassByName(
                                    viewHierarchyElementAndroid, className);
                            if (toReturn == null) {
                                toReturn = CustomHierarchyHelper.getClassByName(className);
                            }
                            return toReturn;
                        }

                        @Override
                        public boolean isCheckable(View view) {
                            return CustomHierarchyHelper.isCheckable(view);
                        }
                    }).build();
            if (image != null) {
                parameters = new Parameters();
                parameters.putScreenCapture(
                        new AtfBufferedImage(image, builder.mMetric, scaleX, scaleY));
            }
        } finally {
            builder.mMetric.recordHierarchyCreationTime();
        }

        hierarchy.mBuilder = builder;
        hierarchy.mParameters = parameters;
        return hierarchy;
    }

    /**
     * @param hierarchy to build result from. If {@link ValidatorHierarchy#isHierarchyBuilt()}
     * is false, returns a result with an internal error.
     * @return Returns ValidatorResult with given hierarchical data.
     */
    public static ValidatorResult generateResults(
            @NotNull ValidatorData.Policy policy,
            @NotNull ValidatorHierarchy hierarchy) {
        ValidatorResult.Builder builder = hierarchy.mBuilder;
        try {
            if (!hierarchy.isHierarchyBuilt()) {
                // Unable to build.
                builder = new Builder();
                String errorMsg = hierarchy.mErrorMessage != null ? hierarchy.mErrorMessage :
                        "Hierarchy is not built yet.";
                builder.mIssues.add(new IssueBuilder()
                        .setCategory("Accessibility")
                        .setType(Type.INTERNAL_ERROR)
                        .setMsg(errorMsg)
                        .setLevel(Level.ERROR)
                        .setSourceClass("ValidatorHierarchy")
                        .build());
                return builder.build();
            }
            builder.mMetric.startGenerateResultsTimer();

            AccessibilityHierarchyAndroid view = hierarchy.mView;
            Parameters parameters = hierarchy.mParameters;

            EnumSet<Level> filter = policy.mLevels;
            ArrayList<AccessibilityHierarchyCheckResult> a11yResults = new ArrayList<>();

            HashSet<AccessibilityHierarchyCheck> policyChecks = policy.mChecks;
            @NotNull Set<AccessibilityHierarchyCheck> checks = policyChecks.isEmpty() ?
                    AccessibilityCheckPreset.getAccessibilityHierarchyChecksForPreset(
                            AccessibilityCheckPreset.LATEST) : policyChecks;

            for (AccessibilityHierarchyCheck check : checks) {
                a11yResults.addAll(check.runCheckOnHierarchy(view, null, parameters));
            }

            for (AccessibilityHierarchyCheckResult result : a11yResults) {
                // TODO: b/183726816 replace this with
                // AccessibilityCheckPreset.getHierarchyCheckForClassName(checkClassName)
                // .getTitleMessage(Locale.ENGLISH)
                String category = ValidatorUtil.getCheckClassCategory(result.getSourceCheckClass());

                ValidatorData.Level level = ValidatorUtil.convertLevel(result.getType());
                if (!filter.contains(level)) {
                    continue;
                }

                try {
                    IssueBuilder issueBuilder = new IssueBuilder().setCategory(category).setMsg(
                            result.getMessage(Locale.ENGLISH).toString()).setLevel(level).setFix(
                            ValidatorUtil.generateFix(result, view, parameters)).setSourceClass(
                            result.getSourceCheckClass().getSimpleName());
                    if (result.getElement() != null) {
                        issueBuilder.setSrcId(result.getElement().getCondensedUniqueId());
                    }
                    AccessibilityHierarchyCheck subclass =
                            AccessibilityCheckPreset.getHierarchyCheckForClass(
                                    result.getSourceCheckClass().asSubclass(
                                            AccessibilityHierarchyCheck.class));
                    if (subclass != null) {
                        issueBuilder.setHelpfulUrl(subclass.getHelpUrl());
                    }
                    builder.mIssues.add(issueBuilder.build());
                } catch (Exception e) {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    e.printStackTrace(pw);
                    builder.mIssues.add(new IssueBuilder()
                            .setCategory(category)
                            .setType(Type.INTERNAL_ERROR)
                            .setMsg(sw.toString())
                            .setLevel(Level.ERROR)
                            .setSourceClass("ValidatorHierarchy").build());
                }
            }
        } finally {
            builder.mMetric.recordGenerateResultsTime();
        }
        return builder.build();
    }

    /**
     * @return the list of internal errors in results. Useful for testing and debugging.
     */
    public static List<Issue> filterInternalErrors(List<ValidatorData.Issue> results) {
        return filterByTypes(results, EnumSet.of(Type.INTERNAL_ERROR));
    }

    /**
     * @return the list filtered by the level. Useful for testing and debugging.
     */
    public static List<Issue> filter(List<ValidatorData.Issue> results, EnumSet<Level> errors) {
        return results.stream().filter(
                issue -> errors.contains(issue.mLevel)).collect(Collectors.toList());
    }

    /**
     * @return the list filtered by the source class name. Useful for testing and debugging.
     */
    public static List<Issue> filter(
            List<ValidatorData.Issue> results, String sourceClass) {
        return results.stream().filter(
                issue -> sourceClass.equals(issue.mSourceClass)).collect(Collectors.toList());
    }

    /**
     * @return the list filtered by the source class name. Useful for testing and debugging.
     */
    public static List<Issue> filterByTypes(
            List<ValidatorData.Issue> results, EnumSet<Type> types) {
        return results.stream().filter(
                issue -> types.contains(issue.mType)).collect(Collectors.toList());
    }

    /**
     * @param checkClass classes expected to extend AccessibilityHierarchyCheck
     * @return {@link AccessibilityCheck.Category} of the class.
     */
    @NotNull
    private static String getCheckClassCategory(@NotNull Class<?> checkClass) {
        try {
            Class<? extends AccessibilityHierarchyCheck> subClass =
                    checkClass.asSubclass(AccessibilityHierarchyCheck.class);
            AccessibilityHierarchyCheck check =
                    AccessibilityCheckPreset.getHierarchyCheckForClass(subClass);
            return (check == null) ? "Accessibility" : check.getCategory().name();
        } catch (ClassCastException e) {
            return "Accessibility";
        }
    }

    /** Convert {@link AccessibilityCheckResultType} to {@link ValidatorData.Level} */
    @NotNull
    private static ValidatorData.Level convertLevel(@NotNull AccessibilityCheckResultType type) {
        switch (type) {
            case ERROR:
                return Level.ERROR;
            case WARNING:
                return Level.WARNING;
            case INFO:
                return Level.INFO;
            // TODO: Maybe useful later?
            case SUPPRESSED:
            case NOT_RUN:
            default:
                return Level.VERBOSE;
        }
    }

    /**
     * Create a {@link ValidatorData.Fix} for the given result, or {@code null} if there is no
     * fixes available.
     *
     * <p>If there are multiple fixes available, return the first fix which is considered to be the
     * best fix available.
     *
     * @param result to generate a fix from.
     * @param hierarchy The hierarchy from which the result is generated from.
     * @param parameters Optional input data or preferences.
     */
    @Nullable
    private static ValidatorData.Fix generateFix(
            @NotNull AccessibilityHierarchyCheckResult result,
            @NotNull AccessibilityHierarchy hierarchy,
            @Nullable Parameters parameters) {
        if (sAllowedCheckResultClassSet4Fix.contains(result.getSourceCheckClass())) {
            ImmutableList<FixSuggestion> fixSuggestions =
                    FixSuggestionPreset.provideFixSuggestions(result, hierarchy, parameters);
            return fixSuggestions.isEmpty() ? null : convertFix(fixSuggestions.get(0));
        }
        return null;
    }

    /** Convert {@link FixSuggestion} to {@link ValidatorData.Fix} */
    @Nullable
    private static ValidatorData.Fix convertFix(@NotNull FixSuggestion fixSuggestion) {
        if (fixSuggestion instanceof CompoundFixSuggestions) {
            CompoundFixSuggestions compoundFixSuggestions = (CompoundFixSuggestions)fixSuggestion;
            List<ValidatorData.Fix> fixes =
                    compoundFixSuggestions
                            .getFixSuggestions()
                            .stream()
                            .map(ValidatorUtil::convertFix)
                            .collect(Collectors.toList());
            return new CompoundFix(
                    fixes,
                    compoundFixSuggestions.getDescription(Locale.ENGLISH));
        } else if (fixSuggestion instanceof RemoveViewAttributeFixSuggestion) {
            RemoveViewAttributeFixSuggestion removeViewAttributeFix =
                    (RemoveViewAttributeFixSuggestion)fixSuggestion;
            return new RemoveViewAttributeFix(
                    convertViewAttribute(removeViewAttributeFix.getViewAttribute()),
                    removeViewAttributeFix.getDescription(Locale.ENGLISH));
        } else if (fixSuggestion instanceof SetViewAttributeFixSuggestion) {
            SetViewAttributeFixSuggestion setViewAttributeFixSuggestion =
                    (SetViewAttributeFixSuggestion)fixSuggestion;
            return new SetViewAttributeFix(
                    convertViewAttribute(setViewAttributeFixSuggestion.getViewAttribute()),
                    setViewAttributeFixSuggestion.getSuggestedValue(),
                    setViewAttributeFixSuggestion.getDescription(Locale.ENGLISH));
        }
        return null;
    }

    /** Convert {@link ViewAttribute} to {@link ValidatorData.ViewAttribute} */
    @NotNull
    private static ValidatorData.ViewAttribute convertViewAttribute(
            @NotNull ViewAttribute viewAttribute) {
        return new ValidatorData.ViewAttribute(
                viewAttribute.getNamespaceUri(),
                viewAttribute.getNamespace(),
                viewAttribute.getAttributeName());
    }
}
