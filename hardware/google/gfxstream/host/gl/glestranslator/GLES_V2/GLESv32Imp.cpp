// Auto-generated with: android/scripts/gen-entries.py --mode=translator_passthrough stream-servers/OpenGLESDispatch/gles32_only.entries --output=stream-servers/glestranslator/GLES_V2/GLESv32Imp.cpp
// This file is best left unedited.
// Try to make changes through gen_translator in gen-entries.py,
// and/or parcel out custom functionality in separate code.
GL_APICALL void GL_APIENTRY glDebugMessageControl(GLenum source, GLenum type, GLenum severity, GLsizei count, const GLuint* ids, GLboolean enabled) {
    GET_CTX_V2();
    SET_ERROR_IF_DISPATCHER_NOT_SUPPORT(glDebugMessageControl);
    ctx->dispatcher().glDebugMessageControl(source, type, severity, count, ids, enabled);
}

GL_APICALL void GL_APIENTRY glDebugMessageInsert(GLenum source, GLenum type, GLuint id, GLenum severity, GLsizei length, const GLchar* buf) {
    GET_CTX_V2();
    SET_ERROR_IF_DISPATCHER_NOT_SUPPORT(glDebugMessageInsert);
    ctx->dispatcher().glDebugMessageInsert(source, type, id, severity, length, buf);
}

GL_APICALL void GL_APIENTRY glDebugMessageCallback(GLDEBUGPROC callback, const void* userParam) {
    GET_CTX_V2();
    SET_ERROR_IF_DISPATCHER_NOT_SUPPORT(glDebugMessageCallback);
    ctx->dispatcher().glDebugMessageCallback(callback, userParam);
}

GL_APICALL GLuint GL_APIENTRY glGetDebugMessageLog(GLuint count, GLsizei bufSize, GLenum* sources, GLenum* types, GLuint* ids, GLenum* severities, GLsizei* lengths, GLchar* messageLog) {
    GET_CTX_V2_RET(0);
    RET_AND_SET_ERROR_IF_DISPATCHER_NOT_SUPPORT(glGetDebugMessageLog, 0);
    GLuint glGetDebugMessageLogRET = ctx->dispatcher().glGetDebugMessageLog(count, bufSize, sources, types, ids, severities, lengths, messageLog);
    return glGetDebugMessageLogRET;
}

GL_APICALL void GL_APIENTRY glPushDebugGroup(GLenum source, GLuint id, GLsizei length, const GLchar* message) {
    GET_CTX_V2();
    SET_ERROR_IF_DISPATCHER_NOT_SUPPORT(glPushDebugGroup);
    ctx->dispatcher().glPushDebugGroup(source, id, length, message);
}

GL_APICALL void GL_APIENTRY glPopDebugGroup() {
    GET_CTX_V2();
    SET_ERROR_IF_DISPATCHER_NOT_SUPPORT(glPopDebugGroup);
    ctx->dispatcher().glPopDebugGroup();
}
