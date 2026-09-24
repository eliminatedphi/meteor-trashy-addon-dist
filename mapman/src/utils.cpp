#include "utils.hpp"
#include "library.hpp"
#include "mapdump.hpp"

#include <QColor>
#include <QImage>
#include <QPainter>

rgb_t modify_color(rgb_t c, uint8_t variant)
{
    int m;
    switch (variant)
    {
        case 0: m = 180; break;
        case 1: m = 200; break;
        case 2: m = 255; break;
        case 3: m = 135; break;
        default: m = 0; break;
    }
    return {uint8_t(m * c.r / 255),
            uint8_t(m * c.g / 255),
            uint8_t(m * c.b / 255)};
}

QColor rgb2qcolor(rgb_t c) {return QColor(c.r, c.g, c.b);}

QPixmap pixmap_of_map_data(const map_data_t &map_data, double scaling)
{
    QImage ret(128, 128, QImage::Format_ARGB32);
    ret.setDevicePixelRatio(scaling);
    for (size_t i = 0; i < 128; ++i)
        for (size_t j = 0; j < 128; ++j)
        {
            uint8_t d = map_data[j * 128 + i];
            uint8_t b = d >> 2;
            uint8_t v = d & 3;
            QColor color = Qt::GlobalColor::transparent;
            if (b > 0 && b < 62)
                color = rgb2qcolor(modify_color(MAP_COLORS[b], v));
            ret.setPixelColor(i, j, color);
        }
    return QPixmap::fromImage(ret);
}

QImage image_of_map_group(const map_library *library, const map_group_t &group, double scaling)
{
    QImage ret(group.hc * 128, group.vc * 128, QImage::Format_ARGB32);
    ret.fill(0);
    ret.setDevicePixelRatio(1.);
    {
        QPainter p(&ret);
        for (int i = 0 ; i < group.vc; ++i)
            for (int j = 0 ; j < group.hc; ++j)
                if (group.populated[i * group.hc + j])
                    p.drawPixmap(128 * j, 128 * i, pixmap_of_map_data(library->get_map(group.ids[i * group.hc + j]).map_data, 1.));
    }
    ret.setDevicePixelRatio(scaling);
    return ret;
}
