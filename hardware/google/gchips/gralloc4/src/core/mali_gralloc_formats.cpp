/*
 * Copyright (C) 2016-2020 ARM Limited. All rights reserved.
 *
 * Copyright (C) 2008 The Android Open Source Project
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

#include <string.h>
#include <dlfcn.h>
#include <inttypes.h>
#include <log/log.h>
#include <assert.h>
#include <vector>
#include <cutils/properties.h>

#include "gralloc_priv.h"
#include "mali_gralloc_bufferallocation.h"
#include "mali_gralloc_usages.h"
#include "format_info.h"
#include "capabilities/gralloc_capabilities.h"
#include "exynos_format.h"

/* Producer/consumer definitions.
 * CPU: Software access
 * GPU: Graphics processor
 * DPU: Display processor
 * VPU: Video processor
 * CAM: Camera ISP
 * TPU: ML accelerator
 */
#define MALI_GRALLOC_PRODUCER_CPU		((uint16_t)1 << 0)
#define MALI_GRALLOC_PRODUCER_GPU		((uint16_t)1 << 1)
#define MALI_GRALLOC_PRODUCER_DPU		((uint16_t)1 << 2)
#define MALI_GRALLOC_PRODUCER_VPU		((uint16_t)1 << 4)
#define MALI_GRALLOC_PRODUCER_CAM		((uint16_t)1 << 5)
#define GOOGLE_GRALLOC_PRODUCER_BIG		((uint16_t)1 << 6)
#define GOOGLE_GRALLOC_PRODUCER_MFC		((uint16_t)1 << 7)
#define GOOGLE_GRALLOC_PRODUCER_VPUS_MASK	(MALI_GRALLOC_PRODUCER_VPU | GOOGLE_GRALLOC_PRODUCER_BIG | GOOGLE_GRALLOC_PRODUCER_MFC)
#define GOOGLE_GRALLOC_PRODUCER_TPU		((uint16_t)1 << 8)

#define MALI_GRALLOC_CONSUMER_CPU		((uint16_t)1 << 0)
#define MALI_GRALLOC_CONSUMER_GPU		((uint16_t)1 << 1)
#define MALI_GRALLOC_CONSUMER_DPU		((uint16_t)1 << 2)
#define MALI_GRALLOC_CONSUMER_VPU		((uint16_t)1 << 3)
#define GOOGLE_GRALLOC_CONSUMER_BIG		((uint16_t)1 << 4)
#define GOOGLE_GRALLOC_CONSUMER_MFC		((uint16_t)1 << 5)
#define GOOGLE_GRALLOC_CONSUMER_VPUS_MASK	(MALI_GRALLOC_CONSUMER_VPU | GOOGLE_GRALLOC_CONSUMER_BIG | GOOGLE_GRALLOC_CONSUMER_MFC)
#define GOOGLE_GRALLOC_CONSUMER_TPU		((uint16_t)1 << 6)


typedef struct
{
	uint32_t base_format;
	uint64_t format_ext;
	format_support_flags f_flags;
} fmt_props;

/*
 * Video encoder consumer can be signalled by a combination of usage flags
 * Besides VIDEO_ENCODER, clients can specify the specific IP block they belong
 * to, for fine-grained control over capabilities.
 *
 * @param usage  [in]    Buffer usage.
 *
 * @return The corresponding consumer flag, or 0 if the consumer is not a VPU.
 */
static uint16_t get_vpu_consumer(uint64_t usage)
{
	if (!(usage & hidl_common::BufferUsage::VIDEO_ENCODER))
		return 0;

	/* When both the BIG and MFC flags are present, the assumption is BIG is the
	   producer and MFC is the consumer. There is no use case as of now in which
	   MFC is the producer and BIG is the consumer. */
	if (usage & GRALLOC_USAGE_GOOGLE_IP_MFC)
		return GOOGLE_GRALLOC_CONSUMER_MFC;

	if (usage & GRALLOC_USAGE_GOOGLE_IP_BIG)
		return GOOGLE_GRALLOC_CONSUMER_BIG;

	// TODO(b/185896428): Support 64-bits usage version for GraphicBufferSource
	return GOOGLE_GRALLOC_CONSUMER_MFC;
}

/*
 * Determines all IP consumers included by the requested buffer usage.
 * Private usage flags are excluded from this process.
 *
 * @param usage   [in]    Buffer usage.
 *
 * @return flags word of all enabled consumers;
 *         0, if no consumers are enabled
 */
static uint16_t get_consumers(uint64_t usage)
{
	uint16_t consumers = 0;

	if (usage & GS101_GRALLOC_USAGE_TPU_INPUT)
	{
		consumers |= GOOGLE_GRALLOC_CONSUMER_TPU;
	}

	/* Exclude usages also not applicable to consumer derivation */
	usage &= ~GRALLOC_USAGE_PROTECTED;

	get_ip_capabilities();

	if ((usage & GRALLOC_USAGE_PUBLIC_MASK) == GRALLOC_USAGE_HW_COMPOSER)
	{
		consumers = MALI_GRALLOC_CONSUMER_DPU;
	}
	else
	{
		if (usage & GRALLOC_USAGE_SW_READ_MASK)
		{
			consumers |= MALI_GRALLOC_CONSUMER_CPU;
		}

		/* GRALLOC_USAGE_HW_FB describes a framebuffer which contains a
		 * pre-composited scene that is scanned-out to a display. This buffer
		 * can be consumed by even the most basic display processor which does
		 * not support multi-layer composition.
		 */
		if (usage & GRALLOC_USAGE_HW_FB)
		{
			consumers |= MALI_GRALLOC_CONSUMER_DPU;
		}

		consumers |= get_vpu_consumer(usage);

		/* GRALLOC_USAGE_HW_COMPOSER does not explicitly define whether the
		 * display processor is producer or consumer. When used in combination
		 * with GRALLOC_USAGE_HW_TEXTURE, it is assumed to be consumer since the
		 * GPU and DPU both act as compositors.
		 */
		if ((usage & (GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_HW_COMPOSER)) ==
		    (GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_HW_COMPOSER))
		{
			consumers |= MALI_GRALLOC_CONSUMER_DPU;
		}

		if (usage & (GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_GPU_DATA_BUFFER))
		{
			consumers |= MALI_GRALLOC_CONSUMER_GPU;
		}
	}

	return consumers;
}

/*
 * Video decoder producer can be signalled by a combination of usage flags
 * Besides VIDEO_DECODER, clients can specify the specific IP block they belong
 * to, for fine-grained control over capabilities.
 *
 * @param usage  [in]    Buffer usage.
 *
 * @return The corresponding producer flag, or 0 if the producer is not a VPU.
 */
static uint16_t get_vpu_producer(uint64_t usage)
{
	if (!(usage & hidl_common::BufferUsage::VIDEO_DECODER))
		return 0;

	/* When both the BIG and MFC flags are present, the assumption is BIG is the
	   producer and MFC is the consumer. There is no use case as of now in which
	   MFC is the producer and BIG is the consumer. */
	if (usage & GRALLOC_USAGE_GOOGLE_IP_BIG)
		return GOOGLE_GRALLOC_PRODUCER_BIG;

	if (usage & GRALLOC_USAGE_GOOGLE_IP_MFC)
		return GOOGLE_GRALLOC_PRODUCER_MFC;

	MALI_GRALLOC_LOGV("Video producer IP not specified, falling back to default VPU producer");
	return MALI_GRALLOC_PRODUCER_VPU;
}

/*
 * Determines all IP producers included by the requested buffer usage.
 * Private usage flags are excluded from this process.
 *
 * @param usage   [in]    Buffer usage.
 *
 * @return flags word of all enabled producers;
 *         0, if no producers are enabled
 */
