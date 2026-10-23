/* Light Launcher: exe-оболочка. К exe дописан jar (zip в конце файла), Java читает его оттуда.
   Находит Java 17+ (Minecraft, Program Files, JAVA_HOME, PATH), при отсутствии предлагает скачать. */
#define UNICODE
#define _UNICODE
#include <windows.h>
#include <shellapi.h>
#include <stdio.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wchar.h>
#include <jni.h>

#ifndef MAINCLASS
#define MAINCLASS "lightmods/App"
#endif

#define MINJAVA 17
#define MAXP 1024

static wchar_t g_best[MAXP];
static int g_bestMajor = 0;

static int file_exists(const wchar_t *p) {
    DWORD a = GetFileAttributesW(p);
    return a != INVALID_FILE_ATTRIBUTES && !(a & FILE_ATTRIBUTE_DIRECTORY);
}

static int java_major(const wchar_t *home) {
    wchar_t p[MAXP];
    _snwprintf(p, MAXP, L"%ls\\release", home);
    HANDLE f = CreateFileW(p, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING, 0, NULL);
    if (f == INVALID_HANDLE_VALUE) return 0;
    char buf[4096];
    DWORD n = 0;
    ReadFile(f, buf, sizeof(buf) - 1, &n, NULL);
    CloseHandle(f);
    buf[n] = 0;
    char *v = strstr(buf, "JAVA_VERSION=\"");
    if (!v) return 0;
    v += 14;
    if (v[0] == '1' && v[1] == '.') v += 2;
    return atoi(v);
}

static void consider(const wchar_t *home) {
    wchar_t exe[MAXP];
    _snwprintf(exe, MAXP, L"%ls\\bin\\javaw.exe", home);
    if (!file_exists(exe)) return;
    int m = java_major(home);
    if (m >= MINJAVA && m > g_bestMajor) {
        g_bestMajor = m;
        wcsncpy(g_best, exe, MAXP - 1);
        g_best[MAXP - 1] = 0;
    }
}

static void scan(const wchar_t *dir, int depth) {
    if (depth < 0 || !dir || !dir[0]) return;
    consider(dir);
    wchar_t pat[MAXP];
    _snwprintf(pat, MAXP, L"%ls\\*", dir);
    WIN32_FIND_DATAW fd;
    HANDLE h = FindFirstFileW(pat, &fd);
    if (h == INVALID_HANDLE_VALUE) return;
    do {
        if (!(fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY)) continue;
        if (wcscmp(fd.cFileName, L".") == 0 || wcscmp(fd.cFileName, L"..") == 0) continue;
        if (_wcsicmp(fd.cFileName, L"bin") == 0 || _wcsicmp(fd.cFileName, L"lib") == 0 || _wcsicmp(fd.cFileName, L"natives") == 0) continue;
        wchar_t sub[MAXP];
        _snwprintf(sub, MAXP, L"%ls\\%ls", dir, fd.cFileName);
        scan(sub, depth - 1);
    } while (FindNextFileW(h, &fd));
    FindClose(h);
}

static void scan_env(const wchar_t *var, const wchar_t *tail, int depth) {
    wchar_t base[MAXP];
    DWORD n = GetEnvironmentVariableW(var, base, MAXP);
    if (n == 0 || n >= MAXP) return;
    wchar_t p[MAXP];
    _snwprintf(p, MAXP, L"%ls%ls", base, tail);
    scan(p, depth);
}

static int find_java(void) {
    g_bestMajor = 0;
    g_best[0] = 0;
    scan_env(L"LightLauncherJava", L"", 0);
    scan_env(L"JAVA_HOME", L"", 0);
    scan_env(L"LOCALAPPDATA", L"\\LightLauncher\\jre", 2);
    scan_env(L"APPDATA", L"\\.minecraft\\runtime", 4);
    scan_env(L"APPDATA", L"\\.minecraftlight\\runtime", 4);
    scan_env(L"APPDATA", L"\\.lightlauncher\\runtime", 4);
    scan_env(L"LOCALAPPDATA", L"\\Packages\\Microsoft.4297127D64EC6_8wekyb3d8bbwe\\LocalCache\\Local\\runtime", 4);
    scan_env(L"ProgramFiles", L"\\Java", 2);
    scan_env(L"ProgramFiles", L"\\Eclipse Adoptium", 2);
    scan_env(L"ProgramFiles", L"\\Microsoft", 2);
    scan_env(L"ProgramFiles", L"\\Zulu", 2);
    scan_env(L"ProgramFiles", L"\\Amazon Corretto", 2);
    scan_env(L"ProgramFiles", L"\\BellSoft", 2);
    scan_env(L"ProgramFiles", L"\\Semeru", 2);
    scan_env(L"ProgramFiles", L"\\Minecraft Launcher\\runtime", 4);
    scan_env(L"ProgramFiles(x86)", L"\\Minecraft Launcher\\runtime", 4);
    {
        wchar_t found[MAXP];
        if (SearchPathW(NULL, L"javaw.exe", NULL, MAXP, found, NULL)) {
            wchar_t *s = wcsrchr(found, L'\\');
            if (s) {
                *s = 0;               /* ...\bin */
                wchar_t *s2 = wcsrchr(found, L'\\');
                if (s2) {
                    *s2 = 0;          /* java home */
                    consider(found);
                }
            }
        }
    }
    return g_bestMajor >= MINJAVA;
}

