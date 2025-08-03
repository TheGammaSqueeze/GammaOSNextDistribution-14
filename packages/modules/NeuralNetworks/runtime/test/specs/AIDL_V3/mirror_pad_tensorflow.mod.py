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

# These examples are taken from the TensorFlow specification:
#
#   https://www.tensorflow.org/api_docs/cc/class/tensorflow/ops/mirror-pad

def test(name, input_dims, input_values, paddings, mode, output_dims, output_values):
    t = Input("t", ("TENSOR_FLOAT32", input_dims))
    paddings = Parameter("paddings", ("TENSOR_INT32", [len(input_dims), 2]), paddings)
    output = Output("output", ("TENSOR_FLOAT32", output_dims))

    model = Model().Operation("MIRROR_PAD", t, paddings, mode).To(output)

    quant8_asymm_type = ("TENSOR_QUANT8_ASYMM", 0.5, 4)
    quant8_asymm = DataTypeConverter(name="quant8_asymm").Identify({
        t: quant8_asymm_type,
        output: quant8_asymm_type,
    })
    quant8_asymm_signed_type = ("TENSOR_QUANT8_ASYMM_SIGNED", 0.25, -9)
    quant8_asymm_signed = DataTypeConverter(name="quant8_asymm_signed").Identify({
        t: quant8_asymm_signed_type,
        output: quant8_asymm_signed_type,
    })
    Example({
        t: input_values,
        output: output_values,
        }, model=model, name=name).AddVariations("float16", quant8_asymm, quant8_asymm_signed, "int32")

test("summary",
     [2, 3], [1, 2, 3,               # input_dims, input_values
              4, 5, 6],
     [1, 1,                          # paddings
      2, 2],
     1,                              # mode = SYMMETRIC
     [4, 7], [2, 1, 1, 2, 3, 3, 2,   # output_dims, output_values
              2, 1, 1, 2, 3, 3, 2,
              5, 4, 4, 5, 6, 6, 5,
              5, 4, 4, 5, 6, 6, 5])

test("mode_reflect",
     [3], [1, 2, 3],        # input_dims, input_values
     [0, 2],                # paddings
     0,                     # mode = REFLECT
     [5], [1, 2, 3, 2, 1])  # output_dims, output_values

test("mode_symmetric",
     [3], [1, 2, 3],        # input_dims, input_values
     [0, 2],                # paddings
     1,                     # mode = SYMMETRIC
     [5], [1, 2, 3, 3, 2])  # output_dims, output_values
