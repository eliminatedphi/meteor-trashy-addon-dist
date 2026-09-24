#ifndef MAINWINDOW_HPP
#define MAINWINDOW_HPP

#include "mapdump.hpp"

#include <vector>

#include <QMainWindow>
#include <QColor>

class map_library;
class group_view;
class slice_view;
class diff_view;
class comms;
class QMdiArea;
class QLabel;
class QSettings;
class QTimer;

class mapman_main_window : public QMainWindow
{
    Q_OBJECT
public:
    mapman_main_window();
    ~mapman_main_window();
private:
    map_library *l;
    group_view *gv;
    slice_view *sv;
    diff_view *dv;
    QMdiArea *cw;
    QLabel *lbsb;
    QSettings *s;
    QTimer *updatet;
    QTimer *highlightt;
    std::vector<map_t> pending_imports;
    std::vector<int32_t> uncollected;
public:
    static comms *cm;
    static mapman_main_window *mw;

    void update_statusbar();
    slice_view *get_slice_view();

    QColor get_default_highlight_color();
    void set_default_highlight_color(QColor c);

    QColor get_not_collected_color();
    void set_not_collected_color(QColor c);

    QColor get_unused_color();
    void set_unused_color(QColor c);

    bool highlight_slots();
    void set_highlight_slots(bool v);

    bool highlight_frames();
    void set_highlight_frames(bool v);

    bool automatic_import();
    void set_automatic_import(bool v);
};

#endif
