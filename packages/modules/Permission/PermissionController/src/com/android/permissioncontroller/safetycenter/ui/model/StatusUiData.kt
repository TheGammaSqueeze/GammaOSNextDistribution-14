package com.android.permissioncontroller.safetycenter.ui.model

import android.content.Context
import android.os.Build.VERSION_CODES.TIRAMISU
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterStatus
import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK
import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN
import android.util.Log
import androidx.annotation.RequiresApi
import com.android.permissioncontroller.R

/** UI model representation of a Status Card. */
@RequiresApi(TIRAMISU)
data class StatusUiData(
    private val status: SafetyCenterStatus,
    @get:JvmName("hasIssues") val hasIssues: Boolean = false,
    @get:JvmName("hasPendingActions") val hasPendingActions: Boolean = false
) {

    constructor(
        safetyCenterData: SafetyCenterData
    ) : this(safetyCenterData.status, hasIssues = safetyCenterData.issues.size > 0)

    // For convenience use in Java.
    fun copyForPendingActions(hasPendingActions: Boolean) =
        copy(hasPendingActions = hasPendingActions)

    companion object {
        private val TAG: String = StatusUiData::class.java.simpleName
        fun getStatusImageResId(severityLevel: Int) =
            when (severityLevel) {
                OVERALL_SEVERITY_LEVEL_UNKNOWN,
                OVERALL_SEVERITY_LEVEL_OK -> R.drawable.safety_status_info
                SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION ->
                    R.drawable.safety_status_recommendation
                SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING ->
                    R.drawable.safety_status_warn
                else -> {
                    Log.w(TAG, "Unexpected OverallSeverityLevel: $severityLevel")
                    R.drawable.safety_status_info
                }
            }
    }

    val title: CharSequence by status::title
    val originalSummary: CharSequence by status::summary
    val severityLevel: Int by status::severityLevel

    val statusImageResId: Int
        get() = getStatusImageResId(severityLevel)

    fun getSummary(context: Context): CharSequence {
        return if (hasPendingActions) {
            // Use a different string for the special quick-settings-only hasPendingActions state.
            context.getString(R.string.safety_center_qs_status_summary)
        } else {
            originalSummary
        }
    }

    fun getContentDescription(context: Context): CharSequence {
        return context.getString(
            R.string.safety_status_preference_title_and_summary_content_description,
            title,
            getSummary(context)
        )
    }

    val isRefreshInProgress: Boolean
        get() =
            when (status.refreshStatus) {
                SafetyCenterStatus.REFRESH_STATUS_FULL_RESCAN_IN_PROGRESS,
                SafetyCenterStatus.REFRESH_STATUS_DATA_FETCH_IN_PROGRESS -> true
                else -> false
            }

    fun shouldShowRescanButton(): Boolean {
        return !hasIssues &&
            !hasPendingActions &&
            when (severityLevel) {
                OVERALL_SEVERITY_LEVEL_OK,
                OVERALL_SEVERITY_LEVEL_UNKNOWN -> true
                else -> false
            }
    }

    enum class ButtonToShow {
        RESCAN,
        REVIEW_SETTINGS
    }
    val buttonToShow: ButtonToShow?
        get() =
            when {
                hasIssues -> null
                hasPendingActions -> ButtonToShow.REVIEW_SETTINGS
                severityLevel == OVERALL_SEVERITY_LEVEL_OK ||
                    severityLevel == OVERALL_SEVERITY_LEVEL_UNKNOWN -> ButtonToShow.RESCAN
                else -> null
            }
}
