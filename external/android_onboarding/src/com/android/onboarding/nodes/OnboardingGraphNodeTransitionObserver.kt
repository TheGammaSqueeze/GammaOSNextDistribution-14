package com.android.onboarding.nodes

/**
 * An [OnboardingGraphLog.Observer] which logs a single entry each time we "transition" from one
 * node to another.
 *
 * This will only make sense when the flow is serial and there are no nodes executing in parallel.
 * Once parallel nodes is common this should be removed.
 *
 * This is only for use exploring how much of the Onboarding flow is covered by nodes. It should not
 * be depended upon for any other purpose.
 */
class OnboardingGraphNodeTransitionObserver(private val observer: OnboardingGraphLog.Observer) :
  OnboardingGraphLog.Observer {

  private var currentNodeId: Long? = null

  override fun onEvent(event: OnboardingGraphLog.OnboardingEvent) {
    if (event.nodeId != currentNodeId) {
      currentNodeId = event.nodeId

      observer.onEvent(event)
    }
  }
}
