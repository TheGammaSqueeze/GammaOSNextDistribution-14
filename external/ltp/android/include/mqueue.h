#ifndef __MQUEUE_H
#define __MQUEUE_H

#include <linux/mqueue.h>

#ifndef mqd_t
#define mqd_t __kernel_mqd_t
#endif

#endif /* __MQUEUE_H */
