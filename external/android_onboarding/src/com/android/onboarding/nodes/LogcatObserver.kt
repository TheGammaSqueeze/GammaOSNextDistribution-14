package com.android.onboarding.nodes

import android.util.Log

/** An [OnboardingGraphLog.Observer] which logs human readable events to logcat. */
class LogcatObserver(
  private val logTag: String = LOG_TAG,
  private val toString: (e: OnboardingGraphLog.OnboardingEvent) -> String =
    OnboardingGraphLog.OnboardingEvent::toString
) : OnboardingGraphLog.Observer {

  override fun onEvent(event: OnboardingGraphLog.OnboardingEvent) {
    Log.i(logTag, toString(event))
  }

  companion object {
    const val LOG_TAG = "RawLogcatGraph"
  }
}
