package com.android.onboarding.contracts.fragment

import android.os.Bundle
import android.support.v4.app.Fragment

/**
 * This abstract class allows setting and getting the input argument from the subclass of fragment.
 * When extending this abstract class, you must declare a single argument type and implement the
 * [performExtractArguments] and [performSetArguments] methods.
 *
 * When using the fragment, [setTypedArguments] and [getTypedArguments] should be used in place of
 * [setArguments] and [getArguments].
 *
 * @param <I> The type of the input argument.
 */
abstract class OnboardingFragment<I> : Fragment() {

  /**
   * Supplies the construction arguments for this fragment.
   *
   * @param argument The argument Bundle.
   */
  @Deprecated("Use setTypedArguments() instead")
  final override fun setArguments(args: Bundle?) {
    super.setArguments(args)
  }

  /**
   * Sets the typed argument that is given by the onboarding contract.
   *
   * @param argument The typed argument.
   */
  fun setTypedArguments(argument: I) {
    setArguments(performSetArguments(argument))
  }

  /**
   * Gets the typed argument that is given by the onboarding contract.
   *
   * @return The typed argument.
   */
  fun getTypedArguments(): I? {
    return performExtractArguments(getArguments())
  }

  /**
   * Performs the setting of the typed argument of the onboarding contract.
   *
   * @param args The typed argument that would be set in the bundle of fragment argument.
   * @return The bundle.
   */
  protected abstract fun performSetArguments(args: I): Bundle

  /**
   * Performs the extraction of the typed argument of the onboarding contract.
   *
   * @param args The bundle that can be available from getArguments().
   * @return The typed argument that would be extracted from the bundle of fragment argument.
   */
  protected abstract fun performExtractArguments(args: Bundle?): I?
}
