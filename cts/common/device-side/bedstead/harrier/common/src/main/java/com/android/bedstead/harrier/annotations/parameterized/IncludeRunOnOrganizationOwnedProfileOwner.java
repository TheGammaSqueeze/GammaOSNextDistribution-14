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

package com.android.bedstead.harrier.annotations.parameterized;

import static com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence.EARLY;

import com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence;
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDelegate;
import com.android.bedstead.harrier.annotations.meta.ParameterizedAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Parameterize a test so that it runs on an organization-owned managed profile. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ParameterizedAnnotation(shadows = IncludeRunOnProfileOwnerProfileWithNoDeviceOwner.class)
@RequireRunOnWorkProfile(isOrganizationOwned = true, dpcKey = "dpc")
@EnsureHasNoDelegate
public @interface IncludeRunOnOrganizationOwnedProfileOwner {
     /**
     * Priority sets the order that annotations will be resolved.
     *
     * <p>Annotations with a lower priority will be resolved before annotations with a higher
     * priority.
     *
     * <p>If there is an order requirement between annotations, ensure that the priority of the
     * annotation which must be resolved first is lower than the one which must be resolved later.
     *
     * <p>Priority can be set to a {@link AnnotationPriorityRunPrecedence} constant, or to any {@link int}.
     */
    int priority() default EARLY;
}