static int download_java(void) {
    const wchar_t *cmd =
        L"powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -Command \""
        L"$ErrorActionPreference='Stop'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; "
        L"$d=Join-Path $env:LOCALAPPDATA 'LightLauncher\\jre'; New-Item -ItemType Directory -Force $d | Out-Null; "
        L"$z=Join-Path $d 'jre.zip'; $ProgressPreference='SilentlyContinue'; "
        L"Invoke-WebRequest -UseBasicParsing 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse' -OutFile $z; "
        L"Expand-Archive -Force $z $d; Remove-Item $z\"";
    wchar_t *c = _wcsdup(cmd);
    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    ZeroMemory(&pi, sizeof(pi));
    BOOL ok = CreateProcessW(NULL, c, NULL, NULL, FALSE, CREATE_NO_WINDOW, NULL, NULL, &si, &pi);
    free(c);
    if (!ok) return 0;
    WaitForSingleObject(pi.hProcess, 10 * 60 * 1000);
    DWORD code = 1;
    GetExitCodeProcess(pi.hProcess, &code);
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);
    return code == 0;
}

/* аргументы после имени программы */
static const wchar_t *skip_argv0(const wchar_t *cl) {
    const wchar_t *p = cl;
    while (*p == L' ') p++;
    if (*p == L'"') {
        p++;
        while (*p && *p != L'"') p++;
        if (*p) p++;
    } else {
        while (*p && *p != L' ') p++;
    }
    while (*p == L' ') p++;
    return p;
}

/* ---- запуск JVM внутри этого же процесса: окно принадлежит LightLauncher.exe, поэтому значок можно закрепить на панели задач ---- */

typedef jint (JNICALL *CreateVMFn)(JavaVM **, void **, void *);

typedef struct {
    wchar_t javaw[MAXP];
    wchar_t self[MAXP];
    int ok;      /* JVM была создана и приложение отработало */
} InProc;

static char *to_utf8(const wchar_t *w) {
    int n = WideCharToMultiByte(CP_UTF8, 0, w, -1, NULL, 0, NULL, NULL);
    char *s = (char *)malloc(n + 1);
    if (!s) return NULL;
    WideCharToMultiByte(CP_UTF8, 0, w, -1, s, n, NULL, NULL);
    s[n] = 0;
    return s;
}

