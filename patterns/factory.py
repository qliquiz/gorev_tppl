from unittest import TestCase, main


class Person:
    def __init__(self, id, name):
        self.id = id
        self.name = name


class PersonFactory:
    def create_person(self, name):
        if not hasattr(self, '_next_id'):
            self._next_id = 0
        person = Person(self._next_id, name)
        self._next_id += 1
        return person


class Evaluate(TestCase):
    def test_exercise(self):
        pf = PersonFactory()

        p1 = pf.create_person('Artem')
        self.assertEqual(p1.name, 'Artem')
        self.assertEqual(p1.id, 0)

        p2 = pf.create_person('Alina')
        self.assertEqual(p2.id, 1)


if __name__ == "__main__":
    main()
