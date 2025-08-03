/*
 * Copyright 2021 The Android Open Source Project
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

#include <audio_utils/ChannelMix.h>

#include <random>
#include <vector>

#include <benchmark/benchmark.h>
#include <log/log.h>

static constexpr audio_channel_mask_t kChannelPositionMasks[] = {
    AUDIO_CHANNEL_OUT_FRONT_LEFT,
    AUDIO_CHANNEL_OUT_FRONT_CENTER,
    AUDIO_CHANNEL_OUT_STEREO,
    AUDIO_CHANNEL_OUT_2POINT1,
    AUDIO_CHANNEL_OUT_2POINT0POINT2,
    AUDIO_CHANNEL_OUT_QUAD, // AUDIO_CHANNEL_OUT_QUAD_BACK
    AUDIO_CHANNEL_OUT_QUAD_SIDE,
    AUDIO_CHANNEL_OUT_SURROUND,
    AUDIO_CHANNEL_OUT_2POINT1POINT2,
    AUDIO_CHANNEL_OUT_3POINT0POINT2,
    AUDIO_CHANNEL_OUT_PENTA,
    AUDIO_CHANNEL_OUT_3POINT1POINT2,
    AUDIO_CHANNEL_OUT_5POINT1, // AUDIO_CHANNEL_OUT_5POINT1_BACK
    AUDIO_CHANNEL_OUT_5POINT1_SIDE,
    AUDIO_CHANNEL_OUT_6POINT1,
    AUDIO_CHANNEL_OUT_5POINT1POINT2,
    AUDIO_CHANNEL_OUT_7POINT1,
    AUDIO_CHANNEL_OUT_5POINT1POINT4,
    AUDIO_CHANNEL_OUT_7POINT1POINT2,
    AUDIO_CHANNEL_OUT_7POINT1POINT4,
    AUDIO_CHANNEL_OUT_9POINT1POINT6,
    AUDIO_CHANNEL_OUT_13POINT_360RA,
    AUDIO_CHANNEL_OUT_22POINT2,
};

/*
$ adb shell /data/benchmarktest64/channelmix_benchmark/channelmix_benchmark
Pixel 7 arm64 benchmark

------------------------------------------------------------------------------------------------
Benchmark                                    Time                      CPU             Iteration
------------------------------------------------------------------------------------------------
channelmix_benchmark:
  #BM_ChannelMix_Stereo/0             1584.624127461959 ns    1577.8786910297354 ns       443677
  #BM_ChannelMix_Stereo/1             1584.678016063811 ns    1577.5935446358542 ns       443724
  #BM_ChannelMix_Stereo/2             257.8439968596911 ns    256.73377516518275 ns      2727131
  #BM_ChannelMix_Stereo/3            2817.8586154104814 ns    2805.4497511201575 ns       249518
  #BM_ChannelMix_Stereo/4             3454.621091622776 ns     3438.280971421562 ns       203825
  #BM_ChannelMix_Stereo/5             814.5202073907247 ns     810.8345646578592 ns       863298
  #BM_ChannelMix_Stereo/6             814.6591305879139 ns     810.8376574553886 ns       863181
  #BM_ChannelMix_Stereo/7            3450.1116802072984 ns     3435.144113779975 ns       203832
  #BM_ChannelMix_Stereo/8            4075.6687216808855 ns     4056.735656426362 ns       172586
  #BM_ChannelMix_Stereo/9            4075.1493397336594 ns    4056.1154684474764 ns       172506
  #BM_ChannelMix_Stereo/10            4050.552323304721 ns     4031.185599428678 ns       173632
  #BM_ChannelMix_Stereo/11             4772.08328528545 ns    4749.7996554086585 ns       147421
  #BM_ChannelMix_Stereo/12           1207.4669943988806 ns     1201.779749169794 ns       582386
  #BM_ChannelMix_Stereo/13           1206.6310224396407 ns    1201.2170626850327 ns       582675
  #BM_ChannelMix_Stereo/14             5333.77644088611 ns     5307.639342522252 ns       131898
  #BM_ChannelMix_Stereo/15           1305.1935398263006 ns    1299.0954717541388 ns       538840
  #BM_ChannelMix_Stereo/16           1305.1100692447556 ns    1299.4297640484895 ns       538670
  #BM_ChannelMix_Stereo/17            1886.552455306027 ns     1878.964744321161 ns       372479
  #BM_ChannelMix_Stereo/18           1833.9518923822216 ns      1825.28045911661 ns       383432
  #BM_ChannelMix_Stereo/19           2123.2422014413596 ns     2113.312087275008 ns       331183
  #BM_ChannelMix_Stereo/20            2566.000912328837 ns    2554.4080810439914 ns       274024
  #BM_ChannelMix_Stereo/21            9674.346659891693 ns     9572.201779926385 ns        72812
  #BM_ChannelMix_Stereo/22            7064.819541015039 ns     7033.133485390462 ns        99524
  #BM_ChannelMix_5Point1/0            3998.744585320616 ns     3979.878947907533 ns       175726
  #BM_ChannelMix_5Point1/1           3996.7273735393856 ns    3979.5646336261952 ns       175845
  #BM_ChannelMix_5Point1/2            571.1685188685985 ns     568.5066485512494 ns      1274789
  #BM_ChannelMix_5Point1/3            6859.186952421266 ns     6827.083100769158 ns       102839
  #BM_ChannelMix_5Point1/4            8247.552512539802 ns     8213.975847278873 ns        85332
  #BM_ChannelMix_5Point1/5            647.7379762376627 ns     644.8995942251229 ns      1095435
  #BM_ChannelMix_5Point1/6            653.6735856426786 ns     651.0027499009582 ns      1077857
  #BM_ChannelMix_5Point1/7             8239.63233966818 ns     8201.233626783234 ns        85307
  #BM_ChannelMix_5Point1/8            9876.205631112243 ns     9830.739673485414 ns        71176
  #BM_ChannelMix_5Point1/9            9895.919856358605 ns     9852.652964371222 ns        71010
  #BM_ChannelMix_5Point1/10           9926.404937439764 ns     9880.174425299469 ns        71211
  #BM_ChannelMix_5Point1/11          11098.554419676255 ns    11046.770994052376 ns        63387
  #BM_ChannelMix_5Point1/12           661.3799346937448 ns     658.2737776463655 ns      1075548
  #BM_ChannelMix_5Point1/13           660.2247365241512 ns        657.1922621923 ns      1074387
  #BM_ChannelMix_5Point1/14           12488.98408949619 ns    12429.793927017769 ns        56315
  #BM_ChannelMix_5Point1/15           785.5108530564086 ns     781.8289591958687 ns       903340
  #BM_ChannelMix_5Point1/16           786.4194859807196 ns     782.6872009662999 ns       894130
  #BM_ChannelMix_5Point1/17           1220.481377951544 ns    1214.7925379524959 ns       574534
  #BM_ChannelMix_5Point1/18          1033.1440404735665 ns    1025.1535907684895 ns       688251
  #BM_ChannelMix_5Point1/19          1384.1550536582333 ns    1378.3095904839972 ns       508869
  #BM_ChannelMix_5Point1/20          1859.6552118374757 ns    1851.2871329217771 ns       378734
  #BM_ChannelMix_5Point1/21           21042.00829614576 ns    20954.427595914778 ns        33389
  #BM_ChannelMix_5Point1/22           4865.180198686846 ns     4843.440710104699 ns       145556
  #BM_ChannelMix_7Point1/0            5086.403517096561 ns     5066.400356235592 ns       136988
  #BM_ChannelMix_7Point1/1             5092.64935733066 ns     5069.928712196951 ns       138018
  #BM_ChannelMix_7Point1/2            580.8386797597832 ns     578.1633211791726 ns      1211931
  #BM_ChannelMix_7Point1/3            8912.321896958316 ns     8873.011453286297 ns        78842
  #BM_ChannelMix_7Point1/4           10968.632097606318 ns     10917.41972469887 ns        63930
  #BM_ChannelMix_7Point1/5            726.8751572135141 ns     723.3895340485537 ns       967633
  #BM_ChannelMix_7Point1/6            10990.37087972071 ns    10939.177440982927 ns        64134
  #BM_ChannelMix_7Point1/7           10973.048671598453 ns    10913.438153663725 ns        64062
  #BM_ChannelMix_7Point1/8           12721.667251641265 ns    12664.338230238987 ns        55273
  #BM_ChannelMix_7Point1/9           12707.525889264853 ns    12654.736756227583 ns        55158
  #BM_ChannelMix_7Point1/10          12749.720973614516 ns    12689.723798830073 ns        55217
  #BM_ChannelMix_7Point1/11          14491.778839392775 ns     14434.37490176607 ns        48354
  #BM_ChannelMix_7Point1/12           737.7057427091167 ns     734.2313228110585 ns       963796
  #BM_ChannelMix_7Point1/13          14496.991651784358 ns    14422.233023217163 ns        48154
  #BM_ChannelMix_7Point1/14          16464.798606889286 ns     16389.21455284934 ns        42782
  #BM_ChannelMix_7Point1/15            559.213653594105 ns     556.9817588821725 ns      1277334
  #BM_ChannelMix_7Point1/16           566.1605467286886 ns     563.6613941659941 ns      1254370
  #BM_ChannelMix_7Point1/17          1253.3420120265812 ns    1248.0777030705242 ns       560261
  #BM_ChannelMix_7Point1/18           1108.042257269461 ns     1102.862052077511 ns       635015
  #BM_ChannelMix_7Point1/19           1527.825791177674 ns     1520.704571637185 ns       457451
  #BM_ChannelMix_7Point1/20          1824.6548966125815 ns    1816.7435915419753 ns       384960
  #BM_ChannelMix_7Point1/21           27557.99023414285 ns     27431.89951759037 ns        25497
  #BM_ChannelMix_7Point1/22            5616.79751878036 ns     5587.643648062816 ns       125261
  #BM_ChannelMix_7Point1Point4/0      7014.499259038577 ns     6984.192739569345 ns       100545
  #BM_ChannelMix_7Point1Point4/1     7021.7462064411075 ns     6988.617095503731 ns       100038
  #BM_ChannelMix_7Point1Point4/2      913.1760532582629 ns      908.506633272119 ns       770585
  #BM_ChannelMix_7Point1Point4/3     12477.598480005978 ns     12422.15004616809 ns        56316
  #BM_ChannelMix_7Point1Point4/4     15322.980915864659 ns    15255.479652302869 ns        45902
  #BM_ChannelMix_7Point1Point4/5     1084.1117429089002 ns    1079.0443395615782 ns       648766
  #BM_ChannelMix_7Point1Point4/6     15314.545660556396 ns     15241.66275902662 ns        45893
  #BM_ChannelMix_7Point1Point4/7     15290.398910794553 ns    15229.703583487835 ns        45905
  #BM_ChannelMix_7Point1Point4/8      17802.86192606147 ns    17706.310160968394 ns        39573
  #BM_ChannelMix_7Point1Point4/9     17793.085298720172 ns    17701.706681480868 ns        39602
  #BM_ChannelMix_7Point1Point4/10    17785.029805845286 ns     17695.70093032652 ns        39556
  #BM_ChannelMix_7Point1Point4/11    20452.726370331704 ns    20362.743108316856 ns        34353
  #BM_ChannelMix_7Point1Point4/12     905.1501997610313 ns     900.8145382372188 ns       778430
  #BM_ChannelMix_7Point1Point4/13    20452.494213333222 ns    20357.311517942882 ns        34303
  #BM_ChannelMix_7Point1Point4/14     22961.73918437959 ns     22856.77036601665 ns        30627
  #BM_ChannelMix_7Point1Point4/15     822.5476244151616 ns     818.6498375120065 ns       864987
  #BM_ChannelMix_7Point1Point4/16     827.1217072917217 ns     823.2904757872059 ns       850212
  #BM_ChannelMix_7Point1Point4/17     991.5142018416226 ns     986.8850542623812 ns       709239
  #BM_ChannelMix_7Point1Point4/18    1115.3075057537858 ns     1109.803063780115 ns       630463
  #BM_ChannelMix_7Point1Point4/19      910.203049595069 ns     906.0727563637828 ns       772496
  #BM_ChannelMix_7Point1Point4/20    1842.6523184706023 ns     1833.970990950399 ns       381674
  #BM_ChannelMix_7Point1Point4/21     38800.57224890828 ns     38630.07895753978 ns        18111
  #BM_ChannelMix_7Point1Point4/22      6308.58632322223 ns     6279.855444602969 ns       111459
  #BM_ChannelMix_9Point1Point6/0      9649.865710953989 ns      9607.98667947049 ns        72895
  #BM_ChannelMix_9Point1Point6/1       9648.56897947997 ns     9607.041150229887 ns        72855
  #BM_ChannelMix_9Point1Point6/2     1467.4483873457445 ns    1460.8920743784784 ns       479117
  #BM_ChannelMix_9Point1Point6/3     16716.733956963417 ns     16632.37615443196 ns        42012
  #BM_ChannelMix_9Point1Point6/4     20287.965075704346 ns    20193.316337419117 ns        34675
  #BM_ChannelMix_9Point1Point6/5     1464.4069860190825 ns    1457.8618415983076 ns       480159
  #BM_ChannelMix_9Point1Point6/6      20273.38667475504 ns    20177.981372379003 ns        34626
  #BM_ChannelMix_9Point1Point6/7     20287.996971267657 ns    20201.494432906646 ns        34668
  #BM_ChannelMix_9Point1Point6/8      23717.49644218307 ns    23606.660708865922 ns        29653
  #BM_ChannelMix_9Point1Point6/9     23693.305799054524 ns     23594.91264329071 ns        29660
  #BM_ChannelMix_9Point1Point6/10       23732.312119779 ns    23620.915066919006 ns        29588
  #BM_ChannelMix_9Point1Point6/11     27105.94758950138 ns    26974.348105900408 ns        25949
  #BM_ChannelMix_9Point1Point6/12    1173.9943372248606 ns    1168.4729043473424 ns       598823
  #BM_ChannelMix_9Point1Point6/13     27091.76928708911 ns    26973.336840077038 ns        25950
  #BM_ChannelMix_9Point1Point6/14     30555.80730795895 ns    30412.954379561914 ns        23016
  #BM_ChannelMix_9Point1Point6/15    1445.3035322833473 ns    1438.1497866413106 ns       486739
  #BM_ChannelMix_9Point1Point6/16    1083.7163404776202 ns     1078.846160485561 ns       648806
  #BM_ChannelMix_9Point1Point6/17    1105.8799540773066 ns    1100.7227664149027 ns       636723
  #BM_ChannelMix_9Point1Point6/18     1104.793673683923 ns    1099.5597121488488 ns       636579
  #BM_ChannelMix_9Point1Point6/19    1104.4723973028551 ns     1099.621290052968 ns       636532
  #BM_ChannelMix_9Point1Point6/20    1110.8329695491907 ns     1105.448990324432 ns       644613
  #BM_ChannelMix_9Point1Point6/21    51622.972821619114 ns     51378.92600612255 ns        13393
  #BM_ChannelMix_9Point1Point6/22     6397.061115862641 ns    6370.5975651920635 ns       109906

*/

