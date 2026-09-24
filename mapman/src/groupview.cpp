#include "groupview.hpp" 
#include "library.hpp"
#include "painter.hpp"
#include "mainwindow.hpp"
#include "comms.hpp"
#include "src/utils.hpp"

#include <algorithm>

#include <QCheckBox>
#include <QTableView>
#include <QStandardItemModel>
#include <QSortFilterProxyModel>
#include <QPushButton>
#include <QLineEdit>
#include <QSpinBox>
#include <QLabel>
#include <QComboBox>
#include <QSplitter>
#include <QHBoxLayout>
#include <QVBoxLayout>
#include <QGraphicsView>
#include <QSpacerItem>
#include <QHeaderView>
#include <QMessageBox>
#include <QFileDialog>

group_view::group_view() : QMdiSubWindow()
{
    auto sp = new QSplitter(this);
    this->setWidget(sp);
    auto leftpane = new QWidget();
    auto l1 = new QVBoxLayout();
    leftpane->setLayout(l1);
    tv = new QTableView();
    m = new QStandardItemModel(this);
    mf = new QSortFilterProxyModel(this);
    mf->setSourceModel(m);
    mf->setFilterCaseSensitivity(Qt::CaseSensitivity::CaseInsensitive);
    mf->setFilterKeyColumn(0);
    tv->setModel(mf);
    tv->setColumnHidden(3, true);
    tv->setSelectionMode(QAbstractItemView::SelectionMode::SingleSelection);
    tv->setSelectionBehavior(QAbstractItemView::SelectionBehavior::SelectRows);
    connect(tv->selectionModel(), &QItemSelectionModel::currentRowChanged,
        [this](const QModelIndex &curidx, const QModelIndex &oldidx) {
            if (oldidx.isValid() && curidx.row() == oldidx.row()) return;
            bool dirty = oldidx.isValid() && this->dirty;
            if (oldidx.isValid())
            {
                int64_t oldgid = mf->data(oldidx.siblingAtColumn(3), Qt::ItemDataRole::DisplayRole).toLongLong();
                auto oldgroup = l->get_group(oldgid);
                dirty |= tetitle->text() != QString::fromStdString(oldgroup.title);
                dirty |= teauthor->text() != QString::fromStdString(oldgroup.author);
                dirty |= sbh->value() != oldgroup.hc;
                dirty |= sbv->value() != oldgroup.vc;
            }
            if (dirty)
            {
                if (QMessageBox::question(this, "Unsaved changes", "Changes to map art not saved! Discard and switch to another map art?") != QMessageBox::StandardButton::Yes)
                {
                    int row = oldidx.row();
                    QMetaObject::invokeMethod(this, [this, row]() {
                        QSignalBlocker b(tv->selectionModel());
                        tv->selectRow(row);
                    }, Qt::ConnectionType::QueuedConnection);
                    return;
                }
            }
            this->update_fields();
            Q_EMIT this->highlight_changed();
    });
    l1->addWidget(tv);
    auto l2 = new QHBoxLayout();
    pbadd = new QPushButton("+");
    pbrem = new QPushButton("-");
    tefilter = new QLineEdit();
    tefilter->setPlaceholderText("Filter");
    cbfiltauthor = new QCheckBox("Search authors");
    connect(pbadd, &QPushButton::pressed, this, &group_view::add_group);
    connect(pbrem, &QPushButton::pressed, this, &group_view::rem_group);
    l2->addWidget(tefilter);
    l2->addWidget(cbfiltauthor);
    l2->addWidget(pbadd);
    l2->addWidget(pbrem);
    l1->addLayout(l2);
    sp->addWidget(leftpane);

    auto rightpane = new QWidget();
    auto l3 = new QVBoxLayout();
    rightpane->setLayout(l3);
    p = new map_painter(true, this);
    l3->addWidget(p->view());
    connect(p, &map_painter::map_id_changed, this, &group_view::painter_drop);
    tetitle = new QLineEdit();
    teauthor = new QLineEdit();
    tetitle->setPlaceholderText("Title");
    teauthor->setPlaceholderText("Author(s)");
    l3->addWidget(tetitle);
    l3->addWidget(teauthor);
    auto l4 = new QHBoxLayout();
    sbv = new QSpinBox();
    sbh = new QSpinBox();
    sbv->setValue(1);
    sbv->setMinimum(1);
    sbv->setMaximum(100);
    connect(sbv, QOverload<int>::of(&QSpinBox::valueChanged), this, &group_view::reset_dim);
    sbh->setValue(1);
    sbh->setMinimum(1);
    sbh->setMaximum(100);
    connect(sbh, QOverload<int>::of(&QSpinBox::valueChanged), this, &group_view::reset_dim);
    l4->addWidget(sbh);
    l4->addWidget(new QLabel("x"));
    l4->addWidget(sbv);
    l4->addItem(new QSpacerItem(0, 0, QSizePolicy::Expanding));
    l4->addWidget(new QLabel("Zoom"));
    cbscale = new QComboBox();
    cbscale->addItems({QStringLiteral("100%"), QStringLiteral("200%"), QStringLiteral("300%"), QStringLiteral("400%")});
    connect(cbscale, &QComboBox::currentIndexChanged, this, [this](int idx) {
        p->set_scaling(idx + 1);
    });
    l4->addWidget(cbscale);
    pbapply = new QPushButton("Save");
    connect(pbapply, &QPushButton::pressed, this, &group_view::update_library);

    l4->addWidget(pbapply);
    l3->addLayout(l4);
    sp->addWidget(rightpane);

    sp->setStretchFactor(0, 1);
    sp->setStretchFactor(1, 3);
    sp->setCollapsible(0, false);
    sp->setCollapsible(1, false);
    l = nullptr;
    dirty = false;
    connect(tefilter, &QLineEdit::textChanged, mf, &QSortFilterProxyModel::setFilterFixedString);
    connect(cbfiltauthor, &QCheckBox::checkStateChanged, [this](Qt::CheckState cs) { mf->setFilterKeyColumn(cs == Qt::CheckState::Checked ? 1 : 0); });
    this->setWindowTitle("Map art listings");
    this->setAttribute(Qt::WA_DeleteOnClose, false);
}

