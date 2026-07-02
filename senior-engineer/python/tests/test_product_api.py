import pytest
from fastapi.testclient import TestClient

from app.main import create_app


def new_client() -> TestClient:
    return TestClient(create_app(), raise_server_exceptions=False)


def test_health_returns_ok():
    client = new_client()
    res = client.get("/health")
    assert res.status_code == 200
    assert res.json() == {"status": "ok"}


def test_list_respects_page_size():
    client = new_client()
    res = client.get("/products?category=demo&page=1&pageSize=5")
    assert res.status_code == 200
    assert len(res.json()) == 5


def test_list_returns_remainder_on_last_page():
    client = new_client()
    res = client.get("/products?category=demo&page=3&pageSize=5")
    assert res.status_code == 200
    assert len(res.json()) == 2


def test_create_assigns_non_empty_id_and_is_retrievable():
    client = new_client()
    res = client.post(
        "/products",
        json={"name": "Keyboard", "category": "peripherals", "price": 49.99, "stock": 7},
    )
    assert res.status_code == 201
    created = res.json()
    assert created["id"].strip()

    get = client.get(f"/products/{created['id']}")
    assert get.status_code == 200
    assert get.json()["name"] == "Keyboard"


def test_create_rejects_zero_price():
    client = new_client()
    res = client.post(
        "/products",
        json={"name": "Freebie", "category": "demo", "price": 0, "stock": 1},
    )
    assert res.status_code == 400


def test_create_rejects_negative_price():
    client = new_client()
    res = client.post(
        "/products",
        json={"name": "Negative", "category": "demo", "price": -5, "stock": 1},
    )
    assert res.status_code == 400


def test_get_missing_returns_404():
    client = new_client()
    res = client.get("/products/does-not-exist")
    assert res.status_code == 404


def test_delete_then_get_returns_404():
    client = new_client()
    delete = client.delete("/products/demo-1")
    assert delete.status_code == 204

    get = client.get("/products/demo-1")
    assert get.status_code == 404


def test_list_filters_by_category():
    client = new_client()
    res = client.get("/products?category=nonexistent&page=1&pageSize=10")
    assert res.status_code == 200
    assert res.json() == []


@pytest.mark.bonus
def test_bonus_upload_image_sets_url_and_publishes_event():
    client = new_client()
    upload = client.post(
        "/products/demo-2/image",
        content=bytes([1, 2, 3, 4]),
        headers={"content-type": "image/png"},
    )
    assert upload.status_code == 200

    product = upload.json()
    assert product["image_url"]

    get = client.get("/products/demo-2")
    assert get.status_code == 200
    assert get.json()["image_url"] == product["image_url"]


@pytest.mark.bonus
def test_bonus_upload_image_for_missing_product_returns_404():
    client = new_client()
    upload = client.post(
        "/products/missing/image",
        content=bytes([9]),
        headers={"content-type": "image/png"},
    )
    assert upload.status_code == 404
