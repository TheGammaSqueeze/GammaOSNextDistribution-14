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

package android.tools.flicker.config

import android.tools.flicker.AssertionInvocationGroup
import android.tools.flicker.assertors.assertions.AppLayerBecomesInvisible
import android.tools.flicker.assertors.assertions.AppLayerBecomesVisible
import android.tools.flicker.assertors.assertions.AppLayerCoversFullScreenAtEnd
import android.tools.flicker.assertors.assertions.AppLayerCoversFullScreenAtStart
import android.tools.flicker.assertors.assertions.AppLayerIsInvisibleAtEnd
import android.tools.flicker.assertors.assertions.AppLayerIsInvisibleAtStart
import android.tools.flicker.assertors.assertions.AppLayerIsVisibleAlways
import android.tools.flicker.assertors.assertions.AppLayerIsVisibleAtEnd
import android.tools.flicker.assertors.assertions.AppLayerIsVisibleAtStart
import android.tools.flicker.assertors.assertions.AppWindowBecomesInvisible
import android.tools.flicker.assertors.assertions.AppWindowBecomesTopWindow
import android.tools.flicker.assertors.assertions.AppWindowBecomesVisible
import android.tools.flicker.assertors.assertions.AppWindowIsInvisibleAtEnd
import android.tools.flicker.assertors.assertions.AppWindowIsInvisibleAtStart
import android.tools.flicker.assertors.assertions.AppWindowIsTopWindowAtStart
import android.tools.flicker.assertors.assertions.AppWindowIsVisibleAlways
import android.tools.flicker.assertors.assertions.AppWindowIsVisibleAtEnd
import android.tools.flicker.assertors.assertions.AppWindowIsVisibleAtStart
import android.tools.flicker.assertors.assertions.AppWindowOnTopAtEnd
import android.tools.flicker.assertors.assertions.AppWindowOnTopAtStart
import android.tools.flicker.assertors.assertions.EntireScreenCoveredAlways
import android.tools.flicker.assertors.assertions.FocusChanges
import android.tools.flicker.assertors.assertions.HasAtMostOneWindowMatching
import android.tools.flicker.assertors.assertions.LayerBecomesInvisible
import android.tools.flicker.assertors.assertions.LayerBecomesVisible
import android.tools.flicker.assertors.assertions.LayerReduces
import android.tools.flicker.assertors.assertions.ScreenLockedAtStart
import android.tools.flicker.assertors.assertions.SplitAppLayerBoundsBecomesVisible
import android.tools.flicker.assertors.assertions.VisibleLayersShownMoreThanOneConsecutiveEntry
import android.tools.flicker.assertors.assertions.VisibleWindowsShownMoreThanOneConsecutiveEntry
import android.tools.flicker.assertors.assertions.WindowBecomesPinned
import android.tools.flicker.assertors.assertions.WindowRemainInsideVisibleBounds
import android.tools.flicker.config.appclose.Components.CLOSING_APPS
import android.tools.flicker.config.applaunch.Components.OPENING_APPS
import android.tools.flicker.config.common.Components.LAUNCHER
import android.tools.flicker.config.pip.Components.PIP_APP
import android.tools.flicker.config.pip.Components.PIP_DISMISS_OVERLAY
import android.tools.flicker.config.splitscreen.Components.SPLIT_SCREEN_DIVIDER
import android.tools.flicker.config.splitscreen.Components.SPLIT_SCREEN_PRIMARY_APP
import android.tools.flicker.config.splitscreen.Components.SPLIT_SCREEN_SECONDARY_APP
import android.tools.traces.component.ComponentNameMatcher

object AssertionTemplates {
    val ENTIRE_TRACE_ASSERTIONS =
        mapOf(
            EntireScreenCoveredAlways() to AssertionInvocationGroup.NON_BLOCKING,
            VisibleWindowsShownMoreThanOneConsecutiveEntry() to
                AssertionInvocationGroup.NON_BLOCKING,
            // Temporarily ignore these layers which might be visible for a single entry
            // and contain only view level changes during that entry (b/286054008)
            VisibleLayersShownMoreThanOneConsecutiveEntry(
                ignore =
                    listOf(
                        ComponentNameMatcher.NOTIFICATION_SHADE,
                        ComponentNameMatcher.VOLUME_DIALOG,
                        ComponentNameMatcher.NAV_BAR,
                    )
            ) to AssertionInvocationGroup.NON_BLOCKING,
        )

    val COMMON_ASSERTIONS =
        listOf(
                EntireScreenCoveredAlways(),
                VisibleWindowsShownMoreThanOneConsecutiveEntry(),
                VisibleLayersShownMoreThanOneConsecutiveEntry(),
            )
            .associateBy({ it }, { AssertionInvocationGroup.BLOCKING })

    val APP_LAUNCH_ASSERTIONS =
        COMMON_ASSERTIONS +
            listOf(
                    AppLayerIsInvisibleAtStart(OPENING_APPS),
                    AppLayerIsVisibleAtEnd(OPENING_APPS),
                    AppLayerBecomesVisible(OPENING_APPS),
                    AppWindowBecomesVisible(OPENING_APPS),
                    AppWindowBecomesTopWindow(OPENING_APPS),
                )
                .associateBy({ it }, { AssertionInvocationGroup.BLOCKING })

