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

import com.android.tools.idea.validator.ValidatorData.Level;
import com.android.tools.idea.validator.ValidatorData.Policy;
import com.android.tools.idea.validator.ValidatorData.Type;
import com.android.tools.layoutlib.annotations.NotNull;
import com.android.tools.layoutlib.annotations.Nullable;
import com.android.tools.layoutlib.annotations.VisibleForTesting;

import android.view.View;

import java.awt.image.BufferedImage;
import java.util.EnumSet;

/**
 * Main class for validating layout.
 */
public class LayoutValidator {

    public static final ValidatorData.Policy DEFAULT_POLICY = new Policy(
            EnumSet.of(Type.ACCESSIBILITY, Type.RENDER),
            EnumSet.of(Level.ERROR, Level.WARNING, Level.INFO, Level.VERBOSE));

    private static ValidatorData.Policy sPolicy = DEFAULT_POLICY;

    private static boolean sPaused = false;

    private static boolean sSaveCroppedImages = false;

    private static boolean sObtainCharacterLocations = true;

    /**
     * @return true if validator is paused. False otherwise.
     */
    public static boolean isPaused() {
        return sPaused;
    }

    /**
     * Pause or resume validator. {@link RenderParamsFlags#FLAG_ENABLE_LAYOUT_VALIDATOR} must be
     * enabled.
     * @param paused true if validator should be paused. False to resume.
     */
    public static void setPaused(boolean paused) {
        sPaused = paused;
    }

    public static boolean shouldSaveCroppedImages() {
        return sSaveCroppedImages;
    }

    /**
     * For Debugging purpose. Save all cropped images used by atf if enabled.
     * @param save
     */
    public static void setSaveCroppedImages(boolean save) {
        sSaveCroppedImages = save;
    }

    /**
     * Indicates whether text character locations should be requested.
     *
     * @param obtainCharacterLocations true if text character locations should be requested.
     */
    public static void setObtainCharacterLocations(boolean obtainCharacterLocations) {
        sObtainCharacterLocations = obtainCharacterLocations;
    }

    /**
     * @return true if text character locations should be requested.
     */
    public static boolean obtainCharacterLocations() {
        return sObtainCharacterLocations;
    }

    /**
     * Validate the layout using the default policy.
     * Precondition: View must be attached to the window.
     *
     * Used for testing.
     *
     * @return The validation results. If no issue is found it'll return empty result.
     */
    @NotNull
    @VisibleForTesting
    public static ValidatorResult validate(
            @NotNull View view,
            @Nullable BufferedImage image,
            float scaleX,
            float scaleY) {
        if (!sPaused && view.isAttachedToWindow()) {
            ValidatorHierarchy hierarchy = ValidatorUtil.buildHierarchy(
                    sPolicy,
                    view,
                    image,
                    scaleX,
                    scaleY);
            return ValidatorUtil.generateResults(sPolicy, hierarchy);
        }
        // TODO: Add non-a11y layout validation later.
        return new ValidatorResult.Builder().build();
    }

    /**
     * Build the hierarchy necessary for validating the layout.
     * The operation is quick thus can be used frequently.
     *
     * @return The hierarchy to be used for validation.
     */
    @NotNull
    public static ValidatorHierarchy buildHierarchy(
            @NotNull View view,
            @Nullable BufferedImage image,
            float scaleX,
            float scaleY) {
        if (!sPaused && view.isAttachedToWindow()) {
            return ValidatorUtil.buildHierarchy(
                    sPolicy,
                    view,
                    image,
                    scaleX,
                    scaleY);
        }
        return new ValidatorHierarchy();
    }

    /**
     * @return The validator result that matches the hierarchy
     */
    @NotNull
    public static ValidatorResult validate(@NotNull ValidatorHierarchy hierarchy) {
        return ValidatorUtil.generateResults(sPolicy, hierarchy);
    }

    /**
     * Update the policy with which to run the validation call.
     * @param policy new policy.
     */
    public static void updatePolicy(@NotNull ValidatorData.Policy policy) {
        sPolicy = policy;
    }
}
