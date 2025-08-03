package com.android.onboarding.tasks

import android.content.Context
import java.time.Duration
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * An abstract base class for defining onboarding tasks in the onboarding process.
 *
 * @param context The application context to be used during task execution.
 * @param Args The type representing the input arguments for the onboarding task.
 * @param Result The type representing the result of the onboarding task.
 */
abstract class OnboardingTask<Args, Result>(val context: Context) {

  // Default timeout value if not specified by the child class
  protected open val defaultTimeout: Duration = Duration.ofSeconds(5)

  /**
   * Runs an onboarding task.
   *
   * @param taskContract The contract representing the onboarding task to be executed.
   * @param taskArgs The input arguments for the task.
   * @param timeout The timeout duration. If not specified, the default timeout defined by the
   *   implementing class will be used.
   * @return An [OnboardingTaskState] representing the result of the task execution. If the task
   *   times out, a failed result will be returned.
   */
  suspend fun runTask(
    taskContract: OnboardingTaskContract<Args, Result>,
    taskArgs: Args,
    timeout: Duration = defaultTimeout,
  ): OnboardingTaskState<Result> {
    var result: Result? = null
    return try {
      if (!taskContract.validate(taskArgs)) {
        return OnboardingTaskState.Failed(
          "Task argument doesn't align with the contract(${taskContract::class.java.simpleName})" +
            "definition. Failed returned immediately before execution.",
          null,
        )
      }

      withTimeout(timeout.toMillis()) {
        result = runTask(taskContract, taskArgs)
        OnboardingTaskState.Completed(result)
      }
    } catch (timeout: TimeoutCancellationException) {
      OnboardingTaskState.Failed("Task timed out", result)
    } catch (e: Exception) {
      OnboardingTaskState.Failed("Task failed : " + e.message, result)
    }
  }

  /**
   * Abstract method to be implemented by child classes. Executes the onboarding task
   * asynchronously.
   *
   * @param taskContract The contract representing the onboarding task to be executed.
   * @param taskArgs The input arguments for the task.
   * @return The result of the onboarding task.
   */
  protected abstract suspend fun runTask(
    taskContract: OnboardingTaskContract<Args, Result>,
    taskArgs: Args,
  ): Result
}
