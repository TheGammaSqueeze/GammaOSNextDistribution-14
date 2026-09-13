// gpubench - offscreen GLES2 fill-rate benchmark.
// Renders a fragment-heavy shader over a fixed-size FBO for a fixed number of
// iterations, glFinish()es, and reports GPU work throughput (Mpixel/s and the
// wall time). GPU-bound by construction (tiny vertex load, heavy per-fragment
// ALU), so at a fixed workload the completion time scales inversely with the
// real GPU clock. Run it at a forced devfreq (userspace governor + set_freq)
// and compare 800 vs 900 MHz to validate the OPP actually delivers.

#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <stdint.h>

static double now_s() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec + ts.tv_nsec * 1e-9;
}

static const char* VS =
    "attribute vec2 p;\n"
    "void main(){ gl_Position = vec4(p,0.0,1.0); }\n";

// Heavy fragment shader: a long ALU loop so we are ALU/clock bound, not
// bandwidth bound. LOOPN controls the per-fragment cost.
static const char* FS =
    "precision highp float;\n"
    "uniform float u;\n"
    "void main(){\n"
    "  float a = u;\n"
    "  vec3 c = vec3(0.0);\n"
    "  for (int i=0;i<256;i++){\n"
    "    a = sin(a)*1.3 + cos(a*1.7)*0.7 + 0.001;\n"
    "    c += vec3(a, a*a, a*0.5);\n"
    "  }\n"
    "  gl_FragColor = vec4(fract(c), 1.0);\n"
    "}\n";

static GLuint compile(GLenum t, const char* src) {
    GLuint s = glCreateShader(t);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    GLint ok = 0; glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[2048]; glGetShaderInfoLog(s, sizeof(log), nullptr, log);
        fprintf(stderr, "shader compile failed: %s\n", log);
        exit(2);
    }
    return s;
}

int main(int argc, char** argv) {
    int dim = 512;         // FBO size
    int iters = 400;       // number of full-screen draws
    if (argc > 1) iters = atoi(argv[1]);
    if (argc > 2) dim = atoi(argv[2]);

    EGLDisplay dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglInitialize(dpy, nullptr, nullptr);
    EGLint cfgAttr[] = {
        EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8,
        EGL_NONE
    };
    EGLConfig cfg; EGLint n = 0;
    if (!eglChooseConfig(dpy, cfgAttr, &cfg, 1, &n) || n < 1) {
        fprintf(stderr, "eglChooseConfig failed\n"); return 3;
    }
    EGLint pb[] = { EGL_WIDTH, dim, EGL_HEIGHT, dim, EGL_NONE };
    EGLSurface surf = eglCreatePbufferSurface(dpy, cfg, pb);
    EGLint ctxAttr[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
    EGLContext ctx = eglCreateContext(dpy, cfg, EGL_NO_CONTEXT, ctxAttr);
    if (!eglMakeCurrent(dpy, surf, surf, ctx)) {
        fprintf(stderr, "eglMakeCurrent failed\n"); return 4;
    }

    GLuint vs = compile(GL_VERTEX_SHADER, VS);
    GLuint fs = compile(GL_FRAGMENT_SHADER, FS);
    GLuint prog = glCreateProgram();
    glAttachShader(prog, vs); glAttachShader(prog, fs);
    glBindAttribLocation(prog, 0, "p");
    glLinkProgram(prog);
    glUseProgram(prog);
    GLint uLoc = glGetUniformLocation(prog, "u");

    // full-screen triangle strip
    const float quad[] = { -1,-1, 1,-1, -1,1, 1,1 };
    GLuint vbo; glGenBuffers(1, &vbo);
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(quad), quad, GL_STATIC_DRAW);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, 0);

    glViewport(0, 0, dim, dim);

    // warm up
    for (int i = 0; i < 20; i++) {
        glUniform1f(uLoc, (float)i * 0.01f);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    }
    glFinish();

    double t0 = now_s();
    for (int i = 0; i < iters; i++) {
        glUniform1f(uLoc, (float)i * 0.013f);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    }
    glFinish();
    double t1 = now_s();

    double dt = t1 - t0;
    double pixels = (double)dim * dim * iters;
    printf("iters=%d dim=%d time=%.4fs draws/s=%.1f Mpix/s=%.1f\n",
           iters, dim, dt, iters / dt, pixels / dt / 1e6);
    return 0;
}
