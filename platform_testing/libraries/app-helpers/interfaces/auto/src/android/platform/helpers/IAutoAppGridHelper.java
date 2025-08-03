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

/** Interface Helper class for App Grid functional tests */
public interface IAutoAppGridHelper extends IAppHelper {
    /**
     * Setup expectations: In App grid.
     *
     * <p>Check if device is currently at the beginning of app grid.
     */
    boolean isAtBeginning();

    /**
     * Setup expectations: In App grid.
     *
     * <p>Check if device is currently at the end of app grid.
     */
    boolean isAtEnd();

    /**
     * Setup expectations: In App grid.
     *
     * <p>Scroll backward on appgrid app.
     */
    boolean scrollBackward();

    /**
     * Setup expectations: In App grid.
     *
     * <p>Scroll to the beginning of the app grid.
     */
    void scrollToBeginning();

    /**
     * Setup expectations: In App grid.
     *
     * <p>Scroll forward on appgrid app.
     */
    boolean scrollForward();

    /**
     * Setup expectations: In App grid.
     *
     * <p>Find and open an application.
     */
    void openApp(String appName);

    /**
     * Setup expectations: Blocking Message displayed.
     *
     * <p>Get the Screen Blocking Message when in Driving Mode.
     *
     * @param appName is name of the application
     */
    String getScreenBlockingMessage(String appName);

    /**
     * Setup expectations: package is in foreground
     *
     * <p>Check the package is in foreground
     *
     * @param packageName is package of the application
     */
    boolean checkPackageInForeground(String packageName);

    /**
     * Setup expectations: Go To Home.
     *
     * <p>Press home button.
     */
    void goToHomePage();
}
