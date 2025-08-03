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

package android.tools.flicker.legacy

abstract class AbstractFlickerTestData : FlickerTestData {
    private var assertionsCheckedCallback: ((Boolean) -> Unit)? = null
    private var createTagCallback: (String) -> Unit = {}

    final override fun withTag(tag: String, commands: FlickerTestData.() -> Any) {
        commands()
        createTagCallback(tag)
    }

    final override fun createTag(tag: String) {
        withTag(tag) {}
    }

    final override fun clearTagListener() {
        assertionsCheckedCallback = {}
    }

    final override fun setAssertionsCheckedCallback(callback: (Boolean) -> Unit) {
        assertionsCheckedCallback = callback
    }

    final override fun setCreateTagListener(callback: (String) -> Unit) {
        createTagCallback = callback
    }
}
