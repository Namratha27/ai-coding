from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import datetime, timezone
from uuid import uuid4

from app.models import CreateOrderRequest, InventoryItem, Order
from app.simulated.cosmos import SimulatedCosmosContainer
from app.simulated.queue import SimulatedQueue


@dataclass
class ServiceResult:
    status_code: int
    body: dict


class FulfillmentProcessor:
    def __init__(self, orders: SimulatedCosmosContainer, queue: SimulatedQueue):
        self.orders = orders
        self.queue = queue
        self._processed_order_ids: set[str] = set()

    def drain(self) -> int:
        raise NotImplementedError("Bonus saga processor is intentionally not implemented")


class OrderService:
    """
    NOTE FOR CANDIDATE: This service intentionally contains distributed-systems
    correctness defects. The tests describe the desired behavior. Fix the BUG
    comments without replacing the simulated dependencies with real Azure services.
    """

    def __init__(self):
        self.orders = SimulatedCosmosContainer("orders")
        self.inventory = SimulatedCosmosContainer("inventory")
        self.queue = SimulatedQueue("order-events")
        self.fulfillment_processor = FulfillmentProcessor(self.orders, self.queue)
        self._seed_inventory()

    def _seed_inventory(self) -> None:
        for item in (
            InventoryItem(sku="SKU-1", available=10, reserved=0),
            InventoryItem(sku="SKU-2", available=5, reserved=0),
            InventoryItem(sku="SKU-3", available=0, reserved=0),
        ):
            self.inventory.create_item(item.model_dump())

    def create_order(self, request: CreateOrderRequest, idempotency_key: str | None) -> ServiceResult:
        if not request.lines:
            return ServiceResult(400, {"detail": "at least one order line is required"})
        if any(line.quantity <= 0 for line in request.lines):
            return ServiceResult(400, {"detail": "line quantity must be positive"})

        # BUG #1: Idempotency-Key is not enforced; repeated requests create new orders.

        # BUG #2: Inventory availability and SKU existence are not checked, so oversell
        # and unknown SKUs are accepted. Inventory counts are also not updated.

        # BUG #4: Total ignores quantity and only sums unit prices.
        total = sum(line.unitPrice for line in request.lines)
        order = Order(
            id=str(uuid4()),
            idempotencyKey=idempotency_key,
            status="Placed",
            lines=request.lines,
            total=total,
            createdAt=datetime.now(timezone.utc).isoformat(),
        )
        stored_order = self.orders.create_item(order.model_dump())
        self.queue.send_message(json.dumps({"type": "OrderPlaced", "orderId": stored_order["id"]}))
        return ServiceResult(201, stored_order)

    def get_order(self, order_id: str) -> ServiceResult:
        order = self.orders.read_item(order_id)
        if order is None:
            return ServiceResult(404, {"detail": "order not found"})
        return ServiceResult(200, order)

    def cancel_order(self, order_id: str) -> ServiceResult:
        order = self.orders.read_item(order_id)
        if order is None:
            return ServiceResult(404, {"detail": "order not found"})
        if order["status"] == "Cancelled":
            return ServiceResult(200, order)

        # BUG #3: Cancellation does not restore reserved inventory.
        order["status"] = "Cancelled"
        stored_order = self.orders.replace_item(order)
        self.queue.send_message(json.dumps({"type": "OrderCancelled", "orderId": order_id}))
        return ServiceResult(200, stored_order)

    def get_inventory(self, sku: str) -> ServiceResult:
        item = self.inventory.read_item(sku)
        if item is None:
            return ServiceResult(404, {"detail": "inventory item not found"})
        return ServiceResult(200, item)

    def drain_events(self) -> ServiceResult:
        processed = self.fulfillment_processor.drain()
        return ServiceResult(200, {"processed": processed})
