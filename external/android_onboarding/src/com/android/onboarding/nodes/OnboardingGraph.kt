package com.android.onboarding.nodes

import com.android.onboarding.nodes.OnboardingGraphLog.OnboardingEvent.ActivityNodeArgumentExtracted
import com.android.onboarding.nodes.OnboardingGraphLog.OnboardingEvent.ActivityNodeExecutedDirectly
import com.android.onboarding.nodes.OnboardingGraphLog.OnboardingEvent.ActivityNodeExecutedForResult
import com.android.onboarding.nodes.OnboardingGraphLog.OnboardingEvent.ActivityNodeExecutedSynchronously
import com.android.onboarding.nodes.OnboardingGraphLog.OnboardingEvent.ActivityNodeExtractArgument
import com.android.onboarding.nodes.OnboardingGraphLog.OnboardingEvent.ActivityNodeFail
import com.android.onboarding.nodes.OnboardingGraphLog.OnboardingEvent.ActivityNodeFailedValidation
import com.android.onboarding.nodes.OnboardingGraphLog.OnboardingEvent.ActivityNodeResultReceived
import com.android.onboarding.nodes.OnboardingGraphLog.OnboardingEvent.ActivityNodeSetResult
import com.android.onboarding.nodes.OnboardingGraphLog.OnboardingEvent.ActivityNodeStartExecuteSynchronously
import com.android.onboarding.nodes.OnboardingGraphLog.OnboardingEvent.ActivityNodeValidating
import java.lang.IllegalStateException
import java.time.Instant
import java.util.Objects

