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

#include "fcp/client/fcp_runner.h"

#include "fcp/client/engine/example_iterator_factory.h"
#include "fcp/client/engine/example_query_plan_engine.h"
#include "fcp/client/engine/plan_engine_helpers.h"
#include "fcp/client/engine/tflite_plan_engine.h"
#include "fcp/client/fl_runner.pb.h"
#include "fcp/client/opstats/opstats_logger.h"
#include "fcp/protos/plan.pb.h"

namespace fcp {
namespace client {

using ::fcp::client::opstats::OpStatsLogger;
using ::google::internal::federated::plan::AggregationConfig;
using ::google::internal::federated::plan::ClientOnlyPlan;
using ::google::internal::federated::plan::FederatedComputeIORouter;
using ::google::internal::federated::plan::TensorflowSpec;

using TfLiteInputs = absl::flat_hash_map<std::string, std::string>;
namespace {

// Creates an ExampleIteratorFactory that routes queries to the
// SimpleTaskEnvironment::CreateExampleIterator() method.
std::unique_ptr<engine::ExampleIteratorFactory>
CreateSimpleTaskEnvironmentIteratorFactory(
    SimpleTaskEnvironment* task_env, const SelectorContext& selector_context) {
  return std::make_unique<engine::FunctionalExampleIteratorFactory>(
      /*can_handle_func=*/
      [](const google::internal::federated::plan::ExampleSelector&) {
        // The SimpleTaskEnvironment-based ExampleIteratorFactory should
        // be the catch-all factory that is able to handle all queries
        // that no other ExampleIteratorFactory is able to handle.
        return true;
      },
      /*create_iterator_func=*/
      [task_env, selector_context](
          const google::internal::federated::plan::ExampleSelector&
              example_selector) {
        return task_env->CreateExampleIterator(example_selector,
                                               selector_context);
      },
      /*should_collect_stats=*/true);
}

std::unique_ptr<TfLiteInputs> ConstructTFLiteInputsForTensorflowSpecPlan(
    const FederatedComputeIORouter& io_router,
    const std::string& checkpoint_input_filename,
    const std::string& checkpoint_output_filename) {
  auto inputs = std::make_unique<TfLiteInputs>();
  if (!io_router.input_filepath_tensor_name().empty()) {
    (*inputs)[io_router.input_filepath_tensor_name()] =
        checkpoint_input_filename;
  }

  if (!io_router.output_filepath_tensor_name().empty()) {
    (*inputs)[io_router.output_filepath_tensor_name()] =
        checkpoint_output_filename;
  }

  return inputs;
}

absl::StatusOr<std::vector<std::string>> ConstructOutputsWithDeterministicOrder(
    const TensorflowSpec& tensorflow_spec,
    const FederatedComputeIORouter& io_router) {
  std::vector<std::string> output_names;
  // The order of output tensor names should match the order in TensorflowSpec.
  for (const auto& output_tensor_spec : tensorflow_spec.output_tensor_specs()) {
    std::string tensor_name = output_tensor_spec.name();
    if (!io_router.aggregations().contains(tensor_name) ||
        !io_router.aggregations().at(tensor_name).has_secure_aggregation()) {
      return absl::InvalidArgumentError(
          "Output tensor is missing in AggregationConfig, or has unsupported "
          "aggregation type.");
    }
    output_names.push_back(tensor_name);
  }

  return output_names;
}

struct PlanResultAndCheckpointFile {
  explicit PlanResultAndCheckpointFile(engine::PlanResult plan_result)
      : plan_result(std::move(plan_result)) {}
  engine::PlanResult plan_result;
  std::string checkpoint_file;

  PlanResultAndCheckpointFile(PlanResultAndCheckpointFile&&) = default;
  PlanResultAndCheckpointFile& operator=(PlanResultAndCheckpointFile&&) =
      default;

