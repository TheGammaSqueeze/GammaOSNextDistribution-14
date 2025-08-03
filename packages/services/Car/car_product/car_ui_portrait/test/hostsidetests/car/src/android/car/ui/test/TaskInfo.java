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
package android.car.ui;

import com.android.tradefed.log.LogUtil;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class TaskInfo {
    private static final String COMPONENT_PATTERN =
            "([A-Za-z]{1}[A-Za-z\\d_]*\\.)+[A-Za-z][A-Za-z\\d_]*/([A-Za-z]{1}[A-Za-z\\d_]*\\.)"
                    + "+[A-Za-z][A-Za-z\\d_]*";
    private static final String TASK_BOUNDS_PATTERN = "bounds=\\[\\d+,\\d+\\]\\[\\d+,\\d+\\]";
    private static final String TASK_USER_ID = "userId=\\d+";
    private static final String TASK_VISIBILITY = "visible=[truefalse]+";
    private static final String TASK_ID_GROUP_PATTERN = "taskId=\\d+:";
    private static final String TASK_ID_PATTERN = "\\d+";
    private static final String TASK_PATTERN =
            TASK_ID_GROUP_PATTERN + " " + COMPONENT_PATTERN + " " + TASK_BOUNDS_PATTERN + " "
                    + TASK_USER_ID + " " + TASK_VISIBILITY;

    private final int mTaskId;
    private final boolean mVisible;
    private final String mBaseActivity;

    TaskInfo(int taskId, String baseActivity, boolean visible) {
        mBaseActivity = baseActivity;
        mTaskId = taskId;
        mVisible = visible;
    }

    static TaskInfo unflattenFromString(String string) {
        LogUtil.CLog.d("TaskInfo unflattenFromString %s", string);
        return new TaskInfo(getTaskId(string), getComponentName(string), getVisibility(string));
    }

    private static String getComponentName(String string) {
        Pattern taskPattern = Pattern.compile(COMPONENT_PATTERN, Pattern.CASE_INSENSITIVE);
        Matcher matcher = taskPattern.matcher(string);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private static boolean getVisibility(String string) {
        Pattern taskPattern = Pattern.compile(TASK_VISIBILITY, Pattern.CASE_INSENSITIVE);
        Matcher matcher = taskPattern.matcher(string);
        if (matcher.find()) {
            String result = matcher.group();
            return result.endsWith("true");
        }
        return false;
    }

    private static int getTaskId(String string) {
        Pattern taskIdGroupPattern = Pattern.compile(TASK_ID_GROUP_PATTERN);
        Matcher matcher = taskIdGroupPattern.matcher(string);
        if (matcher.find()) {
            String idGroup = matcher.group();
            taskIdGroupPattern = Pattern.compile(TASK_ID_PATTERN);
            Matcher idMather = taskIdGroupPattern.matcher(idGroup);
            if (idMather.find()) {
                String id = idMather.group();
                return Integer.parseInt(id);
            }
        }
        return -1;
    }

    static HashMap<String, TaskInfo> getActiveComponents(String stackList) {
        LogUtil.CLog.d("Try to parse result %s", stackList);
        HashMap<String, TaskInfo> results = new HashMap<>();
        Pattern taskPattern = Pattern.compile(TASK_PATTERN, Pattern.CASE_INSENSITIVE);
        Matcher matcher = taskPattern.matcher(stackList);
        while (matcher.find()) {
            TaskInfo taskInfo = TaskInfo.unflattenFromString(matcher.group());
            results.put(taskInfo.getBaseActivity(), taskInfo);
            LogUtil.CLog.d("Find activity %s" + taskInfo.getBaseActivity());
        }
        return results;
    }

    boolean isVisible() {
        return mVisible;
    }

    String getBaseActivity() {
        return mBaseActivity;
    }
}
