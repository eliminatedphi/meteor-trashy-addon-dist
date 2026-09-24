#include "mainwindow.hpp"
#include <QApplication>

int main(int argc, char **argv)
{
    QApplication a(argc, argv);

    mapman_main_window mw;
    mw.show();

    a.exec();
    return 0;
}
