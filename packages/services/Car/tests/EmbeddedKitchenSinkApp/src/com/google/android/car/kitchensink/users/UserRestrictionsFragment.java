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
package com.google.android.car.kitchensink.users;

import android.annotation.Nullable;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manipulate users in various ways
 */
public final class UserRestrictionsFragment extends Fragment {

    private static final String TAG = UserRestrictionsFragment.class.getSimpleName();

    private static final List<String> CONFIGURABLE_USER_RESTRICTIONS =
            Arrays.asList(
                    UserManager.DISALLOW_ADD_USER,
                    UserManager.DISALLOW_BLUETOOTH,
                    UserManager.DISALLOW_CONFIG_BRIGHTNESS,
                    UserManager.DISALLOW_FACTORY_RESET,
                    UserManager.DISALLOW_INSTALL_APPS,
                    UserManager.DISALLOW_MODIFY_ACCOUNTS,
                    UserManager.DISALLOW_OUTGOING_CALLS,
                    UserManager.DISALLOW_REMOVE_USER,
                    UserManager.DISALLOW_SMS,
                    UserManager.DISALLOW_UNINSTALL_APPS,
                    UserManager.DISALLOW_USER_SWITCH
            );

    static {
        Collections.sort(CONFIGURABLE_USER_RESTRICTIONS);
        Log.d(TAG, "Configurable user restrictions: " + CONFIGURABLE_USER_RESTRICTIONS);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.user_restrictions, container, false);
    }

    public void onViewCreated(View view, Bundle savedInstanceState) {
        ListView userRestrictionsList = view.findViewById(R.id.user_restrictions_list);
        userRestrictionsList.setAdapter(
                new UserRestrictionAdapter(getContext(), createUserRestrictionItems()));

        Button applyButton = view.findViewById(R.id.apply_button);
        applyButton.setOnClickListener(v -> {
            UserRestrictionAdapter adapter =
                    (UserRestrictionAdapter) userRestrictionsList.getAdapter();
            int count = adapter.getCount();
            UserManager userManager = getUserManager();

            // Iterate through all of the user restrictions and set their values
            List<String> restrictions = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                UserRestrictionListItem item = (UserRestrictionListItem) adapter.getItem(i);
                String restriction = item.getKey();
                boolean added = item.isChecked();
                userManager.setUserRestriction(restriction, added);
                if (added) {
                    restrictions.add(restriction);
                }
            }
            toast("%d restrictions (%s) have been set on user %d!", restrictions.size(),
                    restrictions, getContext().getUserId());
        });
    }

    private void toast(String format, Object...args) {
        String msg = String.format(format, args);
        Log.i(TAG, msg);
        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private List<UserRestrictionListItem> createUserRestrictionItems() {
        int userId = getContext().getUser().getIdentifier();
        UserHandle user = UserHandle.of(userId);
        UserManager userManager = getUserManager();

        List<UserRestrictionListItem> list = CONFIGURABLE_USER_RESTRICTIONS.stream()
                .map((key) -> new UserRestrictionListItem(key,
                        userManager.hasBaseUserRestriction(key, user)))
                .collect(Collectors.toList());
        Log.d(TAG, "Current restrictions for user " + userId + ": " + list);

        return list;
    }

    private UserManager getUserManager() {
        return getContext().getSystemService(UserManager.class);
    }
}
