package com.android.onboarding.nodes

class DefaultOnboardingGraphLog : OnboardingGraphLog {

  private val observers = mutableSetOf<OnboardingGraphLog.Observer>()

  override fun addObserver(observer: OnboardingGraphLog.Observer) {
    observers.add(observer)
  }

  override fun removeObserver(observer: OnboardingGraphLog.Observer) {
    observers.remove(observer)
  }

  override fun log(event: OnboardingGraphLog.OnboardingEvent) {
    observers.forEach { it.onEvent(event) }
  }
}
