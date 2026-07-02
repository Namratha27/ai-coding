from __future__ import annotations

import json

import pytest
from fastapi.testclient import TestClient

from app.main import create_app


def make_client() -> TestClient:
    return TestClient(create_app(), raise_server_exceptions=False)


def test_health() -> None:
    client = make_client()
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_create_order_returns_201_placed() -> None:
    client = make_client()
    response = client.post("/orders", json={"lines": [{"sku": "SKU-1", "quantity": 1, "unitPrice": 10.0}]})
    body = response.json()
    assert response.status_code == 201
    assert body["id"]
    assert body["status"] == "Placed"


def test_total_uses_quantity() -> None:
    client = make_client()
    response = client.post(
        "/orders",
        json={
            "lines": [
                {"sku": "SKU-1", "quantity": 2, "unitPrice": 10.0},
                {"sku": "SKU-2", "quantity": 3, "unitPrice": 5.0},
            ]
        },
    )
    assert response.status_code == 201
    assert response.json()["total"] == 35.0


def test_valid_order_reserves_inventory() -> None:
    client = make_client()
    response = client.post("/orders", json={"lines": [{"sku": "SKU-1", "quantity": 3, "unitPrice": 2.0}]})
    assert response.status_code == 201

    inventory = client.get("/inventory/SKU-1")
    assert inventory.status_code == 200
    assert inventory.json()["available"] == 7
    assert inventory.json()["reserved"] == 3


def test_oversell_rejected() -> None:
    client = make_client()
    response = client.post("/orders", json={"lines": [{"sku": "SKU-1", "quantity": 99, "unitPrice": 2.0}]})
    assert response.status_code == 409

    inventory = client.get("/inventory/SKU-1")
    assert inventory.status_code == 200
    assert inventory.json()["available"] == 10


def test_unknown_sku_rejected() -> None:
    client = make_client()
    response = client.post("/orders", json={"lines": [{"sku": "SKU-X", "quantity": 1, "unitPrice": 2.0}]})
    assert response.status_code == 409


def test_idempotency_returns_same_order() -> None:
    client = make_client()
    payload = {"lines": [{"sku": "SKU-1", "quantity": 2, "unitPrice": 10.0}]}

    first = client.post("/orders", json=payload, headers={"Idempotency-Key": "k1"})
    second = client.post("/orders", json=payload, headers={"Idempotency-Key": "k1"})

    assert first.status_code == 201
    assert second.status_code in (200, 201)
    assert first.json()["id"] == second.json()["id"]

    inventory = client.get("/inventory/SKU-1")
    assert inventory.json()["available"] == 8
    assert inventory.json()["reserved"] == 2


def test_get_missing_order_404() -> None:
    client = make_client()
    response = client.get("/orders/missing-order")
    assert response.status_code == 404


def test_cancel_restores_inventory() -> None:
    client = make_client()
    placed = client.post("/orders", json={"lines": [{"sku": "SKU-1", "quantity": 4, "unitPrice": 2.0}]})
    assert placed.status_code == 201
    assert client.get("/inventory/SKU-1").json()["available"] == 6

    cancelled = client.post(f"/orders/{placed.json()['id']}/cancel")
    assert cancelled.status_code == 200
    assert cancelled.json()["status"] == "Cancelled"

    inventory = client.get("/inventory/SKU-1")
    assert inventory.status_code == 200
    assert inventory.json()["available"] == 10
    assert inventory.json()["reserved"] == 0


def test_cancel_is_idempotent() -> None:
    client = make_client()
    placed = client.post("/orders", json={"lines": [{"sku": "SKU-1", "quantity": 4, "unitPrice": 2.0}]})
    assert placed.status_code == 201
    assert client.get("/inventory/SKU-1").json()["available"] == 6

    first = client.post(f"/orders/{placed.json()['id']}/cancel")
    second = client.post(f"/orders/{placed.json()['id']}/cancel")
    assert first.status_code == 200
    assert second.status_code == 200

    inventory = client.get("/inventory/SKU-1")
    assert inventory.json()["available"] == 10
    assert inventory.json()["reserved"] == 0


@pytest.mark.bonus
def test_drain_events_fulfills_order() -> None:
    client = make_client()
    placed = client.post("/orders", json={"lines": [{"sku": "SKU-1", "quantity": 1, "unitPrice": 10.0}]})

    drained = client.post("/internal/drain-events")
    assert drained.status_code == 200
    assert drained.json() == {"processed": 1}

    order = client.get(f"/orders/{placed.json()['id']}")
    assert order.status_code == 200
    assert order.json()["status"] == "Fulfilled"


@pytest.mark.bonus
def test_drain_events_publishes_orderfulfilled() -> None:
    client = make_client()
    placed = client.post("/orders", json={"lines": [{"sku": "SKU-1", "quantity": 1, "unitPrice": 10.0}]})

    drained = client.post("/internal/drain-events")
    assert drained.status_code == 200

    service = client.app.state.order_service
    messages = [json.loads(message) for message in service.queue.messages]
    assert {"type": "OrderFulfilled", "orderId": placed.json()["id"]} in messages
