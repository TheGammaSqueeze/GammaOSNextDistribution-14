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

import com.android.interactive.steps.YesNoStep;

/**
 * A step to verify that the OEM-provided default notes app shows a prominent disclosure to the
 * user before allowing the user to access a saved note on the lock screen.
 */
public final class DoesOemNotesAppShowDisclosureBeforeAllowingAccessToSavedNoteOnLockScreen extends
        YesNoStep {

    public DoesOemNotesAppShowDisclosureBeforeAllowingAccessToSavedNoteOnLockScreen() {
        super("Does the OEM-provided default notes app show a prominent disclosure about the risk "
              + "of unauthenticated access before granting access to a saved note on the lock "
              + "screen?");
    }
}
