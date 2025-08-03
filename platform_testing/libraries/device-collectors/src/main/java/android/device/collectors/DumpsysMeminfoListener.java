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

package android.device.collectors;

import android.device.collectors.annotations.OptionClass;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.helpers.DumpsysMeminfoHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@OptionClass(alias = "dumpsys-meminfo-listener")
public class DumpsysMeminfoListener extends BaseCollectionListener<Long> {

    private static final String TAG = DumpsysMeminfoHelper.class.getSimpleName();
    @VisibleForTesting static final String PROCESS_SEPARATOR = ",";
    @VisibleForTesting static final String PROCESS_OBJECT_SEPARATOR = ":";
    @VisibleForTesting static final String OBJECT_SEPARATOR = "#";
    @VisibleForTesting static final String OBJECT_SPACE_SEPARATOR = "_";

    @VisibleForTesting static final String PROCESS_NAMES_KEY = "process-names";
    // Format:
    // com.android.systemui:View#ViewRootImpl#Proxy_Binders,com.google.android.apps.nexuslauncher:View
    @VisibleForTesting
    static final String PROCESS_NAMES_OBJECT_NAMES_KEY = "process-names-object-names";

    private DumpsysMeminfoHelper mDumpsysMeminfoHelper = new DumpsysMeminfoHelper();

    public DumpsysMeminfoListener() {
        createHelperInstance(mDumpsysMeminfoHelper);
    }

    @VisibleForTesting
    public DumpsysMeminfoListener(Bundle args, DumpsysMeminfoHelper helper) {
        super(args, helper);
        mDumpsysMeminfoHelper = helper;
    }

    @Override
    public void setupAdditionalArgs() {
        Bundle args = getArgsBundle();
        String processesString = args.getString(PROCESS_NAMES_KEY, "");
        String processObjectString = args.getString(PROCESS_NAMES_OBJECT_NAMES_KEY, "");
        if (processesString.isEmpty() && processObjectString.isEmpty()) {
            Log.w(TAG, "No process name or object details provided. Nothing will be collected");
            return;
        }

        if (!processesString.isEmpty()) {
            mDumpsysMeminfoHelper.setProcessNames(processesString.split(PROCESS_SEPARATOR));
        }

        // Parse process names and corresponding object names.
        if (!processObjectString.isEmpty()) {
            Map<String, List<String>> processObjectMap = new LinkedHashMap();
            String[] processDetails = processObjectString.split(PROCESS_SEPARATOR);
            for (String processDetail : processDetails) {
                String[] processObjDetail = processDetail.split(PROCESS_OBJECT_SEPARATOR);
                String[] objNames = processObjDetail[1].split(OBJECT_SEPARATOR);
                List<String> updatedObjNames = new ArrayList<>();
                for (String objName : objNames) {
                    updatedObjNames.add(objName.replace(OBJECT_SPACE_SEPARATOR, " "));
                }
                processObjectMap.put(processObjDetail[0], updatedObjNames);
            }
            mDumpsysMeminfoHelper.setProcessObjectNamesMap(processObjectMap);
        }
    }
}
