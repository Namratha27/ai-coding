class SimulatedBlobContainer:
    BASE_URL = "https://sim.blob.local/product-images"

    def __init__(self) -> None:
        self._blobs: dict[str, bytes] = {}

    def upload(self, blob_name: str, content_bytes: bytes) -> str:
        self._blobs[blob_name] = content_bytes
        return f"{self.BASE_URL}/{blob_name}"

    @property
    def count(self) -> int:
        return len(self._blobs)
