from __future__ import annotations

from fastapi import FastAPI, Header, Response

from app.models import CreateOrderRequest
from app.services.order_service import OrderService


def create_app() -> FastAPI:
    app = FastAPI(title="Order Processing Service")
    service = OrderService()
    app.state.order_service = service

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "ok"}

    @app.post("/orders")
    def create_order(
        request: CreateOrderRequest,
        response: Response,
        idempotency_key: str | None = Header(default=None, alias="Idempotency-Key"),
    ) -> dict:
        result = service.create_order(request, idempotency_key)
        response.status_code = result.status_code
        return result.body

    @app.get("/orders/{order_id}")
    def get_order(order_id: str, response: Response) -> dict:
        result = service.get_order(order_id)
        response.status_code = result.status_code
        return result.body

    @app.post("/orders/{order_id}/cancel")
    def cancel_order(order_id: str, response: Response) -> dict:
        result = service.cancel_order(order_id)
        response.status_code = result.status_code
        return result.body

    @app.get("/inventory/{sku}")
    def get_inventory(sku: str, response: Response) -> dict:
        result = service.get_inventory(sku)
        response.status_code = result.status_code
        return result.body

    @app.post("/internal/drain-events")
    def drain_events(response: Response) -> dict:
        result = service.drain_events()
        response.status_code = result.status_code
        return result.body

    return app


app = create_app()
