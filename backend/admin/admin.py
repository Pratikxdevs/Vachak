"""Backend admin console — skeleton for pack approval / provenance review.

Offline-only tooling: lists installed packs, shows manifest checksums, and
records approval state. Never interacts with the running app at runtime.
"""
from __future__ import annotations

from dataclasses import dataclass, field


@dataclass
class ApprovalRecord:
    pack_id: str
    approved: bool = False
    notes: str = ""


@dataclass
class AdminConsole:
    approvals: dict[str, ApprovalRecord] = field(default_factory=dict)

    def approve(self, pack_id: str, notes: str = "") -> None:
        self.approvals[pack_id] = ApprovalRecord(pack_id, True, notes)

    def status(self, pack_id: str) -> str:
        rec = self.approvals.get(pack_id)
        return "approved" if rec and rec.approved else "pending"
