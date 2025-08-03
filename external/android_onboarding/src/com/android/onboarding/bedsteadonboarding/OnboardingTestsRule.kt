package com.android.onboarding.bedsteadonboarding

import android.app.ActivityOptions
import android.content.ContentValues
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.android.onboarding.bedsteadonboarding.annotations.OnboardingNodeScreenshot
import com.android.onboarding.bedsteadonboarding.annotations.TestNodes
import com.android.onboarding.bedsteadonboarding.contractutils.ContractExecutionEligibilityChecker
import com.android.onboarding.bedsteadonboarding.contractutils.ContractUtils
import com.android.onboarding.bedsteadonboarding.data.NodeData
import com.android.onboarding.bedsteadonboarding.data.OnboardingNodeMetadata
import com.android.onboarding.bedsteadonboarding.logcat.LogcatReader
import com.android.onboarding.bedsteadonboarding.providers.ConfigProviderUtil
import com.android.onboarding.bedsteadonboarding.providers.ConfigProviderUtil.TEST_NODE_CLASS_COLUMN
import com.android.onboarding.contracts.ArgumentFreeOnboardingActivityApiContract
import com.android.onboarding.contracts.ArgumentFreeVoidOnboardingActivityApiContract
import com.android.onboarding.contracts.EXTRA_ONBOARDING_NODE_ID
import com.android.onboarding.contracts.OnboardingActivityApiContract
import com.android.onboarding.contracts.UNKNOWN_NODE_ID
import com.android.onboarding.contracts.VoidOnboardingActivityApiContract
import com.android.onboarding.contracts.annotations.OnboardingNode
import com.android.onboarding.contracts.nodeId
import com.android.onboarding.nodes.OnboardingGraph
import com.android.onboarding.nodes.OnboardingGraphLog
import java.lang.AssertionError
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KClass
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
/**
 * TestRule to run before and after each test run. It will store all the test node related configs
 * like which nodes are allowed to execute.
 */
class OnboardingTestsRule : TestRule {

  private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
  private val instrumentationContext = InstrumentationRegistry.getInstrumentation().context
  private val testStorage = TestStorage()

  private lateinit var logcatReader: LogcatReader

  // Initial/ Source node of the graph. In a test multiple nodes could be launched or even a single
  // node could be launched multiple times using [OnboardingTestRule#launch] call. Each such call
  // would start a new graph and hence update [startNodeIdOfGraph] value.
  private var startNodeIdOfGraph: Long = UNKNOWN_NODE_ID
  private var hasOnboardingNodeScreenshotAnnotation: Boolean = false
  private var hasTakenScreenshot: Boolean = false
  private var currentTestName: String = ""
  private var nameOfNodeToTakeScreenshot: String = ""
  private var componentOfNodeToTakeScreenshot: String = ""

  override fun apply(base: Statement, description: Description): Statement {
    return if (description.isTest) {
      applyTest(base, description)
    } else {
      error(
        "OnboardingTestsRule can only be used as a `@Rule`. Unexpected description type: $description"
      )
    }
  }

  private fun applyTest(base: Statement, description: Description): Statement {
    return object : Statement() {
      override fun evaluate() {
        currentTestName = description.methodName
        // Extract test configuration from all the annotations applied to the test.
        val testNodesConfiguration = extractTestNodesConfiguration(description)

        try {
          // Start Reading OnboardingEvents Logs.
          logcatReader = LogcatReader(filterTag = ONBOARDING_EVENT_LOG_TAG).apply { start() }

          // Create an array of [ContentValues] representing the Test Configs.
          val contentValuesForTestConfigs =
            createContentValuesForAllowedNodes(testNodesConfiguration.allowedNodes)

          /* Now that we have an array of [allowedNodes] present in [contentValuesForTestConfigs],
            store them in all the apps in [appsStoringTestConfig] using their [TestContentProvider].
          */
          for (appPackageName in testNodesConfiguration.appsStoringTestConfig) {
            insertTestConfigsInApp(contentValuesForTestConfigs, appPackageName)
          }

          base.evaluate()
        } finally {
          logcatReader.stopReadingLogs()
          deleteAllTestConfigs(testNodesConfiguration.appsStoringTestConfig)

          if (hasOnboardingNodeScreenshotAnnotation && !hasTakenScreenshot) {
            throw AssertionError("OnboardingTestRule#takeScreenshot() not called in this test.")
          }
        }
      }
    }
  }

