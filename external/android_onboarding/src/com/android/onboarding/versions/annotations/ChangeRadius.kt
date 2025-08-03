package com.android.onboarding.versions.annotations

/** The radius of effect of a given change. */
enum class ChangeRadius {
  /**
   * The change only applies to a single component at a time.
   *
   * For example, a change to functionality which shows a loading screen in the current process
   * might be SINGLE_COMPONENT - as long as it doesn't make calls to other components.
   */
  SINGLE_COMPONENT,

  /**
   * The change involves multiple components.
   *
   * For example, any change to an API which one component exposes to another (via activity starts,
   * broadcasts, services, etc.) is intrinsically MULTI_COMPONENT.
   */
  MULTI_COMPONENT
}
