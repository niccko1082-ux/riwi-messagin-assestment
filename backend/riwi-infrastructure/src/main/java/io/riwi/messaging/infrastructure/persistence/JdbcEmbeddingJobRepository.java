package io.riwi.messaging.infrastructure.persistence;

import com.pgvector.PGvector;
import io.riwi.messaging.domain.model.PendingEmbeddingJob;
import io.riwi.messaging.domain.port.EmbeddingJobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Corre con rw_worker (BYPASSRLS): no hay actor, procesa el outbox de toda la BD. */
@Repository
public class JdbcEmbeddingJobRepository implements EmbeddingJobRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcEmbeddingJobRepository(@Qualifier("workerJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<PendingEmbeddingJob> fetchPending(int limit) {
        // Un mensaje puede borrarse (soft delete) entre el encolado del job y este poll:
        // rw_copilot_search_context ya excluye is_deleted, así que embeberlo sería una llamada
        // pagada a NVIDIA para un vector que nunca se va a usar. Se falla el job en vez de
        // dejarlo 'pending' para siempre (nadie más lo va a reintentar).
        failJobsForDeletedMessages();
        String sql = """
                SELECT j.job_id, j.message_id, m.content
                  FROM rw_embedding_jobs j
                  JOIN rw_messages m ON m.message_id = j.message_id
                 WHERE j.status = 'pending'
                 ORDER BY j.job_id
                 LIMIT ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new PendingEmbeddingJob(
                rs.getLong("job_id"), rs.getLong("message_id"), rs.getString("content")), limit);
    }

    private void failJobsForDeletedMessages() {
        callVoid("SELECT rw_fail_deleted_message_embedding_jobs()");
    }

    @Override
    public void recordEmbedding(long jobId, float[] embedding, String embeddingModel) {
        callVoid("SELECT rw_record_embedding(?, ?, ?)", jobId, new PGvector(embedding), embeddingModel);
    }

    @Override
    public void failJob(long jobId) {
        callVoid("SELECT rw_fail_embedding_job(?)", jobId);
    }

    private void callVoid(String sql, Object... args) {
        jdbcTemplate.query(sql, (ResultSetExtractor<Void>) rs -> null, args);
    }
}
