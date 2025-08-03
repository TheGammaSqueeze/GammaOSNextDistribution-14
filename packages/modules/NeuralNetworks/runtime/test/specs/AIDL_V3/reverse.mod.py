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

from itertools import chain

def test(name, axis_value, input_tensor, output_tensor, input_data, output_data):
    model = Model().Operation("REVERSE", input_tensor, [axis_value]).To(output_tensor)
    quant8_asymm_type = ("TENSOR_QUANT8_ASYMM", 0.5, 4)
    quant8_asymm = DataTypeConverter(name="quant8_asymm").Identify({
        input_tensor: quant8_asymm_type,
        output_tensor: quant8_asymm_type,
        })
    quant8_asymm_signed_type = ("TENSOR_QUANT8_ASYMM_SIGNED", 0.25, -9)
    quant8_asymm_signed = DataTypeConverter(name="quant8_asymm_signed").Identify({
        input_tensor: quant8_asymm_signed_type,
        output_tensor: quant8_asymm_signed_type,
        })
    Example({
        input_tensor: input_data,
        output_tensor: output_data,
        }, model=model, name=name).AddVariations("float16", quant8_asymm, quant8_asymm_signed, "int32")

def rrange(hi, lo):
    return range(hi, lo, -1)

test(
    name="dim1",
    axis_value=0,
    input_tensor=Input("in", ("TENSOR_FLOAT32", [3])),
    output_tensor=Output("out", ("TENSOR_FLOAT32", [3])),
    input_data=[6, 7, 8],
    output_data=[8, 7, 6],
    )

test(
    name="dim3_axis0",
    axis_value=0,
    input_tensor=Input("in", ("TENSOR_FLOAT32", [2,3,4])),
    output_tensor=Output("out", ("TENSOR_FLOAT32", [2,3,4])),
    input_data = list(range(24)),
    output_data = list(chain(range(12,24), range(0,12))),
    )

test(
    name="dim3_axis1",
    axis_value=1,
    input_tensor=Input("in", ("TENSOR_FLOAT32", [2,3,4])),
    output_tensor=Output("out", ("TENSOR_FLOAT32", [2,3,4])),
    input_data = list(range(24)),
    output_data = list(chain(range(8,12), range(4,8), range(0,4),
                             range(20,24), range(16,20), range(12,16))),
    )

test(
    name="dim3_axis2",
    axis_value=2,
    input_tensor=Input("in", ("TENSOR_FLOAT32", [2,3,4])),
    output_tensor=Output("out", ("TENSOR_FLOAT32", [2,3,4])),
    input_data = list(range(24)),
    output_data = list(chain(rrange(3,-1), rrange(7,3), rrange(11,7),
                             rrange(15,11), rrange(19,15), rrange(23,19)))
    )