  /**
   * Launches an activity using [OnboardingActivityApiContract]. It will wait indefinitely until the
   * activity starts. Each call to this method will start a new graph.
   */
  fun <I, O> launchAndWaitForNodeToStart(
    activityContract: OnboardingActivityApiContract<I, O>,
    args: I,
  ) {
    ContractExecutionEligibilityChecker.terminateIfNodeIsTriggeredByTestAndIsNotAllowed(
      instrumentationContext,
      activityContract::class.java,
    )
    val nodeIntent = createNodeIntent(activityContract, args)
    startTrampolineActivity(activityContract, nodeIntent)
  }

  /**
   * Launches an activity using [ArgumentFreeOnboardingActivityApiContract]. It will wait
   * indefinitely until the activity starts. Each call to this method will start a new graph.
   */
  fun <O> launchAndWaitForNodeToStart(
    activityContract: ArgumentFreeOnboardingActivityApiContract<O>
  ) {
    ContractExecutionEligibilityChecker.terminateIfNodeIsTriggeredByTestAndIsNotAllowed(
      instrumentationContext,
      activityContract::class.java,
    )
    val nodeIntent = createNodeIntent(activityContract, Unit)
    startTrampolineActivity(activityContract, nodeIntent)
  }

  /**
   * Launches an activity which do not return result using [VoidOnboardingActivityApiContract] and
   * contract arguments. It will wait indefinitely until the activity starts. Each call to this
   * method will start a new graph.
   */
  fun <I> launchAndWaitForNodeToStart(
    activityContract: VoidOnboardingActivityApiContract<I>,
    args: I,
  ) {
    ContractExecutionEligibilityChecker.terminateIfNodeIsTriggeredByTestAndIsNotAllowed(
      instrumentationContext,
      activityContract::class.java,
    )
    val nodeIntent = createNodeIntent(activityContract, args)
    startTrampolineActivity(activityContract, nodeIntent)
  }

  /**
   * Launches an activity which do not return result using
   * [ArgumentFreeVoidOnboardingActivityApiContract]. It will wait indefinitely until the activity
   * starts. Each call to this method will start a new graph.
   */
  fun launchAndWaitForNodeToStart(activityContract: ArgumentFreeVoidOnboardingActivityApiContract) {
    ContractExecutionEligibilityChecker.terminateIfNodeIsTriggeredByTestAndIsNotAllowed(
      instrumentationContext,
      activityContract::class.java,
    )
    val nodeIntent = createNodeIntent(activityContract, Unit)
    startTrampolineActivity(activityContract, nodeIntent)
  }

  /** Captures the current UI on the device and saves it to the test output file. */
  fun takeScreenshot() {
    Log.i(TAG, "Taking screenshot of node $nameOfNodeToTakeScreenshot")
    hasTakenScreenshot = true

    val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
    val outputStream = testStorage.openOutputFile(/* pathname= */ getScreenshotName())
    // Convert the bitmap to PNG.
    screenshot.compress(Bitmap.CompressFormat.PNG, /* quality= */ 100, outputStream)

    // Adding node info to the metadata of the generated sponge.
    testStorage.addOutputProperties(
      mapOf(
        "onboarding_screenshot_node" to nameOfNodeToTakeScreenshot,
        "onboarding_screenshot_component" to componentOfNodeToTakeScreenshot,
      )
    )
  }

