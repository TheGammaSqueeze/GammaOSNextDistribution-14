#include <sys/system_properties.h>

void testlib_sub() {
    __system_property_set("debug.microdroid.app.sublib.run", "true");
}
