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

import static com.android.bedstead.harrier.annotations.enterprise.EnsureHasDevicePolicyManagerRoleHolder.ENSURE_HAS_DEVICE_POLICY_MANAGER_ROLE_HOLDER_PRIORITY;

import com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence;
import com.android.bedstead.harrier.annotations.EnsureTestAppInstalled;
import com.android.queryable.annotations.IntegerQuery;
import com.android.queryable.annotations.Query;
import com.android.queryable.annotations.StringQuery;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark that a test is testing a most important coexistence policy.
 *
 * <p>Tests can use {@code sDeviceState.testApp(MORE_IMPORTANT} and
 * {@code sDeviceState.testApp(LESS_IMPORTANT} to get the different test apps to set the policy.
 *
 * <p>You can use {@code DeviceState} to ensure that the device enters the correct state for the
 * method.
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@EnsureHasDeviceOwner // This will be the MORE_IMPORTANT, setup by DeviceState
@EnsureTestAppInstalled(key = MostImportantCoexistenceTest.LESS_IMPORTANT,
        query = @Query(packageName = @StringQuery(
                isEqualTo = "com.android.bedstead.testapp.NotEmptyTestApp"),
                targetSdkVersion = @IntegerQuery(isGreaterThanOrEqualTo = 34)))
public @interface MostImportantCoexistenceTest {

    String MORE_IMPORTANT = "more_important";
    String LESS_IMPORTANT = "less_important";

    /**
     * The policy being tested.
     *
     * <p>This is only valid if the policy can be applied by a permission.
     */
    Class<?> policy();

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
    int priority() default ENSURE_HAS_DEVICE_POLICY_MANAGER_ROLE_HOLDER_PRIORITY + 1;
}
