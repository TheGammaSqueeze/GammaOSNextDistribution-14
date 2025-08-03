package com.android.onboarding.nodes.testing

import com.android.onboarding.nodes.OnboardingGraphLog

class TestOnboardingGraphObserver : OnboardingGraphLog.Observer {

  val events = mutableListOf<OnboardingGraphLog.OnboardingEvent>()

  override fun onEvent(event: OnboardingGraphLog.OnboardingEvent) {
    events.add(event)
  }
}
