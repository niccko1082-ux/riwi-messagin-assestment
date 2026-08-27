package io.riwi.messaging.infrastructure.ai;

import java.util.List;

/** DTOs del formato OpenAI-compatible expuesto por NVIDIA NIM. total_tokens/embedding se
 *  nombran igual que el JSON para no depender de jackson-annotations en este módulo. */
final class NvidiaDtos {
    private NvidiaDtos() {
    }

    record ChatMessageDto(String role, String content) {
    }

    // max_tokens alto: los modelos de razonamiento (p. ej. openai/gpt-oss-20b en NVIDIA NIM)
    // consumen el budget en "reasoning" antes de emitir "content" — con un límite bajo,
    // content llega null (finish_reason=length cortó antes del contenido real).
    record ChatRequest(String model, List<ChatMessageDto> messages, int max_tokens) {
    }

    record ChatChoice(ChatMessageDto message) {
    }

    record Usage(Integer total_tokens) {
    }

    record ChatResponse(List<ChatChoice> choices, Usage usage) {
    }

    // input_type es obligatorio para modelos asimétricos query/passage de NVIDIA NIM (422 sin
    // él). dimensions trunca el embedding nativo (2048) a 1024 (Matryoshka), la dimensión del
    // VECTOR(1024) en BD — evita una migración de esquema por un detalle del proveedor.
    record EmbeddingRequest(String model, List<String> input, String input_type, int dimensions) {
    }

    record EmbeddingData(List<Float> embedding) {
    }

    record EmbeddingResponse(List<EmbeddingData> data) {
    }
}
