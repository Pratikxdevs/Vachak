# Mundari Phrasebook (offline retrieval fallback)

17,826 Hindi→Mundari pairs copied verbatim from `datasets/hin_mun/corpus.tsv`
(Karya Hindi–Mundari corpus) for deterministic offline lookup by
`MundariPhrasebookEngine`. This is a LOOKUP TABLE, not model output:
exact match → normalized match → token-overlap retrieval → honest miss.

License: Karya Inc. Attribution-NonCommercial-ShareAlike-FreeSoftware 1.0
(see `datasets/hin_mun/LICENSE.txt`, quarantine marker `datasets/_quarantine/hin_mun`).
Non-commercial educational/demo use (SIH26042). NOT bundled as training data.
Replaced by the merged LoRA CT2 model (`modelpacks/stripped_mt_merged`,
`ml/translation/scripts/merge_lora_to_ct2.py`) once that milestone is built —
the engine prefers the merged model automatically when present.
