// llcore — нативное ядро загрузок Light Launcher.
//   llcore fetch <файл-заданий> [потоков]   параллельно качает файлы, проверяет SHA-1, пишет прогресс в stdout
//   llcore sha1 <файл>                      печатает SHA-1 файла
// Файл заданий: строки «url<TAB>путь<TAB>sha1|-<TAB>размер|0» в UTF-8.
// Вывод: «PROG<TAB>готово<TAB>всего», «FAIL<TAB>url<TAB>причина». Код возврата 0 — всё скачано и проверено.
// Windows: WinHTTP (HTTPS, системные сертификаты, keep-alive на поток). Другие ОС: только http:// (для тестов).
#include <atomic>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <mutex>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

#ifdef _WIN32
#include <windows.h>
#include <winhttp.h>
#else
#include <arpa/inet.h>
#include <netdb.h>
#include <sys/socket.h>
#include <unistd.h>
#endif

namespace fs = std::filesystem;

// ---------------------------------------------------------------- SHA-1
struct Sha1 {
    uint32_t h[5] = {0x67452301, 0xEFCDAB89, 0x98BADCFE, 0x10325476, 0xC3D2E1F0};
    uint64_t len = 0;
    uint8_t buf[64];
    size_t n = 0;
    static uint32_t rol(uint32_t v, int s) { return (v << s) | (v >> (32 - s)); }
    void block(const uint8_t* p) {
        uint32_t w[80];
        for (int i = 0; i < 16; i++) w[i] = (uint32_t)p[i * 4] << 24 | (uint32_t)p[i * 4 + 1] << 16 | (uint32_t)p[i * 4 + 2] << 8 | p[i * 4 + 3];
        for (int i = 16; i < 80; i++) w[i] = rol(w[i - 3] ^ w[i - 8] ^ w[i - 14] ^ w[i - 16], 1);
        uint32_t a = h[0], b = h[1], c = h[2], d = h[3], e = h[4];
        for (int i = 0; i < 80; i++) {
            uint32_t f, k;
            if (i < 20) { f = (b & c) | (~b & d); k = 0x5A827999; }
            else if (i < 40) { f = b ^ c ^ d; k = 0x6ED9EBA1; }
            else if (i < 60) { f = (b & c) | (b & d) | (c & d); k = 0x8F1BBCDC; }
            else { f = b ^ c ^ d; k = 0xCA62C1D6; }
            uint32_t t = rol(a, 5) + f + e + k + w[i];
            e = d; d = c; c = rol(b, 30); b = a; a = t;
        }
        h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e;
    }
    void update(const uint8_t* p, size_t m) {
        len += m;
        while (m) {
            size_t t = 64 - n < m ? 64 - n : m;
            memcpy(buf + n, p, t);
            n += t; p += t; m -= t;
            if (n == 64) { block(buf); n = 0; }
        }
    }
    std::string finish() {
        uint64_t bits = len * 8;
        uint8_t pad = 0x80;
        update(&pad, 1);
        uint8_t z = 0;
        while (n != 56) update(&z, 1);
        uint8_t l[8];
        for (int i = 0; i < 8; i++) l[i] = (uint8_t)(bits >> (56 - 8 * i));
        update(l, 8);
        char out[41];
        for (int i = 0; i < 5; i++) snprintf(out + i * 8, 9, "%08x", h[i]);
        return std::string(out, 40);
    }
};

static std::string sha1File(const fs::path& p) {
    std::ifstream in(p, std::ios::binary);
    if (!in) return "";
    Sha1 s;
    std::vector<uint8_t> b(1 << 16);
    while (in) {
        in.read((char*)b.data(), b.size());
        s.update(b.data(), (size_t)in.gcount());
    }
    return s.finish();
}

// ---------------------------------------------------------------- HTTP
struct Url {
    bool https = false;
    std::string host, path;
    int port = 80;
};

static bool parseUrl(const std::string& u, Url& o) {
    size_t p = u.find("://");
    if (p == std::string::npos) return false;
    std::string scheme = u.substr(0, p);
    o.https = scheme == "https";
    if (!o.https && scheme != "http") return false;
    size_t hs = p + 3;
    size_t pe = u.find('/', hs);
    std::string hp = pe == std::string::npos ? u.substr(hs) : u.substr(hs, pe - hs);
    o.path = pe == std::string::npos ? "/" : u.substr(pe);
    o.port = o.https ? 443 : 80;
    size_t c = hp.rfind(':');
    if (c != std::string::npos) { o.port = atoi(hp.c_str() + c + 1); hp = hp.substr(0, c); }
    o.host = hp;
    return !o.host.empty();
}

typedef bool (*Sink)(const uint8_t*, size_t, void*);

