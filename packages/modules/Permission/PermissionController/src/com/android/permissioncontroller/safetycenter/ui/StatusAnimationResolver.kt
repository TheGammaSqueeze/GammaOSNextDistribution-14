/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.permissioncontroller.safetycenter.ui

import android.os.Build
import android.safetycenter.SafetyCenterStatus
import android.util.Log
import androidx.annotation.RequiresApi
import com.android.permissioncontroller.R

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
object StatusAnimationResolver {

    private val LOG_TAG = StatusAnimationResolver::class.java.simpleName

    @JvmStatic
    fun getScanningStartAnimation(severityLevel: Int): Int {
        return when (severityLevel) {
            SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN,
            SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK -> R.drawable.status_info_to_scanning_anim
            SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION ->
                R.drawable.status_recommend_to_scanning_anim
            SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING ->
                R.drawable.status_warn_to_scanning_anim
            else -> {
                Log.w(LOG_TAG, String.format("Unexpected severity level: %s", severityLevel))
                R.drawable.status_info_to_scanning_anim
            }
        }
    }

    @JvmStatic
    fun getScanningAnimation(severityLevel: Int): Int {
        return when (severityLevel) {
            SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN,
            SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK -> R.drawable.status_scanning_anim_info
            SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION ->
                R.drawable.status_scanning_anim_recommend
            SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING ->
                R.drawable.status_scanning_anim_warn
            else -> {
                Log.w(LOG_TAG, String.format("Unexpected severity level: %s", severityLevel))
                R.drawable.status_scanning_anim_info
            }
        }
    }

    @JvmStatic
    fun getScanningEndAnimation(fromSeverityLevel: Int, toSeverityLevel: Int): Int {
        return when (fromSeverityLevel) {
            SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN,
            SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK ->
                getTransitionAnimationFromInfo(toSeverityLevel)
            SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING ->
                getTransitionAnimationFromWarn(toSeverityLevel)
            SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION ->
                getTransitionAnimationFromRecommend(toSeverityLevel)
            else -> {
                Log.w(LOG_TAG, String.format("Unexpected severity level: %s", fromSeverityLevel))
                getTransitionAnimationFromInfo(toSeverityLevel)
            }
        }
    }

    @JvmStatic
    private fun getTransitionAnimationFromInfo(toSeverityLevel: Int): Int {
        return when (toSeverityLevel) {
            SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN,
            SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK ->
                R.drawable.status_scanning_end_anim_info_to_info
            SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING ->
                R.drawable.status_scanning_end_anim_info_to_warn
            SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION ->
                R.drawable.status_scanning_end_anim_info_to_recommend
            else -> {
                Log.w(LOG_TAG, String.format("Unexpected severity level: %s", toSeverityLevel))
                R.drawable.status_scanning_end_anim_info_to_info
            }
        }
    }

    @JvmStatic
    private fun getTransitionAnimationFromRecommend(toSeverityLevel: Int): Int {
        return when (toSeverityLevel) {
            SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN,
            SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK ->
                R.drawable.status_scanning_end_anim_recommend_to_info
            SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING ->
                R.drawable.status_scanning_end_anim_recommend_to_warn
            SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION ->
                R.drawable.status_scanning_end_anim_recommend_to_recommend
            else -> {
                Log.w(LOG_TAG, String.format("Unexpected severity level: %s", toSeverityLevel))
                R.drawable.status_scanning_end_anim_recommend_to_recommend
            }
        }
    }

    @JvmStatic
    private fun getTransitionAnimationFromWarn(toSeverityLevel: Int): Int {
        return when (toSeverityLevel) {
            SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN,
            SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK ->
                R.drawable.status_scanning_end_anim_warn_to_info
            SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION ->
                R.drawable.status_scanning_end_anim_warn_to_recommend
            SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING ->
                R.drawable.status_scanning_end_anim_warn_to_warn
            else -> {
                Log.w(LOG_TAG, String.format("Unexpected severity level: %s", toSeverityLevel))
                R.drawable.status_scanning_end_anim_warn_to_warn
            }
        }
    }

    @JvmStatic
    fun getStatusChangeAnimation(fromSeverity: Int, toSeverity: Int): Int =
        if (
            fromSeverity == toSeverity &&
                fromSeverity != SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK
        ) {
            0
        } else
            when (fromSeverity) {
                SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK ->
                    R.drawable.safety_status_info_to_info_anim
                SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION ->
                    R.drawable.safety_status_recommend_to_info_anim
                SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING -> {
                    if (toSeverity == SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK) {
                        R.drawable.safety_status_warn_to_info_anim
                    } else {
                        R.drawable.safety_status_warn_to_recommend_anim
                    }
                }
                else -> 0
            }
}
