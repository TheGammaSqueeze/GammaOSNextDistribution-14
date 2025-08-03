/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.platform.helpers;

/** Helper class for functional tests of Location Settings */
public interface IAutoSettingsLocationHelper extends IAppHelper {

    /**
     * Setup expectations: Location setting is turned on
     *
     * <p>Check if location switch is turned on.
     */
    void toggleLocation(boolean onOff);

    /**
     * Setup expectations: Select the Location Access
     *
     * <p>Selecting the Location Access option.
     */
    void locationAccess();

    /**
     * Setup expectations: Location setting is turned on
     *
     * <p>Check if location switch is turned on.
     */
    boolean isLocationOn();

    /**
     * Setup expectations: maps widget is open
     *
     * <p>Check if maps widget is present.
     */
    boolean hasMapsWidget();

    /**
     * Setup expectations: verifying recently accessed options
     *
     * <p>Check if recently accessed options is present
     */
    boolean hasRecentlyAccessed();
}
