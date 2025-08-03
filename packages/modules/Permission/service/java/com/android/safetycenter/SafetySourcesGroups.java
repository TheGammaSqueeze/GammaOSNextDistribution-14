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

package com.android.safetycenter;

import android.safetycenter.config.SafetySourcesGroup;

import com.android.modules.utils.build.SdkLevel;

/** Static utilities for working with {@link SafetySourcesGroup} objects. */
final class SafetySourcesGroups {

    /**
     * Returns a builder with all fields of the original group copied other than {@link
     * SafetySourcesGroup#getSafetySources()}.
     */
    static SafetySourcesGroup.Builder copyToBuilderWithoutSources(SafetySourcesGroup group) {
        SafetySourcesGroup.Builder safetySourcesGroupBuilder =
                new SafetySourcesGroup.Builder()
                        .setId(group.getId())
                        .setTitleResId(group.getTitleResId())
                        .setSummaryResId(group.getSummaryResId())
                        .setStatelessIconType(group.getStatelessIconType());
        if (SdkLevel.isAtLeastU()) {
            safetySourcesGroupBuilder.setType(group.getType());
        }
        return safetySourcesGroupBuilder;
    }

    private SafetySourcesGroups() {}
}
