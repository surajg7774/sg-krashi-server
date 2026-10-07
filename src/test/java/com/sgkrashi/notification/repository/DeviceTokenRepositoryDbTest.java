package com.sgkrashi.notification.repository;

import com.sgkrashi.notification.entity.DevicePlatform;
import com.sgkrashi.notification.entity.DeviceToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs the ownership-scoped delete against a real MySQL with the real Flyway schema. Off unless
 * {@code -Ddevicetokens.db.url=jdbc:mysql://localhost:PORT/DB} is given; refuses any non-local host
 * because it inserts and deletes rows in {@code device_tokens}.
 */
@EnabledIfSystemProperty(named = "devicetokens.db.url", matches = ".+")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DeviceTokenRepositoryDbTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getProperty("devicetokens.db.url");
        if (url == null || !(url.contains("//localhost") || url.contains("//127.0.0.1"))) {
            throw new IllegalStateException("Refusing to run against a non-local database");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getProperty("devicetokens.db.user", "root"));
        registry.add("spring.datasource.password", () -> System.getProperty("devicetokens.db.password", ""));
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    DeviceTokenRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    private void insertUser(long id) {
        jdbc.update("INSERT INTO users (id, name, email, created_at, updated_at, is_active) VALUES (?, 'T', ?, NOW(6), NOW(6), 1)",
                id, "t" + id + "@example.invalid");
    }

    private void insertToken(long userId, String token) {
        DeviceToken t = new DeviceToken();
        t.setUserId(userId);
        t.setToken(token);
        t.setPlatform(DevicePlatform.ANDROID);
        repository.saveAndFlush(t);
    }

    @Test
    void deleteByTokenAndUserIdRemovesOnlyTheOwnersRow() {
        insertUser(9001);
        insertUser(9002);
        insertToken(9001, "tok-owner-1");
        insertToken(9002, "tok-other-2");

        assertEquals(0, repository.deleteByTokenAndUserId("tok-other-2", 9001L), "someone else's token is not removed");
        assertEquals(1, repository.findByToken("tok-other-2").stream().count());

        assertEquals(1, repository.deleteByTokenAndUserId("tok-owner-1", 9001L), "own token is removed");
        assertEquals(0, repository.findByToken("tok-owner-1").stream().count());

        assertEquals(0, repository.deleteByTokenAndUserId("does-not-exist", 9001L));
    }
}
