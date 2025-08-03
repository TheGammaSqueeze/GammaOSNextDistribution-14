/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.bedstead.harrier.annotations;

import com.android.bedstead.harrier.HarrierRule
import com.android.bedstead.harrier.UserType.INITIAL_USER;
import com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence.REQUIRE_RUN_ON_PRECEDENCE;
import com.android.bedstead.nene.types.OptionalBoolean.ANY;
import com.android.bedstead.nene.types.OptionalBoolean.FALSE;

import com.android.bedstead.harrier.UserType;
import com.android.bedstead.harrier.annotations.enterprise.AdditionalQueryParameters;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDeviceOwner;
import com.android.bedstead.harrier.annotations.meta.EnsureHasProfileAnnotation;
import com.android.bedstead.nene.types.OptionalBoolean;
import com.android.queryable.annotations.Query;
import com.google.auto.value.AutoAnnotation


/**
 * Mark that a test method should run on a user which has a work profile.
 *
 * Use of this annotation implies
 * [RequireFeature("android.software.managed_users", SKIP)].
 *
 * Your test configuration may be configured so that this test is only run on a user which has
 * a work profile. Otherwise, you can use [DeviceState] to ensure that the device enters
 * the correct state for the method.
 *
 * @param forUser Which user type the work profile should be attached to.
 * @param installInstrumentedApp Whether the instrumented test app should be installed in the work profile.
 * @param dpcKey The key used to identify the profile owner.
 *  This can be used with [AdditionalQueryParameters] to modify the requirements for
 *  the DPC.
 * @param dpc Requirements for the Profile Owner. Defaults to the default version of RemoteDPC.
 * @param dpcIsPrimary Whether the profile owner's DPC should be returned by calls to [DeviceState#dpc()].
 *  Only one device policy controller per test should be marked as primary.
 * @param isOrganizationOwned Whether the work profile device will be in COPE mode.
 * @param useParentInstanceOfDpc If true, uses the [DevicePolicyManager#getParentProfileInstance(ComponentName)]
 *  instance of the dpc when calling to .dpc(). Only used if [dpcIsPrimary] is true.
 * @param switchedToParentUser Should we ensure that we are switched to the parent of the profile.
 * @param isQuietModeEnabled Is the profile in quiet mode?
 * @param priority Priority sets the order that annotations will be resolved.
 *  Annotations with a lower priority will be resolved before annotations with a higher
 *  priority.
 *
 *  If there is an order requirement between annotations, ensure that the priority of the
 *  annotation which must be resolved first is lower than the one which must be resolved later.
 *
 *  Priority can be set to a [AnnotationPriorityRunPrecedence] constant, or to any [int].
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
@EnsureHasProfileAnnotation(value = "android.os.usertype.profile.MANAGED", hasProfileOwner = true)
@RequireFeature("android.software.managed_users")
@EnsureHasNoDeviceOwner // TODO: This should only apply on Android R+
annotation class EnsureHasWorkProfile(
    val forUser: UserType = INITIAL_USER,
    val installInstrumentedApp: OptionalBoolean = ANY,
    val dpcKey: String = DEFAULT_KEY,
    val dpc: Query = Query(),
    val dpcIsPrimary: Boolean = false,
    val isOrganizationOwned: Boolean = false,
    val useParentInstanceOfDpc: Boolean = false,
    val switchedToParentUser: OptionalBoolean = ANY,
    val isQuietModeEnabled: OptionalBoolean = FALSE,
    val priority: Int = ENSURE_HAS_WORK_PROFILE_PRIORITY
)

const val ENSURE_HAS_WORK_PROFILE_PRIORITY = REQUIRE_RUN_ON_PRECEDENCE - 1

const val DEFAULT_KEY = "profileOwner"

/**
 * Return an instance of the generated class that conforms to the specification of
 * [EnsureHasWorkProfile]. See [AutoAnnotation].
 */
fun ensureHasWorkProfile(): EnsureHasWorkProfile {
    return ensureHasWorkProfile(query())
}

@AutoAnnotation
private fun ensureHasWorkProfile(dpc: Query): EnsureHasWorkProfile {
    return AutoAnnotation_EnsureHasWorkProfileKt_ensureHasWorkProfile(dpc)
}

/**
 * A workaround to create an [AutoAnnotation] of [EnsureHasWorkProfile]. [AutoAnnotation]
 * cannot set default values for fields of type Annotation, hence we create an object of [Query]
 * explicitly to pass as the default value of the [dpc] field.
 */
private fun query(): Query {
    return HarrierRule::class.java.getAnnotation<Query>(Query::class.java)
}
