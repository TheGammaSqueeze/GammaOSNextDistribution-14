#include <iostream>
#include <fstream>

#define EXIT_SUCCESS 0
#define EXIT_FAILURE 1
#define EXIT_VULNERABLE 113

int main(int argc, char *argv[]) {
    if (argc == 2) {
        std::string expected = "memory_corrupt";
        if (expected.compare(argv[1]) != 0) {
            std::cout << "unknown command" << std::endl;
            return EXIT_FAILURE;
        }
        std::cout << "attempting a memory access violation" << std::endl;
        *((unsigned int*)0x00000074726630b0) = 0xBAD;
        return EXIT_SUCCESS;
    }

    if (argc != 3) {
        std::cout << "unknown commands" << std::endl;
        return EXIT_FAILURE;
    }

    std::ifstream f(argv[1]);
    if (f.is_open()) {
        // the host test can either check exit code or stdout
        std::cout << "Hello " << f.rdbuf() << "! " << argv[2] << std::endl;
        // please don't use a test-controlled value in a security report
        std::string expected = "secure";
        if (expected.compare(argv[2]) != 0) {
            return EXIT_VULNERABLE;
        } else {
            return EXIT_SUCCESS;
        }
    } else {
        std::cout << "could not open file" << std::endl;
        return EXIT_FAILURE;
    }
}
