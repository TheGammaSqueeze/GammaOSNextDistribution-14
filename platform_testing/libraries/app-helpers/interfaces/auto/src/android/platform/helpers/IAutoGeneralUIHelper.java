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

/** Interface Helper class for generalized UI element manipulation */
public interface IAutoGeneralUIHelper extends IAppHelper {

    /**
     * Setup expectation: none.
     *
     * <p>A general search-and-click method that attempts to click on a UIElement that has a
     * particular text field.
     *
     * <p>This method is a "safe" call, meaning it will catch any exceptions from the attempt.
     *
     * @param text The text of the element to be clicked.
     */
    void clickElementWithText(String text);

    /**
     * Setup expectation: none.
     *
     * <p>Returns whether an element with the given text exists.
     *
     * <p>This method is a "safe" call, meaning it will catch any exceptions from the attempt.
     *
     * @param text The text of the element to be found.
     * @return - Whether a UI Element with the given text exists.
     */
    boolean hasElementWithText(String text);
}
