from __future__ import annotations

from copy import deepcopy
from typing import Any, Callable


class SimulatedCosmosContainer:
    def __init__(self, name: str):
        self.name = name
        self._items: dict[str, dict[str, Any]] = {}

    def create_item(self, item: dict[str, Any]) -> dict[str, Any]:
        item_id = item.get("id") or item.get("sku")
        if not item_id:
            raise ValueError("item must contain 'id' or 'sku'")
        if item_id in self._items:
            raise ValueError(f"item already exists: {item_id}")
        self._items[item_id] = deepcopy(item)
        return deepcopy(self._items[item_id])

    def read_item(self, id: str) -> dict[str, Any] | None:
        item = self._items.get(id)
        return deepcopy(item) if item is not None else None

    def query(self, predicate: Callable[[dict[str, Any]], bool]) -> list[dict[str, Any]]:
        return [deepcopy(item) for item in self._items.values() if predicate(deepcopy(item))]

    def replace_item(self, item: dict[str, Any]) -> dict[str, Any]:
        item_id = item.get("id") or item.get("sku")
        if not item_id:
            raise ValueError("item must contain 'id' or 'sku'")
        if item_id not in self._items:
            raise KeyError(item_id)
        self._items[item_id] = deepcopy(item)
        return deepcopy(self._items[item_id])

    def delete_item(self, id: str) -> None:
        self._items.pop(id, None)
