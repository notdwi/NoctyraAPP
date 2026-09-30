#include <jni.h>
#include <memory>
#include <string>

#define DISCORDPP_IMPLEMENTATION
#include "discordpp.h"

// Ponte mínima entre o DiscordBridge (Kotlin) e o Discord Social SDK.
// Todas as chamadas acontecem na thread principal; os callbacks do SDK rodam dentro de RunCallbacks.

namespace {

JavaVM *gVm = nullptr;
jobject gBridge = nullptr;
std::shared_ptr<discordpp::Client> gClient;
uint64_t gAppId = 0;

JNIEnv *env() {
    JNIEnv *e = nullptr;
    gVm->GetEnv(reinterpret_cast<void **>(&e), JNI_VERSION_1_6);
    return e;
}

std::string toStd(JNIEnv *e, jstring s) {
    if (!s) return {};
    const jsize len = e->GetStringLength(s);
    const jchar *chars = e->GetStringChars(s, nullptr);
    std::string out;
    out.reserve(static_cast<size_t>(len) * 2);
    for (jsize i = 0; i < len; ++i) {
        uint32_t cp = chars[i];
        if (cp >= 0xD800 && cp <= 0xDBFF && i + 1 < len && chars[i + 1] >= 0xDC00 && chars[i + 1] <= 0xDFFF) {
            cp = 0x10000 + ((cp - 0xD800) << 10) + (chars[++i] - 0xDC00);
        }
        if (cp < 0x80) {
            out += static_cast<char>(cp);
        } else if (cp < 0x800) {
            out += static_cast<char>(0xC0 | (cp >> 6));
            out += static_cast<char>(0x80 | (cp & 0x3F));
        } else if (cp < 0x10000) {
            out += static_cast<char>(0xE0 | (cp >> 12));
            out += static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
            out += static_cast<char>(0x80 | (cp & 0x3F));
        } else {
            out += static_cast<char>(0xF0 | (cp >> 18));
            out += static_cast<char>(0x80 | ((cp >> 12) & 0x3F));
            out += static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
            out += static_cast<char>(0x80 | (cp & 0x3F));
        }
    }
    e->ReleaseStringChars(s, chars);
    return out;
}

jmethodID bridgeMethod(JNIEnv *e, const char *name, const char *sig) {
    jclass cls = e->GetObjectClass(gBridge);
    jmethodID m = e->GetMethodID(cls, name, sig);
    e->DeleteLocalRef(cls);
    return m;
}

void notifyStatus(int status) {
    JNIEnv *e = env();
    if (!e || !gBridge) return;
    e->CallVoidMethod(gBridge, bridgeMethod(e, "onStatus", "(I)V"), status);
}

void notifyError(const std::string &msg) {
    JNIEnv *e = env();
    if (!e || !gBridge) return;
    jstring text = e->NewStringUTF(msg.c_str());
    e->CallVoidMethod(gBridge, bridgeMethod(e, "onError", "(Ljava/lang/String;)V"), text);
    e->DeleteLocalRef(text);
}

void notifyTokens(const std::string &access, const std::string &refresh, int32_t expiresIn) {
    JNIEnv *e = env();
    if (!e || !gBridge) return;
    jstring a = e->NewStringUTF(access.c_str());
    jstring r = e->NewStringUTF(refresh.c_str());
    e->CallVoidMethod(gBridge, bridgeMethod(e, "onTokens", "(Ljava/lang/String;Ljava/lang/String;I)V"), a, r, expiresIn);
    e->DeleteLocalRef(a);
    e->DeleteLocalRef(r);
}

void notifyUser() {
    auto user = gClient->GetCurrentUserV2();
    JNIEnv *e = env();
    if (!user || !e || !gBridge) return;
    using Avatar = discordpp::UserHandle::AvatarType;
    jstring name = e->NewStringUTF(user->DisplayName().c_str());
    jstring avatar = e->NewStringUTF(user->AvatarUrl(Avatar::Png, Avatar::Png).c_str());
    e->CallVoidMethod(gBridge, bridgeMethod(e, "onUser", "(Ljava/lang/String;Ljava/lang/String;)V"), name, avatar);
    e->DeleteLocalRef(name);
    e->DeleteLocalRef(avatar);
}

void connectWith(const std::string &token) {
    gClient->UpdateToken(discordpp::AuthorizationTokenType::Bearer, token, [](discordpp::ClientResult r) {
        if (r.Successful()) gClient->Connect();
        else notifyError(r.Error());
    });
}

void onTokens(discordpp::ClientResult r, std::string access, std::string refresh,
              discordpp::AuthorizationTokenType, int32_t expiresIn, std::string) {
    if (!r.Successful()) {
        notifyError(r.Error());
        return;
    }
    notifyTokens(access, refresh, expiresIn);
    connectWith(access);
}

}  // namespace

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *) {
    gVm = vm;
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
Java_com_noctyra_app_discord_DiscordBridge_nativeInit(JNIEnv *e, jobject thiz, jlong appId) {
    if (gClient) return;
    gBridge = e->NewGlobalRef(thiz);
    gAppId = static_cast<uint64_t>(appId);
    gClient = std::make_shared<discordpp::Client>();
    gClient->SetStatusChangedCallback([](discordpp::Client::Status s, discordpp::Client::Error, int32_t) {
        notifyStatus(static_cast<int>(s));
        if (s == discordpp::Client::Status::Ready) notifyUser();
    });
}

JNIEXPORT void JNICALL
Java_com_noctyra_app_discord_DiscordBridge_nativeRunCallbacks(JNIEnv *, jobject) {
    discordpp::RunCallbacks();
}

JNIEXPORT void JNICALL
Java_com_noctyra_app_discord_DiscordBridge_nativeLogin(JNIEnv *, jobject) {
    if (!gClient) return;
    auto verifier = gClient->CreateAuthorizationCodeVerifier();
    discordpp::AuthorizationArgs args;
    args.SetClientId(gAppId);
    args.SetScopes(discordpp::Client::GetDefaultPresenceScopes());
    args.SetCodeChallenge(verifier.Challenge());
    gClient->Authorize(args, [verifier](discordpp::ClientResult r, std::string code, std::string redirect) {
        if (!r.Successful()) {
            notifyError(r.Error());
            return;
        }
        gClient->GetToken(gAppId, code, verifier.Verifier(), redirect, onTokens);
    });
}

JNIEXPORT void JNICALL
Java_com_noctyra_app_discord_DiscordBridge_nativeConnect(JNIEnv *e, jobject, jstring token) {
    if (gClient) connectWith(toStd(e, token));
}

JNIEXPORT void JNICALL
Java_com_noctyra_app_discord_DiscordBridge_nativeRefresh(JNIEnv *e, jobject, jstring refresh) {
    if (gClient) gClient->RefreshToken(gAppId, toStd(e, refresh), onTokens);
}

JNIEXPORT void JNICALL
Java_com_noctyra_app_discord_DiscordBridge_nativeSetActivity(
    JNIEnv *e, jobject, jstring details, jstring state, jstring largeImage, jstring largeText,
    jstring smallImage, jlong startMs, jlong endMs) {
    if (!gClient) return;
    discordpp::Activity activity;
    activity.SetType(discordpp::ActivityTypes::Watching);
    activity.SetDetails(toStd(e, details));
    activity.SetState(toStd(e, state));

    discordpp::ActivityAssets assets;
    std::string image = toStd(e, largeImage);
    if (!image.empty()) assets.SetLargeImage(image);
    assets.SetLargeText(toStd(e, largeText));
    std::string small = toStd(e, smallImage);
    if (!small.empty()) assets.SetSmallImage(small);
    activity.SetAssets(assets);

    if (startMs > 0) {
        discordpp::ActivityTimestamps ts;
        ts.SetStart(static_cast<uint64_t>(startMs));
        if (endMs > startMs) ts.SetEnd(static_cast<uint64_t>(endMs));
        activity.SetTimestamps(ts);
    }
    gClient->UpdateRichPresence(activity, [](discordpp::ClientResult r) {
        if (!r.Successful()) notifyError(r.Error());
    });
}

JNIEXPORT void JNICALL
Java_com_noctyra_app_discord_DiscordBridge_nativeClearActivity(JNIEnv *, jobject) {
    if (gClient) gClient->ClearRichPresence();
}

JNIEXPORT void JNICALL
Java_com_noctyra_app_discord_DiscordBridge_nativeDisconnect(JNIEnv *, jobject) {
    if (gClient) gClient->Disconnect();
}

}  // extern "C"
