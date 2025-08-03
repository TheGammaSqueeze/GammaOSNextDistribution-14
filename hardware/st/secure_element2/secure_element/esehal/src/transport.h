/*****************************************************************************
 * Copyright ©2017-2019 Gemalto – a Thales Company. All rights Reserved.
 *
 * This copy is licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *     http://www.apache.org/licenses/LICENSE-2.0 or https://www.apache.org/licenses/LICENSE-2.0.html
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.

 ****************************************************************************/

/**
 * @file
 * $Author$
 * $Revision$
 * $Date$
 *
 * Interface to transport to transparently send/receive data.
 *
 */

#ifndef TRANSPORT_H
#define TRANSPORT_H

struct se_gto_ctx;
struct t1_state;

int transport_setup(struct se_gto_ctx *ctx);
int transport_teardown(struct se_gto_ctx *ctx);
int block_send(struct t1_state *t1, const void *block, size_t n);
int block_recv(struct t1_state *t1, void *block, size_t n);

#endif /* TRANSPORT_H */