  /**
   * Given the [contractClass] of node, it blocks the test until the node has completed execution.
   * Currently it supports only activity nodes. Other node types will be handled later. There are 3
   * scenarios for node completion for activities.
   * 1. For nodes which return result, until that node has returned result i.e.,
   *    [ActivityNodeSetResult] event has been logged.
   * 2. Some other node has been started. This is a proxy for representing that the node of interest
   *    has finished execution.
   * 3. User-initiated exits like lifecycle events representing destroy, backpress, etc.
   */
  fun blockUntilNodeHasFinished(contractClass: KClass<*>) {
    // Find contract identifier of the node of interest given its contract class.
    val contractIdentifier = ContractUtils.getContractIdentifier(contractClass.java)
    while (true) {
      val graph = OnboardingGraph(getOnboardingEvents())
      if (hasNodeFinishedExecution(contractIdentifier, graph)) return
      // In the logcat, we have not received OnboardingEvent for given node, so wait for some
      // time before checking again.
      Thread.sleep(1000)
    }
  }

  /**
   * Given the [contractIdentifierOfNodeOfInterest] it queries the given [graph] to check if this
   * node has finished execution.
   */
  internal fun hasNodeFinishedExecution(
    contractIdentifierOfNodeOfInterest: String,
    graph: OnboardingGraph,
  ): Boolean {
    // Find the [OnboardingGraph.Node] entry for the source node of graph.
    val startNodes = graph.nodes.entries.filter { it.key == startNodeIdOfGraph }
    if (startNodes.isEmpty()) return false
    val startNode = startNodes.first()

    val nodeIdOfInterest =
      findEarliestStartedNodeWithGivenContract(
        startNode.key,
        graph,
        contractIdentifierOfNodeOfInterest,
      ) ?: return false
    val nodeToWaitFor = graph.nodes[nodeIdOfInterest]!!

    val events = nodeToWaitFor.events
    // Unblock, once the node completion condition has reached.
    return events.any {
      it is OnboardingGraphLog.OnboardingEvent.ActivityNodeFinished ||
        isAnotherNodeAttemptedToBeExecutedForResult(it, contractIdentifierOfNodeOfInterest)
    } || nodeToWaitFor.outgoingEdgesOfValidNodes.isNotEmpty()
  }

  /**
   * This will do a bfs traversal of the given [graph] whose initial node is [startNodeId]. It will
   * look for all nodes whose contractIdentifier = [contractIdentifierOfNodeOfInterest]. Out of
   * those it will return the one which was started first. If there is no node with
   * contractIdentifier as [contractIdentifierOfNodeOfInterest] it will return null.
   */
  internal fun findEarliestStartedNodeWithGivenContract(
    startNodeId: Long,
    graph: OnboardingGraph,
    contractIdentifierOfNodeOfInterest: String,
  ): Long? {
    val queue = ArrayDeque<Long>().apply { add(startNodeId) } // Add initial node
    val visitedNodes = mutableSetOf<Long>()
    var minStartTimeOfNodeOfInterest: Instant = Instant.MAX
    var probableNodeOfInterest: Long? = null

    while (queue.isNotEmpty()) {
      val currentNode = graph.nodes[queue.removeFirst()]!!
      visitedNodes.add(currentNode.id)
      if (
        ContractUtils.getContractIdentifierForNode(currentNode) ==
          contractIdentifierOfNodeOfInterest && currentNode.start < minStartTimeOfNodeOfInterest
      ) {
        probableNodeOfInterest = currentNode.id
        minStartTimeOfNodeOfInterest = currentNode.start
      }

      for (outgoingNode in currentNode.outgoingEdgesOfValidNodes) {
        if (!visitedNodes.contains(outgoingNode.node.id)) {
          queue.add(outgoingNode.node.id)
        }
      }
    }
    return probableNodeOfInterest
  }

  internal fun getStartNodeIdOfGraph() = startNodeIdOfGraph

  // Internal helper function to be used only in robolectric tests.
  internal fun setStartNodeIdOfGraph(nodeId: Long) {
    if (requireRobolectric()) {
      startNodeIdOfGraph = nodeId
    } else {
      throw IllegalAccessException("Not allowed to access internal methods")
    }
  }