template<audio_channel_mask_t OUTPUT_CHANNEL_MASK>
static void BenchmarkChannelMix(benchmark::State& state) {
    const audio_channel_mask_t channelMask = kChannelPositionMasks[state.range(0)];
    using namespace ::android::audio_utils::channels;
    ChannelMix<OUTPUT_CHANNEL_MASK> channelMix(channelMask);
    const size_t outChannels = audio_channel_count_from_out_mask(OUTPUT_CHANNEL_MASK);
    constexpr size_t frameCount = 1024;
    size_t inChannels = audio_channel_count_from_out_mask(channelMask);
    std::vector<float> input(inChannels * frameCount);
    std::vector<float> output(outChannels * frameCount);
    constexpr float amplitude = 0.01f;

    std::minstd_rand gen(channelMask);
    std::uniform_real_distribution<> dis(-amplitude, amplitude);
    for (auto& in : input) {
        in = dis(gen);
    }

    assert(channelMix.getInputChannelMask() != AUDIO_CHANNEL_NONE);
    // Run the test
    for (auto _ : state) {
        benchmark::DoNotOptimize(input.data());
        benchmark::DoNotOptimize(output.data());
        channelMix.process(input.data(), output.data(), frameCount, false /* accumulate */);
        benchmark::ClobberMemory();
    }

    state.SetComplexityN(inChannels);
    state.SetLabel(audio_channel_out_mask_to_string(channelMask));
}