  // Disallow copy and assign.
  PlanResultAndCheckpointFile(const PlanResultAndCheckpointFile&) = delete;
  PlanResultAndCheckpointFile& operator=(const PlanResultAndCheckpointFile&) =
      delete;
};

PlanResultAndCheckpointFile RunPlanWithExampleQuerySpec(
    std::vector<engine::ExampleIteratorFactory*> example_iterator_factories,
    OpStatsLogger* opstats_logger, const Flags* flags,
    const ClientOnlyPlan& client_plan,
    const std::string& checkpoint_output_filename) {
  if (!client_plan.phase().has_example_query_spec()) {
    return PlanResultAndCheckpointFile(engine::PlanResult(
        engine::PlanOutcome::kInvalidArgument,
        absl::InvalidArgumentError("Plan must include ExampleQuerySpec")));
  }
  if (!flags->enable_example_query_plan_engine()) {
    // Example query plan received while the flag is off.
    return PlanResultAndCheckpointFile(engine::PlanResult(
        engine::PlanOutcome::kInvalidArgument,
        absl::InvalidArgumentError(
            "Example query plan received while the flag is off")));
  }
  if (!client_plan.phase().has_federated_example_query()) {
    return PlanResultAndCheckpointFile(engine::PlanResult(
        engine::PlanOutcome::kInvalidArgument,
        absl::InvalidArgumentError("Invalid ExampleQuerySpec-based plan")));
  }
  for (const auto& example_query :
       client_plan.phase().example_query_spec().example_queries()) {
    for (auto const& [vector_name, spec] :
         example_query.output_vector_specs()) {
      const auto& aggregations =
          client_plan.phase().federated_example_query().aggregations();
      if ((aggregations.find(vector_name) == aggregations.end()) ||
          !aggregations.at(vector_name).has_tf_v1_checkpoint_aggregation()) {
        return PlanResultAndCheckpointFile(engine::PlanResult(
            engine::PlanOutcome::kInvalidArgument,
            absl::InvalidArgumentError("Output vector is missing in "
                                       "AggregationConfig, or has unsupported "
                                       "aggregation type.")));
      }
    }
  }

  engine::ExampleQueryPlanEngine plan_engine(example_iterator_factories,
                                             opstats_logger);
  engine::PlanResult plan_result = plan_engine.RunPlan(
      client_plan.phase().example_query_spec(), checkpoint_output_filename);
  PlanResultAndCheckpointFile result(std::move(plan_result));
  result.checkpoint_file = checkpoint_output_filename;
  return result;
}

PlanResultAndCheckpointFile RunPlanWithTensorflowSpec(
    std::vector<engine::ExampleIteratorFactory*> example_iterator_factories,
    std::function<bool()> should_abort, LogManager* log_manager,
    OpStatsLogger* opstats_logger, const Flags* flags,
    const ClientOnlyPlan& client_plan,
    const std::string& checkpoint_input_filename,
    const std::string& checkpoint_output_filename,
    const fcp::client::InterruptibleRunner::TimingConfig& timing_config) {
  if (!client_plan.phase().has_tensorflow_spec()) {
    return PlanResultAndCheckpointFile(engine::PlanResult(
        engine::PlanOutcome::kInvalidArgument,
        absl::InvalidArgumentError("Plan must include TensorflowSpec.")));
  }
  if (!client_plan.phase().has_federated_compute()) {
    return PlanResultAndCheckpointFile(engine::PlanResult(
        engine::PlanOutcome::kInvalidArgument,
        absl::InvalidArgumentError("Invalid TensorflowSpec-based plan")));
  }

  // Get the output tensor names.
  absl::StatusOr<std::vector<std::string>> output_names;
  output_names = ConstructOutputsWithDeterministicOrder(
      client_plan.phase().tensorflow_spec(),
      client_plan.phase().federated_compute());
  if (!output_names.ok()) {
    return PlanResultAndCheckpointFile(engine::PlanResult(
        engine::PlanOutcome::kInvalidArgument, output_names.status()));
  }

  // Run plan and get a set of output tensors back.
  if (flags->use_tflite_training() && !client_plan.tflite_graph().empty()) {
    std::unique_ptr<TfLiteInputs> tflite_inputs =
        ConstructTFLiteInputsForTensorflowSpecPlan(
            client_plan.phase().federated_compute(), checkpoint_input_filename,
            checkpoint_output_filename);
    engine::TfLitePlanEngine plan_engine(example_iterator_factories,
                                         should_abort, log_manager,
                                         opstats_logger, flags, &timing_config);
    engine::PlanResult plan_result = plan_engine.RunPlan(
        client_plan.phase().tensorflow_spec(), client_plan.tflite_graph(),
        std::move(tflite_inputs), *output_names);
    PlanResultAndCheckpointFile result(std::move(plan_result));
    result.checkpoint_file = checkpoint_output_filename;

    return result;
  }

  return PlanResultAndCheckpointFile(
      engine::PlanResult(engine::PlanOutcome::kTensorflowError,
                         absl::InternalError("No plan engine enabled")));
}
}  // namespace

absl::StatusOr<FLRunnerResult> RunFederatedComputation(
    SimpleTaskEnvironment* env_deps, LogManager* log_manager,
    const Flags* flags,
    const google::internal::federated::plan::ClientOnlyPlan& client_plan,
    const std::string& checkpoint_input_filename,
    const std::string& checkpoint_output_filename,
    const std::string& session_name, const std::string& population_name,
    const std::string& task_name,
    const fcp::client::InterruptibleRunner::TimingConfig& timing_config) {
  SelectorContext federated_selector_context;
  federated_selector_context.mutable_computation_properties()->set_session_name(
      session_name);
  FederatedComputation federated_computation;
  federated_computation.set_population_name(population_name);
  *federated_selector_context.mutable_computation_properties()
       ->mutable_federated() = federated_computation;
  federated_selector_context.mutable_computation_properties()
      ->mutable_federated()
      ->set_task_name(task_name);
  if (client_plan.phase().has_example_query_spec()) {
    federated_selector_context.mutable_computation_properties()
        ->set_example_iterator_output_format(
            ::fcp::client::QueryTimeComputationProperties::
                EXAMPLE_QUERY_RESULT);
  } else {
    const auto& federated_compute_io_router =
        client_plan.phase().federated_compute();
    const bool has_simpleagg_tensors =
        !federated_compute_io_router.output_filepath_tensor_name().empty();
    bool all_aggregations_are_secagg = true;
    for (const auto& aggregation : federated_compute_io_router.aggregations()) {
      all_aggregations_are_secagg &=
          aggregation.second.protocol_config_case() ==
          AggregationConfig::kSecureAggregation;
    }
    if (!has_simpleagg_tensors && all_aggregations_are_secagg) {
      federated_selector_context.mutable_computation_properties()
          ->mutable_federated()
          ->mutable_secure_aggregation()
          ->set_minimum_clients_in_server_visible_aggregate(100);
    } else {
      // Has an output checkpoint, so some tensors must be simply aggregated.
      *(federated_selector_context.mutable_computation_properties()
            ->mutable_federated()
            ->mutable_simple_aggregation()) = SimpleAggregation();
    }
  }

  auto opstats_logger =
      engine::CreateOpStatsLogger(env_deps->GetBaseDir(), flags, log_manager,
                                  session_name, population_name);

  // Check if the device conditions allow for checking in with the server
  // and running a federated computation. If not, bail early with the
  // transient error retry window.
  std::function<bool()> should_abort = [env_deps, &timing_config]() {
    return env_deps->ShouldAbort(absl::Now(), timing_config.polling_period);
  };

  // Regular plans can use example iterators from the SimpleTaskEnvironment,
  // those reading the OpStats DB, or those serving Federated Select slices.
  std::unique_ptr<engine::ExampleIteratorFactory> env_example_iterator_factory =
      CreateSimpleTaskEnvironmentIteratorFactory(env_deps,
                                                 federated_selector_context);
  std::vector<engine::ExampleIteratorFactory*> example_iterator_factories{
      env_example_iterator_factory.get()};
  PlanResultAndCheckpointFile plan_result_and_checkpoint_file =
      client_plan.phase().has_example_query_spec()
          ? RunPlanWithExampleQuerySpec(example_iterator_factories,
                                        opstats_logger.get(), flags,
                                        client_plan, checkpoint_output_filename)
          : RunPlanWithTensorflowSpec(example_iterator_factories, should_abort,
                                      log_manager, opstats_logger.get(), flags,
                                      client_plan, checkpoint_input_filename,
                                      checkpoint_output_filename,
                                      timing_config);
  auto outcome = plan_result_and_checkpoint_file.plan_result.outcome;
  FLRunnerResult fl_runner_result;

  if (outcome == engine::PlanOutcome::kSuccess) {
    fl_runner_result.set_contribution_result(FLRunnerResult::SUCCESS);
  } else {
    switch (outcome) {
      case engine::PlanOutcome::kInvalidArgument:
        fl_runner_result.set_error_status(FLRunnerResult::INVALID_ARGUMENT);
        break;
      case engine::PlanOutcome::kTensorflowError:
        fl_runner_result.set_error_status(FLRunnerResult::TENSORFLOW_ERROR);
        break;
      case engine::PlanOutcome::kExampleIteratorError:
        fl_runner_result.set_error_status(
            FLRunnerResult::EXAMPLE_ITERATOR_ERROR);
        break;
      default:
        break;
    }
    fl_runner_result.set_contribution_result(FLRunnerResult::FAIL);
    std::string error_message = std::string{
        plan_result_and_checkpoint_file.plan_result.original_status.message()};
    fl_runner_result.set_error_message(error_message);
  }

  FLRunnerResult::ExampleStats example_stats;
  example_stats.set_example_count(
      plan_result_and_checkpoint_file.plan_result.example_stats.example_count);
  example_stats.set_example_size_bytes(
      plan_result_and_checkpoint_file.plan_result.example_stats
          .example_size_bytes);

  *fl_runner_result.mutable_example_stats() = example_stats;

  return fl_runner_result;
}

}  // namespace client
}  // namespace fcp