// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <ditto/instruction_factory.h>

#include <fcntl.h>
#include <sys/types.h>

#include <random>

#include <ditto/binder_request.h>
#include <ditto/binder_service.h>
#include <ditto/close_file.h>
#include <ditto/cpu_work.h>
#include <ditto/delete_file.h>
#include <ditto/instruction_set.h>
#include <ditto/invalidate_cache.h>
#include <ditto/logger.h>
#include <ditto/memory_allocation.h>
#include <ditto/multiprocessing.h>
#include <ditto/multithreading.h>
#include <ditto/multithreading_utils.h>
#include <ditto/open_file.h>
#include <ditto/read_directory.h>
#include <ditto/read_write_file.h>
#include <ditto/resize_file.h>
#include <ditto/shared_variables.h>
#include <ditto/syscall.h>

namespace dittosuite {
typedef dittosuiteproto::Instruction::InstructionOneofCase InstructionType;
typedef dittosuiteproto::BinderRequest::ServiceOneofCase RequestService;
typedef dittosuiteproto::CpuWork::TypeCase CpuWorkType;

std::unique_ptr<InstructionSet> InstructionFactory::CreateFromProtoInstructionSet(
    const dittosuite::Instruction::Params& instruction_params, const std::list<int>& thread_ids,
    const dittosuiteproto::InstructionSet& proto_instruction_set) {
  std::vector<std::unique_ptr<Instruction>> instructions;
  for (const auto& instruction : proto_instruction_set.instructions()) {
    instructions.push_back(
        std::move(InstructionFactory::CreateFromProtoInstruction(thread_ids, instruction)));
  }

  if (proto_instruction_set.has_iterate_options()) {
    const auto& options = proto_instruction_set.iterate_options();

    int list_key = SharedVariables::GetKey(thread_ids, options.list_name());
    int item_key = SharedVariables::GetKey(thread_ids, options.item_name());
    auto access_order = ConvertOrder(options.access_order());
    auto reseeding = ConvertReseeding(options.reseeding());

    uint32_t seed = options.seed();
    if (!options.has_seed()) {
      seed = time(nullptr);
    }

    return std::make_unique<InstructionSet>(instruction_params, std::move(instructions), list_key,
                                            item_key, access_order, reseeding, seed);
  } else {
    return std::make_unique<InstructionSet>(instruction_params, std::move(instructions));
  }
}

std::unique_ptr<Instruction> InstructionFactory::CreateFromProtoInstruction(
    const std::list<int>& thread_ids, const dittosuiteproto::Instruction& proto_instruction) {
  Instruction::Params instruction_params(Syscall::GetSyscall(), proto_instruction.repeat(),
                                         proto_instruction.period_us());

  switch (proto_instruction.instruction_oneof_case()) {
    case InstructionType::kInstructionSet: {
      return InstructionFactory::CreateFromProtoInstructionSet(instruction_params, thread_ids,
                                                               proto_instruction.instruction_set());
    }
    case InstructionType::kOpenFile: {
      const auto& options = proto_instruction.open_file();

      int fd_key = -1;
      if (options.has_output_fd()) {
        fd_key = SharedVariables::GetKey(thread_ids, options.output_fd());
      }

      dittosuite::OpenFile::AccessMode access_mode;
      {
        switch (options.access_mode()) {
          case dittosuiteproto::AccessMode::READ_ONLY:
            access_mode = OpenFile::AccessMode::kReadOnly;
            break;
          case dittosuiteproto::AccessMode::WRITE_ONLY:
            access_mode = OpenFile::AccessMode::kWriteOnly;
            break;
          case dittosuiteproto::AccessMode::READ_WRITE:
            access_mode = OpenFile::AccessMode::kReadWrite;
            break;
          default:
            LOGF("Invalid instruction OpenFile access mode: it should be at least read or write");
            break;
        }
      }

      if (options.has_input()) {
        int input_key = SharedVariables::GetKey(thread_ids, options.input());
        return std::make_unique<OpenFile>(instruction_params, input_key, options.create(),
                                          options.direct_io(), fd_key, access_mode);
      } else if (options.has_path_name()) {
        return std::make_unique<OpenFile>(instruction_params, options.path_name(), options.create(),
                                          options.direct_io(), fd_key, access_mode);
      } else {
        return std::make_unique<OpenFile>(instruction_params, options.create(), options.direct_io(),
                                          fd_key, access_mode);
      }
    }
    case InstructionType::kDeleteFile: {
      const auto& options = proto_instruction.delete_file();

      if (options.has_input()) {
        int input_key = SharedVariables::GetKey(thread_ids, options.input());
        return std::make_unique<DeleteFile>(instruction_params, input_key);
      } else {
        return std::make_unique<DeleteFile>(instruction_params, options.path_name());
      }
    }
    case InstructionType::kCloseFile: {
      const auto& options = proto_instruction.close_file();

      int fd_key = SharedVariables::GetKey(thread_ids, options.input_fd());

      return std::make_unique<CloseFile>(instruction_params, fd_key);
    }
    case InstructionType::kResizeFile: {
      const auto& options = proto_instruction.resize_file();

      int fd_key = SharedVariables::GetKey(thread_ids, options.input_fd());

      return std::make_unique<ResizeFile>(instruction_params, options.size(), fd_key);
    }
    case InstructionType::kWriteFile: {
      const auto& options = proto_instruction.write_file();

      auto access_order = ConvertOrder(options.access_order());

      uint32_t seed = options.seed();
      if (!options.has_seed()) {
        seed = time(nullptr);
      }

      auto reseeding = ConvertReseeding(options.reseeding());
      int fd_key = SharedVariables::GetKey(thread_ids, options.input_fd());

      return std::make_unique<WriteFile>(instruction_params, options.size(), options.block_size(),
                                         options.starting_offset(), access_order, seed, reseeding,
                                         options.fsync(), fd_key);
    }
    case InstructionType::kReadFile: {
      const auto& options = proto_instruction.read_file();

      auto access_order = ConvertOrder(options.access_order());

      uint32_t seed = options.seed();
      if (!options.has_seed()) {
        seed = time(nullptr);
      }

      auto fadvise = ConvertReadFAdvise(access_order, options.fadvise());
      auto reseeding = ConvertReseeding(options.reseeding());
      int fd_key = SharedVariables::GetKey(thread_ids, options.input_fd());

      return std::make_unique<ReadFile>(instruction_params, options.size(), options.block_size(),
                                        options.starting_offset(), access_order, seed, reseeding,
                                        fadvise, fd_key);
    }
    case InstructionType::kReadDirectory: {
      const auto& options = proto_instruction.read_directory();

      int output_key = SharedVariables::GetKey(thread_ids, options.output());

      return std::make_unique<ReadDirectory>(instruction_params, options.directory_name(),
                                             output_key);
    }
    case InstructionType::kResizeFileRandom: {
      const auto& options = proto_instruction.resize_file_random();

      uint32_t seed = options.seed();
      if (!options.has_seed()) {
        seed = time(nullptr);
      }

      auto reseeding = ConvertReseeding(options.reseeding());
      int fd_key = SharedVariables::GetKey(thread_ids, options.input_fd());

      return std::make_unique<ResizeFileRandom>(instruction_params, options.min(), options.max(),
                                                seed, reseeding, fd_key);
    }
    case InstructionType::kMultithreading: {
      const auto& options = proto_instruction.multithreading();

      std::vector<MultithreadingParams> thread_params;
      std::vector<std::unique_ptr<Instruction>> instructions;
      for (const auto& thread : options.threads()) {
        for (int i = 0; i < thread.spawn(); i++) {
          auto thread_ids_copy = thread_ids;
          thread_ids_copy.push_back(InstructionFactory::GenerateThreadId());
          instructions.push_back(std::move(InstructionFactory::CreateFromProtoInstruction(
              thread_ids_copy, thread.instruction())));

          std::string thread_name;
          if (thread.has_name()) {
            thread_name = thread.name() + "_" + std::to_string(i);
          } else {
            thread_name = std::to_string(i);
          }

          SchedAttr sched_attr = {};
          if (thread.has_sched_attr()) {
            sched_attr = thread.sched_attr();
          }

          SchedAffinity sched_affinity = {};
          if (thread.has_sched_affinity()) {
            sched_affinity = thread.sched_affinity();
          }

          thread_params.push_back(MultithreadingParams(thread_name, sched_attr, sched_affinity));
        }
      }

      if (options.fork()) {
        return std::make_unique<Multiprocessing>(instruction_params, std::move(instructions),
                                                 std::move(thread_params));
      } else {
        return std::make_unique<Multithreading>(instruction_params, std::move(instructions),
                                                std::move(thread_params));
      }
    }
    case InstructionType::kInvalidateCache: {
      return std::make_unique<InvalidateCache>(instruction_params);
    }
#if __ANDROID__
    case InstructionType::kBinderRequest: {
      const auto& binder_request = proto_instruction.binder_request();
      switch (binder_request.service_oneof_case()) {
        case RequestService::kServiceName: {
          const auto& options = proto_instruction.binder_request();
          return std::make_unique<BinderRequestDitto>(instruction_params, options.service_name());
          break;
        }
        case RequestService::kRunningService: {
          return std::make_unique<BinderRequestMountService>(instruction_params);
          break;
        }
        case RequestService::kGenericService: {
          const auto& options = proto_instruction.binder_request();
          const auto& generic_service = options.generic_service();
          return std::make_unique<GenericBinderRequest>(instruction_params, generic_service.name(), generic_service.code(), generic_service.parcel_input());
        }
        case RequestService::SERVICE_ONEOF_NOT_SET: {
          LOGF("No service specified for BinderRequest");
          break;
        }
      }
      break;
    }
    case InstructionType::kBinderService: {
      const auto& options = proto_instruction.binder_service();

      return std::make_unique<BinderService>(instruction_params, options.name(), options.threads());
    }
#endif /*__ANDROID__*/
    case InstructionType::kCpuWork: {
      const auto& options = proto_instruction.cpu_work();

      switch (options.type_case()) {
        case CpuWorkType::kCycles: {
          return std::make_unique<CpuWorkCycles>(instruction_params, options.cycles());
          break;
        }
        case CpuWorkType::kUtilization: {
          return std::make_unique<CpuWorkUtilization>(instruction_params, options.utilization());
          break;
        }
        case CpuWorkType::TYPE_NOT_SET: {
          LOGF("No type specified for CpuWorkload");
          break;
        }
      }
    }
    case InstructionType::kMemAlloc: {
      const auto& options = proto_instruction.mem_alloc();
      return std::make_unique<MemoryAllocation>(instruction_params, options.size());
      break;
    }
    case InstructionType::INSTRUCTION_ONEOF_NOT_SET: {
      LOGF("Instruction was not set in .ditto file");
      break;
    }
    default: {
      LOGF("Invalid instruction was set in .ditto file");
    }
  }
}

int InstructionFactory::GenerateThreadId() {
  return current_thread_id_++;
}

int InstructionFactory::current_thread_id_ = 0;

Reseeding InstructionFactory::ConvertReseeding(const dittosuiteproto::Reseeding proto_reseeding) {
  switch (proto_reseeding) {
    case dittosuiteproto::Reseeding::ONCE: {
      return Reseeding::kOnce;
    }
    case dittosuiteproto::Reseeding::EACH_ROUND_OF_CYCLES: {
      return Reseeding::kEachRoundOfCycles;
    }
    case dittosuiteproto::Reseeding::EACH_CYCLE: {
      return Reseeding::kEachCycle;
    }
    default: {
      LOGF("Invalid Reseeding was provided");
    }
  }
}

Order InstructionFactory::ConvertOrder(const dittosuiteproto::Order proto_order) {
  switch (proto_order) {
    case dittosuiteproto::Order::SEQUENTIAL: {
      return Order::kSequential;
    }
    case dittosuiteproto::Order::RANDOM: {
      return Order::kRandom;
    }
    default: {
      LOGF("Invalid Order was provided");
    }
  }
}

int InstructionFactory::ConvertReadFAdvise(
    const Order access_order, const dittosuiteproto::ReadFile_ReadFAdvise proto_fadvise) {
  switch (proto_fadvise) {
    case dittosuiteproto::ReadFile_ReadFAdvise_AUTOMATIC: {
      switch (access_order) {
        case Order::kSequential: {
          return POSIX_FADV_SEQUENTIAL;
        }
        case Order::kRandom: {
          return POSIX_FADV_RANDOM;
        }
      }
    }
    case dittosuiteproto::ReadFile_ReadFAdvise_NORMAL: {
      return POSIX_FADV_NORMAL;
    }
    case dittosuiteproto::ReadFile_ReadFAdvise_SEQUENTIAL: {
      return POSIX_FADV_SEQUENTIAL;
    }
    case dittosuiteproto::ReadFile_ReadFAdvise_RANDOM: {
      return POSIX_FADV_RANDOM;
    }
    default: {
      LOGF("Invalid ReadFAdvise was provided");
    }
  }
}

}  // namespace dittosuite
