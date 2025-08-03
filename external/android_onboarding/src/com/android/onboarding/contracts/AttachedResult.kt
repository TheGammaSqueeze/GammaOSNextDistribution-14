package com.android.onboarding.contracts

/**
 * Represents the attached result after running [OnboardingActivityApiContract.attach].
 *
 * @property validated `true` if the intent is valid, `false` otherwise.
 */
data class AttachedResult(val validated: Boolean)