  /**
   * Creates an [Intent] to launch the node for contract [activityContract] using [contractArgs].
   */
  internal fun <I> createNodeIntent(
    activityContract: OnboardingActivityApiContract<I, *>,
    contractArgs: I,
  ): Intent {
    // Use reflection to invoke the [OnboardingActivityApiContract#performCreateIntent()] of
    // [activityContract] to create [Intent]. We assume that the instrumented app is not proguarded.
    val intentCreationMethod =
      activityContract::class.java.methods.firstOrNull { it.name == INTENT_CREATION_METHOD_NAME }

    if (intentCreationMethod != null) {
      intentCreationMethod.isAccessible = true
      val nodeIntent =
        intentCreationMethod.invoke(activityContract, instrumentationContext, contractArgs)
          as? Intent ?: error("Unable to create valid Intent for contract $activityContract")
      // Since we start the node for [activityContract] using startActivity from
      // [TrampolineActivity], so the graph is broken, i.e. we can't know what is the nodeId of the
      // [activityContract]. So as a workaround we set the nodeId in the [Intent] here itself.
      startNodeIdOfGraph = nodeIntent.nodeId
      nodeIntent.putExtra(EXTRA_ONBOARDING_NODE_ID, startNodeIdOfGraph)
      return nodeIntent
    } else {
      throw IllegalStateException(
        "Couldn't find $INTENT_CREATION_METHOD_NAME method for contract $activityContract"
      )
    }
  }

  /**
   * Given the [contractIdentifierOfNodeOfInterest] of the activity node, find if it has started by
   * querying the onboarding events [graph] for [ActivityNodeResumed] event. If the
   * [ActivityNodeResumed] event is logged for the node then it means that the activity has started
   * and is visible.
   */
  internal fun hasActivityNodeStarted(
    contractIdentifierOfNodeOfInterest: String,
    graph: OnboardingGraph,
  ): Boolean {
    // Find the [OnboardingGraph.Node] entry for the source node of graph.
    val startNode = graph.nodes.entries.find { it.key == startNodeIdOfGraph } ?: return false
    return hasEventOccurred(
      OnboardingGraphLog.OnboardingEvent.ActivityNodeResumed::class,
      contractIdentifierOfNodeOfInterest,
      startNode.key,
      graph,
    )
  }

  /**
   * Returns if the event represented by its class [eventClassOfInterest] has occurred for the node
   * whose contractIdentifier = [contractIdentifierOfNodeOfInterest]. This will do a bfs traversal
   * of the given [graph] whose initial node is [startNodeId].
   */
  internal fun hasEventOccurred(
    eventClassOfInterest: KClass<*>,
    contractIdentifierOfNodeOfInterest: String,
    startNodeId: Long,
    graph: OnboardingGraph,
  ): Boolean {
    val queue = ArrayDeque<Long>().apply { add(startNodeId) } // Add initial node
    val visitedNodes = mutableSetOf<Long>()
    while (queue.isNotEmpty()) {
      val currentNode = graph.nodes[queue.removeFirst()]!!
      visitedNodes.add(currentNode.id)
      if (
        ContractUtils.getContractIdentifierForNode(currentNode) ==
          contractIdentifierOfNodeOfInterest &&
          currentNode.events.any { it::class == eventClassOfInterest }
      ) {
        return true
      }

      for (outgoingNode in currentNode.outgoingEdgesOfValidNodes) {
        if (!visitedNodes.contains(outgoingNode.node.id)) {
          queue.add(outgoingNode.node.id)
        }
      }
    }
    return false
  }

  /** Returns if the calling function is executed on robolectric. */
  private fun requireRobolectric() = "robolectric" == Build.FINGERPRINT

  /**
   * Stores the test configs using the ContentProvider of the app with given [appPackageName].
   *
   * @param contentValues An array of ContentValues representing the test configurations.
   * @param appPackageName package name of the app where test configs would be stored.
   */
  private fun insertTestConfigsInApp(contentValues: Array<ContentValues>, appPackageName: String) {
    /* Delete test configurations stored by app before inserting new ones. */
    deleteTestConfigsOfApp(appPackageName)
    val uri = ConfigProviderUtil.getTestConfigUri(ConfigProviderUtil.getAuthority(appPackageName))
    instrumentationContext.contentResolver.bulkInsert(uri, contentValues)
  }