static uint16_t get_producers(uint64_t usage)
{
	uint16_t producers = 0;

	if (usage & GS101_GRALLOC_USAGE_TPU_OUTPUT)
	{
		producers |= GOOGLE_GRALLOC_PRODUCER_TPU;
	}

	/* Exclude usages also not applicable to producer derivation */
	usage &= ~GRALLOC_USAGE_PROTECTED;

	get_ip_capabilities();

	if (usage & GRALLOC_USAGE_SW_WRITE_MASK)
	{
		producers |= MALI_GRALLOC_PRODUCER_CPU;
	}

	/* DPU is normally consumer however, when there is an alternative
	 * consumer (VPU) and no other producer (e.g. VPU), it acts as a producer.
	 */
	if ((usage & GRALLOC_USAGE_DECODER) != GRALLOC_USAGE_DECODER &&
	    (usage & (GRALLOC_USAGE_HW_COMPOSER | GRALLOC_USAGE_HW_VIDEO_ENCODER)) ==
	    (GRALLOC_USAGE_HW_COMPOSER | GRALLOC_USAGE_HW_VIDEO_ENCODER))
	{
		producers |= MALI_GRALLOC_PRODUCER_DPU;
	}

	if (usage & (GRALLOC_USAGE_HW_RENDER | GRALLOC_USAGE_GPU_DATA_BUFFER))
	{
		producers |= MALI_GRALLOC_PRODUCER_GPU;
	}

	if (usage & GRALLOC_USAGE_HW_CAMERA_WRITE)
	{
		producers |= MALI_GRALLOC_PRODUCER_CAM;
	}

	producers |= get_vpu_producer(usage);

	return producers;
}

static inline bool consumers_use_cpu_caps(uint16_t consumers)
{
	return consumers & (MALI_GRALLOC_CONSUMER_CPU | GOOGLE_GRALLOC_CONSUMER_TPU);
}

static inline bool producers_use_cpu_caps(uint16_t producers)
{
	return producers & (MALI_GRALLOC_PRODUCER_CPU | GOOGLE_GRALLOC_PRODUCER_TPU);
}

/*
 * Determines the intersection of all IP consumers capability sets. Since all
 * capabiltiies are positive, the intersection can be expressed via a logical
 * AND operation. Capabilities must be defined (OPTIONS_PRESENT) to indicate
 * that an IP is part of the media system (otherwise it will be ignored).
 * See definition of MALI_GRALLOC_FORMAT_CAPABILITY_* for more information.
 *
 * @param consumers   [in]    Buffer consumers.
 *
 * @return flags word of common capabilities shared by *all* consumers;
 *         0, if no capabilities are shared
 */
static uint64_t get_consumer_caps(const uint16_t consumers)
{
	uint64_t consumer_caps = ~0;

	get_ip_capabilities();

	/* Consumers can't write */
	consumer_caps &= ~MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_YUV_WRITE;

	if (consumers_use_cpu_caps(consumers))
	{
		consumer_caps &= cpu_runtime_caps.caps_mask;
	}

	if (consumers & MALI_GRALLOC_CONSUMER_GPU &&
	    gpu_runtime_caps.caps_mask & MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT)
	{
		consumer_caps &= gpu_runtime_caps.caps_mask;
	}

	if (consumers & MALI_GRALLOC_CONSUMER_DPU &&
	    dpu_runtime_caps.caps_mask & MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT)
	{
		consumer_caps &= dpu_runtime_caps.caps_mask;
	}

	if (consumers & MALI_GRALLOC_CONSUMER_VPU &&
	    vpu_runtime_caps.caps_mask & MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT)
	{
		consumer_caps &= vpu_runtime_caps.caps_mask;
	}

	if (consumers & GOOGLE_GRALLOC_CONSUMER_BIG)
	{
#ifdef SOC_ZUMA
		if (bw_runtime_caps.caps_mask & MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT)
			consumer_caps &= bw_runtime_caps.caps_mask;
#else
		if (bo_runtime_caps.caps_mask & MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT)
			consumer_caps &= bo_runtime_caps.caps_mask;
#endif
	}

	if (consumers & GOOGLE_GRALLOC_CONSUMER_MFC &&
	    mfc_runtime_caps.caps_mask & MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT)
	{
		consumer_caps &= mfc_runtime_caps.caps_mask;
	}

	/* TODO: if consumers is 0, set consumer_caps to 0 as well */

	return consumer_caps;
}


/*
 * Determines the intersection of all IP producers capability sets. Since all
 * capabiltiies are positive, the intersection can be expressed via a logical
 * AND operation. Capabilities must be defined (OPTIONS_PRESENT) to indicate
 * that an IP is part of the media system (otherwise it will be ignored).
 * See definition of MALI_GRALLOC_FORMAT_CAPABILITY_* for more information.
 *
 * @param producers   [in]    Buffer producers.
 *
 * @return flags word of common capabilities shared by *all* producers;
 *         0, if no capabilities are shared
 */
static uint64_t get_producer_caps(const uint16_t producers)
{
	uint64_t producer_caps = ~0;

	if (producers == 0)
	{
		/* When no producer is specified assume no capabilities. */
		return 0;
	}

	get_ip_capabilities();

	/* Producers can't read */
	producer_caps &= ~MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_YUV_READ;

	if (producers_use_cpu_caps(producers))
	{
		producer_caps &= cpu_runtime_caps.caps_mask;
	}

	if (producers & MALI_GRALLOC_PRODUCER_GPU &&
	    gpu_runtime_caps.caps_mask & MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT)
	{
		producer_caps &= gpu_runtime_caps.caps_mask;
	}

	if (producers & MALI_GRALLOC_PRODUCER_DPU &&
	    dpu_runtime_caps.caps_mask & MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT)
	{
		producer_caps &= dpu_runtime_caps.caps_mask;
	}

	if (producers & MALI_GRALLOC_PRODUCER_CAM &&
	    cam_runtime_caps.caps_mask & MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT)
	{
		producer_caps &= cam_runtime_caps.caps_mask;
	}

	if (producers & MALI_GRALLOC_PRODUCER_VPU &&
	    vpu_runtime_caps.caps_mask & MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT)
	{
		producer_caps &= vpu_runtime_caps.caps_mask;
	}

	if (producers & GOOGLE_GRALLOC_PRODUCER_BIG)
	{
#ifdef SOC_ZUMA
		if (bw_runtime_caps.caps_mask & MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT)
			producer_caps &= bw_runtime_caps.caps_mask;
#else
		if (bo_runtime_caps.caps_mask & MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT)
			producer_caps &= bo_runtime_caps.caps_mask;
#endif
	}

	if (producers & GOOGLE_GRALLOC_PRODUCER_MFC &&
	    mfc_runtime_caps.caps_mask & MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT)
	{
		producer_caps &= mfc_runtime_caps.caps_mask;
	}

	return producer_caps;
}


/*
 * Update buffer dimensions for producer/consumer constraints. This process is
 * not valid with CPU producer/consumer since the new resolution cannot be
 * communicated to generic clients through the public APIs. Adjustments are
 * likely to be related to AFBC.
 *
 * @param alloc_format   [in]    Format (inc. modifiers) to be allocated.
 * @param usage          [in]    Buffer usage.
 * @param width          [inout] Buffer width (in pixels).
 * @param height         [inout] Buffer height (in pixels).
 *
 * @return none.
 */
