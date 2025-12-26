from unittest import TestCase, main
from copy import deepcopy


def is_singleton(factory):
    o1 = factory()
    o2 = factory()
    return o1 is o2


class Evaluate(TestCase):
    def test_exercise(self):
        obj = [1, 2, 3]
        self.assertTrue(is_singleton(lambda: obj))
        self.assertFalse(is_singleton(lambda: deepcopy(obj)))


if __name__ == "__main__":
    main()