package com.android.healthconnect.controller.managedata

import android.health.connect.HealthDataCategory
import android.icu.text.MessageFormat
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.autodelete.AutoDeleteRange
import com.android.healthconnect.controller.autodelete.AutoDeleteViewModel
import com.android.healthconnect.controller.categories.HealthDataCategoriesFragment
import com.android.healthconnect.controller.shared.preference.HealthPreference
import com.android.healthconnect.controller.shared.preference.HealthPreferenceFragment
import com.android.healthconnect.controller.utils.FeatureUtils
import com.android.healthconnect.controller.utils.logging.ManageDataElement
import com.android.healthconnect.controller.utils.logging.PageName
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint(HealthPreferenceFragment::class)
class ManageDataFragment : Hilt_ManageDataFragment() {

    companion object {
        private const val AUTO_DELETE_PREFERENCE_KEY = "auto_delete"
        private const val DATA_SOURCES_AND_PRIORITY_PREFERENCE_KEY = "data_sources_and_priority"
        private const val SET_UNITS_PREFERENCE_KEY = "set_units"
    }

    init {
        this.setPageName(PageName.MANAGE_DATA_PAGE)
    }

    private val autoDeleteViewModel: AutoDeleteViewModel by activityViewModels()
    @Inject lateinit var featureUtils: FeatureUtils

    private val mAutoDeletePreference: HealthPreference? by lazy {
        preferenceScreen.findPreference(AUTO_DELETE_PREFERENCE_KEY)
    }

    private val mDataSourcesPreference: HealthPreference? by lazy {
        preferenceScreen.findPreference(DATA_SOURCES_AND_PRIORITY_PREFERENCE_KEY)
    }

    private val mSetUnitsPreference: HealthPreference? by lazy {
        preferenceScreen.findPreference(SET_UNITS_PREFERENCE_KEY)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.manage_data_screen, rootKey)

        mAutoDeletePreference?.logName = ManageDataElement.AUTO_DELETE_BUTTON
        mAutoDeletePreference?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_manageData_to_autoDelete)
            true
        }

        if (featureUtils.isNewAppPriorityEnabled()) {
            mDataSourcesPreference?.logName = ManageDataElement.DATA_SOURCES_AND_PRIORITY_BUTTON
            mDataSourcesPreference?.setOnPreferenceClickListener {
                findNavController()
                    .navigate(
                        R.id.action_manageData_to_dataSources,
                        bundleOf(
                            HealthDataCategoriesFragment.CATEGORY_KEY to
                                HealthDataCategory.UNKNOWN))
                true
            }
        } else {
            preferenceScreen.removePreferenceRecursively(DATA_SOURCES_AND_PRIORITY_PREFERENCE_KEY)
        }

        mSetUnitsPreference?.logName = ManageDataElement.SET_UNITS_BUTTON
        mSetUnitsPreference?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_manageData_to_setUnits)
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        autoDeleteViewModel.storedAutoDeleteRange.observe(viewLifecycleOwner) { state ->
            when (state) {
                AutoDeleteViewModel.AutoDeleteState.Loading -> {
                    mAutoDeletePreference?.summary = ""
                }
                is AutoDeleteViewModel.AutoDeleteState.LoadingFailed -> {
                    mAutoDeletePreference?.summary = ""
                }
                is AutoDeleteViewModel.AutoDeleteState.WithData -> {
                    mAutoDeletePreference?.summary = buildSummary(state.autoDeleteRange)
                }
            }
        }
    }

    private fun buildSummary(autoDeleteRange: AutoDeleteRange): String {
        return when (autoDeleteRange) {
            AutoDeleteRange.AUTO_DELETE_RANGE_NEVER -> getString(R.string.range_off)
            AutoDeleteRange.AUTO_DELETE_RANGE_THREE_MONTHS -> {
                val count = AutoDeleteRange.AUTO_DELETE_RANGE_THREE_MONTHS.numberOfMonths
                MessageFormat.format(
                    getString(R.string.range_after_x_months), mapOf("count" to count))
            }
            AutoDeleteRange.AUTO_DELETE_RANGE_EIGHTEEN_MONTHS -> {
                val count = AutoDeleteRange.AUTO_DELETE_RANGE_EIGHTEEN_MONTHS.numberOfMonths
                MessageFormat.format(
                    getString(R.string.range_after_x_months), mapOf("count" to count))
            }
        }
    }
}
