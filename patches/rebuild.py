#!/usr/bin/env python3
"""通用重打包：用 base APK + 新 classes.dex + 追加 dex，生成未签名 APK（保留条目标志与对齐）。"""
import struct, zlib, sys, zipfile

SRC, NEWDEX, MODDEX, DST = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
MANIFEST = sys.argv[5] if len(sys.argv) > 5 and sys.argv[5] else None
ARSC = sys.argv[6] if len(sys.argv) > 6 and sys.argv[6] else None
ALIGN = 4

def local_header(name, crc, csize, usize, method, extra):
    return struct.pack("<IHHHHHIIIHH", 0x04034B50, 20, 0x0800, method, 0, 0,
                       crc, csize, usize, len(name), len(extra)) + name + extra

def main():
    zin = zipfile.ZipFile(SRC)
    newdex = open(NEWDEX, "rb").read()
    moddex = open(MODDEX, "rb").read()
    skip = {"META-INF/MANIFEST.MF", "META-INF/CERT.SF", "META-INF/CERT.RSA"}
    entries = []
    dex_count = 0
    for info in zin.infolist():
        name = info.filename
        if name in skip:
            continue
        if name == "classes.dex":
            entries.append((name, newdex, zipfile.ZIP_DEFLATED))
            dex_count = 1
            continue
        data = zin.read(name)
        entries.append((name, data, info.compress_type))
        if name == "classes2.dex":
            dex_count = 2
    # 追加模块 dex
    entries.append((f"classes{dex_count+1}.dex", moddex, zipfile.ZIP_DEFLATED))

    if MANIFEST:
        mf = open(MANIFEST, "rb").read()
        for idx, e in enumerate(entries):
            if e[0] == "AndroidManifest.xml":
                entries[idx] = ("AndroidManifest.xml", mf, zipfile.ZIP_DEFLATED)
                print("已替换 AndroidManifest.xml")
                break

    if ARSC:
        ar = open(ARSC, "rb").read()
        for idx, e in enumerate(entries):
            if e[0] == "resources.arsc":
                entries[idx] = ("resources.arsc", ar, zipfile.ZIP_STORED)
                print("已替换 resources.arsc")
                break

    out = bytearray()
    central = []
    for name, data, method in entries:
        nb = name.encode("utf-8")
        crc = zlib.crc32(data) & 0xFFFFFFFF
        if method == zipfile.ZIP_STORED:
            payload = data
        else:
            co = zlib.compressobj(9, zlib.DEFLATED, -15)
            payload = co.compress(data) + co.flush()
        extra = b""
        if method == zipfile.ZIP_STORED:
            pad = (ALIGN - (len(out) + 30 + len(nb)) % ALIGN) % ALIGN
            extra = b"\x00" * pad
        out += local_header(nb, crc, len(payload), len(data), method, extra) + payload
        central.append((nb, crc, len(payload), len(data), method, len(out) - len(payload) - 30 - len(nb) - len(extra), extra))

    cd_start = len(out)
    for nb, crc, csize, usize, method, offset, extra in central:
        out += struct.pack("<IHHHHHHIIIHHHHHII", 0x02014B50, 20, 20, 0x0800, method, 0, 0,
                           crc, csize, usize, len(nb), len(extra), 0, 0, 0, 0, offset) + nb + extra
    cd_size = len(out) - cd_start
    out += struct.pack("<IHHHHIIH", 0x06054B50, 0, 0, len(central), len(central), cd_size, cd_start, 0)
    open(DST, "wb").write(bytes(out))
    print(f"写出 {DST}: {len(out)} 字节, {len(central)} 条目, dex={dex_count+1}")

main()
