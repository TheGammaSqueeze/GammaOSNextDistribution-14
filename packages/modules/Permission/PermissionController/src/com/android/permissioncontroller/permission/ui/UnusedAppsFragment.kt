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
@file:Suppress("DEPRECATION")

package com.android.permissioncontroller.permission.ui

import android.Manifest.permission_group
import android.app.AlertDialog
import android.app.Application
import android.app.Dialog
import android.content.Intent
import android.icu.text.MessageFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.android.permissioncontroller.Constants.EXTRA_SESSION_ID
import com.android.permissioncontroller.Constants.INVALID_SESSION_ID
import com.android.permissioncontroller.R
import com.android.permissioncontroller.hibernation.isHibernationEnabled
import com.android.permissioncontroller.permission.ui.model.UnusedAppsViewModel
import com.android.permissioncontroller.permission.ui.model.UnusedAppsViewModel.UnusedPackageInfo
import com.android.permissioncontroller.permission.ui.model.UnusedAppsViewModel.UnusedPeriod
import com.android.permissioncontroller.permission.ui.model.UnusedAppsViewModel.UnusedPeriod.Companion.allPeriods
import com.android.permissioncontroller.permission.ui.model.UnusedAppsViewModelFactory
import com.android.permissioncontroller.permission.utils.KotlinUtils
import java.text.Collator

/**
 * A fragment displaying all applications that are unused as well as the option to remove them and
 * to open them.
 */
