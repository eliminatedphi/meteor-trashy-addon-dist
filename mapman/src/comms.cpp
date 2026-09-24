#include "comms.hpp"
#include "src/mapdump.hpp"
#include "src/utils.hpp"

#include <cstring>
#include <cstdio>

#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

#include <QThread>

new_client_pkt new_client_pkt::from_buffer(uint8_t* buf) {
    new_client_pkt ret;
    memcpy(&ret.client_socket_path_length, buf, 4);
    if (ret.client_socket_path_length == 0)
        ret.client_socket_path = std::string();
    else if (ret.client_socket_path_length < 100)
        ret.client_socket_path = std::string((char*)(buf + 4), ret.client_socket_path_length);
    return ret;
}

map_found_frame map_found_frame::from_buffer(uint8_t* buf) {
    map_found_frame ret;
    memcpy(&ret.mapid, buf, 4);
    if (buf[4]) {
        map_t d;
        d.id = ret.mapid;
        int32_t v = 0;
        memcpy(&v, buf + 5, 4);
        int32_t len = v & 0xfff'ffff;
        bool locked = ((v & 0x8000'0000) != 0);
        uint8_t scale = ((v >> 28) & 7);
        d.custom_name = len > 0 ? std::string((char*) (buf + 9), len) : std::string();
        memcpy(d.map_data.data(), buf + 9 + len, 16384);
        d.locked = locked;
        d.scale = scale;
        ret.data = std::optional(d);
    }
    return ret;
}

map_found_container map_found_container::from_buffer(uint8_t* buf) {
    map_found_container ret;
    int32_t nmaps = 0;
    memcpy(&nmaps, buf, 4);
    uint8_t *p = buf + 4;
    for (int i = 0; i < nmaps; ++i) {
        int32_t v;
        memcpy(&v, p, 4);
        ret.mapid.push_back(v);
        p += 4;
    }
    return ret;
}

focus_map focus_map::from_buffer(uint8_t* buf) {
    focus_map ret;
    memcpy(&ret.mapid, buf, 4);
    return ret;
}

std::vector<uint8_t> highlight_map::to_buffer() {
    std::vector<uint8_t> ret;
    if (mapid.size() > 16384) return ret;
    ret.resize(1 + 4 + 4 + 4 * mapid.size());
    ret[0] = flag;
    uint32_t s = mapid.size();
    memcpy(ret.data() + 1, &color, 4);
    memcpy(ret.data() + 5, &s, 4);
    memcpy(ret.data() + 9, mapid.data(), 4 * s);
    return ret;
}

std::vector<uint8_t> request_map_data::to_buffer() {
    std::vector<uint8_t> ret;
    ret.resize(5);
    ret[0] = flag;
    memcpy(ret.data() + 1, &mapid, 4);
    return ret;
}

comms::comms(std::filesystem::path srvsocketp) : QObject(nullptr) {
    serversocketp = srvsocketp;
    bad = false;
    terminate = false;
    connected = false;
    clientsocketp = std::filesystem::path();
    socketfd = socket(AF_UNIX, SOCK_STREAM | SOCK_NONBLOCK, 0);
    commsth = nullptr;
    if (!~socketfd) {
        bad = true;
        return;
    }
    sockaddr_un addr;
    memset(&addr, 0, sizeof(sockaddr_un));
    addr.sun_family = AF_UNIX;
    if (srvsocketp.native().length() > sizeof(addr.sun_path) - 1) {
        bad = true;
        return;
    }
    memcpy(addr.sun_path, srvsocketp.c_str(), srvsocketp.native().length());
    if (!~bind(socketfd, (const sockaddr*)&addr, sizeof(addr))) {
        bad = true;
        return;
    }
}

comms::~comms() {
    close(socketfd);
    unlink(serversocketp.c_str());
}

void comms::message_client(const uint8_t *data, size_t sz) {
    int socketfd = -1;
    ssize_t wsz = 0;
    if (clientsocketp.empty()) return;
    socketfd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (!~socketfd) return;
    sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    memcpy(addr.sun_path, clientsocketp.c_str(), clientsocketp.native().length());
    int r = ::connect(socketfd, (const sockaddr*) &addr, sizeof(addr));
    if (!~r) goto cleanup;
    wsz = write(socketfd, data, sz);
    if (wsz < sz) {
        fputs("couldn't write the entire message.\n", stderr);
    }
cleanup:
    if (~socketfd) close(socketfd);
}

void comms::start_server() {
    commsth = QThread::create([this]() {
        if (!~listen(socketfd, 10))
            return;
        fd_set fds;
        uint8_t *buf = new uint8_t[20001];
        for (;;) {
            FD_ZERO(&fds);
            FD_SET(socketfd, &fds);
            timeval timeout = {
                .tv_sec = 0,
                .tv_usec = 100000
            };
            int sr = select(socketfd + 1, &fds, nullptr, nullptr, &timeout);
            if (!~sr) break;
            if (sr == 0) {
                if (terminate) break;
            } else {
                int datafd = accept(socketfd, nullptr, nullptr);
                if (~datafd) {
                    int dsz = read(datafd, buf, 20000);
                    // fprintf(stderr, "data received size: %d\n", dsz);
                    // for (int i = 0; i < dsz; ++i) fprintf(stderr, "%02x ", buf[i]);
                    // fprintf(stderr, "\n");
                    uint8_t *dbuf = buf + 1;
                    switch ((sb_packet_type) *buf) {
                        case CLIENT_CONNECT: {
                            new_client_pkt p = new_client_pkt::from_buffer(dbuf);
                            if (p.client_socket_path_length > 0) {
                                fprintf(stderr, "client connected: %s\n", p.client_socket_path.c_str());
                                clientsocketp = std::filesystem::path(p.client_socket_path);
                                connected = true;
                                Q_EMIT client_connected();
                            } else {
                                fprintf(stderr, "client disconnected\n");
                                clientsocketp = std::filesystem::path();
                                connected = false;
                                Q_EMIT client_disconnected();
                            }
                            break;
                        }
                        case MAP_FOUND_FRAME: {
                            map_found_frame p = map_found_frame::from_buffer(dbuf);
                            fprintf(stderr, "map found in frame %d has data? %d\n", p.mapid, p.data.has_value());
                            mtx.lock();
                            qf.push_back(p);
                            mtx.unlock();
                            Q_EMIT map_found_frm();
                            break;
                        }
                        case MAP_FOUND_CONTAINER: {
                            map_found_container p = map_found_container::from_buffer(dbuf);
                            fprintf(stderr, "%lu map(s) found in container\n", p.mapid.size());
                            mtx.lock();
                            qc.insert(qc.end(), p.mapid.begin(), p.mapid.end());
                            mtx.unlock();
                            Q_EMIT map_found_cont();
                            break;
                        }
                        case FOCUS_MAP: {
                            focus_map p = focus_map::from_buffer(dbuf);
                            Q_EMIT scroll_to_map(p.mapid);
                            break;
                        }
                    }
                    close(datafd);
                }
            }
        }
        delete[] buf;
    });
    commsth->start();
}

void comms::stop_server() {
    terminate = true;
    if (commsth) {
        commsth->wait();
        delete commsth;
        commsth = nullptr;
    }
}

void comms::with_queued_frames(std::function<void(std::vector<map_found_frame>&)> f) {
    mtx.lock();
    f(qf);
    mtx.unlock();
}

void comms::with_queued_container_ids(std::function<void(std::vector<int32_t>&)> f) {
    mtx.lock();
    f(qc);
    mtx.unlock();
}

bool comms::is_connected() { return connected; }

void comms::highlight_maps(std::vector<int32_t> id, uint8_t flag, uint32_t color) {
    highlight_map p {flag, color, id};
    auto b = p.to_buffer();
    std::vector<uint8_t> bb;
    bb.push_back(cb_packet_type::HIGHLIGHT_MAP);
    bb.insert(bb.end(), b.begin(), b.end());
    message_client(bb.data(), bb.size());
}

void comms::request_map(uint8_t flag, int32_t mapid) {
    request_map_data p {flag, mapid};
    auto b = p.to_buffer();
    std::vector<uint8_t> bb;
    bb.push_back(cb_packet_type::REQUEST_MAP_DATA);
    bb.insert(bb.end(), b.begin(), b.end());
    message_client(bb.data(), bb.size());
}
