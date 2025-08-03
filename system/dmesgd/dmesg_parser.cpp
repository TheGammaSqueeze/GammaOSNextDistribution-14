/*
 * Copyright 2022, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <regex>
#include <string>

#include "dmesg_parser.h"

namespace dmesg_parser {

const std::string kTimestampRe = "^\\[[^\\]]+\\]\\s";

DmesgParser::DmesgParser() : report_ready_(false) {
    std::string bug_types;
    for (auto t : {"KFENCE", "KASAN"}) {
        if (bug_types.empty()) {
            bug_types = t;
        } else {
            bug_types.append("|");
            bug_types.append(t);
        }
    }
    std::string bug_re = kTimestampRe + "\\[([0-9T\\s]+)\\]\\s(BUG: (" + bug_types + "):.*)";
    this->bug_pattern_ = std::regex(bug_re);
    this->ignore_pattern_ = std::regex("([ _][Rx]..|raw): [0-9a-f]{16}|"
                                       "Hardware name:|Comm:");
    this->addr64_pattern_ = std::regex("\\b(?:0x)?[0-9a-f]{16}\\b");
}

/*
 * Read a single line terminated by a newline, and process it as follows:
 * 1. If we haven't seen a bug header, skip the current line unless it contains
 *    "BUG:".
 *    If it does, parse the line to extract the task ID (T1234), tool name
 *    (KASAN or KFENCE) and the whole report title (needed for report
 *    deduplication).
 * 2. If the current line does not contain the known task ID, skip it.
 * 3. If the current line contains a delimiter ("====="), stop accepting new
 *    lines.
 * 4. Otherwise strip potential sensitive data from the current line and append
 *    it to the report.
 */
void DmesgParser::ProcessLine(const std::string& line) {
    if (report_ready_) return;

    // We haven't encountered a BUG: line yet.
    if (current_report_.empty()) {
        std::smatch m;
        if (std::regex_search(line, m, bug_pattern_)) {
            std::string task_re = kTimestampRe + "\\[" + std::string(m[1]) + "\\]\\s";
            task_line_pattern_ = std::regex(task_re);
            task_delimiter_pattern_ = std::regex(task_re + "={10,}");
            current_title_ = m[2];
            current_tool_ = m[3];
            current_report_ = this->StripSensitiveData(line);
        }
        return;
    }

    // If there is a delimiter, mark the current report as ready.
    if (std::regex_search(line, task_delimiter_pattern_)) {
        report_ready_ = true;
        return;
    }

    if (std::regex_search(line, task_line_pattern_)) current_report_ += StripSensitiveData(line);
}

/*
 * Return true iff the current report is ready (it was terminated by the "====="
 * delimiter.
 */
bool DmesgParser::ReportReady() const {
    return report_ready_;
}

/*
 * Return the tool that generated the currently collected report.
 */
std::string DmesgParser::ReportType() const {
    return current_tool_;
}

/*
 * Return the title of the currently collected report.
 */
std::string DmesgParser::ReportTitle() const {
    return current_title_;
}

/*
 * Return the report collected so far and reset the parser.
 */
std::string DmesgParser::FlushReport() {
    report_ready_ = false;
    return std::move(current_report_);
}

/*
 * Strip potentially sensitive data from the reports by performing the
 * following actions:
 *  1. Drop the entire line, if it contains a process name:
 *       [   69.547684] [ T6006]c7   6006  CPU: 7 PID: 6006 Comm: sh Tainted:
 *
 *     or hardware name:
 *       [   69.558923] [ T6006]c7   6006  Hardware name: Phone1
 *
 *     or a memory dump, e.g.:
 *
 *        ... raw: 4000000000010200 0000000000000000 0000000000000000
 *
 *      or register dump:
 *
 *        ... RIP: 0033:0x7f96443109da
 *        ... RSP: 002b:00007ffcf0b51b08 EFLAGS: 00000202 ORIG_RAX: 00000000000000af
 *        ... RAX: ffffffffffffffda RBX: 000055dc3ee521a0 RCX: 00007f96443109da
 *
 *      (on x86_64)
 *
 *        ... pc : lpm_cpuidle_enter+0x258/0x384
 *        ... lr : lpm_cpuidle_enter+0x1d4/0x384
 *        ... sp : ffffff800820bea0
 *        ... x29: ffffff800820bea0 x28: ffffffc2305f3ce0
 *        ... ...
 *        ... x9 : 0000000000000001 x8 : 0000000000000000
 *
 *      (on ARM64)
 *
 *  2. For substrings that are known to be followed by sensitive information,
 *     cut the line after those substrings and append "DELETED\n",
 *     e.g. " by task ":
 *        ... Read at addr f0ffff87c23fdf7f by task sh/9971
 *     and "Corrupted memory at":
 *        ... Corrupted memory at 0xf0ffff87c23fdf00 [ ! . . . . . . . . . . . . . . . ]
 *
 *  3. Replace all strings that look like 64-bit hexadecimal values, with
 *     XXXXXXXXXXXXXXXX.
 */
std::string DmesgParser::StripSensitiveData(const std::string& line) const {
    if (std::regex_search(line, ignore_pattern_)) return "";

    std::string ret = line;
    for (std::string infix : {"Corrupted memory at ", " by task "}) {
        auto pos = ret.find(infix);
        if (pos != std::string::npos) {
            ret = ret.substr(0, pos + infix.size()) + "DELETED\n";
        }
    }
    ret = std::regex_replace(ret, addr64_pattern_, "XXXXXXXXXXXXXXXX");
    return ret;
}

}  // namespace dmesg_parser
