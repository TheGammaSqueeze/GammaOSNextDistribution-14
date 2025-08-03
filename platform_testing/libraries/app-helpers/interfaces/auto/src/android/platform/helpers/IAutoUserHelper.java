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

public interface IAutoUserHelper extends IAppHelper {

    /**
     * Setup expectation: Profiles and Accounts setting is open.
     *
     * <p>This method is to add an a new user.
     */
    void addUser();

    /**
     * Setup expectation: Profiles and Accounts setting is open.
     *
     * <p>This method is to add an a new user.
     */
    void addUserQuickSettings(String userName);
    /**
     * Setup expectation: Profiles and Accounts setting is open.
     *
     * <p>This method is to open permissions of an existing user.
     */
    void openPermissionsPage(String user);

    /**
     * Setup expectation: Profiles and Accounts setting is open.
     *
     * <p>This method is to delete an existing user.
     */
    void deleteUser(String user);

    /**
     * Setup expectation: Profiles and Accounts setting is open.
     *
     * <p>This method is to delete user's own profile.
     */
    void deleteCurrentUser();

    /**
     * Setup expectation: Profiles and Accounts setting is open.
     *
     * <p>This method is to check if user already exists.
     */
    boolean isUserPresent(String userName);

    /**
     * Setup expectation: Profiles and Accounts setting is open.
     *
     * <p>This method is to switch between existing users.
     */
    void switchUser(String userFrom, String userTo);

    /**
     * Setup expectation: Profiles and Accounts setting is open.
     *
     * <p>This method is to make an existing user admin.
     */
    void makeUserAdmin(String user);

    /**
     * Setup expectation: Profiles and Accounts setting is open.
     *
     * <p>This method is to check if the new user created is admin.
     */
    boolean isNewUserAnAdmin(String user);
    /**
     * Setup expectation: Profiles and Accounts setting is open.
     *
     * <p>This method is used to check whether Add Profile button is Visible ?.
     */
    boolean isVisibleAddProfile();
    /**
     * Setup expectation: Profiles and Accounts setting is open.
     *
     * <p>This method is used to check whether a toggle button is on / off mode.
     */
    boolean isToggleOn(String buttonText);

    /**
     * Setup expectation: Profiles and Accounts setting is open.
     *
     * <p>This method is used to toggle the switch .
     */
    boolean toggle(String buttonText);
}

