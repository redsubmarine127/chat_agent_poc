package com.example.assistant.infrastructure.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Comparator;

@Component
@ConditionalOnProperty(prefix = "assistant.persistence", name = "mode", havingValue = "database")
public class DatabaseMigrationRunner implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseMigrationRunner.class);
    private static final String MIGRATION_LOCATION_PATTERN = "classpath:/db/migration/*.sql";

    private final boolean enabled;
    private final String jdbcUrl;
    private final String driverClassName;
    private final String username;
    private final String password;
    private final ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();

    public DatabaseMigrationRunner(
            @Value("${assistant.migration.enabled:true}") boolean enabled,
            @Value("${spring.flyway.url}") String jdbcUrl,
            @Value("${spring.flyway.driver-class-name:}") String driverClassName,
            @Value("${spring.flyway.user}") String username,
            @Value("${spring.flyway.password}") String password
    ) {
        this.enabled = enabled;
        this.jdbcUrl = jdbcUrl;
        this.driverClassName = driverClassName;
        this.username = username;
        this.password = password;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!enabled) {
            LOGGER.info("database migration runner disabled");
            return;
        }
        if (StringUtils.hasText(driverClassName)) {
            Class.forName(driverClassName);
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            connection.setAutoCommit(false);
            ensureHistoryTable(connection);
            Resource[] resources = resourcePatternResolver.getResources(MIGRATION_LOCATION_PATTERN);
            Arrays.sort(resources, Comparator.comparing(Resource::getFilename, Comparator.nullsLast(String::compareTo)));
            for (Resource resource : resources) {
                applyMigrationIfNecessary(connection, resource);
            }
            connection.commit();
        }
    }

    private void ensureHistoryTable(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS assistant_schema_history (
                        version VARCHAR(64) PRIMARY KEY,
                        description VARCHAR(255) NOT NULL,
                        script VARCHAR(255) NOT NULL,
                        checksum VARCHAR(64) NOT NULL,
                        installed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
    }

    private void applyMigrationIfNecessary(Connection connection, Resource resource) throws Exception {
        String filename = resource.getFilename();
        if (!StringUtils.hasText(filename)) {
            return;
        }
        String version = filename.substring(0, filename.indexOf("__"));
        if (migrationApplied(connection, version)) {
            return;
        }
        LOGGER.info("applying database migration, script={}", filename);
        ScriptUtils.executeSqlScript(connection, new EncodedResource(resource, StandardCharsets.UTF_8));
        recordMigration(connection, version, filename, checksum(resource));
    }

    private boolean migrationApplied(Connection connection, String version) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM assistant_schema_history WHERE version = ?"
        )) {
            statement.setString(1, version);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void recordMigration(Connection connection, String version, String filename, String checksum) throws Exception {
        String description = filename.substring(filename.indexOf("__") + 2, filename.length() - ".sql".length())
                .replace('_', ' ');
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO assistant_schema_history (version, description, script, checksum)
                VALUES (?, ?, ?, ?)
                """)) {
            statement.setString(1, version);
            statement.setString(2, description);
            statement.setString(3, filename);
            statement.setString(4, checksum);
            statement.executeUpdate();
        }
    }

    private String checksum(Resource resource) throws Exception {
        return DigestUtils.md5DigestAsHex(resource.getContentAsByteArray());
    }
}
