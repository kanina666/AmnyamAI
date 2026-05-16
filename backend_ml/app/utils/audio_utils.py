PCM16_SAMPLE_WIDTH_BYTES = 2


def validate_pcm16_mono_chunk(
    chunk: bytes,
    *,
    sample_rate_hertz: int = 16000,
    max_duration_ms: int = 1000,
) -> None:
    if not chunk:
        raise ValueError("Audio chunk is empty")
    if len(chunk) % PCM16_SAMPLE_WIDTH_BYTES != 0:
        raise ValueError("PCM16 chunk size must be aligned to 16-bit samples")

    max_bytes = int(sample_rate_hertz * PCM16_SAMPLE_WIDTH_BYTES * max_duration_ms / 1000)
    if len(chunk) > max_bytes:
        raise ValueError("Audio chunk is too large")


def pcm16_duration_ms(chunk_size_bytes: int, sample_rate_hertz: int = 16000) -> int:
    samples = chunk_size_bytes / PCM16_SAMPLE_WIDTH_BYTES
    return int(samples / sample_rate_hertz * 1000)

