package com.android.onboarding.contracts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContract
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.android.onboarding.bedsteadonboarding.contractutils.ContractExecutionEligibilityChecker
import com.android.onboarding.contracts.ContractResult.Failure
import com.android.onboarding.contracts.annotations.InternalOnboardingApi
import com.android.onboarding.nodes.AndroidOnboardingGraphLog
import com.android.onboarding.nodes.OnboardingGraphLog
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
import com.google.errorprone.annotations.CanIgnoreReturnValue
import java.util.UUID

/**
 * A Contract used for launching an activity as part of the Onboarding flow.
 *
 * <p>It is required that all Activity starts in the Android Onboarding flow go via contracts. This
 * is to allow for better central tracking of onboarding sessions.
 */
abstract class OnboardingActivityApiContract<I, O> : ActivityResultContract<I, O>() {

  /**
   * Create a new ID to be used for the node started by this contract.
   *
   * <p>This is only used when starting a contract - it is not used when extracting arguments during
   * the execution of a contract. In that case, the ID is extracted from the activity intent.
   */
  private fun newOutgoingId(): Long {
    return UUID.randomUUID().leastSignificantBits
  }

  /**
   * Creates an {@link Intent} for this contract containing the given argument.
   *
   * <p>This should be symmetric with [performExtractArgument].
   */
  protected abstract fun performCreateIntent(context: Context, arg: I): Intent

  /**
   * Extracts the argument from the given [Intent].
   *
   * <p>This should be symmetric with [performCreateIntent].
   */
  protected abstract fun performExtractArgument(intent: Intent): I

  /**
   * Sets the given result.
   *
   * This should be symmetric with [performParseResult].
   */
  protected abstract fun performSetResult(result: O): ContractResult

  /**
   * Extracts the result from the given [ContractResult].
   *
   * This should be symmetric with [performSetResult].
   */
  protected abstract fun performParseResult(result: ContractResult): O

  /**
   * Fetches the result without starting the activity.
   *
   * <p>This can be optionally implemented, and should return null if a result cannot be fetched and
   * the activity should be started.
   */
  protected open fun performGetSynchronousResult(context: Context, args: I): SynchronousResult<O>? =
    null

  private var forResultOutGoingId: Long = UNKNOWN_NODE_ID

  /** Extracts an argument passed into the current activity using the contract. */
  fun extractArgument(intent: Intent): I {
    // Injection point when we are receiving control in an activity
    AndroidOnboardingGraphLog.log(
      ActivityNodeExtractArgument(intent.nodeId, this.javaClass, intentToIntentData(intent))
    )

    val argument = performExtractArgument(intent)

    AndroidOnboardingGraphLog.log(
      ActivityNodeArgumentExtracted(intent.nodeId, this.javaClass, argument)
    )

    return argument
  }

  private fun intentToIntentData(intent: Intent): OnboardingGraphLog.OnboardingEvent.IntentData {
    val extras =
      buildMap<String, Any?> {
        intent.extras?.let { for (key in it.keySet()) put(key, it.get(key)) }
      }

    return OnboardingGraphLog.OnboardingEvent.IntentData(intent.action, extras)
  }

  /** Sets a result for this contract. */
  fun setResult(activity: Activity, result: O) {
    // Injection point when we are returning a result from the current activity

    AndroidOnboardingGraphLog.log(ActivityNodeSetResult(activity.nodeId, this.javaClass, result))

    var contractResult = performSetResult(result)

    if (contractResult is Failure) {
      AndroidOnboardingGraphLog.log(ActivityNodeFail(activity.nodeId, contractResult.reason))
    }

    val intent = contractResult.intent ?: Intent()
    intent.putExtra(EXTRA_ONBOARDING_NODE_ID, activity.nodeId)

    activity.setResult(contractResult.resultCode, intent)
  }

  class OnboardingLifecycleObserver<I, O>(
    private var activity: Activity?,
    private val contract: OnboardingActivityApiContract<I, O>,
  ) : LifecycleEventObserver {

    private var isFinishLogged = false

    private fun maybeLogFinish() {
      if (!isFinishLogged && activity?.isFinishing == true) {
        isFinishLogged = true
        AndroidOnboardingGraphLog.log(
          OnboardingGraphLog.OnboardingEvent.ActivityNodeFinished(
            activity?.nodeId ?: UNKNOWN_NODE_ID,
            contract.javaClass,
          )
        )
      }
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
      when (event) {
        Lifecycle.Event.ON_STOP,
        Lifecycle.Event.ON_PAUSE -> maybeLogFinish()
        Lifecycle.Event.ON_DESTROY -> {
          maybeLogFinish()
          activity?.nodeId?.let { waitingForResumeActivity.remove(it) }
          // Clear the activity reference to avoid memory leak.
          activity = null
        }
        Lifecycle.Event.ON_CREATE,
        Lifecycle.Event.ON_START,
        Lifecycle.Event.ON_RESUME -> {
          val nodeId = activity?.nodeId ?: UNKNOWN_NODE_ID
          if (nodeId == UNKNOWN_NODE_ID) {
            Log.w(TAG, "${activity?.componentName} does not contain node id.")
          }
          if (event == Lifecycle.Event.ON_RESUME) {
            AndroidOnboardingGraphLog.log(
              OnboardingGraphLog.OnboardingEvent.ActivityNodeResumed(nodeId, contract.javaClass)
            )
          }
          val waitingForResume =
            nodeId != UNKNOWN_NODE_ID && waitingForResumeActivity.contains(nodeId)
          if (waitingForResume) {
            val sourceNodeId = waitingForResumeActivity[nodeId] ?: UNKNOWN_NODE_ID
            AndroidOnboardingGraphLog.log(
              OnboardingGraphLog.OnboardingEvent.ActivityNodeResumedAfterLaunch(
                sourceNodeId,
                nodeId,
                contract.javaClass,
              )
            )
            waitingForResumeActivity.remove(nodeId)
          }
        }
        Lifecycle.Event.ON_ANY -> {}
      }
    }
  }

