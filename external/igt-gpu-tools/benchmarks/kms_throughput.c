/*
 * Copyright © 2019 Google LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice (including the next
 * paragraph) shall be included in all copies or substantial portions of the
 * Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

/** @file kms_throughput.c
 */

#include <drm.h>
#include <sys/time.h>
#include <xf86drm.h>
#include "drmtest.h"
#include "igt.h"
#include "ion.h"

static void make_display(igt_display_t *display)
{
	display->drm_fd = drm_open_driver_master(DRIVER_ANY);
	igt_display_require(display, display->drm_fd);
	igt_require(display->is_atomic);
	igt_display_require_output(display);
}

static bool get_output(igt_display_t *display,
		       enum pipe *pipe, igt_output_t **output)
{
	igt_info("Display %p has %d pipes\n", display, display->n_pipes);
	for (int i = 0; i < display->n_pipes; ++i)
	{
		igt_info("Pipe %d (crtc %u) has %d planes\n",
			 i, display->pipes[i].crtc_id,
			 display->pipes[i].n_planes);
	}

	for_each_pipe_with_valid_output(display, *pipe, *output)
	{
		/* we are happy with the first one */
		return true;
	}

	return false;
}

static void get_pipe(igt_display_t *display,
		     igt_pipe_t **p, igt_output_t **output)
{
	enum pipe pipe_idx = PIPE_NONE;
	*output = NULL;
	igt_require(get_output(display, &pipe_idx, output));
	igt_info("Using output id %u, name %s\n",
		 (*output)->id, (*output)->name);

	/* I'd love to call this 'pipe' but pipe(2) is in the way */
	*p = &display->pipes[pipe_idx];
	igt_require(*p);

	igt_info("Chosen pipe (crtc %u) has %d planes\n",
		 (*p)->crtc_id, (*p)->n_planes);
}

static void prepare(igt_display_t *display, igt_pipe_t *p, igt_output_t *output)
{
	igt_display_reset(display);
	igt_output_set_pipe(output, p->pipe);
}

static igt_plane_t *plane_for_index(igt_pipe_t *p, size_t index)
{
	return &p->planes[index];
}

struct histogram
{
	struct timeval last_commit;
	size_t num_buckets;
	size_t *buckets;
};

static void histogram_init(struct histogram *h)
{
	gettimeofday(&h->last_commit, NULL);
	h->num_buckets = 100;
	h->buckets = calloc(h->num_buckets, sizeof(*h->buckets));
}

static void histogram_update(struct histogram *h)
{
	struct timeval this_commit;
	gettimeofday(&this_commit, NULL);

	struct timeval diff;
	timersub(&this_commit, &h->last_commit, &diff);
	const size_t ms = (diff.tv_sec * 1000) + (diff.tv_usec / 1000);
	size_t bucket = ms;
	if (bucket >= h->num_buckets)
	{
		// the last bucket is a catch-all
		bucket = h->num_buckets - 1;
	}
	h->buckets[bucket]++;

	memcpy(&h->last_commit, &this_commit, sizeof(this_commit));
}

static void histogram_print(struct histogram *h)
{
	igt_info("Histogram buckets with 1 or more entries:\n");

	for (size_t i = 0; i < h->num_buckets; ++i)
	{
		size_t value = h->buckets[i];

		if (value)
		{
			if (i == h->num_buckets - 1)
			{
				igt_info("%zu+ ms: %zu\n", i, value);
			}
			else
			{
				igt_info("%zu ms: %zu\n", i, value);
			}
		}
	}
}

static void histogram_cleanup(struct histogram *h)
{
	free(h->buckets);
}

static const size_t max_num_fbs = 32;

struct tuning
{
	drmModeModeInfoPtr mode;
	size_t num_iterations;
	size_t num_fb_sets;
	size_t num_fbs;
	struct fbgeom {
		size_t width;
		size_t height;
	} fb_geom[max_num_fbs];
};

static void info_timestamp(const char *text)
{
	struct timeval ts;
	gettimeofday(&ts, NULL);
	igt_debug("%ld: %s\n", ts.tv_usec, text);
}

