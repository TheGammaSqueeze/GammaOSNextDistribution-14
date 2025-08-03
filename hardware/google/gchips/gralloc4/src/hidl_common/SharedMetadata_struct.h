/*
 * Copyright (C) 2020 Arm Limited.
 *
 * Copyright 2016 The Android Open Source Project
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

#include <optional>
#include <vector>
#include <VendorVideoAPI.h>
#include "gralloctypes/Gralloc4.h"
#include <pixel-gralloc/metadata.h>

namespace arm
{
namespace mapper
{
namespace common
{

using aidl::android::hardware::graphics::common::Rect;
using aidl::android::hardware::graphics::common::Smpte2086;
using aidl::android::hardware::graphics::common::Cta861_3;
using aidl::android::hardware::graphics::common::BlendMode;
using aidl::android::hardware::graphics::common::Dataspace;
using ::pixel::graphics::VideoGMV;

template <typename T>
struct aligned_optional
{
	enum class state : uint32_t
	{
		vacant,
		occupied,
	};

	state item_state { state::vacant };
	T item {};

	aligned_optional() = default;

	aligned_optional(T initial_value)
	    : item_state(state::occupied)
	    , item(initial_value)
	{
	}

	aligned_optional(std::optional<T> std_optional)
	    : item_state(std_optional ? state::occupied : state::vacant)
	{
		if (std_optional)
		{
			item = *std_optional;
		}
	}

	std::optional<T> to_std_optional() const
	{
		switch (item_state)
		{
		case state::vacant: return std::nullopt;
		case state::occupied: return std::make_optional(item);
		}
	}
};

template <typename T, size_t N>
struct aligned_inline_vector
{
	uint32_t size;
	T contents[N];

	constexpr uint32_t capacity() const
	{
		return N;
	}

	const T *data() const
	{
		return &contents[0];
	}

	T *data()
	{
		return &contents[0];
	}
};

struct shared_metadata
{
	ExynosVideoMeta video_private_data;
	VideoGMV video_gmv_data;

	aligned_optional<BlendMode> blend_mode {};
	aligned_optional<Rect> crop {};
	aligned_optional<Cta861_3> cta861_3 {};
	aligned_optional<Dataspace> dataspace {};
	aligned_optional<Smpte2086> smpte2086 {};
	aligned_inline_vector<uint8_t, 2048> smpte2094_40 {};
	aligned_inline_vector<char, 256> name {};

	shared_metadata() = default;

	shared_metadata(std::string_view in_name)
	{
		name.size = std::min(name.capacity(), static_cast<uint32_t>(in_name.size()));
		std::memcpy(name.data(), in_name.data(), name.size);
	}

	std::string_view get_name() const
	{
		return name.size > 0
		    ? std::string_view(name.data(), name.size)
		    : std::string_view();
	}
};

/* TODO: convert alignment assert taking video metadata into account */
#if 0
static_assert(offsetof(shared_metadata, blend_mode) == 0, "bad alignment");
static_assert(sizeof(shared_metadata::blend_mode) == 8, "bad size");

static_assert(offsetof(shared_metadata, crop) == 8, "bad alignment");
static_assert(sizeof(shared_metadata::crop) == 20, "bad size");

static_assert(offsetof(shared_metadata, cta861_3) == 28, "bad alignment");
static_assert(sizeof(shared_metadata::cta861_3) == 12, "bad size");

static_assert(offsetof(shared_metadata, dataspace) == 40, "bad alignment");
static_assert(sizeof(shared_metadata::dataspace) == 8, "bad size");

static_assert(offsetof(shared_metadata, smpte2086) == 48, "bad alignment");
static_assert(sizeof(shared_metadata::smpte2086) == 44, "bad size");

static_assert(offsetof(shared_metadata, smpte2094_40) == 92, "bad alignment");
static_assert(sizeof(shared_metadata::smpte2094_40) == 2052, "bad size");

static_assert(offsetof(shared_metadata, name) == 2144, "bad alignment");
static_assert(sizeof(shared_metadata::name) == 260, "bad size");

static_assert(alignof(shared_metadata) == 4, "bad alignment");
static_assert(sizeof(shared_metadata) == 2404, "bad size");
#endif

} // namespace common
} // namespace mapper
} // namespace arm

