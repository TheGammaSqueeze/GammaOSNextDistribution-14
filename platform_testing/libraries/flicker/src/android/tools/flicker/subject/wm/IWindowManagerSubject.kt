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

package android.tools.flicker.subject.wm

import android.tools.PlatformConsts
import android.tools.Rotation
import android.tools.traces.component.IComponentMatcher
import android.tools.traces.wm.Activity
import android.tools.traces.wm.DisplayContent
import android.tools.traces.wm.WindowState

/** Base interface for WM trace and state assertions */
interface IWindowManagerSubject<WMSubjectType, RegionSubjectType> {
    /** Asserts the current WindowManager state doesn't contain [WindowState]s */
    fun isEmpty(): WMSubjectType

    /** Asserts the current WindowManager state contains [WindowState]s */
    fun isNotEmpty(): WMSubjectType

    /**
     * Obtains the region occupied by all windows matching [componentMatcher]
     *
     * @param componentMatcher Components to search
     */
    fun visibleRegion(componentMatcher: IComponentMatcher? = null): RegionSubjectType

    /**
     * Asserts the state contains a [WindowState] matching [componentMatcher] above the app windows
     *
     * @param componentMatcher Component to search
     */
    fun containsAboveAppWindow(componentMatcher: IComponentMatcher): WMSubjectType

    /**
     * Asserts the state contains a [WindowState] matching [componentMatcher] below the app windows
     *
     * @param componentMatcher Component to search
     */
    fun containsBelowAppWindow(componentMatcher: IComponentMatcher): WMSubjectType

    /**
     * Asserts the state contains [WindowState]s matching [aboveWindowComponentMatcher] and
     * [belowWindowComponentMatcher], and that [aboveWindowComponentMatcher] is above
     * [belowWindowComponentMatcher]
     *
     * This assertion can be used, for example, to assert that a PIP window is shown above other
     * apps.
     *
     * @param aboveWindowComponentMatcher name of the window that should be above
     * @param belowWindowComponentMatcher name of the window that should be below
     */
    fun isAboveWindow(
        aboveWindowComponentMatcher: IComponentMatcher,
        belowWindowComponentMatcher: IComponentMatcher
    ): WMSubjectType

    /**
     * Asserts the state contains a non-app [WindowState] matching [componentMatcher]
     *
     * @param componentMatcher Component to search
     */
    fun containsNonAppWindow(componentMatcher: IComponentMatcher): WMSubjectType

    /**
     * Asserts the top visible app window in the state matches [componentMatcher]
     *
     * @param componentMatcher Component to search
     */
    fun isAppWindowOnTop(componentMatcher: IComponentMatcher): WMSubjectType

    /**
     * Asserts the top visible app window in the state doesn't match [componentMatcher]
     *
     * @param componentMatcher Component to search
     */
    fun isAppWindowNotOnTop(componentMatcher: IComponentMatcher): WMSubjectType

    /**
     * Asserts the bounds of the [WindowState] matching [componentMatcher] don't overlap.
     *
     * @param componentMatcher Component to search
     */
    fun doNotOverlap(vararg componentMatcher: IComponentMatcher): WMSubjectType

    /**
     * Asserts the state contains an app [WindowState] matching [componentMatcher]
     *
     * @param componentMatcher Component to search
     */
    fun containsAppWindow(componentMatcher: IComponentMatcher): WMSubjectType

    /**
     * Asserts the display with id [displayId] has rotation [rotation]
     *
     * @param rotation to assert
     * @param displayId of the target display
     */
    fun hasRotation(
        rotation: Rotation,
        displayId: Int = PlatformConsts.DEFAULT_DISPLAY
    ): WMSubjectType

    /**
     * Asserts the state contains a [WindowState] matching [componentMatcher].
     *
     * @param componentMatcher Components to search
     */
    fun contains(componentMatcher: IComponentMatcher): WMSubjectType

    /**
     * Asserts the state doesn't contain a [WindowState] nor an [Activity] matching
     * [componentMatcher].
     *
     * @param componentMatcher Components to search
     */
    fun notContainsAppWindow(componentMatcher: IComponentMatcher): WMSubjectType

    /**
     * Asserts the state doesn't contain a [WindowState] matching [componentMatcher].
     *
     * @param componentMatcher Components to search
     */
    fun notContains(componentMatcher: IComponentMatcher): WMSubjectType

    fun isRecentsActivityVisible(): WMSubjectType

    fun isRecentsActivityInvisible(): WMSubjectType

