#ifndef LIBRARY_HPP
#define LIBRARY_HPP

#include "mapdump.hpp"

#include <filesystem>
#include <unordered_set>
#include <vector>

struct sqlite3;

class map_library
{
public:
    map_library();
    ~map_library();

    std::vector<int> map_ids();
    bool has_map(int id) const;
    void set_map(const map_t &map);
    map_t get_map(int id) const;
    const std::unordered_set<int>& map_idset();

    std::vector<int64_t> groups();
    int64_t new_group(const map_group_t &g);
    bool has_group(int64_t gid) const;
    void set_group(int64_t gid, const map_group_t &g);
    map_group_t get_group(int64_t gid) const;
    void remove_group(int64_t gid);
    size_t groups_count();
    std::unordered_set<int> ungrouped_maps();

    void tally_diff(const std::vector<int> &tally,
                          std::vector<int> &a_b,
                          std::vector<int> &b_a) const;

    bool open_db(const std::filesystem::path &p);
    bool is_db_open();
private:
    void init_db();
    bool verify_db();
    void update_db_v2_3();
    sqlite3 *db;

    bool mapidset_valid;
    std::unordered_set<int> mapidset;
    bool groupcnt_valid;
    size_t groupcnt;
};

#endif
