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

package android.platform.helpers;

import android.app.Instrumentation;
import android.platform.helpers.ScrollUtility.ScrollActions;
import android.platform.helpers.ScrollUtility.ScrollDirection;

import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;

public class ContactDetailsHelperImpl extends AbstractStandardAppHelper
        implements IAutoDialContactDetailsHelper {
    private static final String LOG_TAG = DialHelperImpl.class.getSimpleName();

    private ScrollUtility mScrollUtility;
    private ScrollActions mScrollAction;
    private BySelector mBackwardButtonSelector;
    private BySelector mForwardButtonSelector;
    private BySelector mScrollableElementSelector;
    private ScrollDirection mScrollDirection;

    public ContactDetailsHelperImpl(Instrumentation instr) {
        super(instr);
        mScrollUtility = ScrollUtility.getInstance(getSpectatioUiUtil());
        mScrollAction =
                ScrollActions.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.CONTACT_DETAILS_SCROLL_ACTION));
        mBackwardButtonSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.CONTACT_DETAILS_SCROLL_BACKWARD);
        mForwardButtonSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.CONTACT_DETAILS_SCROLL_FORWARD);
        mScrollableElementSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.CONTACT_DETAILS_SCROLL_ELEMENT);
        mScrollDirection =
                ScrollDirection.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.CONTACT_DETAILS_SCROLL_DIRECTION));
    }

    /** {@inheritDoc} */
    @Override
    public void dismissInitialDialogs() {
        UiObject2 dialogBox =
                getSpectatioUiUtil()
                        .findUiObject(
                                getUiElementFromConfig(
                                        AutomotiveConfigConstants.CONTACTS_DIALOG_BOX));
        if (dialogBox != null) {
            executeWorkflow(AutomotiveConfigConstants.APPS_INITIAL_DIALOGS);
        }
    }

    /** {@inheritDoc} */
    @Override
    public String getPackage() {
        return getPackageFromConfig(AutomotiveConfigConstants.DIAL_PACKAGE);
    }

    /** {@inheritDoc} */
    public void open() {
        getSpectatioUiUtil().pressHome();
        getSpectatioUiUtil().wait1Second();
        getSpectatioUiUtil()
                .executeShellCommand(
                        getCommandFromConfig(
                                AutomotiveConfigConstants.OPEN_PHONE_ACTIVITY_COMMAND));
        getSpectatioUiUtil().wait1Second();
    }

    /** {@inheritDoc} */
    @Override
    public String getLauncherName() {
        throw new UnsupportedOperationException("Operation not supported.");
    }

    /** {@inheritDoc} */
    public void addRemoveFavoriteContact() {
        BySelector favoriteButtonSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.ADD_CONTACT_TO_FAVORITE);
        UiObject2 favoriteButton = getSpectatioUiUtil().findUiObject(favoriteButtonSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        favoriteButton, AutomotiveConfigConstants.ADD_CONTACT_TO_FAVORITE);
        getSpectatioUiUtil().clickAndWait(favoriteButton);
    }

    private UiObject2 getNumberByContactType(String type) {
        UiObject2 contactByType =
                mScrollUtility.scrollAndFindUiObject(
                        mScrollAction,
                        mScrollDirection,
                        mForwardButtonSelector,
                        mBackwardButtonSelector,
                        mScrollableElementSelector,
                        getUiElementFromConfig(type),
                        String.format("scroll to find %s", type));
        getSpectatioUiUtil().validateUiObject(contactByType, String.format("Given type %s", type));
            return contactByType;
    }

    /** {@inheritDoc} */
    public void makeCallFromDetailsPageByType(ContactType contactType) {
        UiObject2 number = null;
        switch (contactType) {
            case HOME:
                number = getNumberByContactType(AutomotiveConfigConstants.CONTACT_TYPE_HOME);
                break;
            case WORK:
                number = getNumberByContactType(AutomotiveConfigConstants.CONTACT_TYPE_WORK);
                break;
            case MOBILE:
                number = getNumberByContactType(AutomotiveConfigConstants.CONTACT_TYPE_MOBILE);
                break;
            default:
                number = getSpectatioUiUtil().findUiObject("undefined");
        }
        getSpectatioUiUtil()
                .validateUiObject(number, String.format("Contact Type %s", contactType));
        getSpectatioUiUtil().clickAndWait(number);
    }

    /** {@inheritDoc} */
    public void closeDetailsPage() {
        // count is used to avoid infinite loop in case someone invokes
        // after exiting settings application
        int count = 5;
        while (count > 0
                && getSpectatioUiUtil()
                                .findUiObject(
                                        getUiElementFromConfig(
                                                AutomotiveConfigConstants.DIAL_PAD_MENU))
                        == null) {
            getSpectatioUiUtil().pressBack();
            getSpectatioUiUtil().wait5Seconds();
            count--;
        }
    }
}
