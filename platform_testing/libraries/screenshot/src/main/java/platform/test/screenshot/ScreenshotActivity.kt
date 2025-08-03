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

package platform.test.screenshot

import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity

/**
 * The Activity that is launched and whose content is set for screenshot tests. Please add the
 * following snippet to your test's AndroidManifest.xml
 *
 * <activity android:name="platform.test.screenshot.ScreenshotActivity" android:exported="true">
 * </activity>
 */
class ScreenshotActivity : ComponentActivity()
/**
 * The Activity that is launched and whose content is set for screenshot tests. Please add the
 * following snippet to your test's AndroidManifest.xml
 *
 * <activity android:name="platform.test.screenshot.FragmentScreenshotActivity"
 * android:exported="true"> </activity>
 */
class FragmentScreenshotActivity : AppCompatActivity()
