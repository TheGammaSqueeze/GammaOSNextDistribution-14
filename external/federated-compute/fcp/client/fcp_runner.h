/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
#ifndef FCP_CLIENT_FCP_RUNNER_H_
#define FCP_CLIENT_FCP_RUNNER_H_

#include <string>

#include "fcp/client/fl_runner.pb.h"
#include "fcp/client/flags.h"
#include "fcp/client/interruptible_runner.h"
#include "fcp/client/log_manager.h"
#include "fcp/client/simple_task_environment.h"
#include "fcp/protos/plan.pb.h"

namespace fcp {
namespace client {

// This is exposed for use that only invoke run plan on engine and exclude http
// protocol parts.
absl::StatusOr<FLRunnerResult> RunFederatedComputation(
    SimpleTaskEnvironment* env_deps, LogManager* log_manager,
    const Flags* flags,
    const google::internal::federated::plan::ClientOnlyPlan& client_plan,
    const std::string& checkpoint_input_filename,
    const std::string& checkpoint_output_filename,
    const std::string& session_name, const std::string& population_name,
    const std::string& task_name,
    const fcp::client::InterruptibleRunner::TimingConfig& timing_config);

}  // namespace client
}  // namespace fcp

#endif  // FCP_CLIENT_FCP_RUNNER_H_