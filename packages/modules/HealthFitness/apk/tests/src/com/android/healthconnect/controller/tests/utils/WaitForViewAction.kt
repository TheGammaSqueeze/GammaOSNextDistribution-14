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
package com.android.healthconnect.controller.tests.utils

import android.view.View
import androidx.test.espresso.PerformException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.util.HumanReadables
import androidx.test.espresso.util.TreeIterables
import java.util.concurrent.TimeoutException
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.StringDescription

/** A custom view action that waits for a matching view to appear. */
class WaitForViewAction(private val mMatcher: Matcher<View>, private val mWaitTimeMillis: Long) :
    ViewAction {
    override fun getConstraints(): Matcher<View> {
        return ViewMatchers.isRoot()
    }

    override fun getDescription(): String {
        val description: Description = StringDescription()
        mMatcher.describeTo(description)
        return ("wait at most " +
            mWaitTimeMillis +
            " milliseconds for view " +
            description.toString())
    }

    override fun perform(uiController: UiController, view: View) {
        uiController.loopMainThreadUntilIdle()
        val startTime = System.currentTimeMillis()
        val endTime = startTime + mWaitTimeMillis
        do {
            for (child in TreeIterables.breadthFirstViewTraversal(view)) {
                if (mMatcher.matches(child)) {
                    return
                }
            }
            uiController.loopMainThreadForAtLeast(50)
        } while (System.currentTimeMillis() < endTime)
        throw PerformException.Builder()
            .withActionDescription(this.description)
            .withViewDescription(HumanReadables.describe(view))
            .withCause(TimeoutException())
            .build()
    }

    companion object {
        fun waitForView(matcher: Matcher<View>, waitTimeMillis: Long = 500): ViewAction {
            return WaitForViewAction(matcher, waitTimeMillis)
        }
    }
}
