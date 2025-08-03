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

package android.car;

import android.os.UserHandle;
import android.car.user.UserCreationRequest;
import android.car.user.UserCreationResult;
import android.car.user.UserIdentificationAssociationResponse;
import android.car.user.UserLifecycleEventFilter;
import android.car.user.UserRemovalResult;
import android.car.user.UserStartRequest;
import android.car.user.UserStartResponse;
import android.car.user.UserStopRequest;
import android.car.user.UserStopResponse;
import android.car.user.UserSwitchResult;
import android.car.ICarResultReceiver;
import android.car.util.concurrent.AndroidFuture;

import com.android.car.internal.ResultCallbackImpl;

/** @hide */
interface ICarUserService {
    void switchUser(int targetUserId, int timeoutMs,
      in ResultCallbackImpl<UserSwitchResult> callback, boolean ignoreUxRestriction);
    void logoutUser(int timeoutMs, in ResultCallbackImpl<UserSwitchResult> callback);
    void setUserSwitchUiCallback(in ICarResultReceiver callback);
    void createUser(in UserCreationRequest userCreationRequest, int timeoutMs,
          in ResultCallbackImpl<UserCreationResult> callback);
    void startUser(in UserStartRequest request, in ResultCallbackImpl<UserStartResponse> callback);
    void stopUser(in UserStopRequest request, in ResultCallbackImpl<UserStopResponse> callback);
    void removeUser(int userId, in ResultCallbackImpl<UserRemovalResult> callback);
    void setLifecycleListenerForApp(String pkgName, in UserLifecycleEventFilter filter,
      in ICarResultReceiver listener);
    void resetLifecycleListenerForApp(in ICarResultReceiver listener);
    UserIdentificationAssociationResponse getUserIdentificationAssociation(in int[] types);
    void setUserIdentificationAssociation(int timeoutMs, in int[] types, in int[] values,
      in AndroidFuture<UserIdentificationAssociationResponse> result);
    boolean isUserHalUserAssociationSupported();
}
