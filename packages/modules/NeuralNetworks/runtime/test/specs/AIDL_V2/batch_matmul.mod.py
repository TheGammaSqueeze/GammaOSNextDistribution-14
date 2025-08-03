#
# Copyright (C) 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
def test(name, input0, input1, adj0, adj1, output, input0_data, input1_data,
    output_data):
  model = Model().Operation("BATCH_MATMUL", input0, input1, adj0, adj1).To(
      output)
  quant8_signed = DataTypeConverter().Identify({
      input0: ("TENSOR_QUANT8_ASYMM_SIGNED", 0.25, 0),
      input1: ("TENSOR_QUANT8_ASYMM_SIGNED", 0.50, -64),
      output: ("TENSOR_QUANT8_ASYMM_SIGNED", 1.00, -128),
  })
  Example({
      input0: input0_data,
      input1: input1_data,
      output: output_data,
  }, model=model,
      name=name).AddVariations("float16", "int32", quant8_signed)


test(
    name="Simple",
    input0=Input("op1", "TENSOR_FLOAT32", "{1, 2, 3}"),
    input1=Input("op2", "TENSOR_FLOAT32", "{1, 3, 4}"),
    adj0=BoolScalar("adj0", False),
    adj1=BoolScalar("adj1", False),
    output=Output("op3", "TENSOR_FLOAT32", "{1, 2, 4}"),
    input0_data=[1, 2, 3, 4, 5, 6],
    input1_data=[7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18],
    output_data=[74., 80., 86., 92., 173., 188., 203., 218.],
)

test(
    name="RHSAdjoint",
    input0=Input("op1", "TENSOR_FLOAT32", "{1, 2, 3}"),
    input1=Input("op2", "TENSOR_FLOAT32", "{1, 4, 3}"),
    adj0=BoolScalar("adj0", False),
    adj1=BoolScalar("adj1", True),
    output=Output("op3", "TENSOR_FLOAT32", "{1, 2, 4}"),
    input0_data=[1, 2, 3, 4, 5, 6],
    input1_data=[7, 11, 15, 8, 12, 16, 9, 13, 17, 10, 14, 18],
    output_data=[74., 80., 86., 92., 173., 188., 203., 218.],
)

test(
    name="LHSAdjoint",
    input0=Input("op1", "TENSOR_FLOAT32", "{1, 3, 2}"),
    input1=Input("op2", "TENSOR_FLOAT32", "{1, 3, 4}"),
    adj0=BoolScalar("adj0", True),
    adj1=BoolScalar("adj1", False),
    output=Output("op3", "TENSOR_FLOAT32", "{1, 2, 4}"),
    input0_data=[1, 4, 2, 5, 3, 6],
    input1_data=[7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18],
    output_data=[74., 80., 86., 92., 173., 188., 203., 218.],
)

test(
    name="TwoBatchSize",
    input0=Input("op1", "TENSOR_FLOAT32", "{2, 2, 3}"),
    input1=Input("op2", "TENSOR_FLOAT32", "{2, 3, 4}"),
    adj0=BoolScalar("adj0", False),
    adj1=BoolScalar("adj1", False),
    output=Output("op3", "TENSOR_FLOAT32", "{2, 2, 4}"),
    input0_data=[1, 2, 3, 4, 5, 6, 1, 2, 3, 4, 5, 6],
    input1_data=[7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18,
                 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18],
    output_data=[74., 80., 86., 92., 173., 188., 203., 218.,
                 74., 80., 86., 92., 173., 188., 203., 218.],
)
