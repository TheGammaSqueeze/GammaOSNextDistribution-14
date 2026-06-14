/*
 * Copyright (C) 2026 GammaOS
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

// Canyon music visualizer - a 1:1 port of the web app's canyon_port.js terrain
// flythrough. The web samples the heightfield with a VERTEX texture fetch; nano
// runs a strict GLES2 context with no guaranteed vertex texture units, so the
// per-vertex terrain height is precomputed on the CPU from the decoded normalmap
// (the EXACT sampleHeight + valley formula) and uploaded as an aHeight attribute
// each frame. Everything else (camera, the 57-preset cross-fade cycle, the 3-pass
// terrain -> feedback motion-blur -> Reinhard tonemap pipeline, the audio
// reactivity) is the web logic unchanged. The tonemap pass composites to the panel
// with the DRM rotation + a cross-fade alpha (the SQUARE Waves<->Canyon toggle).

#include "NanoMenuMusicCanyon.h"
#include "NanoMenuPS3.h"

#include <math.h>
#include <stdio.h>
#include <string.h>
#include <vector>

#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <png.h>

#include <utils/Log.h>

namespace android {
namespace ps3canyon {

// ---------------------------------------------------------------------------
// Preset table (REAL values transcribed from presets.json, canyon_port.js).
// Column order matches CANYON_COLS.
// ---------------------------------------------------------------------------
enum {
    C_posX=0, C_posY, C_posZ, C_tarX, C_tarY, C_tarZ, C_speed, C_timeAhead, C_zoom,
    C_colR, C_colG, C_colB, C_colScale, C_colBias, C_lineR, C_lineG, C_lineB,
    C_fogMin, C_fogMax, C_fogR, C_fogG, C_fogB,
    C_tHeight, C_tWidth, C_tSlide, C_tScaleX, C_tTexScale, C_tBump,
    C_vHeight, C_vCentre, C_vWidth, C_vSharp,
    C_feedback, C_feedX, C_feedY, C_feedRot,
    C_focalDepth, C_focalVar, C_skyBlur, C_lod, C_fadeColour
};
static const int kCanyonNumPresets = 57;
static const int kCanyonNumCols = 41;
static const float kCanyonPresets[kCanyonNumPresets][kCanyonNumCols] = {
  {0.0f,8.0f,0.0f,0.0f,-3.0f,156.0f,20.0f,159.0f,65.0f,10.0f,10.0f,10.0f,0.0f,0.0f,0.0f,0.0f,0.0f,30.0f,70.0f,0.0f,0.0f,0.0f,1.0f,1.0f,1.0f,1.0f,10.0f,0.0f,0.0f,0.0f,0.0f,0.0f,0.9f,1.0f,1.0f,0.0f,50.0f,50.0f,0.0f,0.0f,0.0f},
  {-40.0f,20.0f,55.0f,25.0f,-50.0f,200.0f,16.0f,150.0f,55.0f,0.9f,1.3f,4.4f,1.5f,0.2f,1.0f,0.4f,0.0f,-25.0f,110.0f,0.35f,0.05f,0.0f,20.0f,8.0f,0.0f,1.5f,10.0f,0.3f,0.5f,0.0f,0.0f,0.6f,0.8f,1.0f,0.95f,0.0f,75.0f,70.0f,0.5f,0.2f,1.0f},
  {30.0f,20.0f,81.0f,-2.0f,-52.0f,208.0f,22.0f,150.0f,76.0f,2.6f,5.8f,2.3f,1.5f,0.18f,1.0f,1.0f,0.8f,9.0f,60.0f,0.46f,0.45f,0.8f,35.0f,3.0f,0.0f,1.75f,10.0f,0.125f,0.0f,0.0f,0.0f,0.0f,0.4f,0.8f,1.01f,0.3f,240.0f,600.0f,0.5f,0.4f,1.0f},
  {0.0f,20.0f,42.0f,0.0f,-120.0f,210.0f,22.0f,150.0f,124.0f,2.8f,1.6f,1.13f,1.5f,0.0f,0.78f,0.44f,0.69f,-80.0f,150.0f,0.0f,0.2f,0.3f,40.0f,5.0f,0.0f,2.4f,10.0f,0.125f,0.0f,0.0f,0.0f,0.0f,0.8f,1.0f,1.0f,0.0f,60.0f,75.0f,0.6f,0.1f,1.0f},
  {0.1f,29.0f,109.24f,0.0f,-130.0f,342.0f,42.8f,128.0f,132.0f,4.5f,3.9f,1.07f,1.6f,0.0f,0.46f,0.0f,0.0f,-122.0f,108.0f,0.0f,0.0f,0.0f,22.0f,9.7f,0.0f,2.0f,10.0f,0.125f,0.0f,0.0f,0.0f,0.0f,0.6f,0.98f,0.98f,-3.1416f,177.0f,38.0f,0.9f,0.08f,1.0f},
  {0.0f,13.0f,75.5f,0.0f,-440.0f,388.0f,17.67f,135.7f,78.0f,7.0f,5.0f,4.0f,1.4f,0.04f,1.0f,0.87f,1.0f,38.0f,170.0f,0.0f,0.0f,0.0f,10.0f,6.0f,0.0f,0.69f,1.31f,0.16f,0.2f,0.0f,0.0f,0.1f,0.0f,1.0f,1.0f,0.0f,10.0f,125.0f,0.2f,0.05f,1.0f},
  {0.0f,4.5f,90.0f,0.0f,-25.0f,127.0f,25.0f,153.0f,136.0f,9.2f,7.7f,9.49f,1.9f,0.0f,0.94f,1.0f,1.0f,-15.0f,94.0f,0.08f,0.0f,0.2f,15.0f,6.9f,5.2f,2.35f,0.34f,0.5f,7.3f,0.0f,0.0f,0.2f,0.46f,1.0f,0.96f,0.0f,5.0f,470.0f,0.4f,0.2f,1.0f},
  {-48.0f,15.5f,11.48f,24.0f,-75.0f,208.0f,36.0f,162.0f,64.0f,4.7f,4.0f,3.7f,1.5f,0.18f,1.0f,0.7f,0.4f,5.0f,240.0f,0.0f,0.0f,0.0f,48.0f,5.5f,3.9f,3.3f,10.0f,0.125f,2.5f,0.0f,0.6f,0.18f,0.85f,1.0f,1.0f,0.0f,70.0f,100.0f,0.5f,0.61f,1.0f},
  {4.0f,27.0f,55.0f,13.0f,-25.0f,130.0f,18.6f,128.0f,72.0f,0.9f,1.3f,4.4f,1.5f,0.0f,1.0f,0.1f,1.0f,23.0f,90.0f,0.34f,0.1f,0.33f,40.0f,4.0f,0.0f,2.0f,10.0f,0.2f,1.0f,0.0f,0.0f,0.3f,0.0f,1.0f,0.96f,0.0f,27.0f,100.0f,1.0f,1.0f,1.0f},
  {0.0f,12.0f,96.0f,-2.0f,-44.0f,208.0f,23.0f,150.0f,76.0f,5.4f,3.0f,9.7f,9.1f,0.18f,1.0f,0.86f,0.2f,5.0f,66.0f,0.0f,0.11f,0.0f,15.0f,4.0f,0.0f,0.6f,10.0f,0.125f,1.0f,0.0f,0.0f,0.3f,0.85f,1.0f,0.98f,0.0f,273.0f,100.0f,0.0f,0.6f,1.0f},
  {77.0f,15.0f,70.0f,-210.0f,-52.0f,208.0f,36.39f,179.0f,51.0f,3.8f,4.7f,7.8f,6.4f,0.08f,0.7f,0.4f,0.0f,21.0f,175.0f,0.0f,0.0f,0.0f,15.0f,5.0f,0.0f,2.0f,10.0f,0.125f,1.0f,0.0f,0.0f,0.3f,0.84f,1.0f,1.0f,0.0f,17.5f,107.0f,0.7f,0.1f,1.0f},
  {47.44f,33.0f,88.5f,13.0f,-60.0f,168.0f,2.4f,119.5f,68.0f,3.2f,9.0f,6.0f,1.14f,0.519f,1.0f,0.514f,0.38f,68.4f,114.1f,0.917f,0.631f,0.44f,102.0f,1.5f,2.5f,2.03f,3.84f,0.215f,8.2f,0.32f,0.0f,0.33f,0.0f,1.0f,0.96f,0.0f,27.0f,100.0f,0.0f,0.559f,1.0f},
  {0.01f,31.0f,83.5f,0.01f,83.0f,208.0f,2.7f,28.8f,160.0f,0.0f,0.0f,0.0f,0.86f,0.05f,0.87f,0.5f,0.4f,44.3f,248.6f,0.534f,0.532f,0.51f,19.8f,1.73f,-0.26f,0.89f,10.0f,0.125f,8.2f,0.32f,0.0f,0.33f,0.59f,0.96f,0.98f,-3.14f,177.8f,38.5f,0.26f,0.0f,1.0f},
  {110.0f,9.54f,35.301f,-356.0f,134.0f,284.0f,6.63f,-20.0f,94.0f,6.6f,5.4f,4.7f,8.7f,0.14f,1.0f,0.5f,0.3f,41.2f,155.7f,0.053f,0.056f,0.11f,7.0f,24.9001f,29.7f,11.8201f,2.1f,1.67f,5.2f,0.0f,0.31f,0.06f,0.0f,0.01f,0.5f,0.01f,45.3f,0.8f,0.5f,0.17f,1.0f},
  {117.92f,33.53f,94.901f,-2.01f,-110.0f,414.0f,33.53f,114.2f,111.0f,6.6f,5.4f,3.6f,2.12f,1.78f,0.58f,0.392f,0.37f,-154.8f,94.3f,0.94f,1.0f,0.91f,132.2f,8.3f,50.4998f,4.6101f,3.31f,1.94f,0.5f,0.01f,0.0f,0.06f,0.0f,0.17f,0.01f,-0.08f,50.3f,241.6f,0.581f,0.12f,0.4f},
  {42.23f,17.5f,58.31f,0.0f,-57.0f,152.0f,49.0f,401.0f,139.0f,7.7f,6.4f,8.0f,0.31f,0.615f,0.456f,0.464f,0.444f,10.0f,147.0f,0.0f,0.12f,0.125f,159.6f,10.48f,3.9f,1.9399f,0.0f,0.0f,26.8f,0.835f,0.31f,0.06f,0.1f,0.06f,0.01f,25.2801f,14.199f,238.9f,0.009f,0.0f,1.0f},
  {5.51f,18.03f,85.5f,6.02f,-108.46f,363.31f,15.4f,170.0f,122.0f,9.4f,2.9f,2.87f,0.31f,0.07f,0.26f,0.58f,0.9f,39.8f,159.0f,0.0f,0.0f,0.08f,143.7f,1.0399f,16.16f,0.71f,12.56f,0.649f,0.0f,0.0f,0.14f,3.3799f,0.0f,2.03f,1.4f,0.08f,43.7f,207.0f,0.775f,0.07f,0.7f},
  {-19.3f,84.91f,80.5f,220.8f,-48.15f,230.0f,21.7f,170.0f,149.0f,3.19f,4.9f,2.9f,1.3f,0.0f,0.23f,0.08f,0.34f,122.4f,370.0f,0.0f,0.0f,0.0f,317.4f,1.28f,1.86f,1.23f,1.1f,0.0f,1.1f,0.0f,0.2f,1.559f,0.0f,0.01f,1.1f,0.0f,108.0f,245.0f,0.383f,0.142f,0.9f},
  {0.03f,3.91f,108.93f,1.1f,1.85f,72.0f,37.8f,119.6f,113.0f,7.7f,3.35f,3.4f,1.0f,0.0f,0.009f,0.162f,0.123f,122.4f,370.0f,0.08f,0.09f,0.079f,122.8f,-0.09f,2.76f,0.3f,2.7f,1.0f,0.0f,0.0f,0.0f,0.43f,0.06f,0.01f,0.01f,2.78f,21.6f,945.0f,1.0f,0.218f,0.9f},
  {5.74f,15.0f,98.32f,328.85f,3.0f,785.0f,32.43f,119.6f,149.0f,9.1f,2.3f,2.1f,1.4f,0.12f,0.71f,0.61f,1.0f,107.4f,158.0f,0.0f,0.02f,0.0f,91.9f,0.3f,19.43f,0.93f,10.0254f,0.44f,0.15f,0.62f,0.54f,3.119f,0.09f,0.06f,0.01f,-0.4f,40.0f,245.0f,0.31f,0.155f,0.7f},
  {1.01f,20.0f,53.5f,0.0f,-54.0f,208.0f,26.3f,183.999f,100.0f,1.6f,1.5f,0.7f,1.5f,0.18f,1.0f,0.0f,0.0f,342.0f,395.0f,0.0f,0.0f,0.0f,40.0f,4.0f,0.0f,1.2f,10.0f,0.0f,0.0f,0.0f,0.0f,0.0f,0.0f,1.0f,0.95f,0.0f,124.8f,256.5f,0.0f,0.0f,1.0f},
  {47.44f,33.0f,88.5f,13.0f,-60.0f,168.0f,2.4f,119.5f,68.0f,3.2f,9.0f,6.0f,1.14f,0.519f,1.0f,0.514f,0.38f,68.4f,114.1f,0.917f,0.631f,0.44f,102.0f,1.5f,2.5f,2.03f,3.84f,0.215f,8.2f,0.32f,0.0f,0.33f,0.0f,1.0f,0.96f,0.0f,27.0f,100.0f,0.0f,0.559f,1.0f},
  {49.44f,48.5f,100.5f,13.0f,-60.0f,168.0f,2.4f,119.5f,68.0f,10.0f,10.0f,10.0f,1.14f,0.519f,0.99f,0.496f,0.436f,68.4f,114.1f,0.917f,0.631f,0.44f,102.0f,1.5f,2.5f,2.03f,3.84f,0.215f,8.2f,0.32f,0.0f,0.33f,0.0f,1.0f,0.96f,0.0f,27.0f,100.0f,0.0f,0.559f,1.0f},
  {16.94f,27.5f,101.94f,9.0f,-60.0f,396.0f,2.4f,121.2f,137.0f,7.8f,9.0f,8.0f,1.14f,0.519f,1.0f,0.519f,0.38f,68.4f,114.1f,0.87f,0.651f,0.65f,102.0f,1.5f,2.5f,2.03f,3.84f,0.215f,8.2f,0.32f,0.0f,0.33f,0.0f,1.0f,0.96f,0.0f,-145.0f,45.0f,0.08f,0.544f,1.0f},
  {47.44f,33.0f,117.0f,58.0f,-243.0f,168.0f,2.4f,119.5f,68.0f,3.2f,9.0f,6.0f,1.14f,0.519f,1.0f,0.514f,0.38f,68.4f,114.1f,0.917f,0.631f,0.44f,102.0f,1.5f,2.5f,2.03f,3.84f,0.215f,8.2f,0.32f,0.0f,0.33f,0.0f,1.0f,0.96f,0.0f,297.0f,30.6f,0.0f,0.669f,1.0f},
  {92.2f,55.0f,84.73f,-94.4f,-69.0f,133.0f,1.12f,88.5f,52.0f,10.0f,1.9f,4.49f,0.12f,0.719f,0.728f,0.737f,0.927f,68.4f,114.1f,0.84f,0.8f,0.79f,137.0f,1.5f,2.5f,1.64f,8.55f,0.226f,8.73f,0.32f,0.008f,0.33f,0.4f,1.0f,0.96f,0.0f,27.0f,100.0f,0.0f,0.559f,0.8f},
  {92.2f,55.0f,84.73f,-94.4f,-69.0f,249.0f,12.62f,115.5f,52.0f,10.0f,1.9f,4.49f,0.12f,0.719f,0.728f,0.737f,0.927f,68.4f,114.1f,0.619f,0.701f,0.816f,137.0f,1.5f,2.5f,1.87f,8.55f,0.226f,8.73f,0.32f,0.008f,0.33f,0.4f,1.0f,0.96f,0.0f,27.0f,100.0f,0.0f,0.559f,0.8f},
  {92.2f,55.0f,84.73f,-94.4f,-181.0f,249.0f,22.42f,115.5f,39.0f,10.0f,0.7f,6.89f,0.12f,0.719f,0.766f,0.782f,0.927f,68.4f,114.1f,0.036f,0.0f,0.136f,137.0f,1.5f,2.5f,1.87f,8.55f,0.226f,8.73f,0.32f,0.008f,0.33f,0.4f,1.0f,0.96f,0.0f,27.0f,100.0f,0.0f,0.559f,0.8f},
  {44.44f,31.5f,93.0f,13.0f,-60.0f,168.0f,2.4f,119.5f,68.0f,0.0f,0.0f,0.09f,0.6f,0.546f,0.04f,0.53f,0.67f,84.4f,114.1f,0.0f,0.0f,0.15f,102.0f,2.3f,2.5f,2.03f,3.84f,0.215f,8.2f,0.32f,0.0f,0.33f,0.0f,1.0f,0.96f,0.0f,27.0f,100.0f,0.0f,0.559f,1.0f},
  {66.44f,27.0f,74.0f,13.0f,-60.0f,168.0f,20.5f,119.0f,68.0f,0.8f,2.0f,0.0f,0.16f,0.43f,1.0f,0.564f,0.41f,68.4f,139.1f,0.76f,0.87f,0.41f,102.0f,-1.0f,1.2f,2.0f,5.04f,0.565f,11.7f,0.32f,0.0f,0.33f,0.0f,1.0f,0.96f,0.0f,37.0f,390.0f,0.92f,0.351f,1.0f},
  {47.44f,33.0f,88.0f,13.0f,-60.0f,168.0f,2.4f,119.5f,68.0f,0.29f,0.7f,1.1f,1.03f,0.171f,0.909f,0.61f,0.413f,84.4f,114.1f,0.92f,1.0f,0.72f,102.0f,2.3f,2.5f,2.03f,3.84f,0.215f,8.2f,0.32f,0.0f,0.33f,0.0f,1.0f,0.96f,0.0f,27.0f,100.0f,0.0f,0.559f,1.0f},
  {30.44f,33.0f,88.5f,13.0f,-60.0f,168.0f,2.4f,119.5f,68.0f,1.5f,0.2f,2.1f,0.07f,0.257f,0.695f,0.395f,0.366f,68.4f,114.1f,0.95f,0.66f,0.49f,102.0f,1.5f,2.5f,2.03f,3.84f,0.215f,8.2f,0.32f,0.0f,0.33f,0.0f,1.0f,0.96f,0.0f,27.0f,100.0f,0.0f,0.559f,1.0f},
  {30.44f,54.0f,76.5f,14.0f,-60.0f,168.0f,2.4f,119.5f,68.0f,8.0f,1.11f,0.0f,0.11f,0.654f,0.733f,0.642f,0.702f,68.4f,114.1f,0.92f,0.726f,0.44f,102.0f,1.5f,2.5f,2.03f,3.84f,0.215f,8.2f,0.32f,0.0f,0.33f,0.0f,1.0f,0.96f,0.0f,27.0f,100.0f,0.0f,0.559f,1.0f},
  {30.0f,20.0f,81.0f,-2.0f,-52.0f,208.0f,22.0f,150.0f,76.0f,2.6f,5.8f,2.3f,1.5f,0.18f,1.0f,1.0f,0.8f,9.0f,60.0f,0.46f,0.45f,0.8f,35.0f,3.0f,0.0f,1.75f,10.0f,0.125f,0.0f,0.0f,0.0f,0.0f,0.4f,0.8f,1.01f,0.3f,240.0f,600.0f,0.5f,0.4f,1.0f},
  {4.0f,12.5f,90.0f,-2.0f,-165.0f,246.0f,22.0f,150.0f,116.0f,1.6f,6.2f,0.0f,0.71f,0.54f,1.0f,1.0f,0.8f,9.0f,60.0f,0.41f,0.29f,0.67f,37.0f,3.8f,1.04f,3.15f,10.0f,0.125f,0.0f,0.0f,0.0f,0.0f,0.4f,0.8f,1.01f,0.3f,240.0f,600.0f,0.5f,0.323f,1.0f},
  {71.0f,20.0f,81.0f,-2.0f,-52.0f,208.0f,22.0f,122.3f,45.0f,0.6f,2.6f,0.8f,1.5f,0.18f,1.0f,1.0f,0.8f,9.0f,60.0f,0.5f,0.63f,0.8f,35.0f,7.6f,8.5f,2.45f,19.1f,0.035f,20.5f,1.17f,0.0f,0.0f,0.11f,0.01f,1.51f,0.79f,709.0f,416.0f,0.28f,0.57f,1.0f},
  {87.0f,20.0f,90.0f,17.0f,-90.0f,100.0f,22.0f,150.0f,76.0f,2.4f,3.0f,2.1f,2.5f,0.33f,1.0f,1.0f,0.8f,9.0f,60.0f,0.46f,0.45f,0.8f,35.0f,3.0f,0.0f,1.75f,10.0f,0.125f,0.0f,0.0f,0.0f,0.0f,0.4f,0.8f,1.01f,0.3f,240.0f,600.0f,0.5f,0.4f,1.0f},
  {-40.0f,20.0f,55.0f,25.0f,-50.0f,200.0f,16.0f,150.0f,55.0f,0.9f,1.3f,4.4f,1.5f,0.2f,1.0f,0.4f,0.0f,-25.0f,110.0f,0.35f,0.05f,0.0f,20.0f,8.0f,0.0f,1.5f,10.0f,0.3f,0.5f,0.0f,0.0f,0.6f,0.8f,1.0f,0.95f,0.0f,75.0f,70.0f,0.5f,0.433f,1.0f},
  {-100.0f,56.5f,3.0f,50.0f,-39.0f,208.0f,36.0f,162.0f,45.0f,1.4f,3.4f,9.1f,1.8f,0.0f,1.0f,0.35f,0.0f,5.0f,240.0f,0.05f,0.0f,0.07f,48.0f,5.5f,3.9f,2.7f,10.0f,0.125f,2.5f,0.0f,0.6f,0.18f,0.71f,1.0f,1.0f,0.0f,330.0f,306.0f,1.0f,0.33f,1.0f},
  {77.0f,15.0f,70.0f,-210.0f,-52.0f,208.0f,36.39f,179.0f,51.0f,3.8f,4.7f,7.8f,6.4f,0.08f,0.7f,0.4f,0.0f,21.0f,175.0f,0.0f,0.0f,0.0f,15.0f,5.0f,0.0f,2.0f,10.0f,0.125f,1.0f,0.0f,0.0f,0.3f,0.84f,1.0f,1.0f,0.0f,17.5f,107.0f,0.7f,0.1f,1.0f},
  {59.5f,26.5f,58.0f,-69.0f,-210.0f,466.0f,36.39f,179.0f,51.0f,3.8f,6.8f,7.8f,6.4f,0.08f,0.94f,0.4f,0.0f,21.0f,175.0f,0.0f,0.0f,0.0f,15.0f,10.3f,0.0f,1.9f,10.0f,0.125f,0.0f,0.0f,0.0f,0.3f,0.33f,1.0f,1.0f,0.0f,17.5f,107.0f,0.7f,0.27f,1.0f},
  {-4.18f,26.5f,53.86f,-1.15f,163.0f,199.0f,1.1f,50.6f,60.0f,6.2f,8.6f,1.11f,0.8f,0.4949f,0.88f,0.619f,0.679f,92.4f,314.2f,1.0f,1.0f,1.0f,26.0f,-1.1499f,126.329f,0.9f,13.5254f,0.46f,1.01f,2.9f,1.0f,10.0f,0.0f,0.01f,0.01f,10.57f,2.1f,211.0f,0.95f,0.29f,0.8f},
  {-4.18f,26.5f,43.36f,17.85f,-33.0f,86.0f,1.1f,50.6f,60.0f,0.28f,1.2f,5.2f,0.8f,0.4949f,1.0f,1.0f,0.679f,92.4f,314.2f,1.0f,1.0f,1.0f,26.0f,-1.1499f,126.329f,0.9f,13.5254f,0.46f,1.01f,2.9f,1.0f,10.0f,0.0f,0.01f,0.01f,-4.33f,2.1f,211.0f,0.95f,0.28f,0.8f},
  {-4.18f,26.5f,53.86f,-1.15f,745.0f,530.0f,1.1f,50.6f,60.0f,4.3f,4.8f,1.24f,0.8f,0.4949f,1.0f,0.589f,0.679f,92.4f,314.2f,1.0f,1.0f,1.0f,26.0f,-1.1499f,126.329f,0.9f,13.5254f,0.46f,1.01f,2.9f,1.0f,10.0f,0.0f,0.01f,0.01f,10.57f,2.1f,211.0f,0.95f,0.29f,0.8f},
  {-1.18f,40.58f,38.86f,17.85f,-33.0f,110.0f,1.1f,50.6f,60.0f,10.0f,10.0f,10.0f,0.0f,0.5329f,0.93f,0.91f,1.0f,92.4f,314.1f,0.77f,0.89f,0.96f,26.0f,-1.1499f,126.329f,0.9f,13.5254f,0.46f,1.01f,2.9f,1.0f,10.0f,0.09f,0.01f,0.01f,10.57f,35.1f,229.0f,0.95f,0.27f,0.8f},
  {-4.18f,39.0f,79.36f,-63.15f,-197.17f,120.0f,2.49f,50.6f,60.0f,4.02f,9.0f,5.3f,2.5f,0.88f,0.99f,0.75f,0.66f,92.4f,317.1f,1.0f,0.94f,0.88f,38.1f,-1.1499f,126.329f,0.9f,13.5254f,0.46f,1.01f,2.9f,1.0f,10.0f,0.0f,0.01f,0.01f,1.07f,72.1f,113.0f,1.0f,0.3f,0.7f},
  {3.24f,91.5f,109.32f,0.85f,3.0f,77.0f,49.0f,-38.4f,52.0f,6.0f,1.1f,10.0f,0.0f,1.46f,0.92f,1.0f,1.0f,71.4f,288.2f,0.68f,0.94f,0.95f,118.0f,-0.4399f,126.329f,0.9f,13.5254f,0.46f,0.0f,2.9f,1.0f,10.0f,0.0f,1.05f,3.3f,-2.03f,-8.9f,206.0f,0.95f,0.0f,0.7f},
  {0.01f,19.41f,108.93f,0.02f,1.85f,72.0f,37.8f,119.6f,113.0f,7.7f,2.55f,2.7f,1.03f,0.03f,0.459f,0.84f,0.473f,19.4f,332.6f,0.0f,0.0f,0.07f,167.8f,-0.4099f,-0.14f,0.7f,2.7f,1.0f,0.0f,0.0f,0.0f,0.43f,0.06f,0.01f,0.01f,2.78f,41.6f,146.0f,1.0f,0.16f,0.9f},
  {0.02f,5.38f,108.93f,-0.05f,9.85f,72.0f,39.3f,231.6f,113.0f,7.7f,2.55f,2.7f,1.43f,0.41f,0.0f,0.88f,1.0f,19.4f,332.6f,0.0f,0.0f,0.07f,124.8f,0.5401f,2.76f,0.7f,2.7f,1.0f,0.0f,2.64f,0.0f,0.43f,0.0f,0.01f,0.01f,2.78f,41.6f,146.0f,1.0f,0.16f,0.5f},
  {5.74f,20.0f,76.32f,328.85f,-101.0f,785.0f,41.53f,119.6f,149.0f,9.1f,2.3f,2.1f,1.4f,0.12f,0.47f,0.61f,1.0f,107.4f,158.0f,0.0f,0.02f,0.0f,91.9f,0.3f,19.43f,0.93f,10.0254f,0.44f,0.15f,0.62f,0.54f,3.119f,0.09f,0.06f,0.01f,-0.4f,40.0f,245.0f,0.31f,0.155f,0.7f},
  {-107.76f,20.5f,31.82f,285.85f,24.0f,973.03f,49.0f,306.6f,138.0f,2.4f,2.33f,3.03f,1.29f,0.17f,0.72f,0.834f,0.81f,107.4f,158.0f,0.0f,0.02f,0.0f,78.7999f,2.88f,76.1293f,1.78f,13.1254f,0.44f,0.0f,9.2801f,0.0f,2.454f,0.0f,0.01f,0.01f,0.01f,180.0f,464.0f,0.86f,0.03f,0.7f},
  {-40.0f,13.0f,85.82f,-34.15f,-234.0f,653.0f,49.0f,356.6f,153.0f,2.3f,2.7f,5.0f,1.06f,0.08f,0.974f,0.994f,0.92f,-130.6f,390.0f,0.9f,1.0f,1.0f,44.7999f,1.6501f,72.2294f,1.28f,19.7654f,0.76f,0.0f,9.1401f,0.58f,1.114f,0.09f,0.06f,0.01f,0.07f,12.1f,130.0f,0.31f,0.1f,0.9f},
  {-60.0f,10.0f,72.32f,-34.15f,-234.0f,653.0f,49.0f,131.6f,155.0f,1.3f,0.88f,3.5f,1.48f,0.0f,0.86f,0.89f,0.4f,107.4f,158.0f,0.37f,1.0f,1.0f,79.2999f,0.1301f,114.729f,1.1f,13.5254f,0.48f,0.0f,7.87f,1.0f,3.7199f,0.09f,0.06f,0.01f,0.07f,14.1f,130.0f,0.31f,0.086f,0.5f},
  {15.03f,66.03f,40.5f,-6.96f,-327.46f,295.31f,49.0f,233.0f,49.0f,5.9f,4.4f,4.3f,2.5f,0.29f,0.387f,0.63f,0.9f,39.8f,159.0f,0.0f,0.19f,0.43f,69.7f,0.7399f,30.6601f,1.92f,1.8f,0.429f,0.1f,1.43f,0.37f,0.0f,0.0f,2.03f,1.4f,0.08f,43.7f,207.0f,0.775f,0.54f,0.7f},
  {0.03f,15.53f,78.5f,34.04f,-60.46f,300.31f,49.0f,233.0f,145.0f,10.0f,8.6f,2.87f,0.46f,0.03f,0.197f,0.63f,0.9f,39.8f,159.0f,0.0f,0.16f,0.37f,121.7f,0.3399f,30.6601f,0.82f,5.71f,0.0f,0.1f,1.43f,0.37f,0.0f,0.0f,2.03f,1.4f,0.08f,43.7f,207.0f,0.775f,0.07f,0.8f},
  {-3.76f,16.5f,72.32f,-2.15f,-98.0f,539.0f,49.0f,131.6f,160.0f,2.83f,2.58f,2.54f,1.23f,0.767f,0.89f,0.9f,1.0f,71.6f,284.0f,0.93f,1.0f,1.0f,67.7999f,1.0401f,58.0299f,1.3f,15.6254f,0.25f,0.0f,8.28f,0.0f,5.2999f,0.06f,1.11f,1.9f,-2.62f,-8.7f,201.0f,0.93f,0.015f,0.7f},
  {6.27f,12.5f,29.32f,-7.15f,541.0f,601.0f,40.1f,-5.4f,139.0f,0.0f,0.0f,0.0f,2.87f,0.672f,1.0f,1.0f,1.0f,71.4f,288.2f,0.76f,1.0f,0.95f,116.0f,-1.1499f,126.329f,0.9f,13.5254f,0.46f,0.0f,2.9f,1.0f,10.0f,0.2f,2.71f,5.0f,19.67f,62.1f,-21.0f,0.04f,0.22f,0.8f},
};

// ---------------------------------------------------------------------------
// Lattice resolution. The web uses 200x256; nano recomputes the heightfield on
// the CPU every frame, so this is the perf-safe value (a denser lattice only
// sharpens the silhouette of a fast-receding flythrough). Bump if there is room.
// ---------------------------------------------------------------------------
static const int GRID_W = 160;
static const int GRID_D = 192;
static const float HALF_W = 120.0f, DEPTH = 260.0f;   // world lattice extent (web)
static const float PRESET_DWELL = 7.0f;               // seconds per preset

// ---------------------------------------------------------------------------
// Shaders. TERRAIN_VS takes a CPU-precomputed aHeight (no vertex texture fetch);
// TERRAIN_FS, BLUR and the tonemap are 1:1 with canyon_port.js. COMP folds the
// Reinhard tonemap into the panel composite with the DRM rotation + crossfade.
// ---------------------------------------------------------------------------
static const char* TERRAIN_VS =
    "precision highp float;\n"
    "attribute vec2 aGrid;\n"
    "attribute float aHeight;\n"          // CPU-precomputed world Y (sampleHeight*THeight - valley)
    "uniform mat4 uMVP;\n"
    "uniform vec3 uCamPos;\n"
    "uniform float uScroll;\n"
    "uniform float uHalfW, uDepth;\n"
    "uniform float uTWidth, uTScaleX, uTexScale, uTSlide;\n"
    "uniform float uTHeight;\n"            // for the ridge highlight normalisation only
    "uniform float uFogMin, uFogRange;\n"
    "varying vec2 vUV;\n"
    "varying float vFog;\n"
    "varying float vRidge;\n"
    "varying float vWorldY;\n"
    "void main(){\n"
    "  float wx = (aGrid.x - 0.5) * 2.0 * uHalfW * uTWidth;\n"
    "  float wz = uCamPos.z + aGrid.y * uDepth;\n"
    "  vec2 uv = vec2(wx / (uTexScale * uTScaleX * 8.0),\n"
    "                 (wz + uScroll * uTSlide) / (uTexScale * 8.0));\n"
    "  float wy = aHeight;\n"
    "  vUV = uv;\n"
    "  vWorldY = wy;\n"
    "  vRidge = clamp(wy / max(uTHeight*6.0, 0.5), 0.0, 1.0);\n"
    "  float dist = wz - uCamPos.z;\n"
    "  vFog = clamp((dist - uFogMin) / max(uFogRange, 0.001), 0.0, 1.0);\n"
    "  gl_Position = uMVP * vec4(wx, wy, wz, 1.0);\n"
    "}\n";

static const char* TERRAIN_FS =
    "precision highp float;\n"
    "uniform sampler2D uNormal;\n"
    "uniform vec3 uColour;\n"
    "uniform float uColScale, uColBias;\n"
    "uniform vec3 uLineColour;\n"
    "uniform vec3 uFogColour;\n"
    "uniform float uBump;\n"
    "varying vec2 vUV;\n"
    "varying float vFog;\n"
    "varying float vRidge;\n"
    "varying float vWorldY;\n"
    "vec3 fresLUT(float t){\n"
    "  vec3 lo = vec3(0.04, 0.14, 0.17);\n"
    "  vec3 hi = vec3(0.70, 0.95, 1.05);\n"
    "  return mix(lo, hi, pow(clamp(t,0.0,1.0), 1.3));\n"
    "}\n"
    "void main(){\n"
    "  vec3 n = texture2D(uNormal, fract(vUV)).rgb;\n"
    "  vec2 t = (n.xy - 0.5) * (1.0 + uBump * 4.0);\n"
    "  vec3 nrm = normalize(vec3(t.x, max(n.z, 0.05), t.y));\n"
    "  float rim = 1.0 - clamp(nrm.y, 0.0, 1.0);\n"
    "  vec3 sheen = fresLUT(rim);\n"
    "  vec3 base = uColour * uColScale + uColBias;\n"
    "  vec3 lit = sheen * (0.6 + 0.25*base);\n"
    "  lit += uLineColour * vRidge * (0.4 + rim);\n"
    "  vec3 col = mix(lit, uFogColour, vFog);\n"
    "  gl_FragColor = vec4(col, 1.0);\n"
    "}\n";

static const char* QUAD_VS =
    "precision highp float;\n"
    "attribute vec2 aPos;\n"
    "varying vec2 vT;\n"
    "void main(){ vT = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }\n";

static const char* BLUR_FS =
    "precision highp float;\n"
    "uniform sampler2D uCurrent;\n"
    "uniform sampler2D uPrev;\n"
    "uniform float uFeedback;\n"
    "uniform vec2 uFeedScale;\n"
    "uniform float uFeedRot;\n"
    "varying vec2 vT;\n"
    "void main(){\n"
    "  vec2 p = vT - 0.5;\n"
    "  float c = cos(uFeedRot), s = sin(uFeedRot);\n"
    "  p = mat2(c, -s, s, c) * p;\n"
    "  p /= max(uFeedScale, vec2(0.001));\n"
    "  vec2 puv = p + 0.5;\n"
    "  vec3 prev = vec3(0.0);\n"
    "  if (puv.x > 0.0 && puv.x < 1.0 && puv.y > 0.0 && puv.y < 1.0)\n"
    "    prev = texture2D(uPrev, puv).rgb;\n"
    "  vec3 cur = texture2D(uCurrent, vT).rgb;\n"
    "  vec3 outc = max(cur, prev * uFeedback);\n"
    "  outc = mix(outc, cur, 0.35);\n"
    "  gl_FragColor = vec4(outc, 1.0);\n"
    "}\n";

// Composite: Reinhard tonemap (canyon_port.js PRESENT_FS) + DRM rotation + the
// Waves<->Canyon crossfade alpha. Renders feedA to the panel frame rect.
static const char* COMP_VS =
    "precision highp float;\n"
    "attribute vec2 aPos;\n"
    "attribute vec2 aUV;\n"
    "uniform mat2 uRotation;\n"
    "varying vec2 vT;\n"
    "void main(){ vT = aUV; gl_Position = vec4(uRotation * aPos, 0.0, 1.0); }\n";

static const char* COMP_FS =
    "precision mediump float;\n"
    "uniform sampler2D uTex;\n"
    "uniform float uExposure;\n"
    "uniform float uAlpha;\n"
    "varying vec2 vT;\n"
    "void main(){\n"
    "  vec3 c = texture2D(uTex, vT).rgb * uExposure;\n"
    "  c = c / (c + vec3(0.85));\n"
    "  c = pow(c, vec3(1.0 / 2.2));\n"
    "  gl_FragColor = vec4(c, uAlpha);\n"
    "}\n";

// ---------------------------------------------------------------------------
// GL state
// ---------------------------------------------------------------------------
static bool sReady = false, sTriedInit = false;
static GLuint sTerrProg = 0, sBlurProg = 0, sCompProg = 0;
// terrain locations
static GLint tAGrid, tAHeight, tMVP, tCamPos, tScroll, tHalfW, tDepth,
             tTWidth, tTScaleX, tTexScale, tTSlide, tTHeight, tFogMin, tFogRange,
             tNormal, tColour, tColScale, tColBias, tLineColour, tFogColour, tBump;
// blur locations
static GLint bAPos, bCurrent, bPrev, bFeedback, bFeedScale, bFeedRot;
// composite locations
static GLint cAPos, cAUV, cTex, cExposure, cAlpha, cRot;

static GLuint sNormalTex = 0;
static std::vector<unsigned char> sNormalPix;   // CPU RGBA, for the height sampler
static int sNormalW = 0, sNormalH = 0;
static bool sNormalPow2 = false;   // W,H both power-of-two -> bitmask wrap (no slow modulo)
static int sNormalWMask = 0, sNormalHMask = 0;

// mesh: static aGrid VBO + per-frame aHeight VBO; stripIdx maps each strip vertex
// to its heightGrid cell so the expensive sampleHeight runs once per grid point.
static GLuint sGridVBO = 0, sHeightVBO = 0;
static int sStripCount = 0;
static std::vector<int> sStripIdx;
static std::vector<float> sHeightGrid;   // GRID_W*GRID_D
static std::vector<float> sHeightStrip;  // sStripCount

// half-res render targets
struct RT { GLuint fb, tex, depth; int w, h; };
static RT sScene = {0,0,0,0,0}, sFeedA = {0,0,0,0,0}, sFeedB = {0,0,0,0,0};
static GLuint sQuadVBO = 0;

// preset clock
static int sPresetIdx = 0;
static float sPresetT = 0.0f;
static double sCamZ = 0.0;

// ---------------------------------------------------------------------------
// helpers
// ---------------------------------------------------------------------------
static GLuint compileShader(GLenum type, const char* src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    GLint ok = 0; glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) { char log[512]; glGetShaderInfoLog(s, sizeof(log), nullptr, log);
               ALOGE("ps3canyon: shader compile: %s", log); }
    return s;
}
static GLuint linkProgram(const char* vs, const char* fs) {
    GLuint v = compileShader(GL_VERTEX_SHADER, vs), f = compileShader(GL_FRAGMENT_SHADER, fs);
    GLuint p = glCreateProgram();
    glAttachShader(p, v); glAttachShader(p, f);
    glLinkProgram(p);
    glDeleteShader(v); glDeleteShader(f);
    GLint ok = 0; glGetProgramiv(p, GL_LINK_STATUS, &ok);
    if (!ok) { char log[512]; glGetProgramInfoLog(p, sizeof(log), nullptr, log);
               ALOGE("ps3canyon: link: %s", log); glDeleteProgram(p); return 0; }
    return p;
}

static bool resolveAsset(const char* file, char* out, size_t n) {
    snprintf(out, n, "/data/system/nano_xmb/canyon/%s", file);
    FILE* f = fopen(out, "rb"); if (f) { fclose(f); return true; }
    snprintf(out, n, "/system/etc/nano_xmb/canyon/%s", file);
    f = fopen(out, "rb"); if (f) { fclose(f); return true; }
    return false;
}

// Decode the normalmap into CPU RGBA pixels (for the height sampler) and upload a
// REPEAT/LINEAR GL texture (for the fragment normal).
static bool loadNormal() {
    char path[256];
    if (!resolveAsset("canyon_normalmap.png", path, sizeof(path))) {
        ALOGE("ps3canyon: normalmap not found"); return false;
    }
    FILE* fp = fopen(path, "rb"); if (!fp) return false;
    png_structp png = png_create_read_struct(PNG_LIBPNG_VER_STRING, nullptr, nullptr, nullptr);
    if (!png) { fclose(fp); return false; }
    png_infop info = png_create_info_struct(png);
    if (!info) { png_destroy_read_struct(&png, nullptr, nullptr); fclose(fp); return false; }
    if (setjmp(png_jmpbuf(png))) { png_destroy_read_struct(&png, &info, nullptr); fclose(fp); return false; }
    png_init_io(png, fp);
    png_read_info(png, info);
    int w = png_get_image_width(png, info), h = png_get_image_height(png, info);
    int color = png_get_color_type(png, info), depth = png_get_bit_depth(png, info);
    if (depth == 16) png_set_strip_16(png);
    if (color == PNG_COLOR_TYPE_PALETTE) png_set_palette_to_rgb(png);
    if (color == PNG_COLOR_TYPE_GRAY && depth < 8) png_set_expand_gray_1_2_4_to_8(png);
    if (png_get_valid(png, info, PNG_INFO_tRNS)) png_set_tRNS_to_alpha(png);
    if (color == PNG_COLOR_TYPE_GRAY || color == PNG_COLOR_TYPE_GRAY_ALPHA) png_set_gray_to_rgb(png);
    if (color == PNG_COLOR_TYPE_RGB || color == PNG_COLOR_TYPE_GRAY || color == PNG_COLOR_TYPE_PALETTE)
        png_set_filler(png, 0xFF, PNG_FILLER_AFTER);
    png_read_update_info(png, info);
    sNormalPix.assign((size_t)w * h * 4, 0);
    std::vector<png_bytep> rows((size_t)h);
    for (int y = 0; y < h; y++) rows[(size_t)y] = sNormalPix.data() + (size_t)y * w * 4;
    png_read_image(png, rows.data());
    png_destroy_read_struct(&png, &info, nullptr);
    fclose(fp);
    sNormalW = w; sNormalH = h;
    sNormalPow2 = (w > 0 && h > 0 && (w & (w - 1)) == 0 && (h & (h - 1)) == 0);
    sNormalWMask = w - 1; sNormalHMask = h - 1;

    glGenTextures(1, &sNormalTex);
    glBindTexture(GL_TEXTURE_2D, sNormalTex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, sNormalPix.data());
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    return true;
}

// Bilinear sample of the normalmap with REPEAT wrap, matching GL texture2D. uv is
// in tile space; out[3] = R/G/B in [0,1].
static inline void sampleNormal(float u, float v, float* out) {
    int W = sNormalW, H = sNormalH;
    if (W <= 0 || H <= 0) { out[0] = out[1] = 0.5f; out[2] = 1.0f; return; }
    float fx = u * (float)W - 0.5f, fy = v * (float)H - 0.5f;
    int x0 = (int)floorf(fx), y0 = (int)floorf(fy);
    float tx = fx - (float)x0, ty = fy - (float)y0;
    int x1 = x0 + 1, y1 = y0 + 1;
    if (sNormalPow2) {
        // power-of-two: bitmask wrap (A53 integer modulo is the per-vertex hot path)
        x0 &= sNormalWMask; x1 &= sNormalWMask;
        y0 &= sNormalHMask; y1 &= sNormalHMask;
    } else {
        x0 = ((x0 % W) + W) % W; x1 = ((x1 % W) + W) % W;
        y0 = ((y0 % H) + H) % H; y1 = ((y1 % H) + H) % H;
    }
    const unsigned char* p = sNormalPix.data();
    const unsigned char* a = p + ((size_t)y0 * W + x0) * 4;
    const unsigned char* b = p + ((size_t)y0 * W + x1) * 4;
    const unsigned char* c = p + ((size_t)y1 * W + x0) * 4;
    const unsigned char* d = p + ((size_t)y1 * W + x1) * 4;
    for (int k = 0; k < 3; k++) {
        float top = a[k] + (b[k] - a[k]) * tx;
        float bot = c[k] + (d[k] - c[k]) * tx;
        out[k] = (top + (bot - top) * ty) * (1.0f / 255.0f);
    }
}

// CPU port of TERRAIN_VS sampleHeight (two octaves).
static inline float sampleHeight(float u, float v) {
    float n0[3], n1[3];
    sampleNormal(u, v, n0);
    sampleNormal(u * 0.37f + 0.21f, v * 0.37f + 0.21f, n1);
    float h = (n0[0] - 0.5f) + (n0[1] - 0.5f) + (1.0f - n0[2]) * 4.0f;
    h += ((n1[0] - 0.5f) + (n1[1] - 0.5f) + (1.0f - n1[2]) * 4.0f) * 0.5f;
    return h;
}

static void makeRT(RT& rt, int w, int h, bool withDepth) {
    if (!rt.fb) glGenFramebuffers(1, &rt.fb);
    if (!rt.tex) glGenTextures(1, &rt.tex);
    glBindTexture(GL_TEXTURE_2D, rt.tex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glBindFramebuffer(GL_FRAMEBUFFER, rt.fb);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, rt.tex, 0);
    if (withDepth) {
        if (!rt.depth) glGenRenderbuffers(1, &rt.depth);
        glBindRenderbuffer(GL_RENDERBUFFER, rt.depth);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT16, w, h);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, rt.depth);
    }
    rt.w = w; rt.h = h;
}

static bool ensureRTs(int fw, int fh) {
    int hw = fw >> 1; if (hw < 2) hw = 2;
    int hh = fh >> 1; if (hh < 2) hh = 2;
    if (sScene.w == hw && sScene.h == hh && sScene.fb) return true;
    makeRT(sScene, hw, hh, true);
    makeRT(sFeedA, hw, hh, false);
    makeRT(sFeedB, hw, hh, false);
    // clear the feedback buffers so the first feedback frame has a clean prev
    GLuint fbs[2] = { sFeedA.fb, sFeedB.fb };
    for (int i = 0; i < 2; i++) {
        glBindFramebuffer(GL_FRAMEBUFFER, fbs[i]);
        glViewport(0, 0, hw, hh);
        glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
    }
    return true;
}

// triangle-strip lattice (canyon_port.js buildMesh) -> static aGrid VBO + the
// per-vertex heightGrid index, plus the per-frame aHeight VBO.
static void buildMesh() {
    std::vector<float> grid;        // aGrid x,z per vertex
    sStripIdx.clear();
    auto pushV = [&](int ix, int iz) {
        grid.push_back((float)ix / (float)(GRID_W - 1));
        grid.push_back((float)iz / (float)(GRID_D - 1));
        sStripIdx.push_back(iz * GRID_W + ix);
    };
    for (int z = 0; z < GRID_D - 1; z++) {
        for (int x = 0; x < GRID_W; x++) { pushV(x, z); pushV(x, z + 1); }
        pushV(GRID_W - 1, z + 1);   // degenerate wrap to the next row
        pushV(0, z + 1);
    }
    sStripCount = (int)(grid.size() / 2);
    sHeightStrip.assign((size_t)sStripCount, 0.0f);
    sHeightGrid.assign((size_t)GRID_W * GRID_D, 0.0f);

    if (!sGridVBO) glGenBuffers(1, &sGridVBO);
    glBindBuffer(GL_ARRAY_BUFFER, sGridVBO);
    glBufferData(GL_ARRAY_BUFFER, (GLsizeiptr)(grid.size() * sizeof(float)), grid.data(), GL_STATIC_DRAW);
    if (!sHeightVBO) glGenBuffers(1, &sHeightVBO);
    glBindBuffer(GL_ARRAY_BUFFER, sHeightVBO);
    glBufferData(GL_ARRAY_BUFFER, (GLsizeiptr)(sStripCount * sizeof(float)), nullptr, GL_DYNAMIC_DRAW);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
}

// ---- mat4 (column-major, matching canyon_port.js) ----
static void mPerspective(float* m, float fovy, float aspect, float nearp, float farp) {
    float f = 1.0f / tanf(fovy / 2.0f), nf = 1.0f / (nearp - farp);
    m[0]=f/aspect; m[1]=0; m[2]=0; m[3]=0;
    m[4]=0; m[5]=f; m[6]=0; m[7]=0;
    m[8]=0; m[9]=0; m[10]=(farp+nearp)*nf; m[11]=-1;
    m[12]=0; m[13]=0; m[14]=(2.0f*farp*nearp)*nf; m[15]=0;
}
static void mLookAt(float* m, const float* eye, const float* ctr, const float* up) {
    float z[3] = {eye[0]-ctr[0], eye[1]-ctr[1], eye[2]-ctr[2]};
    float zl = sqrtf(z[0]*z[0]+z[1]*z[1]+z[2]*z[2]); if (zl < 1e-6f) zl = 1.0f;
    z[0]/=zl; z[1]/=zl; z[2]/=zl;
    float x[3] = {up[1]*z[2]-up[2]*z[1], up[2]*z[0]-up[0]*z[2], up[0]*z[1]-up[1]*z[0]};
    float xl = sqrtf(x[0]*x[0]+x[1]*x[1]+x[2]*x[2]); if (xl < 1e-6f) xl = 1.0f;
    x[0]/=xl; x[1]/=xl; x[2]/=xl;
    float y[3] = {z[1]*x[2]-z[2]*x[1], z[2]*x[0]-z[0]*x[2], z[0]*x[1]-z[1]*x[0]};
    m[0]=x[0]; m[1]=y[0]; m[2]=z[0]; m[3]=0;
    m[4]=x[1]; m[5]=y[1]; m[6]=z[1]; m[7]=0;
    m[8]=x[2]; m[9]=y[2]; m[10]=z[2]; m[11]=0;
    m[12]=-(x[0]*eye[0]+x[1]*eye[1]+x[2]*eye[2]);
    m[13]=-(y[0]*eye[0]+y[1]*eye[1]+y[2]*eye[2]);
    m[14]=-(z[0]*eye[0]+z[1]*eye[1]+z[2]*eye[2]);
    m[15]=1;
}
static void mMul(float* o, const float* a, const float* b) {
    for (int c = 0; c < 4; c++) for (int r = 0; r < 4; r++) {
        float s = 0; for (int k = 0; k < 4; k++) s += a[k*4+r] * b[c*4+k];
        o[c*4+r] = s;
    }
}

// ---------------------------------------------------------------------------
// public API
// ---------------------------------------------------------------------------
bool init() {
    if (sReady) return true;
    if (sTriedInit) return false;
    sTriedInit = true;
    sTerrProg = linkProgram(TERRAIN_VS, TERRAIN_FS);
    sBlurProg = linkProgram(QUAD_VS, BLUR_FS);
    sCompProg = linkProgram(COMP_VS, COMP_FS);
    if (!sTerrProg || !sBlurProg || !sCompProg) return false;
    tAGrid     = glGetAttribLocation(sTerrProg, "aGrid");
    tAHeight   = glGetAttribLocation(sTerrProg, "aHeight");
    tMVP       = glGetUniformLocation(sTerrProg, "uMVP");
    tCamPos    = glGetUniformLocation(sTerrProg, "uCamPos");
    tScroll    = glGetUniformLocation(sTerrProg, "uScroll");
    tHalfW     = glGetUniformLocation(sTerrProg, "uHalfW");
    tDepth     = glGetUniformLocation(sTerrProg, "uDepth");
    tTWidth    = glGetUniformLocation(sTerrProg, "uTWidth");
    tTScaleX   = glGetUniformLocation(sTerrProg, "uTScaleX");
    tTexScale  = glGetUniformLocation(sTerrProg, "uTexScale");
    tTSlide    = glGetUniformLocation(sTerrProg, "uTSlide");
    tTHeight   = glGetUniformLocation(sTerrProg, "uTHeight");
    tFogMin    = glGetUniformLocation(sTerrProg, "uFogMin");
    tFogRange  = glGetUniformLocation(sTerrProg, "uFogRange");
    tNormal    = glGetUniformLocation(sTerrProg, "uNormal");
    tColour    = glGetUniformLocation(sTerrProg, "uColour");
    tColScale  = glGetUniformLocation(sTerrProg, "uColScale");
    tColBias   = glGetUniformLocation(sTerrProg, "uColBias");
    tLineColour= glGetUniformLocation(sTerrProg, "uLineColour");
    tFogColour = glGetUniformLocation(sTerrProg, "uFogColour");
    tBump      = glGetUniformLocation(sTerrProg, "uBump");
    bAPos      = glGetAttribLocation(sBlurProg, "aPos");
    bCurrent   = glGetUniformLocation(sBlurProg, "uCurrent");
    bPrev      = glGetUniformLocation(sBlurProg, "uPrev");
    bFeedback  = glGetUniformLocation(sBlurProg, "uFeedback");
    bFeedScale = glGetUniformLocation(sBlurProg, "uFeedScale");
    bFeedRot   = glGetUniformLocation(sBlurProg, "uFeedRot");
    cAPos      = glGetAttribLocation(sCompProg, "aPos");
    cAUV       = glGetAttribLocation(sCompProg, "aUV");
    cTex       = glGetUniformLocation(sCompProg, "uTex");
    cExposure  = glGetUniformLocation(sCompProg, "uExposure");
    cAlpha     = glGetUniformLocation(sCompProg, "uAlpha");
    cRot       = glGetUniformLocation(sCompProg, "uRotation");
    if (!loadNormal()) return false;
    buildMesh();
    static const GLfloat quad[] = { -1,-1, 1,-1, -1,1, -1,1, 1,-1, 1,1 };
    glGenBuffers(1, &sQuadVBO);
    glBindBuffer(GL_ARRAY_BUFFER, sQuadVBO);
    glBufferData(GL_ARRAY_BUFFER, sizeof(quad), quad, GL_STATIC_DRAW);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    sReady = true;
    ALOGI("ps3canyon: ready (%d presets, %dx%d lattice, normal %dx%d)",
          kCanyonNumPresets, GRID_W, GRID_D, sNormalW, sNormalH);
    return true;
}

void shutdown() {
    if (sTerrProg) glDeleteProgram(sTerrProg);
    if (sBlurProg) glDeleteProgram(sBlurProg);
    if (sCompProg) glDeleteProgram(sCompProg);
    if (sNormalTex) glDeleteTextures(1, &sNormalTex);
    if (sGridVBO) glDeleteBuffers(1, &sGridVBO);
    if (sHeightVBO) glDeleteBuffers(1, &sHeightVBO);
    if (sQuadVBO) glDeleteBuffers(1, &sQuadVBO);
    RT* rts[3] = { &sScene, &sFeedA, &sFeedB };
    for (int i = 0; i < 3; i++) {
        if (rts[i]->fb) glDeleteFramebuffers(1, &rts[i]->fb);
        if (rts[i]->tex) glDeleteTextures(1, &rts[i]->tex);
        if (rts[i]->depth) glDeleteRenderbuffers(1, &rts[i]->depth);
        *rts[i] = {0,0,0,0,0};
    }
    sTerrProg = sBlurProg = sCompProg = 0; sNormalTex = 0;
    sGridVBO = sHeightVBO = sQuadVBO = 0;
    sNormalPix.clear(); sStripIdx.clear(); sHeightGrid.clear(); sHeightStrip.clear();
    sReady = false; sTriedInit = false;
    sPresetIdx = 0; sPresetT = 0.0f; sCamZ = 0.0;
}

bool ready() { return sReady; }

void reset() { sPresetIdx = 0; sPresetT = 0.0f; sCamZ = 0.0; }

static void drawQuad(GLint posLoc) {
    glBindBuffer(GL_ARRAY_BUFFER, sQuadVBO);
    glEnableVertexAttribArray(posLoc);
    glVertexAttribPointer(posLoc, 2, GL_FLOAT, GL_FALSE, 0, (const void*)0);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    glDisableVertexAttribArray(posLoc);
}

void render(int panelW, int panelH, const float rotMat2[4], float alpha,
            float dt, const nanoaudio::Bands& bands) {
    if (!sReady && !init()) return;
    if (alpha <= 0.0f) return;
    const int fw = (int)(ps3::gFrameW + 0.5f);
    const int fh = (int)(ps3::gFrameH + 0.5f);
    if (fw < 4 || fh < 4) return;
    if (dt <= 0.0f || dt > 0.1f) dt = 0.016f;

    GLint prevFbo = 0; glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFbo);
    GLint prevVp[4] = {0,0,panelW,panelH}; glGetIntegerv(GL_VIEWPORT, prevVp);
    if (!ensureRTs(fw, fh)) return;

    // ---- preset cross-fade cycle (canyon_port.js mpDrawCanyon) ----
    sPresetT += dt / PRESET_DWELL;
    while (sPresetT >= 1.0f) { sPresetT -= 1.0f; sPresetIdx = (sPresetIdx + 1) % kCanyonNumPresets; }
    const float* A = kCanyonPresets[sPresetIdx];
    const float* B = kCanyonPresets[(sPresetIdx + 1) % kCanyonNumPresets];
    float tt = sPresetT * sPresetT * (3.0f - 2.0f * sPresetT);
    float P[kCanyonNumCols];
    for (int i = 0; i < kCanyonNumCols; i++) P[i] = A[i] + (B[i] - A[i]) * tt;

    // ---- camera (flies forward at speed + bass surge) ----
    float bass = bands.bass, mid = bands.mid;
    sCamZ += (double)dt * (double)P[C_speed] * (1.0 + (double)bass * 0.5);
    float camZ = (float)sCamZ;
    float eye[3] = { P[C_posX], P[C_posY], camZ };
    float tar[3] = { P[C_tarX], P[C_tarY], camZ + (P[C_tarZ] - P[C_posZ]) };
    float up[3] = { 0, 1, 0 };
    float fov = P[C_zoom] * (float)M_PI / 180.0f;
    float aspect = (float)fw / (float)fh;
    float proj[16], view[16], mvp[16];
    mPerspective(proj, fov, aspect, 0.5f, 2000.0f);
    mLookAt(view, eye, tar, up);
    mMul(mvp, proj, view);

    // ---- CPU heightfield (replaces the web's vertex texture fetch) ----
    float tWidth = P[C_tWidth] > 0.1f ? P[C_tWidth] : 0.1f;
    float tScaleX = P[C_tScaleX] > 0.1f ? P[C_tScaleX] : 0.1f;
    float texScale = P[C_tTexScale] > 0.1f ? P[C_tTexScale] : 0.1f;
    float tHeight = P[C_tHeight], tSlide = P[C_tSlide];
    float vHeight = P[C_vHeight], vCentre = P[C_vCentre];
    float vWidth = P[C_vWidth] > 0.0001f ? P[C_vWidth] : 0.0001f, vSharp = P[C_vSharp];
    float invUvx = 1.0f / (texScale * tScaleX * 8.0f);
    float invUvy = 1.0f / (texScale * 8.0f);
    float scrollY = camZ * tSlide;
    float vExp = 1.0f + vSharp * 3.0f;
    // valley + wx are per-column (depend on wx only); hoist them out of the z loop.
    static std::vector<float> valleyCol, wxCol;
    valleyCol.resize(GRID_W); wxCol.resize(GRID_W);
    for (int x = 0; x < GRID_W; x++) {
        float xn = (float)x / (float)(GRID_W - 1);
        float wx = (xn - 0.5f) * 2.0f * HALF_W * tWidth;
        wxCol[x] = wx;
        valleyCol[x] = vHeight * expf(-powf(fabsf(wx - vCentre) / vWidth, vExp)) * tHeight;
    }
    for (int z = 0; z < GRID_D; z++) {
        float zn = (float)z / (float)(GRID_D - 1);
        float wz = camZ + zn * DEPTH;
        float uvy = (wz + scrollY) * invUvy;
        float* row = &sHeightGrid[(size_t)z * GRID_W];
        for (int x = 0; x < GRID_W; x++) {
            float uvx = wxCol[x] * invUvx;
            row[x] = sampleHeight(uvx, uvy) * tHeight - valleyCol[x];
        }
    }
    for (int i = 0; i < sStripCount; i++) sHeightStrip[i] = sHeightGrid[sStripIdx[i]];
    glBindBuffer(GL_ARRAY_BUFFER, sHeightVBO);
    glBufferData(GL_ARRAY_BUFFER, (GLsizeiptr)(sStripCount * sizeof(float)),
                 sHeightStrip.data(), GL_DYNAMIC_DRAW);
    glBindBuffer(GL_ARRAY_BUFFER, 0);

    // ---- pass 1: terrain -> scene (half-res, depth) ----
    glBindFramebuffer(GL_FRAMEBUFFER, sScene.fb);
    glViewport(0, 0, sScene.w, sScene.h);
    glEnable(GL_DEPTH_TEST);
    glDisable(GL_BLEND);
    glClearColor(P[C_fogR], P[C_fogG], P[C_fogB], 1.0f);   // sky = fog colour
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    glUseProgram(sTerrProg);
    glBindBuffer(GL_ARRAY_BUFFER, sGridVBO);
    glEnableVertexAttribArray(tAGrid);
    glVertexAttribPointer(tAGrid, 2, GL_FLOAT, GL_FALSE, 0, (const void*)0);
    glBindBuffer(GL_ARRAY_BUFFER, sHeightVBO);
    glEnableVertexAttribArray(tAHeight);
    glVertexAttribPointer(tAHeight, 1, GL_FLOAT, GL_FALSE, 0, (const void*)0);
    glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, sNormalTex);
    glUniform1i(tNormal, 0);
    glUniformMatrix4fv(tMVP, 1, GL_FALSE, mvp);
    glUniform3f(tCamPos, eye[0], eye[1], eye[2]);
    glUniform1f(tScroll, camZ);
    glUniform1f(tHalfW, HALF_W); glUniform1f(tDepth, DEPTH);
    glUniform1f(tTWidth, tWidth); glUniform1f(tTScaleX, tScaleX);
    glUniform1f(tTexScale, texScale); glUniform1f(tTSlide, tSlide);
    glUniform1f(tTHeight, tHeight);
    glUniform1f(tFogMin, P[C_fogMin]);
    glUniform1f(tFogRange, (P[C_fogMax] - P[C_fogMin]) > 0.001f ? (P[C_fogMax] - P[C_fogMin]) : 0.001f);
    glUniform3f(tColour, P[C_colR], P[C_colG], P[C_colB]);
    glUniform1f(tColScale, P[C_colScale]); glUniform1f(tColBias, P[C_colBias]);
    glUniform3f(tLineColour, P[C_lineR], P[C_lineG], P[C_lineB]);
    glUniform3f(tFogColour, P[C_fogR], P[C_fogG], P[C_fogB]);
    glUniform1f(tBump, P[C_tBump]);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, sStripCount);
    glDisableVertexAttribArray(tAGrid);
    glDisableVertexAttribArray(tAHeight);
    glBindBuffer(GL_ARRAY_BUFFER, 0);

    // ---- pass 2: feedback compositor (scene + warped prev) -> feedB ----
    glDisable(GL_DEPTH_TEST);
    glBindFramebuffer(GL_FRAMEBUFFER, sFeedB.fb);
    glViewport(0, 0, sFeedB.w, sFeedB.h);
    glUseProgram(sBlurProg);
    glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, sScene.tex); glUniform1i(bCurrent, 0);
    glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, sFeedA.tex); glUniform1i(bPrev, 1);
    glUniform1f(bFeedback, P[C_feedback]);
    glUniform2f(bFeedScale, P[C_feedX], P[C_feedY]);
    glUniform1f(bFeedRot, P[C_feedRot] * (float)M_PI / 180.0f);
    drawQuad(bAPos);
    glActiveTexture(GL_TEXTURE0);
    // swap: feedA becomes the new persisted buffer (web swaps then presents feedA)
    RT tmp = sFeedA; sFeedA = sFeedB; sFeedB = tmp;

    // ---- pass 3: tonemap + composite feedA -> panel (DRM rotation + crossfade) ----
    glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)prevFbo);
    glViewport(prevVp[0], prevVp[1], prevVp[2], prevVp[3]);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glUseProgram(sCompProg);
    float fx0 = ps3::gFrameX, fy0 = ps3::gFrameY;
    float fx1 = fx0 + ps3::gFrameW, fy1 = fy0 + ps3::gFrameH;
    float pw = (float)panelW, ph = (float)panelH;
    float nx0 = fx0 / pw * 2.0f - 1.0f, nx1 = fx1 / pw * 2.0f - 1.0f;
    float ny0 = 1.0f - fy0 / ph * 2.0f, ny1 = 1.0f - fy1 / ph * 2.0f;
    const GLfloat cv[] = {
        nx0, ny1, 0.f, 0.f,   nx1, ny1, 1.f, 0.f,   nx1, ny0, 1.f, 1.f,
        nx0, ny1, 0.f, 0.f,   nx1, ny0, 1.f, 1.f,   nx0, ny0, 0.f, 1.f,
    };
    static const GLfloat kIdentity[4] = {1.f, 0.f, 0.f, 1.f};
    const GLfloat* rm = rotMat2 ? rotMat2 : kIdentity;
    glUniformMatrix2fv(cRot, 1, GL_FALSE, rm);
    glUniform1f(cExposure, 1.0f + mid * 0.3f);
    glUniform1f(cAlpha, alpha > 1.0f ? 1.0f : alpha);
    glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, sFeedA.tex); glUniform1i(cTex, 0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glVertexAttribPointer(cAPos, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat), cv);
    glEnableVertexAttribArray(cAPos);
    glVertexAttribPointer(cAUV, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat), cv + 2);
    glEnableVertexAttribArray(cAUV);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    glDisableVertexAttribArray(cAPos);
    glDisableVertexAttribArray(cAUV);
}

} // namespace ps3canyon
} // namespace android
