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

import android.tools.Rotation
import android.tools.datatypes.Region
import android.tools.flicker.assertions.Fact
import android.tools.flicker.subject.FlickerSubject
import android.tools.flicker.subject.exceptions.ExceptionMessageBuilder
import android.tools.flicker.subject.exceptions.IncorrectVisibilityException
import android.tools.flicker.subject.exceptions.InvalidElementException
import android.tools.flicker.subject.exceptions.InvalidPropertyException
import android.tools.flicker.subject.exceptions.SubjectAssertionError
import android.tools.flicker.subject.region.RegionSubject
import android.tools.io.Reader
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.component.IComponentMatcher
import android.tools.traces.wm.WindowManagerState
import android.tools.traces.wm.WindowState

/**
 * Subject for [WindowManagerState] objects, used to make assertions over behaviors that occur on a
 * single WM state.
 *
 * To make assertions over a specific state from a trace it is recommended to create a subject using
 * [WindowManagerTraceSubject](myTrace) and select the specific state using:
 * ```
 *     [WindowManagerTraceSubject.first]
 *     [WindowManagerTraceSubject.last]
 *     [WindowManagerTraceSubject.entry]
 * ```
 *
 * Alternatively, it is also possible to use [WindowManagerStateSubject](myState).
 *
 * Example:
 * ```
 *    val trace = WindowManagerTraceParser().parse(myTraceFile)
 *    val subject = WindowManagerTraceSubject(trace).first()
 *        .contains("ValidWindow")
 *        .notContains("ImaginaryWindow")
 *        .showsAboveAppWindow("NavigationBar")
 *        .invoke { myCustomAssertion(this) }
 * ```
 */
