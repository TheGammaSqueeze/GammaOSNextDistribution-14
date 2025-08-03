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
import android.util.Log;

import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;

/** Helper class for facet bar related functions */
public class FacetBarHelperImpl extends AbstractStandardAppHelper implements IAutoFacetBarHelper {

    private static final String LOG_TAG = FacetBarHelperImpl.class.getSimpleName();

    public FacetBarHelperImpl(Instrumentation instr) {
        super(instr);

        // Set Facet Icons
        FACET_BAR.HOME.setFacetIcon(AutomotiveConfigConstants.HOME_FACET_BUTTON);
        FACET_BAR.PHONE.setFacetIcon(AutomotiveConfigConstants.PHONE_FACET_BUTTON);
        FACET_BAR.APP_GRID.setFacetIcon(AutomotiveConfigConstants.APP_GRID_FACET_BUTTON);
        FACET_BAR.HVAC.setFacetIcon(AutomotiveConfigConstants.HVAC_FACET_BUTTON);
        FACET_BAR.NOTIFICATION.setFacetIcon(AutomotiveConfigConstants.NOTIFICATION_FACET_BUTTON);

        // Set App Verification
        VERIFY_OPEN_APP.HOME.setAppResourceForVerification(
                AutomotiveConfigConstants.HOME_BOTTOM_CARD);
        VERIFY_OPEN_APP.PHONE.setAppResourceForVerification(AutomotiveConfigConstants.DIALER_VIEW);
        VERIFY_OPEN_APP.APP_GRID.setAppResourceForVerification(
                AutomotiveConfigConstants.APP_GRID_VIEW_ID);
        VERIFY_OPEN_APP.HVAC.setAppResourceForVerification(AutomotiveConfigConstants.HVAC_PANEL);
        VERIFY_OPEN_APP.NOTIFICATION.setAppResourceForVerification(
                AutomotiveConfigConstants.NOTIFICATION_VIEW);
    }

    /** {@inheritDoc} */
    @Override
    public String getPackage() {
        return "";
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

    @Override
    public boolean isAppInForeground(VERIFY_OPEN_APP appResource) {
        BySelector appSelector =
                getUiElementFromConfig(appResource.getAppResourceForVerification());
        return getSpectatioUiUtil().hasUiElement(appSelector);
    }

    @Override
    public void clickOnFacetIcon(FACET_BAR facetIcon) {
        Log.i(LOG_TAG, String.format("facetIcon: %s", facetIcon.getFacetIcon()));
        BySelector appFacetButtonSelector = getUiElementFromConfig(facetIcon.getFacetIcon());
        UiObject2 appFacetButton = getSpectatioUiUtil().findUiObject(appFacetButtonSelector);
        getSpectatioUiUtil()
                .validateUiObject(appFacetButton, String.format(facetIcon.getFacetIcon()));
        getSpectatioUiUtil().clickAndWait(appFacetButton);
        getSpectatioUiUtil().wait1Second();
    }

    @Override
    public void goToHomeScreen() {
        getSpectatioUiUtil().pressHome();
        getSpectatioUiUtil().waitForIdle();
    }
}
