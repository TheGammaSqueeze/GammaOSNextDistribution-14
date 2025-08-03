#ifndef __EXYNOS_SYNC_FENCE__
#define __EXYNOS_SYNC_FENCE__

#define SYNC_IOC_MAGIC      '>'
#define SYNC_IOC_FENCE_NAME    _IOWR(SYNC_IOC_MAGIC, 10, char[32])

#endif

