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

import static com.android.bedstead.nene.utils.Tags.USES_NOTIFICATIONS;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to indicate that a test interacts with notifications.
 *
 * <p>This will mean that the test will not run on low ram devices (as they do not support usage
 * of {@code NotificationListener}).
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@RequireNotLowRamDevice(
        reason = "This test uses NotificationListener which is not supported on low ram devices")
@TestTag(USES_NOTIFICATIONS)
public @interface NotificationsTest {
}
