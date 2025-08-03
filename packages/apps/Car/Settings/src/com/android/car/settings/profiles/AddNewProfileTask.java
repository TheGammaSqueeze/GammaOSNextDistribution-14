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

package com.android.car.settings.profiles;

import android.app.ActivityManager;
import android.car.user.CarUserManager;
import android.car.user.UserCreationResult;
import android.car.user.UserStartRequest;
import android.car.user.UserStopRequest;
import android.car.util.concurrent.AsyncFuture;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.AsyncTask;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.car.internal.user.UserHelper;
import com.android.car.settings.common.Logger;

import java.util.concurrent.ExecutionException;

/**
 * Task to add a new profile to the device
 */
public class AddNewProfileTask extends AsyncTask<String, Void, UserInfo> {
    private static final Logger LOG = new Logger(AddNewProfileTask.class);

    private final Context mContext;
    private final CarUserManager mCarUserManager;
    private final AddNewProfileListener mAddNewProfileListener;
    private final UserManager mUserManager;

    public AddNewProfileTask(Context context, CarUserManager carUserManager,
            AddNewProfileListener addNewProfileListener) {
        mContext = context;
        mCarUserManager = carUserManager;
        mAddNewProfileListener = addNewProfileListener;
        mUserManager = context.getSystemService(UserManager.class);
    }

    @Override
    protected UserInfo doInBackground(String... profileNames) {
        AsyncFuture<UserCreationResult> future = mCarUserManager.createUser(profileNames[0],
                /* flags= */ 0);
        try {
            UserCreationResult result = future.get();
            if (result.isSuccess()) {
                UserInfo user = mUserManager.getUserInfo(result.getUser().getIdentifier());
                if (user != null) {
                    UserHelper.setDefaultNonAdminRestrictions(mContext, user.getUserHandle(),
                            /* enable= */ true);
                    UserHelper.assignDefaultIcon(mContext, user.getUserHandle());
                } else {
                    LOG.wtf("Inconsistent state: successful future with null profile - "
                            + result.toString());
                }
                return user;
            }
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.e("Error creating new profile: ", e);
        }
        return null;
    }

    @Override
    protected void onPreExecute() { }

    @Override
    protected void onPostExecute(UserInfo user) {
        if (user != null) {
            mAddNewProfileListener.onProfileAddedSuccess();
            UserHandle currentUser = mContext.getUser();

            if (currentUser.getIdentifier() == ActivityManager.getCurrentUser()) {
                mCarUserManager.switchUser(user.id);
            } else {
                // For passengers we need to call stop and start
                try {
                    mCarUserManager.stopUser(
                            new UserStopRequest.Builder(currentUser).setForce().build(),
                            mContext.getMainExecutor(),
                            result -> {
                                LOG.i("Stop user result: " + result.toString());
                                if (result.isSuccess()) {
                                    startUser(user);
                                }
                            });
                } catch (Exception e) {
                    LOG.e("Exception stopping user " + currentUser, e);
                }
            }
        } else {
            mAddNewProfileListener.onProfileAddedFailure();
        }
    }

    private void startUser(UserInfo user) {
        int displayId = mContext.getDisplayId();

        try {
            mCarUserManager.startUser(
                    new UserStartRequest.Builder(user.getUserHandle())
                            .setDisplayId(displayId)
                            .build(),
                    Runnable::run,
                    result -> LOG.i("Start user result: " + result.toString()));
        } catch (Exception e) {
            LOG.e("Exception starting user " + user.id, e);
        }
    }

    /**
     * Interface for getting notified when AddNewProfileTask has been completed.
     */
    public interface AddNewProfileListener {
        /**
         * Invoked in AddNewProfileTask.onPostExecute after the profile has been created
         * successfully.
         */
        void onProfileAddedSuccess();

        /**
         * Invoked in AddNewProfileTask.onPostExecute if new profile creation failed.
         */
        void onProfileAddedFailure();
    }
}
