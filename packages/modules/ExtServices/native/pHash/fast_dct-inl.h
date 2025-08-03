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

#include <cmath> // for sqrt() and ceil()
#include <memory>
#include <vector>

#include "array2d.h"
#include "fast_dct.h"

#include "fft.h"   // external FFT routines
#include "fft2d.h" // external 2D FFT routines

namespace android {

template <typename Torig, typename Tdct>
FastDCT<Torig, Tdct>::FastDCT(int block_size)
      : block_size_(block_size),
        intermediate_scale_factor_(1.0),
        orth_scale_factor_(2.0 / static_cast<double>(block_size_)),
        sqrt_orth_scale_factor_(sqrt(orth_scale_factor_)),
        sqrt_one_half_(sqrt(0.5)),
        ddct2d_in_out_ptr_array_(new double *[block_size_]),
        contiguous_double_array_(new double[block_size_ * block_size_]),
        cos_sin_table_(new double[block_size * 3 / 2 + 1]),
        ddct_in_out_(new double[block_size_]),
        t_workspace_(new double[4 * block_size]),
        bit_reversal_workspace_(new int[2 + static_cast<int>(ceil(sqrt(block_size)))]) {
    // Check that block_size_ is a power of two
    int block_size_shifted = block_size_;
    int num_shifts = 0;
    while (block_size_shifted >>= 1) num_shifts++;

    // As per the comments above the definition of ddct and ddct2d in
    // the external fft2d library, setting bit_reversal_workspace_[0] to
    // zero will cause it and the cos_sin table to be initialized the
    // first time one of the low-level C routines is called.
    bit_reversal_workspace_[0] = 0;

    // The ddct routine requires that the elements of
    // ddct2d_in_out_ptr_array_ point to successive block_size_-sized
    // blocks in a contiguous array.
    ddct2d_in_out_ptr_array_[0] = contiguous_double_array_.get();
    for (int i = 1; i < block_size_; i++) {
        ddct2d_in_out_ptr_array_[i] = ddct2d_in_out_ptr_array_[i - 1] + block_size_;
    }
}

// Perform one-dimensional forward DCT.
template <typename Torig, typename Tdct>
void FastDCT<Torig, Tdct>::ForwardTransform1D(const std::vector<Torig> &v_in,
                                              std::vector<Tdct> *v_out) const {
    for (int i = 0; i < block_size_; i++) {
        ddct_in_out_[i] = static_cast<double>(v_in[i]);
    }

    ddct(block_size_, -1, ddct_in_out_.get(), bit_reversal_workspace_.get(), cos_sin_table_.get());

    for (int i = 0; i < block_size_; i++) {
        ddct_in_out_[i] *= sqrt_orth_scale_factor_ * intermediate_scale_factor_;
    }
    ddct_in_out_[0] *= sqrt_one_half_;
    for (int i = 0; i < block_size_; i++) {
        (*v_out)[i] = static_cast<Tdct>(ddct_in_out_[i] + 0.5) - static_cast<Tdct>(0.5);
    }
}

// Perform one-dimensional reverse (inverse) DCT.
template <typename Torig, typename Tdct>
void FastDCT<Torig, Tdct>::ReverseTransform1D(const std::vector<Tdct> &v_in,
                                              std::vector<Torig> *v_out) const {
    for (int i = 0; i < block_size_; i++) {
        ddct_in_out_[i] = static_cast<double>(v_in[i]);
    }
    ddct_in_out_[0] *= sqrt_one_half_;

    ddct(block_size_, 1, ddct_in_out_.get(), bit_reversal_workspace_.get(), cos_sin_table_.get());

    for (int i = 0; i < block_size_; i++) {
        ddct_in_out_[i] *= sqrt_orth_scale_factor_ / intermediate_scale_factor_;
    }
    for (int i = 0; i < block_size_; i++) {
        (*v_out)[i] = static_cast<Torig>(ddct_in_out_[i] + 0.5) - static_cast<Torig>(0.5);
    }
}

// Perform two-dimensional forward DCT.
template <typename Torig, typename Tdct>
void FastDCT<Torig, Tdct>::ForwardTransform2D(const Array2D<Torig> &m_in,
                                              Array2D<Tdct> *m_out) const {
    for (int row = 0; row < block_size_; row++) {
        for (int col = 0; col < block_size_; col++) {
            ddct2d_in_out_ptr_array_[row][col] = static_cast<double>(m_in(row, col));
        }
    }

    ddct2d(block_size_, block_size_, -1, ddct2d_in_out_ptr_array_.get(), t_workspace_.get(),
           bit_reversal_workspace_.get(), cos_sin_table_.get());

    double isf_squared = intermediate_scale_factor_ * intermediate_scale_factor_;
    for (int row = 0; row < block_size_; row++) {
        for (int col = 0; col < block_size_; col++) {
            ddct2d_in_out_ptr_array_[row][col] *= orth_scale_factor_ * isf_squared;
        }
    }
    for (int i = 0; i < block_size_; i++) {
        ddct2d_in_out_ptr_array_[i][0] *= sqrt_one_half_;
        ddct2d_in_out_ptr_array_[0][i] *= sqrt_one_half_;
    }

    for (int row = 0; row < block_size_; row++) {
        for (int col = 0; col < block_size_; col++) {
            (*m_out)(row, col) = static_cast<Tdct>(ddct2d_in_out_ptr_array_[row][col] + 0.5) -
                    static_cast<Tdct>(0.5);
        }
    }
}

// Perform two-dimensional reverse (inverse) DCT.
template <typename Torig, typename Tdct>
void FastDCT<Torig, Tdct>::ReverseTransform2D(const Array2D<Tdct> &m_in,
                                              Array2D<Torig> *m_out) const {
    for (int row = 0; row < block_size_; row++) {
        for (int col = 0; col < block_size_; col++) {
            ddct2d_in_out_ptr_array_[row][col] = static_cast<double>(m_in(row, col));
        }
    }

    for (int i = 0; i < block_size_; i++) {
        ddct2d_in_out_ptr_array_[i][0] *= sqrt_one_half_;
        ddct2d_in_out_ptr_array_[0][i] *= sqrt_one_half_;
    }

    ddct2d(block_size_, block_size_, 1, ddct2d_in_out_ptr_array_.get(), t_workspace_.get(),
           bit_reversal_workspace_.get(), cos_sin_table_.get());

    double inv_isf_squared = 1.0 / (intermediate_scale_factor_ * intermediate_scale_factor_);
    for (int row = 0; row < block_size_; row++) {
        for (int col = 0; col < block_size_; col++) {
            ddct2d_in_out_ptr_array_[row][col] *= orth_scale_factor_ * inv_isf_squared;
        }
    }

    for (int row = 0; row < block_size_; row++) {
        for (int col = 0; col < block_size_; col++) {
            (*m_out)(row, col) = static_cast<Torig>(ddct2d_in_out_ptr_array_[row][col] + 0.5) -
                    static_cast<Torig>(0.5);
        }
    }
}
} // namespace android
