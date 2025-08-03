package com.android.onboarding.bedsteadonboarding.contractutils

import com.android.onboarding.contracts.annotations.OnboardingNode
import com.android.onboarding.nodes.OnboardingGraph

/** Contains utility methods for node contracts. */
object ContractUtils {

  private const val contractIdentifierSeparator = "."

  /** Returns a string uniquely identifying a node's contract given its [contractClass]. */
  fun getContractIdentifier(contractClass: Class<*>) =
    "${OnboardingNode.extractComponentNameFromClass(contractClass)}${contractIdentifierSeparator}${OnboardingNode.extractNodeNameFromClass(contractClass)}"

  /** Returns a string uniquely identifying the contract for the given [node]. */
  fun getContractIdentifierForNode(node: OnboardingGraph.Node) =
    "${node.component}$contractIdentifierSeparator${node.name}"

  /**
   * Returns a string uniquely identifying a node's contract given its [nodeComponent] and
   * [nodeName].
   */
  fun getContractIdentifier(nodeComponent: String, nodeName: String) =
    "${nodeComponent}$contractIdentifierSeparator${nodeName}"
}