class UnusedAppsFragment<PF, UnusedAppPref> : Fragment() where
PF : PreferenceFragmentCompat,
PF : UnusedAppsFragment.Parent<UnusedAppPref>,
UnusedAppPref : Preference,
UnusedAppPref : RemovablePref {

    private lateinit var viewModel: UnusedAppsViewModel
    private lateinit var collator: Collator
    private var sessionId: Long = 0L
    private var isFirstLoad = false

    companion object {
        const val INFO_MSG_CATEGORY = "info_msg_category"
        private const val SHOW_LOAD_DELAY_MS = 200L
        private const val INFO_MSG_KEY = "info_msg"
        private const val ELEVATION_HIGH = 8f
        private val LOG_TAG = UnusedAppsFragment::class.java.simpleName

        @JvmStatic
        fun <PF, UnusedAppPref> newInstance(): UnusedAppsFragment<PF, UnusedAppPref> where
        PF : PreferenceFragmentCompat,
        PF : UnusedAppsFragment.Parent<UnusedAppPref>,
        UnusedAppPref : Preference,
        UnusedAppPref : RemovablePref {
            return UnusedAppsFragment()
        }

        /**
         * Create the args needed for this fragment
         *
         * @param sessionId The current session Id
         * @return A bundle containing the session Id
         */
        @JvmStatic
        fun createArgs(sessionId: Long): Bundle {
            val bundle = Bundle()
            bundle.putLong(EXTRA_SESSION_ID, sessionId)
            return bundle
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val preferenceFragment: PF = requirePreferenceFragment()
        isFirstLoad = true

        collator =
            Collator.getInstance(context!!.getResources().getConfiguration().getLocales().get(0))
        sessionId = arguments!!.getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID)
        val factory = UnusedAppsViewModelFactory(activity!!.application, sessionId)
        viewModel = ViewModelProvider(this, factory).get(UnusedAppsViewModel::class.java)
        viewModel.unusedPackageCategoriesLiveData.observe(
            this,
            Observer {
                it?.let { pkgs ->
                    updatePackages(pkgs)
                    preferenceFragment.setLoadingState(loading = false, animate = true)
                }
            }
        )

        activity?.getActionBar()?.setDisplayHomeAsUpEnabled(true)

        if (!viewModel.unusedPackageCategoriesLiveData.isInitialized) {
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed(
                {
                    if (!viewModel.unusedPackageCategoriesLiveData.isInitialized) {
                        preferenceFragment.setLoadingState(loading = true, animate = true)
                    } else {
                        updatePackages(viewModel.unusedPackageCategoriesLiveData.value!!)
                    }
                },
                SHOW_LOAD_DELAY_MS
            )
        } else {
            updatePackages(viewModel.unusedPackageCategoriesLiveData.value!!)
        }
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        activity?.actionBar?.setElevation(ELEVATION_HIGH)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val preferenceFragment: PF = requirePreferenceFragment()
        if (isHibernationEnabled()) {
            preferenceFragment.setTitle(getString(R.string.unused_apps_page_title))
        } else {
            preferenceFragment.setTitle(getString(R.string.permission_removed_page_title))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun requirePreferenceFragment(): PF {
        return requireParentFragment() as PF
    }

    /** Create [PreferenceScreen] in the parent fragment. */
    private fun createPreferenceScreen() {
        val preferenceFragment: PF = requirePreferenceFragment()
        val preferenceScreen =
            preferenceFragment.preferenceManager.inflateFromResource(
                context!!,
                R.xml.unused_app_categories,
                /* rootPreferences= */ null
            )

        for (period in allPeriods) {
            val periodCat = PreferenceCategory(context!!)
            periodCat.key = period.name
            periodCat.order = 0
            preferenceScreen.addPreference(periodCat)
        }
        preferenceFragment.preferenceScreen = preferenceScreen

        val infoMsgCategory = preferenceScreen.findPreference<PreferenceCategory>(INFO_MSG_CATEGORY)
        val footerPreference = preferenceFragment.createFooterPreference()
        footerPreference.key = INFO_MSG_KEY
        infoMsgCategory?.addPreference(footerPreference)
    }

    @Suppress("UNCHECKED_CAST")
    private fun updatePackages(categorizedPackages: Map<UnusedPeriod, List<UnusedPackageInfo>>) {
        val preferenceFragment: PF = requirePreferenceFragment()
        if (preferenceFragment.preferenceScreen == null) {
            createPreferenceScreen()
        }
        val preferenceScreen: PreferenceScreen = preferenceFragment.preferenceScreen

        // Remove stale preferences
        val removedPrefs = mutableMapOf<String, UnusedAppPref>()
        for (period in allPeriods) {
            val category = preferenceScreen.findPreference<PreferenceCategory>(period.name)!!
            for (i in 0 until category.preferenceCount) {
                val pref = category.getPreference(i) as UnusedAppPref
                val contains =
                    categorizedPackages[period]?.any { (pkgName, user, _) ->
                        val key = createKey(pkgName, user)
                        pref.key == key
                    }
                if (contains != true) {
                    removedPrefs[pref.key] = pref
                }
            }

            for ((_, pref) in removedPrefs) {
                category.removePreference(pref)
            }
        }

        var allCategoriesEmpty = true
        for ((period, packages) in categorizedPackages) {
            val category = preferenceScreen.findPreference<PreferenceCategory>(period.name)!!
            val months = period.months
            category.title =
                MessageFormat.format(
                    getString(R.string.last_opened_category_title),
                    mapOf("count" to months)
                )
            category.isVisible = packages.isNotEmpty()
            if (packages.isNotEmpty()) {
                allCategoriesEmpty = false
            }

            for ((pkgName, user, isSystemApp, permSet) in packages) {
                val revokedPerms = permSet.toList()
                val key = createKey(pkgName, user)

                var pref = category.findPreference<UnusedAppPref>(key)
                if (pref == null) {
                    pref =
                        removedPrefs[key]
                            ?: preferenceFragment.createUnusedAppPref(
                                activity!!.application,
                                pkgName,
                                user
                            )
                    pref.key = key
                    pref.title = KotlinUtils.getPackageLabel(activity!!.application, pkgName, user)
                }

                pref.setRemoveClickRunnable { viewModel.requestUninstallApp(this, pkgName, user) }
                pref.setRemoveComponentEnabled(!isSystemApp)

                pref.onPreferenceClickListener =
                    Preference.OnPreferenceClickListener { _ ->
                        viewModel.navigateToAppInfo(pkgName, user, sessionId)
                        true
                    }

                val mostImportant = getMostImportantGroup(revokedPerms)
                val importantLabel = KotlinUtils.getPermGroupLabel(context!!, mostImportant)
                pref.summary =
                    when {
                        revokedPerms.isEmpty() -> null
                        revokedPerms.size == 1 ->
                            getString(R.string.auto_revoked_app_summary_one, importantLabel)
                        revokedPerms.size == 2 -> {
                            val otherLabel =
                                if (revokedPerms[0] == mostImportant) {
                                    KotlinUtils.getPermGroupLabel(context!!, revokedPerms[1])
                                } else {
                                    KotlinUtils.getPermGroupLabel(context!!, revokedPerms[0])
                                }
                            getString(
                                R.string.auto_revoked_app_summary_two,
                                importantLabel,
                                otherLabel
                            )
                        }
                        else ->
                            getString(
                                R.string.auto_revoked_app_summary_many,
                                importantLabel,
                                "${revokedPerms.size - 1}"
                            )
                    }
                category.addPreference(pref)
                KotlinUtils.sortPreferenceGroup(category, this::comparePreference, false)
            }
        }

        preferenceFragment.setEmptyState(allCategoriesEmpty)

        if (isFirstLoad) {
            if (categorizedPackages.any { (_, packages) -> packages.isNotEmpty() }) {
                isFirstLoad = false
            }
            Log.i(LOG_TAG, "sessionId: $sessionId Showed Auto Revoke Page")
            for (period in allPeriods) {
                Log.i(
                    LOG_TAG,
                    "sessionId: $sessionId $period unused: " + "${categorizedPackages[period]}"
                )
                for (revokedPackageInfo in categorizedPackages[period]!!) {
                    for (groupName in revokedPackageInfo.revokedGroups) {
                        val isNewlyRevoked = period.isNewlyUnused()
                        viewModel.logAppView(
                            revokedPackageInfo.packageName,
                            revokedPackageInfo.user,
                            groupName,
                            isNewlyRevoked
                        )
                    }
                }
            }
        }
    }

    private fun comparePreference(lhs: Preference, rhs: Preference): Int {
        var result = collator.compare(lhs.title.toString(), rhs.title.toString())
        if (result == 0) {
            result = lhs.key.compareTo(rhs.key)
        }
        return result
    }

    private fun createKey(packageName: String, user: UserHandle): String {
        return "$packageName:${user.identifier}"
    }

    private fun getMostImportantGroup(groupNames: List<String>): String {
        return when {
            groupNames.contains(permission_group.LOCATION) -> permission_group.LOCATION
            groupNames.contains(permission_group.MICROPHONE) -> permission_group.MICROPHONE
            groupNames.contains(permission_group.CAMERA) -> permission_group.CAMERA
            groupNames.contains(permission_group.CONTACTS) -> permission_group.CONTACTS
            groupNames.contains(permission_group.STORAGE) -> permission_group.STORAGE
            groupNames.contains(permission_group.CALENDAR) -> permission_group.CALENDAR
            groupNames.isNotEmpty() -> groupNames[0]
            else -> ""
        }
    }

    private fun createDisableDialog(packageName: String, user: UserHandle) {
        val dialog = DisableDialog()

        val args = Bundle()
        args.putString(Intent.EXTRA_PACKAGE_NAME, packageName)
        args.putParcelable(Intent.EXTRA_USER, user)
        dialog.arguments = args

        dialog.isCancelable = true

        dialog.show(childFragmentManager.beginTransaction(), DisableDialog::class.java.name)
    }

    class DisableDialog : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val fragment = parentFragment as UnusedAppsFragment<*, *>
            val packageName = arguments!!.getString(Intent.EXTRA_PACKAGE_NAME)!!
            val user = arguments!!.getParcelable<UserHandle>(Intent.EXTRA_USER)!!
            val b =
                AlertDialog.Builder(context!!)
                    .setMessage(R.string.app_disable_dlg_text)
                    .setPositiveButton(R.string.app_disable_dlg_positive) { _, _ ->
                        fragment.viewModel.disableApp(packageName, user)
                    }
                    .setNegativeButton(R.string.cancel, null)
            val d: Dialog = b.create()
            d.setCanceledOnTouchOutside(true)
            return d
        }
    }

    /** Interface that the parent fragment must implement. */
    interface Parent<UnusedAppPref> where
    UnusedAppPref : Preference,
    UnusedAppPref : RemovablePref {

        /**
         * Set the title of the current settings page.
         *
         * @param title the title of the current settings page
         */
        fun setTitle(title: CharSequence)

        /**
         * Creates the footer preference that explains why permissions have been re-used and how an
         * app can re-request them.
         */
        fun createFooterPreference(): Preference

        /**
         * Sets the loading state of the view.
         *
         * @param loading whether the view is loading
         * @param animate whether the load state should change with a fade animation
         */
        fun setLoadingState(loading: Boolean, animate: Boolean)

        /**
         * Creates a preference which represents an app that is unused. Has the app icon and label,
         * as well as a button to uninstall/disable the app, and a button to open the app.
         *
         * @param app The current application
         * @param packageName The name of the package whose icon this preference will retrieve
         * @param user The user whose package icon will be retrieved
         */
        fun createUnusedAppPref(
            app: Application,
            packageName: String,
            user: UserHandle,
        ): UnusedAppPref

        /**
         * Updates the state based on whether the content is empty.
         *
         * @param empty whether the content is empty
         */
        fun setEmptyState(empty: Boolean)
    }
}
