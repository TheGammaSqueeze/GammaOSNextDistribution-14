#ifndef SAMSUNG_DISPLAYPORT_FOR_HWC_H
#define SAMSUNG_DISPLAYPORT_FOR_HWC_H

struct exynos_displayport_data {
	enum {
		EXYNOS_DISPLAYPORT_STATE_PRESET = 0,
		EXYNOS_DISPLAYPORT_STATE_ENUM_PRESET,
		EXYNOS_DISPLAYPORT_STATE_RECONNECTION,
		EXYNOS_DISPLAYPORT_STATE_HDR_INFO,
		EXYNOS_DISPLAYPORT_STATE_AUDIO,
	} state;
	struct	v4l2_dv_timings timings;
	struct	v4l2_enum_dv_timings etimings;
	__u32	audio_info;
	int hdr_support;
};

#define EXYNOS_GET_DISPLAYPORT_CONFIG		_IOW('F', 300, \
						struct exynos_displayport_data)
#define EXYNOS_SET_DISPLAYPORT_CONFIG		_IOW('F', 301, \
						struct exynos_displayport_data)
#define EXYNOS_SET_HPD_FOR_TEST _IOW('F', 999, struct exynos_displayport_data)

#endif /* SAMSUNG_DISPLAYPORT_FOR_HWC_H */
