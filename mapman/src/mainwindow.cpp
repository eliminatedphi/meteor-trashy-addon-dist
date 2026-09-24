#include "mainwindow.hpp"
#include "groupview.hpp"
#include "mapdump.hpp"
#include "sliceview.hpp"
#include "diffview.hpp"
#include "library.hpp"
#include "comms.hpp"

#include <QMdiArea>
#include <QMenuBar>
#include <QMenu>
#include <QAction>
#include <QLabel>
#include <QFileDialog>
#include <QApplication>
#include <QStatusBar>
#include <QMessageBox>
#include <QSettings>
#include <QTimer>
#include <QIcon>
#include <QPixmap>
#include <QColorDialog>

#include <filesystem>
#include <vector>

#define MAPMAN_VERSION "pre-alpha"

comms *mapman_main_window::cm = nullptr;
mapman_main_window *mapman_main_window::mw = nullptr;

mapman_main_window::mapman_main_window() : QMainWindow()
{
    mw = this;
    l = nullptr;
    s = new QSettings("phi.eliminated", "BibliothecaArcana", this);
    updatet = new QTimer();
    updatet->setSingleShot(true);
    highlightt = new QTimer();
    highlightt->setSingleShot(true);
    lbsb = new QLabel();
    statusBar()->addPermanentWidget(lbsb);
    update_statusbar();
    cw = new QMdiArea();
    setCentralWidget(cw);
    gv = new group_view();
    sv = new slice_view();
    dv = new diff_view();
    cm = new comms("/tmp/mapman.socket");
    cm->start_server();
    cw->addSubWindow(gv);
    cw->addSubWindow(sv);
    gv->hide();
    sv->hide();
    dv->hide();
    this->setWindowTitle("Bibliotheca Arcana");
    cw->cascadeSubWindows();
    auto fm = this->menuBar()->addMenu("&File");
    auto cra = fm->addAction("Create / L&oad MapDB...");
    auto cla = fm->addAction("&Close MapDB");
    fm->addSeparator();
    auto xca = fm->addAction("&Export Current Art...");
    auto xaa = fm->addAction("Export &All Arts...");
    fm->addSeparator();
    auto fua = fm->addAction("Find &unused slices");
    fm->addSeparator();
    auto lda = fm->addAction("&Load Map Dump...");
    fm->addSeparator();
    auto cta = fm->addAction("&Compare Map Tally...");
    fm->addSeparator();
    auto qa = fm->addAction("&Quit");
    cla->setEnabled(false);
    lda->setEnabled(false);
    cta->setEnabled(false);
    xca->setEnabled(false);
    xaa->setEnabled(false);
    auto im = this->menuBar()->addMenu("&Integration");
    auto hca = im->addAction("Set default &highlight color");
    auto nca = im->addAction("Set &not collected color");
    auto uca = im->addAction("Set &unused slices color");
    im->addSeparator();
    auto hfa = im->addAction("Highlight item &frames");
    auto hsa = im->addAction("Highlight inventory &slots");
    im->addSeparator();
    auto aia = im->addAction("Enable &auto import");
    auto ina = im->addAction("&Import all framed maps now");
    ina->setEnabled(false);
    hfa->setCheckable(true);
    hsa->setCheckable(true);
    aia->setCheckable(true);
    hfa->setChecked(highlight_frames());
    hsa->setChecked(highlight_slots());
    aia->setChecked(automatic_import());
    QPixmap p0(64, 64);
    p0.fill(get_default_highlight_color());
    hca->setIcon(QIcon(p0));
    QPixmap p1(64, 64);
    p1.fill(get_not_collected_color());
    nca->setIcon(QIcon(p1));
    QPixmap p2(64, 64);
    p2.fill(get_unused_color());
    uca->setIcon(QIcon(p2));
    connect(hca, &QAction::triggered, [this, hca] {
        QColor c = QColorDialog::getColor(get_default_highlight_color(), this);
        if (c.isValid()) {
            set_default_highlight_color(c);
            QPixmap p(64, 64);
            p.fill(c);
            hca->setIcon(QIcon(p));
        }
    });
    connect(nca, &QAction::triggered, [this, nca] {
        QColor c = QColorDialog::getColor(get_not_collected_color(), this);
        if (c.isValid()) {
            set_not_collected_color(c);
            QPixmap p(64, 64);
            p.fill(c);
            nca->setIcon(QIcon(p));
        }
    });
    connect(uca, &QAction::triggered, [this, uca] {
        QColor c = QColorDialog::getColor(get_unused_color(), this);
        if (c.isValid()) {
            set_unused_color(c);
            QPixmap p(64, 64);
            p.fill(c);
            uca->setIcon(QIcon(p));
        }
    });
    connect(ina, &QAction::triggered, [this] {
        cm->request_map(1, 0);
    });
    connect(hfa, &QAction::toggled, [this](bool c) { set_highlight_frames(c);});
    connect(hsa, &QAction::toggled, [this](bool c) { set_highlight_slots(c);});
    connect(aia, &QAction::toggled, [this](bool c) { set_automatic_import(c);});
    auto wm = this->menuBar()->addMenu("&Windows");
    auto swa = wm->addAction("Map &listings");
    auto gwa = wm->addAction("Map &art listings");
    connect(swa, &QAction::triggered, [this] { sv->widget()->show(); sv->show(); });
    connect(gwa, &QAction::triggered, [this] { gv->widget()->show(); gv->show(); });
    swa->setEnabled(false);
    gwa->setEnabled(false);
    connect(cra, &QAction::triggered, [this, cla, lda, cta, xca, xaa, swa, gwa] {
        QString dbp = s->value("dbpath", QString()).toString();
        QString fn = QFileDialog::getSaveFileName(this, "Create / Load MapDB", dbp, "*.mapdb", nullptr, QFileDialog::Option::DontConfirmOverwrite);
        if (fn.length())
        {
            if (l) delete l;
            l = new map_library();
            std::filesystem::path p(fn.toStdWString());
            if (!l->open_db(p))
            {
                delete l;
                l = nullptr;
                return;
            }
            s->setValue("dbpath", QString::fromStdWString(p.parent_path().wstring()));
            sv->set_library(l);
            gv->set_library(l);
            update_statusbar();
            sv->refresh();
            gv->refresh_list();
            cla->setEnabled(true);
            lda->setEnabled(true);
            cta->setEnabled(true);
            xca->setEnabled(true);
            xaa->setEnabled(true);
            swa->setEnabled(true);
            gwa->setEnabled(true);
            swa->trigger();
            gwa->trigger();
            cw->cascadeSubWindows();
        }
    });
    connect(cla, &QAction::triggered, [this, cla, lda, cta, xca, xaa, swa, gwa] {
        if (l) delete l;
        l = nullptr;
        cla->setEnabled(false);
        lda->setEnabled(false);
        cta->setEnabled(false);
        xca->setEnabled(false);
        xaa->setEnabled(false);
        swa->setEnabled(false);
        gwa->setEnabled(false);
        gv->hide();
        sv->hide();
    });
    connect(fua, &QAction::triggered, [this] { sv->highlight_ungrouped_maps(); });
    connect(xca, &QAction::triggered, gv, &group_view::export_current_group);
    connect(xaa, &QAction::triggered, gv, &group_view::export_all_groups);
    connect(lda, &QAction::triggered, [this] {
        if (!l) return;
        QString fn = QFileDialog::getOpenFileName(this, "Load Map Dump", QString(), "*.gz");
        if (fn.length())
        {
            std::vector<map_t> m;
            if (load_dumps(fn.toStdString().c_str(), m))
            {
                for (auto &map : m)
                    l->set_map(map);
                sv->refresh();
                uncollected.clear();
                update_statusbar();
            }
        }
    });
    connect(cta, &QAction::triggered, [this] {
        if (!l) return;
        QString fn = QFileDialog::getOpenFileName(this, "Select Map Tally", QString(), "*.gz");
        if (fn.length())
        {
            auto tally = load_tally(fn.toStdString().c_str());
            std::vector<int> a_b, b_a;
            l->tally_diff(tally, a_b, b_a);
            dv->set_results(a_b, b_a);
            dv->show();
        }
    });
    connect(qa, &QAction::triggered, [] {
        QApplication::exit();
    });
    auto hm = this->menuBar()->addMenu("&Help");
    auto aba = hm->addAction("&About");
    auto aqa = hm->addAction("About &Qt");
    connect(aba, &QAction::triggered, [this]{
        QMessageBox::about(this, "About Mapman", QString(R"(
Bibliotheca Arcana 
A minecraft map art management utility.

%1

eliminatedphi 2023-2026

License: GPL-3.0-only
)")
    .arg(MAPMAN_VERSION).trimmed());
    });
    connect(aqa, &QAction::triggered, []{ QApplication::aboutQt(); });
    connect(cm, &comms::client_connected, [this, ina] {
        ina->setEnabled(true);
        statusBar()->showMessage("Client connected.");
    });
    connect(cm, &comms::client_disconnected, [this, ina] {
        ina->setEnabled(false);
        statusBar()->showMessage("Client disconnected.");
    });
    auto update_highlights = [this] {
        uint8_t f = 0;
        if (mw->highlight_frames()) f |= highlight_map_flag::FRAME;
        if (mw->highlight_slots()) f |= highlight_map_flag::ITEM;
        cm->highlight_maps(std::vector<int32_t>(), highlight_map_flag::CLEAR | f, 0);
        if (f) {
            auto hh = gv->get_highlights();
            for (auto [col, h] : hh)
                cm->highlight_maps(h, f, col);
        }
        cm->highlight_maps(uncollected, f, get_not_collected_color().rgb());
    };
    connect(gv, &group_view::highlight_changed, this, update_highlights);
    connect(cm, &comms::map_found_cont, this, [this, update_highlights] {
        // highlight any maps that's not in the library
        if (!l || !l->is_db_open()) return;
        cm->with_queued_container_ids([this, update_highlights](auto &qc) {
            const auto &set = l->map_idset();
            for (auto &i : qc) {
                if (set.find(i) == set.end()) {
                    uncollected.push_back(i);
                }
            }
            qc.clear();
            highlightt->start(250);
        });
    });
    connect(cm, &comms::map_found_frm, this, [this] {
        if (!l || !l->is_db_open()) return;
        cm->with_queued_frames([this](auto &qf) {
            for (auto &f : qf) {
                if (f.data.has_value()) {
                    pending_imports.push_back(f.data.value());
                    updatet->start(500);
                } else {
                    const auto &s = l->map_idset();
                    if (s.find(f.mapid) == s.end()) {
                        if (automatic_import())
                            cm->request_map(0, f.mapid);
                        else
                            uncollected.push_back(f.mapid);
                    }
                }
            }
            qf.clear();
            highlightt->start(250);
        });
    });
    connect(cm, &comms::scroll_to_map, sv, &slice_view::focus_map_id);
    connect(updatet, &QTimer::timeout, [this]() {
        if (!l || !l->is_db_open()) return;
        for (auto &m : pending_imports) {
            l->set_map(m);
        }
        sv->refresh();
        statusBar()->showMessage(QString("%1 maps imported.").arg(pending_imports.size()));
        pending_imports.clear();
        uncollected.clear();
    });
    connect(highlightt, &QTimer::timeout, update_highlights);
}

