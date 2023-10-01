package ru.spasitel.factorioautoplanner.simple

enum class InsertersPlaces(var iX: Int, var iY: Int, var cX: Int, var cY: Int, var direction: Int) {
    n1(0, -1, 0, -2, 0),
    n2(1, -1, 1, -2, 0),
    n3(2, -1, 2, -2, 0),
    e1(3, 0, 4, 0, 2),
    e2(3, 1, 4, 1, 2),
    e3(3, 2, 4, 2, 2),
    s1(0, 3, 0, 4, 4),
    s2(1, 3, 1, 4, 4),
    s3(2, 3, 2, 4, 4),
    w1(-1, 0, -2, 0, 6),
    w2(-1, 1, -2, 1, 6),
    w3(-1, 2, -2, 2, 6);
}