class OnboardingGraph(events: Set<OnboardingGraphLog.OnboardingEvent>) {
  val nodes: Map<Long, Node> by lazy {
    val nodeMap = mutableMapOf<Long, Node>()

    for (e in events) {
      when (e) {
        is ActivityNodeExecutedDirectly -> {
          nodeMap.updateNode(
            e,
            e.nodeId,
            e.timestamp,
            name = e.nodeName,
            component = e.nodeComponent,
            argument = e.argument,
            incomingEdge = InternalEdge.ClosedIncomingEdge(e.sourceNodeId, e.timestamp),
            type = NodeType.ACTIVITY,
          )
          nodeMap.updateNode(
            e,
            e.sourceNodeId,
            e.timestamp,
            outgoingEdge = InternalEdge.OutgoingEdge(e.nodeId, e.timestamp),
          )
        }
        is ActivityNodeExecutedForResult -> {
          nodeMap.updateNode(
            e,
            e.nodeId,
            e.timestamp,
            name = e.nodeName,
            component = e.nodeComponent,
            argument = e.argument,
            incomingEdge = InternalEdge.OpenIncomingEdge(e.sourceNodeId, e.timestamp),
            type = NodeType.ACTIVITY,
          )
          nodeMap.updateNode(
            e,
            e.sourceNodeId,
            e.timestamp,
            outgoingEdge = InternalEdge.OutgoingEdge(e.nodeId, e.timestamp),
          )
        }
        is ActivityNodeValidating -> {
          // We don't log the incoming intent beyond the normal event log as it's an implementation
          // detail
          nodeMap.updateNode(
            e,
            e.nodeId,
            e.timestamp,
            name = e.nodeName,
            component = e.nodeComponent,
            type = NodeType.ACTIVITY,
          )
        }
        is ActivityNodeExtractArgument -> {
          // We don't log the incoming intent beyond the normal event log as it's an implementation
          // detail
          nodeMap.updateNode(
            e,
            e.nodeId,
            e.timestamp,
            name = e.nodeName,
            component = e.nodeComponent,
            type = NodeType.ACTIVITY,
          )
        }
        is ActivityNodeFailedValidation -> {
          // We don't log the incoming intent beyond the normal event log as it's an implementation
          // detail
          nodeMap.updateNode(
            e,
            e.nodeId,
            e.timestamp,
            name = e.nodeName,
            component = e.nodeComponent,
            failureReason = e.exception,
            type = NodeType.ACTIVITY,
          )
        }
        is ActivityNodeArgumentExtracted -> {
          nodeMap.updateNode(
            e,
            e.nodeId,
            e.timestamp,
            name = e.nodeName,
            component = e.nodeComponent,
            argument = e.argument,
            type = NodeType.ACTIVITY,
          )
        }
        is ActivityNodeSetResult -> {
          nodeMap.updateNode(
            e,
            e.nodeId,
            e.timestamp,
            name = e.nodeName,
            component = e.nodeComponent,
            result = e.result,
            type = NodeType.ACTIVITY,
          )
        }
        is ActivityNodeResultReceived -> {
          // Will be dealt with in the second loop
        }
        is ActivityNodeFail -> {
          // Will be dealt with in the second loop
        }
        is ActivityNodeStartExecuteSynchronously -> {
          nodeMap.updateNode(
            e,
            e.nodeId,
            e.timestamp,
            name = e.nodeName,
            component = e.nodeComponent,
            argument = e.argument,
            incomingEdge = InternalEdge.OpenIncomingEdge(e.sourceNodeId, e.timestamp),
            type = NodeType.SYNCHRONOUS,
          )
          nodeMap.updateNode(
            e,
            e.sourceNodeId,
            e.timestamp,
            outgoingEdge = InternalEdge.OutgoingEdge(e.nodeId, e.timestamp),
          )
        }
        is ActivityNodeExecutedSynchronously -> {
          nodeMap.updateNode(
            e,
            e.nodeId,
            e.timestamp,
            name = e.nodeName,
            component = e.nodeComponent,
            result = e.result,
            type = NodeType.SYNCHRONOUS,
          )
        }
        is OnboardingGraphLog.OnboardingEvent.ActivityNodeFinished -> {
          nodeMap.updateNode(
            e,
            e.nodeId,
            e.timestamp,
            name = e.nodeName,
            component = e.nodeComponent,
            type = NodeType.ACTIVITY,
          )
        }
        is OnboardingGraphLog.OnboardingEvent.ActivityNodeResumedAfterLaunch -> {
          nodeMap.updateNode(
            e,
            e.nodeId,
            e.timestamp,
            name = e.nodeName,
            component = e.nodeComponent,
            incomingEdge = InternalEdge.ClosedIncomingEdge(e.sourceNodeId, e.timestamp),
            type = NodeType.ACTIVITY,
          )
        }
        is OnboardingGraphLog.OnboardingEvent.ActivityNodeResumed -> {
          nodeMap.updateNode(
            e,
            e.nodeId,
            e.timestamp,
            name = e.nodeName,
            component = e.nodeComponent,
            type = NodeType.ACTIVITY,
          )
        }
      }
    }

    // We run through a second time to update results now that all nodes exist
    for (e in events) {
      if (e is ActivityNodeResultReceived) {
        nodeMap.updateNode(
          e,
          e.nodeId,
          e.timestamp,
          name = e.nodeName,
          component = e.nodeComponent,
          result = e.result,
        )
        val node = nodeMap[e.nodeId]!!
        node._incomingEdge?.let { nodeMap.updateNode(id = it.id, timestamp = e.timestamp) }
      } else if (e is ActivityNodeFail) {
        nodeMap.updateNode(
          e,
          e.nodeId,
          e.timestamp,
          failureReason = IllegalStateException(e.reason),
        )
        val node = nodeMap[e.nodeId]!!
        with(node._incomingEdge) {
          if (this == null) {
            println("node._incomingEdge is null. Event: $e, Node: $node")
          } else {
            nodeMap.updateNode(id = this.id, timestamp = e.timestamp)
          }
        }
      }
    }

    val nodesToRemove = mutableListOf<Long>()

    // Then we need to recursively expand the time of all callers which are waiting for a result -
    // as they haven't finished executing
    for (node in nodeMap.values) {
      var recursiveNode: Node? = node
      while (recursiveNode != null) {
        recursiveNode =
          recursiveNode._incomingEdge?.let { incomingEdge ->
            if (incomingEdge is InternalEdge.OpenIncomingEdge) {
              // If the edge is "Open" then it's waiting for a reply to so the incoming node must
              // be at least as long as this node
              val incomingNode = nodeMap[incomingEdge.id]!!
              node.start
                .takeIf { it.isBefore(incomingNode.start) }
                ?.also { incomingNode._start = it }
              node.end.takeIf { it.isAfter(incomingNode.end) }?.also { incomingNode._end = it }

              incomingNode
            } else null
          }
      }

      if (node.isSynchronous) {
        if (node._events.size == 1) {
          // It only started and did nothing else
          nodesToRemove.add(node.id)
        }
      }
    }

    nodesToRemove.forEach { nodeMap.remove(it) }
    nodeMap.toMap()
  }