static void flip_overlays(igt_pipe_t *p, struct igt_fb **fb_sets,
			  const struct tuning *tuning,
			  size_t iter)
{
	size_t fb_set = iter % tuning->num_fb_sets;
	struct igt_fb *fbs = fb_sets[fb_set];

	for (size_t i = 0; i < tuning->num_fbs; ++i)
	{
		igt_plane_t *plane = plane_for_index(p, i);
		igt_plane_set_prop_value(plane, IGT_PLANE_ZPOS, i);
		igt_plane_set_fb(plane, &fbs[i]);
	}

	igt_pipe_obj_set_prop_value(p, IGT_CRTC_ACTIVE, 1);

	info_timestamp("start commit");
	igt_display_commit2(p->display, COMMIT_ATOMIC);
	info_timestamp("end commit");
}

static void repeat_flip(igt_pipe_t *p, struct igt_fb **fb_sets,
			const struct tuning *tuning)
{
	struct histogram h;
	histogram_init(&h);

	for (size_t iter = 0; iter < tuning->num_iterations; ++iter)
	{
		igt_debug("Iteration %zu\n", iter);
		flip_overlays(p, fb_sets, tuning, iter);
		histogram_update(&h);
	}

	igt_debug("About to clear fbs\n");

	for (size_t i = 0; i < tuning->num_fbs; ++i)
	{
		igt_plane_t *plane = plane_for_index(p, i);
		igt_plane_set_fb(plane, NULL);
	}

	igt_debug("About to flip with no fbs\n");

	igt_display_commit2(p->display, COMMIT_ATOMIC);
	igt_wait_for_vblank(p->display->drm_fd, p->pipe);

	igt_debug("About to deactivate the crtc\n");

	igt_pipe_obj_set_prop_value(p, IGT_CRTC_ACTIVE, 0);
	igt_display_commit2(p->display, COMMIT_ATOMIC);

	histogram_print(&h);
	histogram_cleanup(&h);
}

static void create_dumb_fb(igt_display_t *display,
			   size_t width, size_t height,
			   struct igt_fb *fb)
{
	igt_create_fb(display->drm_fd,
		      width, height,
		      DRM_FORMAT_ARGB8888, LOCAL_DRM_FORMAT_MOD_NONE, fb);
}


static int get_num_planes(igt_display_t *display)
{
	const int drm_fd = display->drm_fd;
	int ret;

	drmModePlaneRes *const plane_resources =
		drmModeGetPlaneResources(drm_fd);

	ret = plane_resources->count_planes;

	drmModeFreePlaneResources(plane_resources);

	return ret;
}

static int get_max_zpos(igt_display_t *display, igt_pipe_t *p)
{
	igt_plane_t *primary = plane_for_index(p, 0);

	drmModePropertyPtr zpos_prop = NULL;

	if (kmstest_get_property(display->drm_fd,
				 primary->drm_plane->plane_id,
				 DRM_MODE_OBJECT_PLANE,
				 "zpos", NULL, NULL,
				 &zpos_prop) &&
	    zpos_prop &&
	    zpos_prop->flags & DRM_MODE_PROP_RANGE)
	{
		return zpos_prop->values[1];
	}
	else
	{
		return -1;
	}
}

size_t get_num_fbs(igt_display_t *display, igt_pipe_t *p)
{
	const char *NUM_FBS = getenv("NUM_FBS");

	if (NUM_FBS)
	{
		return (size_t)atoi(NUM_FBS);
	}
	else
	{
		const int num_planes = get_num_planes(display);
		const int max_zpos = get_max_zpos(display, p);

		if (max_zpos >= 0 && max_zpos + 1 < num_planes)
		{
			return (size_t)max_zpos + 1;
		}
		else
		{
			return (size_t)num_planes;
		}
	}
}

size_t calculate_complexity(const drmModeModeInfoPtr mode)
{
	return (size_t)mode->hdisplay * (size_t)mode->vdisplay * (size_t)mode->vrefresh;
}

drmModeModeInfoPtr get_peak_mode(igt_output_t *output)
{
	drmModeConnector *const connector = output->config.connector;
	if (!connector || connector->count_modes == 0)
	{
		return NULL;
	}

	drmModeModeInfoPtr peak_mode = &connector->modes[0];
	size_t peak_complexity = calculate_complexity(peak_mode);

	for (drmModeModeInfoPtr mode = &connector->modes[0];
	     mode < &connector->modes[connector->count_modes]; ++mode)
	{
		const size_t complexity = calculate_complexity(mode);
		igt_debug("Mode %zu is %hux%hu@%u\n",
			  (size_t)(mode - connector->modes),
			  mode->hdisplay, mode->vdisplay, mode->vrefresh);
		if (complexity > peak_complexity)
		{
			peak_mode = mode;
			peak_complexity = complexity;
		}
	}

	return peak_mode;
}

