from collections.abc import Callable
from typing import TypeVar

T = TypeVar("T")


class SimulatedCosmosContainer[T]:
    def __init__(self) -> None:
        self._store: dict[str, T] = {}

    def create_item(self, item: T) -> T:
        self._store[getattr(item, "id")] = item
        return item

    def read_item(self, id: str) -> T | None:
        return self._store.get(id)

    def query(self, predicate: Callable[[T], bool]) -> list[T]:
        return [item for item in self._store.values() if predicate(item)]

    def replace_item(self, item: T) -> T:
        self._store[getattr(item, "id")] = item
        return item

    def delete_item(self, id: str) -> bool:
        return self._store.pop(id, None) is not None
