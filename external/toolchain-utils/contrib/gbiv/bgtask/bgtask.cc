/*
 * Copyright 2023 The ChromiumOS Authors
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
#include <err.h>
#include <errno.h>
#include <linux/ioprio.h>
#include <sched.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/resource.h>
#include <sys/syscall.h>
#include <sys/sysinfo.h>
#include <unistd.h>

#include <string_view>

static const char description[] = R"(bgtask - `nice -n19` but more.

This program exists to set a few process attributes, and exec another program.

Intended usage is:
$ bgtask ./my_long_running_build --extra-optimizations --and-more

Specifically, `bgtask`:
  - sets its own priority so essentially any other task will take priority, then
  - sets its I/O priority so any regular task will take priority, then
  - sets its CPU mask so it may only run on 9/10ths of your cores (this step is
    skipped if you've already got a more restrictive mask), then
  - execs the command you gave it.
)";

static void print_help_and_exit(int exit_code) {
  fputs(description, stderr);
  exit(exit_code);
}

static void deprioritize_nice_or_warn() {
  constexpr int current_process = 0;
  constexpr int max_nice_priority = 19;
  if (setpriority(PRIO_PROCESS, current_process, max_nice_priority)) {
    warn("setting priority failed");
  }
}

static void deprioritize_io_or_warn() {
  // Glibc provides no wrapper here.
  constexpr int current_process = 0;
  const int background_io_prio = IOPRIO_PRIO_VALUE(IOPRIO_CLASS_IDLE, 0);
  if (syscall(SYS_ioprio_set, IOPRIO_WHO_PROCESS, current_process,
              background_io_prio) == -1) {
    warn("setting ioprio failed");
  }
}

static void restrict_cpu_mask_or_warn() {
  const int available_cpus = get_nprocs();
  // Use 9/10ths of CPUs, as requested in the mask.
  const int cpus_to_use = (available_cpus * 9) / 10;
  // Erm, single-core systems need not apply.
  if (cpus_to_use == 0) {
    return;
  }

  // Note pid==0 means "the current thread," for sched_*affinity calls.
  constexpr int current_process = 0;
  cpu_set_t current_mask;
  CPU_ZERO(&current_mask);
  if (sched_getaffinity(current_process, sizeof(current_mask), &current_mask) ==
      -1) {
    if (errno == EINVAL) {
      // This can only happen if a machine has >1K cores. That can be handled,
      // but is extra complexity that I can't test & isn't expected to
      // realistically be a problem in the next few years.
      warnx("statically-allocated cpu affinity mask is too small");
    } else {
      warn("sched_getaffinity failed");
    }
    return;
  }

  const int cpus_in_current_mask = CPU_COUNT(&current_mask);
  int cpus_to_disable = cpus_in_current_mask - cpus_to_use;
  if (cpus_to_disable <= 0) {
    // Don't warn; this probably isn't useful informtion to the user.
    return;
  }

  for (int i = 0; i < available_cpus; ++i) {
    if (CPU_ISSET(i, &current_mask)) {
      CPU_CLR(i, &current_mask);
      --cpus_to_disable;
      if (cpus_to_disable == 0) {
        break;
      }
    }
  }

  if (cpus_to_disable != 0) {
    warnx("Internal error: iterated through CPU mask but had %d CPUs left to "
          "disable.",
          cpus_to_disable);
    return;
  }

  if (sched_setaffinity(current_process, sizeof(current_mask), &current_mask) ==
      -1) {
    warn("sched_setaffinity failed");
  }
}

__attribute__((noreturn)) int main(int argc, char **argv) {
  if (argc == 1) {
    print_help_and_exit(/*exit_code=*/1);
  }

  std::string_view argv1 = argv[1];
  // Do minimal option parsing, since the user is likely to be passing `-flags`
  // and `--flags` to the program they're invoking.
  if (argv1 == "-h" || argv1 == "--help") {
    print_help_and_exit(/*exit_code=*/0);
  }

  deprioritize_nice_or_warn();
  deprioritize_io_or_warn();
  restrict_cpu_mask_or_warn();

  execvp(argv[1], &argv[1]);
  err(1, "execvp failed");
}
