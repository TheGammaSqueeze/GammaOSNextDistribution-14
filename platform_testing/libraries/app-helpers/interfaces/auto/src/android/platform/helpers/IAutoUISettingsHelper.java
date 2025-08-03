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

/** Helper class for functional tests of App Info settings */
public interface IAutoUISettingsHelper extends IAppHelper {

    /**
     * Setup expectation: Open previous page
     *
     * <p>Press back button on screen
     */
    void pressBackButton();

    /**
     * Setup expectation: Option Menu is open.
     *
     * <p>Open Settings UI Options
     *
     * @param targetConstant - Constant value of the target.
     */
    void openUIOptions(String targetConstant);

    /**
     * Setup expectation: Verify if UI element is present
     *
     * <p>To Verify if UI element is present
     *
     * @param element - element name
     */
    boolean hasUIElement(String element);
}
