#include "xdf_io.h"

/* plain io */
#define NO_PRIV 1
#define SKIP_PARTITION 2
#define ALWAYS_GET_GEOMETRY 4

Stream_t *OpenImage(struct device *out_dev, struct device *dev,
		    const char *name, int mode, char *errmsg,
		    int flags, int lockMode,
		    mt_off_t *maxSize, int *geomFailureP,
#ifdef USE_XDF
		    struct xdf_info *xdf_info
#else
		    void *dummy
#endif
		    );
