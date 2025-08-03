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

#pragma once

#include <regex>
#include <string>

namespace dmesg_parser {

class DmesgParser {
  public:
    DmesgParser();
    void ProcessLine(const std::string& line);
    bool ReportReady() const;
    std::string ReportType() const;
    std::string ReportTitle() const;
    std::string FlushReport();

  private:
    std::string StripSensitiveData(const std::string& line) const;

    bool report_ready_;
    std::string last_report_;
    std::regex bug_pattern_, ignore_pattern_, addr64_pattern_;
    std::regex task_line_pattern_, task_delimiter_pattern_;
    std::string current_report_;
    std::string current_task_, current_tool_, current_title_;
};

}  // namespace dmesg_parser
