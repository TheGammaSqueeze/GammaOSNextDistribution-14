// Copyright (C) 2023 Google LLC
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
#include "icing/query/advanced_query_parser/function.h"

#include <vector>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "gtest/gtest.h"
#include "icing/query/advanced_query_parser/param.h"
#include "icing/query/advanced_query_parser/pending-value.h"
#include "icing/testing/common-matchers.h"

namespace icing {
namespace lib {

namespace {

using ::testing::IsTrue;

struct TrivialEval {
  libtextclassifier3::StatusOr<PendingValue> operator()(
      const std::vector<PendingValue>&) const {
    return PendingValue();
  }
};

TEST(FunctionTest, NoParamCreateSucceeds) {
  ICING_ASSERT_OK_AND_ASSIGN(
      Function function, Function::Create(/*return_type=*/DataType::kString,
                                          "foo", /*params=*/{}, TrivialEval()));
  // foo()
  std::vector<PendingValue> empty_args;
  ICING_ASSERT_OK_AND_ASSIGN(PendingValue val,
                             function.Eval(std::move(empty_args)));
  EXPECT_THAT(val.is_placeholder(), IsTrue());
}

TEST(FunctionTest, NoParamNonEmptyArgsFails) {
  ICING_ASSERT_OK_AND_ASSIGN(
      Function function, Function::Create(/*return_type=*/DataType::kString,
                                          "foo", /*params=*/{}, TrivialEval()));

  // foo()
  std::vector<PendingValue> args;
  args.push_back(PendingValue());
  EXPECT_THAT(function.Eval(std::move(args)),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(FunctionTest, ParamNotWrongTypeFails) {
  ICING_ASSERT_OK_AND_ASSIGN(
      Function function,
      Function::Create(/*return_type=*/DataType::kString, "foo",
                       /*params=*/{Param(DataType::kString)}, TrivialEval()));
  std::string_view query = "foo(bar)";
  std::vector<PendingValue> args;
  args.push_back(PendingValue::CreateTextPendingValue(
      QueryTerm{"bar", query.substr(4, 3), /*is_prefix_val=*/false}));
  EXPECT_THAT(function.Eval(std::move(args)),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(FunctionTest, ParamRequiredArgSucceeds) {
  ICING_ASSERT_OK_AND_ASSIGN(
      Function function,
      Function::Create(/*return_type=*/DataType::kString, "foo",
                       /*params=*/{Param(DataType::kString)}, TrivialEval()));

  std::string_view query = R"(foo("bar"))";
  std::vector<PendingValue> args;
  args.push_back(PendingValue::CreateStringPendingValue(
      QueryTerm{"bar", query.substr(5, 3), /*is_prefix_val=*/false}));
  ICING_ASSERT_OK_AND_ASSIGN(PendingValue val, function.Eval(std::move(args)));
  EXPECT_THAT(val.is_placeholder(), IsTrue());
}

TEST(FunctionTest, ParamRequiredArgNotPresentFails) {
  ICING_ASSERT_OK_AND_ASSIGN(
      Function function,
      Function::Create(/*return_type=*/DataType::kString, "foo",
                       /*params=*/{Param(DataType::kString)}, TrivialEval()));

  // foo()
  std::vector<PendingValue> empty_args;
  EXPECT_THAT(function.Eval(std::move(empty_args)),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(FunctionTest, ParamOptionalArgNotPresentSucceeds) {
  ICING_ASSERT_OK_AND_ASSIGN(
      Function function,
      Function::Create(
          /*return_type=*/DataType::kString, "foo",
          /*params=*/{Param(DataType::kString, Cardinality::kOptional)},
          TrivialEval()));

  // foo()
  std::vector<PendingValue> empty_args;
  ICING_ASSERT_OK_AND_ASSIGN(PendingValue val,
                             function.Eval(std::move(empty_args)));
  EXPECT_THAT(val.is_placeholder(), IsTrue());
}

TEST(FunctionTest, ParamVariableArgNotPresentSucceeds) {
  ICING_ASSERT_OK_AND_ASSIGN(
      Function function,
      Function::Create(
          /*return_type=*/DataType::kString, "foo",
          /*params=*/{Param(DataType::kString, Cardinality::kVariable)},
          TrivialEval()));

  // foo()
  std::vector<PendingValue> empty_args;
  ICING_ASSERT_OK_AND_ASSIGN(PendingValue val,
                             function.Eval(std::move(empty_args)));
  EXPECT_THAT(val.is_placeholder(), IsTrue());
}

TEST(FunctionTest, MultipleArgsTrailingOptionalSucceeds) {
  ICING_ASSERT_OK_AND_ASSIGN(
      Function function, Function::Create(
                             /*return_type=*/DataType::kString, "foo",
                             /*params=*/
                             {Param(DataType::kString, Cardinality::kRequired),
                              Param(DataType::kString, Cardinality::kOptional)},
                             TrivialEval()));

  std::string_view query = R"(foo("bar"))";
  std::vector<PendingValue> args;
  args.push_back(PendingValue::CreateStringPendingValue(
      QueryTerm{"bar", query.substr(5, 3), /*is_prefix_val=*/false}));
  ICING_ASSERT_OK_AND_ASSIGN(PendingValue val, function.Eval(std::move(args)));
  EXPECT_THAT(val.is_placeholder(), IsTrue());

  query = R"(foo("bar", "baz"))";
  args = std::vector<PendingValue>();
  args.push_back(PendingValue::CreateStringPendingValue(
      QueryTerm{"bar", query.substr(5, 3), /*is_prefix_val=*/false}));
  args.push_back(PendingValue::CreateStringPendingValue(
      QueryTerm{"baz", query.substr(12, 3), /*is_prefix_val=*/false}));
  ICING_ASSERT_OK_AND_ASSIGN(val, function.Eval(std::move(args)));
  EXPECT_THAT(val.is_placeholder(), IsTrue());
}

TEST(FunctionTest, MultipleArgsTrailingVariableSucceeds) {
  ICING_ASSERT_OK_AND_ASSIGN(
      Function function, Function::Create(
                             /*return_type=*/DataType::kString, "foo",
                             /*params=*/
                             {Param(DataType::kString, Cardinality::kRequired),
                              Param(DataType::kString, Cardinality::kVariable)},
                             TrivialEval()));

  std::string_view query = R"(foo("bar"))";
  std::vector<PendingValue> args;
  args.push_back(PendingValue::CreateStringPendingValue(
      QueryTerm{"bar", query.substr(5, 3), /*is_prefix_val=*/false}));
  ICING_ASSERT_OK_AND_ASSIGN(PendingValue val, function.Eval(std::move(args)));
  EXPECT_THAT(val.is_placeholder(), IsTrue());

  query = R"(foo("bar", "baz"))";
  args = std::vector<PendingValue>();
  args.push_back(PendingValue::CreateStringPendingValue(
      QueryTerm{"bar", query.substr(5, 3), /*is_prefix_val=*/false}));
  args.push_back(PendingValue::CreateStringPendingValue(
      QueryTerm{"baz", query.substr(12, 3), /*is_prefix_val=*/false}));
  ICING_ASSERT_OK_AND_ASSIGN(val, function.Eval(std::move(args)));
  EXPECT_THAT(val.is_placeholder(), IsTrue());

  query = R"(foo("bar", "baz", "bat"))";
  args = std::vector<PendingValue>();
  args.push_back(PendingValue::CreateStringPendingValue(
      QueryTerm{"bar", query.substr(5, 3), /*is_prefix_val=*/false}));
  args.push_back(PendingValue::CreateStringPendingValue(
      QueryTerm{"baz", query.substr(12, 3), /*is_prefix_val=*/false}));
  args.push_back(PendingValue::CreateStringPendingValue(
      QueryTerm{"bat", query.substr(19, 3), /*is_prefix_val=*/false}));
  ICING_ASSERT_OK_AND_ASSIGN(val, function.Eval(std::move(args)));
  EXPECT_THAT(val.is_placeholder(), IsTrue());
}

TEST(FunctionTest, MultipleArgsOptionalBeforeRequiredFails) {
  EXPECT_THAT(Function::Create(
                  /*return_type=*/DataType::kString, "foo",
                  /*params=*/
                  {Param(DataType::kString, Cardinality::kOptional),
                   Param(DataType::kString, Cardinality::kRequired)},
                  TrivialEval()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(FunctionTest, MultipleArgsOptionalBeforeOptionalSucceeds) {
  ICING_ASSERT_OK_AND_ASSIGN(
      Function function, Function::Create(
                             /*return_type=*/DataType::kString, "foo",
                             /*params=*/
                             {Param(DataType::kString, Cardinality::kOptional),
                              Param(DataType::kText, Cardinality::kOptional)},
                             TrivialEval()));

  // foo()
  std::vector<PendingValue> args;
  ICING_ASSERT_OK_AND_ASSIGN(PendingValue val, function.Eval(std::move(args)));
  EXPECT_THAT(val.is_placeholder(), IsTrue());

  std::string_view query = R"(foo("bar"))";
  args = std::vector<PendingValue>();
  args.push_back(PendingValue::CreateStringPendingValue(
      QueryTerm{"bar", query.substr(5, 3), /*is_prefix_val=*/false}));
  ICING_ASSERT_OK_AND_ASSIGN(val, function.Eval(std::move(args)));
  EXPECT_THAT(val.is_placeholder(), IsTrue());

  query = R"(foo("bar", baz))";
  args = std::vector<PendingValue>();
  args.push_back(PendingValue::CreateStringPendingValue(
      QueryTerm{"bar", query.substr(5, 3), /*is_prefix_val=*/false}));
  args.push_back(PendingValue::CreateTextPendingValue(
      QueryTerm{"baz", query.substr(11, 3), /*is_prefix_val=*/false}));
  ICING_ASSERT_OK_AND_ASSIGN(val, function.Eval(std::move(args)));
  EXPECT_THAT(val.is_placeholder(), IsTrue());

  query = R"(foo(baz))";
  args = std::vector<PendingValue>();
  args.push_back(PendingValue::CreateTextPendingValue(
      QueryTerm{"baz", query.substr(4, 3), /*is_prefix_val=*/false}));
  EXPECT_THAT(function.Eval(std::move(args)),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(FunctionTest, MultipleArgsOptionalBeforeVariableSucceeds) {
  ICING_ASSERT_OK_AND_ASSIGN(
      Function function, Function::Create(
                             /*return_type=*/DataType::kString, "foo",
                             /*params=*/
                             {Param(DataType::kString, Cardinality::kOptional),
                              Param(DataType::kText, Cardinality::kVariable)},
                             TrivialEval()));

  // foo()
  std::vector<PendingValue> args;
  ICING_ASSERT_OK_AND_ASSIGN(PendingValue val, function.Eval(std::move(args)));
  EXPECT_THAT(val.is_placeholder(), IsTrue());

  std::string_view query = R"(foo("bar"))";
  args = std::vector<PendingValue>();
  args.push_back(PendingValue::CreateStringPendingValue(
      QueryTerm{"bar", query.substr(5, 3), /*is_prefix_val=*/false}));
  ICING_ASSERT_OK_AND_ASSIGN(val, function.Eval(std::move(args)));
  EXPECT_THAT(val.is_placeholder(), IsTrue());

  query = R"(foo("bar", baz))";
  args = std::vector<PendingValue>();
  args.push_back(PendingValue::CreateStringPendingValue(
      QueryTerm{"bar", query.substr(5, 3), /*is_prefix_val=*/false}));
  args.push_back(PendingValue::CreateTextPendingValue(
      QueryTerm{"baz", query.substr(11, 3), /*is_prefix_val=*/false}));
  ICING_ASSERT_OK_AND_ASSIGN(val, function.Eval(std::move(args)));
  EXPECT_THAT(val.is_placeholder(), IsTrue());

  query = R"(foo("bar", baz, bat))";
  args = std::vector<PendingValue>();
  args.push_back(PendingValue::CreateStringPendingValue(
      QueryTerm{"bar", query.substr(5, 3), /*is_prefix_val=*/false}));
  args.push_back(PendingValue::CreateTextPendingValue(
      QueryTerm{"baz", query.substr(11, 3), /*is_prefix_val=*/false}));
  args.push_back(PendingValue::CreateTextPendingValue(
      QueryTerm{"bat", query.substr(16, 3), /*is_prefix_val=*/false}));
  ICING_ASSERT_OK_AND_ASSIGN(val, function.Eval(std::move(args)));
  EXPECT_THAT(val.is_placeholder(), IsTrue());

  query = R"(foo(baz))";
  args = std::vector<PendingValue>();
  args.push_back(PendingValue::CreateTextPendingValue(
      QueryTerm{"baz", query.substr(4, 3), /*is_prefix_val=*/false}));
  EXPECT_THAT(function.Eval(std::move(args)),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  query = R"(foo(baz, bat))";
  args = std::vector<PendingValue>();
  args.push_back(PendingValue::CreateTextPendingValue(
      QueryTerm{"baz", query.substr(4, 3), /*is_prefix_val=*/false}));
  args.push_back(PendingValue::CreateTextPendingValue(
      QueryTerm{"bat", query.substr(9, 3), /*is_prefix_val=*/false}));
  EXPECT_THAT(function.Eval(std::move(args)),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(FunctionTest, MultipleArgsVariableBeforeRequiredFails) {
  EXPECT_THAT(Function::Create(
                  /*return_type=*/DataType::kString, "foo",
                  /*params=*/
                  {Param(DataType::kString, Cardinality::kVariable),
                   Param(DataType::kString, Cardinality::kRequired)},
                  TrivialEval()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(FunctionTest, MultipleArgsVariableBeforeOptionalFails) {
  EXPECT_THAT(Function::Create(
                  /*return_type=*/DataType::kString, "foo",
                  /*params=*/
                  {Param(DataType::kString, Cardinality::kVariable),
                   Param(DataType::kString, Cardinality::kOptional)},
                  TrivialEval()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(FunctionTest, MultipleArgsVariableBeforeVariableFails) {
  EXPECT_THAT(Function::Create(
                  /*return_type=*/DataType::kString, "foo",
                  /*params=*/
                  {Param(DataType::kString, Cardinality::kVariable),
                   Param(DataType::kString, Cardinality::kVariable)},
                  TrivialEval()),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

}  // namespace

}  // namespace lib
}  // namespace icing