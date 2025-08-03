#include <fstream>
#include <sstream>
#include <regex>
#include <vector>
#include <algorithm>

#include <cerrno>
#include <sys/stat.h>

#include <hardware/memtrack.h>
#include <hardware/exynos/ion.h>

#include "memtrack_exynos.h"

using namespace std;

#define NUM_AVAILABLE_FLAGS 4U
static unsigned int available_flags [NUM_AVAILABLE_FLAGS] = {
    MEMTRACK_FLAG_SMAPS_UNACCOUNTED | MEMTRACK_FLAG_SHARED_PSS | MEMTRACK_FLAG_SYSTEM | MEMTRACK_FLAG_NONSECURE,
    MEMTRACK_FLAG_SMAPS_UNACCOUNTED | MEMTRACK_FLAG_SHARED_PSS | MEMTRACK_FLAG_DEDICATED | MEMTRACK_FLAG_NONSECURE,
    MEMTRACK_FLAG_SMAPS_UNACCOUNTED | MEMTRACK_FLAG_SHARED_PSS | MEMTRACK_FLAG_SYSTEM | MEMTRACK_FLAG_SECURE,
    MEMTRACK_FLAG_SMAPS_UNACCOUNTED | MEMTRACK_FLAG_SHARED_PSS | MEMTRACK_FLAG_DEDICATED| MEMTRACK_FLAG_SECURE,
};

struct DmabufBuffer {
    unsigned int id;
    unsigned int type;
    size_t size;
    size_t pss;
    DmabufBuffer(unsigned int _id, size_t _size, size_t _pss)
        : id(_id), type(MEMTRACK_FLAG_SMAPS_UNACCOUNTED | MEMTRACK_FLAG_SHARED_PSS), size(_size), pss(_pss)
    { }
    void setPoolType(string _type) { type |= (_type == "carveout") ? MEMTRACK_FLAG_DEDICATED : MEMTRACK_FLAG_SYSTEM; }
    void setFlags(unsigned int flags) { type |= (flags & ION_FLAG_PROTECTED) ? MEMTRACK_FLAG_SECURE : MEMTRACK_FLAG_NONSECURE; }
};

const char DMABUF_FOOTPRINT_PATH[] = "/sys/kernel/debug/dma_buf/footprint/";
static bool build_dmabuf_footprint(vector<DmabufBuffer> &buffers, pid_t pid)
{
    ostringstream dmabuf_path;
    dmabuf_path << DMABUF_FOOTPRINT_PATH << pid;

    ifstream dmabuf(dmabuf_path.str());
    if (!dmabuf)
        return false;
    //
    // exp_name      size     share
    // ion-102   69271552  34635776
    regex rex("\\s*ion-(\\d+)\\s+(\\d+)\\s+(\\d+).*");
    smatch mch;
    // 1-id, 2-size, 3-pss
    for (string line; getline(dmabuf, line); )
        if (regex_match(line, mch, rex))
            buffers.emplace_back(stoul(mch[1], 0, 10), stoul(mch[2], 0, 10), stoul(mch[3], 0, 10));

    return true;
}

const char ION_BUFFERS_PATH[] = "/sys/kernel/debug/ion/buffers";
static bool complete_dmabuf_footprint(int type, vector<DmabufBuffer> &buffers)
{
    ifstream ion(ION_BUFFERS_PATH);
    if (!ion)
        return false;

    // [  id]            heap heaptype flags size(kb) : iommu_mapped...
    // [ 106] ion_system_heap   system  0x40    16912 : 19080000.dsim(0)
    regex rexion("\\[ *(\\d+)\\] +[[:alnum:]\\-_]+ +(\\w+) +([x[:xdigit:]]+) +(\\d+).*");
    smatch mch;
    // 1-id, 2-heaptype, 3-flags, 4-size
    for (string line; getline(ion, line); ) {
        if (regex_match(line, mch, rexion)) {
            unsigned int id = stoul(mch[1], 0, 10);
            unsigned int flags = stoul(mch[3], 0, 16);
            size_t len = stoul(mch[4], 0, 10) * 1024;
            auto elem = find_if(begin(buffers), end(buffers), [id, len] (auto &item) {
                                    return (item.id == id) && (item.size == len);
                                });
            // passes if type = OTHER && not flag & hwrender or type == GRAPHIC && flag & hwrender
            if ((elem != end(buffers)) && ((type == MEMTRACK_TYPE_OTHER) == !(flags & ION_FLAG_MAY_HWRENDER))) {
                elem->setFlags(flags);
                elem->setPoolType(mch[2]);
            }
        }
    }

    return true;
}

int dmabuf_memtrack_get_memory(pid_t pid, int type, struct memtrack_record *records, size_t *num_records)
{
    if ((type != MEMTRACK_TYPE_OTHER) && (type != MEMTRACK_TYPE_GRAPHICS))
        return -ENODEV;

    if (*num_records == 0) {
        *num_records = NUM_AVAILABLE_FLAGS;
        return 0;
    }

    *num_records = (*num_records < NUM_AVAILABLE_FLAGS) ? *num_records : NUM_AVAILABLE_FLAGS;

    for (size_t i = 0; i < *num_records; i++) {
        records[i].size_in_bytes = 0;
        records[i].flags = available_flags[i];
    }

    vector<DmabufBuffer> buffers;

    if (!build_dmabuf_footprint(buffers, pid))
        return -ENODEV;

    if (buffers.size() == 0)
        return 0;

    if (!complete_dmabuf_footprint(type, buffers))
        return -ENODEV;

    for (auto item: buffers) {
        for (size_t i = 0; i < *num_records; i++) {
            if (item.type == available_flags[i]) {
                records[i].size_in_bytes += item.pss;
                break;
            }
        }
    }

    return 0;
}
