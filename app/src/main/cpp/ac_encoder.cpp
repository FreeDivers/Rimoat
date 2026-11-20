#include <jni.h>
#include <dlfcn.h>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <vector>
#include <limits>

// Serialize access to the native state encoder and its shared output buffer.
namespace {
std::mutex encoderMutex;
struct Request {
    uint16_t protocol;
    uint16_t key;
    uint32_t counter;
    uint32_t timerMinutes;
    uint8_t state[20];
};
static_assert(sizeof(Request) == 32);
void fail(JNIEnv* env, const char* message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_qinyan_rimoat_data_NativeAcEncoder_supportedAbi(JNIEnv*, jobject) {
#if defined(__aarch64__) || defined(__arm__)
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT jintArray JNICALL
Java_org_qinyan_rimoat_data_NativeAcEncoder_encodeFrame(
    JNIEnv* env, jobject, jint protocol, jint key, jint counter, jint timer, jbyteArray state) {
    std::lock_guard<std::mutex> lock(encoderMutex);
    if (!state || env->GetArrayLength(state) != 20 || protocol < 1 || protocol > 791 ||
        key < 0 || key > 65535 || timer < 0 || timer > 1440) {
        fail(env, "Invalid AC encoding request"); return nullptr;
    }
    static void* library = dlopen("liblocalirdb.so", RTLD_NOW | RTLD_LOCAL);
    if (!library) { fail(env, "Local AC encoder unavailable"); return nullptr; }
    static void* symbol = dlsym(library, "Java_com_tiqiaa_local_LocalIrDb_irdbGetAirCode");
    static Dl_info info{};
    static const bool resolved = symbol && dladdr(symbol, &info);
    if (!resolved) { fail(env, "Unsupported AC encoder image"); return nullptr; }
    auto base = reinterpret_cast<uintptr_t>(info.dli_fbase);
#if defined(__aarch64__)
    constexpr uintptr_t entry = 0x4bc0b0, countOffset = 0x6d7d00, pulsesOffset = 0x6d6260;
#elif defined(__arm__)
    constexpr uintptr_t entry = 0x2ee811, countOffset = 0x420c10, pulsesOffset = 0x41f210;
#else
    // Non-ARM devices use the portable basic-state store.
    return nullptr;
    constexpr uintptr_t entry = 0, countOffset = 0, pulsesOffset = 0;
#endif
    Request request{};
    request.protocol = protocol; request.key = key;
    request.counter = static_cast<uint32_t>(counter); request.timerMinutes = timer;
    env->GetByteArrayRegion(state, 0, 20, reinterpret_cast<jbyte*>(request.state));
    if (env->ExceptionCheck()) return nullptr;
    int frequency = 0;
    *reinterpret_cast<int*>(base + countOffset) = 0;
    auto encode = reinterpret_cast<int (*)(Request*, int*)>(base + entry);
    const int result = encode(&request, &frequency);
    const int count = *reinterpret_cast<int*>(base + countOffset);
    if (result <= 0 || count <= 0 || count > 800) return nullptr;
    if (frequency == 0) frequency = 38000;
    if (frequency < 10000 || frequency > 100000) { fail(env, "Invalid AC carrier frequency"); return nullptr; }
    const auto pulses = reinterpret_cast<int32_t*>(base + pulsesOffset);
    std::vector<jint> output(count + 1);
    output[0] = frequency;
    int64_t duration = 40000;
    for (int i = 0; i < count; ++i) {
        int64_t pulse = pulses[i];
        if (pulse < 0) pulse = -pulse;
        duration += pulse;
        if (pulse == 0 || duration >= 2000000) return nullptr;
        output[i + 1] = static_cast<jint>(pulse);
    }
    jintArray array = env->NewIntArray(output.size());
    if (array) env->SetIntArrayRegion(array, 0, output.size(), output.data());
    return array;
}
