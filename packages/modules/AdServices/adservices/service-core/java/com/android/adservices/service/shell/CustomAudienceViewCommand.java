/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.shell;

import static com.android.adservices.service.shell.AdServicesShellCommandHandler.TAG;

import android.adservices.common.AdTechIdentifier;
import android.util.Log;

import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBCustomAudienceBackgroundFetchData;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONException;

import java.io.PrintWriter;
import java.util.Optional;

/** Command to view custom audiences created in Protected Audience. */
final class CustomAudienceViewCommand extends AbstractShellCommand {

    @VisibleForTesting public static final String CMD = "view";
    public static final String HELP =
            CustomAudienceShellCommandFactory.COMMAND_PREFIX
                    + " "
                    + CMD
                    + " --"
                    + CustomAudienceArgs.OWNER
                    + " <owner> --"
                    + CustomAudienceArgs.BUYER
                    + " <buyer> --"
                    + CustomAudienceArgs.NAME
                    + " <name>"
                    + "\n    View a custom audience. For a CA to appear here, it must be 1) "
                    + "created in a a context where android:debuggable=\"true\" is in the owning "
                    + "app's manifest and 2) system-wide developer options are enabled";

    private final CustomAudienceDao mCustomAudienceDao;
    private final CustomAudienceArgParser mCustomAudienceArgParser;

    CustomAudienceViewCommand(CustomAudienceDao customAudienceDao) {
        mCustomAudienceDao = customAudienceDao;
        mCustomAudienceArgParser =
                new CustomAudienceArgParser(
                        CustomAudienceArgs.OWNER,
                        CustomAudienceArgs.BUYER,
                        CustomAudienceArgs.NAME);
    }

    @Override
    public String getCommandName() {
        return CMD;
    }

    @Override
    public int run(PrintWriter out, PrintWriter err, String[] args) {
        try {
            mCustomAudienceArgParser.parse(args);
        } catch (IllegalArgumentException e) {
            err.printf("Failed to parse arguments: %s\n", e.getMessage());
            Log.e(TAG, "Failed to parse arguments: " + e.getMessage());
            return invalidArgsError(HELP, err, args);
        }

        String owner = mCustomAudienceArgParser.getValue(CustomAudienceArgs.OWNER);
        AdTechIdentifier buyer =
                AdTechIdentifier.fromString(
                        mCustomAudienceArgParser.getValue(CustomAudienceArgs.BUYER));
        String name = mCustomAudienceArgParser.getValue(CustomAudienceArgs.NAME);
        Optional<DBCustomAudience> customAudience =
                queryForDebuggableCustomAudience(owner, buyer, name);
        Optional<DBCustomAudienceBackgroundFetchData> customAudienceBackgroundFetchData =
                queryForDebuggableCustomAudienceBackgroundFetchData(owner, buyer, name);
        try {
            if (customAudience.isPresent() && customAudienceBackgroundFetchData.isPresent()) {
                out.print(
                        CustomAudienceHelper.toJson(
                                customAudience.get(), customAudienceBackgroundFetchData.get()));
            } else {
                out.print("{}");
            }
            return RESULT_OK;
        } catch (JSONException e) {
            err.printf("Failed to generate output: %s\n", e.getMessage());
            Log.e(TAG, "Failed to generate JSON: " + e.getMessage());
            return RESULT_GENERIC_ERROR;
        }
    }

    private Optional<DBCustomAudience> queryForDebuggableCustomAudience(
            String owner, AdTechIdentifier buyer, String name) {
        Log.d(
                TAG,
                String.format(
                        "Querying for CA with owner %s, buyer %s and name %s", owner, buyer, name));
        DBCustomAudience customAudience =
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(owner, buyer, name);
        if (customAudience == null || !customAudience.isDebuggable()) {
            Log.d(
                    TAG,
                    String.format(
                            "No debuggable custom audience found with owner %s, buyer %s and name"
                                    + " %s.",
                            owner, buyer, name));
            return Optional.empty();
        } else {
            Log.d(
                    TAG,
                    String.format(
                            "Debuggable custom audience found with owner %s, buyer %s and name"
                                    + " %s.",
                            owner, buyer, name));
            return Optional.of(customAudience);
        }
    }

    private Optional<DBCustomAudienceBackgroundFetchData>
            queryForDebuggableCustomAudienceBackgroundFetchData(
                    String owner, AdTechIdentifier buyer, String name) {
        Log.d(
                TAG,
                String.format(
                        "Querying for CA background fetch data with owner %s, buyer %s and name %s",
                        owner, buyer, name));
        DBCustomAudienceBackgroundFetchData customAudienceBackgroundFetchData =
                mCustomAudienceDao.getDebuggableCustomAudienceBackgroundFetchDataByPrimaryKey(
                        owner, buyer, name);
        if (customAudienceBackgroundFetchData == null) {
            Log.d(
                    TAG,
                    String.format(
                            "No debuggable custom audience background fetch data found with owner"
                                    + " %s, buyer %s and name %s.",
                            owner, buyer, name));
            return Optional.empty();
        } else {
            Log.d(
                    TAG,
                    String.format(
                            "Debuggable custom audience background fetch data found with owner "
                                    + "%s, buyer %s and name %s",
                            owner, buyer, name));
            return Optional.of(customAudienceBackgroundFetchData);
        }
    }
}
