package com.android.onboarding.contracts

import android.content.Context
import android.os.Bundle

/** Equivalent to [Activity.registerForActivityResult] when a result is not expected. */
fun <I> Context.registerForActivityLaunch(contract: OnboardingActivityApiContract<I, *>) =
  ActivityLauncher(this, contract)

/** A wrapper around a request to launch a particular activity via a contract. */
class ActivityLauncher<I>(
  private val context: Context,
  private val contract: OnboardingActivityApiContract<I, *>,
) {

  fun launch(arg: I) {
    context.startActivity(contract.createIntentDirectly(context, arg))
  }

  /** Launches an activity with given [intentFlags] and contract arguments [args]. */
  fun launch(arg: I, intentFlags: Int): Long {
    val intent = contract.createIntentDirectly(context, arg)
    intent.flags = intentFlags
    context.startActivity(intent)
    return intent.getLongExtra(EXTRA_ONBOARDING_NODE_ID, UNKNOWN_NODE_ID)
  }

  fun launch(arg: I, options: Bundle?) {
    context.startActivity(contract.createIntentDirectly(context, arg), options)
  }
}
