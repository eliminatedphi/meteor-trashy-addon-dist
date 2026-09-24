#ifndef MAPPAINTER_HPP
#define MAPPAINTER_HPP

#include <utility>
#include <vector>
#include <QObject>

class QGraphicsScene;
class QGraphicsView;
class QGraphicsItem;
class map_library;

class map_painter : public QObject
{
    Q_OBJECT
public:
    map_painter(bool accept_drops, QWidget *parent = nullptr);
    ~map_painter();
    void set_dimension(int h, int v);
    void set_map_library(map_library *lib);
    void set_map_id(int pos, bool populated, int id, bool user_input = false);
    void set_scaling(double scale);
    bool accept_drops() { return _accept_drops; }

    QGraphicsView* view() { return v; }
signals:
    void map_id_changed(int pos, bool populated, int id);
private:
    QGraphicsView *v;
    QGraphicsScene *s;
    map_library *l;
    int hc;
    int vc;
    double slice_dim;
    bool _accept_drops;
    std::vector<QGraphicsItem*> slices;
    std::vector<std::tuple<QGraphicsItem*, QGraphicsItem*>> bgspr;
};

#endif
