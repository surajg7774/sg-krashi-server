package com.sgkrashi.dairy.service;

import com.sgkrashi.dairy.service.DairyDeliveryPreparationService.RunSummary;
import com.sgkrashi.productstore.entity.Product;
import com.sgkrashi.productstore.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the nightly preparation against a real MySQL with the real Flyway schema (so it also proves the migration and
 * the entity mapping agree). Off unless {@code -Ddairy.db.url=jdbc:mysql://localhost:PORT/DB} is given; refuses any
 * non-local host because it inserts and deletes rows.
 */
@EnabledIfSystemProperty(named = "dairy.db.url", matches = ".+")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(DairyDeliveryPreparationService.class)
class DairyDeliveryPreparationDbTest {

    private static final LocalDate DAY = LocalDate.of(2031, 1, 6);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getProperty("dairy.db.url");
        if (url == null || !(url.contains("//localhost") || url.contains("//127.0.0.1"))) {
            throw new IllegalStateException("Refusing to run against a non-local database");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getProperty("dairy.db.user", "root"));
        registry.add("spring.datasource.password", () -> System.getProperty("dairy.db.password", ""));
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    DairyDeliveryPreparationService preparation;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager transactionManager;

    private long productId;

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbc.update("INSERT INTO users (id, name, email, created_at, updated_at, is_active) VALUES (9101, 'A', 'a9101@example.invalid', NOW(6), NOW(6), 1)");
        jdbc.update("INSERT INTO users (id, name, email, created_at, updated_at, is_active) VALUES (9102, 'B', 'b9102@example.invalid', NOW(6), NOW(6), 1)");
        Long categoryId = jdbc.queryForObject("SELECT id FROM product_categories WHERE slug = 'dairy'", Long.class);
        jdbc.update("INSERT INTO products (category_id, name, slug, description, price, stock_qty, is_organic_certified, created_at, updated_at, is_active) "
                + "VALUES (?, 'Test product', 'zz-dairy-test', 'x', 10.00, 0, 0, NOW(6), NOW(6), 1)", categoryId);
        productId = jdbc.queryForObject("SELECT id FROM products WHERE slug = 'zz-dairy-test'", Long.class);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM dairy_deliveries WHERE subscription_id IN (SELECT id FROM dairy_subscriptions WHERE user_id IN (9101, 9102))");
        jdbc.update("DELETE FROM dairy_subscription_skips WHERE subscription_id IN (SELECT id FROM dairy_subscriptions WHERE user_id IN (9101, 9102))");
        jdbc.update("DELETE FROM dairy_subscriptions WHERE user_id IN (9101, 9102)");
        jdbc.update("DELETE FROM products WHERE slug = 'zz-dairy-test'");
        jdbc.update("DELETE FROM users WHERE id IN (9101, 9102)");
    }

    private long subscription(long userId, int quantity) {
        jdbc.update("INSERT INTO dairy_subscriptions (user_id, product_id, quantity, frequency, start_date, status, address_line1, address_city, "
                + "address_state, address_pincode, created_at, updated_at, is_active) VALUES (?, ?, ?, 'DAILY', '2031-01-01', 'ACTIVE', "
                + "'1 Test Road', 'Town', 'State', '482001', NOW(6), NOW(6), 1)", userId, productId, quantity);
        return jdbc.queryForObject("SELECT MAX(id) FROM dairy_subscriptions WHERE user_id = ?", Long.class, userId);
    }

    private void stock(int quantity) {
        jdbc.update("UPDATE products SET stock_qty = ? WHERE id = ?", quantity, productId);
    }

    private int stock() {
        return jdbc.queryForObject("SELECT stock_qty FROM products WHERE id = ?", Integer.class, productId);
    }

    private int rows(String status) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dairy_deliveries WHERE delivery_date = ? AND status = ?", Integer.class, DAY, status);
    }

    @Test
    void tenSimulatedDaysFollowWeekdaysPauseAndSkip() {
        // Mon 6 Jan 2031 .. Wed 15 Jan 2031. Delivers Mondays and Wednesdays, paused on Wed 8th, skipping Mon 13th.
        jdbc.update("INSERT INTO dairy_subscriptions (user_id, product_id, quantity, frequency, weekdays, start_date, status, pause_from, pause_to, "
                + "address_line1, address_city, address_state, address_pincode, created_at, updated_at, is_active) VALUES "
                + "(9101, ?, 1, 'DAYS', 'MON,WED', '2031-01-01', 'PAUSED', '2031-01-08', '2031-01-08', '1 Test Road', 'Town', 'State', '482001', NOW(6), NOW(6), 1)", productId);
        long id = jdbc.queryForObject("SELECT MAX(id) FROM dairy_subscriptions WHERE user_id = 9101", Long.class);
        jdbc.update("INSERT INTO dairy_subscription_skips (subscription_id, skip_date) VALUES (?, '2031-01-13')", id);
        stock(10);

        for (int day = 6; day <= 15; day++) {
            preparation.prepareFor(LocalDate.of(2031, 1, day));
        }
        // And a second pass over every day changes nothing.
        for (int day = 6; day <= 15; day++) {
            preparation.prepareFor(LocalDate.of(2031, 1, day));
        }

        List<String> dates = jdbc.queryForList("SELECT CAST(delivery_date AS CHAR) FROM dairy_deliveries ORDER BY delivery_date", String.class);
        assertEquals(List.of("2031-01-06", "2031-01-15"), dates);
        assertEquals(8, stock());
    }

    @Test
    void preparesADeliveryAndTakesTheStock() {
        subscription(9101, 2);
        stock(10);

        RunSummary summary = preparation.prepareFor(DAY);

        assertEquals(1, summary.scheduled());
        assertEquals(1, rows("SCHEDULED"));
        assertEquals(8, stock());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dairy_deliveries WHERE stock_reserved = 1", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dairy_deliveries WHERE address_line1 <> '1 Test Road'", Integer.class));
    }

    @Test
    void runningTwiceNeverDuplicatesADeliveryOrTakesStockTwice() {
        subscription(9101, 2);
        subscription(9102, 3);
        stock(20);

        RunSummary first = preparation.prepareFor(DAY);
        RunSummary second = preparation.prepareFor(DAY);

        assertEquals(2, first.scheduled());
        assertEquals(0, second.scheduled());
        assertEquals(2, second.alreadyPrepared());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dairy_deliveries", Integer.class));
        assertEquals(15, stock());
    }

    @Test
    void shortStockSkipsTheDeliveryAndNeverGoesNegative() {
        subscription(9101, 5);
        stock(3);

        RunSummary summary = preparation.prepareFor(DAY);

        assertEquals(1, summary.outOfStock());
        assertEquals(1, rows("SKIPPED_OUT_OF_STOCK"));
        assertEquals(3, stock());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dairy_deliveries WHERE stock_reserved = 1", Integer.class));
    }

    @Test
    void theEarliestSubscriberGetsTheLastUnits() {
        long first = subscription(9101, 3);
        subscription(9102, 3);
        stock(4);

        preparation.prepareFor(DAY);

        assertEquals("SCHEDULED", jdbc.queryForObject("SELECT status FROM dairy_deliveries WHERE subscription_id = ?", String.class, first));
        assertEquals(1, rows("SKIPPED_OUT_OF_STOCK"));
        assertEquals(1, stock());
    }

    @Test
    void twoRunsAtTheSameTimeStillPrepareEachDeliveryOnce() throws Exception {
        subscription(9101, 1);
        subscription(9102, 1);
        stock(10);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<RunSummary>> runs = List.of(
                pool.submit(() -> { go.await(); return preparation.prepareFor(DAY); }),
                pool.submit(() -> { go.await(); return preparation.prepareFor(DAY); }));
        go.countDown();
        for (Future<RunSummary> run : runs) {
            run.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dairy_deliveries", Integer.class));
        assertEquals(8, stock());
    }

    @Test
    void theLastUnitGoesToExactlyOneOfTheJobAndAConcurrentCheckout() throws Exception {
        subscription(9101, 1);
        stock(1);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);

        Future<RunSummary> job = pool.submit(() -> { go.await(); return preparation.prepareFor(DAY); });
        // The same locking checkout uses: lock the product row, check, then take.
        Future<Boolean> checkout = pool.submit(() -> {
            go.await();
            return tx.execute(status -> {
                Product product = productRepository.findByIdForUpdate(productId).orElseThrow();
                if (product.getStockQty() < 1) {
                    return false;
                }
                product.setStockQty(product.getStockQty() - 1);
                productRepository.save(product);
                return true;
            });
        });
        go.countDown();
        RunSummary summary = job.get(60, TimeUnit.SECONDS);
        boolean checkoutGotIt = checkout.get(60, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(0, stock());
        assertTrue(checkoutGotIt ^ (summary.scheduled() == 1), "exactly one of them must get the last unit");
        if (checkoutGotIt) {
            assertEquals(1, rows("SKIPPED_OUT_OF_STOCK"));
        } else {
            assertEquals(1, rows("SCHEDULED"));
        }
    }
}
