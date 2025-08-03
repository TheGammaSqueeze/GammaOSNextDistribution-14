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

package com.android.cts.appcompat;

import static com.google.common.truth.Truth.assertWithMessage;

import android.compat.cts.Change;
import android.compat.cts.CompatChangeGatingTestCase;

import com.google.common.collect.ImmutableSet;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public final class CompatChangesValidConfigTest extends CompatChangeGatingTestCase {

    private static final long RESTRICT_STORAGE_ACCESS_FRAMEWORK = 141600225L;
    private static final long SPLIT_AS_STREAM_RETURNS_SINGLE_EMPTY_STRING = 288845345L;
    private static final long PRIORITY_QUEUE_OFFER_NON_COMPARABLE_ONE_ELEMENT = 289878283L;
    private static final String FEATURE_WATCH = "android.hardware.type.watch";

    private static final Set<String> OVERRIDES_ALLOWLIST = ImmutableSet.of(
        // This change id will sometimes remain enabled if an instrumentation test fails.
        "ALLOW_TEST_API_ACCESS"
    );

    private static final Set<String> OVERRIDABLE_CHANGES = ImmutableSet.of(
            "ALWAYS_SANDBOX_DISPLAY_APIS",
            "CTS_SYSTEM_API_OVERRIDABLE_CHANGEID",
            "DEFER_BOOT_COMPLETED_BROADCAST_CHANGE_ID",
            "DOWNSCALED",
            "DOWNSCALED_INVERSE",
            "DOWNSCALE_30",
            "DOWNSCALE_35",
            "DOWNSCALE_40",
            "DOWNSCALE_45",
            "DOWNSCALE_50",
            "DOWNSCALE_55",
            "DOWNSCALE_60",
            "DOWNSCALE_65",
            "DOWNSCALE_70",
            "DOWNSCALE_75",
            "DOWNSCALE_80",
            "DOWNSCALE_85",
            "DOWNSCALE_90",
            "DO_NOT_DOWNSCALE_TO_1080P_ON_TV",
            "ENFORCE_MINIMUM_TIME_WINDOWS",
            "FGS_BG_START_RESTRICTION_CHANGE_ID",
            "FGS_BOOT_COMPLETED_RESTRICTIONS",
            "FGS_TYPE_DATA_SYNC_DEPRECATION_CHANGE_ID",
            "FGS_TYPE_DATA_SYNC_DISABLED_CHANGE_ID",
            "FGS_TYPE_NONE_DEPRECATION_CHANGE_ID",
            "FGS_TYPE_NONE_DISABLED_CHANGE_ID",
            "FGS_TYPE_PERMISSION_CHANGE_ID",
            "FORCE_NON_RESIZE_APP",
            "FORCE_RESIZE_APP",
            "OVERRIDE_CAMERA_ROTATE_AND_CROP_DEFAULTS",
            "OVERRIDE_CAMERA_RESIZABLE_AND_SDK_CHECK",
            "OVERRIDE_CAMERA_ROTATE_AND_CROP",
            "OVERRIDE_CAMERA_LANDSCAPE_TO_PORTRAIT",
            "IGNORE_ALLOW_BACKUP_IN_D2D",
            "IGNORE_FULL_BACKUP_CONTENT_IN_D2D",
            "NEVER_SANDBOX_DISPLAY_APIS",
            "OVERRIDE_MIN_ASPECT_RATIO",
            "OVERRIDE_MIN_ASPECT_RATIO_EXCLUDE_PORTRAIT_FULLSCREEN",
            "OVERRIDE_MIN_ASPECT_RATIO_PORTRAIT_ONLY",
            "OVERRIDE_MIN_ASPECT_RATIO_LARGE",
            "OVERRIDE_MIN_ASPECT_RATIO_MEDIUM",
            "OVERRIDE_MIN_ASPECT_RATIO_TO_ALIGN_WITH_SPLIT_SCREEN",
            "IMPLICIT_INTENTS_ONLY_MATCH_EXPORTED_COMPONENTS",
            "BLOCK_MUTABLE_IMPLICIT_PENDING_INTENT",
            "OVERRIDE_ENABLE_COMPAT_FAKE_FOCUS",
            "OVERRIDE_UNDEFINED_ORIENTATION_TO_PORTRAIT",
            "OVERRIDE_UNDEFINED_ORIENTATION_TO_NOSENSOR",
            "OVERRIDE_LANDSCAPE_ORIENTATION_TO_REVERSE_LANDSCAPE",
            "OVERRIDE_ANY_ORIENTATION",
            "OVERRIDE_ANY_ORIENTATION_TO_USER",
            "OVERRIDE_USE_DISPLAY_LANDSCAPE_NATURAL_ORIENTATION",
            "OVERRIDE_ENABLE_COMPAT_IGNORE_REQUESTED_ORIENTATION",
            "OVERRIDE_ORIENTATION_ONLY_FOR_CAMERA",
            "OVERRIDE_CAMERA_COMPAT_DISABLE_FORCE_ROTATION",
            "OVERRIDE_CAMERA_COMPAT_DISABLE_REFRESH",
            "OVERRIDE_CAMERA_COMPAT_ENABLE_REFRESH_VIA_PAUSE",
            "OVERRIDE_ENABLE_COMPAT_IGNORE_REQUESTED_ORIENTATION",
            "OVERRIDE_ENABLE_COMPAT_IGNORE_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED",
            "OVERRIDE_RESPECT_REQUESTED_ORIENTATION",
            "OVERRIDE_SANDBOX_VIEW_BOUNDS_APIS",
            "DEFAULT_RESCIND_BAL_FG_PRIVILEGES_BOUND_SERVICE",
            "DEFAULT_RESCIND_BAL_PRIVILEGES_FROM_PENDING_INTENT_SENDER",
            "RETURN_DEVICE_VOLUME_BEHAVIOR_ABSOLUTE_ADJUST_ONLY",
            "OVERRIDE_ENABLE_EXPECTED_PRSENTATION_TIME",
            "ENFORCE_INTENTS_TO_MATCH_INTENT_FILTERS",
            "SEND_CHOOSER_RESULT"
    );

    /**
     * Check that there are no overrides.
     */
    public void testNoOverrides() throws Exception {
        for (Change c : getOnDeviceCompatConfig()) {
            if (!OVERRIDES_ALLOWLIST.contains(c.changeName) && !c.overridable) {
                assertWithMessage("Change should not have overrides: " + c)
                        .that(c.hasOverrides).isFalse();
            }
        }
    }

    /**
     * Check that only approved changes are overridable.
     */
    public void testOnlyAllowedlistedChangesAreOverridable() throws Exception {
        for (Change c : getOnDeviceCompatConfig()) {
            // Skip changeIDs with EnabledSince more than platform sdk version
            if (c.overridable && getDevice().checkApiLevelAgainstNextRelease(c.sinceSdk)) {
                assertWithMessage("Please contact compat-team@google.com for approval")
                        .that(OVERRIDABLE_CHANGES).contains(c.changeName);
            }
        }
    }

    /**
     * Check that the on device config contains all the expected change ids defined in the platform.
     * The device may contain extra changes, but none may be removed.
     */
    // Ignored due to b/319227557
    // public void testDeviceContainsExpectedConfig() throws Exception {
    //     assertThat(getOnDeviceCompatConfig()).containsAtLeastElementsIn(getExpectedCompatConfig());
    // }


    /**
     * Parse the expected (i.e. defined in platform) config xml.
     */
    private List<Change> getExpectedCompatConfig() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document dom = db.parse(getClass().getResourceAsStream("/cts_all_compat_config.xml"));
        Element root = dom.getDocumentElement();
        NodeList changeNodes = root.getElementsByTagName("compat-change");
        List<Change> changes = new ArrayList<>();
        for (int nodeIdx = 0; nodeIdx < changeNodes.getLength(); ++nodeIdx) {
            Change change = Change.fromNode(changeNodes.item(nodeIdx));
            // Exclude logging only changes from the expected config. See b/155264388.
            if (!change.loggingOnly) {
                changes.add(change);
            }
        }

        // RESTRICT_STORAGE_ACCESS_FRAMEWORK not supported on wear
        if (getDevice().hasFeature(FEATURE_WATCH)) {
            for (Iterator<Change> it = changes.iterator(); it.hasNext();) {
                if (it.next().changeId == RESTRICT_STORAGE_ACCESS_FRAMEWORK) {
                    it.remove();
                }
            }
        }

        // Exclude SPLIT_AS_STREAM_RETURNS_SINGLE_EMPTY_STRING
        // This feature is enabled only from U for apps targeting SDK 34+, see b/288845345
        changes.removeIf(c -> c.changeId == SPLIT_AS_STREAM_RETURNS_SINGLE_EMPTY_STRING);

        // Exclude PRIORITY_QUEUE_OFFER_NON_COMPARABLE_ONE_ELEMENT
        // This feature is enabled only from U for apps targeting SDK 34+, see b/297482242
        changes.removeIf(c -> c.changeId == PRIORITY_QUEUE_OFFER_NON_COMPARABLE_ONE_ELEMENT);

        return changes;
    }

}
