package com.android.onboarding.contracts

import android.app.Activity
import android.content.Intent
import com.android.onboarding.nodes.AndroidOnboardingGraphLog
import com.android.onboarding.nodes.OnboardingGraphLog
import java.util.UUID
import javax.inject.Qualifier

typealias NodeId = Long

/**
 * A DI qualifier to inject node ID where applicable. Consuming module should scope this to a given
 * activity's lifecycle.
 */
@Retention(AnnotationRetention.RUNTIME) @Qualifier annotation class OnboardingNodeId

/**
 * Marks entities that require awareness of onboarding node currently being executed
 *
 * @property nodeId the ID of the currently executing node
 */
interface NodeAware {
  @OnboardingNodeId val nodeId: NodeId
}

/** Extra key used when storing a node id */
const val EXTRA_ONBOARDING_NODE_ID = "com.android.onboarding.ONBOARDING_NODE_ID"

const val UNKNOWN_NODE_ID: NodeId = -1L

val Intent.nodeId: NodeId
  get() =
    getLongExtra(EXTRA_ONBOARDING_NODE_ID, UNKNOWN_NODE_ID).let {
      if (it == UNKNOWN_NODE_ID) {
        val id = UUID.randomUUID().leastSignificantBits
        this.putExtra(EXTRA_ONBOARDING_NODE_ID, id)
        id
      } else {
        it
      }
    }

val Activity.nodeId: NodeId
  get() {
    intent = intent ?: Intent()
    return intent.nodeId
  }

/** Mark the executing node as failed with [reason]. */
fun Activity.failNode(reason: String? = null) {
  AndroidOnboardingGraphLog.log(OnboardingGraphLog.OnboardingEvent.ActivityNodeFail(nodeId, reason))
}
