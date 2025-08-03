package com.android.onboarding.contracts

import android.content.Intent

/**
 * Interface implemented by a [OnboardingActivityApiContract] to allow it to be used with
 * [findExecutingContract].
 *
 * This allows a single activity to implement multiple contracts, as long as those contracts
 * can be distinguished based on properties of the [Intent].
 *
 * Recommended usage:
 * ```
 * val contracts = arrayOf(RedContract(), BlueContract(), GreenContract())
 * val contract = intent?.findExecutingContract(*contracts) ?: RedContract()
 * ```
 */
interface IdentifyExecutingContract {
  /** Returns true if this [OnboardingActivityApiContract] is currently executing. */
  fun isExecuting(intent: Intent): Boolean
}

/**
 * Find which of multiple non-overlapping contracts are executing.
 *
 * See [IdentifyExecutingContract] for usage.
 */
fun <I : IdentifyExecutingContract> Intent.findExecutingContract(vararg contracts: I) =
  contracts.firstOrNull { it.isExecuting(this) }
