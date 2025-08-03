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

package android.platform.helpers.rules

import android.platform.helpers.LockscreenUtils
import android.platform.helpers.LockscreenUtils.LockscreenType
import android.platform.helpers.LockscreenUtils.LockscreenType.*
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Sets [type] lockscreen before the test and resets it to [SWIPE] afterwards.
 *
 * Note that if [NONE] type is set, it is needed to unlock the current one before not having it in
 * the future.
 *
 * This default to [SWIPE] in [finished] as it is the default after a factory reset.
 */
class LockscreenRule(private val type: LockscreenType) : TestWatcher() {

    override fun starting(description: Description?) {
        when (type) {
            PIN -> setLockscreen(type = PIN, code = VALID_PIN)
            NONE -> setLockscreen(type = NONE)
            PASSWORD,
            PATTERN,
            SWIPE -> TODO("Not yet supported.")
        }
    }

    override fun finished(description: Description?) {
        if (type == PIN) {
            LockscreenUtils.resetLockscreen(VALID_PIN)
        }
        setLockscreen(SWIPE)
    }
}

private const val VALID_PIN = "1234"

/** Wrapper for java method to make above code less verbose and error prone. */
private fun setLockscreen(
    type: LockscreenType,
    code: String? = null,
    expectedLocked: Boolean = code != null
): Unit = LockscreenUtils.setLockscreen(type, code, expectedLocked)
