#ifndef GROUPVIEW_HPP
#define GROUPVIEW_HPP

#include <cstdint>
#include <vector>
#include <utility>

#include <QMdiSubWindow>
#include "mapdump.hpp"

class QTableView;
class QPushButton;
class QLineEdit;
class QSpinBox;
class QLabel;
class QComboBox;
class QCheckBox;
class QStandardItemModel;
class QSortFilterProxyModel;
class map_library;
class map_painter;
class comms;

class group_view : public QMdiSubWindow
{
Q_OBJECT
public:
    group_view();
    ~group_view();
    void set_library(map_library *lib);
    void export_group_image(QString fn, int64_t gid);
    std::vector<std::pair<uint32_t, std::vector<int32_t>>> get_highlights();

public Q_SLOTS:
    void add_group();
    void rem_group();
    void update_fields();
    void update_library();
    void painter_drop(int pos, bool populated, int id);
    void refresh_list();
    void reset_dim();
    void update_map_view();
    void export_current_group();
    void export_all_groups();

Q_SIGNALS:
    void highlight_changed();

private:
    QTableView *tv;
    QStandardItemModel *m;
    QSortFilterProxyModel *mf;
    QPushButton *pbadd;
    QPushButton *pbrem;
    QLineEdit *tetitle;
    QLineEdit *teauthor;
    QLineEdit *tefilter;
    QCheckBox *cbfiltauthor;
    QSpinBox *sbh;
    QSpinBox *sbv;
    QComboBox *cbscale;
    QPushButton *pbapply;
    bool dirty;
    map_library *l;
    map_painter *p;
    map_group_t current_group;
};

#endif