void mali_gralloc_adjust_dimensions(const uint64_t alloc_format,
                                    const uint64_t usage,
                                    uint64_t* const width,
                                    uint64_t* const height)
{
	/* Determine producers. */
	const uint16_t producers = get_producers(usage);

	/*
	 * Video producer requires additional height padding of AFBC buffers (whole
	 * rows of 16x16 superblocks). Cropping will be applied to internal
	 * dimensions to fit the public size.
	 */
	if ((producers & GOOGLE_GRALLOC_PRODUCER_VPUS_MASK) &&
		(alloc_format & MALI_GRALLOC_INTFMT_AFBC_BASIC))
	{
		const int32_t idx = get_format_index(alloc_format & MALI_GRALLOC_INTFMT_FMT_MASK);
		if (idx != -1)
		{
			/* 8-bit/10-bit YUV420 formats. */
			if (formats[idx].is_yuv && formats[idx].hsub == 2 && formats[idx].vsub == 2)
			{
				*height += (alloc_format & MALI_GRALLOC_INTFMT_AFBC_TILED_HEADERS) ? 16 : 32;
			}
		}
	}

	if (producers & MALI_GRALLOC_PRODUCER_GPU)
	{
		/* Pad all AFBC allocations to multiple of GPU tile size. */
		if (alloc_format & MALI_GRALLOC_INTFMT_AFBC_BASIC)
		{
			*width = GRALLOC_ALIGN(*width, 16);
			*height = GRALLOC_ALIGN(*height, 16);
		}
	}

	MALI_GRALLOC_LOGV("%s: alloc_format=(%s 0x%" PRIx64 ") usage=(%s 0x%" PRIx64
		") alloc_width=%" PRIu64 ", alloc_height=%" PRIu64 "",
		__FUNCTION__, format_name(alloc_format), alloc_format, describe_usage(usage).c_str(),
		usage, *width, *height);
}


/*
 * Obtain level of support for base format across all producers and consumers as
 * defined by IP support table. This support is defined for the most capable IP -
 * specific IP might have reduced support based on specific capabilities.
 *
 * @param producers      [in]    Producers (flags).
 * @param consumers      [in]    Consumers (flags).
 * @param format         [in]    Format entry in IP support table.
 *
 * @return format support flags.
 */
static format_support_flags ip_supports_base_format(const uint16_t producers,
                                                    const uint16_t consumers,
                                                    const format_ip_support_t * const format)
{
	format_support_flags support = ~0;

	/* Determine producer support for base format. */
	if (producers_use_cpu_caps(producers))
	{
		support &= format->cpu_wr;
	}
	if (producers & MALI_GRALLOC_PRODUCER_GPU)
	{
		support &= format->gpu_wr;
	}
	if (producers & MALI_GRALLOC_PRODUCER_DPU)
	{
		support &= format->dpu_wr;
	}
	if (producers & MALI_GRALLOC_PRODUCER_CAM)
	{
		support &= format->cam_wr;
	}
	if (producers & GOOGLE_GRALLOC_PRODUCER_VPUS_MASK)
	{
		support &= format->vpu_wr;
	}

	/* Determine consumer support for base format. */
	if (consumers_use_cpu_caps(consumers))
	{
		support &= format->cpu_rd;
	}
	if (consumers & MALI_GRALLOC_CONSUMER_GPU)
	{
		support &= format->gpu_rd;
	}
	if (consumers & MALI_GRALLOC_CONSUMER_DPU)
	{
		support &= format->dpu_rd;
	}
	if (consumers & GOOGLE_GRALLOC_CONSUMER_VPUS_MASK)
	{
		support &= format->vpu_rd;
	}

	return support;
}


/*
 * Determines whether a base format is subsampled YUV, where each
 * chroma channel has fewer samples than the luma channel. The
 * sub-sampling is always a power of 2.
 *
 * @param base_format   [in]    Base format (internal).
 *
 * @return 1, where format is subsampled YUV;
 *         0, otherwise
 */
bool is_subsampled_yuv(const uint32_t base_format)
{
	unsigned long i;

	for (i = 0; i < num_formats; i++)
	{
		if (formats[i].id == (base_format & MALI_GRALLOC_INTFMT_FMT_MASK))
		{
			if (formats[i].is_yuv == true &&
			    (formats[i].hsub > 1 || formats[i].vsub > 1))
			{
				return true;
			}
		}
	}
	return false;
}


/*
 * Determines whether multi-plane AFBC (requires specific IP capabiltiies) is
 * supported across all producers and consumers.
 *
 * @param producers      [in]    Producers (flags).
 * @param consumers      [in]    Consumers (flags).
 * @param producer_caps  [in]    Producer capabilities (flags).
 * @param consumer_caps  [in]    Consumer capabilities (flags).
 *
 * @return 1, multiplane AFBC is supported
 *         0, otherwise
 */
static inline bool is_afbc_multiplane_supported(const uint16_t producers,
                                                const uint16_t consumers,
                                                const uint64_t producer_caps,
                                                const uint64_t consumer_caps)
{
	GRALLOC_UNUSED(consumers);

	return (producer_caps & consumer_caps & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_BASIC &&
	        producer_caps & consumer_caps & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_TILED_HEADERS &&
	        producer_caps & consumer_caps & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_EXTRAWIDEBLK &&
	        /*no_producer*/ consumer_caps & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_MULTIPLANE_READ &&
	        producers == 0) ? true : false;
}


/*
 * Determines whether a given base format is supported by all producers and
 * consumers. After checking broad support across producer/consumer IP, this
 * function uses capabilities to disable features (base formats and AFBC
 * modifiers) that are not supported by specific versions of each IP.
 *
 * @param fmt_idx        [in]    Index into format properties table (base format).
 * @param ip_fmt_idx     [in]    Index into format IP support table (base format).
 * @param usage          [in]    Buffer usage.
 * @param producers      [in]    Producers (flags).
 * @param consumers      [in]    Consumers (flags).
 * @param producer_caps  [in]    Producer capabilities (flags).
 * @param consumer_caps  [in]    Consumer capabilities (flags).
 *
 * @return format support flags.
 */
static format_support_flags is_format_supported(const int32_t fmt_idx,
                                                const int32_t ip_fmt_idx,
                                                const uint64_t usage,
                                                const uint16_t producers,
                                                const uint16_t consumers,
                                                const uint64_t producer_caps,
                                                const uint64_t consumer_caps)
{
	/* Determine format support from table. */
	format_support_flags f_flags = ip_supports_base_format(producers, consumers,
	                                                       &formats_ip_support[ip_fmt_idx]);

	/* Determine whether producers/consumers support required AFBC features. */
	if (f_flags & F_AFBC)
	{
		if (!formats[fmt_idx].afbc ||
		    (producer_caps & consumer_caps & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_BASIC) == 0)
		{
			f_flags &= ~F_AFBC;
		}

		/* Check that multi-plane format supported by producers/consumers. */
		if (formats[fmt_idx].npln > 1 &&
		    !is_afbc_multiplane_supported(producers, consumers, producer_caps, consumer_caps))
		{
			f_flags &= ~F_AFBC;
		}

		/* Apply some additional restrictions from producer_caps and consumer_caps */
		/* Some modifiers affect base format support */
		if (formats[fmt_idx].is_yuv)
		{
			if ((producer_caps & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_YUV_WRITE) == 0)
			{
				f_flags &= ~F_AFBC;
			}

			if ((consumer_caps & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_YUV_READ) == 0)
			{
				f_flags &= ~F_AFBC;
			}
		}

		if (usage & GRALLOC_USAGE_FRONT_BUFFER)
		{
			if ((producer_caps & consumer_caps & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_DOUBLE_BODY) == 0)
			{
				f_flags &= ~F_AFBC;
			}
		}
	}
	if (f_flags != F_NONE)
	{
		if (formats[fmt_idx].id == MALI_GRALLOC_FORMAT_INTERNAL_RGBA_1010102 &&
		    (producer_caps & consumer_caps & MALI_GRALLOC_FORMAT_CAPABILITY_PIXFMT_RGBA1010102) == 0)
		{
			/* TODO: forcing F_LIN is a W/A. Make a proper solution */
			f_flags = F_NONE;
			f_flags = F_LIN;
		}
		else if (formats[fmt_idx].id == MALI_GRALLOC_FORMAT_INTERNAL_RGBA_16161616)
		{
			if ((producer_caps & consumer_caps & MALI_GRALLOC_FORMAT_CAPABILITY_PIXFMT_RGBA16161616) == 0)
			{
				/* TODO: forcing F_LIN is a W/A. Make a proper solution */
				//f_flags = F_NONE;
				f_flags = F_LIN;
			}
			else if ((producer_caps & consumer_caps & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_RGBA16161616) == 0)
			{
				f_flags = F_LIN;
			}
		}
	}

	return f_flags;
}


