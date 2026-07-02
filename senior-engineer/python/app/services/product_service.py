from dataclasses import dataclass
from typing import Generic, TypeVar

from app.models import CreateProductRequest, Product, UpdateProductRequest
from app.simulated.blob import SimulatedBlobContainer
from app.simulated.cosmos import SimulatedCosmosContainer
from app.simulated.queue import SimulatedQueue

T = TypeVar("T")


@dataclass(frozen=True)
class ServiceResult(Generic[T]):
    status: int
    value: T | None = None
    error: str | None = None

    @classmethod
    def ok(cls, value: T | None) -> "ServiceResult[T]":
        return cls(status=200, value=value)

    @classmethod
    def created(cls, value: T) -> "ServiceResult[T]":
        return cls(status=201, value=value)

    @classmethod
    def no_content(cls) -> "ServiceResult[T]":
        return cls(status=204)

    @classmethod
    def bad_request(cls, error: str) -> "ServiceResult[T]":
        return cls(status=400, error=error)

    @classmethod
    def not_found(cls, error: str) -> "ServiceResult[T]":
        return cls(status=404, error=error)


class ProductService:
    """Business logic for the product catalog.

    NOTE FOR CANDIDATE: several behaviours in this class are incorrect. The test
    suite documents the expected behaviour. Use your AI agent to locate and fix
    the defects, then implement the bonus image-upload flow.
    """

    def __init__(
        self,
        products: SimulatedCosmosContainer[Product],
        images: SimulatedBlobContainer,
        events: SimulatedQueue,
    ) -> None:
        self._products = products
        self._images = images
        self._events = events
        self._seed()

    def _seed(self) -> None:
        for i in range(1, 13):
            self._products.create_item(
                Product(
                    id=f"demo-{i}",
                    name=f"Demo Product {i}",
                    category="demo",
                    price=10 + i,
                    stock=i,
                )
            )

    def list(self, category: str | None, page: int, page_size: int) -> ServiceResult[list[Product]]:
        all_items = self._products.query(
            lambda p: category is None or p.category.lower() == category.lower()
        )
        ordered = sorted(all_items, key=lambda p: p.id)

        # BUG #1: pagination parameters are ignored — every matching item is
        # returned regardless of page / pageSize.
        items = ordered

        return ServiceResult.ok(items)

    def get(self, id: str) -> ServiceResult[Product]:
        product = self._products.read_item(id)

        # BUG #4: a missing product is returned as a 200 with a null body
        # instead of a 404.
        return ServiceResult.ok(product)

    def create(self, request: CreateProductRequest) -> ServiceResult[Product]:
        # BUG #3: no validation — a zero/negative price (or empty name) is accepted.

        product = Product(
            # BUG #2: the id is never generated, so the stored document and the
            # response come back with an empty id and cannot be fetched later.
            name=request.name,
            category=request.category,
            price=request.price,
            stock=request.stock,
        )

        created = self._products.create_item(product)
        return ServiceResult.created(created)

    def update(self, id: str, request: UpdateProductRequest) -> ServiceResult[Product]:
        existing = self._products.read_item(id)
        if existing is None:
            return ServiceResult.not_found(f"Product '{id}' not found")

        existing.name = request.name
        existing.category = request.category
        existing.price = request.price
        existing.stock = request.stock

        updated = self._products.replace_item(existing)
        return ServiceResult.ok(updated)

    def delete(self, id: str) -> ServiceResult[Product]:
        removed = self._products.delete_item(id)
        if removed:
            return ServiceResult.no_content()
        return ServiceResult.not_found(f"Product '{id}' not found")

    def attach_image(self, id: str, content: bytes, content_type: str) -> ServiceResult[Product]:
        """BONUS: attach an image to a product.

        Expected behaviour: 404 if missing; otherwise upload to blob storage, set
        image_url, persist the product, publish an ImageAdded queue event, and
        return the updated product.
        """
        raise NotImplementedError("Bonus: implement the image-upload end-to-end flow.")
