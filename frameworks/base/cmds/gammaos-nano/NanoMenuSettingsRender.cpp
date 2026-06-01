/*
 * Copyright (C) 2026 GammaOS
 *
 * Rendering for the hierarchical settings browser. Draws a full-screen
 * modal with breadcrumbs, scrollable item list, and value indicators
 * for each setting type.
 */

#define LOG_TAG "GammaOSNano"

#include <cmath>
#include <cstdio>
#include <string>
#include <vector>

#include <log/log.h>
#include <GLES2/gl2.h>

#include "NanoMenu.h"
#include "NanoMenuSettingsTree.h"
#include "NanoMenuShaders.h"

namespace android {

void NanoMenu::renderSettingsTree() {
    float sf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
    if (sf < 0.5f) sf = 0.5f;

    drawQuad(0, 0, mWidth, mHeight, 0.05f, 0.05f, 0.08f, 0.92f);

    float pad = 24.0f * sf;
    float titleScale = 2.4f * sf;
    float breadcrumbScale = 1.4f * sf;
    float rowScale = 1.9f * sf;
    float valueScale = 1.5f * sf;
    float footScale = 1.3f * sf;

    std::string breadcrumb = settingsTreeBreadcrumb();
    drawText(breadcrumb.c_str(), pad, pad, titleScale,
             0.90f, 0.90f, 1.0f, 1.0f);

    float headerH = pad + FONT_CHAR_H * titleScale + 8.0f * sf;

    if (mSettingsNavStack.size() > 1) {
        float bY = headerH;
        std::string parentLabel = "< Back";
        drawText(parentLabel.c_str(), pad, bY, breadcrumbScale,
                 0.50f, 0.50f, 0.55f, 0.7f);
        headerH = bY + FONT_CHAR_H * breadcrumbScale + 6.0f * sf;
    }

    float listTop = headerH + 4.0f * sf;
    float rowH = FONT_CHAR_H * rowScale + 12.0f * sf;
    int visibleRows = (int)((mHeight - listTop - 50.0f * sf) / rowH);
    if (visibleRows < 3) visibleRows = 3;

    int numItems = (int)mSettingsTreeVisible.size();

    if (numItems == 0) {
        drawText("No items", pad, listTop, rowScale,
                 0.5f, 0.5f, 0.55f, 0.7f);
    } else {
        if (mSettingsTreeSelected < mSettingsTreeScrollTop)
            mSettingsTreeScrollTop = mSettingsTreeSelected;
        if (mSettingsTreeSelected >= mSettingsTreeScrollTop + visibleRows)
            mSettingsTreeScrollTop = mSettingsTreeSelected - visibleRows + 1;
        if (mSettingsTreeScrollTop < 0) mSettingsTreeScrollTop = 0;

        int end = mSettingsTreeScrollTop + visibleRows;
        if (end > numItems) end = numItems;

        for (int i = mSettingsTreeScrollTop; i < end; i++) {
            float y = listTop + (float)(i - mSettingsTreeScrollTop) * rowH;
            bool sel = (i == mSettingsTreeSelected);

            int nodeIdx = mSettingsTreeVisible[i];
            if (nodeIdx < 0 || nodeIdx >= (int)mSettingsNodes.size()) continue;
            const auto& node = mSettingsNodes[nodeIdx];

            if (sel) {
                drawQuad(pad - 6.0f * sf, y - 2.0f * sf,
                         mWidth - pad * 2.0f + 12.0f * sf,
                         rowH - 4.0f * sf,
                         0.15f, 0.30f, 0.65f, 0.60f);
            }

            float textX = pad + 8.0f * sf;
            float textY = y + rowH * 0.15f;
            float labelR = sel ? 1.0f : 0.85f;
            float labelG = sel ? 1.0f : 0.85f;
            float labelB = sel ? 1.0f : 0.88f;
            float labelA = (node.type == SettingNodeType::kInfo) ? 0.65f : 1.0f;

            drawText(node.label.c_str(), textX, textY, rowScale,
                     labelR, labelG, labelB, labelA);

            float rightEdge = mWidth - pad - 4.0f * sf;

            switch (node.type) {
            case SettingNodeType::kCategory:
            case SettingNodeType::kScreen: {
                const char* arrow = ">";
                float aw = measureText(arrow, rowScale);
                drawText(arrow, rightEdge - aw, textY, rowScale,
                         0.6f, 0.6f, 0.65f, 0.8f);
                break;
            }
            case SettingNodeType::kToggle: {
                std::string val = getSettingsCachedValue(nodeIdx);
                bool isOn = (val == "1" || val == "true");
                const char* pill = isOn ? "[ On ]" : "[ Off ]";
                float pScale = rowScale * 0.85f;
                float pw = measureText(pill, pScale);
                drawText(pill, rightEdge - pw, y + rowH * 0.18f,
                         pScale,
                         isOn ? 0.35f : 0.80f,
                         isOn ? 0.90f : 0.40f,
                         isOn ? 0.35f : 0.40f, 1.0f);
                break;
            }
            case SettingNodeType::kText:
            case SettingNodeType::kInfo: {
                std::string val = getSettingsCachedValue(nodeIdx);
                if (val.empty()) val = "-";
                if (val.size() > 30) val = val.substr(0, 27) + "...";
                float vw = measureText(val.c_str(), valueScale);
                float maxValW = mWidth * 0.40f;
                if (vw > maxValW) {
                    while (val.size() > 4 && measureText(val.c_str(), valueScale) > maxValW) {
                        val = val.substr(0, val.size() - 4) + "...";
                    }
                    vw = measureText(val.c_str(), valueScale);
                }
                float valR = (node.type == SettingNodeType::kInfo) ? 0.55f : 0.85f;
                float valG = (node.type == SettingNodeType::kInfo) ? 0.55f : 0.85f;
                float valB = (node.type == SettingNodeType::kInfo) ? 0.60f : 0.50f;
                drawText(val.c_str(), rightEdge - vw, y + rowH * 0.22f,
                         valueScale, valR, valG, valB, 0.90f);
                break;
            }
            case SettingNodeType::kList: {
                std::string val = getSettingsCachedValue(nodeIdx);
                auto opts = parseListOptions(node.options);
                std::string display = val;
                for (const auto& o : opts) {
                    if (o.value == val) { display = o.label; break; }
                }
                if (display.size() > 25) display = display.substr(0, 22) + "...";
                std::string listStr = "< " + display + " >";
                float lw = measureText(listStr.c_str(), valueScale);
                drawText(listStr.c_str(), rightEdge - lw, y + rowH * 0.22f,
                         valueScale, 0.85f, 0.85f, 0.50f, 0.90f);
                break;
            }
            case SettingNodeType::kAction: {
                const char* arrow = ">";
                float aw = measureText(arrow, rowScale);
                drawText(arrow, rightEdge - aw, textY, rowScale,
                         0.90f, 0.55f, 0.35f, 0.8f);
                break;
            }
            }
        }

        if (numItems > visibleRows) {
            float scrollBarH = mHeight - listTop - 50.0f * sf;
            float thumbH = scrollBarH * ((float)visibleRows / (float)numItems);
            if (thumbH < 10.0f * sf) thumbH = 10.0f * sf;
            float scrollRange = scrollBarH - thumbH;
            float thumbY = listTop + scrollRange *
                ((float)mSettingsTreeScrollTop / (float)(numItems - visibleRows));
            float barX = mWidth - 6.0f * sf;
            drawQuad(barX, listTop, 4.0f * sf, scrollBarH,
                     0.2f, 0.2f, 0.25f, 0.3f);
            drawQuad(barX, thumbY, 4.0f * sf, thumbH,
                     0.5f, 0.5f, 0.6f, 0.6f);
        }
    }

    const char* footer = "A: Select | B: Back | L/R: Adjust";
    float fw = measureText(footer, footScale);
    drawText(footer, (mWidth - fw) / 2.0f,
             mHeight - FONT_CHAR_H * footScale - 10.0f * sf,
             footScale, 0.50f, 0.50f, 0.55f, 0.85f);

    // OSK (with its inline preview line) is drawn last in render() by renderOsk().
}

} // namespace android
