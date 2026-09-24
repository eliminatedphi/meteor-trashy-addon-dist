#ifndef MAPDUMP_HPP
#define MAPDUMP_HPP

#include <cstdint>
#include <string>
#include <vector>

#include "utils.hpp"

struct map_t
{
    int id;
    std::string custom_name;
    bool locked;
    uint8_t scale;
    map_data_t map_data;
};

struct map_group_t
{
    std::string title;
    std::string author;
    int hc;
    int vc;
    std::vector<int> ids;
    std::vector<bool> populated;
};

bool load_dumps(const char *fn, std::vector<map_t> &dumps);
std::vector<int> load_tally(const char *fn);

#endif
