import http.server, socketserver, json, hashlib, os, sys, zipfile, io, struct, zlib, urllib.parse
PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 18080
HOST = f"http://localhost:{PORT}"
HERE = os.path.dirname(os.path.abspath(__file__))

def png(color):
    w = h = 32
    raw = b''.join(b'\x00' + bytes(color) * w for _ in range(h))
    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')

FILES = {}
def add_file(path, data):
    FILES[path] = data
    return hashlib.sha1(data).hexdigest()

REALJAVA = os.environ.get('REALJAVA', 'java')
add_file('/files/java.sh', ('#!/bin/sh\nexec ' + REALJAVA + ' "$@"\n').encode())
client_sha = add_file('/files/client-1.21.1.jar', b'FAKECLIENTJAR' * 100)
sodium_sha = add_file('/files/sodium.jar', b'sodium-bytes' * 50)
api_sha = add_file('/files/fabric-api.jar', b'fabricapi-bytes' * 50)
empm_sha = add_file('/files/enablemultiplayermode-1.0.jar', b'empm-bytes' * 40)
rp_sha = add_file('/files/pack.zip', b'resourcepack-bytes' * 50)
sh_sha = add_file('/files/shader.zip', b'shader-bytes' * 50)
for i, col in enumerate([(200, 60, 60), (60, 160, 90), (70, 110, 210), (220, 170, 40)]):
    add_file(f'/icons/{i}.png', png(col))

# modpack
idx = {"formatVersion": 1, "game": "minecraft", "versionId": "1.0", "name": "Test Pack",
       "files": [{"path": "mods/sodium.jar", "hashes": {"sha1": sodium_sha}, "env": {"client": "required", "server": "required"}, "downloads": [HOST + "/files/sodium.jar"], "fileSize": 600},
                 {"path": "mods/serveronly.jar", "hashes": {"sha1": api_sha}, "env": {"client": "unsupported", "server": "required"}, "downloads": [HOST + "/files/fabric-api.jar"], "fileSize": 750},
                 {"path": "resourcepacks/pack.zip", "hashes": {"sha1": rp_sha}, "downloads": [HOST + "/files/pack.zip"], "fileSize": 900}],
       "dependencies": {"minecraft": "1.21.1", "fabric-loader": "0.16.9"}}
buf = io.BytesIO()
with zipfile.ZipFile(buf, 'w') as z:
    z.writestr('modrinth.index.json', json.dumps(idx))
    z.writestr('overrides/config/test.txt', 'hello')
    z.writestr('client-overrides/options.txt', 'lang:ru_ru')
    z.writestr('overrides/../../evil.txt', 'EVIL')
mrpack = buf.getvalue()
mrpack_sha = add_file('/files/testpack.mrpack', mrpack)

with open(os.path.join(HERE, 'fake', 'forge-installer.jar'), 'rb') as f:
    FILES['/forgemaven/1.21.1-52.0.0/forge-1.21.1-52.0.0-installer.jar'] = f.read()

def mk_version(vid, pid, name, gvs, loaders, fn, sha, deps=(), vtype='release', date='2026-01-01T00:00:00Z'):
    return {"id": vid, "project_id": pid, "name": name, "version_number": name, "version_type": vtype, "date_published": date,
            "game_versions": gvs, "loaders": loaders,
            "files": [{"url": HOST + '/files/' + fn, "filename": fn, "primary": True, "size": 100, "hashes": {"sha1": sha}}],
            "dependencies": [{"project_id": d, "version_id": None, "dependency_type": "required"} for d in deps]}

