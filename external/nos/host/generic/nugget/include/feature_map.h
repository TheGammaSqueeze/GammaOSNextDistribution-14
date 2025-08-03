/**
 * \file
 * Feature ID format and inline decode functions
 */

#pragma once

/*****************************************************************************/

#define TA_MASK 0xFF000000
#define TA_OFFSET 24
#define TA_FIELD 8 // Max 256 TAs

#define FEATURE_MASK 0x00FFFFFF
#define FEATURE_OFFSET 0
#define FEATURE_FIELD 24 // Can support up to 2^24 features

#define TA_FROM_FEATURE_ID(id)                                                 \
	((enum feature_support_app_id)((id & TA_MASK) >> TA_OFFSET))
#define MODULE_FROM_FEATURE_ID(id) ((id & FEATURE_MASK) >> FEATURE_OFFSET)
/*****************************************************************************/

enum feature_support_app_id {
  feature_id_avb = 0,
  feature_id_gfa = 1,
  feature_id_identity = 2,
  feature_id_keymint = 3,
  feature_id_nugget = 4,
  feature_id_weaver = 5,

  /* Please do not change numbers after they've been released */

  feature_id_count,       // used in sparse lookup table
  feature_id_max = 0xff,  // 8-bit TA_FIELD
};
static_assert(feature_id_count <= feature_id_max,
              "Too many enum feature_support_app_id values");

enum km_feature_list {
  km_feature_individual_attest = 0,
  km_feature_batch_attest = 1,
  km_feature_gnubby_attest = 2,
  km_feature_rkp = 3,
  km_feature_rkp_dice = 4,
  km_feature_dice = 5,
  km_feature_multimei = 6,

  /* Please do not change numbers after they've been released */

  km_feature_max = FEATURE_MASK,  // 24-bit FEATURE_FIELD
};

enum nugget_feature_list {
  nugget_feature_test_image = 0,

  /* Please do not change numbers after they've been released */

  nugget_feature_max = FEATURE_MASK,  // 24-bit FEATURE_FIELD
};

enum avb_feature_list {
  avb_feature_api_no_proto = 0,

  /* Please do not change numbers after they've been released */

  avb_feature_max = FEATURE_MASK,  // 24-bit FEATURE_FIELD
};

enum weaver_feature_list {
  weaver_feature_api_no_proto = 0,

  /* Please do not change numbers after they've been released */

  weaver_feature_max = FEATURE_MASK,  // 24-bit FEATURE_FIELD
};
