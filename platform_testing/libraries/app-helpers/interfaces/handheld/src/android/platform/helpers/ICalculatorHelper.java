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

package android.platform.helpers;

/** An App Helper interface for Calculator. */
public interface ICalculatorHelper extends IAppHelper {
    /*
     * Setup expectations: Calculator is open and visible.
     */
    void longPressDelete();

    /*
     * Setup expectations: Calculator is open and visible.
     *
     * @param digits Digits (0-9) in a form of a string.
     * @param delayBetweenKeyPresses delay between key presses in milliseconds
     */
    void typeDigits(String digits, long delayBetweenKeyPresses);

    /*
     * Setup expectations: Calculator is open and visible.
     */
    void selectEqual();

    /*
     * Setup expectations: Calculator is open and visible.
     */
    void selectMultiply();

    /*
     * Setup expectations: Calculator is open and visible.
     */
    void selectSubtract();

    /*
     * Setup expectations: Calculator is open and visible.
     */
    void selectAdd();

    /*
     * Setup expectations: Calculator is open and visible.
     */
    void selectDivide();
}
