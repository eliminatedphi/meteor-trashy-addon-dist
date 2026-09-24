#include "library.hpp"
#include "mapdump.hpp"

#include <cstring>
#include <filesystem>

#include <sqlite3.h>

const int MAPDB_VERSION = 3;

map_library::map_library() : db(nullptr) {
    mapidset_valid = false;
    groupcnt_valid = false;
}

map_library::~map_library()
{
    if (db)
        sqlite3_close(db);
}

std::vector<int> map_library::map_ids()
{
    std::vector<int> ret;
    if (!mapidset_valid) mapidset.clear();
    sqlite3_stmt *st = nullptr;
    sqlite3_prepare_v2(db, "select id from maps;", -1, &st, 0);
    while (1)
    {
        int r = sqlite3_step(st);
        if (r != SQLITE_ROW) break;
        int v = sqlite3_column_int(st, 0);
        ret.push_back(v);
        if (!mapidset_valid) mapidset.insert(v);
    }
    sqlite3_finalize(st);
    mapidset_valid = true;
    return ret;
}

bool map_library::has_map(int id) const
{
    sqlite3_stmt *st = nullptr;
    sqlite3_prepare_v2(db, "select id from maps where id = ?;", -1, &st, 0);
    sqlite3_bind_int(st, 1, id);
    int r = sqlite3_step(st);
    sqlite3_finalize(st);
    return r == SQLITE_ROW;
}

void map_library::set_map(const map_t &map)
{
    sqlite3_stmt *st = nullptr;
    if (has_map(map.id))
    {
        sqlite3_prepare_v2(db, "update maps set custom_name = ?, locked = ?, scale = ?, data = ? where id = ?;", -1, &st, 0);
        sqlite3_bind_text(st, 1, map.custom_name.c_str(), map.custom_name.length(), SQLITE_STATIC);
        sqlite3_bind_int(st, 2, map.locked);
        sqlite3_bind_int(st, 3, map.scale);
        sqlite3_bind_blob(st, 4, map.map_data.data(), map.map_data.size(), SQLITE_STATIC);
        sqlite3_bind_int(st, 5, map.id);
        sqlite3_step(st);
    }
    else
    {
        sqlite3_prepare_v2(db, "insert into maps (id, custom_name, locked, scale, data) values(?, ?, ?, ?, ?);", -1, &st, 0);
        sqlite3_bind_int(st, 1, map.id);
        sqlite3_bind_text(st, 2, map.custom_name.c_str(), map.custom_name.length(), SQLITE_STATIC);
        sqlite3_bind_int(st, 3, map.locked);
        sqlite3_bind_int(st, 4, map.scale);
        sqlite3_bind_blob(st, 5, map.map_data.data(), map.map_data.size(), SQLITE_STATIC);
        sqlite3_step(st);
    }
    mapidset_valid = false;
    sqlite3_finalize(st);
}

map_t map_library::get_map(int id) const
{
    sqlite3_stmt *st = nullptr;
    sqlite3_prepare_v2(db, "select custom_name, locked, scale, data from maps where id = ?;", -1, &st, 0);
    sqlite3_bind_int(st, 1, id);
    map_t ret{id, std::string(), false, 0, map_data_t()};
    if (sqlite3_step(st) == SQLITE_ROW)
    {
        ret.custom_name = std::string((char*)sqlite3_column_text(st, 0));
        ret.locked = sqlite3_column_int(st, 1) ? true : false;
        ret.scale = sqlite3_column_int(st, 2);
        memcpy(ret.map_data.data(), sqlite3_column_blob(st, 3), ret.map_data.size());
    }
    sqlite3_finalize(st);
    return ret;
}

const std::unordered_set<int>& map_library::map_idset() {
    if (!mapidset_valid) {
        mapidset.clear();
        for (auto &i : map_ids())
            mapidset.insert(i);
        mapidset_valid = true;
    }
    return mapidset;
}

std::vector<int64_t> map_library::groups()
{
    std::vector<int64_t> ret;
    sqlite3_stmt *st = nullptr;
    sqlite3_prepare_v2(db, "select rowid from groups;", -1, &st, 0);
    while (1)
    {
        int r = sqlite3_step(st);
        if (r != SQLITE_ROW) break;
        ret.push_back(sqlite3_column_int64(st, 0));
    }
    sqlite3_finalize(st);
    groupcnt = ret.size();
    groupcnt_valid = true;
    return ret;
}

