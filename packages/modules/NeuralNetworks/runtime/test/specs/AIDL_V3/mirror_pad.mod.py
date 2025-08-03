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

import numpy
import random

def random_value(r):
    # Compute a pseudorandom value in the range [0, 1) and scale it to
    # make it more interesting for int32 variation and make it simple
    # to specify quantized variation.
    return 30 * r.random()

# Generate test case for specified modes (0=reflect, 1=symmetric).  If
# clampReflectPadding is True, then while generating test for reflect,
# accommodate padding that equals the maximum for symmetric and hence
# exceeds by one the maximum for reflect, by clamping the padding to
# the maximum for reflect.
def test(name, seed, input_dims, orig_paddings, modes=[0, 1], clampReflectPadding=False):
    for mode in modes:
        r = random.Random()
        r.seed(seed)

        paddings = orig_paddings.copy()
        if mode==0 and clampReflectPadding:
            for i in range(0, len(input_dims)):
                for leftright in [0, 1]:
                    padding_index = i * 2 + leftright
                    if paddings[padding_index] == input_dims[i]:
                        paddings[padding_index] = input_dims[i] - 1

        input_tensor = Input("in", ("TENSOR_FLOAT32", input_dims))
        padding_tensor = Parameter("padding", ("TENSOR_INT32", [len(input_dims), 2]), paddings)
        output_dims = [sum(x) for x in zip(input_dims, paddings[0::2], paddings[1::2])]
        output_tensor = Output("out", ("TENSOR_FLOAT32", output_dims))
        model = Model().Operation("MIRROR_PAD", input_tensor, padding_tensor, mode).To(output_tensor)

        input_data = [random_value(r) for x in range(0, numpy.prod(input_dims))]

        numpy_input_data = numpy.reshape(input_data, input_dims)
        numpy_paddings = list(zip(paddings[0::2], paddings[1::2]))
        numpy_mode = "reflect" if mode==0 else "symmetric"
        numpy_output_data = numpy.pad(numpy_input_data, numpy_paddings, mode=numpy_mode)

        output_data = numpy_output_data.flatten().tolist()

        quant8_asymm_type = ("TENSOR_QUANT8_ASYMM", 0.125, 4)
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
            }, model=model, name=name + "_" + numpy_mode).AddVariations("float16", quant8_asymm, quant8_asymm_signed, "int32")


# Test simple
test("one", 0, [2, 3], [0, 1, 1, 2])

# Test high rank
test("two", 1, [1, 2, 1, 2, 1, 2, 1, 2, 1, 2], [0, 0,  # dim=1
                                                0, 1,  # dim=2
                                                0, 1,  # dim=1
                                                1, 0,  # dim=2
                                                1, 0,  # dim=1
                                                1, 2,  # dim=2
                                                1, 1,  # dim=1
                                                2, 1,  # dim=2
                                                0, 1,  # dim=1
                                                0, 2], # dim=2
     clampReflectPadding=True)

# Test maxed-out padding values
test("three", 2, [3, 4, 5, 6], [3, 3,  # dim=3
                                4, 2,  # dim=4
                                1, 5,  # dim=5
                                1, 1], # dim=6
     modes=[1]) # symmetric
