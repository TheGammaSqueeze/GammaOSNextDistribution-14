/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.app.role;

import android.app.role.IOnRoleHoldersChangedListener;
import android.os.Bundle;
import android.os.RemoteCallback;

/**
 * @hide
 */
interface IRoleManager {

    boolean isRoleAvailableAsUser(in String roleName, int userId);

    boolean isRoleHeldAsUser(in String roleName, in String packageName, int userId);

    List<String> getRoleHoldersAsUser(in String roleName, int userId);

    void addRoleHolderAsUser(in String roleName, in String packageName, int flags, int userId,
            in RemoteCallback callback);

    void removeRoleHolderAsUser(in String roleName, in String packageName, int flags, int userId,
            in RemoteCallback callback);

    void clearRoleHoldersAsUser(in String roleName, int flags, int userId,
            in RemoteCallback callback);

    String getDefaultApplicationAsUser(in String roleName, int userId);

    void setDefaultApplicationAsUser(in String roleName, in String packageName, int flags,
	    int userId, in RemoteCallback callback);

    void addOnRoleHoldersChangedListenerAsUser(IOnRoleHoldersChangedListener listener, int userId);

    void removeOnRoleHoldersChangedListenerAsUser(IOnRoleHoldersChangedListener listener,
            int userId);

    boolean isBypassingRoleQualification();

    void setBypassingRoleQualification(boolean bypassRoleQualification);

    boolean isRoleFallbackEnabledAsUser(in String roleName, int userId);

    void setRoleFallbackEnabledAsUser(in String roleName, boolean fallbackEnabled, int userId);

    void setRoleNamesFromControllerAsUser(in List<String> roleNames, int userId);

    boolean addRoleHolderFromControllerAsUser(in String roleName, in String packageName,
            int userId);

    boolean removeRoleHolderFromControllerAsUser(in String roleName, in String packageName,
            int userId);

    List<String> getHeldRolesFromControllerAsUser(in String packageName, int userId);

    String getBrowserRoleHolder(int userId);

    boolean setBrowserRoleHolder(String packageName, int userId);

    String getSmsRoleHolder(int userId);

    String getEmergencyRoleHolder(int userId);

    boolean isRoleVisibleAsUser(in String roleName, int userId);

    boolean isApplicationVisibleForRoleAsUser(in String roleName, in String packageName,
            int userId);
}