#ifdef _WIN32
static std::wstring widen(const std::string& s) {
    if (s.empty()) return L"";
    int n = MultiByteToWideChar(CP_UTF8, 0, s.data(), (int)s.size(), nullptr, 0);
    std::wstring w(n, 0);
    MultiByteToWideChar(CP_UTF8, 0, s.data(), (int)s.size(), &w[0], n);
    return w;
}

struct Http {
    HINTERNET ses = nullptr;
    HINTERNET con = nullptr;
    std::string conHost;
    int conPort = 0;
    ~Http() { close(); if (ses) WinHttpCloseHandle(ses); }
    void close() { if (con) { WinHttpCloseHandle(con); con = nullptr; } conHost.clear(); }
    // 0 — успех; иначе текст ошибки в err
    bool get(const Url& u, Sink sink, void* ctx, std::string& err) {
        if (!ses) {
            ses = WinHttpOpen(L"LightLauncher-core/1.2", WINHTTP_ACCESS_TYPE_AUTOMATIC_PROXY, WINHTTP_NO_PROXY_NAME, WINHTTP_NO_PROXY_BYPASS, 0);
            if (!ses) { err = "WinHttpOpen"; return false; }
            WinHttpSetTimeouts(ses, 15000, 15000, 30000, 60000);
            DWORD pol = WINHTTP_OPTION_REDIRECT_POLICY_ALWAYS;
            WinHttpSetOption(ses, WINHTTP_OPTION_REDIRECT_POLICY, &pol, sizeof pol);
        }
        if (!con || conHost != u.host || conPort != u.port) {
            close();
            con = WinHttpConnect(ses, widen(u.host).c_str(), (INTERNET_PORT)u.port, 0);
            if (!con) { err = "connect " + std::to_string(GetLastError()); return false; }
            conHost = u.host;
            conPort = u.port;
        }
        HINTERNET rq = WinHttpOpenRequest(con, L"GET", widen(u.path).c_str(), nullptr, WINHTTP_NO_REFERER, WINHTTP_DEFAULT_ACCEPT_TYPES, u.https ? WINHTTP_FLAG_SECURE : 0);
        if (!rq) { err = "request " + std::to_string(GetLastError()); close(); return false; }
        bool ok = false;
        if (WinHttpSendRequest(rq, WINHTTP_NO_ADDITIONAL_HEADERS, 0, WINHTTP_NO_REQUEST_DATA, 0, 0, 0) && WinHttpReceiveResponse(rq, nullptr)) {
            DWORD code = 0, sz = sizeof code;
            WinHttpQueryHeaders(rq, WINHTTP_QUERY_STATUS_CODE | WINHTTP_QUERY_FLAG_NUMBER, WINHTTP_HEADER_NAME_BY_INDEX, &code, &sz, WINHTTP_NO_HEADER_INDEX);
            if (code != 200) {
                err = "HTTP " + std::to_string(code);
            } else {
                ok = true;
                std::vector<uint8_t> b(1 << 16);
                for (;;) {
                    DWORD got = 0;
                    if (!WinHttpReadData(rq, b.data(), (DWORD)b.size(), &got)) { err = "read " + std::to_string(GetLastError()); ok = false; break; }
                    if (!got) break;
                    if (!sink(b.data(), got, ctx)) { err = "write"; ok = false; break; }
                }
            }
        } else {
            err = "net " + std::to_string(GetLastError());
        }
        WinHttpCloseHandle(rq);
        if (!ok) close();
        return ok;
    }
};
#else
struct Http {
    // тестовая реализация: простой HTTP/1.0 без TLS
    bool get(const Url& u, Sink sink, void* ctx, std::string& err) {
        if (u.https) { err = "https не поддерживается на этой ОС"; return false; }
        addrinfo hints{}, *res = nullptr;
        hints.ai_socktype = SOCK_STREAM;
        if (getaddrinfo(u.host.c_str(), std::to_string(u.port).c_str(), &hints, &res) != 0 || !res) { err = "dns"; return false; }
        int fd = socket(res->ai_family, SOCK_STREAM, 0);
        if (fd < 0 || connect(fd, res->ai_addr, res->ai_addrlen) != 0) { err = "connect"; freeaddrinfo(res); if (fd >= 0) close(fd); return false; }
        freeaddrinfo(res);
        std::string rq = "GET " + u.path + " HTTP/1.0\r\nHost: " + u.host + "\r\nConnection: close\r\n\r\n";
        if (send(fd, rq.data(), rq.size(), 0) < 0) { err = "send"; close(fd); return false; }
        std::string head;
        char b[65536];
        bool inBody = false, ok = true;
        for (;;) {
            ssize_t n = recv(fd, b, sizeof b, 0);
            if (n <= 0) break;
            if (!inBody) {
                head.append(b, n);
                size_t e = head.find("\r\n\r\n");
                if (e == std::string::npos) continue;
                int code = atoi(head.c_str() + head.find(' ') + 1);
                if (code != 200) { err = "HTTP " + std::to_string(code); ok = false; break; }
                inBody = true;
                std::string rest = head.substr(e + 4);
                if (!rest.empty() && !sink((const uint8_t*)rest.data(), rest.size(), ctx)) { err = "write"; ok = false; break; }
            } else if (!sink((const uint8_t*)b, (size_t)n, ctx)) { err = "write"; ok = false; break; }
        }
        close(fd);
        if (ok && !inBody) { err = "пустой ответ"; ok = false; }
        return ok;
    }
};
#endif

