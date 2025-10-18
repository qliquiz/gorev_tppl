import pytest
from plib import Point

@pytest.fixture
def points():
    return Point(0, 0), Point(2, 2)

class TestPoint:
    def test_creation(self):
        p = Point(1, 2)
        assert p.x == 1 and p.y == 2

        with pytest.raises(TypeError):
            Point(1.5, 1.5)

    def test_add(self, points):
        p1, p2 = points
        assert p2 + p1 == Point(2, 2)

    def test_sub(self, points):
        p1, p2 = points
        assert p2 - p1 == Point(2, 2)

    def test_iadd(self, points):
        p1, p2 = points
        original_p1 = p1
        p1 += p2
        assert p1 == Point(2, 2)
        assert p1 is original_p1

    def test_isub(self, points):
        p1, p2 = points
        original_p1 = p1
        p1 -= p2
        assert p1 == -Point(2, 2)
        assert p1 is original_p1

    def test_eq(self):
        with pytest.raises(NotImplementedError):
            Point(0, 0) != int

    def test_distance_to(self):
        p1 = Point(0, 0)
        p2 = Point(2, 0)
        assert p1.to(p2) == 2

    def test_str(self):
        assert str(Point(1, 0)) == 'Point(1, 0)'

    def test_repr(self):
        assert repr(Point(1, 0)) == 'Point(1, 0)'

    def test_is_center(self):
        assert Point(0, 0).is_center()

    def test_to_json(self):
        p = Point(0, 0)
        assert p.to_json() == '{"x": 0, "y": 0}'

    def test_from_json(self):
        p = Point(0, 0)
        assert p.from_json(p.to_json()) == p

    @pytest.mark.parametrize(
            'p1, p2, distance',
            [(Point(0, 0), Point(0, 10), 10),
            (Point(0, 0), Point(10, 0), 10),
            (Point(0, 0), Point(1, 1), 1.414)],
    )
    def test_distance_all_axis(self, p1, p2, distance):
        assert p1.to(p2) == pytest.approx(distance, 0.001)
