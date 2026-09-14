#!/usr/bin/env python3
"""One-shot: snapshot_download Quipus 0.6-speechv2 into modelpacks/quipus."""
import os
from huggingface_hub import snapshot_download

p = snapshot_download(
    "hyperneuronAILabs/quipus-0.6-speechv2",
    local_dir="modelpacks/quipus",
    local_dir_use_symlinks=False,
)
print("DONE", p)