/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.car.cts.builtin.user;

import static org.junit.Assert.fail;

import android.car.cts.builtin.CtsCarShellCommand;

import com.android.tradefed.device.ITestDevice;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InitializedUsersCommand extends CtsCarShellCommand {

    private static final String COMMAND_NAME = "cmd user list -v ";
    private static final int  USER_INFO_MIN_STRING_LENGTH = 20;

    private static final Pattern USER_PATTERN =
            Pattern.compile(".*id=(\\d+).*type=([^\\s]+).*");
    private static final int USER_PATTERN_GROUP_ID = 1;
    private static final int USER_PATTERN_GROUP_TYPE = 2;

    private List<Integer> mInitializedUsers;
    private boolean mHasHeadlessUser;

    public InitializedUsersCommand(ITestDevice device) {
        super(COMMAND_NAME, device);
    }

    public List<Integer> getInitializedUsers() {
        return mInitializedUsers;
    }

    public boolean hasHeadlessUser() {
        return mHasHeadlessUser;
    }

    @Override
    protected void parseCommandReturn() throws Exception {
        mInitializedUsers = new ArrayList<Integer>();
        mHasHeadlessUser = false;
        String[] userInfoList = mCommandReturn.split("\n");
        for (int i = 0; i < userInfoList.length; i++) {
            if (userInfoList[i].length() > USER_INFO_MIN_STRING_LENGTH) {
                if (userInfoList[i].contains("INITIALIZED")) {
                    Matcher matcher = USER_PATTERN.matcher(userInfoList[i]);
                    if (!matcher.find()) {
                        fail("parseCommandReturn: no match was found in: " + userInfoList[i]);
                    }
                    int userId = Integer.parseInt(matcher.group(USER_PATTERN_GROUP_ID));
                    mInitializedUsers.add(userId);
                    String type = matcher.group(USER_PATTERN_GROUP_TYPE);
                    if (!mHasHeadlessUser && type.contains("system.HEADLESS")) {
                        mHasHeadlessUser = true;
                    }
                }
            }
        }
    }
}
