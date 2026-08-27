package io.riwi.messaging.infrastructure.persistence;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

/** DataSource separado para rw_worker (Fase 5): un rol distinto de rw_app, con BYPASSRLS y
 *  permisos acotados a rw_embedding_jobs/rw_messages (ver 010_create_embedding_worker.sql).
 *  No comparte pool con el datasource principal para no mezclar privilegios por accidente.
 *
 *  Declarar aquí un segundo bean DataSource desactiva la autoconfiguración de Spring Boot
 *  del datasource principal (@ConditionalOnMissingBean(DataSource.class) — cualquier bean
 *  DataSource, sin importar el nombre, la desactiva), así que el principal también se declara
 *  explícito y @Primary; si no, todo el JdbcTemplate sin @Qualifier (la mayoría de los
 *  repositorios) terminaría usando por accidente el datasource de rw_worker. */
@Configuration
@EnableConfigurationProperties(WorkerDataSourceConfig.Properties.class)
public class WorkerDataSourceConfig {

    @ConfigurationProperties(prefix = "riwi.worker-datasource")
    public record Properties(String url, String username, String password) {
    }

    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    // DataSourceBuilder.create().build() con @ConfigurationProperties directo sobre el bean ya
    // construido no funciona: HikariDataSource no tiene un setter "url" (solo "jdbcUrl"), así
    // que la propiedad se pierde en silencio. DataSourceProperties.initializeDataSourceBuilder()
    // sí sabe mapear "url" al setter correcto según la implementación concreta. El segundo
    // @ConfigurationProperties (spring.datasource.hikari) es el mismo patrón que usa la
    // autoconfiguración de Boot para el pool — sin él, tuning futuro (pool-size, timeouts) no
    // aplicaría a este datasource.
    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public DataSource dataSource(DataSourceProperties dataSourceProperties) {
        return dataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    // Con 2 beans JdbcTemplate en contexto (este y workerJdbcTemplate), el autowiring sin
    // @Qualifier de los demás repositorios necesita un @Primary explícito — no basta con que
    // el DataSource lo sea.
    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public DataSource workerDataSource(Properties props) {
        return DataSourceBuilder.create()
                .url(props.url())
                .username(props.username())
                .password(props.password())
                .driverClassName("org.postgresql.Driver")
                .build();
    }

    @Bean
    public JdbcTemplate workerJdbcTemplate(@Qualifier("workerDataSource") DataSource workerDataSource) {
        return new JdbcTemplate(workerDataSource);
    }
}
