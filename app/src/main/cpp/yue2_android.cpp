#include <jni.h>
#include <atomic>
#include <string>
#include <vector>
#define main yue2_server_entry
#include "tools/yue-server.cpp"
#undef main
static std::atomic<bool> s_running{false};
extern "C" JNIEXPORT jint JNICALL Java_com_bomber16693_s26creativestudio_NativeYuE_runServer(JNIEnv* env,jobject,jstring model,jstring vae){
    bool expected=false; if(!s_running.compare_exchange_strong(expected,true))return -2;
    const char* m=env->GetStringUTFChars(model,nullptr);
    const char* v=env->GetStringUTFChars(vae,nullptr);
    std::string mp(m),vp(v);
    env->ReleaseStringUTFChars(model,m);env->ReleaseStringUTFChars(vae,v);
    std::vector<std::string> args={"yue2mobile","--model",mp,"--vae",vp,"--host","127.0.0.1","--port","8087","--device","CPU","--max-batch","1"};
    std::vector<char*> argv;
    for(auto& a:args) argv.push_back(a.data());
    int result=yue2_server_entry((int)argv.size(),argv.data());
    s_running.store(false);
    return result;
}
extern "C" JNIEXPORT void JNICALL Java_com_bomber16693_s26creativestudio_NativeYuE_stopServer(JNIEnv*,jobject){
    if(g_svr)g_svr->stop();
}
