/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#pragma once

#include <android/log.h>

#include <sstream>

extern std::ostream aout;
extern std::ostream aerr;

/*!
 * Use this class to create an output stream that writes to logcat. By default, a global one is
 * defined as @a aout
 */
class AndroidOut : public std::stringbuf {
public:
    /*!
     * Creates a new output stream for logcat
     * @param kLogTag the log tag to output
     */
    inline AndroidOut(const char* logTag, android_LogPriority priority)
          : mLogTag(logTag), mPriority(priority) {}

protected:
    virtual int sync() override {
        __android_log_print(mPriority, mLogTag, "%s", str().c_str());

        str("");
        return 0;
    }

private:
    const char* mLogTag;
    android_LogPriority mPriority;
};
