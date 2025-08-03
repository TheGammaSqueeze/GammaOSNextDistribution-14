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

# value = (8_bit_encoding - zeroPoint) * scale

# If square roots are 0.5, 1, 2, 4
# Then reciprocal square roots (outputs) are 2, 1, 0.5, 0.25
# And squares (inputs) are 0.25, 1, 4, 16

for inScale, inOffset, inToken in [(0.25, 0, "25h_0"),
                                   (0.125, 10, "125t_10")]:
    for outScale, outOffset, outToken  in [(0.25, 0, "25h_0"),
                                            (0.01, 75, "1h_75")]:

        input0_values = []
        output0_values = []
        for in0, out0 in [(0.25, 2),
                          (1, 1),
                          (4, 0.5),
                          (16, 0.25)]:
            input0_value = in0 / inScale + inOffset
            output0_value = out0 / outScale + outOffset
            if 0 <= input0_value < 128 and 0 <= output0_value < 128:
                # We use [0, 128) as the range because the same values are used for
                # both TENSOR_QUANT8_ASYMM and TENSOR_QUANT8_ASYMM_SIGNED  testing
                input0_values.append(input0_value)
                output0_values.append(output0_value)

        input0 = Input("input0", "TENSOR_QUANT8_ASYMM", "{%d}, %f, %d" % (len(input0_values), inScale, inOffset))
        output0 = Output("output0", "TENSOR_QUANT8_ASYMM", "{%d}, %f, %d" % (len(output0_values), outScale, outOffset))
        model = Model().Operation("RSQRT", input0).To(output0)

        example_name = "%s_%s" % (inToken, outToken)
        Example({
            input0: input0_values,
            output0: output0_values,
        }, name=example_name)

# We rely on QuantizationCouplingTest to replicate this test case for TENSOR_QUANT8_ASYMM_SIGNED.
