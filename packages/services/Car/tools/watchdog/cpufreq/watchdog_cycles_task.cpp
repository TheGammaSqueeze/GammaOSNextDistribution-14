/**
 * Copyright (c) 2023, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Original source: /system/extras/cpu_loads/simd.cpp by Tim Murray

#include <arpa/inet.h>
#include <cutils/sockets.h>
#include <hardware/gralloc.h>

#include <fcntl.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

#include <algorithm>
#include <fstream>
#include <iostream>
#include <numeric>
#include <string>
#include <tuple>
#include <vector>

#define EIGEN_RUNTIME_NO_MALLOC

#include <Eigen/Dense>

/*
 * Multiplies matrix B and C and adds the result to matrix A for m iterations.
 */
int main(int, char**) {
    int N = 4096;
    Eigen::MatrixXd a(N, N);
    Eigen::MatrixXd b(N, N);
    Eigen::MatrixXd c(N, N);

    for (int i = 0; i < N; i++) {
        for (int j = 0; j < N; j++) {
            a(i, j) = 1 + i * j;
            b(i, j) = 2 + i * j;
            c(i, j) = 3 + i * j;
        }
    }

    int m = 5;
    std::cout << "starting matrix multiplication (N: " << N << ", iters: " << m << ")" << std::endl;
    for (int i = 0; i < m; ++i) {
        a.noalias() += (b * c);
        b(1, 5) += 5.0;
        c(5, 1) -= 5.0;
    }

    return 0;
}
