/*
 * Copyright 2021 Google LLC
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
// fcp:google3-internal-file
#include "fcp/client/engine/tflite_plan_engine.h"

#include <algorithm>
#include <functional>
#include <memory>
#include <string>
#include <utility>

#include "absl/status/statusor.h"
#include "fcp/client/client_runner.h"
#include "fcp/client/diag_codes.pb.h"
#include "fcp/client/opstats/opstats_example_store.h"
#include "fcp/client/test_helpers.h"
#include "fcp/testing/testing.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "tensorflow/core/framework/tensor.pb.h"
#include "tensorflow/core/framework/tensor_shape.pb.h"
#include "tensorflow/core/protobuf/struct.pb.h"

namespace fcp {
namespace client {
namespace engine {
namespace {
using ::fcp::client::opstats::OpStatsSequence;
using ::google::internal::federated::plan::ClientOnlyPlan;
using ::google::internal::federated::plan::Dataset;
using ::google::internal::federated::plan::FederatedComputeEligibilityIORouter;
using ::google::internal::federated::plan::FederatedComputeIORouter;
using ::google::internal::federated::plan::LocalComputeIORouter;
using ::testing::Gt;
using ::testing::InSequence;
using ::testing::Invoke;
using ::testing::IsEmpty;
using ::testing::NiceMock;
using ::testing::Return;
using ::testing::StrictMock;

// We turn formatting off to prevent line breaks, which ensures that these paths
// are more easily code searchable.
// clang-format off
constexpr absl::string_view kArtifactPrefix =
    "intelligence/brella/testing/tasks/mnist/simpleagg_mnist_training_tflite_task_artifacts"; // NOLINT
constexpr absl::string_view kEligibilityPlanArtifactPrefix =
    "intelligence/brella/testing/tasks/eligibility_eval/eligibility_eval_tflite_task_artifacts"; // NOLINT
constexpr absl::string_view kSecaggArtifactPrefix =
    "intelligence/brella/testing/tasks/secagg_only_tflite_task_artifacts";
constexpr absl::string_view kLcArtifactPrefix =
    "intelligence/brella/testing/local_computation/mnist_tflite_personalization_artifacts"; // NOLINT
constexpr absl::string_view kLcInitialCheckpoint =
    "intelligence/brella/testing/local_computation/initial.ckpt";
constexpr absl::string_view kConstantInputsArtifactPrefix =
    "intelligence/brella/testing/tasks/mnist/simpleagg_constant_tflite_inputs_task_artifacts"; // NOLINT
// clang-format on

const char* const kCollectionUri = "app:/test_collection";
const char* const kEligibilityEvalCollectionUri =
    "app:/test_eligibility_eval_collection";
const char* const kLcTrainCollectionUri = "app:/p13n_train_collection";
const char* const kLcTestCollectionUri = "app:/p13n_test_collection";

// Parameterized with whether per_phase_logs should be used.
class TfLitePlanEngineTest : public testing::Test {
 protected:
  void SetUp() override {
    EXPECT_CALL(mock_opstats_logger_, IsOpStatsEnabled())
        .WillRepeatedly(Return(true));
    EXPECT_CALL(mock_opstats_logger_, GetOpStatsDb())
        .WillRepeatedly(Return(&mock_opstats_db_));
    EXPECT_CALL(mock_opstats_db_, Read())
        .WillRepeatedly(Return(OpStatsSequence::default_instance()));
    EXPECT_CALL(mock_flags_, ensure_dynamic_tensors_are_released())
        .WillRepeatedly(Return(true));
    EXPECT_CALL(mock_flags_, large_tensor_threshold_for_dynamic_allocation())
        .WillRepeatedly(Return(1000));
    EXPECT_CALL(mock_flags_, num_threads_for_tflite())
        .WillRepeatedly(Return(4));
    EXPECT_CALL(mock_flags_, disable_tflite_delegate_clustering())
        .WillRepeatedly(Return(false));
    EXPECT_CALL(mock_flags_, support_constant_tf_inputs())
        .WillRepeatedly(Return(false));
  }

  void InitializeFlTask(absl::string_view prefix) {
    LoadArtifacts();

    example_iterator_factory_ =
        std::make_unique<FunctionalExampleIteratorFactory>(
            [&dataset = dataset_](
                const google::internal::federated::plan::ExampleSelector&
                    selector) {
              return std::make_unique<::fcp::client::SimpleExampleIterator>(
                  dataset);
            });

    // Compute dataset stats.
    for (const Dataset::ClientDataset& client_dataset :
         dataset_.client_data()) {
      num_examples_ += client_dataset.example_size();
      for (const std::string& example : client_dataset.example()) {
        example_bytes_ += example.size();
      }
    }
    // The single session FL plan specifies both input and output filepaths in
    // its FederatedComputeIORouter.
    FederatedComputeIORouter io_router =
        client_only_plan_.phase().federated_compute();
    if (!io_router.input_filepath_tensor_name().empty()) {
      (*inputs_)[io_router.input_filepath_tensor_name()] = checkpoint_input_fd_;
    }
    checkpoint_output_filename_ =
        files_impl_.CreateTempFile("output", ".ckp").value();
    ASSERT_EQ(std::filesystem::file_size(checkpoint_output_filename_), 0);
    int fd = open(checkpoint_output_filename_.c_str(), O_WRONLY);
    ASSERT_NE(-1, fd);
    checkpoint_output_fd_ = absl::StrCat("fd:///", fd);
    if (!io_router.output_filepath_tensor_name().empty()) {
      (*inputs_)[io_router.output_filepath_tensor_name()] =
          checkpoint_output_fd_;
    }

    for (const auto& tensor_spec :
         client_only_plan_.phase().tensorflow_spec().output_tensor_specs()) {
      output_names_.push_back(tensor_spec.name());
    }
  }

  void LoadArtifacts() {
    absl::StatusOr<::fcp::client::ComputationArtifacts> artifacts =
        ::fcp::client::LoadFlArtifacts();
    EXPECT_TRUE(artifacts.ok());
    client_only_plan_ = std::move(artifacts->plan);
    dataset_ = std::move(artifacts->dataset);
    checkpoint_input_filename_ = artifacts->checkpoint_filepath;
    int fd = open(checkpoint_input_filename_.c_str(), O_RDONLY);
    ASSERT_NE(-1, fd);
    checkpoint_input_fd_ = absl::StrCat("fd:///", fd);
  }

  void ComputeDatasetStats(const std::string& collection_uri) {
    for (const Dataset::ClientDataset& client_dataset :
         dataset_.client_data()) {
      for (const Dataset::ClientDataset::SelectedExample& selected_example :
           client_dataset.selected_example()) {
        if (selected_example.selector().collection_uri() != collection_uri) {
          continue;
        }
        num_examples_ += selected_example.example_size();
        for (const auto& example : selected_example.example()) {
          example_bytes_ += example.size();
        }
      }
    }
  }

  fcp::client::FilesImpl files_impl_;
  StrictMock<MockLogManager> mock_log_manager_;
  StrictMock<MockOpStatsLogger> mock_opstats_logger_;
  StrictMock<MockOpStatsDb> mock_opstats_db_;
  StrictMock<MockFlags> mock_flags_;
  std::unique_ptr<ExampleIteratorFactory> example_iterator_factory_;
  // Never abort, by default.
  std::function<bool()> should_abort_ = []() { return false; };

  ClientOnlyPlan client_only_plan_;
  Dataset dataset_;
  std::string checkpoint_input_filename_;
  std::string checkpoint_output_filename_;
  std::string checkpoint_input_fd_;
  std::string checkpoint_output_fd_;

  int num_examples_ = 0;
  int example_bytes_ = 0;
  std::unique_ptr<absl::flat_hash_map<std::string, std::string>> inputs_ =
      std::make_unique<absl::flat_hash_map<std::string, std::string>>();
  std::vector<std::string> output_names_;

  fcp::client::InterruptibleRunner::TimingConfig timing_config_ = {
      // Use 10 ms to make the polling faster, otherwise the Abort test might
      // fail because the plan finishes before interruption.
      .polling_period = absl::Milliseconds(10),
      .graceful_shutdown_period = absl::Milliseconds(1000),
      .extended_shutdown_period = absl::Milliseconds(2000),
  };
};

TEST_F(TfLitePlanEngineTest, SimpleAggPlanSucceeds) {
  InitializeFlTask(kArtifactPrefix);

  EXPECT_CALL(mock_log_manager_,
              LogDiag(ProdDiagCode::BACKGROUND_TRAINING_TFLITE_ENGINE_USED));

  EXPECT_CALL(
      mock_opstats_logger_,
      UpdateDatasetStats(kCollectionUri, num_examples_, example_bytes_));

  TfLitePlanEngine plan_engine({example_iterator_factory_.get()}, should_abort_,
                               &mock_log_manager_, &mock_opstats_logger_,
                               &mock_flags_, &timing_config_);
  engine::PlanResult result = plan_engine.RunPlan(
      client_only_plan_.phase().tensorflow_spec(),
      client_only_plan_.tflite_graph(), std::move(inputs_), output_names_);

  EXPECT_THAT(result.outcome, PlanOutcome::kSuccess);
  EXPECT_THAT(result.output_tensors.size(), 0);
  EXPECT_THAT(result.output_names.size(), 0);
  EXPECT_EQ(result.example_stats.example_count, num_examples_);
  EXPECT_EQ(result.example_stats.example_size_bytes, example_bytes_);
  EXPECT_GT(std::filesystem::file_size(checkpoint_output_filename_), 0);
}
}  // namespace
}  // namespace engine
}  // namespace client
}  // namespace fcp
