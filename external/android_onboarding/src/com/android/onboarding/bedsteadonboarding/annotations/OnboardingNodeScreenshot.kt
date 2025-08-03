package com.android.onboarding.bedsteadonboarding.annotations

/**
 * Mark that a test is responsible for taking the representative screenshot of the given node.
 *
 * @property node the allowed node to execute.
 *
 * This implies that the specified node is allowed to be launched (as if it was included in
 * [TestNodes]).
 */
annotation class OnboardingNodeScreenshot(val node: TestSingleNode)
