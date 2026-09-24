#ifndef SLICEVIEW_HPP
#define SLICEVIEW_HPP

#include <unordered_map>

#include <QMdiSubWindow>
#include <QModelIndex>

class map_library;
class map_painter;
class QListView;
class QStandardItemModel;
class QSortFilterProxyModel;
class QLineEdit;
class QComboBox;

class slice_view : public QMdiSubWindow
{
    Q_OBJECT
public:
    slice_view();
    ~slice_view();
    void set_library(map_library *lib);
    void highlight_ungrouped_maps();
    void remove_highlight(int id);
public Q_SLOTS:
    void refresh();
    void focus_map_id(int32_t id);
protected:
    bool eventFilter(QObject *o, QEvent *e);
private:
    QListView *lv;
    QStandardItemModel *m;
    QSortFilterProxyModel *mf;
    QLineEdit *tefilter;
    QComboBox *cbscale;
    map_painter *p;
    map_library *l;
    QModelIndex dragidx;
    QPointF dragpos;
    std::unordered_map<int, int> id_row;
};

#endif
