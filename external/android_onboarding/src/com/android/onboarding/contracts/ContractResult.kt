package com.android.onboarding.contracts

import android.content.Intent
import com.android.onboarding.contracts.annotations.InternalOnboardingApi

/** The result of encoding a contract result for passing as an activity result. */
sealed interface ContractResult {
  val resultCode: Int
  val intent: Intent?

  data class Success
  @JvmOverloads
  constructor(override val resultCode: Int, override val intent: Intent? = null) : ContractResult

  data class Failure
  @JvmOverloads
  constructor(
    override val resultCode: Int,
    override val intent: Intent? = null,
    val reason: String? = null
  ) : ContractResult
}

@InternalOnboardingApi
data class UnknownContractResult(
  override val resultCode: Int,
  override val intent: Intent? = null,
) : ContractResult

interface ContractResultSerializer<R> {
  fun read(result: ContractResult): R

  fun write(result: R): ContractResult
}
