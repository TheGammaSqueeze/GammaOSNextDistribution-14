%% template file for generating OperationTypes.h.
%% see README.md.
/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_OPERATION_TYPES_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_OPERATION_TYPES_H

namespace android::nn {

%insert Operation_1.0_Comment
enum class OperationType {
%insert Operation_1.0

%insert Operation_1.1

%insert Operation_1.2

%insert Operation_1.3

%insert Operation_fl6

%insert Operation_fl7

    /**
     * DEPRECATED. Since HAL version 1.2, extensions are the preferred
     * alternative to OEM operation and data types.
     *
     * This operation is OEM specific. It should only be used for OEM
     * applications.
     */
    OEM_OPERATION = 10000,

#ifdef NN_EXPERIMENTAL_FEATURE
    /**
     * Expands a representation of a sparse tensor to a dense tensor.
     *
     * To encode a conceptual n-dimensional dense tensor with dims [D0, ..., Dn-1], potentially with
     * a k-dimensional block (0 <= k <= n) with dims [Dn, ..., Dn+k-1], the format specifies:
     * * 1: In what order to traverse these dimensions. For example, to store a 2-D matrix in row
     *      major order, the traversal order would be [D0, D1], whereas to store it in column major
     *      order, the traversal order would be [D1, D0]. If the 2-D matrix has a 2-D inner block,
     *      the traversal order could be [D0, D1, D2, D3].
     * * 2: How each block dimension in [Dn, ..., Dn+k-1] maps to the original tensor dimension in
     *      [D0, ..., Dn-1].
     * * 3: In the traversal order defined above, the format (dense vs. sparse) and index metadata
     *      for each dimension. For a dense dimension, this is just the size of that dimension. For
     *      a sparse dimension, it's the same as the compressed index defined in the Compressed
     *      Sparse Row (CSR) format.
     *      (http://scipy-lectures.org/advanced/scipy_sparse/csr_matrix.html)
     *
     * The number of inputs to this operation is determined by the number of dimensions (including
     * the block dimensions) of the sparsity parameters. Currently, the only formats supported are
     * DENSE and SPARSE_CSR, but additional sparsity formats may be added in later versions of this
     * operation.
     *
     * Supported tensor {@link OperandType}:
     * * {@link OperandType::TENSOR_FLOAT16}
     * * {@link OperandType::TENSOR_FLOAT32}
     * * {@link OperandType::TENSOR_QUANT8_SYMM}
     * * {@link OperandType::TENSOR_QUANT8_ASYMM}
     * * {@link OperandType::TENSOR_QUANT8_ASYMM_SIGNED}
     * * {@link OperandType::TENSOR_BOOL8}
     * * {@link OperandType::TENSOR_INT32}
     * * {@link OperandType::TENSOR_QUANT16_SYMM}
     * * {@link OperandType::TENSOR_QUANT16_ASYMM}
     *
     *
     * Reference:
     * * This implementation is a modification of the TACO format.
     *   http://tensor-compiler.org/kjolstad-oopsla17-tensor-compiler.pdf
     *
     * Inputs:
     * * 0: A 1-D tensor representing the compressed sparse tensor data of a conceptual
     *      n-dimensional tensor.
     * * 1: A 1-D {@link OperandType::TENSOR_INT32} tensor defining the traversal order for reading
     *      the non-zero blocks. For an n-dimensional tensor with dimensions [D0, D1, …, Dn-1]: if
     *      block sparse with a k-dimensional block (0 < k <= n), the traversal order has n+k
     *      elements. The first n elements are still a permutation of [D0, …, Dn-1]. The last k
     *      elements are a permutation of [Dn, …, Dn+k-1], defining how to traverse a block
     *      internally. If not block sparse, the traversal order is just a permutation of [D0, …,
     *      Dn-1].
     * * 2: An optional 1-D {@link OperandType::TENSOR_INT32} tensor defining the block map. For a
     *      block sparse n-dimensional tensor with a k-dimensional block (0 < k <= n), it stores how
     *      a block dimension [Dn, …, Dn+k-1] maps to the original tensor dimension in [D0, …,
     *      Dn-1]. For i, j where 0 <= i < j < k, blockMap[i] < blockMap[j]. If not block sparse,
     *      this is null.
     * * 3: A 1-D {@link OperandType::TENSOR_INT32} tensor with n+k elements defining the format of
     *      each dimension in the traversal order (listed above). The format is either DENSE (where
     *      DENSE = 0) or SPARSE_CSR (where SPARSE_CSR = 1). DENSE means that each coordinate in
     *      this dimension is stored implicitly. SPARSE_CSR means only the coordinates with non-zero
     *      elements are stored.
     * * 4: A 1-D {@link OperandType::TENSOR_INT32} tensor with n+k elements defining the size of
     *      each dimension or block. The product of all these sizes totals the number of elements in
     *      the dense tensor. First n elements represent the sparse tensor’s shape, and the last k
     *      elements represent the block’s shape.
     * * 5 ~ (5 + 2 * (n+k)): An optional pair of {@link OperandType::TENSOR_INT32} tensors which
     *      together specify the sparse indices along that dimension. The first pair of arguments
     *      corresponds to D0, the second to D1, and so on until Dn+k-1. If the dimension is DENSE,
     *      both arguments in the pair are null and the dimension is implicitly specified by the
     *      corresponding element in Input 4. If the dimension is SPARSE_CSR, then we use the pair
     *      of array segments and array indices to encode that dimension:
     * * * +0: An optional list of n+k input 1-D {@link OperandType::TENSOR_INT32} tensors, defining
     *         the array segments. The array segments represent how to segment the indices array,
     *         each segment corresponds to one element in the previous dimension. Array segments are
     *         interspersed with array indices (listed below), so this input could be input (5, 5 +
     *         2, …, 5 + 2*(n+k-1)). For i, j where 0 =< i < j, arraySegments[i] <=
     *         arraySegments[j]. Used if the dimension is SPARSE_CSR, omitted if the dimension is
     *         DENSE.
     * * * +1: An optional list of n+k input 1-D {@link OperandType::TENSOR_INT32} tensors, defining
     *         the array indices. The array indices represent the index of the non-zero elements
     *         within this dimension (as those in the CSR matrix format, where the first array is
     *         row pointers and the second array is column indices). Array indices are interspersed
     *         with array segments (listed above), so this input could be input (6, 6 + 2, …, 6 +
     *         2*(n+k-1)). Used if the dimension is SPARSE_CSR, omitted if the dimension is DENSE.
     *
     * Outputs:
     * * 0: An n-D dense tensor. The output tensor has the same {@link OperandType} as input 0.
     */
    DENSIFY = 20000,
#endif  // NN_EXPERIMENTAL_FEATURE
};

}  // namespace android::nn

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_OPERATION_TYPES_H