  /** Returns the immutable set of OnboardingEvents read from logs */
  private fun getOnboardingEvents(): Set<OnboardingGraphLog.OnboardingEvent> {
    val onboardingEvents = mutableSetOf<OnboardingGraphLog.OnboardingEvent>()
    for (logLines in logcatReader.getFilteredLogs()) {
      // Is an onboarding graph line containing OnboardingEvent.
      val encoded = logLines.split("${ONBOARDING_EVENT_LOG_TAG}: ")[1]

      // Deserialize and store the OnboardingEvent in-memory.
      val event = OnboardingGraphLog.OnboardingEvent.deserialize(encoded)
      onboardingEvents.add(event)
    }
    return onboardingEvents.toSet()
  }

  /**
   * Returns [true] if a node other than the one with name [contractIdentifierOfNode], has been
   * started for result
   */
  private fun isAnotherNodeAttemptedToBeExecutedForResult(
    event: OnboardingGraphLog.OnboardingEvent,
    contractIdentifierOfNode: String,
  ) =
    (event is OnboardingGraphLog.OnboardingEvent.ActivityNodeStartExecuteSynchronously) &&
      (ContractUtils.getContractIdentifier(event.nodeComponent, event.nodeName) !=
        contractIdentifierOfNode)

  /**
   * Start the [TrampolineActivity] in the app owning [activityContract]. The [Intent] for
   * [TrampolineActivity] will also contain the [nodeIntent] to launch the actual intended node for
   * [activityContract] as an extra.
   */
  private fun startTrampolineActivity(
    activityContract: OnboardingActivityApiContract<*, *>,
    nodeIntent: Intent,
  ) {
    val trampolineActivityIntent =
      Intent(getTrampolineActivityIntentAction(activityContract)).apply {
        putExtra(EXTRA_NODE_START_INTENT_KEY, nodeIntent)
        flags = INTENT_FLAGS_START_IN_NEW_TASK
      }

    val activityOptions =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ActivityOptions.makeBasic().apply { isShareIdentityEnabled = true }
      } else {
        null
      }

