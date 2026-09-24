#include "painter.hpp"
#include "library.hpp"
#include "utils.hpp"
#include "mainwindow.hpp"
#include "sliceview.hpp"

#include <QGraphicsView>
#include <QGraphicsScene>
#include <QGraphicsSimpleTextItem>
#include <QGraphicsRectItem>
#include <QGraphicsPixmapItem>
#include <QGraphicsSceneDragDropEvent>
#include <QMimeData>
#include <QPixmapCache>

class drop_rect : public QGraphicsRectItem
{
public:
    drop_rect(int pos, double dim, map_painter *painter);
signals:
    void dropped(int pos, int id);
protected:
    void dragEnterEvent(QGraphicsSceneDragDropEvent *e);
    void dropEvent(QGraphicsSceneDragDropEvent *e);
private:
    int p;
    map_painter *pt;
};

drop_rect::drop_rect(int pos, double dim, map_painter *painter) :
    QGraphicsRectItem(QRectF(0, 0, dim, dim), nullptr),
    p(pos),
    pt(painter)
{
    setAcceptDrops(true);
    setPen(QPen(qobject_cast<QWidget*>(painter->parent())->palette().text(), 4));
}

void drop_rect::dragEnterEvent(QGraphicsSceneDragDropEvent *e)
{
    if (!pt->accept_drops()) {
        e->setAccepted(false);
        return;
    }
    auto ba = e->mimeData()->data("application/x-map-id");
    e->setAccepted(ba.length() >= 4);
}

void drop_rect::dropEvent(QGraphicsSceneDragDropEvent *e)
{
    auto ba = e->mimeData()->data("application/x-map-id");
    if (ba.length() >= 4 && pt->accept_drops())
    {
        e->setDropAction(Qt::DropAction::CopyAction);
        int id = *reinterpret_cast<int*>(ba.data());
        pt->set_map_id(p, true, id, true);
        mapman_main_window::mw->get_slice_view()->remove_highlight(id);
    }
    else e->setDropAction(Qt::DropAction::IgnoreAction);
}

map_painter::map_painter(bool accept_drops, QWidget *parent)
    : _accept_drops(accept_drops), l(nullptr), QObject(parent)
{
    s = new QGraphicsScene(0, 0, 0, 0);
    v = new QGraphicsView(s, parent);
    slice_dim = 128 / parent->devicePixelRatio();
    hc = vc = 0;
}

map_painter::~map_painter()
{
    delete s;
    delete v;
}

void map_painter::set_dimension(int h, int v)
{
    hc = h;
    vc = v;
    s->setSceneRect(0, 0, hc * slice_dim, vc * slice_dim);
    s->clear();
    slices.clear();
    slices.resize(hc * vc, nullptr);
    bgspr.clear();
    bgspr.resize(hc * vc, {nullptr, nullptr});
    for (int i = 0; i < vc; ++i)
        for (int j = 0; j < hc; ++j)
        {
            auto t = s->addSimpleText(QString::number(i * hc + j));
            t->setPos(j * slice_dim + slice_dim / 2 - t->boundingRect().width() / 2, i * slice_dim + slice_dim / 2 - t->boundingRect().height() / 2);
            t->setBrush(qobject_cast<QWidget*>(parent())->palette().text());
            auto r = new drop_rect(i * hc + j, slice_dim, this);
            s->addItem(r);
            bgspr[i * hc + j] = {t, r};
            r->setPos(j * slice_dim, i * slice_dim);
        }
}

void map_painter::set_map_id(int pos, bool populated, int id, bool user_input)
{
    if (!l || pos >= hc * vc || pos < 0) return;
    if (slices[pos])
    {
        s->removeItem(slices[pos]);
        delete slices[pos];
        slices[pos] = nullptr;
    }
    auto [t, r] = bgspr[pos];
    if (populated)
    {
        QPixmap pm;
        if (!QPixmapCache::find(QString("map_%1").arg(id), &pm))
        {
            pm = pixmap_of_map_data(l->get_map(id).map_data, this->v->devicePixelRatio());
            QPixmapCache::insert(QString("map_%1").arg(id), pm);
        }
        auto p = s->addPixmap(pm);
        auto m = l->get_map(id);
        p->setToolTip(QString("%1\nId #%2%3")
            .arg(m.custom_name.empty() ? QString("Map") : QString::fromStdString(m.custom_name))
            .arg(m.id)
            .arg(m.locked ? "\nLocked" : ""));
        int x = pos / hc;
        int y = pos % hc;
        p->setPos(y * slice_dim, x * slice_dim);
        slices[pos] = p;
        static_cast<QAbstractGraphicsShapeItem*>(t)->setPen(QColor(Qt::GlobalColor::transparent));
        static_cast<QAbstractGraphicsShapeItem*>(r)->setPen(QColor(Qt::GlobalColor::transparent));
    }
    if (user_input)
        emit map_id_changed(pos, populated, id);
}

void map_painter::set_scaling(double scale)
{
    v->setTransform(QTransform::fromScale(scale, scale));
}

void map_painter::set_map_library(map_library *lib) { l = lib; }