/*
 * Ensures that the allocation format conforms to the AFBC specification and is
 * supported by producers and consumers. Format modifiers are (in most cases)
 * disabled as required to make valid. It is important to first resolve invalid
 * combinations which are not dependent upon others to reduce the possibility of
 * circular dependency.
 *
 * @param alloc_format          [in]    Allocation format (base + modifiers).
 * @param producer_active_caps  [in]    Producer capabilities (flags).
 * @param consumer_active_caps  [in]    Consumer capabilities (flags).
 *
 * @return valid alloc_format with AFBC possibly disabled (if required)
 */
static uint64_t validate_afbc_format(uint64_t alloc_format,
                                     const uint64_t producer_active_caps,
                                     const uint64_t consumer_active_caps)
{
	const uint32_t base_format = alloc_format & MALI_GRALLOC_INTFMT_FMT_MASK;

	/*
	 * AFBC with tiled-headers must be enabled for AFBC front-buffer-safe allocations.
	 * NOTE: format selection algorithm will always try and enable AFBC with
	 * tiled-headers where supported by producer(s) and consumer(s).
	 */
	if (alloc_format & MALI_GRALLOC_INTFMT_AFBC_DOUBLE_BODY)
	{
		/*
		 * Disable (extra-) wide-block which is unsupported with front-buffer safe AFBC.
		 */
		alloc_format &= ~MALI_GRALLOC_INTFMT_AFBC_WIDEBLK;
		alloc_format &= ~MALI_GRALLOC_INTFMT_AFBC_EXTRAWIDEBLK;
	}

	/*
	 * AFBC specification: Split-block is not supported for
	 * subsampled formats (YUV) when wide-block is enabled.
	 */
	if (alloc_format & MALI_GRALLOC_INTFMT_AFBC_WIDEBLK &&
	    alloc_format & MALI_GRALLOC_INTFMT_AFBC_SPLITBLK &&
	    is_subsampled_yuv(base_format))
	{
		/* Disable split-block instead of wide-block because because
		 * wide-block has greater impact on display performance.
		 */
		alloc_format &= ~MALI_GRALLOC_INTFMT_AFBC_SPLITBLK;
	}

	/* AFBC specification: Split-block must be enabled for
	 * non-subsampled formats > 16 bpp, where wide-block is enabled.
	 */
	if (alloc_format & MALI_GRALLOC_INTFMT_AFBC_WIDEBLK &&
	   (alloc_format & MALI_GRALLOC_INTFMT_AFBC_SPLITBLK) == 0 &&
	   !is_subsampled_yuv(base_format) &&
	   base_format != MALI_GRALLOC_FORMAT_INTERNAL_RGB_565)
	{
		/* Enable split-block if supported by producer(s) & consumer(s),
		 * otherwise disable wide-block.
		 */
		if (producer_active_caps & consumer_active_caps &
			MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_SPLITBLK)
		{
			alloc_format |= MALI_GRALLOC_INTFMT_AFBC_SPLITBLK;
		}
		else
		{
			alloc_format &= ~MALI_GRALLOC_INTFMT_AFBC_WIDEBLK;
		}
	}

	/* Some RGB formats don't support split block. */
	if (base_format == MALI_GRALLOC_FORMAT_INTERNAL_RGB_565)
	{
		alloc_format &= ~MALI_GRALLOC_INTFMT_AFBC_SPLITBLK;
	}

	/* Ensure that AFBC features are supported by producers/consumers. */
	if ((alloc_format & MALI_GRALLOC_INTFMT_AFBC_BASIC) &&
	    (producer_active_caps & consumer_active_caps & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_BASIC) == 0)
	{
		MALI_GRALLOC_LOGE("AFBC basic selected but not supported by producer/consumer. Disabling "
		       "MALI_GRALLOC_INTFMT_AFBC_BASIC");
		alloc_format &= ~MALI_GRALLOC_INTFMT_AFBC_BASIC;
	}

	if ((alloc_format & MALI_GRALLOC_INTFMT_AFBC_SPLITBLK) &&
	    (producer_active_caps & consumer_active_caps & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_SPLITBLK) == 0)
	{
		MALI_GRALLOC_LOGE("AFBC split-block selected but not supported by producer/consumer. Disabling "
		       "MALI_GRALLOC_INTFMT_AFBC_SPLITBLK");
		alloc_format &= ~MALI_GRALLOC_INTFMT_AFBC_SPLITBLK;
	}

	if ((alloc_format & MALI_GRALLOC_INTFMT_AFBC_WIDEBLK) &&
	    (producer_active_caps & consumer_active_caps & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_WIDEBLK) == 0)
	{
		MALI_GRALLOC_LOGE("AFBC wide-block selected but not supported by producer/consumer. Disabling "
		       "MALI_GRALLOC_INTFMT_AFBC_WIDEBLK");
		alloc_format &= ~MALI_GRALLOC_INTFMT_AFBC_WIDEBLK;
	}

	if ((alloc_format & MALI_GRALLOC_INTFMT_AFBC_TILED_HEADERS) &&
	    (producer_active_caps & consumer_active_caps & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_TILED_HEADERS) == 0)
	{
		MALI_GRALLOC_LOGE("AFBC tiled-headers selected but not supported by producer/consumer. Disabling "
		       "MALI_GRALLOC_INTFMT_AFBC_TILED_HEADERS");
		alloc_format &= ~MALI_GRALLOC_INTFMT_AFBC_TILED_HEADERS;
	}

	if (!((alloc_format & MALI_GRALLOC_INTFMT_AFBC_SPARSE) ||
	      (producer_active_caps & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_WRITE_NON_SPARSE)))
	{
		MALI_GRALLOC_LOGE("AFBC sparse not selected while producer cannot write non-sparse. Enabling "
		      "MALI_GRALLOC_INTFMT_AFBC_SPARSE");
		alloc_format |= MALI_GRALLOC_INTFMT_AFBC_SPARSE;
	}

	return alloc_format;
}


/*
 * Derives a valid AFBC format (via modifiers) for all producers and consumers.
 * Formats are validated after enabling the largest feature set supported (and
 * desirable) for the IP usage. Some format modifier combinations are not
 * compatible. See MALI_GRALLOC_INTFMT_* modifiers for more information.
 *
 * @param base_format    [in]    Base format (internal).
 * @param usage          [in]    Buffer usage.
 * @param producer       [in]    Buffer producers (write).
 * @param consumer       [in]    Buffer consumers (read).
 * @param producer_caps  [in]    Buffer producer capabilities (intersection).
 * @param consumer_caps  [in]    Buffer consumer capabilities (intersection).
 *
 * @return valid AFBC format, where modifiers are enabled (supported/preferred);
 *         base format without modifers, otherwise
 */