  /**
   * Attaches to the specified Activity in onCreate. This validates the intent can be parsed into an
   * argument.
   *
   * @param activity The Activity to attach to.
   * @param intent The Intent to use (defaults to the Activity's intent).
   * @return An AttachedResult object with information about the attachment, including the
   *   validation result.
   */
  @CanIgnoreReturnValue
  fun attach(activity: ComponentActivity, intent: Intent = activity.intent): AttachedResult {
    return attach(activity, activity.lifecycle, intent)
  }

  /**
   * Attaches to the specified Activity in onCreate. This validates the intent can be parsed into an
   * argument.
   *
   * A lifecycle should be provided. If the activity does not support lifecycle owner, it should
   * implement a LifeCycleOwner or migrate to use androidx ComponentActivity.
   *
   * @param activity The Activity to attach to.
   * @param lifecycle The Lifecycle of the activity.
   * @param intent The Intent to use (defaults to the Activity's intent).
   * @return An AttachedResult object with information about the attachment, including the
   *   validation result.
   */
  @CanIgnoreReturnValue
  fun attach(
    activity: Activity,
    lifecycle: Lifecycle?,
    intent: Intent = activity.intent,
  ): AttachedResult {
    val validated = validateInternal(activity, intent)
    lifecycle?.addObserver(OnboardingLifecycleObserver(activity, this))
    return AttachedResult(validated)
  }

  /**
   * Validate that the intent can be parsed into an argument.
   *
   * <p>When parsing fails, the failure will be recorded so that it can be fixed.
   *
   * @param activity the current activity context
   * @param intent the [Intent] to validate (defaults to the activity's current intent)
   * @return `true` if the intent is valid, `false` otherwise
   * @deprecated use [attach].
   */
  @CanIgnoreReturnValue
  @Deprecated("Use attach instead", ReplaceWith("attach(activity, intent)"))
  fun validate(activity: ComponentActivity, intent: Intent = activity.intent): Boolean {
    return validate(activity, activity.lifecycle, intent)
  }

  /**
   * Validate that the intent can be parsed into an argument.
   *
   * <p>When parsing fails, the failure will be recorded so that it can be fixed.
   *
   * @param activity the current activity context
   * @param lifecycle the lifecycle of the activity, used for logging purposes
   * @param intent the [Intent] to validate (defaults to the activity's current intent)
   * @return `true` if the intent is valid, `false` otherwise
   */
  @CanIgnoreReturnValue
  @Deprecated("Use attach instead", ReplaceWith("attach(activity, lifecycle, intent)"))
  fun validate(
    activity: Activity,
    lifecycle: Lifecycle?,
    intent: Intent = activity.intent,
  ): Boolean {
    val validated = validateInternal(activity, intent)
    lifecycle?.addObserver(OnboardingLifecycleObserver(activity, this))
    return validated
  }

  private fun validateInternal(activity: Activity, intent: Intent): Boolean {
    AndroidOnboardingGraphLog.log(
      ActivityNodeValidating(activity.nodeId, this.javaClass, intentToIntentData(intent))
    )
    return runCatching { extractArgument(intent) }
      .onFailure {
        AndroidOnboardingGraphLog.log(
          ActivityNodeFailedValidation(
            nodeId = activity.nodeId,
            nodeClass = this.javaClass,
            exception = it,
            intent = intentToIntentData(intent),
          )
        )
      }
      .map { true }
      .getOrDefault(false)
  }

  private fun extractNodeId(context: Context) =
    if (context is Activity) {
      // This is true in all known cases - but we need to accept Context because that's the AndroidX
      // API surface
      context.nodeId
    } else {
      UNKNOWN_NODE_ID
    }

  /** Create an Intent with the intention of launching the contract without expecting a result. */
  internal fun createIntentDirectly(context: Context, input: I): Intent {
    // Injection point when we are passing control out of the current activity without expecting a
    // result
    val intent = performCreateIntent(context, input)
    val outgoingId = newOutgoingId()
    intent.putExtra(EXTRA_ONBOARDING_NODE_ID, outgoingId)
    // Track for resume event for activity node id.
    waitingForResumeActivity.put(extractNodeId(context), outgoingId)

    AndroidOnboardingGraphLog.log(
      ActivityNodeExecutedDirectly(extractNodeId(context), outgoingId, this.javaClass, input)
    )

    ContractExecutionEligibilityChecker.terminateIfNodeIsTriggeredByTestAndIsNotAllowed(
      context,
      this.javaClass,
    )

    return intent
  }

