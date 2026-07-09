#pragma once

// Native Vulkan shader pipeline for RetroArch .slangp presets.
// Compiles .slang shaders via glslang → SPIR-V, runs them natively on the GPU
// through Vulkan graphics pipelines — no transpilation to SkSL needed.
//
// This replaces GammaCustomShader's SkRuntimeEffect-based approach with a
// pipeline that mirrors RetroArch's own shader_vulkan backend, giving near-100%
// compatibility with the RetroArch shader ecosystem.

#include <vulkan/vulkan.h>

#include <SkSurface.h>
#include <ui/GraphicTypes.h>

#include <memory>
#include <mutex>
#include <string>
#include <vector>

namespace android {
namespace renderengine {
namespace skia {

class SkiaCapture;

// Opaque handle to the Vulkan device context obtained from SkiaVkRenderEngine.
struct GammaVkContext {
    VkInstance          instance       = VK_NULL_HANDLE;
    VkPhysicalDevice    physicalDevice = VK_NULL_HANDLE;
    VkDevice            device         = VK_NULL_HANDLE;
    VkQueue             queue          = VK_NULL_HANDLE;
    uint32_t            queueFamily    = 0;
    PFN_vkGetInstanceProcAddr getInstanceProcAddr = nullptr;
    PFN_vkGetDeviceProcAddr   getDeviceProcAddr   = nullptr;
};

// Retrieve the Vulkan context from the render engine.
// Implemented in SkiaVkRenderEngine.cpp.
bool gammaGetVkContext(GammaVkContext& out);

// ---------------------------------------------------------------------------
// Parsed .slangp preset types (shared with GammaCustomShader)
// ---------------------------------------------------------------------------

struct GammaSlangParam {
    std::string id;
    std::string desc;
    float initial = 0.0f;
    float minimum = 0.0f;
    float maximum = 1.0f;
    float step    = 0.01f;
    float current = 0.0f;
};

struct GammaSlangLut {
    std::string id;
    std::string path;
    bool filterLinear = true;
    bool mipmap       = false;
    // Runtime Vulkan resources (populated after init)
    VkImage     image  = VK_NULL_HANDLE;
    VkImageView view   = VK_NULL_HANDLE;
    VkSampler   sampler = VK_NULL_HANDLE;
    VkDeviceMemory memory = VK_NULL_HANDLE;
    int width  = 0;
    int height = 0;
};

struct GammaSlangPass {
    std::string shaderPath;
    std::string alias;
    bool filterLinear  = false;
    int  scaleTypeX    = 0;   // 0=source, 1=absolute, 2=viewport
    int  scaleTypeY    = 0;
    float scaleX       = 1.0f;
    float scaleY       = 1.0f;
    int   absX         = 0;
    int   absY         = 0;
    bool  srgbFbo      = false;
    bool  floatFbo     = false;
    bool  mipmap       = false;
    int   frameCountMod = 0;
    bool  feedback     = false;

    // Compiled SPIR-V (populated during compilation)
    std::vector<uint32_t> vertexSpirv;
    std::vector<uint32_t> fragmentSpirv;
};

struct GammaSlangPreset {
    std::string presetPath;
    std::string presetDir;
    std::vector<GammaSlangPass>  passes;
    std::vector<GammaSlangLut>   luts;
    std::vector<GammaSlangParam> params;
    int  feedbackPass = -1;
    bool valid        = false;
};

// ---------------------------------------------------------------------------
// Per-pass Vulkan resources
// ---------------------------------------------------------------------------

struct VkPassResources {
    VkShaderModule   vertModule   = VK_NULL_HANDLE;
    VkShaderModule   fragModule   = VK_NULL_HANDLE;
    VkPipelineLayout layout       = VK_NULL_HANDLE;
    VkPipeline       pipeline     = VK_NULL_HANDLE;
    VkRenderPass     renderPass   = VK_NULL_HANDLE;
    VkDescriptorSetLayout descSetLayout = VK_NULL_HANDLE;

