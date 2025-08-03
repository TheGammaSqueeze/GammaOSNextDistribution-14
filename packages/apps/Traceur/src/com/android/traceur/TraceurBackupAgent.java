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
 * limitations under the License
 */

package com.android.traceur;

import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;
import android.preference.PreferenceManager;
import com.android.traceur.Receiver;

public class TraceurBackupAgent extends BackupAgentHelper {

    private static final String PREFS_BACKUP_KEY = "traceur_backup_prefs";

    @Override
    public void onCreate() {
        SharedPreferencesBackupHelper helper = new SharedPreferencesBackupHelper(
                this, PreferenceManager.getDefaultSharedPreferencesName(this));
        addHelper(PREFS_BACKUP_KEY, helper);
    }

    @Override
    public void onRestoreFinished() {
        Receiver.updateQuickSettings(this);
    }
}
