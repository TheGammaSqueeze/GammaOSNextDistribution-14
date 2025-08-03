package com.android.onboarding.contracts

import android.app.Activity
import java.lang.IllegalArgumentException

/** Default activity results */
enum class SuccessFail(val resultCode: Int) {
  SUCCESS(Activity.RESULT_OK),
  CANCELLED(Activity.RESULT_CANCELED);

  companion object {
    fun parseResultCode(resultCode: Int) =
      when (resultCode) {
        CANCELLED.resultCode -> CANCELLED
        SUCCESS.resultCode -> SUCCESS
        else -> throw IllegalArgumentException("Unknown result code: $resultCode")
      }
  }
}
