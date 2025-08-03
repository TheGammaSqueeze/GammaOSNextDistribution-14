/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.fakesystemapp.launcher;

import android.app.Activity;

/**
 * A blank home activity used to replace the launcher activity.
 *
 * <p>This is in place to emulate the android system behavior when both the real Launcher and
 * Settings app are installed.
 *
 * <ol>
 *   <li>system boots - still encrypted.
 *   <li>System looks for default home 'directBootAware' activity. Finds FallbackHome and launches
 *   <li>user_unlocked event sent - FallbackHome triggers powermanager.userActivity and finishes
 *   <li>System looks for home activity - finds EmptyHomeActivity and launches
 * </ol>
 */
public class EmptyHomeActivity extends Activity {}
