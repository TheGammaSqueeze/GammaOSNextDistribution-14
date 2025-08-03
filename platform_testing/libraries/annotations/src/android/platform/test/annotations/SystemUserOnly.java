/*
 * Copyright (C) 2019 The Android Open Source Project
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
package android.platform.test.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks that the test is valid only when running against system user.
 *
 * <p>Note that this annotation is only used to skip tests from running when TradeFed
 * module-parameter {@code secondary_user} is applied (and consequently also for {@code atest
 * testName --user-type secondary_user}).
 *
 * <p>Tests that are run in secondary user for any other reason will be unaffected by this parameter
 * and will therefore not be skipped.
 *
 * <p>For example, tests annotated with this will <b>not</b> be skipped if:
 *
 * <ul>
 *   <li>you manually switch to a secondary user prior to running the test
 *   <li>the device is running in Headless System User Mode (HSUM)
 * </ul>
 *
 * <p>Because some devices (such as HSUM devices) do run their tests in secondary users, this
 * annotation should be avoided; instead, tests should handle their underlying assumptions
 * themselves, skipping tests based on the properties of the user in which the test is running (such
 * as whether the user is an Admin user).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface SystemUserOnly {
    /** The reason why the test has to be run against system user. */
    String reason() default "";
}
