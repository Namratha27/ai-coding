from pydantic import BaseModel


class Product(BaseModel):
    id: str = ""
    name: str = ""
    category: str = ""
    price: float = 0
    stock: int = 0
    image_url: str | None = None


class CreateProductRequest(BaseModel):
    name: str
    category: str
    price: float
    stock: int


class UpdateProductRequest(BaseModel):
    name: str
    category: str
    price: float
    stock: int