static uint64_t get_afbc_format(const uint32_t base_format,
                                const uint64_t usage,
                                const uint16_t producer,
                                const uint16_t consumer,
                                const uint64_t producer_caps,
                                const uint64_t consumer_caps)
{
	uint64_t alloc_format = base_format;

	/*
	 * Determine AFBC modifiers where capabilities are defined for all producers
	 * and consumers. NOTE: AFBC is not supported for video transcode (VPU --> VPU).
	 */
	if (producer_caps & consumer_caps & MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT &&
	    ((producer & GOOGLE_GRALLOC_PRODUCER_VPUS_MASK) == 0 || (consumer & GOOGLE_GRALLOC_CONSUMER_VPUS_MASK) == 0))
	{
		if (producer_caps & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_BASIC &&
		    consumer_caps & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_BASIC)
		{
			alloc_format |= MALI_GRALLOC_INTFMT_AFBC_BASIC;

#if 0
			const int format_idx = get_format_index(base_format);

			/* TODO: AFBC YUV TRANSFORM corrupts screen when enabled. find out why */
			if (format_idx != -1)
			{
				if (formats[format_idx].yuv_transform == true)
				{
					alloc_format |= MALI_GRALLOC_INTFMT_AFBC_YUV_TRANSFORM;
				}
			}
#endif

			if (!(producer_caps & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_WRITE_NON_SPARSE))
			{
				alloc_format |= MALI_GRALLOC_INTFMT_AFBC_SPARSE;
			}

			if (producer_caps & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_TILED_HEADERS &&
			    consumer_caps & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_TILED_HEADERS)
			{
				alloc_format |= MALI_GRALLOC_INTFMT_AFBC_TILED_HEADERS;

				if (usage & GRALLOC_USAGE_FRONT_BUFFER &&
				    producer_caps & consumer_caps & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_DOUBLE_BODY)
				{
					alloc_format |= MALI_GRALLOC_INTFMT_AFBC_DOUBLE_BODY;
				}
			}

			/*
			 * Specific producer/consumer combinations benefit from additional
			 * AFBC features (e.g. * --> GPU).
			 */
			if (consumer & MALI_GRALLOC_CONSUMER_GPU)
			{
				if (producer_caps & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_SPLITBLK &&
				    consumer_caps & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_SPLITBLK)
				{
					alloc_format |= MALI_GRALLOC_INTFMT_AFBC_SPLITBLK;
				}

				/*
				 * NOTE: assume that all AFBC layers are pre-rotated. 16x16 SB
				 * must be used with DPU consumer when rotation is required.
				 */
				if (producer_caps & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_WIDEBLK &&
				    consumer_caps & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_WIDEBLK)
				{
					alloc_format |= MALI_GRALLOC_INTFMT_AFBC_WIDEBLK;
				}
			}
		}
	}

	alloc_format = validate_afbc_format(alloc_format, producer_caps, consumer_caps);

	return alloc_format;
}

/*
 * Obtains the 'active' capabilities (for producers/consumers) by applying
 * additional constraints to the capabilities declared for each IP. Some rules
 * are based on format, others specific to producer/consumer. This function must
 * be careful not to make any assumptions about the base format properties since
 * fallback might still occur. It is safe to use any properties which are common
 * across all compatible formats as defined by is_format_compatible().
 *
 * @param format                 [in]    Base format requested.
 * @param producers              [in]    Producers (flags).
 * @param consumers              [in]    Consumers (flags).
 * @param producer_active_caps   [out]   Active producer capabilities (flags).
 * @param consumer_active_caps   [out]   Active consumer capabilities (flags).
 *
 * @return none.
 */
static void get_active_caps(const format_info_t format,
                            const uint16_t producers,
                            const uint16_t consumers,
                            uint64_t * const producer_active_caps,
                            uint64_t * const consumer_active_caps)
{
	const uint64_t producer_caps = (producer_active_caps) ? *producer_active_caps : 0;
	const uint64_t consumer_caps = (consumer_active_caps) ? *consumer_active_caps : 0;
	uint64_t producer_mask = ~0;
	uint64_t consumer_mask = ~0;

	if (format.is_yuv)
	{
		if ((producer_caps & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_YUV_WRITE) == 0)
		{
			producer_mask &= ~MALI_GRALLOC_FORMAT_CAPABILITY_AFBCENABLE_MASK;
		}
		else if (producers & MALI_GRALLOC_PRODUCER_GPU)
		{
			/* All GPUs that can write YUV AFBC can only do it in 16x16,
			 * optionally with tiled headers.
			 */
			producer_mask &=
				~(MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_SPLITBLK |
				  MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_WIDEBLK);
		}

		if ((consumer_caps & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_YUV_READ) == 0)
		{
			consumer_mask &= ~MALI_GRALLOC_FORMAT_CAPABILITY_AFBCENABLE_MASK;
		}
	}

	// TODO: b/183385318 Prefer 16x16 AFBC over 32x8 for GPU --> GPU
	if ((producers & MALI_GRALLOC_PRODUCER_GPU) &&
	    (consumers & MALI_GRALLOC_CONSUMER_GPU) &&
            (producer_caps & consumer_caps & MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_BASIC))
	{
		producer_mask &=
			~(MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_SPLITBLK |
			  MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_WIDEBLK);

		consumer_mask &=
			~(MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_SPLITBLK |
			  MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_WIDEBLK);
	}

	if (consumers & MALI_GRALLOC_CONSUMER_DPU)
	{
		/* DPU does not support split-block other than RGB(A) 24/32-bit */
		if (!format.is_rgb || format.bpp[0] < 24)
		{
			if (consumers & MALI_GRALLOC_CONSUMER_DPU)
			{
				consumer_mask &= ~MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_SPLITBLK;
			}
		}
	}

	if (producer_active_caps)
	{
		*producer_active_caps &= producer_mask;
	}
	if (consumer_active_caps)
	{
		*consumer_active_caps &= consumer_mask;
	}
}


/*
 * Obtains support flags and modifiers for base format.
 *
 * @param base_format           [in]    Base format for which to deduce support.
 * @param usage                 [in]    Buffer usage.
 * @param producers             [in]    Producers (flags).
 * @param consumers             [in]    Consumers (flags).
 * @param producer_active_caps  [in]    Producer capabilities (flags).
 * @param consumer_active_caps  [in]    Consumer capabilities (flags).
 * @param fmt_supported         [out]   Format (base + modifiers) and support (flags).
 *
 * @return 1, base format supported
 *         0, otherwise
 */