static DWORD WINAPI jvm_thread(LPVOID p) {
    InProc *ip = (InProc *)p;
    ip->ok = 0;
    wchar_t home[MAXP], bin[MAXP], dll[MAXP];
    wcsncpy(home, ip->javaw, MAXP - 1);
    home[MAXP - 1] = 0;
    wchar_t *s = wcsrchr(home, L'\\');
    if (!s) return 0;
    *s = 0;                       /* ...\bin */
    wcsncpy(bin, home, MAXP - 1);
    bin[MAXP - 1] = 0;
    s = wcsrchr(home, L'\\');
    if (!s) return 0;
    *s = 0;                       /* java home */
    _snwprintf(dll, MAXP, L"%ls\\server\\jvm.dll", bin);
    if (!file_exists(dll)) _snwprintf(dll, MAXP, L"%ls\\client\\jvm.dll", bin);
    if (!file_exists(dll)) return 0;
    SetDllDirectoryW(bin);
    HMODULE h = LoadLibraryW(dll);
    if (!h) return 0;
    CreateVMFn create = (CreateVMFn)GetProcAddress(h, "JNI_CreateJavaVM");
    if (!create) return 0;

    char *cp = to_utf8(ip->self);
    if (!cp) return 0;
    char opt1[MAXP * 3 + 32];
    _snprintf(opt1, sizeof(opt1), "-Djava.class.path=%s", cp);
    free(cp);
    JavaVMOption opts[3];
    opts[0].optionString = opt1;
    opts[0].extraInfo = NULL;
    opts[1].optionString = "-Dfile.encoding=UTF-8";
    opts[1].extraInfo = NULL;
    opts[2].optionString = "-Dsun.stdout.encoding=UTF-8";
    opts[2].extraInfo = NULL;
    JavaVMInitArgs args;
    args.version = JNI_VERSION_1_8;
    args.nOptions = 3;
    args.options = opts;
    args.ignoreUnrecognized = JNI_TRUE;
    JavaVM *vm = NULL;
    JNIEnv *env = NULL;
    if (create(&vm, (void **)&env, &args) < 0 || !vm || !env) return 0;
    ip->ok = 1;

    jclass cls = (*env)->FindClass(env, MAINCLASS);
    if (cls) {
        jmethodID mid = (*env)->GetStaticMethodID(env, cls, "main", "([Ljava/lang/String;)V");
        if (mid) {
            int argc = 0;
            LPWSTR *argv = CommandLineToArgvW(GetCommandLineW(), &argc);
            int n = argc > 1 ? argc - 1 : 0;
            jclass sc = (*env)->FindClass(env, "java/lang/String");
            jobjectArray arr = (*env)->NewObjectArray(env, n, sc, NULL);
            for (int i = 0; i < n && arr; i++) {
                char *u = to_utf8(argv[i + 1]);
                if (u) {
                    jstring js = (*env)->NewStringUTF(env, u);
                    (*env)->SetObjectArrayElement(env, arr, i, js);
                    free(u);
                }
            }
            if (arr) (*env)->CallStaticVoidMethod(env, cls, mid, arr);
        }
    }
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
    (*vm)->DetachCurrentThread(vm);
    (*vm)->DestroyJavaVM(vm);
    return 0;
}

static int run_in_process(const wchar_t *javaw, const wchar_t *self) {
    InProc *ip = (InProc *)calloc(1, sizeof(InProc));
    if (!ip) return 0;
    wcsncpy(ip->javaw, javaw, MAXP - 1);
    wcsncpy(ip->self, self, MAXP - 1);
    HANDLE t = CreateThread(NULL, 8 * 1024 * 1024, jvm_thread, ip, 0, NULL);
    if (!t) return 0;
    WaitForSingleObject(t, INFINITE);
    CloseHandle(t);
    return ip->ok;
}

int WINAPI WinMain(HINSTANCE hi, HINSTANCE hp, LPSTR cmd, int show) {
    (void)hi; (void)hp; (void)cmd; (void)show;
    wchar_t self[MAXP];
    GetModuleFileNameW(NULL, self, MAXP);

    if (!find_java()) {
        int r = MessageBoxW(NULL,
            L"Для работы нужна Java 17 или новее, но она не найдена.\n\n"
            L"Скачать её автоматически сейчас (около 50 МБ, один раз)?\n"
            L"После нажатия «Да» подождите минуту-две — лаунчер откроется сам.",
            L"Light Launcher", MB_YESNO | MB_ICONQUESTION);
        if (r == IDYES) {
            if (!download_java() || !find_java()) {
                MessageBoxW(NULL,
                    L"Не удалось скачать Java автоматически.\nУстановите Java 21 (например, Temurin с adoptium.net) и запустите Light Launcher снова.",
                    L"Light Launcher", MB_OK | MB_ICONERROR);
                ShellExecuteW(NULL, L"open", L"https://adoptium.net/temurin/releases/?version=21", NULL, NULL, SW_SHOWNORMAL);
                return 1;
            }
        } else {
            return 1;
        }
    }

    /* основной путь: JVM в этом же процессе; если не вышло — запасной (отдельный javaw) */
    if (run_in_process(g_best, self)) return 0;

    const wchar_t *rest = skip_argv0(GetCommandLineW());
    size_t len = wcslen(g_best) + wcslen(self) + wcslen(rest) + 128;
    wchar_t *cl = (wchar_t *)malloc(len * sizeof(wchar_t));
    if (!cl) return 1;
    _snwprintf(cl, len, L"\"%ls\" -Dfile.encoding=UTF-8 -jar \"%ls\" %ls", g_best, self, rest);
    cl[len - 1] = 0;

    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    ZeroMemory(&pi, sizeof(pi));
    if (!CreateProcessW(g_best, cl, NULL, NULL, FALSE, 0, NULL, NULL, &si, &pi)) {
        MessageBoxW(NULL, L"Не удалось запустить Java.", L"Light Launcher", MB_OK | MB_ICONERROR);
        free(cl);
        return 1;
    }
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);
    free(cl);
    return 0;
}