    // Intermediate framebuffer (not used for the final pass)
    VkImage        fbImage        = VK_NULL_HANDLE;
    VkDeviceMemory fbMemory       = VK_NULL_HANDLE;
    VkDeviceSize   fbMemorySize   = 0;   // allocation size, needed to wrap the output for Skia
    VkImageView    fbView         = VK_NULL_HANDLE;
    VkFramebuffer  framebuffer    = VK_NULL_HANDLE;
    VkSampler      fbSampler      = VK_NULL_HANDLE;
    int fbWidth  = 0;
    int fbHeight = 0;
    VkFormat fbFormat = VK_FORMAT_R8G8B8A8_UNORM;

    // Feedback texture (previous frame's output for this pass)
    VkImage        feedbackImage   = VK_NULL_HANDLE;
    VkDeviceMemory feedbackMemory  = VK_NULL_HANDLE;
    VkImageView    feedbackView    = VK_NULL_HANDLE;
    int feedbackW = 0, feedbackH = 0;

    // UBO for uniform data
    VkBuffer       ubo       = VK_NULL_HANDLE;
    VkDeviceMemory uboMemory = VK_NULL_HANDLE;
    uint32_t       uboSize   = 0;
    void*          uboMapped = nullptr;

    // Push constant data & size
    uint32_t       pushConstantSize = 0;

    // Reflection data: which uniform offsets / texture bindings are used
    struct UniformOffsets {
        int mvp            = -1;  // mat4
        int outputSize     = -1;  // vec4
        int finalViewport  = -1;  // vec4
        int frameCount     = -1;  // uint
        int frameDirection = -1;  // int
        int originalSize   = -1;  // vec4
        int sourceSize     = -1;  // vec4
        // Texture size uniforms indexed by texture semantic
        int passOutputSize[16] = {};   // vec4 each
        int passFeedbackSize[16] = {}; // vec4 each
        int originalHistorySize[16] = {}; // vec4 each
        int userSize[16]   = {};       // vec4 each, for LUT textures
        // User float parameters
        struct ParamOffset {
            int offset = -1;
        };
        std::vector<ParamOffset> paramOffsets;

        UniformOffsets() {
            for (auto& v : passOutputSize) v = -1;
            for (auto& v : passFeedbackSize) v = -1;
            for (auto& v : originalHistorySize) v = -1;
            for (auto& v : userSize) v = -1;
        }
    };

    struct TextureBinding {
        int binding = -1;  // descriptor binding index
    };

    UniformOffsets uboOffsets;
    UniformOffsets pushOffsets;

    // Texture binding indices
    TextureBinding texOriginal;
    TextureBinding texSource;
    TextureBinding texPassOutput[16];
    TextureBinding texPassFeedback[16];
    TextureBinding texOriginalHistory[16];
    TextureBinding texUser[16];   // LUT textures
};

// ---------------------------------------------------------------------------
// The filter chain: manages all passes, textures, and rendering
// ---------------------------------------------------------------------------

class GammaVulkanFilterChain {
public:
    GammaVulkanFilterChain();
    ~GammaVulkanFilterChain();

    // Initialize the filter chain with a parsed preset.
    // Compiles shaders, creates pipelines and framebuffers.
    bool init(const GammaVkContext& ctx, GammaSlangPreset& preset,
              int displayW = 1920, int displayH = 1080);

    // Execute the filter chain for one frame.
    // srcImage/srcView: the input frame (from Skia's srcSurface)
    // dstImage: the output target (Skia's dstSurface)
    // srcW/srcH: source dimensions
    // viewW/viewH: viewport (output) dimensions
    // frameCount: current frame number
    // Render all passes to internal FBOs.  The final pass output is in the
    // last pass's fbImage/fbView which can be wrapped as an SkImage.
    bool render(VkCommandBuffer cmd,
                VkImageView srcView, VkSampler srcSampler,
                int srcW, int srcH,
                int viewW, int viewH,
                uint32_t frameCount);

    // GPU-direct render: takes a VkImage (from Skia's surface) as input,
    // renders all passes, returns output VkImage info.
    // No CPU readback — all data stays on GPU.
    bool renderFromVkImage(VkImage srcImage, int srcW, int srcH,
                           int viewW, int viewH, uint32_t frameCount);