  private fun MutableMap<Long, Node>.updateNode(
    event: OnboardingGraphLog.OnboardingEvent? = null,
    id: Long,
    timestamp: Instant,
    name: String? = null,
    component: String? = null,
    argument: Any? = null,
    result: Any? = null,
    outgoingEdge: InternalEdge? = null,
    incomingEdge: InternalEdge? = null,
    failureReason: Throwable? = null,
    type: NodeType? = null,
  ) {
    this.getOrPut(id) { Node(this, id) }
      .also { node ->
        name?.also { node._name = it }
        component?.also { node._component = it }

        argument?.also { node._argument = it }
        result?.also { node._result = it }

        timestamp
          .takeIf { node._start == null || it.isBefore(node._start) }
          ?.also { node._start = it }
        timestamp.takeIf { node._end == null || it.isAfter(node._end) }?.also { node._end = it }

        failureReason?.also { node._failureReasons.add(it) }

        outgoingEdge?.also { node._outgoingEdges.add(it) }
        incomingEdge?.also { node._incomingEdge = it }

        event?.also { node._events.add(it) }

        type?.also { node._type = it }
      }
  }

  data class Node(
    private var nodeMap: Map<Long, Node>,
    internal var _id: Long,
    internal var _name: String? = null,
    internal var _argument: Any? = null,
    internal var _result: Any? = null,
    internal var _start: Instant? = null,
    internal var _end: Instant? = null,
    internal var _outgoingEdges: MutableSet<InternalEdge> = mutableSetOf(),
    internal var _incomingEdge: InternalEdge? = null,
    internal var _failureReasons: MutableSet<Throwable> = mutableSetOf(),
    internal var _type: NodeType = NodeType.UNKNOWN,
    internal var _component: String? = null,

    // Note that one event can be associated with multiple nodes
    internal var _events: MutableSet<OnboardingGraphLog.OnboardingEvent> = mutableSetOf(),
  ) {
    val id: Long
      get() = _id

    val name: String
      get() = _name ?: "Unnamed node $id"
    val argument: Any?
      get() = _argument

    val result: Any?
      get() = _result

    val start: Instant
      get() = _start ?: throw IllegalStateException("No start time for node $id")

    val end: Instant
      get() = _end ?: throw IllegalStateException("No end time for node $id")

    val outgoingEdges: Set<Edge>
      get() =
        _outgoingEdges
          .map {
            if (nodeMap.contains(it.id)) {
              Edge(nodeMap[it.id]!!, it.timestamp)
            } else {
              throw IllegalStateException("$id relies on non-existing outgoing node $it")
            }
          }
          .toSet()

    // It filters the invalid outgoing edges i.e. edges to non existent node and returns only the
    // valid ones.
    val outgoingEdgesOfValidNodes: Set<Edge>
      get() =
        _outgoingEdges
          .filter { nodeMap.containsKey(it.id) }
          .map { Edge(nodeMap[it.id]!!, it.timestamp) }
          .toSet()

    val incomingEdge: Edge?
      get() =
        _incomingEdge?.let {
          if (nodeMap.containsKey(it.id)) {
            Edge(nodeMap[it.id]!!, it.timestamp)
          } else {
            throw IllegalStateException("$id relies on non-existing incoming node $it")
          }
        }

    val failureReasons: Set<Throwable>
      get() = _failureReasons.toSet()

    val isFailed: Boolean
      get() = failureReasons.isNotEmpty()

    val type: NodeType
      get() = _type

    val component: String?
      get() = _component

    val isSynchronous: Boolean
      get() = type == NodeType.SYNCHRONOUS

    val events
      get() = _events.toSet()

    val isComplete: Boolean
      get() = _events.size >= 2

    override fun hashCode(): Int = Objects.hash(_id, _name)

    override fun toString(): String =
      "{Node $_id name=$_name, argument=$_argument," +
        " result=$_result, start=$_start, end=$_end," +
        " outgoingEdges=$_outgoingEdges," +
        " incomingEdge=$_incomingEdge}"
  }

  sealed class InternalEdge(val id: Long, val timestamp: Instant) {
    class OutgoingEdge(id: Long, timestamp: Instant) : InternalEdge(id, timestamp)

    class OpenIncomingEdge(id: Long, timestamp: Instant) : InternalEdge(id, timestamp)

    class ClosedIncomingEdge(id: Long, timestamp: Instant) : InternalEdge(id, timestamp)
  }

  data class Edge(val node: Node, val timestamp: Instant)

  enum class NodeType {
    UNKNOWN,
    ACTIVITY,
    SYNCHRONOUS
  }
}
