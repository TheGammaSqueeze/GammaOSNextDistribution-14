#include <stdio.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include <errno.h>
#include <getopt.h>
#include <stdbool.h>
#include <limits.h>
#include <string.h>
#include <inttypes.h>
#include <linux/mfd/adnc/iaxxx-module.h>

#define LOG_TAG "ia_get_pwr_stats"

#include <log/log.h>

#define GETOPT_HELP_CHAR (CHAR_MIN - 2)

#define DEV_NODE "/dev/iaxxx-module-celldrv"

struct ia_pwr_mgr {
    FILE *dev_node;
};

static struct option const long_options[] =
{
    {"cumulative count", no_argument, NULL, 'c'},
    {"help", no_argument, NULL, GETOPT_HELP_CHAR},
    {NULL, 0, NULL, 0}
};

void usage() {
    fputs("\
    USAGE -\n\
    -------\n\
    1) get_pwr_stats -c\n\
    \n\
    get pwr stats with all cumulative counters for each\n\
    mpll and apll clock freq values\n\
    ", stdout);

    fputs("\n\
    OPTIONS - \n\
    ---------\n\
    -c     get pwr stats for each mpll and apll clk values.\n\
    ", stdout);

    exit(EXIT_FAILURE);
}

int main(int argc, char *argv[]) {
    struct ia_pwr_mgr * pwr = NULL;
    struct iaxxx_pwr_stats pwr_stats_count;
    char use_case;
    int getchr;
    int i = 0;
    int err = 0;

    if (argc <= 1) {
        usage();
    }

    while ((getchr = getopt_long (argc, argv, "c", long_options, NULL)) != -1) {
        switch (getchr) {
        case 'c':
            use_case = 'c';
         break;
        case GETOPT_HELP_CHAR:
        default:
            usage();
        break;
        }
    }

    pwr = (struct ia_pwr_mgr*) malloc(sizeof(struct ia_pwr_mgr));
    if (NULL == pwr) {
        ALOGE("ERROR Failed to allocated memory for ia_pwr_mgr");
        fprintf(stderr, "ERROR Failed to allocated memory for ia_pwr_mgr");
        return -ENOMEM;
    }

    if((pwr->dev_node = fopen(DEV_NODE, "r")) == NULL) {
        ALOGE("%s: ERROR file %s open for write error: %s\n",
                __func__, DEV_NODE, strerror(errno));
        err = -EBADFD;
        fprintf(stderr, "%s: ERROR file %s open for write error: %s\n", __func__, DEV_NODE, strerror(errno));
        goto out;
    }

    if ('c' == use_case) {
        err = ioctl(fileno(pwr->dev_node), IAXXX_POWER_STATS_COUNT, &pwr_stats_count);
        if (0 != err) {
            ALOGE("ERROR: POWER STATS COUNT failed %d(%s)", errno, strerror(errno));
            fprintf(stderr, "ERROR: POWER STATS COUNT failed %d(%s)", errno, strerror(errno));
            goto out;
        }

        for (i = 0; i < NUM_MPLL_CLK_FREQ; i++){
            ALOGI("mpll_cumulative_cnts (%d) counter value: %"PRIu64"\n", i, pwr_stats_count.mpll_cumulative_cnts[i]);
            fprintf(stdout, "mpll_cumulative_cnts (%d) counter value: %"PRIu64"\n", i, pwr_stats_count.mpll_cumulative_cnts[i]);
        }

        for (i = 0; i < NUM_APLL_CLK_FREQ; i++){
            ALOGI("apll_cumulative_cnts (%d) counter value: %"PRIu64"\n", i, pwr_stats_count.apll_cumulative_cnts[i]);
            fprintf(stdout, "apll_cumulative_cnts (%d) counter value: %"PRIu64"\n", i, pwr_stats_count.apll_cumulative_cnts[i]);
        }

        for (i = 0; i < NUM_MPLL_CLK_FREQ; i++){
            ALOGI("mpllCumulativeDur (%d) Duration  value: %" PRIu64 "\n", i, pwr_stats_count.mpllCumulativeDur[i]);
            fprintf(stdout, "mpllCumulativeDur (%d) Duration  value %" PRIu64 "\n", i, pwr_stats_count.mpllCumulativeDur[i]);
        }

        for (i = 0; i < NUM_MPLL_CLK_FREQ; i++){
            ALOGI("mpllTimeStamp (%d) Last time stamp value : %" PRIu64 "\n", i, pwr_stats_count.mpllTimeStamp[i]);
            fprintf(stdout, "mpllTimeStamp (%d) Last time stamp value : %"PRIu64"\n", i, pwr_stats_count.mpllTimeStamp[i]);
        }

        ALOGI("sleepModeTimeStamp, Last time stamp value for Sleep : %" PRIu64 "\n", pwr_stats_count.sleepModeTimeStamp);
        fprintf(stdout, "sleepModeTimeStamp, Last time stamp value for Sleep : %" PRIu64"\n", pwr_stats_count.sleepModeTimeStamp);

        ALOGI("sleepModeCumulativeDur, Total cumulative sleep duration : %"PRIu64"\n", pwr_stats_count.sleepModeCumulativeDur);
        fprintf(stdout, "sleepModeCumulativeDur, Total cumulative sleep duration : %"PRIu64"\n", pwr_stats_count.sleepModeCumulativeDur);

	ALOGI("POWER STATS COUNT success\n");
        fprintf(stdout, "POWER STATS COUNT success\n");
    }

out:
    if (pwr) {
        if (pwr->dev_node) {
            fclose(pwr->dev_node);
        }
        free(pwr);
        pwr = NULL;
    }

    return err;
}
