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

import static com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner.DO_PO_PRIORITY;

import com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence;
import com.android.bedstead.harrier.annotations.RequireNotInstantApp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark that a test requires that the given admin delegates the given scope to a test app.
 *
 * <p>You should use {@code DeviceState} to ensure that the device enters
 * the correct state for the method. You can use {@code Devicestate#delegate()} to interact with
 * the delegate.
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
// TODO(b/206441366): Add instant app support
@RequireNotInstantApp(reason = "Instant Apps cannot run Enterprise Tests")
// TODO(b/219750042): If we leave over appops and permissions then the delegate will have them
public @interface EnsureHasDelegate {

    /** The default key used for the testapp installed as delegate */
    String DELEGATE_KEY = "delegate";

    // TODO(276740719): Add support for customisable delegates
//    /**
//     * The key used to identify this delegate.
//     *
//     * <p>This can be used with {@link AdditionalQueryParameters} to modify the requirements for
//     * the delegate. */
//    String key() default DELEGATE_KEY;

    int ENSURE_HAS_DELEGATE_PRIORITY = DO_PO_PRIORITY + 1; // Should run after setting DO/PO

    enum AdminType {
        DEVICE_OWNER,
        PROFILE_OWNER,
        PRIMARY
    }

    // TODO(276740719): Add support for querying for the delegate

    /**
     * The admin that should delegate this scope.
     *
     * <p>If this is set to {@link AdminType#PRIMARY} and {@link #isPrimary()} is true, then the
     * delegate will replace the primary dpc as primary without error.
     */
    AdminType admin();

    /** The scope being delegated. */
    String[] scopes();

    /**
     * Whether this delegate should be returned by calls to {@code DeviceState#dpc()}.
     *
     * <p>Only one policy manager per test should be marked as primary.
     */
    boolean isPrimary() default false;

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
    int priority() default ENSURE_HAS_DELEGATE_PRIORITY;
}
