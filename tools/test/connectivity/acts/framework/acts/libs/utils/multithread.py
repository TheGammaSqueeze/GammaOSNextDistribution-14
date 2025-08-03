#!/usr/bin/env python3
#
#   Copyright 2021 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import concurrent.futures
import logging

def task_wrapper(task):
    """Task wrapper for multithread_func

    Args:
        task[0]: function to be wrapped.
        task[1]: function args.

    Returns:
        Return value of wrapped function call.
    """
    func = task[0]
    params = task[1]
    return func(*params)


def run_multithread_func_async(log, task):
    """Starts a multi-threaded function asynchronously.

    Args:
        log: log object.
        task: a task to be executed in parallel.

    Returns:
        Future object representing the execution of the task.
    """
    executor = concurrent.futures.ThreadPoolExecutor(max_workers=1)
    try:
        future_object = executor.submit(task_wrapper, task)
    except Exception as e:
        log.error("Exception error %s", e)
        raise
    return future_object


def run_multithread_func(log, tasks):
    """Run multi-thread functions and return results.

    Args:
        log: log object.
        tasks: a list of tasks to be executed in parallel.

    Returns:
        results for tasks.
    """
    MAX_NUMBER_OF_WORKERS = 10
    number_of_workers = min(MAX_NUMBER_OF_WORKERS, len(tasks))
    executor = concurrent.futures.ThreadPoolExecutor(
        max_workers=number_of_workers)
    if not log: log = logging
    try:
        results = list(executor.map(task_wrapper, tasks))
    except Exception as e:
        log.error("Exception error %s", e)
        raise
    executor.shutdown()
    if log:
        log.info("multithread_func %s result: %s",
                 [task[0].__name__ for task in tasks], results)
    return results


def multithread_func(log, tasks):
    """Multi-thread function wrapper.

    Args:
        log: log object.
        tasks: tasks to be executed in parallel.

    Returns:
        True if all tasks return True.
        False if any task return False.
    """
    results = run_multithread_func(log, tasks)
    for r in results:
        if not r:
            return False
    return True


def multithread_func_and_check_results(log, tasks, expected_results):
    """Multi-thread function wrapper.

    Args:
        log: log object.
        tasks: tasks to be executed in parallel.
        expected_results: check if the results from tasks match expected_results.

    Returns:
        True if expected_results are met.
        False if expected_results are not met.
    """
    return_value = True
    results = run_multithread_func(log, tasks)
    log.info("multithread_func result: %s, expecting %s", results,
             expected_results)
    for task, result, expected_result in zip(tasks, results, expected_results):
        if result != expected_result:
            logging.info("Result for task %s is %s, expecting %s", task[0],
                         result, expected_result)
            return_value = False
    return return_value