    val APP_CLOSE_ASSERTIONS =
        COMMON_ASSERTIONS.toMutableMap().also {
            it.remove(VisibleLayersShownMoreThanOneConsecutiveEntry())
        } +
            listOf(
                    AppLayerIsVisibleAtStart(CLOSING_APPS),
                    AppLayerIsInvisibleAtEnd(CLOSING_APPS),
                    AppWindowIsVisibleAtStart(CLOSING_APPS),
                    AppWindowIsInvisibleAtEnd(CLOSING_APPS),
                    AppLayerBecomesInvisible(CLOSING_APPS),
                    AppWindowBecomesInvisible(CLOSING_APPS),
                    AppWindowIsTopWindowAtStart(CLOSING_APPS),
                    VisibleLayersShownMoreThanOneConsecutiveEntry(
                        listOf(ComponentNameMatcher.NAV_BAR)
                    )
                )
                .associateBy({ it }, { AssertionInvocationGroup.BLOCKING })

    val APP_LAUNCH_FROM_HOME_ASSERTIONS =
        APP_LAUNCH_ASSERTIONS +
            listOf(
                    AppLayerIsVisibleAtStart(LAUNCHER),
                    AppLayerIsInvisibleAtEnd(LAUNCHER),
                )
                .associateBy({ it }, { AssertionInvocationGroup.BLOCKING })

    val APP_LAUNCH_FROM_LOCK_ASSERTIONS =
        APP_LAUNCH_ASSERTIONS +
            listOf(FocusChanges(toComponent = OPENING_APPS), ScreenLockedAtStart())
                .associateBy({ it }, { AssertionInvocationGroup.BLOCKING })

    val APP_CLOSE_TO_HOME_ASSERTIONS =
        APP_CLOSE_ASSERTIONS +
            listOf(
                    AppLayerIsInvisibleAtStart(LAUNCHER),
                    AppLayerIsVisibleAtEnd(LAUNCHER),
                    AppWindowIsInvisibleAtStart(LAUNCHER),
                    AppWindowIsVisibleAtEnd(LAUNCHER),
                    AppWindowBecomesTopWindow(LAUNCHER),
                )
                .associateBy({ it }, { AssertionInvocationGroup.BLOCKING })

    val APP_LAUNCH_FROM_NOTIFICATION_ASSERTIONS = COMMON_ASSERTIONS + APP_LAUNCH_ASSERTIONS

    val LAUNCHER_QUICK_SWITCH_ASSERTIONS =
        COMMON_ASSERTIONS +
            APP_LAUNCH_ASSERTIONS +
            APP_CLOSE_ASSERTIONS +
            listOf(
                    AppLayerCoversFullScreenAtStart(CLOSING_APPS),
                    AppLayerCoversFullScreenAtEnd(OPENING_APPS),
                    AppWindowOnTopAtStart(CLOSING_APPS),
                    AppWindowOnTopAtEnd(OPENING_APPS),
                    AppWindowBecomesInvisible(CLOSING_APPS),
                    AppLayerBecomesInvisible(CLOSING_APPS),
                    AppWindowBecomesVisible(OPENING_APPS),
                    AppLayerBecomesVisible(OPENING_APPS),
                )
                .associateBy({ it }, { AssertionInvocationGroup.BLOCKING })

    val APP_CLOSE_TO_PIP_ASSERTIONS =
        COMMON_ASSERTIONS +
            listOf(
                    LayerReduces(PIP_APP),
                    FocusChanges(),
                    AppWindowIsVisibleAlways(PIP_APP),
                    WindowRemainInsideVisibleBounds(PIP_APP),
                    WindowBecomesPinned(PIP_APP),
                    LayerBecomesVisible(LAUNCHER),
                    HasAtMostOneWindowMatching(PIP_DISMISS_OVERLAY)
                )
                .associateBy({ it }, { AssertionInvocationGroup.BLOCKING })

    val ENTER_SPLITSCREEN_ASSERTIONS =
        COMMON_ASSERTIONS +
            listOf(
                    LayerBecomesVisible(SPLIT_SCREEN_DIVIDER),
                    AppLayerIsVisibleAtEnd(SPLIT_SCREEN_PRIMARY_APP),
                    AppLayerBecomesVisible(SPLIT_SCREEN_SECONDARY_APP),
                    SplitAppLayerBoundsBecomesVisible(
                        SPLIT_SCREEN_PRIMARY_APP,
                        isPrimaryApp = true
                    ),
                    SplitAppLayerBoundsBecomesVisible(
                        SPLIT_SCREEN_SECONDARY_APP,
                        isPrimaryApp = false
                    ),
                    AppWindowBecomesVisible(SPLIT_SCREEN_PRIMARY_APP),
                    AppWindowBecomesVisible(SPLIT_SCREEN_SECONDARY_APP),
                )
                .associateBy({ it }, { AssertionInvocationGroup.BLOCKING })

    val EXIT_SPLITSCREEN_ASSERTIONS =
        COMMON_ASSERTIONS +
            listOf(
                    LayerBecomesInvisible(SPLIT_SCREEN_DIVIDER),
                    AppLayerBecomesInvisible(SPLIT_SCREEN_PRIMARY_APP),
                    AppLayerIsVisibleAlways(SPLIT_SCREEN_SECONDARY_APP),
                    AppWindowBecomesInvisible(SPLIT_SCREEN_PRIMARY_APP),
                    AppWindowIsVisibleAlways(SPLIT_SCREEN_SECONDARY_APP),
                )
                .associateBy({ it }, { AssertionInvocationGroup.BLOCKING })

    val RESIZE_SPLITSCREEN_ASSERTIONS =
        COMMON_ASSERTIONS +
            listOf(
                    AppLayerIsVisibleAlways(SPLIT_SCREEN_PRIMARY_APP),
                    AppLayerIsVisibleAlways(SPLIT_SCREEN_SECONDARY_APP),
                    AppWindowIsVisibleAlways(SPLIT_SCREEN_PRIMARY_APP),
                    AppWindowIsVisibleAlways(SPLIT_SCREEN_SECONDARY_APP),
                )
                .associateBy({ it }, { AssertionInvocationGroup.BLOCKING })
}