int64_t map_library::new_group(const map_group_t &g)
{
    groupcnt_valid = false;
    sqlite3_stmt *st = nullptr;
    sqlite3_prepare_v2(db, "insert into groups (title, author, horizontal_count, vertical_count) values(?, ?, ?, ?);", -1, &st, 0);
    sqlite3_bind_text(st, 1, g.title.c_str(), g.title.length(), SQLITE_STATIC);
    sqlite3_bind_text(st, 2, g.author.c_str(), g.author.length(), SQLITE_STATIC);
    sqlite3_bind_int(st, 3, g.hc);
    sqlite3_bind_int(st, 4, g.vc);
    sqlite3_step(st);
    sqlite3_finalize(st);
    int64_t id = sqlite3_last_insert_rowid(db);
    sqlite3_prepare_v2(db, "insert into group_maps (gid, pos, map_id) values(?, ?, ?);", -1, &st, 0);
    sqlite3_bind_int64(st, 1, id);
    for (size_t i = 0; i < g.ids.size(); ++i)
    {
        if (!g.populated[i])
            continue;
        sqlite3_bind_int(st, 2, i);
        sqlite3_bind_int(st, 3, g.ids[i]);
        sqlite3_step(st);
        if (i + 1 < g.ids.size())
            sqlite3_reset(st);
    }
    sqlite3_finalize(st);
    return id;
}

bool map_library::has_group(int64_t gid) const
{
    sqlite3_stmt *st = nullptr;
    sqlite3_prepare_v2(db, "select rowid from groups where rowid = ?;", -1, &st, 0);
    sqlite3_bind_int(st, 1, gid);
    int r = sqlite3_step(st);
    sqlite3_finalize(st);
    return r == SQLITE_ROW;
}

void map_library::set_group(int64_t gid, const map_group_t &g)
{
    if (!has_group(gid))
        return;
    sqlite3_stmt *st = nullptr;
    sqlite3_prepare_v2(db, R"sql(
    update groups set title = ?, author = ?, horizontal_count = ?, vertical_count = ? where rowid = ?;
    )sql", -1, &st, 0);
    sqlite3_bind_text(st, 1, g.title.c_str(), g.title.length(), SQLITE_STATIC);
    sqlite3_bind_text(st, 2, g.author.c_str(), g.author.length(), SQLITE_STATIC);
    sqlite3_bind_int(st, 3, g.hc);
    sqlite3_bind_int(st, 4, g.vc);
    sqlite3_bind_int64(st, 5, gid);
    sqlite3_step(st);
    sqlite3_finalize(st);
    sqlite3_prepare_v2(db, R"sql(
    delete from group_maps where gid = ?;
    )sql", -1, &st, 0);
    sqlite3_bind_int64(st, 1, gid);
    sqlite3_step(st);
    sqlite3_finalize(st);
    sqlite3_prepare_v2(db, "insert into group_maps (gid, pos, map_id) values(?, ?, ?);", -1, &st, 0);
    sqlite3_bind_int(st, 1, gid);
    for (size_t i = 0; i < g.ids.size(); ++i)
    {
        if (!g.populated[i])
            continue;
        sqlite3_bind_int(st, 2, i);
        sqlite3_bind_int(st, 3, g.ids[i]);
        sqlite3_step(st);
        if (i + 1 < g.ids.size())
            sqlite3_reset(st);
    }
    sqlite3_finalize(st);
}

map_group_t map_library::get_group(int64_t gid) const
{
    map_group_t ret{};
    if (!has_group(gid))
        return ret;
    sqlite3_stmt *st = nullptr;
    sqlite3_prepare_v2(db, "select title, author, horizontal_count, vertical_count from groups where rowid = ?;", -1, &st, 0);
    sqlite3_bind_int64(st, 1, gid);
    if (sqlite3_step(st) == SQLITE_ROW)
    {
        ret.title = std::string((char*)sqlite3_column_text(st, 0));
        ret.author = std::string((char*)sqlite3_column_text(st, 1));
        ret.hc = sqlite3_column_int(st, 2);
        ret.vc = sqlite3_column_int(st, 3);
    }
    sqlite3_finalize(st);
    ret.ids.resize(ret.hc * ret.vc);
    ret.populated.resize(ret.hc * ret.vc, false);
    sqlite3_prepare_v2(db, "select pos, map_id from group_maps where gid = ?;", -1, &st, 0);
    sqlite3_bind_int(st, 1, gid);
    while (sqlite3_step(st) == SQLITE_ROW)
    {
        int pos = sqlite3_column_int(st, 0);
        int id = sqlite3_column_int(st, 1);
        if (pos < ret.hc * ret.vc)
        {
            ret.ids[pos] = id;
            ret.populated[pos] = true;
        }
    }
    sqlite3_finalize(st);
    return ret;
}

void map_library::remove_group(int64_t gid)
{
    if (!has_group(gid))
        return;
    groupcnt_valid = false;
    sqlite3_stmt *st = nullptr;
    sqlite3_prepare_v2(db, R"sql(
        delete from groups where rowid = ?;
        delete from group_maps where gid = ?1;
    )sql", -1, &st, 0);
    sqlite3_bind_int64(st, 1, gid);
    sqlite3_step(st);
    sqlite3_finalize(st);
}

size_t map_library::groups_count() {
    if (!groupcnt_valid) {
        groupcnt = groups().size();
        groupcnt_valid = true;
    }
    return groupcnt;
}

