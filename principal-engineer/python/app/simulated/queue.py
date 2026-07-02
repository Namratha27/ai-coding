from __future__ import annotations


class SimulatedQueue:
    def __init__(self, name: str):
        self.name = name
        self.messages: list[str] = []

    def send_message(self, message: str) -> None:
        self.messages.append(message)

    @property
    def count(self) -> int:
        return len(self.messages)