    // CPU readback render (for GL backend): reads pixels in, renders, reads pixels out.
    bool renderFromPixels(const void* srcPixels, int srcW, int srcH,
                          void* outPixels, int dstW, int dstH,
                          uint32_t frameCount);

    // Access the last pass's output image (valid after render()).
    VkImage        getOutputImage()     const;
    VkImageView    getOutputView()      const;
    // The output image's backing memory + its allocation size, so the caller can
    // wrap the output VkImage as a Skia texture (BorrowTextureFrom needs fAlloc).
    VkDeviceMemory getOutputMemory()    const;
    VkDeviceSize   getOutputAllocSize() const;
    int         getOutputWidth()  const;
    int         getOutputHeight() const;
    VkFormat    getOutputFormat()  const;

    // Release all Vulkan resources.
    void destroy();

    // Check if initialized successfully.
    bool isValid() const { return mValid; }

    // Update parameter values (triggers UBO update, no recompilation needed).
    void setParam(const std::string& id, float value);

    // GammaOS: advertise a lower effect source resolution (SourceSize/OriginalSize)
    // to the shader so CRT scanlines / masks come out coarse and visible while the
    // sampled texture stays full-res. This decouples effect density from base
    // resolution, mirroring the GLSL chain's scanline_density control. 0 = off.
    void setEffectSource(int w, int h) { mEffW = w; mEffH = h; }

    // Allocate/free a one-shot command buffer from the internal command pool.
    VkCommandBuffer allocCommandBuffer();
    void freeCommandBuffer(VkCommandBuffer cmd);

    // Create a VkImageView for an external VkImage.
    VkImageView createExternalImageView(VkImage image, VkFormat format);
    void destroyImageView(VkImageView view);

    // Create a sampler (exposed for source texture).
    bool createSourceSampler(VkSampler& out) { return createSampler(true, false, out); }

    // Submit a command buffer and wait for completion.
    bool submitAndWait(VkCommandBuffer cmd);

    // Begin recording a command buffer (one-time submit).
    bool beginCommandBuffer(VkCommandBuffer cmd);
    // End recording.
    bool endCommandBuffer(VkCommandBuffer cmd);

    // Transition an image layout.
    void transitionImageLayout(VkCommandBuffer cmd, VkImage image,
                               VkImageLayout oldLayout, VkImageLayout newLayout) {
        transitionImage(cmd, image, oldLayout, newLayout);
    }

    const GammaVkContext& getContext() const { return mCtx; }

private:
    // Managed source texture for pixel upload path
    VkImage        mSrcImage   = VK_NULL_HANDLE;
    VkDeviceMemory mSrcMemory  = VK_NULL_HANDLE;
    VkImageView    mSrcView    = VK_NULL_HANDLE;
    VkSampler      mSrcSampler = VK_NULL_HANDLE;
    int            mSrcW = 0, mSrcH = 0;

    // Staging buffers for upload/download
    VkBuffer       mStagingBuf    = VK_NULL_HANDLE;
    VkDeviceMemory mStagingMemory = VK_NULL_HANDLE;
    VkDeviceSize   mStagingSize   = 0;

    bool ensureSrcTexture(int w, int h);
    bool ensureStagingBuffer(VkDeviceSize size);

    bool compileSlangShader(const std::string& path,
                            std::vector<uint32_t>& vertexSpirv,
                            std::vector<uint32_t>& fragmentSpirv,
                            std::vector<GammaSlangParam>& params);
    bool reflectPass(int passIndex);
    bool createPassPipeline(int passIndex, int viewW, int viewH);
    bool createPassFramebuffer(int passIndex, int width, int height);
    bool createUBO(int passIndex);
    void fillUBO(int passIndex, int srcW, int srcH, int viewW, int viewH,
                 int passW, int passH, uint32_t frameCount);
    void computePassSize(const GammaSlangPass& pass,
                         int srcW, int srcH, int viewW, int viewH,
                         int& outW, int& outH);