class WindowManagerStateSubject(
    val wmState: WindowManagerState,
    override val reader: Reader? = null,
    val trace: WindowManagerTraceSubject? = null,
) : FlickerSubject(), IWindowManagerSubject<WindowManagerStateSubject, RegionSubject> {
    override val timestamp = wmState.timestamp

    val subjects by lazy { wmState.windowStates.map { WindowStateSubject(reader, timestamp, it) } }

    val appWindows: List<WindowStateSubject>
        get() = subjects.filter { wmState.appWindows.contains(it.windowState) }

    val nonAppWindows: List<WindowStateSubject>
        get() = subjects.filter { wmState.nonAppWindows.contains(it.windowState) }

    val aboveAppWindows: List<WindowStateSubject>
        get() = subjects.filter { wmState.aboveAppWindows.contains(it.windowState) }

    val belowAppWindows: List<WindowStateSubject>
        get() = subjects.filter { wmState.belowAppWindows.contains(it.windowState) }

    val visibleWindows: List<WindowStateSubject>
        get() = subjects.filter { wmState.visibleWindows.contains(it.windowState) }

    val visibleAppWindows: List<WindowStateSubject>
        get() = subjects.filter { wmState.visibleAppWindows.contains(it.windowState) }

    /** Executes a custom [assertion] on the current subject */
    operator fun invoke(assertion: (WindowManagerState) -> Unit): WindowManagerStateSubject =
        apply {
            assertion(this.wmState)
        }

    /** {@inheritDoc} */
    override fun isEmpty(): WindowManagerStateSubject = apply {
        check { "WM state is empty" }.that(subjects.isEmpty()).isEqual(true)
    }

    /** {@inheritDoc} */
    override fun isNotEmpty(): WindowManagerStateSubject = apply {
        check { "WM state is not empty" }.that(subjects.isEmpty()).isEqual(false)
    }

    /** {@inheritDoc} */
    override fun visibleRegion(componentMatcher: IComponentMatcher?): RegionSubject {
        val selectedWindows =
            if (componentMatcher == null) {
                // No filters so use all subjects
                subjects
            } else {
                subjects.filter { componentMatcher.windowMatchesAnyOf(it.windowState) }
            }

        if (selectedWindows.isEmpty()) {
            val errorMsgBuilder =
                ExceptionMessageBuilder()
                    .forSubject(this)
                    .forInvalidElement(
                        componentMatcher?.toWindowIdentifier() ?: "<any>",
                        expectElementExists = true
                    )
            throw InvalidElementException(errorMsgBuilder)
        }

        val visibleWindows = selectedWindows.filter { it.isVisible }
        val visibleRegions = visibleWindows.map { it.windowState.frameRegion }
        return RegionSubject(visibleRegions, timestamp, reader)
    }

    /** {@inheritDoc} */
    override fun containsAboveAppWindow(
        componentMatcher: IComponentMatcher
    ): WindowManagerStateSubject = apply {
        if (!wmState.contains(componentMatcher)) {
            throw createElementNotFoundException(componentMatcher)
        }
        if (!wmState.isAboveAppWindow(componentMatcher)) {
            throw createElementNotFoundException(componentMatcher)
        }
    }

    /** {@inheritDoc} */
    override fun containsBelowAppWindow(
        componentMatcher: IComponentMatcher
    ): WindowManagerStateSubject = apply {
        if (!wmState.contains(componentMatcher)) {
            throw createElementNotFoundException(componentMatcher)
        }
        if (!wmState.isBelowAppWindow(componentMatcher)) {
            throw createElementNotFoundException(componentMatcher)
        }
    }

    /** {@inheritDoc} */
    override fun isAboveWindow(
        aboveWindowComponentMatcher: IComponentMatcher,
        belowWindowComponentMatcher: IComponentMatcher
    ): WindowManagerStateSubject = apply {
        contains(aboveWindowComponentMatcher)
        contains(belowWindowComponentMatcher)

        val aboveWindow =
            wmState.windowStates.first { aboveWindowComponentMatcher.windowMatchesAnyOf(it) }
        val belowWindow =
            wmState.windowStates.first { belowWindowComponentMatcher.windowMatchesAnyOf(it) }

        val errorMsgBuilder =
            ExceptionMessageBuilder()
                .forSubject(this)
                .addExtraDescription(
                    Fact("Above window filter", aboveWindowComponentMatcher.toWindowIdentifier())
                )
                .addExtraDescription(
                    Fact("Below window filter", belowWindowComponentMatcher.toWindowIdentifier())
                )

        if (aboveWindow == belowWindow) {
            errorMsgBuilder
                .setMessage("Above and below windows should be different")
                .setActual(aboveWindow.title)
            throw SubjectAssertionError(errorMsgBuilder)
        }

        // windows are ordered by z-order, from top to bottom
        val aboveZ =
            wmState.windowStates.indexOfFirst { aboveWindowComponentMatcher.windowMatchesAnyOf(it) }
        val belowZ =
            wmState.windowStates.indexOfFirst { belowWindowComponentMatcher.windowMatchesAnyOf(it) }
        if (aboveZ >= belowZ) {
            errorMsgBuilder
                .setMessage("${aboveWindow.title} should be above ${belowWindow.title}")
                .setActual("${belowWindow.title} is above")
                .setExpected("${aboveWindow.title} is below")
            throw SubjectAssertionError(errorMsgBuilder)
        }
    }

    /** {@inheritDoc} */
    override fun containsNonAppWindow(
        componentMatcher: IComponentMatcher
    ): WindowManagerStateSubject = apply {
        if (!wmState.contains(componentMatcher)) {
            throw createElementNotFoundException(componentMatcher)
        }
        if (!wmState.isNonAppWindow(componentMatcher)) {
            throw createElementNotFoundException(componentMatcher)
        }
    }

    /** {@inheritDoc} */
    override fun isAppWindowOnTop(componentMatcher: IComponentMatcher): WindowManagerStateSubject =
        apply {
            if (wmState.visibleAppWindows.isEmpty()) {
                val errorMsgBuilder =
                    ExceptionMessageBuilder()
                        .forSubject(this)
                        .forInvalidElement(
                            componentMatcher.toWindowIdentifier(),
                            expectElementExists = true
                        )
                        .addExtraDescription("Type", "App window")
                throw InvalidElementException(errorMsgBuilder)
            }

            val topVisibleAppWindow = wmState.topVisibleAppWindow
            val topWindowMatches =
                topVisibleAppWindow != null &&
                    componentMatcher.windowMatchesAnyOf(topVisibleAppWindow)

            if (!topWindowMatches) {
                isNotEmpty()

                val errorMsgBuilder =
                    ExceptionMessageBuilder()
                        .forSubject(this)
                        .forInvalidProperty("Top visible app window")
                        .setActual(topVisibleAppWindow?.name)
                        .setExpected(componentMatcher.toWindowIdentifier())
                throw InvalidPropertyException(errorMsgBuilder)
            }
        }

    /** {@inheritDoc} */
    override fun isAppWindowNotOnTop(
        componentMatcher: IComponentMatcher
    ): WindowManagerStateSubject = apply {
        val topVisibleAppWindow = wmState.topVisibleAppWindow
        if (
            topVisibleAppWindow != null && componentMatcher.windowMatchesAnyOf(topVisibleAppWindow)
        ) {
            val topWindow = subjects.first { it.windowState == topVisibleAppWindow }
            val errorMsgBuilder =
                ExceptionMessageBuilder()
                    .forSubject(this)
                    .forInvalidProperty("${topWindow.name} should not be on top")
                    .setActual(topWindow.name)
                    .setExpected(componentMatcher.toWindowIdentifier())
                    .addExtraDescription("Type", "App window")
                    .addExtraDescription("Filter", componentMatcher.toWindowIdentifier())
            throw InvalidPropertyException(errorMsgBuilder)
        }
    }

    /** {@inheritDoc} */
    override fun doNotOverlap(
        vararg componentMatcher: IComponentMatcher
    ): WindowManagerStateSubject = apply {
        val componentNames = componentMatcher.joinToString(", ") { it.toWindowIdentifier() }
        if (componentMatcher.size == 1) {
            throw IllegalArgumentException(
                "Must give more than one window to check! (Given $componentNames)"
            )
        }

        componentMatcher.forEach { contains(it) }
        val foundWindows =
            componentMatcher
                .toSet()
                .associateWith { act ->
                    wmState.windowStates.firstOrNull { act.windowMatchesAnyOf(it) }
                }
                // keep entries only for windows that we actually found by removing nulls
                .filterValues { it != null }
        val foundWindowsRegions =
            foundWindows.mapValues { (_, v) -> v?.frameRegion ?: Region.EMPTY }

        val regions = foundWindowsRegions.entries.toList()
        for (i in regions.indices) {
            val (ourTitle, ourRegion) = regions[i]
            for (j in i + 1 until regions.size) {
                val (otherTitle, otherRegion) = regions[j]
                val overlapRegion = Region().also { it.set(ourRegion) }
                if (overlapRegion.op(otherRegion, Region.Op.INTERSECT)) {
                    val errorMsgBuilder =
                        ExceptionMessageBuilder()
                            .forSubject(this)
                            .setMessage("$componentNames should not overlap")
                            .setActual("$ourTitle overlaps with $otherTitle")
                            .addExtraDescription("$ourTitle region", ourRegion)
                            .addExtraDescription("$otherTitle region", otherRegion)
                            .addExtraDescription("Overlap region", overlapRegion)
                    throw SubjectAssertionError(errorMsgBuilder)
                }
            }
        }
    }

    /** {@inheritDoc} */
    override fun containsAppWindow(componentMatcher: IComponentMatcher): WindowManagerStateSubject =
        apply {
            // Check existence of activity
            val activity = wmState.getActivitiesForWindow(componentMatcher).firstOrNull()

            if (activity == null) {
                val errorMsgBuilder =
                    ExceptionMessageBuilder()
                        .forSubject(this)
                        .forInvalidElement(
                            componentMatcher.toActivityIdentifier(),
                            expectElementExists = true
                        )
                throw InvalidElementException(errorMsgBuilder)
            }
            // Check existence of window.
            contains(componentMatcher)
        }

    /** {@inheritDoc} */
    override fun hasRotation(rotation: Rotation, displayId: Int): WindowManagerStateSubject =
        apply {
            check { "rotation" }.that(wmState.getRotation(displayId)).isEqual(rotation)
        }

    /** {@inheritDoc} */
    override fun contains(componentMatcher: IComponentMatcher): WindowManagerStateSubject = apply {
        contains(subjects, componentMatcher)
    }

    /** {@inheritDoc} */
    override fun notContainsAppWindow(
        componentMatcher: IComponentMatcher
    ): WindowManagerStateSubject = apply {
        // system components (e.g., NavBar, StatusBar, PipOverlay) don't have a package name
        // nor an activity, ignore them
        if (wmState.containsActivity(componentMatcher)) {
            val errorMsgBuilder =
                ExceptionMessageBuilder()
                    .forSubject(this)
                    .forInvalidElement(
                        componentMatcher.toActivityIdentifier(),
                        expectElementExists = false
                    )
            throw InvalidElementException(errorMsgBuilder)
        }
        notContains(componentMatcher)
    }

    /** {@inheritDoc} */
    override fun notContains(componentMatcher: IComponentMatcher): WindowManagerStateSubject =
        apply {
            if (wmState.containsWindow(componentMatcher)) {
                val errorMsgBuilder =
                    ExceptionMessageBuilder()
                        .forSubject(this)
                        .forInvalidElement(
                            componentMatcher.toWindowIdentifier(),
                            expectElementExists = false
                        )
                throw InvalidElementException(errorMsgBuilder)
            }
        }

    /** {@inheritDoc} */
    override fun isRecentsActivityVisible(): WindowManagerStateSubject = apply {
        if (wmState.isHomeRecentsComponent) {
            isHomeActivityVisible()
        } else {
            if (!wmState.isRecentsActivityVisible) {
                val errorMsgBuilder =
                    ExceptionMessageBuilder()
                        .forSubject(this)
                        .forIncorrectVisibility("Recents activity", expectElementVisible = true)
                        .setActual(wmState.isRecentsActivityVisible)
                throw IncorrectVisibilityException(errorMsgBuilder)
            }
        }
    }

    /** {@inheritDoc} */
    override fun isRecentsActivityInvisible(): WindowManagerStateSubject = apply {
        if (wmState.isHomeRecentsComponent) {
            isHomeActivityInvisible()
        } else {
            if (wmState.isRecentsActivityVisible) {
                val errorMsgBuilder =
                    ExceptionMessageBuilder()
                        .forSubject(this)
                        .forIncorrectVisibility("Recents activity", expectElementVisible = false)
                        .setActual(wmState.isRecentsActivityVisible)
                throw IncorrectVisibilityException(errorMsgBuilder)
            }
        }
    }

    /** {@inheritDoc} */
    override fun isValid(): WindowManagerStateSubject = apply {
        check { "Stacks count" }.that(wmState.stackCount).isGreater(0)
        // TODO: Update when keyguard will be shown on multiple displays
        if (!wmState.keyguardControllerState.isKeyguardShowing) {
            check { "Resumed activity" }.that(wmState.resumedActivitiesCount).isGreater(0)
        }
        check { "No focused activity" }.that(wmState.focusedActivity).isNotEqual(null)
        wmState.rootTasks.forEach { aStack ->
            val stackId = aStack.rootTaskId
            aStack.tasks.forEach { aTask ->
                check { "Root task Id for stack $aTask" }.that(stackId).isEqual(aTask.rootTaskId)
            }
        }
        check { "Front window" }.that(wmState.frontWindow).isNotNull()
        check { "Focused window" }.that(wmState.focusedWindow).isNotNull()
        check { "Focused app" }.that(wmState.focusedApp.isNotEmpty()).isEqual(true)
    }

    /** {@inheritDoc} */
    override fun isNonAppWindowVisible(
        componentMatcher: IComponentMatcher
    ): WindowManagerStateSubject = apply {
        if (!wmState.contains(componentMatcher)) {
            throw createElementNotFoundException(componentMatcher)
        }
        if (!wmState.isNonAppWindow(componentMatcher)) {
            throw createElementNotFoundException(componentMatcher)
        }
        if (!wmState.isVisible(componentMatcher)) {
            throw createIncorrectVisibilityException(componentMatcher, expectElementVisible = true)
        }
    }

    /** {@inheritDoc} */
    override fun isAppWindowVisible(
        componentMatcher: IComponentMatcher
    ): WindowManagerStateSubject = apply {
        if (!wmState.contains(componentMatcher)) {
            throw createElementNotFoundException(componentMatcher)
        }
        if (!wmState.isAppWindow(componentMatcher)) {
            throw createElementNotFoundException(componentMatcher)
        }
        if (!wmState.isVisible(componentMatcher)) {
            throw createIncorrectVisibilityException(componentMatcher, expectElementVisible = true)
        }
    }

    /** {@inheritDoc} */
    override fun hasNoVisibleAppWindow(): WindowManagerStateSubject = apply {
        check { "Visible app windows" }
            .that(visibleAppWindows.joinToString(", ") { it.name })
            .isEqual("")
    }

    /** {@inheritDoc} */
    override fun isKeyguardShowing(): WindowManagerStateSubject = apply {
        check { "Keyguard or AOD showing" }
            .that(
                wmState.isKeyguardShowing || wmState.isAodShowing,
            )
            .isEqual(true)
    }

    /** {@inheritDoc} */
    override fun isAppWindowInvisible(
        componentMatcher: IComponentMatcher
    ): WindowManagerStateSubject = apply { checkWindowIsInvisible(appWindows, componentMatcher) }

    /** {@inheritDoc} */
    override fun isNonAppWindowInvisible(
        componentMatcher: IComponentMatcher
    ): WindowManagerStateSubject = apply { checkWindowIsInvisible(nonAppWindows, componentMatcher) }

    private fun checkWindowIsVisible(
        subjectList: List<WindowStateSubject>,
        componentMatcher: IComponentMatcher
    ) {
        // Check existence of window.
        contains(subjectList, componentMatcher)

        val foundWindows =
            subjectList.filter { componentMatcher.windowMatchesAnyOf(it.windowState) }

        val visibleWindows =
            wmState.visibleWindows.filter { visibleWindow ->
                foundWindows.any { it.windowState == visibleWindow }
            }

        if (visibleWindows.isEmpty()) {
            val errorMsgBuilder =
                ExceptionMessageBuilder()
                    .forSubject(this)
                    .forIncorrectVisibility(
                        componentMatcher.toWindowIdentifier(),
                        expectElementVisible = true
                    )
                    .setActual(foundWindows.map { Fact("Is invisible", it.name) })
            throw IncorrectVisibilityException(errorMsgBuilder)
        }
    }

    private fun checkWindowIsInvisible(
        subjectList: List<WindowStateSubject>,
        componentMatcher: IComponentMatcher
    ) {
        val foundWindows =
            subjectList.filter { componentMatcher.windowMatchesAnyOf(it.windowState) }

        val visibleWindows =
            wmState.visibleWindows.filter { visibleWindow ->
                foundWindows.any { it.windowState == visibleWindow }
            }

        if (visibleWindows.isNotEmpty()) {
            val errorMsgBuilder =
                ExceptionMessageBuilder()
                    .forSubject(this)
                    .forIncorrectVisibility(
                        componentMatcher.toWindowIdentifier(),
                        expectElementVisible = false
                    )
                    .setActual(visibleWindows.map { Fact("Is visible", it.name) })
            throw IncorrectVisibilityException(errorMsgBuilder)
        }
    }

    private fun contains(
        subjectList: List<WindowStateSubject>,
        componentMatcher: IComponentMatcher
    ) {
        if (!componentMatcher.windowMatchesAnyOf(subjectList.map { it.windowState })) {
            val errorMsgBuilder =
                ExceptionMessageBuilder()
                    .forSubject(this)
                    .forInvalidElement(
                        componentMatcher.toWindowIdentifier(),
                        expectElementExists = true
                    )
            throw InvalidElementException(errorMsgBuilder)
        }
    }

    private fun createIncorrectVisibilityException(
        componentMatcher: IComponentMatcher,
        expectElementVisible: Boolean
    ) =
        IncorrectVisibilityException(
            ExceptionMessageBuilder()
                .forSubject(this)
                .forIncorrectVisibility(componentMatcher.toWindowIdentifier(), expectElementVisible)
        )

    private fun createElementNotFoundException(componentMatcher: IComponentMatcher) =
        InvalidElementException(
            ExceptionMessageBuilder()
                .forSubject(this)
                .forInvalidElement(
                    componentMatcher.toWindowIdentifier(),
                    expectElementExists = true
                )
        )

    /** {@inheritDoc} */
    override fun isHomeActivityVisible(): WindowManagerStateSubject = apply {
        if (!wmState.isHomeActivityVisible) {
            val errorMsgBuilder =
                ExceptionMessageBuilder()
                    .forSubject(this)
                    .forIncorrectVisibility("Home activity", expectElementVisible = true)
            throw IncorrectVisibilityException(errorMsgBuilder)
        }
    }

    /** {@inheritDoc} */
    override fun isHomeActivityInvisible(): WindowManagerStateSubject = apply {
        val homeIsVisible = wmState.homeActivity?.isVisible ?: false
        if (homeIsVisible) {
            val errorMsgBuilder =
                ExceptionMessageBuilder()
                    .forSubject(this)
                    .forIncorrectVisibility("Home activity", expectElementVisible = false)
            throw IncorrectVisibilityException(errorMsgBuilder)
        }
    }

    /** {@inheritDoc} */
    override fun isFocusedApp(app: String): WindowManagerStateSubject = apply {
        check { "Window is focused app $app" }.that(wmState.focusedApp).isEqual(app)
    }

    /** {@inheritDoc} */
    override fun isNotFocusedApp(app: String): WindowManagerStateSubject = apply {
        check { "Window is not focused app $app" }.that(wmState.focusedApp).isNotEqual(app)
    }

    /** {@inheritDoc} */
    override fun isPinned(componentMatcher: IComponentMatcher): WindowManagerStateSubject = apply {
        contains(componentMatcher)
        check { "Window is pinned ${componentMatcher.toWindowIdentifier()}" }
            .that(wmState.isInPipMode(componentMatcher))
            .isEqual(true)
    }

    /** {@inheritDoc} */
    override fun isNotPinned(componentMatcher: IComponentMatcher): WindowManagerStateSubject =
        apply {
            contains(componentMatcher)
            check { "Window is pinned ${componentMatcher.toWindowIdentifier()}" }
                .that(wmState.isInPipMode(componentMatcher))
                .isEqual(false)
        }

    /** {@inheritDoc} */
    override fun isAppSnapshotStartingWindowVisibleFor(
        componentMatcher: IComponentMatcher
    ): WindowManagerStateSubject = apply {
        val activity = wmState.getActivitiesForWindow(componentMatcher).firstOrNull()

        if (activity == null) {
            val errorMsgBuilder =
                ExceptionMessageBuilder()
                    .forSubject(this)
                    .forInvalidElement(
                        componentMatcher.toActivityIdentifier(),
                        expectElementExists = true
                    )
            throw InvalidElementException(errorMsgBuilder)
        }

        // Check existence and visibility of SnapshotStartingWindow
        val snapshotStartingWindow =
            activity.getWindows(ComponentNameMatcher.SNAPSHOT).firstOrNull()

        if (snapshotStartingWindow == null) {
            val errorMsgBuilder =
                ExceptionMessageBuilder()
                    .forSubject(this)
                    .forInvalidElement(
                        ComponentNameMatcher.SNAPSHOT.toWindowIdentifier(),
                        expectElementExists = true
                    )
            throw InvalidElementException(errorMsgBuilder)
        }

        if (!activity.isVisible) {
            val errorMsgBuilder =
                ExceptionMessageBuilder()
                    .forSubject(this)
                    .forIncorrectVisibility(
                        componentMatcher.toActivityIdentifier(),
                        expectElementVisible = true
                    )
            throw IncorrectVisibilityException(errorMsgBuilder)
        }

        if (!snapshotStartingWindow.isVisible) {
            val errorMsgBuilder =
                ExceptionMessageBuilder()
                    .forSubject(this)
                    .forIncorrectVisibility(
                        ComponentNameMatcher.SNAPSHOT.toWindowIdentifier(),
                        expectElementVisible = true
                    )
            throw IncorrectVisibilityException(errorMsgBuilder)
        }
    }

    /** {@inheritDoc} */
    override fun isAboveAppWindowVisible(
        componentMatcher: IComponentMatcher
    ): WindowManagerStateSubject =
        containsAboveAppWindow(componentMatcher).isNonAppWindowVisible(componentMatcher)

    /** {@inheritDoc} */
    override fun isAboveAppWindowInvisible(
        componentMatcher: IComponentMatcher
    ): WindowManagerStateSubject =
        containsAboveAppWindow(componentMatcher).isNonAppWindowInvisible(componentMatcher)

    /** {@inheritDoc} */
    override fun isBelowAppWindowVisible(
        componentMatcher: IComponentMatcher
    ): WindowManagerStateSubject =
        containsBelowAppWindow(componentMatcher).isNonAppWindowVisible(componentMatcher)

    /** {@inheritDoc} */
    override fun isBelowAppWindowInvisible(
        componentMatcher: IComponentMatcher
    ): WindowManagerStateSubject =
        containsBelowAppWindow(componentMatcher).isNonAppWindowInvisible(componentMatcher)

    /** {@inheritDoc} */
    override fun containsAtLeastOneDisplay(): WindowManagerStateSubject = apply {
        check { "Displays" }.that(wmState.displays.size).isGreater(0)
    }

    /** Obtains the first subject with [WindowState.title] containing [name]. */
    fun windowState(name: String): WindowStateSubject? = windowState { it.name.contains(name) }

    /**
     * Obtains the first subject matching [predicate].
     *
     * @param predicate to search for a subject
     */
    fun windowState(predicate: (WindowState) -> Boolean): WindowStateSubject? =
        subjects.firstOrNull { predicate(it.windowState) }

    override fun toString(): String {
        return "WindowManagerStateSubject($wmState)"
    }
}