bool get_supported_format(const uint32_t base_format,
                          const uint64_t usage,
                          const uint16_t producers,
                          const uint16_t consumers,
                          const uint64_t producer_active_caps,
                          const uint64_t consumer_active_caps,
                          fmt_props * const fmt_supported)
{
	const int32_t fmt_idx = get_format_index(base_format);
	const int32_t ip_fmt_idx = get_ip_format_index(base_format);
	assert(fmt_idx >= 0);
	if (ip_fmt_idx == -1)
	{
		/* Return undefined base format. */
		MALI_GRALLOC_LOGE("Failed to find IP support info for format id: 0x%" PRIx32,
		      base_format);
		return false;
	}

	fmt_supported->f_flags = is_format_supported(fmt_idx,
	                                             ip_fmt_idx,
	                                             usage,
	                                             producers,
	                                             consumers,
	                                             producer_active_caps,
	                                             consumer_active_caps);
	MALI_GRALLOC_LOGV("IP support: 0x%" PRIx16, fmt_supported->f_flags);
	if (fmt_supported->f_flags == F_NONE &&
	    consumers & MALI_GRALLOC_CONSUMER_GPU &&
	    consumers & MALI_GRALLOC_CONSUMER_DPU)
	{
		/* Determine alternative caps for formats when GPU/DPU consumer.
		 * Although we normally combine capabilities for multiple consumers with "AND",
		 * in some situations (e.g. formats) we make best effort and say that fallback
		 * to GPU is acceptable and perferred over rejecting allocation. GPU composition
		 * must always be supported in case of fallback from DPU.
		 */
		const uint16_t consumers_nodpu = consumers & ~MALI_GRALLOC_CONSUMER_DPU;
		uint64_t consumer_nodpu_caps = consumer_active_caps;

		/* Set consumer caps to GPU-only (assume superset of DPU). */
		consumer_nodpu_caps = get_consumer_caps(consumers_nodpu);
		get_active_caps(formats[fmt_idx],
		                producers, consumers_nodpu,
		                NULL, &consumer_nodpu_caps);

		fmt_supported->f_flags = is_format_supported(fmt_idx,
	                                                 ip_fmt_idx,
		                                             usage,
	                                                 producers,
	                                                 consumers_nodpu,
	                                                 producer_active_caps,
	                                                 consumer_nodpu_caps);
	}

	fmt_supported->base_format = base_format;
	if (fmt_supported->f_flags & F_AFBC)
	{
		const uint64_t afbc_format = get_afbc_format(base_format,
		                                             usage,
		                                             producers,
		                                             consumers,
		                                             producer_active_caps,
		                                             consumer_active_caps);

		MALI_GRALLOC_LOGV("AFBC format: (%s 0x%" PRIx64 ")",
			format_name(afbc_format), afbc_format);

		/* Disable AFBC when forced by usage or no format modifiers selected. */
		if ((usage & MALI_GRALLOC_USAGE_NO_AFBC) == MALI_GRALLOC_USAGE_NO_AFBC ||
		    afbc_format == fmt_supported->base_format)
		{
			fmt_supported->f_flags &= ~F_AFBC;
		}

		/* Check that AFBC features are correct for multiplane format. */
		alloc_type_t alloc_type{};
		get_alloc_type(afbc_format & MALI_GRALLOC_INTFMT_EXT_MASK,
					   fmt_idx,
					   usage,
					   &alloc_type);
		if (formats[fmt_idx].npln > 1 && alloc_type.is_multi_plane == false)
		{
			fmt_supported->f_flags &= ~F_AFBC;
		}

		/* Store any format modifiers */
		fmt_supported->format_ext = afbc_format & MALI_GRALLOC_INTFMT_EXT_MASK;
	}
	if ((fmt_supported->f_flags & F_AFBC) == 0)
	{
		fmt_supported->format_ext = 0;
	}

	MALI_GRALLOC_LOGV("Ext format: (%s 0x%" PRIx64 ")", format_name(fmt_supported->format_ext),
		fmt_supported->format_ext);

	return (fmt_supported->f_flags == F_NONE) ? false : true;
}


/*
 * Determines whether two base formats have comparable 'color' components. Alpha
 * is considered unimportant for YUV formats.
 *
 * @param f_old     [in]    Format properties (old format).
 * @param f_new     [in]    Format properties (new format).
 *
 * @return 1, format components are equivalent
 *         0, otherwise
 */
static bool comparable_components(const format_info_t * const f_old,
                                  const format_info_t * const f_new)
{
	if (f_old->is_yuv && f_new->bps == f_old->bps)
	{
		/* Formats have the same number of components. */
		if (f_new->total_components() == f_old->total_components())
		{
			return true;
		}

		/* Alpha component can be dropped for yuv formats.
		 * This assumption is required for mapping Y0L2 to
		 * single plane 10-bit YUV420 AFBC.
		 */
		if (f_old->has_alpha)
		{
			if (f_new->total_components() == 3 &&
			    f_new->is_yuv &&
			    !f_new->has_alpha)
			{
				return true;
			}
		}
	}
	else if (f_old->is_rgb)
	{
		if (f_new->total_components() == f_old->total_components())
		{
			if (f_new->bpp[0] == f_old->bpp[0] && f_new->bps == f_old->bps)
			{
				return true;
			}
		}
	}
	else
	{
		if (f_new->id == f_old->id)
		{
			return true;
		}
	}

	return false;
}


/*
 * Determines whether two base formats are compatible such that data from one
 * format could be accurately represented/interpreted in the other format.
 *
 * @param f_old     [in]    Format properties (old format).
 * @param f_new     [in]    Format properties (new format).
 *
 * @return 1, formats are equivalent
 *         0, otherwise
 */
static bool is_format_compatible(const format_info_t * const f_old,
                                 const format_info_t * const f_new)
{
	if (f_new->hsub == f_old->hsub &&
	    f_new->vsub == f_old->vsub &&
	    f_new->is_rgb == f_old->is_rgb &&
	    f_new->is_yuv == f_old->is_yuv &&
	    comparable_components(f_old, f_new))
	{
		return true;
	}
	else
	{
		return false;
	}
}

/**
 * Provide a grade for the compatible format with respect to the requested format. Used to find the best compatible
 * format.
 *
 * @param fmt[in]    Compatible format properties.
 * @param req_format Requested base format.
 *
 * @return The grade of the compatible format. Higher is better. Returns 0 if format extensions are incompatible with
 * requested format.
 */
uint64_t grade_format(const fmt_props &fmt, uint32_t req_format)
{
	uint64_t grade = 1;

	GRALLOC_UNUSED(req_format);

	static const struct {
		uint64_t fmt_ext;
		uint64_t value;
	} fmt_ext_values[] {
		{ MALI_GRALLOC_INTFMT_AFBC_BASIC, 1 },
		{ MALI_GRALLOC_INTFMT_AFBC_SPLITBLK, 1 },
		{ MALI_GRALLOC_INTFMT_AFBC_WIDEBLK, 1 },
		{ MALI_GRALLOC_INTFMT_AFBC_TILED_HEADERS, 1 },
		{ MALI_GRALLOC_INTFMT_AFBC_EXTRAWIDEBLK, 1 },
		{ MALI_GRALLOC_INTFMT_AFBC_DOUBLE_BODY, 1 },
		{ MALI_GRALLOC_INTFMT_AFBC_BCH, 1 },
		{ MALI_GRALLOC_INTFMT_AFBC_YUV_TRANSFORM, 1 },
		{ MALI_GRALLOC_INTFMT_AFBC_SPARSE, 1 },
	};
	for (auto& ext : fmt_ext_values)
	{
		if (fmt.format_ext & ext.fmt_ext)
		{
			grade += ext.value;
		}
	}

	return grade;
}

/*
 * Obtains the 'best' allocation format for requested format and usage:
 * 1. Find compatible base formats (based on format properties alone)
 * 2. Find base formats supported by producers/consumers
 * 3. Find best modifiers from supported base formats
 * 4. Select allocation format from "best" base format with "best" modifiers
 *
 * NOTE: Base format re-mapping should not take place when CPU usage is
 * requested.
 *
 * @param req_base_format       [in]    Base format requested by client.
 * @param usage                 [in]    Buffer usage.
 * @param producers             [in]    Producers (flags).
 * @param consumers             [in]    Consumers (flags).
 * @param producer_active_caps  [in]    Producer capabilities (flags).
 * @param consumer_active_caps  [in]    Consumer capabilities (flags).
 *
 * @return alloc_format, supported for usage;
 *         MALI_GRALLOC_FORMAT_INTERNAL_UNDEFINED, otherwise
 */
