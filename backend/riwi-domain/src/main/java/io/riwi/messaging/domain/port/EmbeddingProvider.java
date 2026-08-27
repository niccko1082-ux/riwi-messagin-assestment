package io.riwi.messaging.domain.port;

/** Interfaz intercambiable de proveedor de embeddings (compatible con SDK estilo OpenAI).
 *  Passage/query separados: modelos asimétricos (p. ej. nv-embedqa) alinean mal el espacio
 *  vectorial si la pregunta se embebe igual que el contenido indexado. */
public interface EmbeddingProvider {
    float[] embedPassage(String text);

    float[] embedQuery(String text);
}
