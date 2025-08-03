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

/** CarSmsMessengerHelperImpl class for Android Auto platform functional tests */
public class CarSmsMessengerHelperImpl extends AbstractStandardAppHelper
        implements IAutoCarSmsMessengerHelper {

    public CarSmsMessengerHelperImpl(Instrumentation instr) {
        super(instr);
    }

    /** {@inheritDoc} */
    @Override
    public String getPackage() {
        return getPackageFromConfig(AutomotiveConfigConstants.SMS_PACKAGE);
    }

    /** {@inheritDoc} */
    @Override
    public String getLauncherName() {
        throw new UnsupportedOperationException("Operation not supported.");
    }

    /** {@inheritDoc} */
    @Override
    public void dismissInitialDialogs() {
        // Nothing to dismiss
    }

    /** {@inheritDoc} */
    @Override
    public void open() {
        getSpectatioUiUtil().pressHome();
        getSpectatioUiUtil().wait1Second();
        getSpectatioUiUtil()
                .executeShellCommand(
                        getCommandFromConfig(AutomotiveConfigConstants.OPEN_SMS_ACTIVITY_COMMAND));
        getSpectatioUiUtil().wait1Second();
    }

    @Override
    public boolean isSmsBluetoothErrorDisplayed() {
        BySelector bluetoothErrorSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.SMS_BLUETOOTH_ERROR);
        return getSpectatioUiUtil().hasUiElement(bluetoothErrorSelector);
    }

    @Override
    public boolean isUnreadSmsDisplayed() {
        BySelector unreadBadgeSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.SMS_UNREAD_BADGE);
        return getSpectatioUiUtil().hasUiElement(unreadBadgeSelector);
    }

    @Override
    public boolean isSmsPreviewDisplayed(String text) {
        BySelector smsTextSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.SMS_PREVIEW_TEXT);
        UiObject2 smsPreviewTextObj = getSpectatioUiUtil().findUiObject(smsTextSelector);
        getSpectatioUiUtil()
                .validateUiObject(smsPreviewTextObj, AutomotiveConfigConstants.SMS_PREVIEW_TEXT);
        String smsPreviewText = smsPreviewTextObj.getText();
        return smsPreviewText.contains(text);
    }

    @Override
    public boolean isSmsTimeStampDisplayed() {
        BySelector timeStampSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.SMS_PREVIEW_TIMESTAMP);
        return getSpectatioUiUtil().hasUiElement(timeStampSelector);
    }

    @Override
    public boolean isNoMessagesDisplayed() {
        BySelector noMessagesSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.SMS_EMPTY_MESSAGE);
        return getSpectatioUiUtil().hasUiElement(noMessagesSelector);
    }

    @Override
    public void tapToReadAloud() {
        BySelector smsTextSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.TAP_TO_READ_ALOUD);
        UiObject2 tapToReadAloudObj = getSpectatioUiUtil().findUiObject(smsTextSelector);
        getSpectatioUiUtil()
                .validateUiObject(tapToReadAloudObj, AutomotiveConfigConstants.TAP_TO_READ_ALOUD);
        getSpectatioUiUtil().clickAndWait(tapToReadAloudObj);
    }

    @Override
    public boolean isAssistantSMSTranscriptionPlateDisplayed() {
        BySelector assistantSMSPlateSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.ASSISTANT_SMS_TRANSCRIPTION_PLATE);
        return (getSpectatioUiUtil().hasUiElement(assistantSMSPlateSelector));
    }
}