std::unordered_set<int> map_library::ungrouped_maps() {
    sqlite3_stmt *st = nullptr;
    std::unordered_set<int> ret;
    sqlite3_prepare_v2(db, R"sql(
        select id from maps full outer join group_maps on maps.id == group_maps.map_id where map_id is NULL
    )sql", -1, &st, 0);
    while (sqlite3_step(st) == SQLITE_ROW) {
        int id = sqlite3_column_int(st, 0);
        ret.insert(id);
    }
    return ret;
}

/*
 * tally (in): list of maps in storage containers
 * a_b (out): maps that are in the library but not in containers
 * b_a (out): maps that are in containers but not in the library
 */
void map_library::tally_diff(const std::vector<int> &tally,
                             std::vector<int> &a_b,
                             std::vector<int> &b_a) const
{
    sqlite3_exec(db, "create table temp_tally(id int);", nullptr, nullptr, nullptr);
    sqlite3_stmt *st = nullptr;
    sqlite3_prepare_v2(db, "insert into temp_tally (id) values(?);", -1, &st, 0);
    for (auto &id : tally)
    {
        sqlite3_bind_int(st, 1, id);
        sqlite3_step(st);
        sqlite3_reset(st);
    }
    sqlite3_finalize(st);
    sqlite3_prepare_v2(db, R"sql(
        select maps.id as mid, temp_tally.id as tid
        from maps full outer join temp_tally on maps.id = temp_tally.id
        where mid is NULL or tid is NULL;
    )sql", -1, &st, 0);
    a_b.clear();
    b_a.clear();
    while (sqlite3_step(st) == SQLITE_ROW)
    {
        int mid = sqlite3_column_int(st, 0);
        int tid = sqlite3_column_int(st, 1);
        bool mid_null = sqlite3_column_type(st, 0) == SQLITE_NULL;
        bool tid_null = sqlite3_column_type(st, 1) == SQLITE_NULL;
        if (mid_null)
            b_a.push_back(tid);
        if (tid_null)
            a_b.push_back(mid);
    }
    sqlite3_finalize(st);
    sqlite3_exec(db, "drop table temp_tally;", nullptr, nullptr, nullptr);
    std::sort(a_b.begin(), a_b.end());
    std::sort(b_a.begin(), b_a.end());
}


bool map_library::open_db(const std::filesystem::path &p)
{
    bool needs_creation = !std::filesystem::is_regular_file(p);
#if PATH_VALSIZE == 2
    sqlite3_open16(p.c_str(), &db);
#else
    sqlite3_open(p.c_str(), &db);
#endif
    if (needs_creation)
        init_db();
    else
    {
        if (!verify_db())
        {
            sqlite3_close(db);
            db = nullptr;
            return false;
        }
    }
    return true;
}
bool map_library::is_db_open() {
    return db;
}

void map_library::init_db()
{
    sqlite3_exec(db, R"sql(
        create table mapdbinfo(
            version int,
            name text
        );
    )sql", nullptr, nullptr, nullptr);

    sqlite3_stmt *vst;
    sqlite3_prepare_v2(db, "insert into mapdbinfo (version) values(?);", -1, &vst, 0);
    sqlite3_bind_int(vst, 1, MAPDB_VERSION);
    sqlite3_step(vst);
    sqlite3_finalize(vst);

    sqlite3_exec(db, R"sql(
        create table maps(
            id integer primary key,
            custom_name text,
            locked integer,
            scale integer,
            data blob
        );
    )sql", nullptr, nullptr, nullptr);
    sqlite3_exec(db, R"sql(
        create table groups(
            title text,
            author text,
            horizontal_count integer,
            vertical_count integer
        );
    )sql", nullptr, nullptr, nullptr);
    sqlite3_exec(db, R"sql(
        create table group_maps(
            gid integer,
            pos integer,
            map_id integer
        );
    )sql", nullptr, nullptr, nullptr);
}

void map_library::update_db_v2_3()
{
    sqlite3_exec(db, R"sql(
        alter table maps add column scale integer;
    )sql", nullptr, nullptr, nullptr);
    sqlite3_exec(db, R"sql(
        update mapdbinfo set version=3;
    )sql", nullptr, nullptr, nullptr);
    sqlite3_exec(db, R"sql(
        update maps set scale=255;
    )sql", nullptr, nullptr, nullptr);
}

bool map_library::verify_db()
{
    sqlite3_stmt *vst;
    sqlite3_prepare_v2(db, "select version from mapdbinfo;", -1, &vst, 0);
    if (sqlite3_step(vst) != SQLITE_ROW) {sqlite3_finalize(vst); return false;}
    int v = sqlite3_column_int(vst, 0);
    if (v == 2) {
        update_db_v2_3();
        v = 3;
    }
    if (MAPDB_VERSION != v) {sqlite3_finalize(vst); return false;}
    sqlite3_finalize(vst);
    return true;
}
