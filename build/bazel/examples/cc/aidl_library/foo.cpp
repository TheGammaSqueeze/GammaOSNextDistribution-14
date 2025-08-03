#include <foo/BpFoo.h>

namespace android {
    void main() {
        // Call boilerplate implementation of proxy (e.g. BpFoo)
        foo::BpFoo* bf = new foo::BpFoo(nullptr);
        bf->doFoo();
    };
}
