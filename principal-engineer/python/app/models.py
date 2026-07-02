from __future__ import annotations

from pydantic import BaseModel, Field


class InventoryItem(BaseModel):
    sku: str
    available: int
    reserved: int


class OrderLine(BaseModel):
    sku: str
    quantity: int = Field(...)
    unitPrice: float


class Order(BaseModel):
    id: str
    idempotencyKey: str | None = None
    status: str
    lines: list[OrderLine]
    total: float
    createdAt: str


class CreateOrderRequest(BaseModel):
    lines: list[OrderLine]
