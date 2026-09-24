#ifndef DIFFVIEW_HPP
#define DIFFVIEW_HPP

#include <QWidget>

#include <vector>

class QTextEdit;

class diff_view : public QWidget
{
    Q_OBJECT
public:
    diff_view(QWidget* par = nullptr);
    void set_results(const std::vector<int> &a_b, const std::vector<int> &b_a);
private:
    QTextEdit *tea_b;
    QTextEdit *teb_a;
};

#endif