    // Start activity and wait.
    instrumentationContext.startActivity(trampolineActivityIntent, activityOptions?.toBundle())
    waitForActivityLaunch(activityContract)
  }

  /** Get the [Intent] action to launch Trampoline Activity of the application owning [contract]. */
  private fun getTrampolineActivityIntentAction(
    contract: OnboardingActivityApiContract<*, *>
  ): String {
    val onboardingNode =
      extractOnboardingNodeAnnotation(contract)
        ?: error("Can't fetch OnboardingNode annotation for contract $contract")
    val onboardingNodeMetadata = getOnboardingNodeMetadata(onboardingNode)

    return "${onboardingNodeMetadata.packageName}${TRAMPOLINE_ACTIVITY_NAME}"
  }

  /**
   * Extract the [OnboardingNode] annotation applied to activity [contract]. There should be exactly
   * 1 such annotation and if there is none then null is returned.
   */
  private fun extractOnboardingNodeAnnotation(contract: OnboardingActivityApiContract<*, *>) =
    contract::class.annotations.filterIsInstance<OnboardingNode>().firstOrNull()

  /**
   * From the [TestNodes] annotation, extract all the nodes which are allowed to execute and also
   * the package name of the apps containing the nodes and store them in [TestNodesConfiguration].
   */
  private fun processTestNodesAnnotation(testNodes: TestNodes): TestNodesConfiguration {
    val allowedNodes = mutableListOf<NodeData>()
    val appsStoringTestConfig = mutableSetOf<String>(instrumentationContext.packageName)

    for (node in testNodes.nodes) {
      extractAllowedNodeAndItsAppPackage(node.contract).apply {
        allowedNodes.add(first)
        appsStoringTestConfig.add(second)
      }
    }
    return TestNodesConfiguration(appsStoringTestConfig.toSet(), allowedNodes.toList())
  }

  /**
   * From the [OnboardingNodeScreenshot] annotation, extract the allowed node to execute and also
   * the package name of the app containing this node and store them in [TestNodesConfiguration].
   */
  private fun processOnboardingNodeScreenshotAnnotation(
    onboardingNodeScreenshot: OnboardingNodeScreenshot
  ): TestNodesConfiguration {
    validateUiNode(onboardingNodeScreenshot.node.contract.annotations)
    hasOnboardingNodeScreenshotAnnotation = true

    var allowedNode: NodeData
    val appsStoringTestConfig = mutableSetOf(instrumentationContext.packageName)

    extractAllowedNodeAndItsAppPackage(onboardingNodeScreenshot.node.contract).apply {
      allowedNode = first
      appsStoringTestConfig.add(second)
    }

    return TestNodesConfiguration(appsStoringTestConfig.toSet(), listOf(allowedNode))
  }

  private fun validateUiNode(annotations: List<Annotation>) {
    for (annotation in annotations) {
      if (annotation is OnboardingNode) {
        when (annotation.uiType) {
          OnboardingNode.UiType.EDUCATION,
          OnboardingNode.UiType.INPUT,
          OnboardingNode.UiType.LOADING,
          OnboardingNode.UiType.OTHER -> {
            nameOfNodeToTakeScreenshot = annotation.name
            componentOfNodeToTakeScreenshot = annotation.component
          }
          OnboardingNode.UiType.HOST,
          OnboardingNode.UiType.NONE,
          OnboardingNode.UiType.INVISIBLE ->
            throw IllegalArgumentException(
              "Not allowed to take screenshot for non-UI onboarding nodes."
            )
        }
      }
    }
  }

  /**
   * Given a node's contract class, returns its [NodeData] representation and the package name of
   * the app to which it belongs.
   */
  private fun extractAllowedNodeAndItsAppPackage(nodeContract: KClass<*>): Pair<NodeData, String> {
    for (annotation in nodeContract.annotations) {
      if (annotation is OnboardingNode) {
        val onboardingNodeMetadata = getOnboardingNodeMetadata(annotation)
        return Pair(
          NodeData(
            allowedContractIdentifier = ContractUtils.getContractIdentifier(nodeContract.java)
          ),
          onboardingNodeMetadata.packageName,
        )
      }
    }
    error("$nodeContract class does not have OnboardingNode annotation")
  }

  private fun getOnboardingNodeMetadata(onboardingNode: OnboardingNode): OnboardingNodeMetadata {
    val nodeMetadata = onboardingNode.component.split("/")
    return when (nodeMetadata.size) {
      1 -> OnboardingNodeMetadata(nodeMetadata[0], nodeMetadata[0])
      2 -> OnboardingNodeMetadata(nodeMetadata[0], nodeMetadata[1])
      else -> error("OnboardingNode component ${onboardingNode.component} is invalid")
    }
  }

  // Creates a list of ContentValues representing the list of allowedNodes.
  private fun createContentValuesForAllowedNodes(
    allowedNodes: List<NodeData>
  ): Array<ContentValues> =
    allowedNodes
      .map { node ->
        ContentValues().apply { put(TEST_NODE_CLASS_COLUMN, node.allowedContractIdentifier) }
      }
      .toTypedArray()

  // Deletes the test configs stored all the apps listed in [appsStoringTestConfig] .
  private fun deleteAllTestConfigs(appsStoringTestConfig: Set<String>) {
    for (appPackageName in appsStoringTestConfig) {
      deleteTestConfigsOfApp(appPackageName)
    }
  }

  // Deletes the test configs stored by a given app.
  private fun deleteTestConfigsOfApp(packageName: String) {
    val uri = ConfigProviderUtil.getTestConfigUri(ConfigProviderUtil.getAuthority(packageName))
    instrumentationContext.contentResolver.delete(uri, /* where= */ null, /* selectionArgs= */ null)
  }

  /**
   * Extracts the [TestNodesConfiguration] from all annotations applied to [description].
   *
   * If no annotation is provided, then null will be returned.
   */
  private fun extractTestNodesConfiguration(description: Description): TestNodesConfiguration {
    val testNodesAnnotationConfiguration =
      extractTestNodesAnnotation(description)?.let { processTestNodesAnnotation(it) }
        ?: TestNodesConfiguration()
    val onboardingNodeScreenshotAnnotationTestConfiguration =
      extractOnboardingNodeScreenshotAnnotation(description)?.let {
        processOnboardingNodeScreenshotAnnotation(it)
      } ?: TestNodesConfiguration()

    return TestNodesConfiguration(
      appsStoringTestConfig =
        testNodesAnnotationConfiguration.appsStoringTestConfig +
          onboardingNodeScreenshotAnnotationTestConfiguration.appsStoringTestConfig,
      allowedNodes =
        testNodesAnnotationConfiguration.allowedNodes +
          onboardingNodeScreenshotAnnotationTestConfiguration.allowedNodes,
    )
  }

  /**
   * Extracts the [TestNodes] annotation applied to [description].
   *
   * There can only be a maximum of 1 such annotation, and if 0 is provided then null will be
   * returned.
   */
  private fun extractTestNodesAnnotation(description: Description) =
    description.annotations?.filterIsInstance<TestNodes>()?.firstOrNull()

  /**
   * Extracts the [OnboardingNodeScreenshot] annotation applied to [description].
   *
   * There can only be a maximum of 1 such annotation, and if 0 is provided then null will be
   * returned.
   */
  private fun extractOnboardingNodeScreenshotAnnotation(description: Description) =
    description.annotations?.filterIsInstance<OnboardingNodeScreenshot>()?.firstOrNull()

  /**
   * Returns the screenshot path derived from the component name [componentOfNodeToTakeScreenshot],
   * node name [nameOfNodeToTakeScreenshot] and test method name [currentTestName].
   */
  private fun getScreenshotName() =
    "$componentOfNodeToTakeScreenshot/$nameOfNodeToTakeScreenshot/${currentTestName}.png"

  private fun waitForActivityLaunch(activityContract: OnboardingActivityApiContract<*, *>) {
    if (!requireRobolectric()) {
      val contractIdentifier = ContractUtils.getContractIdentifier(activityContract::class.java)
      val contractPackageName = extractAllowedNodeAndItsAppPackage(activityContract::class).second
      while (true) {
        val graph = OnboardingGraph(getOnboardingEvents())
        if (hasActivityNodeStarted(contractIdentifier, graph)) {
          // Pause the test for up to 60 seconds, waiting for a top-level UI element (depth 0) from
          // the package specified by [contractPackageName] to appear on the screen.
          device.wait(
            Until.hasObject(By.pkg(contractPackageName).depth(0)),
            Duration.ofSeconds(60).toMillis(),
          )
          Thread.sleep(1000) // Wait an additional second in case UI automator is flaky.
          return
        }
        Thread.sleep(100) // Check every 100 milliseconds.
      }
    }
  }

  /**
   * Stores the test configuration obtained by processing test level annotations. This data will be
   * later used to store the list of [allowedNodes] in all the apps given by [appsStoringTestConfig]
   * using their [TestContentProvider].
   */
  private data class TestNodesConfiguration(
    /**
     * Stores the package name of apps where the test configs will be stored. This includes the app
     * package name of allowed nodes and the instrumented app.
     */
    val appsStoringTestConfig: Set<String> = setOf(),

    /** Stores the list of nodes which are allowed to execute in tests. */
    val allowedNodes: List<NodeData> = listOf(),
  )

  companion object {
    const val TAG = "OnboardingTestsRule"
    const val ONBOARDING_EVENT_LOG_TAG = "ZZOnboardingGraph"
    const val EXTRA_NODE_START_INTENT_KEY =
      "com.android.onboarding.bedsteadonboarding.activities.extra.NODE_START_INTENT"

    private const val INTENT_CREATION_METHOD_NAME = "performCreateIntent"
    private const val INTENT_FLAGS_START_IN_NEW_TASK =
      FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK

    // LINT.IfChange(activity_action_name)
    private const val TRAMPOLINE_ACTIVITY_NAME = ".bedstead.onboarding.trampolineactivity"
    // LINT.ThenChange(src/com/android/onboarding/bedsteadonboarding/AndroidManifest.xml:activity_action_name)
  }
}
