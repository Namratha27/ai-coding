from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, Response

from app.models import CreateProductRequest, Product, UpdateProductRequest
from app.services.product_service import ServiceResult, ProductService
from app.simulated.blob import SimulatedBlobContainer
from app.simulated.cosmos import SimulatedCosmosContainer
from app.simulated.queue import SimulatedQueue


def _to_http(result: ServiceResult):
    if result.status == 200:
        return result.value
    if result.status == 201:
        headers = {}
        if isinstance(result.value, Product):
            headers["Location"] = f"/products/{result.value.id}"
        return JSONResponse(status_code=201, content=result.value.model_dump(), headers=headers)
    if result.status == 204:
        return Response(status_code=204)
    if result.status in (400, 404):
        return JSONResponse(status_code=result.status, content={"error": result.error})
    return Response(status_code=result.status)


def create_app() -> FastAPI:
    products = SimulatedCosmosContainer[Product]()
    images = SimulatedBlobContainer()
    events = SimulatedQueue()
    service = ProductService(products, images, events)

    app = FastAPI(title="Product Catalog Service")
    app.state.products = products
    app.state.images = images
    app.state.events = events
    app.state.product_service = service

    @app.get("/health")
    def health():
        return {"status": "ok"}

    @app.get("/products")
    def list_products(category: str | None = None, page: int = 1, pageSize: int = 10):
        return _to_http(service.list(category, page, pageSize))

    @app.get("/products/{id}")
    def get_product(id: str):
        return _to_http(service.get(id))

    @app.post("/products")
    def create_product(request: CreateProductRequest):
        return _to_http(service.create(request))

    @app.put("/products/{id}")
    def update_product(id: str, request: UpdateProductRequest):
        return _to_http(service.update(id, request))

    @app.delete("/products/{id}")
    def delete_product(id: str):
        return _to_http(service.delete(id))

    @app.post("/products/{id}/image")
    async def attach_image(id: str, request: Request):
        content = await request.body()
        content_type = request.headers.get("content-type", "application/octet-stream")
        return _to_http(service.attach_image(id, content, content_type))

    return app


app = create_app()
