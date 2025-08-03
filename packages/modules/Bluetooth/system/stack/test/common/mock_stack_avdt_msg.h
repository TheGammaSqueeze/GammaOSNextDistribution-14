/*
 * Copyright 2020 The Android Open Source Project
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

#include <cstddef>
#include <cstdint>

/**
 * Get how many responses sent
 *
 * @return the count of responses.
 */
size_t mock_avdt_msg_send_rsp_get_count(void);

/**
 * Clear all the history of the sent responses.
 */
void mock_avdt_msg_send_rsp_clear_history(void);

/**
 * Get the nth (zero based) response's sig id
 *
 * @param nth response recorded since last time clear history
 * @return the sig id of the response
 *
 * @note undefined behavior if nth >= total count
 */
uint8_t mock_avdt_msg_send_rsp_get_sig_id_at(size_t nth);

/**
 * Get how many commands sent
 *
 * @return the count of commands.
 */
size_t mock_avdt_msg_send_cmd_get_count(void);

/**
 * Clear all the history of the sent commands.
 */
void mock_avdt_msg_send_cmd_clear_history(void);

/**
 * Get the nth (zero based) commands's sig id
 *
 * @param nth command recorded since last time clear history
 * @return the sig id of the command
 *
 * @note undefined behavior if nth >= total count
 */
uint8_t mock_avdt_msg_send_cmd_get_sig_id_at(size_t nth);