  final override fun createIntent(context: Context, input: I): Intent {
    // Injection point when we are passing control out of the current activity
    val intent = performCreateIntent(context, input)

    // We should assume forResultOutGoingId is already set because, in
    // activityResultRegistry.onLaunch, getSynchronousResult is always called first. Then
    // createIntent may be called afterwards.
    // We should expect a outgoingId created in getSynchronousResult and store it in
    // forResultOutGoingId.
    // https://source.corp.google.com/h/googleplex-android/platform/frameworks/support/+/androidx-platform-dev:activity/activity/src/main/java/androidx/activity/ComponentActivity.kt
    if (forResultOutGoingId != UNKNOWN_NODE_ID) {
      intent.putExtra(EXTRA_ONBOARDING_NODE_ID, forResultOutGoingId)
    } else {
      // getSynchronousResult is not called. This may be not called from
      // actityResultRegistry.onLaunch. We will use the outgoing which was just created in
      // performCreateIntent.
      forResultOutGoingId = intent.getLongExtra(EXTRA_ONBOARDING_NODE_ID, UNKNOWN_NODE_ID)
      Log.w(TAG, "getSynchronousResult was not called when creating intent.")
    }

    AndroidOnboardingGraphLog.log(
      ActivityNodeExecutedForResult(
        extractNodeId(context),
        forResultOutGoingId,
        this.javaClass,
        input,
      )
    )
    forResultOutGoingId = UNKNOWN_NODE_ID
    return intent
  }

  @OptIn(InternalOnboardingApi::class)
  final override fun parseResult(resultCode: Int, intent: Intent?): O {
    // Injection point when control has returned to the current activity

    val id = intent?.nodeId ?: UNKNOWN_NODE_ID

    val result = performParseResult(UnknownContractResult(resultCode, intent))

    AndroidOnboardingGraphLog.log(ActivityNodeResultReceived(id, this.javaClass, result))

    if (result is Failure) {
      AndroidOnboardingGraphLog.log(ActivityNodeFail(id, result.reason))
    }

    return result
  }

  final override fun getSynchronousResult(context: Context, input: I): SynchronousResult<O>? {
    // Injection point when making a synchronous call

    val thisNodeId = extractNodeId(context)

    forResultOutGoingId = newOutgoingId()

    AndroidOnboardingGraphLog.log(
      ActivityNodeStartExecuteSynchronously(thisNodeId, forResultOutGoingId, this.javaClass, input)
    )

    ContractExecutionEligibilityChecker.terminateIfNodeIsTriggeredByTestAndIsNotAllowed(
      context,
      this.javaClass,
    )

    val result = performGetSynchronousResult(context, input)
    if (result != null) {
      // Injection point when the synchronous result was used and the activity was skipped

      AndroidOnboardingGraphLog.log(
        ActivityNodeExecutedSynchronously(forResultOutGoingId, this.javaClass, result.value)
      )
    }

    return result
  }

  companion object {
    @Deprecated(
      message = "Moved",
      replaceWith =
        ReplaceWith(
          "UNKNOWN_NODE_ID",
          imports = ["com.android.onboarding.contracts.UNKNOWN_NODE_ID"],
        ),
    )
    const val UNKNOWN_NODE: NodeId = UNKNOWN_NODE_ID

    const val TAG = "OnboardingApiContract"

    // nodeId to outgoingId
    private val waitingForResumeActivity: MutableMap<Long, Long> = mutableMapOf()
  }
}

/** Equivalent to [OnboardingActivityApiContract] for contracts which do not return a result. */
abstract class VoidOnboardingActivityApiContract<I> : OnboardingActivityApiContract<I, Unit>() {
  final override fun performSetResult(result: Unit): ContractResult {
    // Does nothing - no result
    return Failure(0, reason = "Should never be called")
  }

  final override fun performParseResult(result: ContractResult) {
    // Does nothing - no result
  }
}

/** Equivalent to [OnboardingActivityApiContract] for contracts which do not take arguments. */
abstract class ArgumentFreeOnboardingActivityApiContract<O> :
  OnboardingActivityApiContract<Unit, O>() {
  final override fun performExtractArgument(intent: Intent) {
    // Does nothing - no argument
  }
}

/** Equivalent to [VoidOnboardingActivityApiContract] for contracts which do not take arguments. */
abstract class ArgumentFreeVoidOnboardingActivityApiContract :
  VoidOnboardingActivityApiContract<Unit>() {
  final override fun performExtractArgument(intent: Intent) {
    // Does nothing - no argument
  }
}

/** Returns [true] if the activity is launched using onboarding contract, [false] otherwise. */
fun Activity.isLaunchedByOnboardingContract(): Boolean {
  return this.intent.hasExtra(EXTRA_ONBOARDING_NODE_ID)
}
