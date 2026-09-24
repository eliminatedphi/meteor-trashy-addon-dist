#include "mapdump.hpp"

#include <algorithm>

#include <zlib.h>

bool load_dump(gzFile f, map_t &d)
{
    if (gzread(f, &d.id, 4) < 4) return false;
    int name_len;
    if (gzread(f, &name_len, 4) < 4) return false;
    d.locked = ((name_len & 0x8000'0000) != 0);
    d.scale = ((name_len >> 28) & 7);
    name_len &= 0xfff'ffff;
    if (name_len)
    {
        char *name = new char[name_len];
        if (gzread(f, name, name_len) != name_len)
        {
            delete[] name;
            return false;
        }
        d.custom_name = std::string(name, name_len);
        delete[] name;
    } else d.custom_name = std::string();
    if (gzread(f, d.map_data.data(), 128 * 128) < 128 * 128)
        return false;
    return true;
}

bool load_dumps(const char *fn, std::vector<map_t> &dumps)
{
    gzFile f = gzopen(fn, "rb");
    dumps.clear();
    while (!gzeof(f))
    {
        map_t d;
        if (load_dump(f, d))
            dumps.emplace_back(std::move(d));
    }
    gzclose(f);
    return dumps.size() != 0;
}

std::vector<int> load_tally(const char *fn)
{
    std::vector<int> ret;
    gzFile f = gzopen(fn, "rb");
    while (!gzeof(f))
    {
        int t;
        gzread(f, &t, 4);
        ret.push_back(t);
    }
    std::sort(ret.begin(), ret.end());
    auto uend = std::unique(ret.begin(), ret.end());
    ret.erase(uend, ret.end());
    return ret;
}
