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

import android.app.Instrumentation;

import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;

/** Helper class for general UI actions */
public class PhoneHelperImpl extends AbstractStandardAppHelper implements IAutoPhoneHelper {
    public PhoneHelperImpl(Instrumentation instr) {
        super(instr);
    }

    /** {@inheritDoc} */
    @Override
    public void pressCallButton() {
        pressUIElement(AutomotiveConfigConstants.MOBILE_CALL_BUTTON);
    }

    /** {@inheritDoc} */
    @Override
    public void pressDialpadIcon() {
        pressUIElement(AutomotiveConfigConstants.MOBILE_DIAL_PAD_ICON);
    }

    /** {@inheritDoc} */
    @Override
    public void pressPhoneIcon() {
        pressUIElement(AutomotiveConfigConstants.MOBILE_PHONE_ICON);
    }

    /** {@inheritDoc} */
    @Override
    public void dialNumberOnDialpad(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("No phone number provided");
        }
        char[] array = phoneNumber.toCharArray();
        for (char ch : array) {
            UiObject2 numberButton =
                    getSpectatioUiUtil()
                            .findUiObject(getUiElementFromConfig(Character.toString(ch)));
            if (numberButton == null) {
                numberButton = getSpectatioUiUtil().findUiObject(Character.toString(ch));
            }
            getSpectatioUiUtil()
                    .validateUiObject(
                            numberButton, String.format("Number %s", Character.toString(ch)));
            numberButton.click();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void enterNumberOnDialpad(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("No phone number provided");
        }

        BySelector dialPadInputSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.MOBILE_DIALPAD_INPUT);

        UiObject2 dialPadInput = getSpectatioUiUtil().findUiObject(dialPadInputSelector);
        getSpectatioUiUtil()
                .validateUiObject(dialPadInput, AutomotiveConfigConstants.MOBILE_DIALPAD_INPUT);

        dialPadInput.setText(phoneNumber);
    }

    /** General UI element press method */
    private void pressUIElement(String selectorID) {
        BySelector searchResultSelector = getUiElementFromConfig(selectorID);

        UiObject2 searchResult = getSpectatioUiUtil().findUiObject(searchResultSelector);

        getSpectatioUiUtil().validateUiObject(searchResult, selectorID);

        getSpectatioUiUtil().clickAndWait(searchResult);
    }

    /** {@inheritDoc} */
    @Override
    public String getLauncherName() {
        throw new UnsupportedOperationException("Operation not supported.");
    }

    /** {@inheritDoc} */
    @Override
    public String getPackage() {
        return getPackageFromConfig(AutomotiveConfigConstants.PHONE_DEVICE_PACKAGE);
    }

    /** {@inheritDoc} */
    @Override
    public void dismissInitialDialogs() {
        // Nothing to dismiss
    }
}