group_view::~group_view()
{
    delete p;
}

void group_view::set_library(map_library *lib)
{
    l = lib;
    p->set_map_library(l);
    refresh_list();
}

void group_view::export_group_image(QString fn, int64_t gid)
{
    if (!l || !l->has_group(gid))
        return;
    QImage img = image_of_map_group(l, l->get_group(gid), 1.);
    img.save(fn, "PNG");
}

void group_view::add_group()
{
    map_group_t g {
        std::string(),
        std::string(),
        1, 1,
        {0},
        {false}
    };
    l->new_group(g);
    refresh_list();
    tv->setCurrentIndex(mf->mapFromSource(m->index(m->rowCount() - 1, 0)));
}

void group_view::rem_group()
{
    if (!tv->currentIndex().isValid())
        return;
    int64_t curgid = mf->data(tv->currentIndex().siblingAtColumn(3), Qt::ItemDataRole::DisplayRole).toLongLong();
    l->remove_group(curgid);
    refresh_list();
}

void group_view::update_fields()
{
    if (!tv->currentIndex().isValid())
        return;
    int64_t curgid = mf->data(tv->currentIndex().siblingAtColumn(3), Qt::ItemDataRole::DisplayRole).toLongLong();
    current_group = l->get_group(curgid);
    auto &g = current_group;
    tetitle->setText(QString::fromStdString(g.title));
    teauthor->setText(QString::fromStdString(g.author));
    QSignalBlocker bh(sbh);
    QSignalBlocker bv(sbv);
    sbh->setValue(g.hc);
    sbv->setValue(g.vc);
    update_map_view();
    dirty = false;
}

void group_view::painter_drop(int pos, bool populated, int id)
{
    if (!tv->currentIndex().isValid())
        return;
    auto &g = current_group;
    g.populated[pos] = populated;
    g.ids[pos] = id;
    if (tetitle->text().isEmpty()) {
        map_t m = l->get_map(id);
        if (!m.custom_name.empty()) {
            tetitle->setText(QString::fromStdString(m.custom_name));
            current_group.title = m.custom_name;
        }
    }
    dirty = true;
}

