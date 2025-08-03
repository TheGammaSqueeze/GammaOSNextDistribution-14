/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include <util.h>
#include <leds_gpio.h>
#include <plat/gpio.h>

static uint32_t leds[] = { GPIO_PB(13), GPIO_PB(12), GPIO_PB(15), GPIO_PB(14) };

static const struct LedsGpio mStmLedsGpioBoardCfg = {
    .leds_array = leds,
    .num = ARRAY_SIZE(leds),
};

const struct LedsGpio *ledsGpioBoardCfg(void)
{
    return &mStmLedsGpioBoardCfg;
}
