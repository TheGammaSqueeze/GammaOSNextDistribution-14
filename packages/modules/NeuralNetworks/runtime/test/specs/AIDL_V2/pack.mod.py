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
def test(name, axis_value, input_tensors, output_tensor, inputs_data, output_data):
    model = Model().Operation("PACK", Int32Scalar("axis", axis_value), *input_tensors).To(output_tensor)
    quant8_asymm_type = ("TENSOR_QUANT8_ASYMM", 0.5, 4)
    quant8_asymm_dict = dict(zip([*input_tensors, output_tensor], [quant8_asymm_type] * (len(input_tensors) + 1)))
    quant8_asymm = DataTypeConverter(name="quant8_asymm").Identify(quant8_asymm_dict)
    quant8_asymm_signed_type = ("TENSOR_QUANT8_ASYMM_SIGNED", 0.25, -9)
    quant8_asymm_signed_dict = dict(zip([*input_tensors, output_tensor], [quant8_asymm_signed_type] * (len(input_tensors) + 1)))
    quant8_asymm_signed = DataTypeConverter(name="quant8_asymm_signed").Identify(quant8_asymm_signed_dict)
    Example((dict(zip(input_tensors, inputs_data)), {output_tensor: output_data}), model=model, name=name).AddVariations("float16", quant8_asymm, quant8_asymm_signed, "int32")

test(
    name="FLOAT32_unary_axis0",
    axis_value=0,
    input_tensors=[Input("in0", ("TENSOR_FLOAT32", [2]))],
    output_tensor=Output("out", ("TENSOR_FLOAT32", [1,2])),
    inputs_data=[[3, 4]],
    output_data=[3, 4],
)

test(
    name="FLOAT32_unary_axis1",
    axis_value=1,
    input_tensors=[Input("in0", ("TENSOR_FLOAT32", [2]))],
    output_tensor=Output("out", ("TENSOR_FLOAT32", [2,1])),
    inputs_data=[[3, 4]],
    output_data=[3, 4],
)

test(
    name="FLOAT32_binary_axis0",
    axis_value=0,
    input_tensors=[Input("in0", ("TENSOR_FLOAT32", [3,4])),
                   Input("in1", ("TENSOR_FLOAT32", [3,4]))],
    output_tensor=Output("out", ("TENSOR_FLOAT32", [2,3,4])),
    inputs_data=[[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11],
                 [12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23]],
    output_data=[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11,
                 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23],

)

test(
    name="FLOAT32_binary_axis1",
    axis_value=1,
    input_tensors=[Input("in0", ("TENSOR_FLOAT32", [3,4])),
                   Input("in1", ("TENSOR_FLOAT32", [3,4]))],
    output_tensor=Output("out", ("TENSOR_FLOAT32", [3,2,4])),
    inputs_data=[[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11],
                 [12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23]],
    output_data=[0, 1, 2, 3,
                 12, 13, 14, 15,
                 4, 5, 6, 7,
                 16, 17, 18, 19,
                 8, 9, 10, 11,
                 20, 21, 22, 23],
)

test(
    name="FLOAT32_binary_axis2",
    axis_value=2,
    input_tensors=[Input("in0", ("TENSOR_FLOAT32", [3,4])),
                   Input("in1", ("TENSOR_FLOAT32", [3,4]))],
    output_tensor=Output("out", ("TENSOR_FLOAT32", [3,4,2])),
    inputs_data=[[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11],
                 [12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23]],
    output_data=[0, 12,
                 1, 13,
                 2, 14,
                 3, 15,
                 4, 16,
                 5, 17,
                 6, 18,
                 7, 19,
                 8, 20,
                 9, 21,
                 10, 22,
                 11, 23],
)
