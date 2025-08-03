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

import static com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence.LATE;

import com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence;
import com.android.bedstead.harrier.annotations.EnsureTestAppInstalled;
import com.android.queryable.annotations.Query;
import com.android.queryable.annotations.StringQuery;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark that a test is testing a most restrictive coexistence policy.
 *
 * <p>Tests can use {@code sDeviceState.testApp(DPC_1} and {@code sDeviceState.testApp(DPC_2} to
 * get the different test apps to set the policy.
 *
 * <p>You can use {@code DeviceState} to ensure that the device enters the correct state for the
 * method.
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@EnsureTestAppInstalled(key = MostRestrictiveCoexistenceTest.DPC_1,
        query = @Query(packageName = @StringQuery(
                isEqualTo = "com.android.bedstead.testapp.NotEmptyTestApp")))
@EnsureTestAppInstalled(key = MostRestrictiveCoexistenceTest.DPC_2,
        query = @Query(packageName = @StringQuery(
                isEqualTo = "com.android.bedstead.testapp.TestOnlyDeviceAdminTestApp")))
// TODO: We need to expose the permission in the annotation and apply it in devicestate...
public @interface MostRestrictiveCoexistenceTest {

    String DPC_1 = "dpc1";
    String DPC_2 = "dpc2";

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
    int priority() default LATE;
}
