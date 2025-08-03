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

import com.android.tools.layoutlib.annotations.Nullable;

import com.google.android.apps.common.testing.accessibility.framework.Parameters;
import com.google.android.apps.common.testing.accessibility.framework.uielement.AccessibilityHierarchyAndroid;

/**
 * Hierarchical data required for running the ATF scanner checks.
 * Creation of the hierarchical data is pretty quick.
 */
public class ValidatorHierarchy {
    /** Contains meta data (such as src map) that is required for building result */
    public @Nullable ValidatorResult.Builder mBuilder = null;
    /** Contains view hierarchy data related to a11y */
    public @Nullable AccessibilityHierarchyAndroid mView = null;
    /** Contains screen capture of the view */
    public @Nullable Parameters mParameters = null;

    public @Nullable String mErrorMessage = null;

    /** Returns true if hierarchical data is available to build results. */
    public boolean isHierarchyBuilt() {
        return mBuilder != null && mView != null;
    }

}