static void BM_ChannelMix_Stereo(benchmark::State& state) {
    BenchmarkChannelMix<AUDIO_CHANNEL_OUT_STEREO>(state);
}

static void BM_ChannelMix_5Point1(benchmark::State& state) {
    BenchmarkChannelMix<AUDIO_CHANNEL_OUT_5POINT1>(state);
}

static void BM_ChannelMix_7Point1(benchmark::State& state) {
    BenchmarkChannelMix<AUDIO_CHANNEL_OUT_7POINT1>(state);
}

static void BM_ChannelMix_7Point1Point4(benchmark::State& state) {
    BenchmarkChannelMix<AUDIO_CHANNEL_OUT_7POINT1POINT4>(state);
}

static void BM_ChannelMix_9Point1Point6(benchmark::State& state) {
    BenchmarkChannelMix<AUDIO_CHANNEL_OUT_9POINT1POINT6>(state);
}

static void ChannelMixArgs(benchmark::internal::Benchmark* b) {
    for (int i = 0; i < (int)std::size(kChannelPositionMasks); i++) {
        b->Args({i});
    }
}

BENCHMARK(BM_ChannelMix_Stereo)->Apply(ChannelMixArgs);

BENCHMARK(BM_ChannelMix_5Point1)->Apply(ChannelMixArgs);

BENCHMARK(BM_ChannelMix_7Point1)->Apply(ChannelMixArgs);

BENCHMARK(BM_ChannelMix_7Point1Point4)->Apply(ChannelMixArgs);

BENCHMARK(BM_ChannelMix_9Point1Point6)->Apply(ChannelMixArgs);

BENCHMARK_MAIN();
