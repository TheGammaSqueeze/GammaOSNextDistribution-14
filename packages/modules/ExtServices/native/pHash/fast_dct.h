/*
 * Copyright 2021 The Android Open Source Project
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

#pragma once

#include <memory>
#include <vector>
#include "array2d.h"

namespace android {

template <typename Torig, typename Tdct>
class FastDCT {
public:
    explicit FastDCT(int block_size);

    void ForwardTransform1D(const std::vector<Torig> &v_in, std::vector<Tdct> *v_out) const;
    void ReverseTransform1D(const std::vector<Tdct> &v_in, std::vector<Torig> *v_out) const;
    void ForwardTransform2D(const Array2D<Torig> &m_in, Array2D<Tdct> *m_out) const;
    void ReverseTransform2D(const Array2D<Tdct> &m_in, Array2D<Torig> *m_out) const;

    int block_size() const { return block_size_; }
    double intermediate_scale_factor() const { return intermediate_scale_factor_; }

    void set_intermediate_scale_factor(double val) { intermediate_scale_factor_ = val; }

private:
    // Dimensionality of input/output space for 1D transform.
    const int block_size_;

    // DCT coefficients will be scaled by this factor during the forward
    // transform, then divided by this factor during the reverse transform.
    double intermediate_scale_factor_;

    // Scaling factors for making transform orthonormal.
    const double orth_scale_factor_;
    const double sqrt_orth_scale_factor_;

    // Square root of one-half gets used a lot so define it as a constant.
    const double sqrt_one_half_;

    // Array for passing data into and out of external C ddct2d routine.
    std::unique_ptr<double *[]> ddct2d_in_out_ptr_array_;

    // Above pointer array will point into this contiguous block.
    std::unique_ptr<double[]> contiguous_double_array_;

    // Table of cosine and sine values used by external C ddct routine.
    std::unique_ptr<double[]> cos_sin_table_;

    // Array used for passing data into and out of ddct.
    std::unique_ptr<double[]> ddct_in_out_;

    // Scratch workspace for use by ddct2d.
    std::unique_ptr<double[]> t_workspace_;

    // Array used to compute the bit-reversal patterns in ddct.
    std::unique_ptr<int[]> bit_reversal_workspace_;
};
} // namespace android