void get_tuning(struct tuning *tuning,
		igt_display_t *display, igt_pipe_t *p,
		igt_output_t *output)
{
	tuning->mode = get_peak_mode(output);
	igt_require(tuning->mode);

	if (igt_output_get_mode(output) != tuning->mode)
	{
		igt_output_override_mode(output, tuning->mode);
		igt_display_commit2(p->display, COMMIT_ATOMIC);
	}

	igt_info("Chosen mode:\n");
	kmstest_dump_mode(tuning->mode);

	tuning->num_iterations = 1000;
	tuning->num_fb_sets = 2;

	tuning->num_fbs = get_num_fbs(display, p);
	igt_require(tuning->num_fbs <= max_num_fbs);

	drmModeModeInfo *mode = igt_output_get_mode(output);
	const char *FB_WIDTH = getenv("FB_WIDTH");
	const char *FB_HEIGHT = getenv("FB_HEIGHT");

	const size_t requested_fb_width = FB_WIDTH ?
		(size_t)atoi(FB_WIDTH) :
		mode->hdisplay;

	const size_t requested_fb_height = FB_HEIGHT ?
		(size_t)atoi(FB_HEIGHT) :
		mode->vdisplay;

	igt_display_commit2(p->display, COMMIT_ATOMIC);

	struct igt_fb fb;
	create_dumb_fb(p->display, requested_fb_width, requested_fb_height, &fb);

	for (size_t i = 0; i < tuning->num_fbs; ++i)
	{
		igt_plane_t *const plane = plane_for_index(p, i);
		igt_plane_set_prop_value(plane, IGT_PLANE_ZPOS, i);
		igt_plane_set_fb(plane, &fb);

		int ret = igt_display_try_commit_atomic(p->display,
							DRM_MODE_ATOMIC_TEST_ONLY,
							NULL);

		if (ret)
		{
			tuning->fb_geom[i].width = mode->hdisplay;
			tuning->fb_geom[i].height = mode->vdisplay;
		}
		else
		{
			tuning->fb_geom[i].width = requested_fb_width;
			tuning->fb_geom[i].height = requested_fb_height;
		}

		igt_info("Plane %zu is %zux%zu\n", i,
			 tuning->fb_geom[i].width,
			 tuning->fb_geom[i].height);

		igt_plane_set_fb(plane, NULL);
	}

	igt_remove_fb(p->display->drm_fd, &fb);
}

igt_main
{
	igt_display_t display = {};
	make_display(&display);

	igt_pipe_t *p = NULL;
	igt_output_t *output = NULL;
	get_pipe(&display, &p, &output);

	do_or_die(drmSetClientCap(
		display.drm_fd,
		DRM_CLIENT_CAP_ATOMIC,
		1));

	do_or_die(drmSetClientCap(
		display.drm_fd,
		DRM_CLIENT_CAP_UNIVERSAL_PLANES,
		1));

	igt_pipe_refresh(&display, p->pipe, true);

	prepare(&display, p, output);

	drmModeModeInfoPtr orig_mode = igt_output_get_mode(output);

	struct tuning tuning;
	get_tuning(&tuning, &display, p, output);

	{
		struct igt_fb **fb_sets =
				malloc(sizeof(struct igt_fb*[tuning.num_fb_sets]));

		for (size_t i = 0; i < tuning.num_fb_sets; ++i)
		{
			fb_sets[i] = malloc(sizeof(struct igt_fb[tuning.num_fbs]));
			struct igt_fb *fbs = fb_sets[i];
			for (size_t j = 0; j < tuning.num_fbs; ++j)
			{
				create_dumb_fb(&display,
					       tuning.fb_geom[j].width,
					       tuning.fb_geom[j].height,
					       &fbs[j]);
			};
		}


		repeat_flip(p, fb_sets, &tuning);

		for (size_t i = 0; i < tuning.num_fb_sets; ++i)
		{
			struct igt_fb *fbs = fb_sets[i];
			for (size_t j = 0; j < tuning.num_fbs; ++j)
			{
				igt_remove_fb(display.drm_fd, &fbs[j]);
			};
			free(fbs);
		}
		free(fb_sets);
	}

	if (orig_mode != tuning.mode)
	{
		igt_output_override_mode(output, orig_mode);
		igt_display_commit2(&display, COMMIT_ATOMIC);
	}

	igt_info("Success\n");
}
