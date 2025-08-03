#include <foo/BpFoo.h>

int main() {
    // Call boilerplate implementation of proxy (e.g. BpFoo)
    foo::BpFoo* bf = new foo::BpFoo(nullptr);
    bf->doFoo();
    return 0;
}
