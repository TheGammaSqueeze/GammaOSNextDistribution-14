/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "./execute.h"

#include <linux/securebits.h>
#include <linux/uio.h>
#include <seccomp_policy.h>
#include <sys/capability.h>
#include <sys/personality.h>
#include <sys/prctl.h>
#include <sys/ptrace.h>
#include <sys/wait.h>
#include <unistd.h>

#include <iostream>
#include <memory>

#include "./elf-utils.h"
#include "./registers.h"
#include "./shell-code.h"

namespace shell_as {

namespace {

// Capabilities are implemented as a 64-bit bit-vector. Therefore the maximum
// number of capabilities supported by a kernel is 64.
constexpr cap_value_t kMaxCapabilities = 64;

bool DropPreExecPrivileges(const shell_as::SecurityContext* context) {
  // The ordering here is important:
  //   (1) The platform's seccomp filters disallow setresgiud, so it must come
  //       before the seccomp drop.
  //   (2) Adding seccomp filters must happen before setresuid because setresuid
  //       drops some capabilities which are required for seccomp.
  if (context->group_id.has_value() &&
      setresgid(context->group_id.value(), context->group_id.value(),
                context->group_id.value()) != 0) {
    std::cerr << "Unable to set group id: " << context->group_id.value()
              << std::endl;
    return false;
  }
  if (context->supplementary_group_ids.has_value() &&
      setgroups(context->supplementary_group_ids.value().size(),
                context->supplementary_group_ids.value().data()) != 0) {
    std::cerr << "Unable to set supplementary groups." << std::endl;
    return false;
  }

  if (context->seccomp_filter.has_value()) {
    switch (context->seccomp_filter.value()) {
      case shell_as::kAppFilter:
        set_app_seccomp_filter();
        break;
      case shell_as::kAppZygoteFilter:
        set_app_zygote_seccomp_filter();
        break;
      case shell_as::kSystemFilter:
        set_system_seccomp_filter();
        break;
    }
  }

  // This must be set prior to setresuid, otherwise that call will drop the
  // permitted set of capabilities.
  if (prctl(PR_SET_KEEPCAPS, 1, 0, 0, 0) != 0) {
    std::cerr << "Unable to set keep capabilities." << std::endl;
    return false;
  }

  if (context->user_id.has_value() &&
      setresuid(context->user_id.value(), context->user_id.value(),
                context->user_id.value()) != 0) {
    std::cerr << "Unable to set user id: " << context->user_id.value()
              << std::endl;
    return false;
  }

  // Capabilities must be reacquired after setresuid since it still modifies
  // capabilities, but it leaves the permitted set intact.
  if (context->capabilities.has_value()) {
    // The first step is to raise all the capabilities possible in all sets
    // including the inheritable set. This defines the superset of possible
    // capabilities that can be passed on after calling execve.
    //
    // The reason that all capabilities are raised in the inheritable set is due
    // to a limitation of libcap. libcap may not contain a capability definition
    // for all capabilities supported by the kernel. If this occurs, it will
    // silently ignore requests to raise unknown capabilities via cap_set_flag.
    //
    // However, when parsing a cap_t from a text value, libcap will treat "all"
    // as all possible 64 capability bits as set.
    cap_t all_capabilities = cap_from_text("all+pie");
    if (cap_set_proc(all_capabilities) != 0) {
      std::cerr << "Unable to raise inheritable capability set." << std::endl;
      cap_free(all_capabilities);
      return false;
    }
    cap_free(all_capabilities);

    // The second step is to raise the /desired/ capability subset in the
    // ambient capability set. These are the capabilities that will actually be
    // passed to the process after execve.
    if (prctl(PR_CAP_AMBIENT, PR_CAP_AMBIENT_CLEAR_ALL, 0, 0, 0) != 0) {
      std::cerr << "Unable to clear ambient capabilities." << std::endl;
      return false;
    }
    cap_t desired_capabilities = context->capabilities.value();
    for (cap_value_t cap = 0; cap < kMaxCapabilities; cap++) {
      // Skip capability values not supported by the kernel.
      if (!CAP_IS_SUPPORTED(cap)) {
        continue;
      }
      cap_flag_value_t value = CAP_CLEAR;
      if (cap_get_flag(desired_capabilities, cap, CAP_PERMITTED, &value) == 0 &&
          value == CAP_SET) {
        if (prctl(PR_CAP_AMBIENT, PR_CAP_AMBIENT_RAISE, cap, 0, 0) != 0) {
          std::cerr << "Unable to raise capability " << cap
                    << " in the ambient set." << std::endl;
          return false;
        }
      }
    }

    // The final step is to raise the SECBIT_NOROOT flag. The kernel has special
    // case logic that treats root calling execve differently than other users.
    //
    // By default all bits in the permitted set prior to calling execve will be
    // raised after calling execve. This would ignore the work above and result
    // in the process to have all capabilities.
    //
    // Setting the SECBIT_NOROOT disables this special casing for root and
    // causes the kernel to treat it as any other UID.
    int64_t secure_bits = prctl(PR_GET_SECUREBITS, 0, 0, 0, 0);
    if (secure_bits < 0 ||
        prctl(PR_SET_SECUREBITS, secure_bits | SECBIT_NOROOT, 0, 0, 0) != 0) {
      std::cerr << "Unable to raise SECBIT_NOROOT." << std::endl;
      return false;
    }
  }
  return true;
}

uint8_t ReadChildByte(const pid_t process, const uintptr_t address) {
  uintptr_t data = ptrace(PTRACE_PEEKDATA, process, address, nullptr);
  return ((uint8_t*)&data)[0];
}

void WriteChildByte(const pid_t process, const uintptr_t address,
                    const uint8_t value) {
  // This is not the most efficient way to write data to a process. However, it
  // reduces code complexity of handling different word sizes and reading and
  // writing memory that is not a multiple of the native word size.
  uintptr_t data = ptrace(PTRACE_PEEKDATA, process, address, nullptr);
  ((uint8_t*)&data)[0] = value;
  ptrace(PTRACE_POKEDATA, process, address, data);
}

void ReadChildMemory(const pid_t process, uintptr_t process_address,
                     uint8_t* bytes, size_t byte_count) {
  for (; byte_count != 0; byte_count--, bytes++, process_address++) {
    *bytes = ReadChildByte(process, process_address);
  }
}

void WriteChildMemory(const pid_t process, uintptr_t process_address,
                      uint8_t const* bytes, size_t byte_count) {
  for (; byte_count != 0; byte_count--, bytes++, process_address++) {
    WriteChildByte(process, process_address, *bytes);
  }
}

// Executes shell code in a target process.
//
// The following assumptions are made:
//  * The process is currently being ptraced and that the process has already
//    stopped.
//  * The shell code will raise SIGSTOP when it has finished as signal that
//    control flow should be handed back to the original code.
//  * The shell code only alters registers and pushes values onto the stack.
//
// Execution is performed by overwriting the memory under the current
// instruction pointer with the shell code. After the shell code signals
// completion the original register state and memory are restored.
//
// If the above assumptions are met, then this function will leave the process
// in a stopped state that is equivalent to the original state.
bool ExecuteShellCode(const pid_t process, const uint8_t* shell_code,
                      const size_t shell_code_size) {
  REGISTER_STRUCT registers;
  struct iovec registers_iovec;
  registers_iovec.iov_base = &registers;
  registers_iovec.iov_len = sizeof(REGISTER_STRUCT);
  ptrace(PTRACE_GETREGSET, process, 1, &registers_iovec);

  std::unique_ptr<uint8_t[]> memory_backup(new uint8_t[shell_code_size]);
  ReadChildMemory(process, PROGRAM_COUNTER(registers), memory_backup.get(),
                  shell_code_size);
  WriteChildMemory(process, PROGRAM_COUNTER(registers), shell_code,
                   shell_code_size);

  // Execute the shell code and wait for the signal that it has finished.
  ptrace(PTRACE_CONT, process, NULL, NULL);
  int status;
  waitpid(process, &status, 0);
  if (status >> 8 != SIGSTOP) {
    std::cerr << "Failed to execute SELinux shellcode." << std::endl;
    return false;
  }

  ptrace(PTRACE_SETREGSET, process, 1, &registers_iovec);
  WriteChildMemory(process, PROGRAM_COUNTER(registers), memory_backup.get(),
                   shell_code_size);
  return true;
}

bool SetProgramCounter(const pid_t process_id, uint64_t program_counter) {
  REGISTER_STRUCT registers;
  struct iovec registers_iovec;
  registers_iovec.iov_base = &registers;
  registers_iovec.iov_len = sizeof(REGISTER_STRUCT);
  if (ptrace(PTRACE_GETREGSET, process_id, 1, &registers_iovec) != 0) {
    return false;
  }
  PROGRAM_COUNTER(registers) = program_counter;
  if ((ptrace(PTRACE_SETREGSET, process_id, 1, &registers_iovec)) != 0) {
    return false;
  }
  return true;
}

bool StepToEntryPoint(const pid_t process_id) {
  bool is_arm_mode;
  uint64_t entry_address;
  if (!GetElfEntryPoint(process_id, &entry_address, &is_arm_mode)) {
    std::cerr << "Not able to determine Elf entry point." << std::endl;
    return false;
  }
  if (is_arm_mode) {
    // TODO(willcoster): If there is a need to handle ARM mode instructions in
    // addition to thumb instructions update this with ARM mode shell code.
    std::cerr << "Attempting to run an ARM-mode binary. "
              << "shell-as currently only supports thumb-mode. "
              << "Bug willcoster@ if you run into this error." << std::endl;
    return false;
  }

  int expected_signal = 0;
  size_t trap_code_size = 0;
  std::unique_ptr<uint8_t[]> trap_code =
      GetTrapShellCode(&expected_signal, &trap_code_size);
  std::unique_ptr<uint8_t[]> backup(new uint8_t[trap_code_size]);

  // Set a break point at the entry point declared by the Elf file. When a
  // statically linked binary is executed this will be the first instruction
  // executed.
  //
  // When a dynamically linked binary is executed, the dynamic linker is
  // executed first. This brings .so files into memory and resolves shared
  // symbols. Once this process is finished, it jumps to the entry point
  // declared in the Elf file.
  ReadChildMemory(process_id, entry_address, backup.get(), trap_code_size);
  WriteChildMemory(process_id, entry_address, trap_code.get(), trap_code_size);
  ptrace(PTRACE_CONT, process_id, NULL, NULL);
  int status;
  waitpid(process_id, &status, 0);
  if (status >> 8 != expected_signal) {
    std::cerr << "Program exited unexpectedly while stepping to entry point."
              << std::endl;
    std::cerr << "Expected status " << expected_signal << " but encountered "
              << (status >> 8) << std::endl;
    return false;
  }

  if (!SetProgramCounter(process_id, entry_address)) {
    return false;
  }
  WriteChildMemory(process_id, entry_address, backup.get(), trap_code_size);
  return true;
}

}  // namespace

bool ExecuteInContext(char* const executable_and_args[],
                      const shell_as::SecurityContext* context) {
  // Getting an executable running in a lower privileged context is tricky with
  // SELinux. The recommended approach in the documentation is to use setexeccon
  // which sets the context on the next execve call.
  //
  // However, this doesn't work for unprivileged processes like untrusted apps
  // in Android because they are not allowed to execute most binaries.
  //
  // To work around this, ptrace is used to inject shell code into the new
  // process just after it has executed an execve syscall. This shell code then
  // sets the desired SELinux context.
  pid_t child = fork();
  if (child == 0) {
    // Disabling ASLR makes it easier to determine the entry point of the target
    // executable.
    personality(ADDR_NO_RANDOMIZE);

    // Drop the privileges that can be dropped before executing the new binary
    // and exit early if there is an issue.
    if (!DropPreExecPrivileges(context)) {
      exit(1);
    }

    ptrace(PTRACE_TRACEME, 0, NULL, NULL);
    raise(SIGSTOP);  // Wait for the parent process to attach.
    execv(executable_and_args[0], executable_and_args);
  } else {
    // Wait for the child to reach the SIGSTOP line above.
    int status;
    waitpid(child, &status, 0);
    if ((status >> 8) != SIGSTOP) {
      // If the first status is not SIGSTOP, then the child aborted early
      // because it was not able to set the user and group IDs.
      return false;
    }

    // Break inside the child's execv call.
    ptrace(PTRACE_SETOPTIONS, child, NULL,
           PTRACE_O_TRACEEXEC | PTRACE_O_EXITKILL);
    ptrace(PTRACE_CONT, child, NULL, NULL);
    waitpid(child, &status, 0);
    if (status >> 8 != (SIGTRAP | PTRACE_EVENT_EXEC << 8)) {
      std::cerr << "Failed to execute " << executable_and_args[0] << std::endl;
      return false;
    }

    // Allow the dynamic linker to run before dropping to a lower SELinux
    // context. This is required for executing in some very constrained domains
    // like mediacodec.
    //
    // If the context was dropped before the dynamic linker runs, then when the
    // linker attempts to read /proc/self/exe to determine dynamic symbol
    // information, SELinux will kill the binary if the domain is not allowed to
    // read the binary's executable file.
    //
    // This happens for example, when attempting to run any toybox binary (id,
    // sh, etc) as mediacodec.
    if (!StepToEntryPoint(child)) {
      std::cerr << "Something bad happened stepping to the entry point."
                << std::endl;
      return false;
    }

    // Run the SELinux shellcode in the child process before the child can
    // execute any instructions in the newly loaded executable.
    if (context->selinux_context.has_value()) {
      size_t shell_code_size;
      std::unique_ptr<uint8_t[]> shell_code = GetSELinuxShellCode(
          context->selinux_context.value(), &shell_code_size);
      bool success = ExecuteShellCode(child, shell_code.get(), shell_code_size);
      if (!success) {
        return false;
      }
    }

    // Resume and detach from the child now that the SELinux context has been
    // updated.
    ptrace(PTRACE_DETACH, child, NULL, NULL);
    waitpid(child, nullptr, 0);
  }
  return true;
}

}  // namespace shell_as
