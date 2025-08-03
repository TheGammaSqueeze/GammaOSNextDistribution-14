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
package com.android.healthconnect.controller.shared.preference

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import android.view.animation.AnimationUtils.loadAnimation
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.RecyclerView
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.shared.HealthPreferenceComparisonCallback
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.HealthConnectLoggerEntryPoint
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.logging.ToolbarElement
import com.android.healthconnect.controller.utils.setupSharedMenu
import com.google.android.material.appbar.AppBarLayout
import dagger.hilt.android.EntryPointAccessors

/** A base fragment that represents a page in Health Connect. */
abstract class HealthPreferenceFragment : PreferenceFragmentCompat() {

    private lateinit var logger: HealthConnectLogger
    private lateinit var loadingView: View
    private lateinit var errorView: TextView
    private lateinit var preferenceContainer: ViewGroup
    private lateinit var prefView: ViewGroup
    private var pageName: PageName = PageName.UNKNOWN_PAGE
    private var isLoading: Boolean = false
    private var hasError: Boolean = false

    fun setPageName(pageName: PageName) {
        this.pageName = pageName
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setupLogger()
        super.onCreate(savedInstanceState)
        val appBarLayout = requireActivity().findViewById<AppBarLayout>(
            com.android.settingslib.collapsingtoolbar.R.id.app_bar)
        appBarLayout?.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        appBarLayout?.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
    }

    override fun onResume() {
        super.onResume()
        logger.setPageId(pageName)
        logger.logPageImpression()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        logger.setPageId(pageName)
        val rootView =
            inflater.inflate(R.layout.preference_frame, container, /*attachToRoot */ false)
        loadingView = rootView.findViewById(R.id.progress_indicator)
        errorView = rootView.findViewById(R.id.error_view)
        prefView = rootView.findViewById(R.id.pref_container)
        preferenceContainer =
            super.onCreateView(inflater, container, savedInstanceState) as ViewGroup
        setLoading(isLoading, animate = false, force = true)
        prefView.addView(preferenceContainer)
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSharedMenu(viewLifecycleOwner, logger)
        logger.logImpression(ToolbarElement.TOOLBAR_SETTINGS_BUTTON)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceComparisonCallback = HealthPreferenceComparisonCallback()
    }

    override fun onCreateAdapter(preferenceScreen: PreferenceScreen): RecyclerView.Adapter<*> {
        val adapter = super.onCreateAdapter(preferenceScreen)
        /* By default, the PreferenceGroupAdapter does setHasStableIds(true). Since each Preference
         * is internally allocated with an auto-incremented ID, it does not allow us to gracefully
         * update only changed preferences based on HealthPreferenceComparisonCallback. In order to
         * allow the list to track the changes, we need to ignore the Preference IDs. */
        adapter.setHasStableIds(false)
        return adapter
    }

    protected fun setLoading(isLoading: Boolean, animate: Boolean = true) {
        setLoading(isLoading, animate, false)
    }

    protected fun setError(hasError: Boolean, @StringRes errorText: Int = R.string.default_error) {
        if (this.hasError != hasError) {
            this.hasError = hasError
            // If there is no created view, there is no reason to animate.
            val canAnimate = view != null
            setViewShown(preferenceContainer, !hasError, canAnimate)
            setViewShown(loadingView, !hasError, canAnimate)
            setViewShown(errorView, hasError, canAnimate)
            errorView.setText(errorText)
        }
    }

    private fun setLoading(loading: Boolean, animate: Boolean, force: Boolean) {
        if (isLoading != loading || force) {
            isLoading = loading
            // If there is no created view, there is no reason to animate.
            val canAnimate = animate && view != null
            setViewShown(preferenceContainer, !loading, canAnimate)
            setViewShown(errorView, shown = false, animate = false)
            setViewShown(loadingView, loading, canAnimate)
        }
    }

    private fun setViewShown(view: View, shown: Boolean, animate: Boolean) {
        if (animate) {
            val animation: Animation =
                loadAnimation(
                    context, if (shown) android.R.anim.fade_in else android.R.anim.fade_out)
            if (shown) {
                view.visibility = View.VISIBLE
            } else {
                animation.setAnimationListener(
                    object : AnimationListener {
                        override fun onAnimationStart(animation: Animation) {}

                        override fun onAnimationRepeat(animation: Animation) {}

                        override fun onAnimationEnd(animation: Animation) {
                            view.visibility = View.INVISIBLE
                        }
                    })
            }
            view.startAnimation(animation)
        } else {
            view.clearAnimation()
            view.visibility = if (shown) View.VISIBLE else View.GONE
        }
    }

    private fun setupLogger() {
        val hiltEntryPoint =
            EntryPointAccessors.fromApplication(
                requireContext().applicationContext, HealthConnectLoggerEntryPoint::class.java)
        logger = hiltEntryPoint.logger()
        logger.setPageId(pageName)
    }
}
