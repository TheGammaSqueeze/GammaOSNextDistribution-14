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

package com.android.bedstead.harrier.annotations.enterprise;

import com.android.bedstead.harrier.HarrierRule
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.UserType.INSTRUMENTED_USER
import com.android.bedstead.harrier.annotations.RequireFeature
import com.android.bedstead.harrier.annotations.RequireNotInstantApp
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner.DO_PO_PRIORITY
import com.android.bedstead.nene.packages.CommonPackages.FEATURE_DEVICE_ADMIN
import com.android.queryable.annotations.Query
import com.google.auto.value.AutoAnnotation

/**
 * Mark that a test requires that a profile owner is set.
 *
 * You can use {@code DeviceState} to ensure that the device enters
 * the correct state for the method. If using [DeviceState], you can use
 * [DeviceState#profileOwner()] to interact with the profile owner.
 *
 * @param onUser Which user type the profile owner should be installed on.
 * @param key The key used to identify this DPC.
 *  This can be used with [AdditionalQueryParameters] to modify the requirements for
 *  the DPC.
 * @param dpc Requirements for the DPC. Defaults to the default version of RemoteDPC.
 * @param isPrimary Whether this DPC should be returned by calls to [Devicestate#dpc()].
 *  Only one policy manager per test should be marked as primary.
 * @param useParentInstance If true, uses the [DevicePolicyManager#getParentProfileInstance(ComponentName)]
 *  instance of the dpc when calling to .dpc()
 *  Only used if [isPrimary] is true.
 * @param affiliationIds Affiliation ids to be set for the profile owner.
 * @param priority Priority sets the order that annotations will be resolved.
 *  Annotations with a lower priority will be resolved before annotations with a higher
 *  priority.
 *
 *  If there is an order requirement between annotations, ensure that the priority of the
 *  annotation which must be resolved first is lower than the one which must be resolved later.
 *
 *  Priority can be set to a [AnnotationPriorityRunPrecedence] constant, or to any [int].
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
@RequireFeature(FEATURE_DEVICE_ADMIN)
// TODO(b/206441366): Add instant app support
@RequireNotInstantApp(reason = "Instant Apps cannot run Enterprise Tests")
annotation class EnsureHasProfileOwner(val onUser: UserType = INSTRUMENTED_USER,
                                       val key: String = DEFAULT_KEY,
                                       val dpc: Query = Query(),
                                       val isPrimary: Boolean = false,
                                       val useParentInstance: Boolean = false,
                                       val affiliationIds: Array<String> = [],
                                       val priority: Int = DO_PO_PRIORITY)

const val DEFAULT_KEY = "profileOwner"

/**
 * Return an instance of the generated class that conforms to the specification of
 * [EnsureHasProfileOwner]. See [AutoAnnotation].
 */
fun ensureHasProfileOwner(): EnsureHasProfileOwner {
    return ensureHasProfileOwner(query())
}

@AutoAnnotation
private fun ensureHasProfileOwner(dpc: Query): EnsureHasProfileOwner {
    return AutoAnnotation_EnsureHasProfileOwnerKt_ensureHasProfileOwner(dpc)
}

/**
 * A workaround to create an [AutoAnnotation] of [EnsureHasProfileOwner]. [AutoAnnotation]
 * cannot set default values for fields of type Annotation, hence we create an object of [Query]
 * explicitly to pass as the default value of the [dpc] field.
 */
private fun query(): Query {
    return HarrierRule::class.java.getAnnotation<Query>(Query::class.java)
}
