#!/usr/bin/env python3
"""PyTorch wrappers for IndicTrans2 ONNX export (naklitechie I/O layout).

Restored to the upstream layout (attention_mask=None). These trace correctly
under transformers>=4.57 where the decoder causal-mask prep is export-safe.
"""
from __future__ import annotations

import torch
import torch.nn as nn


def weights_are_tied(decoder: nn.Module, lm_head: nn.Module) -> bool:
    embed = decoder.embed_tokens.weight
    lm = lm_head.weight
    return embed.data_ptr() == lm.data_ptr()


def tie_lm_head_to_embed_tokens(decoder: nn.Module, lm_head: nn.Module) -> None:
    lm_head.weight = decoder.embed_tokens.weight


def _flatten_past(past_key_values: tuple) -> tuple[torch.Tensor, ...]:
    flat: list[torch.Tensor] = []
    for layer_past in past_key_values:
        dec_k, dec_v, enc_k, enc_v = layer_past
        flat.extend([dec_k, dec_v, enc_k, enc_v])
    return tuple(flat)


def _unflatten_past(flat: tuple[torch.Tensor, ...]) -> tuple:
    past = []
    for i in range(0, len(flat), 4):
        past.append((flat[i], flat[i + 1], flat[i + 2], flat[i + 3]))
    return tuple(past)


def present_output_names(num_layers: int) -> list[str]:
    names: list[str] = []
    for i in range(num_layers):
        names.extend([
            f"present.{i}.decoder.key",
            f"present.{i}.decoder.value",
            f"present.{i}.encoder.key",
            f"present.{i}.encoder.value",
        ])
    return names


def past_input_names(num_layers: int) -> list[str]:
    names: list[str] = []
    for i in range(num_layers):
        names.extend([
            f"past_key_values.{i}.decoder.key",
            f"past_key_values.{i}.decoder.value",
            f"past_key_values.{i}.encoder.key",
            f"past_key_values.{i}.encoder.value",
        ])
    return names


class IndicTransEncoderWrapper(nn.Module):
    def __init__(self, encoder: nn.Module) -> None:
        super().__init__()
        self.encoder = encoder

    def forward(self, input_ids: torch.Tensor, attention_mask: torch.Tensor) -> torch.Tensor:
        return self.encoder(input_ids=input_ids, attention_mask=attention_mask).last_hidden_state


class IndicTransDecoderWrapper(nn.Module):
    """First decode step — no past KV in, full present KV out + logits."""

    def __init__(self, decoder: nn.Module, lm_head: nn.Module) -> None:
        super().__init__()
        self.decoder = decoder
        self.lm_head = lm_head

    def forward(
        self,
        input_ids: torch.Tensor,
        encoder_attention_mask: torch.Tensor,
        encoder_hidden_states: torch.Tensor,
    ) -> tuple[torch.Tensor, ...]:
        # Pass an EXPLICIT lower-triangular self-attention mask so the traced
        # graph cannot bake a constant/identity causal mask at trace length 1.
        # 1 = attend, 0 = masked. The decoder combines this with its own causal
        # bias, yielding a correct lower-triangular mask for any decoder length
        # (this is what makes non-cache greedy correct for multi-token input).
        t = input_ids.shape[1]
        causal = torch.tril(torch.ones(1, t, dtype=torch.float32, device=input_ids.device))
        out = self.decoder(
            input_ids=input_ids,
            attention_mask=causal,
            encoder_hidden_states=encoder_hidden_states,
            encoder_attention_mask=encoder_attention_mask,
            use_cache=True,
        )
        logits = self.lm_head(out.last_hidden_state)
        return (logits, *_flatten_past(out.past_key_values))


class IndicTransDecoderWithPastWrapper(nn.Module):
    """Autoregressive steps 2..N — past KV in/out, uses dummy encoder hidden states to trigger cross-attention."""

    def __init__(self, decoder: nn.Module, lm_head: nn.Module, num_layers: int) -> None:
        super().__init__()
        self.decoder = decoder
        self.lm_head = lm_head
        self.num_layers = num_layers

    def forward(
        self,
        input_ids: torch.Tensor,
        encoder_attention_mask: torch.Tensor,
        *past_flat: torch.Tensor,
    ) -> tuple[torch.Tensor, ...]:
        past_key_values = _unflatten_past(past_flat)

        batch_size = input_ids.shape[0]
        encoder_seq_len = encoder_attention_mask.shape[1]
        embed_dim = getattr(self.decoder, "embed_dim", 512)
        dummy_encoder_hidden_states = torch.zeros(
            batch_size,
            encoder_seq_len,
            embed_dim,
            dtype=torch.float32,
            device=input_ids.device,
        )

        out = self.decoder(
            input_ids=input_ids,
            attention_mask=None,
            encoder_hidden_states=dummy_encoder_hidden_states,
            encoder_attention_mask=encoder_attention_mask,
            past_key_values=past_key_values,
            use_cache=True,
        )
        logits = self.lm_head(out.last_hidden_state)
        logits = logits + encoder_attention_mask.sum() * 0.0

        return (logits, *_flatten_past(out.past_key_values))