VERSIONS = {
    "PROJ1": [mk_version("V1", "PROJ1", "Sodium 0.6", ["1.21.1"], ["fabric", "quilt"], "sodium.jar", sodium_sha, deps=["LIB1"])],
    "LIB1": [mk_version("V2", "LIB1", "Fabric API 0.100", ["1.21.1"], ["fabric"], "fabric-api.jar", api_sha)],
    "PROJ2": [mk_version("V3", "PROJ2", "OldMod 1.0", ["1.20.1"], ["fabric"], "oldmod.jar", sodium_sha)],
    "RP1": [mk_version("V4", "RP1", "Faithful 1.0", ["1.21.1"], ["minecraft"], "pack.zip", rp_sha)],
    "SH1": [mk_version("V5", "SH1", "Shader 1.0", ["1.21.1"], ["iris"], "shader.zip", sh_sha)],
    "oH7RCgH6": [mk_version("V9", "oH7RCgH6", "EnableMultiPlayerMode 1.0", ["1.16.4", "1.16.5"], ["fabric", "forge"], "enablemultiplayermode-1.0.jar", empm_sha)],
    "MP1": [mk_version("V6", "MP1", "Test Pack 1.0", ["1.21.1"], ["fabric"], "testpack.mrpack", mrpack_sha)],
}
TITLES = {"PROJ1": "Sodium Test", "LIB1": "Fabric API Test", "PROJ2": "Old Mod", "RP1": "Faithful Test", "SH1": "Shader Test", "MP1": "Test Pack"}
HITS = {
    "mod": [("PROJ1", "Sodium Test", "A modern rendering engine for Minecraft which greatly improves performance, frame rates and graphics quality without changing the look of the game at all.", 123456789, 98765),
            ("LIB1", "Fabric API Test", "Core API module for the Fabric toolchain providing hooks and interoperability between mods.", 99999999, 54321),
            ("PROJ2", "Old Mod", "Only for 1.20.1.", 5000, 12)],
    "resourcepack": [("RP1", "Faithful Test", "Faithful at 32x resolution texture pack, extended with additional detail.", 4000000, 3000)],
    "shader": [("SH1", "Shader Test", "A lovely shader pack with volumetric clouds.", 700000, 800)],
    "modpack": [("MP1", "Test Pack", "A cool modpack for testing the launcher.", 321000, 777)],
}

