/*
 * Copyright 2023 The Android Open Source Project
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

#include <cmath>
#include <memory>
#include <vector>

#include "TextureAsset.h"

union Vector3 {
    struct {
        float x, y, z;
    };
    float idx[3];
    Vector3 operator*(float value) { return Vector3{{x * value, y * value, z * value}}; }
    Vector3 operator/(float value) { return Vector3{{x / value, y / value, z / value}}; }
    Vector3 operator+(Vector3 const &other) {
        return Vector3{{x + other.x, y + other.y, z + other.z}};
    }
    Vector3 operator-(Vector3 const &other) {
        return Vector3{{x - other.x, y - other.y, z - other.z}};
    }
};

union Vector2 {
    struct {
        float x, y;
    };
    struct {
        float u, v;
    };
    float idx[2];
};

struct Vertex {
    constexpr Vertex(const Vector3 &inPosition, const Vector2 &inUV)
          : position(inPosition), uv(inUV) {}

    Vector3 position;
    Vector2 uv;
};

typedef uint16_t Index;

class Model {
public:
    inline Model(std::vector<Vertex> vertices, std::vector<Index> indices,
                 std::shared_ptr<TextureAsset> spTexture)
          : currentVertices_(vertices),
            startVertices_(std::move(vertices)),
            indices_(std::move(indices)),
            spTexture_(std::move(spTexture)) {
        findCenter();
    }

    inline const Vertex *getVertexData() const { return currentVertices_.data(); }

    inline size_t getIndexCount() const { return indices_.size(); }

    inline const Index *getIndexData() const { return indices_.data(); }

    inline const TextureAsset &getTexture() const { return *spTexture_; }

    inline const Vector3 getCenter() { return center_; }

    void move(Vector3 offset) {
        for (int i = 0; i < startVertices_.size(); ++i) {
            startVertices_[i].position = startVertices_[i].position + offset;
            currentVertices_[i].position = currentVertices_[i].position + offset;
        }
        center_ = center_ + offset;
    }

    void setRotation(float angle) {
        float rad = angle + rotationOffset_;
        for (int i = 0; i < startVertices_.size(); ++i) {
            Vector3 normalized = startVertices_[i].position - center_;
            Vector3 out{{0, 0, 0}};
            out.x = normalized.x * cos(rad) - normalized.y * sin(rad);
            out.y = normalized.x * sin(rad) + normalized.y * cos(rad);
            currentVertices_[i].position = out + center_;
        }
    }

    void setRotationOffset(float angle) { rotationOffset_ = angle; }

private:
    void findCenter() {
        Vector3 center{{0, 0, 0}};
        for (auto &&vertex : startVertices_) {
            center = center + vertex.position;
        }
        center_ = center / static_cast<float>(startVertices_.size());
    }

    Vector3 center_;
    std::vector<Vertex> currentVertices_;
    std::vector<Vertex> startVertices_;
    std::vector<Index> indices_;
    std::shared_ptr<TextureAsset> spTexture_;
    float rotationOffset_;
};