void group_view::update_library()
{
    if (!tv->currentIndex().isValid())
        return;
    int64_t curgid = mf->data(tv->currentIndex().siblingAtColumn(3), Qt::ItemDataRole::DisplayRole).toLongLong();
    auto &g = current_group;
    g.title = tetitle->text().toStdString();
    g.author = teauthor->text().toStdString();
    g.hc = sbh->value();
    g.vc = sbv->value();
    l->set_group(curgid, g);
    refresh_list();
    mapman_main_window::mw->update_statusbar();
    dirty = false;
}

void group_view::refresh_list()
{
    int64_t curgid = -1;
    if (tv->currentIndex().isValid() && tv->currentIndex().siblingAtColumn(3).isValid())
    {
        auto idx = tv->currentIndex().siblingAtColumn(3);
        curgid = mf->data(idx, Qt::ItemDataRole::DisplayRole).toLongLong();
    }
    m->clear();
    m->setHorizontalHeaderLabels({"Title", "Author(s)", "Dimension", "id"});
    tv->setColumnHidden(3, true);
    auto gids = l->groups();
    auto idx = QModelIndex();
    for (auto gid : gids)
    {
        map_group_t g = l->get_group(gid);
        QStandardItem *t = new QStandardItem(QString::fromStdString(g.title));
        QStandardItem *a = new QStandardItem(QString::fromStdString(g.author));
        QStandardItem *d = new QStandardItem(QString("%1x%2").arg(g.hc).arg(g.vc));
        QStandardItem *i = new QStandardItem();
        i->setData(QVariant((qlonglong)gid), Qt::ItemDataRole::DisplayRole);
        m->appendRow({t, a, d, i});
        if (gid == curgid)
            idx = mf->mapFromSource(t->index());
    }
    if (idx.isValid())
    {
        tv->setCurrentIndex(idx);
        tv->scrollTo(idx);
    }
    tv->resizeColumnsToContents();
}

void group_view::reset_dim()
{
    p->set_dimension(sbh->value(), sbv->value());
    if (!tv->currentIndex().isValid())
        return;
    auto &g = current_group;
    g.hc = sbh->value();
    g.vc = sbv->value();
    g.ids.resize(g.hc * g.vc);
    g.populated.resize(g.hc * g.vc);
    std::fill(g.populated.begin(), g.populated.end(), false);
    dirty = true;
}

void group_view::update_map_view()
{
    auto h = std::vector<int32_t>();
    auto &g = current_group;
    p->set_dimension(g.hc, g.vc);
    for (int i = 0; i < g.hc * g.vc; ++i) {
        p->set_map_id(i, g.populated[i], g.ids[i]);
        if (g.populated[i])
            h.push_back(g.ids[i]);
    }
}

std::vector<std::pair<uint32_t, std::vector<int32_t>>> group_view::get_highlights() {
    auto mw = mapman_main_window::mw;
    std::vector<std::pair<uint32_t, std::vector<int32_t>>> ret;
    int64_t curgid = mf->data(tv->currentIndex().siblingAtColumn(3), Qt::ItemDataRole::DisplayRole).toLongLong();
    if (~curgid) {
        auto current_group = l->get_group(curgid);
        auto g = l->get_group(curgid);
        std::vector<int32_t> h;
        for (int i = 0; i < g.hc * g.vc; ++i) {
            if (g.populated[i])
                h.push_back(g.ids[i]);
        }
        ret.push_back(std::make_pair(mw->get_default_highlight_color().rgb(), h));
    }
    return ret;
}

void group_view::export_current_group()
{
    int64_t curgid = -1;
    if (tv->currentIndex().isValid() && tv->currentIndex().siblingAtColumn(3).isValid())
    {
        auto idx = tv->currentIndex().siblingAtColumn(3);
        curgid = mf->data(idx, Qt::ItemDataRole::DisplayRole).toLongLong();
    }
    if (!~curgid)
        return;
    QString fn = QFileDialog::getSaveFileName(this, "Export Image", QString(), "Images (*.png)");
    if (fn.isEmpty()) return;
    export_group_image(fn, curgid);
}

void group_view::export_all_groups()
{
    QString fp = QFileDialog::getExistingDirectory(this, "Select Output Directory");
    if (fp.isEmpty()) return;
    auto gids = l->groups();
    for (auto gid : gids)
    {
        auto g = l->get_group(gid);
        auto fn = QString("%1/%2_%3.png").arg(fp).arg(gid).arg(QString::fromStdString(g.title));
        export_group_image(fn, gid);
    }
}
