package com.android.onboarding.tasks

import android.os.PersistableBundle
import com.google.errorprone.annotations.CanIgnoreReturnValue

/**
 * Abstract class representing the contract for an onboarding task.
 *
 * This class defines the contract for an onboarding task, specifying how task arguments and results
 * should be handled. Implementing classes are expected to provide concrete implementations for
 * encoding and extracting task arguments/results.
 *
 * @param Args The type representing the task arguments.
 * @param Result The type representing the task result.
 */
interface OnboardingTaskContract<Args, Result> {
  /**
   * Returns the package name associated with this contract.
   *
   * @return The package name.
   */
  val packageName: String

  /**
   * Validates the integrity of the provided task arguments.
   *
   * This function checks whether the provided task arguments are valid and returns a Boolean
   * indicating the validation result.
   *
   * @param args The task arguments to validate.
   * @return `true` if the task arguments are valid, `false` otherwise.
   */
  @CanIgnoreReturnValue fun validate(args: Args?): Boolean

  /**
   * Encodes task arguments into a [PersistableBundle] object.
   *
   * @param args The task arguments to encode.
   * @return The encoded [PersistableBundle].
   */
  fun encodeArgs(args: Args): PersistableBundle

  /**
   * Extracts task arguments from a [PersistableBundle] and returns them as an [Args].
   *
   * @param bundle The [PersistableBundle] containing the encoded task arguments.
   * @return The extracted task arguments.
   */
  fun extractArgs(bundle: PersistableBundle): Args

  /**
   * Encodes a task result into a [PersistableBundle].
   *
   * @param result The task result to encode.
   * @return The encoded [PersistableBundle].
   */
  fun encodeResult(result: Result): PersistableBundle

  /**
   * Extracts task results from a [PersistableBundle] and returns them as a [Result].
   *
   * @param bundle The [PersistableBundle] containing the encoded task results.
   * @return The extracted task results.
   */
  fun extractResult(bundle: PersistableBundle): Result
}

/** Equivalent to [OnboardingTaskContract] for contracts which do not take arguments. */
interface ArgumentFreeOnboardingTaskContract<Result> : OnboardingTaskContract<Unit, Result> {

  override fun validate(args: Unit?): Boolean = true

  override fun encodeArgs(args: Unit): PersistableBundle = PersistableBundle()

  override fun extractArgs(bundle: PersistableBundle) {
    // Do nothing as no arguments.
  }
}

/** Equivalent to [OnboardingTaskContract] for contracts which do not return result. */
interface VoidOnboardingTaskContract<Args> : OnboardingTaskContract<Args, Unit> {

  override fun encodeResult(result: Unit): PersistableBundle = PersistableBundle()

  override fun extractResult(bundle: PersistableBundle) {
    // Do nothing as no result.
  }
}

/**
 * Equivalent to [OnboardingTaskContract] for contracts which do not take arguments or return
 * results.
 */
interface ArgumentFreeVoidOnboardingTaskContract : ArgumentFreeOnboardingTaskContract<Unit> {

  override fun encodeResult(result: Unit): PersistableBundle = PersistableBundle()

  override fun extractResult(bundle: PersistableBundle) {
    // Do nothing as no result.
  }
}
