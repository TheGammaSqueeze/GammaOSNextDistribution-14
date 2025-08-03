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

package com.android.interactive.steps.notesrole;

import com.android.interactive.steps.ActAndConfirmStep;

/** A step to create and save a note in the OEM-provided default notes app. */
public final class CreateAndSaveANewNoteInOemDefaultNotesApp extends ActAndConfirmStep {

    public CreateAndSaveANewNoteInOemDefaultNotesApp() {
        super("The test will automatically launch OEM-provided default notes app. Please create "
              + "and save a note.");
    }
}
