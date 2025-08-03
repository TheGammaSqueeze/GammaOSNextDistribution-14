package com.android.onboarding.tasks

import com.google.common.util.concurrent.ListenableFuture

/**
 * Manages the execution and state of onboarding tasks within the onboarding process. This interface
 * provides a set of functions for triggering a task, monitoring, and obtaining results from
 * onboarding tasks.
 */
interface OnboardingTaskManager {

  /**
   * Executes an onboarding task asynchronously and returns a token for subsequent status queries.
   *
   * @param task The onboarding task to be executed. This parameter will be removed in the later
   *   milestone stage.
   * @param taskContract The contract defining the task's arguments and result for the onboarding
   *   task.
   * @return The token associated with the initiated onboarding task.
   */
  fun <
    Result,
    Task : OnboardingTask<Unit, Result>,
    Contract : OnboardingTaskContract<Unit, Result>,
  > runTask(task: Task, taskContract: Contract): OnboardingTaskToken =
    runTask(task, taskContract, Unit)

  /**
   * Executes an onboarding task asynchronously and returns a token for subsequent status queries.
   *
   * @param task The onboarding task to be executed. This parameter will be removed in the later
   *   milestone stage.
   * @param taskContract The contract defining task's arguments and result for the onboarding task.
   * @param taskArgs A defined argument for the onboarding task.
   * @return The token associated with the initiated onboarding task.
   */
  fun <
    Args,
    Result,
    Task : OnboardingTask<Args, Result>,
    Contract : OnboardingTaskContract<Args, Result>,
  > runTask(task: Task, taskContract: Contract, taskArgs: Args): OnboardingTaskToken

  /**
   * Executes an onboarding task asynchronously and waits for its completion, providing the final
   * result.
   *
   * @param task The onboarding task to be executed. This parameter will be removed in the later
   *   milestone stage.
   * @param taskContract The contract defining task's arguments and result for the onboarding task.
   * @param taskArgs A defined argument for the onboarding task.
   * @return The final result of the onboarding task.
   */
  suspend fun <Args, Result> runTaskAndGetResult(
    task: OnboardingTask<Args, Result>,
    taskContract: OnboardingTaskContract<Args, Result>,
    taskArgs: Args,
  ): OnboardingTaskState<Result>

  /**
   * Executes an onboarding task asynchronously and waits for its completion, providing the final
   * result.
   *
   * @param task The onboarding task to be executed. This parameter will be removed in the later
   *   milestone stage.
   * @param taskContract The contract defining task's arguments and result for the onboarding task.
   * @param taskArgs A defined argument for the onboarding task.
   * @return A [ListenableFuture] representing the final state of the onboarding task. The future
   *   encapsulates the asynchronous execution and completion of the task, allowing for the
   *   retrieval of the task state or result.
   */
  fun <Args, Result> runTaskAndGetResultAsync(
    task: OnboardingTask<Args, Result>,
    taskContract: OnboardingTaskContract<Args, Result>,
    taskArgs: Args,
  ): ListenableFuture<OnboardingTaskState<Result>>

  /**
   * Retrieves the current state of a previously initiated onboarding task.
   *
   * @param taskToken The token associated with the onboarding task.
   * @return The result of the onboarding task, or null if no matching task exists.
   */
  fun <RESULT> getTaskState(taskToken: OnboardingTaskToken): OnboardingTaskState<RESULT>?

  /**
   * Waits for the result of a previously initiated onboarding task.
   *
   * If the provided [taskToken] is not valid, the caller receives a null object, indicating that
   * this token is not recorded in the manager.
   *
   * If the [taskToken] represents a task in a timeout state or encounters an exeception, the caller
   * receives a [OnboardingTaskState.Failed] state.
   *
   * If the onboarding task completes successfully, the caller receives a
   * [OnboardingTaskState.Completed] state with [Result] data.
   *
   * @param taskToken The token associated with the onboarding task.
   * @return The final result of the onboarding task, or null if the token is not valid.
   */
  suspend fun <Result> waitForCompleted(
    taskToken: OnboardingTaskToken
  ): OnboardingTaskState<Result>? = null

  /**
   * Asynchronously waits for the result of a previously initiated onboarding task.
   *
   * If the provided [taskToken] is not valid, the caller receives a null object, indicating that
   * this token is not recorded in the manager.
   *
   * If the [taskToken] represents a task in a timeout state or encounters an exeception, the caller
   * receives a [OnboardingTaskState.Failed] state.
   *
   * If the onboarding task completes successfully, the caller receives a
   * [OnboardingTaskState.Completed] state with [Result] data.
   *
   * @param taskToken The token associated with the onboarding task.
   * @return A [ListenableFuture] containing the final result of the onboarding task.
   */
  fun <Result> waitForCompletedAsync(
    taskToken: OnboardingTaskToken
  ): ListenableFuture<OnboardingTaskState<Result>?>
}