    bool createImage(int width, int height, VkFormat format, VkImageUsageFlags usage,
                     VkImage& image, VkDeviceMemory& memory, VkImageView& view,
                     VkDeviceSize* outMemSize = nullptr);
    bool createSampler(bool linear, bool mipmap, VkSampler& sampler);
    uint32_t findMemoryType(uint32_t typeFilter, VkMemoryPropertyFlags properties);
    void transitionImage(VkCommandBuffer cmd, VkImage image,
                         VkImageLayout oldLayout, VkImageLayout newLayout);

    bool loadLutTexture(GammaSlangLut& lut, VkCommandBuffer cmd);
    bool loadLutTextures(VkCommandBuffer cmd);

    GammaVkContext mCtx = {};
    GammaSlangPreset* mPreset = nullptr;
    std::vector<VkPassResources> mPasses;

    // Advertised effect source size (SourceSize/OriginalSize decouple). 0 = off.
    int mEffW = 0, mEffH = 0;

    // Frame history ring buffer
    static constexpr int kMaxHistory = 8;
    struct HistoryFrame {
        VkImage        image   = VK_NULL_HANDLE;
        VkDeviceMemory memory  = VK_NULL_HANDLE;
        VkImageView    view    = VK_NULL_HANDLE;
        int width = 0, height = 0;
    };
    std::vector<HistoryFrame> mHistory;
    int mHistorySize = 0;

    // Vertex buffer for fullscreen quad
    VkBuffer       mVertexBuffer = VK_NULL_HANDLE;
    VkDeviceMemory mVertexMemory = VK_NULL_HANDLE;

    // Command pool
    VkCommandPool mCommandPool = VK_NULL_HANDLE;

    // Descriptor pool
    VkDescriptorPool mDescriptorPool = VK_NULL_HANDLE;
    std::vector<VkDescriptorSet> mDescriptorSets;

