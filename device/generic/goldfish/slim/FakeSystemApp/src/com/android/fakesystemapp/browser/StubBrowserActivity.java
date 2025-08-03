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

package com.android.fakesystemapp.browser;

import android.app.Activity;

/**
 * A blank activity used to handle browser intents.
 *
 * This allows code-under-test that attempt to resolve or send browser intents to pass on the slim
 * image, which has no actual browser.
 */
public class StubBrowserActivity extends Activity {}
