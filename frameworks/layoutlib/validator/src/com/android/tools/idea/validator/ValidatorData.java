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

import com.android.tools.layoutlib.annotations.NotNull;
import com.android.tools.layoutlib.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;

import com.google.android.apps.common.testing.accessibility.framework.AccessibilityHierarchyCheck;

/**
 * Data used for layout validation.
 */
public class ValidatorData {

    /**
     * Category of validation
     */
    public enum Type {
        ACCESSIBILITY,
        RENDER,
        INTERNAL_ERROR
    }

    /**
     * Level of importance
     */
    public enum Level {
        ERROR,
        WARNING,
        INFO,
        /** The test not ran or suppressed. */
        VERBOSE,
    }

    /**
     * Determine what types and levels of validation to run.
     */
    public static class Policy {
        /** Sets of types to filter by. */
        @NotNull public final EnumSet<Type> mTypes;
        /** Sets of levels to filter by. */
        @NotNull public final EnumSet<Level> mLevels;
        /**
         * List of checks to use for the scan. If empty we use the default set
         * defined by {@link AccessibilityCheckPreset.LATEST}
         */
        @NotNull public final HashSet<AccessibilityHierarchyCheck> mChecks = new HashSet();

        public Policy(@NotNull EnumSet<Type> types, @NotNull EnumSet<Level> levels) {
            mTypes = types;
            mLevels = levels;
        }
    }

    /**
     * Issue describing the layout problem.
     */
    public static class Issue {
        @NotNull
        public final String mCategory;
        @NotNull
        public final Type mType;
        @NotNull
        public final String mMsg;
        @NotNull
        public final Level mLevel;
        @Nullable
        public final Long mSrcId;
        @Nullable
        public final Fix mFix;
        @NotNull
        public final String mSourceClass;
        @Nullable
        public final String mHelpfulUrl;

        private Issue(
                @NotNull String category,
                @NotNull Type type,
                @NotNull String msg,
                @NotNull Level level,
                @Nullable Long srcId,
                @Nullable Fix fix,
                @NotNull String sourceClass,
                @Nullable String helpfulUrl) {
            mCategory = category;
            mType = type;
            mMsg = msg;
            mLevel = level;
            mSrcId = srcId;
            mFix = fix;
            mSourceClass = sourceClass;
            mHelpfulUrl = helpfulUrl;
        }

        public static class IssueBuilder {
            private String mCategory;
            private Type mType = Type.ACCESSIBILITY;
            private String mMsg;
            private Level mLevel;
            private Long mSrcId;
            private Fix mFix;
            private String mSourceClass;
            private String mHelpfulUrl;

            public IssueBuilder setCategory(String category) {
                mCategory = category;
                return this;
            }

            public IssueBuilder setType(Type type) {
                mType = type;
                return this;
            }

            public IssueBuilder setMsg(String msg) {
                mMsg = msg;
                return this;
            }

            public IssueBuilder setLevel(Level level) {
                mLevel = level;
                return this;
            }

            public IssueBuilder setSrcId(Long srcId) {
                mSrcId = srcId;
                return this;
            }

            public IssueBuilder setFix(Fix fix) {
                mFix = fix;
                return this;
            }

            public IssueBuilder setSourceClass(String sourceClass) {
                mSourceClass = sourceClass;
                return this;
            }

            public IssueBuilder setHelpfulUrl(String url) {
                mHelpfulUrl = url;
                return this;
            }

            public Issue build() {
                assert(mCategory != null);
                assert(mType != null);
                assert(mMsg != null);
                assert(mLevel != null);
                assert(mSourceClass != null);
                return new Issue(mCategory,
                        mType,
                        mMsg,
                        mLevel,
                        mSrcId,
                        mFix,
                        mSourceClass,
                        mHelpfulUrl);
            }
        }
    }

    /**
     * Represents a view attribute which contains a namespace and an attribute name.
     */
    public static class ViewAttribute {
        /** The namespace used in XML files for this view attribute. */
        @NotNull public final String mNamespaceUri;
        /** The namespace of this view attribute. */
        @NotNull public final String mNamespace;
        /** The attribute name of this view attribute. */
        @NotNull public final String mAttributeName;

        public ViewAttribute(
                @NotNull String namespaceUri,
                @NotNull String namespace,
                @NotNull String attributeName) {
            mNamespaceUri = namespaceUri;
            mNamespace = namespace;
            mAttributeName = attributeName;
        }
    }

    /**
     * Suggested fix to the user or to the studio.
     */
    public static interface Fix {
        /**
         * @return a human-readable description for this fix.
         */
        @NotNull String getDescription();
    }

    /**
     * Suggest setting a value to a {@link ViewAttribute} to fix a specific {@link Issue}.
     *
     * <ul>
     *   <li>If the view attribute has not been set before, add the view attribute and set its value
     *       to the suggested value.
     *   <li>If the view attribute has been set before, replace its value with the suggested value.
     *   <li>If the suggested value is an empty string, ask the developer to set the view attribute
     *       to a meaningful non-empty string or resource reference. DO NOT set the view attribute
     *       to an empty string.
     * </ul>
     */
    public static class SetViewAttributeFix implements Fix {
        /** The {@link ViewAttribute} suggested to be changed. */
        @NotNull public final ViewAttribute mViewAttribute;

        /** The suggested value of the {@link ViewAttribute} suggested to be changed. */
        @NotNull public final String mSuggestedValue;

        @NotNull private final String mDescription;

        public SetViewAttributeFix(@NotNull ViewAttribute viewAttribute,
                @NotNull String suggestedValue, @NotNull String description) {
            mViewAttribute = viewAttribute;
            mSuggestedValue = suggestedValue;
            mDescription = description;
        }

        @Override
        @NotNull public String getDescription() {
            return mDescription;
        }
    }

    /**
     * Suggest removing a {@link ViewAttribute} to fix a specific {@link Issue}.
     */
    public static class RemoveViewAttributeFix implements Fix {
        /** The {@link ViewAttribute} suggested to be removed. */
        @NotNull public final ViewAttribute mViewAttribute;

        @NotNull private final String mDescription;

        public RemoveViewAttributeFix(@NotNull ViewAttribute viewAttribute,
                @NotNull String description) {
            mViewAttribute = viewAttribute;
            mDescription = description;
        }

        @Override
        @NotNull public String getDescription() {
            return mDescription;
        }
    }

    /**
     * Suggest applying multiple {@link Fix} together to fix a specific {@link Issue}.
     *
     * <p>A {@link CompoundFix} must contain at least 2 {@link Fix}.
     */
    public static class CompoundFix implements Fix {
        /** Lists of {@link Fix} suggested to be applied together. */
        @NotNull public final List<Fix> mFixes;

        @NotNull private final String mDescription;

        public CompoundFix(@NotNull List<Fix> fixes, String description) {
            mFixes = fixes;
            mDescription = description;
        }

        @Override
        @NotNull public String getDescription() {
            return mDescription;
        }
    }
}
