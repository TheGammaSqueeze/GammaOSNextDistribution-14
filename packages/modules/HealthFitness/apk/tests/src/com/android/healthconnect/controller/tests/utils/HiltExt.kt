/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.tests.utils

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.annotation.StyleRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.tests.TestActivity

inline fun <reified T : Fragment> launchFragment(
    fragmentArgs: Bundle? = null,
    @StyleRes themeResId: Int = R.style.Theme_HealthConnect,
    crossinline action: Fragment.() -> Unit = {}
): ActivityScenario<TestActivity> {
    val startActivityIntent =
        Intent.makeMainActivity(
                ComponentName(
                    ApplicationProvider.getApplicationContext(), TestActivity::class.java))
            .putExtra(
                "androidx.fragment.app.testing.FragmentScenario.EmptyFragmentActivity.THEME_EXTRAS_BUNDLE_KEY",
                themeResId)

    return ActivityScenario.launch<TestActivity>(startActivityIntent).onActivity { activity ->
        val fragment: Fragment =
            activity.supportFragmentManager.fragmentFactory.instantiate(
                T::class.java.classLoader, T::class.java.name)
        fragment.arguments = fragmentArgs
        activity.supportFragmentManager
            .beginTransaction()
            .add(android.R.id.content, fragment, "")
            .commitNow()

        fragment.action()
    }
}

inline fun <reified T : DialogFragment> launchDialog(
    arguments: Bundle? = null,
    tag: String = "TEST_DIALOG_TAG",
    @StyleRes themeResId: Int = R.style.Theme_HealthConnect,
    crossinline action: DialogFragment.() -> Unit = {}
): ActivityScenario<TestActivity> {
    val startActivityIntent =
        Intent.makeMainActivity(
                ComponentName(
                    ApplicationProvider.getApplicationContext(), TestActivity::class.java))
            .putExtra(
                "androidx.fragment.app.testing.FragmentScenario.EmptyFragmentActivity.THEME_EXTRAS_BUNDLE_KEY",
                themeResId)

    return ActivityScenario.launch<TestActivity>(startActivityIntent).onActivity { activity ->
        val fragment: Fragment =
            activity.supportFragmentManager.fragmentFactory.instantiate(
                T::class.java.classLoader, T::class.java.name)
        fragment as DialogFragment
        fragment.arguments = arguments
        fragment.show(activity.supportFragmentManager, tag)

        fragment.action()
    }
}

inline fun <reified T : Fragment> launchFragment(
    crossinline fragmentInstantiation: () -> T,
    @StyleRes themeResId: Int = R.style.Theme_HealthConnect
): ActivityScenario<TestActivity> {
    val startActivityIntent =
        Intent.makeMainActivity(
                ComponentName(
                    ApplicationProvider.getApplicationContext(), TestActivity::class.java))
            .putExtra(
                "androidx.fragment.app.testing.FragmentScenario.EmptyFragmentActivity.THEME_EXTRAS_BUNDLE_KEY",
                themeResId)
    return ActivityScenario.launch<TestActivity>(startActivityIntent).onActivity { activity ->
        activity.supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, fragmentInstantiation.invoke())
            .commitNow()
    }
}
