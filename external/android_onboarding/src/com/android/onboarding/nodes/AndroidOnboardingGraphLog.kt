package com.android.onboarding.nodes

import com.android.onboarding.flags.DefaultOnboardingFlagsProvider

/** A Singleton [OnboardingGraphLog] which automatically logs all events to logcat. */
object AndroidOnboardingGraphLog : OnboardingGraphLog {

  const val LOG_TAG = "ZZZOnboardingGraph"
  private val onboardingFlags = DefaultOnboardingFlagsProvider()
  private val logcatObserver = LogcatObserver(LOG_TAG) { it.serializeToString() }
  private val defaultOnboardingGraphLog =
    DefaultOnboardingGraphLog().also {
      it.addObserver(logcatObserver)

      if (onboardingFlags.shouldVisualiseNodeTransitionsInLogcat) {
        it.addObserver(
          OnboardingGraphNodeTransitionObserver(
            LogcatObserver(logTag = "OnboardingGraph") { node ->
              "${node.nodeName}(${node.nodeId})"
            }
          )
        )
      }
    }

  override fun addObserver(observer: OnboardingGraphLog.Observer) {
    defaultOnboardingGraphLog.addObserver(observer)
  }

  override fun removeObserver(observer: OnboardingGraphLog.Observer) {
    defaultOnboardingGraphLog.removeObserver(observer)
  }

  override fun log(event: OnboardingGraphLog.OnboardingEvent) {
    defaultOnboardingGraphLog.log(event)
  }
}
