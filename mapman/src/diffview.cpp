#include "diffview.hpp"

#include <QTextEdit>
#include <QLabel>
#include <QHBoxLayout>
#include <QVBoxLayout>

diff_view::diff_view(QWidget *par) : QWidget(par)
{
    this->setWindowTitle("Map tally comparison results");
    tea_b = new QTextEdit();
    teb_a = new QTextEdit();
    tea_b->setReadOnly(true);
    teb_a->setReadOnly(true);
    auto hl = new QHBoxLayout();
    auto lvl = new QVBoxLayout();
    lvl->addWidget(new QLabel("Maps present in library but not in tally"));
    lvl->addWidget(tea_b);
    auto rvl = new QVBoxLayout();
    rvl->addWidget(new QLabel("Maps present in tally but not in library"));
    rvl->addWidget(teb_a);
    hl->addLayout(lvl);
    hl->addLayout(rvl);
    this->setLayout(hl);
}

void diff_view::set_results(const std::vector<int> &a_b, const std::vector<int> &b_a)
{
    tea_b->clear();
    teb_a->clear();
    QString sa_b, sb_a;
    for (auto &i : a_b) sa_b.append(QString("%1\n").arg(i));
    for (auto &i : b_a) sb_a.append(QString("%1\n").arg(i));
    tea_b->setText(sa_b);
    teb_a->setText(sb_a);
}