    /**
     * Asserts the state is valid, that is, if it has:
     * - a resumed activity
     * - a focused activity
     * - a focused window
     * - a front window
     * - a focused app
     */
    fun isValid(): WMSubjectType

    /**
     * Asserts the state contains a visible [WindowState] matching [componentMatcher].
     *
     * Also, if [componentMatcher] has a package name (i.e., is not a system component), also checks
     * that it contains a visible [Activity] matching [componentMatcher].
     *
     * @param componentMatcher Components to search
     */
    fun isNonAppWindowVisible(componentMatcher: IComponentMatcher): WMSubjectType

    /**
     * Asserts the state contains a visible [WindowState] matching [componentMatcher].
     *
     * Also, if [componentMatcher] has a package name (i.e., is not a system component), also checks
     * that it contains a visible [Activity] matching [componentMatcher].
     *
     * @param componentMatcher Components to search
     */
    fun isAppWindowVisible(componentMatcher: IComponentMatcher): WMSubjectType

    /** Asserts the state contains no visible app windows. */
    fun hasNoVisibleAppWindow(): WMSubjectType

    /** Asserts the state contains no visible app windows. */
    fun isKeyguardShowing(): WMSubjectType

    /**
     * Asserts the state contains an invisible window [WindowState] matching [componentMatcher].
     *
     * Also, if [componentMatcher] has a package name (i.e., is not a system component), also checks
     * that it contains an invisible [Activity] matching [componentMatcher].
     *
     * @param componentMatcher Components to search
     */
    fun isAppWindowInvisible(componentMatcher: IComponentMatcher): WMSubjectType

    /**
     * Asserts the state contains an invisible window matching [componentMatcher].
     *
     * Also, if [componentMatcher] has a package name (i.e., is not a system component), also checks
     * that it contains an invisible [Activity] matching [componentMatcher].
     *
     * @param componentMatcher Components to search
     */
    fun isNonAppWindowInvisible(componentMatcher: IComponentMatcher): WMSubjectType

    /** Asserts the state home activity is visible */
    fun isHomeActivityVisible(): WMSubjectType

    /** Asserts the state home activity is invisible */
    fun isHomeActivityInvisible(): WMSubjectType

    /**
     * Asserts that [app] is the focused app
     *
     * @param app App to check
     */
    fun isFocusedApp(app: String): WMSubjectType

    /**
     * Asserts that [app] is not the focused app
     *
     * @param app App to check
     */
    fun isNotFocusedApp(app: String): WMSubjectType

    /**
     * Asserts that [componentMatcher] exists and is pinned (in PIP mode)
     *
     * @param componentMatcher Components to search
     */
    fun isPinned(componentMatcher: IComponentMatcher): WMSubjectType

    /**
     * Asserts that [componentMatcher] exists and is not pinned (not in PIP mode)
     *
     * @param componentMatcher Components to search
     */
    fun isNotPinned(componentMatcher: IComponentMatcher): WMSubjectType

    /**
     * Checks if the activity with matching [componentMatcher] is visible
     *
     * In the case that an app is stopped in the background (e.g. OS stopped it to release memory)
     * the app window will not be immediately visible when switching back to the app. Checking if a
     * snapshotStartingWindow is present for that app instead can decrease flakiness levels of the
     * assertion.
     *
     * @param componentMatcher Component to search
     */
    fun isAppSnapshotStartingWindowVisibleFor(componentMatcher: IComponentMatcher): WMSubjectType

    /**
     * Checks if the non-app window matching [componentMatcher] exists above the app windows and is
     * visible
     *
     * @param componentMatcher Components to search
     */
    fun isAboveAppWindowVisible(componentMatcher: IComponentMatcher): WMSubjectType

    /**
     * Checks if the non-app window matching [componentMatcher] exists above the app windows and is
     * invisible
     *
     * @param componentMatcher Components to search
     */
    fun isAboveAppWindowInvisible(componentMatcher: IComponentMatcher): WMSubjectType

    /**
     * Checks if the non-app window matching [componentMatcher] exists below the app windows and is
     * visible
     *
     * @param componentMatcher Components to search
     */
    fun isBelowAppWindowVisible(componentMatcher: IComponentMatcher): WMSubjectType

    /**
     * Checks if the non-app window matching [componentMatcher] exists below the app windows and is
     * invisible
     *
     * @param componentMatcher Components to search
     */
    fun isBelowAppWindowInvisible(componentMatcher: IComponentMatcher): WMSubjectType

    /** Checks if the state contains at least one [DisplayContent] */
    fun containsAtLeastOneDisplay(): WMSubjectType
}
