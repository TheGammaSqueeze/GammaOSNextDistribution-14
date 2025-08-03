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

/** Interface Helper class for Facet Bar functional tests */
public interface IAutoFacetBarHelper extends IAppHelper {
    enum FACET_BAR {
        HOME("HOME"),
        PHONE("PHONE"),
        APP_GRID("APP_GRID"),
        HVAC("HVAC"),
        NOTIFICATION("NOTIFICATION");

        private String mFacetIcon;

        FACET_BAR(String mFacetIcon) {
            this.mFacetIcon = mFacetIcon;
        }

        public String getFacetIcon() {
            return mFacetIcon;
        }

        public void setFacetIcon(String mFacetIcon) {
            this.mFacetIcon = mFacetIcon;
        }
    };

    enum VERIFY_OPEN_APP {
        HOME("HOME"),
        PHONE("PHONE"),
        APP_GRID("APP_GRID"),
        HVAC("HVAC"),
        NOTIFICATION("NOTIFICATION");

        private String mAppResource;

        VERIFY_OPEN_APP(String mAppResource) {
            this.mAppResource = mAppResource;
        }

        public String getAppResourceForVerification() {
            return mAppResource;
        }

        public void setAppResourceForVerification(String mAppResource) {
            this.mAppResource = mAppResource;
        }
    };

    /**
     * Setup expectation: None.
     *
     * <p>This method is open app using facet bar.
     */
    void clickOnFacetIcon(FACET_BAR facetIcon);

    /**
     * Setup expectation: None.
     *
     * <p>This method is verify if the app is opened.
     */
    boolean isAppInForeground(VERIFY_OPEN_APP appResource);

    /**
     * Setup expectation: None.
     *
     * <p>This method opens the homescreen.
     */
    void goToHomeScreen();
}
