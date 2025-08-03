#include <array>

#include "TaskProcessor.h"

/**
 * Sets all entries of the buffer to a value that depends on its coordinate and a delta.
 */
class SimpleTask : public android::renderscript::Task {
    uint8_t* mBuffer;
    uint8_t mDelta;
    virtual void processData(int threadIndex, size_t startX, size_t startY, size_t endX,
                             size_t endY);

   public:
    SimpleTask(uint8_t* buffer, size_t vectorSize, size_t sizeX, size_t sizeY, uint8_t delta)
        : Task{sizeX, sizeY, vectorSize, false, nullptr}, mBuffer{buffer}, mDelta{delta} {}
};

/**
 * Create a new value that's a function of the x, y coordinates and a delta.
 */
static uint8_t newValue(size_t x, size_t y, uint8_t delta) {
    return (((x & 0xff) << 4) | (y & 0xff)) + delta;
}

void SimpleTask::processData(int /*threadIndex*/, size_t startX, size_t startY, size_t endX,
                             size_t endY) {
    for (size_t y = startY; y < endY; y++) {
        for (size_t x = startX; x < endX; x++) {
            size_t index = (y * mSizeX + x) * mVectorSize;
            for (size_t i = 0; i < mVectorSize; i++) {
                // Use add to make sure the opertion is only done once. This assumes
                // the buffer starts set at 0.
                mBuffer[index + i] += newValue(x, y, mDelta + i);
            }
        }
    }
}

/**
 * Returns true if all the entries of the vector are the expected value.
 * Prints an error if not.
 */
bool verifyAllTheSame(const std::vector<uint8_t>& buffer, size_t vectorSize, size_t sizeX,
                      size_t sizeY, uint8_t delta) {
    for (size_t y = 0; y < sizeY; y++) {
        for (size_t x = 0; x < sizeX; x++) {
            size_t index = (y * sizeX + x) * vectorSize;
            for (size_t i = 0; i < vectorSize; i++) {
                uint8_t expectedValue = newValue(x, y, delta + i);
                if (buffer[index + i] != expectedValue) {
                    printf("Test Error at %zu, %zu. Expected %u found %u instead\n", x, y,
                           expectedValue, buffer[index + i]);
                    return false;
                }
            }
        }
    }
    return true;
}

/**
 * Create a buffer of the specified size, set each entry of that buffer
 * to the specified value using TaskProcessor, and verify the results.
 */
void testOne(android::renderscript::TaskProcessor* processor, uint8_t delta, size_t vectorSize,
             size_t sizeX, size_t sizeY) {
    std::vector<uint8_t> buffer(sizeX * sizeY * vectorSize);

    SimpleTask task{buffer.data(), vectorSize, sizeX, sizeY, delta};
    processor->doTask(&task);

    if (verifyAllTheSame(buffer, vectorSize, sizeX, sizeY, delta)) {
        printf("Test %u: All good!\n", delta);
    }
}

int main() {
    std::vector<std::thread> testThreads;

    // Test with multiple threads, to help find synchronization errors.
    android::renderscript::TaskProcessor processorA(1);
    android::renderscript::TaskProcessor processorB(4);
    testThreads.emplace_back(testOne, &processorA, 1, 4, 30, 40);
    testThreads.emplace_back(testOne, &processorB, 1, 4, 30, 40);
    testThreads.emplace_back(testOne, &processorA, 2, 4, 800, 600);
    testThreads.emplace_back(testOne, &processorB, 2, 4, 800, 600);
    testThreads.emplace_back(testOne, &processorA, 3, 1, 123, 47);
    testThreads.emplace_back(testOne, &processorB, 3, 1, 123, 47);
    testThreads.emplace_back(testOne, &processorA, 5, 2, 5000, 8000);
    testThreads.emplace_back(testOne, &processorB, 5, 2, 5000, 8000);
    testThreads.emplace_back(testOne, &processorA, 6, 3, 26000, 1);
    testThreads.emplace_back(testOne, &processorB, 6, 3, 26000, 1);
    testThreads.emplace_back(testOne, &processorA, 7, 4, 1, 26000);
    testThreads.emplace_back(testOne, &processorB, 7, 4, 1, 26000);
    testThreads.emplace_back(testOne, &processorA, 8, 4, 1000, 1000);
    testThreads.emplace_back(testOne, &processorB, 8, 4, 1000, 1000);
    testThreads.emplace_back(testOne, &processorA, 9, 1, 1, 1);
    testThreads.emplace_back(testOne, &processorB, 9, 1, 1, 1);

    for (auto& thread : testThreads) {
        thread.join();
    }
    return 0;
}
