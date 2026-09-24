#include "sliceview.hpp"
#include "library.hpp"
#include "painter.hpp"
#include "mainwindow.hpp"
#include "utils.hpp"

#include <unordered_set>

#include <QListView>
#include <QGraphicsView>
#include <QStandardItemModel>
#include <QSortFilterProxyModel>
#include <QSplitter>
#include <QHBoxLayout>
#include <QVBoxLayout>
#include <QDrag>
#include <QMimeData>
#include <QEvent>
#include <QLabel>
#include <QMouseEvent>
#include <QLineEdit>
#include <QComboBox>
#include <QApplication>

slice_view::slice_view() : l(nullptr)
{
    lv = new QListView(this);
    m = new QStandardItemModel(this);
    mf = new QSortFilterProxyModel(this);
    mf->setSourceModel(m);
    mf->setFilterCaseSensitivity(Qt::CaseSensitivity::CaseInsensitive);
    tefilter = new QLineEdit(this);
    tefilter->setPlaceholderText("Filter");
    lv->setModel(mf);
    lv->setSelectionMode(QAbstractItemView::SelectionMode::SingleSelection);
    lv->setDragDropMode(QAbstractItemView::DragDropMode::NoDragDrop);
    lv->viewport()->installEventFilter(this);
    lv->setIconSize(QSize(24, 24));
    cbscale = new QComboBox();
    cbscale = new QComboBox();
    cbscale->addItems({QStringLiteral("100%"), QStringLiteral("200%"), QStringLiteral("300%"), QStringLiteral("400%")});
    connect(cbscale, &QComboBox::currentIndexChanged, this, [this](int idx) {
        p->set_scaling(idx + 1);
        switch (idx) {
            case 0: lv->setIconSize(QSize(24, 24)); break;
            case 1: lv->setIconSize(QSize(32, 32)); break;
            case 2: lv->setIconSize(QSize(48, 48)); break;
            case 3: lv->setIconSize(QSize(64, 64)); break;
        }
    });
    p = new map_painter(false, this);
    p->set_dimension(1, 1);
    auto leftcontainer = new QWidget();
    auto leftlayout = new QVBoxLayout();
    auto lowerleftlayout = new QHBoxLayout();
    lowerleftlayout->addWidget(tefilter);
    lowerleftlayout->addWidget(new QLabel("Zoom"));
    lowerleftlayout->addWidget(cbscale);
    leftcontainer->setLayout(leftlayout);
    leftlayout->addWidget(lv);
    leftlayout->addLayout(lowerleftlayout);
    auto layout = new QSplitter(Qt::Orientation::Horizontal, this);
    layout->setContentsMargins(6, 6, 6, 6);
    layout->addWidget(leftcontainer);
    layout->addWidget(p->view());
    layout->setStretchFactor(0, 1);
    layout->setStretchFactor(1, 3);
    layout->setCollapsible(0, false);
    layout->setCollapsible(1, false);
    this->setWidget(layout);
    connect(lv->selectionModel(), &QItemSelectionModel::currentChanged,
        [this](const QModelIndex &cur, const QModelIndex&) {
            if (this->l)
                this->p->set_map_id(0, true, cur.data(Qt::UserRole + 1).toInt());
        });
    connect(lv, &QAbstractItemView::pressed,
        [this](const QModelIndex &idx) {
            dragidx = idx;
        });
    connect(tefilter, &QLineEdit::textChanged, mf, &QSortFilterProxyModel::setFilterFixedString);
    this->setWindowTitle("Map listings");
    this->setAttribute(Qt::WA_DeleteOnClose, false);
}

slice_view::~slice_view()
{
    delete p;
}

void slice_view::set_library(map_library *lib)
{
    l = lib;
    p->set_map_library(l);
    refresh();
}

bool slice_view::eventFilter(QObject *o, QEvent *e)
{
    if (e->type() == QEvent::MouseButtonRelease)
        dragidx = QModelIndex();
    if (e->type() == QEvent::MouseButtonPress)
        dragpos = static_cast<QMouseEvent*>(e)->globalPosition();
    if (e->type() == QEvent::MouseMove)
    {
        auto pos = static_cast<QMouseEvent*>(e)->globalPosition();
        if (dragidx.isValid() && (pos - dragpos).manhattanLength() >= QApplication::startDragDistance())
        {
            auto *d = new QDrag(lv);
            int mapid = dragidx.data(Qt::ItemDataRole::UserRole + 1).toInt();
            d->setPixmap(qvariant_cast<QIcon>(dragidx.data(Qt::ItemDataRole::DecorationRole)).pixmap(128, 128));
            auto *m = new QMimeData();
            m->setData("application/x-map-id", QByteArray(reinterpret_cast<char*>(&mapid), 4));
            d->setMimeData(m);
            d->exec(Qt::DropAction::CopyAction);
        }
    }
    return false;
}

void slice_view::refresh()
{
    int curid = lv->currentIndex().data().toInt();
    m->clear();
    id_row.clear();
    auto ids = l->map_ids();
    for (auto id : ids)
    {
        map_t map = l->get_map(id);
        QPixmap pm = pixmap_of_map_data(map.map_data);
        QString text = QString("(%1)").arg(id);
        if (map.locked) text.append(" [L]");
        if (map.custom_name.length()) text = QString::fromStdString(map.custom_name) + " " + text;
        QStandardItem *itm = new QStandardItem(QIcon(pm), text);
        itm->setData(QVariant(id));
        id_row[id] = m->rowCount();
        m->appendRow(itm);
        if (id == curid)
            lv->setCurrentIndex(mf->mapFromSource(itm->index()));
    }
}

void slice_view::focus_map_id(int32_t id)
{
    if (id_row.find(id) == id_row.end()) return;
    lv->setCurrentIndex(mf->mapFromSource(m->index(id_row[id], 0)));
}

void slice_view::highlight_ungrouped_maps() {
    std::unordered_set<int> s = l->ungrouped_maps();
    for (int i = 0; i < m->rowCount(); ++i) {
        QStandardItem *itm = m->item(i);
        if (s.find(itm->data().toInt()) != s.end())
            itm->setBackground(mapman_main_window::mw->get_unused_color());
    }
}

void slice_view::remove_highlight(int id) {
    for (int i = 0; i < m->rowCount(); ++i) {
        QStandardItem *itm = m->item(i);
        if (itm->data().toInt() == id)
            itm->setBackground(Qt::GlobalColor::transparent);
    }
}