POLLS = {}
class H(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def send(self, code, body, ctype='application/json'):
        if isinstance(body, (dict, list)): body = json.dumps(body).encode()
        elif isinstance(body, str): body = body.encode()
        self.send_response(code); self.send_header('Content-Type', ctype); self.send_header('Content-Length', str(len(body))); self.end_headers(); self.wfile.write(body)
    def do_POST(self):
        n = int(self.headers.get('Content-Length', 0)); body = self.rfile.read(n).decode(); p = urllib.parse.urlparse(self.path).path
        if p == '/ms/devicecode':
            return self.send(200, {"user_code": "ABCD1234", "device_code": "DEV", "verification_uri": "https://microsoft.com/link", "interval": 1, "expires_in": 60})
        if p == '/ms/token':
            f = urllib.parse.parse_qs(body)
            POLLS['n'] = POLLS.get('n', 0) + 1
            if f.get('grant_type', [''])[0].startswith('urn') and POLLS['n'] < 2: return self.send(400, {"error": "authorization_pending"})
            return self.send(200, {"access_token": "MSACCESS", "refresh_token": "REFRESH" + str(POLLS['n'])})
        if p == '/xbl':
            return self.send(200, {"Token": "XBLTOKEN", "DisplayClaims": {"xui": [{"uhs": "UHS"}]}})
        if p == '/xsts':
            return self.send(200, {"Token": "XSTSTOKEN", "DisplayClaims": {"xui": [{"uhs": "UHS"}]}})
        if p == '/mcapi/authentication/login_with_xbox':
            d = json.loads(body)
            if d.get('identityToken') != 'XBL3.0 x=UHS;XSTSTOKEN': return self.send(400, {"error": "bad"})
            return self.send(200, {"access_token": "MCTOKEN", "expires_in": 86400})
        if p == '/ely/authenticate':
            d = json.loads(body)
            if d['password'] == 'secret':
                return self.send(401, {"error": "ForbiddenOperationException", "errorMessage": "Account protected with two factor auth."})
            if d['password'] != 'secret:123456': return self.send(401, {"errorMessage": "Invalid credentials. Invalid nickname or password."})
            return self.send(200, {"accessToken": "ELYTOK", "clientToken": d['clientToken'], "selectedProfile": {"id": "0123456789abcdef0123456789abcdef", "name": "ElyPlayer"}})
        if p == '/ely/validate':
            return self.send(204 if json.loads(body)['accessToken'] == 'ELYTOK' else 403, '')
        if p == '/ely/refresh':
            return self.send(200, {"accessToken": "ELYTOK2", "clientToken": json.loads(body)['clientToken']})
        self.send(404, {"error": "no " + p})

    def do_GET(self):
        u = urllib.parse.urlparse(self.path); q = urllib.parse.parse_qs(u.query); p = u.path
        if p in FILES: return self.send(200, FILES[p], 'application/octet-stream')
        if p == '/v2/search':
            facets = json.loads(q.get('facets', ['[]'])[0]); t = 'mod'
            for f in facets:
                for x in f:
                    if x.startswith('project_type:'): t = x.split(':')[1]
            off = int(q.get('offset', ['0'])[0]); query = q.get('query', [''])[0].lower()
            hits = [h for h in HITS.get(t, []) if query in h[1].lower()]
            vers = [x.split(':')[1] for f in facets for x in f if x.startswith('versions:')]
            if vers: hits = [h for h in hits if vers[0] in VERSIONS[h[0]][0]['game_versions']]
            out = [{"project_id": h[0], "slug": h[0].lower(), "title": h[1], "description": h[2], "author": "tester", "icon_url": f"{HOST}/icons/{i % 4}.png", "downloads": h[3], "follows": h[4], "project_type": t, "categories": ["optimization", "fabric"]} for i, h in enumerate(hits)][off:off + 20]
            return self.send(200, {"hits": out, "offset": off, "limit": 20, "total_hits": len(hits)})
        parts = p.strip('/').split('/')
        if p.startswith('/v2/project/') and len(parts) == 4 and parts[3] == 'version':
            vs = VERSIONS.get(parts[2], [])
            if 'loaders' in q:
                ls = json.loads(q['loaders'][0]); vs = [v for v in vs if set(ls) & set(v['loaders'])]
            if 'game_versions' in q:
                gs = json.loads(q['game_versions'][0]); vs = [v for v in vs if set(gs) & set(v['game_versions'])]
            return self.send(200, vs)
        if p.startswith('/v2/project/') and len(parts) == 3:
            return self.send(200, {"title": TITLES.get(parts[2], parts[2])})
        if p == '/manifest':
            return self.send(200, {"latest": {"release": "1.21.1", "snapshot": "24w40a"}, "versions": [
                {"id": "24w40a", "type": "snapshot", "url": HOST + "/mc/24w40a.json", "sha1": "x"},
                {"id": "1.21.1", "type": "release", "url": HOST + "/mc/1.21.1.json", "sha1": None},
                {"id": "1.20.1", "type": "release", "url": HOST + "/mc/1.20.1.json", "sha1": None}]})
        if p.startswith('/mc/'):
            return self.send(200, {"id": p[4:-5], "downloads": {"client": {"url": HOST + "/files/client-1.21.1.jar", "sha1": client_sha, "size": 1}}})
        if p == '/fabric/versions/loader/1.21.1':
            return self.send(200, [{"loader": {"version": "0.17.0-beta.1", "stable": False}}, {"loader": {"version": "0.16.9", "stable": True}}, {"loader": {"version": "0.16.5", "stable": True}}])
        if p == '/fabric/versions/loader/1.21.1/0.16.9/profile/json':
            return self.send(200, {"id": "fabric-loader-0.16.9-1.21.1", "inheritsFrom": "1.21.1", "type": "release", "libraries": [], "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient"})
        if p.startswith('/fabric/versions/loader/'):
            return self.send(200, [])
        if p == '/forgemeta/maven-metadata.json':
            return self.send(200, {"1.21.1": ["1.21.1-51.0.1", "1.21.1-52.0.0"]})
        if p == '/forgemeta/promotions_slim.json':
            return self.send(200, {"promos": {"1.21.1-recommended": "52.0.0", "1.21.1-latest": "52.0.0"}})
        if p == '/neometa.xml':
            return self.send(200, "<metadata><versioning><versions><version>21.1.5</version><version>21.1.77</version><version>21.0.9</version><version>21.4.2-beta</version></versions></versioning></metadata>", 'application/xml')
        if p.startswith('/assets/'):
            for fp, data in FILES.items():
                h = hashlib.sha1(data).hexdigest()
                if p == '/assets/' + h[:2] + '/' + h: return self.send(200, data, 'application/octet-stream')
        if p == '/runtimes/all.json':
            return self.send(200, {"linux": {"jre-test": [{"manifest": {"url": HOST + "/runtimes/jre-test.json"}}]}})
        if p == '/runtimes/jre-test.json':
            data = FILES['/files/java.sh']
            return self.send(200, {"files": {"bin": {"type": "directory"}, "bin/java": {"type": "file", "executable": True, "downloads": {"raw": {"sha1": hashlib.sha1(data).hexdigest(), "size": len(data), "url": HOST + "/files/java.sh"}}}}})
        if p == '/mcapi/minecraft/profile':
            if self.headers.get('Authorization') == 'Bearer MCTOKEN': return self.send(200, {"id": "0123456789abcdef0123456789abcdef", "name": "MsPlayer"})
            return self.send(401, {})
        self.send(404, {"error": "not found " + p})

class S(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True; allow_reuse_address = True

print("mock on", PORT, flush=True)
S(('127.0.0.1', PORT), H).serve_forever()
