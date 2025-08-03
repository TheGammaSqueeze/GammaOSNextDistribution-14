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

package android.tools.device.apphelpers

import android.app.Instrumentation
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.tools.traces.component.ComponentNameMatcher
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/** Helper to launch the Maps app (not compatible with AOSP) */
class MapsAppHelper
@JvmOverloads
constructor(
    instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation(),
    pkgManager: PackageManager = instrumentation.context.packageManager
) :
    StandardAppHelper(
        instrumentation,
        getMapLauncherName(pkgManager),
        getMapComponent(pkgManager)
    ) {

    /** Search for a specific query in Maps and select the first address matching the query */
    fun doSearch(query: String) {
        dismissDialogs()
        inputSearch(query)
        startSearch(query)

        // Best effort attempt to make sure the direction button shows up.
        // "Best effort" because when searching for generic items such as "gas station",
        // the direction button won't show up if there are multiple search results.
        getDirectionsButton() ?: error("Unable to find start directions button")
    }

    /** Get directions to currently selected address */
    fun getDirections() {
        dismissDialogs() // pull up dialog
        val directionsButton: UiObject2 =
            getDirectionsButton() ?: error("Unable to find start directions button")
        directionsButton.click()
        uiDevice.waitForIdle()
        var currentLocation: UiObject2? = getYourLocation()
        if (currentLocation == null) {
            val startPointBox: UiObject2 =
                getStartPointBox() ?: error("Unable to find starting point box")
            startPointBox.click()
            uiDevice.waitForIdle()
            currentLocation = getYourLocation() ?: error("Unable to find your location option")
            currentLocation.click()
            uiDevice.waitForIdle()
        }

        dismissDialogs() // location services / get a ride

        getStartNavigationButton() ?: error("Unable to find start navigation button")
    }

    /** Start the navigation mode for the selected address */
    fun startNavigation() {
        dismissDialogs() // get a ride

        var startNavigationButton: UiObject2 =
            getStartNavigationButton() ?: error("Unable to find start navigation button")
        startNavigationButton.click()
        uiDevice.waitForIdle()

        // "Welcome to Google Maps Navigation"
        // "How navigation data makes Maps better"
        // "Welcome to driving mode"
        dismissDialogs()

        waitForNavigationToStart()
    }

    fun waitForNavigationToStart() {
        var hasCloseNavigationDesc = getCloseNavigationButton() != null
        var tryCounter = 0
        while (tryCounter < MAX_RETRY_TIMES && !hasCloseNavigationDesc) {
            dismissDialogs()
            hasCloseNavigationDesc = getCloseNavigationButton(WAIT_TIMEOUT) != null
            tryCounter += 1
        }
        getCloseNavigationButton() ?: error("Unable to find close navigation button")
    }

    private fun inputSearch(query: String) {
        // Navigate backwards if necessary, launching Maps does not always start on the query screen
        for (backup in 5 downTo 1) {
            if (hasSearchBar(0)) {
                break
            } else {
                uiDevice.pressBack()
                uiDevice.waitForIdle()
            }
        }

        // Select search bar
        val searchSelect: UiObject2 =
            getSelectableSearchBar() ?: error("No selectable search bar found.")
        searchSelect.click()
        uiDevice.waitForIdle()

        // Edit search query
        val searchEdit: UiObject2 =
            getEditableSearchBar() ?: error("Not editable search bar found.")
        searchEdit.clear()
        searchEdit.setText(query)
        uiDevice.waitForIdle()
    }

    private fun startSearch(query: String) {
        // Search and click first matching result, then wait for the directions option
        val SEARCH_TIMEOUT_IN_MSECS: Long = 25000
        val pattern: Pattern = Pattern.compile(query, Pattern.CASE_INSENSITIVE)
        for (retry in 0 until MAX_RETRY_TIMES) {
            // Only wait for results to load during the first retry to preserve search timeout
            val firstAddressResult =
                uiDevice.wait(
                    Until.findObject(By.pkg(UI_PACKAGE).clazz(UI_TEXTVIEW_CLASS).text(pattern)),
                    if (retry == 0) SEARCH_TIMEOUT_IN_MSECS else 0
                )
                    ?: continue
            firstAddressResult.click()
            uiDevice.waitForIdle()
            return
        }
        error("Did not detect address result after 25 seconds")
    }

    private fun hasSearchBar(wait_time: Long = WAIT_TIMEOUT): Boolean {
        return getSelectableSearchBar(wait_time) != null || getEditableSearchBar(wait_time) != null
    }

    private fun getSelectableSearchBar(wait_time: Long = WAIT_TIMEOUT): UiObject2? {
        return uiDevice.wait(
            Until.findObject(By.res(UI_PACKAGE, UI_SELECTABLE_SEARCHBAR_ID)),
            wait_time
        )
    }

    private fun getEditableSearchBar(wait_time: Long = WAIT_TIMEOUT): UiObject2? {
        return uiDevice.wait(
            Until.findObject(By.res(UI_PACKAGE, UI_EDITABLE_SEARCHBAR_ID)),
            wait_time
        )
    }

    private fun getDirectionsButton(wait_time: Long = WAIT_TIMEOUT): UiObject2? {
        return uiDevice.wait(
            Until.findObject(By.descContains(UI_DIRECTIONS_BUTTON_DESC)),
            wait_time
        )
    }

    private fun getStartPointBox(wait_time: Long = WAIT_TIMEOUT): UiObject2? {
        return uiDevice.wait(Until.findObject(By.text(UI_STARTING_POINT_DESC)), wait_time)
    }

    private fun getYourLocation(wait_time: Long = WAIT_TIMEOUT): UiObject2? {
        return uiDevice.wait(Until.findObject(By.text(UI_YOUR_LOCATION_DESC)), wait_time)
    }

    private fun getStartNavigationButton(wait_time: Long = WAIT_TIMEOUT): UiObject2? {
        return uiDevice.wait(Until.findObject(By.descContains(UI_START_NAVIGATION_DESC)), wait_time)
    }

    private fun getCloseNavigationButton(wait_time: Long = WAIT_TIMEOUT): UiObject2? {
        return uiDevice.wait(Until.findObject(By.descContains(UI_CLOSE_NAVIGATION_DESC)), wait_time)
    }

    private fun dismissDialogs() {
        var tryDismiss = true
        val pattern = Pattern.compile(UI_CONFIRMATION_BUTTONS_PATTERN, Pattern.CASE_INSENSITIVE)
        while (tryDismiss) {
            tryDismiss = false
            val button: UiObject2? = uiDevice.wait(Until.findObject(By.text(pattern)), WAIT_TIMEOUT)
            if (button != null) {
                tryDismiss = true
                button.click()
                uiDevice.waitForIdle()
            }
        }
    }

    companion object {
        const val INTENT_NAVIGATION = "google.navigation:q=`Golden Gate Bridge`"
        const val INTENT_LOCATION = "google.streetview:cbll=46.414382,10.013988"

        private const val MAX_RETRY_TIMES = 5
        private const val WAIT_TIMEOUT: Long = 5000
        private const val UI_PACKAGE = "com.google.android.apps.maps"
        private const val UI_SELECTABLE_SEARCHBAR_ID = "search_omnibox_text_box"
        private const val UI_EDITABLE_SEARCHBAR_ID = "search_omnibox_edit_text"
        private const val UI_DIRECTIONS_BUTTON_DESC = "Directions"
        private const val UI_TEXTVIEW_CLASS = "android.widget.TextView"
        private const val UI_START_NAVIGATION_DESC = "Start driving navigation"
        private const val UI_STARTING_POINT_DESC = "Choose start location"
        private const val UI_YOUR_LOCATION_DESC = "Your location"
        private const val UI_CLOSE_NAVIGATION_DESC = "Close navigation"
        private const val UI_CONFIRMATION_BUTTONS_PATTERN =
            "OK|TURN ON|YES, I'M IN|GOT IT|ACCEPT & CONTINUE|START|SKIP|DISMISS"

        fun getMapIntent(intentString: String): Intent {
            val gmmIntentUri = Uri.parse(intentString)
            val intent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return intent
        }

        private fun getResolveInfo(pkgManager: PackageManager): ResolveInfo {
            val mapsIntent = getMapIntent(INTENT_LOCATION)
            return pkgManager.resolveActivity(mapsIntent, PackageManager.MATCH_DEFAULT_ONLY)
                ?: error("unable to resolve camera activity")
        }

        private fun getMapComponent(pkgManager: PackageManager): ComponentNameMatcher {
            val resolveInfo = getResolveInfo(pkgManager)
            return ComponentNameMatcher(
                resolveInfo.activityInfo.packageName,
                className = resolveInfo.activityInfo.name
            )
        }

        private fun getMapLauncherName(pkgManager: PackageManager): String {
            val resolveInfo = getResolveInfo(pkgManager)
            return resolveInfo.loadLabel(pkgManager).toString()
        }
    }
}
