#ifndef COMMS_HPP
#define COMMS_HPP

#include "mapdump.hpp"

#include <cstdint>
#include <filesystem>
#include <functional>
#include <string>
#include <optional>
#include <vector>

#include <QObject>
#include <QMutex>

enum sb_packet_type {
    CLIENT_CONNECT = 0,
    MAP_FOUND_FRAME = 1,
    MAP_FOUND_CONTAINER = 2,
    FOCUS_MAP = 3
};

enum cb_packet_type {
    HIGHLIGHT_MAP = 0,
    REQUEST_MAP_DATA = 1
};

enum highlight_map_flag {
    FRAME = 1,
    ITEM = 2,
    REMOVE = 4,
    CLEAR = 8
};

struct new_client_pkt {
    // 4 bytes | n bytes
    int32_t client_socket_path_length;
    std::string client_socket_path;

    static new_client_pkt from_buffer(uint8_t* buf);
};

struct map_found_frame {
    // 4 bytes | 1 byte | 4 bytes (mapdump format len+flag) | n bytes | 16384 bytes (optional)
    int32_t mapid;
    std::optional<map_t> data;

    static map_found_frame from_buffer(uint8_t* buf);
};

struct map_found_container {
    // 4 bytes | n * 4 bytes
    std::vector<int32_t> mapid;

    static map_found_container from_buffer(uint8_t* buf);
};

struct focus_map {
    // 4 bytes
    int32_t mapid;

    static focus_map from_buffer(uint8_t* buf);
};

struct highlight_map {
    // 1 byte | 4 bytes | 4 bytes | 4 * n bytes
    uint8_t flag;
    uint32_t color;
    std::vector<int32_t> mapid;

    std::vector<uint8_t> to_buffer();
};

struct request_map_data {
    // 1 byte | 4 bytes
    uint8_t flag; // 1 = request maps from all item frames in view instead
    int32_t mapid;

    std::vector<uint8_t> to_buffer();
};

class QThread;
class comms : public QObject {
    Q_OBJECT
private:
    int socketfd;
    bool bad;
    bool terminate;
    bool connected;
    QThread* commsth;
    std::filesystem::path clientsocketp;
    std::filesystem::path serversocketp;
    void message_client(const uint8_t *data, size_t sz);
    std::vector<map_found_frame> qf;
    std::vector<int32_t> qc;
    QMutex mtx;
public:
    comms(std::filesystem::path srvsocketp);
    virtual ~comms();

    void start_server();
    void stop_server();

    void with_queued_frames(std::function<void(std::vector<map_found_frame>&)> f);
    void with_queued_container_ids(std::function<void(std::vector<int32_t>&)> f);

    bool is_connected();

public Q_SLOTS:
    void highlight_maps(std::vector<int32_t> id, uint8_t flag, uint32_t color);
    void request_map(uint8_t flag, int32_t mapid);

Q_SIGNALS: // all emitting from client listener thread
    void client_connected();
    void client_disconnected();
    void map_found_frm();
    void map_found_cont();
    void scroll_to_map(int32_t id);
};

#endif