static uint64_t get_best_format(const uint32_t req_base_format,
                                const uint64_t usage,
                                const uint16_t producers,
                                const uint16_t consumers,
                                const uint64_t producer_active_caps,
                                const uint64_t consumer_active_caps)
{
	uint64_t alloc_format = MALI_GRALLOC_FORMAT_INTERNAL_UNDEFINED;

	assert(req_base_format != MALI_GRALLOC_FORMAT_INTERNAL_UNDEFINED);
	const int32_t req_fmt_idx = get_format_index(req_base_format);
	MALI_GRALLOC_LOGV("req_base_format: (%s 0x%" PRIx32 ")",
		format_name(req_base_format), req_base_format);
	MALI_GRALLOC_LOGV("req_fmt_idx: %d", req_fmt_idx);
	assert(req_fmt_idx >= 0);

	/* 1. Find compatible base formats. */
	std::vector<fmt_props> f_compat;
	for (uint16_t i = 0; i < num_formats; i++)
	{
		if (is_format_compatible(&formats[req_fmt_idx], &formats[i]))
		{
			fmt_props fmt = {0, 0, 0};
			fmt.base_format = formats[i].id;
			MALI_GRALLOC_LOGV("Compatible: Base-format: (%s 0x%" PRIx32 ")",
				format_name(fmt.base_format), fmt.base_format);
			f_compat.push_back(fmt);
		}
	}
	assert(f_compat.size() > 0);

	/* 2. Find base formats supported by IP and among them, find the highest
	 * number of modifier enabled format and check if requested format is present
	 */

	int32_t num_supported_formats = 0;
	uint64_t req_format_grade = 0;
	uint64_t best_fmt_grade = 0;
	uint64_t first_of_best_formats = MALI_GRALLOC_FORMAT_INTERNAL_UNDEFINED;
	uint64_t req_format = MALI_GRALLOC_FORMAT_INTERNAL_UNDEFINED;

	for (uint16_t i = 0; i < f_compat.size(); i++)
	{
		MALI_GRALLOC_LOGV("Compatible: Base-format: 0x%" PRIx32, f_compat[i].base_format);
		fmt_props fmt = {0, 0, 0};
		bool supported = get_supported_format(f_compat[i].base_format,
		                                      usage,
		                                      producers,
		                                      consumers,
		                                      producer_active_caps,
		                                      consumer_active_caps,
		                                      &fmt);
		if (supported)
		{
			const uint64_t sup_fmt_grade = grade_format(fmt, req_base_format);
			if (sup_fmt_grade)
			{
				num_supported_formats++;
				MALI_GRALLOC_LOGV("Supported: Base-format: (%s 0x%" PRIx32 "), Modifiers: 0x%" PRIx64 ", Flags: 0x%" PRIx16,
			      format_name(fmt.base_format), fmt.base_format, fmt.format_ext, fmt.f_flags);

				/* 3. Find best modifiers from supported base formats */
				if (sup_fmt_grade > best_fmt_grade)
				{
					best_fmt_grade = sup_fmt_grade;
					first_of_best_formats = fmt.base_format | fmt.format_ext;
				}

				/* Check if current supported format is same as requested format */
				if (fmt.base_format == req_base_format)
				{
					req_format_grade = sup_fmt_grade;
					req_format = fmt.base_format | fmt.format_ext;
				}
			}
		}
	}

	/* 4. Select allocation format from "best" base format with "best" modifiers */
	if (num_supported_formats > 0)
	{
		/* Select first/one of best format when requested format is either not
		* supported or requested format is not the best format.
		*/
		if ((req_format_grade != best_fmt_grade) &&
			(((producers & MALI_GRALLOC_PRODUCER_CPU) == 0) &&
			((consumers & MALI_GRALLOC_CONSUMER_CPU) == 0)))
		{
			alloc_format = first_of_best_formats;
		}
		else if (req_format_grade != 0)
		{
			alloc_format = req_format;
		}
	}

	MALI_GRALLOC_LOGV("Selected format: (%s 0x%" PRIx64 ")",
		format_name(alloc_format), alloc_format);
	return alloc_format;
}


/*
 * Obtain format modifiers from requested format.
 *
 * @param req_format       [in]    Requested format (base + optional modifiers).
 * @param usage            [in]    Buffer usage.
 *
 * @return format modifiers, where extracted from requested format;
 *         0, otherwise
 */
uint64_t get_format_ext(const uint64_t req_format, const uint64_t usage)
{
	/* TODO: clean up this function. Or remove it */
	GRALLOC_UNUSED(usage);
	return req_format & MALI_GRALLOC_INTFMT_EXT_MASK;
}


/*
 * Obtain base format from requested format. There are two primary ways in which
 * the client can specify requested format:
 * - Public API:
 *   - Normal usage, with HAL_PIXEL_FORMAT_* / MALI_GRALLOC_FORMAT_INTERNAL_*
 *   - Private usage, (as normal usage) with additional format modifiers (MALI_GRALLOC_INTFMT_*)
 * - Private API: allows private usage to be provided explicitly
 *                (type == MALI_GRALLOC_FORMAT_TYPE_INTERNAL)
 *
 * @param req_format       [in]    Requested format (base + optional modifiers).
 * @param usage            [in]    Buffer usage.
 * @param type             [in]    Format type (public usage or internal).
 * @param map_to_internal  [in]    Base format (if public HAL_PIXEL_FORMAT_*)
 *                                 should be mapped to internal representation.
 *
 * @return base format, where identified/translated from requested format;
 *         MALI_GRALLOC_FORMAT_INTERNAL_UNDEFINED, otherwise
 */
uint32_t get_base_format(const uint64_t req_format,
                         const uint64_t usage,
                         const mali_gralloc_format_type type,
                         const bool map_to_internal)
{
	GRALLOC_UNUSED(type);

	uint32_t base_format = MALI_GRALLOC_FORMAT_INTERNAL_UNDEFINED;

	/* Mask out extension bits which could be present with type 'internal'. */
	base_format = req_format & MALI_GRALLOC_INTFMT_FMT_MASK;

	/* Map Android flexible formats to internal base formats */
	if (req_format == HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED)
	{
		auto consumers = get_consumers(usage);
		auto producers = get_producers(usage);

		if ((usage & GRALLOC_USAGE_HW_TEXTURE) || (usage & GRALLOC_USAGE_HW_COMPOSER))
		{
			base_format = HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN;
		}
		else if ((producers & MALI_GRALLOC_PRODUCER_CAM) &&
			 !(producers & MALI_GRALLOC_PRODUCER_GPU) &&
			 (consumers == GOOGLE_GRALLOC_CONSUMER_MFC))
		{
			// Allocated camera buffer is SBWC compressed when
			// 1. Camera is one of the producers
			// 2. GPU is not one of the producers
			// 3. MFC is the sole consumer
			if (property_get_bool("debug.vendor.gpu.record_sbwc", true)) {
				base_format = HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC;
			} else {
				base_format = HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN;
			}
		}
		else if (get_consumers(usage) & GOOGLE_GRALLOC_CONSUMER_BIG)
		{
			base_format = HAL_PIXEL_FORMAT_GOOGLE_NV12_SP;
		}
		else if (usage & GRALLOC_USAGE_HW_VIDEO_ENCODER)
		{
			base_format = HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN;
		}
		else if (usage & GRALLOC_USAGE_VIDEO_PRIVATE_DATA)
		{
			base_format = HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN;
		}
		else if ((usage & GRALLOC_USAGE_HW_CAMERA_READ) && (usage & GRALLOC_USAGE_HW_CAMERA_WRITE))
		{
			// Camera IMPLEMENTATION_DEFINED format output maps to NV21.
			base_format = HAL_PIXEL_FORMAT_YCrCb_420_SP;
		}
		else
		{
			base_format = HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN;
		}
	}
	else if (req_format == HAL_PIXEL_FORMAT_YCbCr_420_888)
	{
		if (get_consumers(usage) & GOOGLE_GRALLOC_CONSUMER_BIG)
		{
			base_format = HAL_PIXEL_FORMAT_GOOGLE_NV12_SP;
		}
		else if (usage & (GRALLOC_USAGE_HW_VIDEO_ENCODER | GRALLOC_USAGE_HW_VIDEO_DECODER))
		{
			base_format = HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN;
		}
		else if (usage & GRALLOC_USAGE_HW_CAMERA_WRITE)
		{
			// Catchall for camera write. DO NOT CHANGE WITHOUT TESTING THESE SCENARIOS:
			// 1. Camera capture and initial photo processing
			// 2. Other major camera operations - video recording, portrait etc
			// 3. Faceauth
			// 4. Multi-profile user photo add
			// 5. Capture and resize - use chat app to capture a photo
			// Re-run these steps with GPU composition:
			// adb shell service call SurfaceFlinger 1008 i32 1
			base_format = HAL_PIXEL_FORMAT_YCrCb_420_SP;
		}
		else
		{
			base_format = HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN;
		}
	}
	else if (req_format == HAL_PIXEL_FORMAT_YCBCR_P010)
	{
		if (get_consumers(usage) & GOOGLE_GRALLOC_CONSUMER_BIG)
		{
			base_format = HAL_PIXEL_FORMAT_GOOGLE_NV12_SP_10B;
		}
		else if (usage & (GRALLOC_USAGE_HW_VIDEO_ENCODER | GRALLOC_USAGE_HW_VIDEO_DECODER))
		{
			base_format = HAL_PIXEL_FORMAT_EXYNOS_YCbCr_P010_SPN;
		}
	}

	/* Obtain a valid base format, optionally mapped to internal. Flex formats
	 * are always mapped to internal base format.
	 * NOTE: Overlap between HAL_PIXEL_FORMAT_* and MALI_GRALLOC_FORMAT_INTERNAL_*
	 * is intentional. See enumerations for more information.
	 */
	return get_internal_format(base_format, map_to_internal);
}