    // Vulkan function pointers (loaded via getDeviceProcAddr)
    PFN_vkCreateShaderModule           vkCreateShaderModule_  = nullptr;
    PFN_vkDestroyShaderModule          vkDestroyShaderModule_ = nullptr;
    PFN_vkCreatePipelineLayout         vkCreatePipelineLayout_ = nullptr;
    PFN_vkDestroyPipelineLayout        vkDestroyPipelineLayout_ = nullptr;
    PFN_vkCreateGraphicsPipelines      vkCreateGraphicsPipelines_ = nullptr;
    PFN_vkDestroyPipeline              vkDestroyPipeline_ = nullptr;
    PFN_vkCreateRenderPass             vkCreateRenderPass_ = nullptr;
    PFN_vkDestroyRenderPass            vkDestroyRenderPass_ = nullptr;
    PFN_vkCreateFramebuffer            vkCreateFramebuffer_ = nullptr;
    PFN_vkDestroyFramebuffer           vkDestroyFramebuffer_ = nullptr;
    PFN_vkCreateImageView              vkCreateImageView_ = nullptr;
    PFN_vkDestroyImageView             vkDestroyImageView_ = nullptr;
    PFN_vkCreateImage                  vkCreateImage_ = nullptr;
    PFN_vkDestroyImage                 vkDestroyImage_ = nullptr;
    PFN_vkAllocateMemory               vkAllocateMemory_ = nullptr;
    PFN_vkFreeMemory                   vkFreeMemory_ = nullptr;
    PFN_vkBindImageMemory              vkBindImageMemory_ = nullptr;
    PFN_vkGetImageMemoryRequirements   vkGetImageMemoryRequirements_ = nullptr;
    PFN_vkCreateBuffer                 vkCreateBuffer_ = nullptr;
    PFN_vkDestroyBuffer                vkDestroyBuffer_ = nullptr;
    PFN_vkBindBufferMemory             vkBindBufferMemory_ = nullptr;
    PFN_vkGetBufferMemoryRequirements  vkGetBufferMemoryRequirements_ = nullptr;
    PFN_vkMapMemory                    vkMapMemory_ = nullptr;
    PFN_vkUnmapMemory                  vkUnmapMemory_ = nullptr;
    PFN_vkCreateSampler                vkCreateSampler_ = nullptr;
    PFN_vkDestroySampler               vkDestroySampler_ = nullptr;
    PFN_vkCreateDescriptorSetLayout    vkCreateDescriptorSetLayout_ = nullptr;
    PFN_vkDestroyDescriptorSetLayout   vkDestroyDescriptorSetLayout_ = nullptr;
    PFN_vkCreateDescriptorPool         vkCreateDescriptorPool_ = nullptr;
    PFN_vkDestroyDescriptorPool        vkDestroyDescriptorPool_ = nullptr;
    PFN_vkAllocateDescriptorSets       vkAllocateDescriptorSets_ = nullptr;
    PFN_vkUpdateDescriptorSets         vkUpdateDescriptorSets_ = nullptr;
    PFN_vkAllocateCommandBuffers       vkAllocateCommandBuffers_ = nullptr;
    PFN_vkFreeCommandBuffers           vkFreeCommandBuffers_ = nullptr;
    PFN_vkCreateCommandPool            vkCreateCommandPool_ = nullptr;
    PFN_vkDestroyCommandPool           vkDestroyCommandPool_ = nullptr;
    PFN_vkBeginCommandBuffer           vkBeginCommandBuffer_ = nullptr;
    PFN_vkEndCommandBuffer             vkEndCommandBuffer_ = nullptr;
    PFN_vkQueueSubmit                  vkQueueSubmit_ = nullptr;
    PFN_vkQueueWaitIdle                vkQueueWaitIdle_ = nullptr;
    PFN_vkCreateFence                  vkCreateFence_ = nullptr;
    PFN_vkDestroyFence                 vkDestroyFence_ = nullptr;
    PFN_vkWaitForFences                vkWaitForFences_ = nullptr;
    PFN_vkResetFences                  vkResetFences_ = nullptr;
    PFN_vkResetCommandPool             vkResetCommandPool_ = nullptr;
    PFN_vkCmdBeginRenderPass           vkCmdBeginRenderPass_ = nullptr;
    PFN_vkCmdEndRenderPass             vkCmdEndRenderPass_ = nullptr;
    PFN_vkCmdBindPipeline              vkCmdBindPipeline_ = nullptr;
    PFN_vkCmdBindDescriptorSets        vkCmdBindDescriptorSets_ = nullptr;
    PFN_vkCmdBindVertexBuffers         vkCmdBindVertexBuffers_ = nullptr;
    PFN_vkCmdDraw                      vkCmdDraw_ = nullptr;
    PFN_vkCmdSetViewport               vkCmdSetViewport_ = nullptr;
    PFN_vkCmdSetScissor                vkCmdSetScissor_ = nullptr;
    PFN_vkCmdPipelineBarrier           vkCmdPipelineBarrier_ = nullptr;
    PFN_vkCmdPushConstants             vkCmdPushConstants_ = nullptr;
    PFN_vkCmdCopyBufferToImage         vkCmdCopyBufferToImage_ = nullptr;
    PFN_vkCmdCopyImageToBuffer         vkCmdCopyImageToBuffer_ = nullptr;
    PFN_vkFlushMappedMemoryRanges      vkFlushMappedMemoryRanges_ = nullptr;
    PFN_vkGetPhysicalDeviceMemoryProperties vkGetPhysicalDeviceMemoryProperties_ = nullptr;

    VkPhysicalDeviceMemoryProperties mMemoryProperties = {};
    bool mValid = false;
    bool mVkFuncsLoaded = false;
    bool loadVkFuncs();

};

// ---------------------------------------------------------------------------
// Public API (drop-in replacement for GammaCustomShader::apply)
// ---------------------------------------------------------------------------

struct GammaVulkanShaderChain {
    static bool apply(SkSurface* dstSurface,
                      SkSurface* srcSurface,
                      SkiaCapture* capture,
                      ui::Dataspace outDataspace,
                      bool isProtected,
                      bool testOverlay,
                      bool ctmBfiBlack,
                      float defaultScanAngleDeg);
};

// Parse a .slangp preset file into a GammaSlangPreset.
bool parseGammaSlangPreset(const std::string& path, GammaSlangPreset& out);

} // namespace skia
} // namespace renderengine
} // namespace android
