#include <iostream>

// Forward declaration because we don't have a proper header file
// for the dummy shared lib.
void shared_lib_2_func(const char* name);

int main() {
    std::cout << "Hello, world!" << std::endl;
    shared_lib_2_func("world");
    return 0;
}