/*
 * Select pixel format (base + modifier) for allocation.
 *
 * @param req_format       [in]   Format (base + optional modifiers) requested by client.
 * @param type             [in]   Format type (public usage or internal).
 * @param usage            [in]   Buffer usage.
 *
 * @return alloc_format, format to be used in allocation;
 *         MALI_GRALLOC_FORMAT_INTERNAL_UNDEFINED, where no suitable
 *         format could be found.
 */
uint64_t mali_gralloc_select_format(const uint64_t req_format,
                                    const mali_gralloc_format_type type,
                                    const uint64_t usage)
{
	uint64_t alloc_format = MALI_GRALLOC_FORMAT_INTERNAL_UNDEFINED;

	/*
	 * Obtain base_format (no extension bits) and indexes into format tables.
	 */
	const uint32_t req_base_format = get_base_format(req_format, usage, type, true);
	const int32_t req_fmt_idx = get_format_index(req_base_format);
	if (req_base_format == MALI_GRALLOC_FORMAT_INTERNAL_UNDEFINED ||
	    req_fmt_idx == -1)
	{
		MALI_GRALLOC_LOGE("Invalid base format! req_base_format = (%s 0x%" PRIx32
			"), req_format = (%s 0x%" PRIx64 "), type = 0x%" PRIx32,
			format_name(req_base_format), req_base_format, format_name(req_format), req_format, type);
		goto out;
	}

	/* Reject if usage specified is outside white list of valid usages. */
	if (type != MALI_GRALLOC_FORMAT_TYPE_INTERNAL && (usage & (~VALID_USAGE)) != 0)
	{
		MALI_GRALLOC_LOGW("Invalid usage specified: %s 0x%" PRIx64, describe_usage(usage).c_str(), usage);
	}

	/* TODO: Make a function for finding formats that should be allocated as the request format */
	if (is_exynos_format(req_base_format) || req_base_format == HAL_PIXEL_FORMAT_BLOB)
	{
		alloc_format = req_base_format;
	}
	else if (usage == 0)
	{
		/* Allocate format as-is when no usage is specified */
		alloc_format = req_base_format;
	}
	else
	{
		/* Determine producers and consumers. */
		const uint16_t producers = get_producers(usage);
		const uint16_t consumers = get_consumers(usage);

		MALI_GRALLOC_LOGV("Producers: 0x%" PRIx16 ", Consumers: 0x%" PRIx16,
		      producers, consumers);

		/* Obtain producer and consumer capabilities. */
		const uint64_t producer_caps = get_producer_caps(producers);

		uint64_t consumer_caps = 0;
#ifdef GRALLOC_HWC_FB_DISABLE_AFBC
		if (GRALLOC_HWC_FB_DISABLE_AFBC && DISABLE_FRAMEBUFFER_HAL && (usage & GRALLOC_USAGE_HW_FB))
		{
			/* Override capabilities to disable AFBC for DRM HWC framebuffer surfaces. */
			consumer_caps = MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT;
		}
		else
#endif
		{
			consumer_caps = get_consumer_caps(consumers);
		}

		MALI_GRALLOC_LOGV("Producer caps: 0x%" PRIx64 ", Consumer caps: 0x%" PRIx64,
		      producer_caps, consumer_caps);

		if (producers == 0 && consumers == 0)
		{
			MALI_GRALLOC_LOGE("Producer and consumer not identified.");
			goto out;
		}
		else if (producers == 0 || consumers == 0)
		{
			MALI_GRALLOC_LOGV("Producer or consumer not identified.");
		}

		if ((usage & MALI_GRALLOC_USAGE_NO_AFBC) == MALI_GRALLOC_USAGE_NO_AFBC &&
		    formats[req_fmt_idx].is_yuv)
		{
			MALI_GRALLOC_LOGE("ERROR: Invalid usage 'MALI_GRALLOC_USAGE_NO_AFBC' when allocating YUV formats");
			goto out;
		}

		uint64_t producer_active_caps = producer_caps;
		uint64_t consumer_active_caps = consumer_caps;

		get_active_caps(formats[req_fmt_idx],
		                producers, consumers,
		                &producer_active_caps, &consumer_active_caps);

		MALI_GRALLOC_LOGV("Producer caps (active): 0x%" PRIx64 ", Consumer caps (active): 0x%" PRIx64,
		      producer_active_caps, consumer_active_caps);

		/* TODO: reimplment get_best_format */
		alloc_format = get_best_format(formats[req_fmt_idx].id,
		                               usage,
		                               producers,
		                               consumers,
		                               producer_active_caps,
		                               consumer_active_caps);
	}

out:
	MALI_GRALLOC_LOGV("%s: req_format=(%s 0x%08" PRIx64 "), usage=(%s 0x%" PRIx64
	      "), req_base_format=(%s 0x%" PRIx32 "), alloc_format=(%s, 0x%" PRIx64 ")", __func__,
	      format_name(req_format), req_format, describe_usage(usage).c_str(), usage,
		  format_name(req_base_format), req_base_format, format_name(alloc_format), alloc_format);

	return alloc_format;
}

bool is_exynos_format(uint32_t base_format)
{
	switch (base_format)
	{
		case HAL_PIXEL_FORMAT_YCrCb_420_SP:
		case HAL_PIXEL_FORMAT_EXYNOS_YV12_M:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_P:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_P_M:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_TILED:
		case HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M:
		case HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_FULL:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_S10B:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_S10B:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_P010_SPN:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_P010_M:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC:
		case HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_SBWC:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC:
		case HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_10B_SBWC:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC_L50:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC_L75:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC_L50:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC_L75:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L40:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L60:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L80:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L40:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L60:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L80:
			return true;
	}

	return false;
}

uint8_t get_exynos_fd_count(uint32_t format) {
	switch (format)
	{
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC:
		case HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_SBWC:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC:
		case HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_10B_SBWC:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC_L50:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC_L75:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L40:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L60:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L80:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_TILED:
		case HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M:
		case HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_FULL:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_S10B:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_P010_M:
			return 2;
		case HAL_PIXEL_FORMAT_EXYNOS_YV12_M:
		case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_P_M:
			return 3;
	}

	return 1;
}

