class SimulatedQueue:
    def __init__(self) -> None:
        self.messages: list[str] = []

    def send_message(self, message: str) -> None:
        self.messages.append(message)

    @property
    def count(self) -> int:
        return len(self.messages)