mapman_main_window::~mapman_main_window()
{
    if (l)
        delete l;
    delete dv;
    delete updatet;
    delete highlightt;
    cm->stop_server();
    delete cm;
}

void mapman_main_window::update_statusbar() {
    if (!l || !l->is_db_open()) lbsb->setText("No database loaded.");
    else 
    lbsb->setText(QString("%1 arts and %2 slices loaded.").arg(l->groups_count()).arg(l->map_idset().size()));
}
slice_view* mapman_main_window::get_slice_view() { return sv; }

QColor mapman_main_window::get_default_highlight_color() {
    return s->value("default-highlight-color", QColor(0xFFFFEE33)).value<QColor>();
}
void mapman_main_window::set_default_highlight_color(QColor c) {
    s->setValue("default-highlight-color", c);
}

QColor mapman_main_window::get_not_collected_color() {
    return s->value("not-collected-color", QColor(0xFFFF33EE)).value<QColor>();
}
void mapman_main_window::set_not_collected_color(QColor c) {
    s->setValue("not-collected-color", c);
}

QColor mapman_main_window::get_unused_color() {
    return s->value("unused-slice-color", QColor(0xFF661111)).value<QColor>();
}
void mapman_main_window::set_unused_color(QColor c) {
    s->setValue("unused-slice-color", c);
}

bool mapman_main_window::highlight_slots() {
    return s->value("highlight-slots", false).toBool();
}
void mapman_main_window::set_highlight_slots(bool v) {
    s->setValue("highlight-slots", v);
}

bool mapman_main_window::highlight_frames() {
    return s->value("highlight-frames", false).toBool();
}
void mapman_main_window::set_highlight_frames(bool v) {
    s->setValue("highlight-frames", v);
}

bool mapman_main_window::automatic_import() {
    return s->value("automatic-import", false).toBool();
}
void mapman_main_window::set_automatic_import(bool v) {
    s->setValue("automatic-import", v);
}