// ---------------------------------------------------------------- задания
struct Job {
    std::string url, path, sha1;
    long long size = 0;
};

struct Out {
    std::ofstream f;
    Sha1 sha;
    long long bytes = 0;
};

static bool outSink(const uint8_t* p, size_t n, void* c) {
    Out* o = (Out*)c;
    o->f.write((const char*)p, (std::streamsize)n);
    o->sha.update(p, n);
    o->bytes += (long long)n;
    return (bool)o->f;
}

static std::mutex printMu;
static void say(const std::string& s) {
    std::lock_guard<std::mutex> l(printMu);
    fputs(s.c_str(), stdout);
    fputc('\n', stdout);
    fflush(stdout);
}

static bool fresh(const Job& j) {
    std::error_code ec;
    fs::path p = fs::u8path(j.path);
    if (!fs::is_regular_file(p, ec)) return false;
    if (j.size <= 0) return true;
    return (long long)fs::file_size(p, ec) == j.size;
}

// пустая строка — успех
static std::string runJob(Http& http, const Job& j) {
    if (fresh(j)) return "";
    Url u;
    if (!parseUrl(j.url, u)) return "неверная ссылка";
    fs::path dest = fs::u8path(j.path);
    std::error_code ec;
    fs::create_directories(dest.parent_path(), ec);
    fs::path part = dest;
    part += ".part";
    std::string err;
    for (int attempt = 0; attempt < 3; attempt++) {
        Out o;
        o.f.open(part, std::ios::binary | std::ios::trunc);
        if (!o.f) return "не удалось создать файл";
        bool ok = http.get(u, outSink, &o, err);
        o.f.close();
        if (ok) {
            std::string h = o.sha.finish();
            if (!j.sha1.empty() && j.sha1 != "-" && h != j.sha1) {
                err = "контрольная сумма не совпала";
            } else if (j.size > 0 && o.bytes != j.size) {
                err = "размер не совпал";
            } else {
                fs::rename(part, dest, ec);
                if (ec) { err = "rename: " + ec.message(); } else return "";
            }
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(300 * (attempt + 1)));
    }
    fs::remove(part, ec);
    return err.empty() ? "ошибка" : err;
}

static int cmdFetch(const char* file, int threads) {
    std::ifstream in(file);
    if (!in) { say("FAIL\t-\tнет файла заданий"); return 2; }
    std::vector<Job> jobs;
    std::string line;
    while (std::getline(in, line)) {
        if (!line.empty() && line.back() == '\r') line.pop_back();
        std::vector<std::string> f;
        std::stringstream ss(line);
        std::string t;
        while (std::getline(ss, t, '\t')) f.push_back(t);
        if (f.size() < 2) continue;
        Job j;
        j.url = f[0];
        j.path = f[1];
        if (f.size() > 2) j.sha1 = f[2];
        if (f.size() > 3) j.size = atoll(f[3].c_str());
        jobs.push_back(j);
    }
    size_t total = jobs.size();
    std::atomic<size_t> next{0}, done{0}, failed{0};
    if (threads < 1) threads = 1;
    if (threads > 16) threads = 16;
    if ((size_t)threads > total) threads = (int)(total ? total : 1);
    auto worker = [&]() {
        Http http;
        for (;;) {
            size_t i = next.fetch_add(1);
            if (i >= total) break;
            std::string e = runJob(http, jobs[i]);
            if (!e.empty()) { failed++; say("FAIL\t" + jobs[i].url + "\t" + e); }
            size_t d = ++done;
            if (d == total || d % 20 == 0) say("PROG\t" + std::to_string(d) + "\t" + std::to_string(total));
        }
    };
    std::vector<std::thread> ts;
    for (int i = 0; i < threads; i++) ts.emplace_back(worker);
    for (auto& t : ts) t.join();
    say("PROG\t" + std::to_string(done.load()) + "\t" + std::to_string(total));
    return failed ? 1 : 0;
}

int main(int argc, char** argv) {
    if (argc >= 3 && !strcmp(argv[1], "sha1")) {
        printf("%s\n", sha1File(fs::u8path(argv[2])).c_str());
        return 0;
    }
    if (argc >= 3 && !strcmp(argv[1], "fetch")) return cmdFetch(argv[2], argc > 3 ? atoi(argv[3]) : 8);
    fprintf(stderr, "llcore fetch <jobs> [threads] | llcore sha1 <file>\n");
    return 2;
}
