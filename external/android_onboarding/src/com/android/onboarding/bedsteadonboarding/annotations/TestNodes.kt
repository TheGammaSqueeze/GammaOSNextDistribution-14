package com.android.onboarding.bedsteadonboarding.annotations

/**
 * Mark that the test will allow the given array of nodes to be executed. Nodes not listed here will
 * not be executed and when attempted will mark the end of execution of the process under test.
 *
 * @property nodes the list of the nodes which are allowed to execute.
 */
annotation class TestNodes(val nodes: Array<TestSingleNode>)
