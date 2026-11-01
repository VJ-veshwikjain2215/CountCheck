package com.countcheck;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_JDBC_URL", matches = ".+")
class StoreIntegrationTest {
  @Autowired Store store;
  Store.Member a, b;
  String password = "test-only-long-password";

  @BeforeEach
  void setup() {
    String id = UUID.randomUUID().toString();
    store.register("QA A", "A", id + "a@example.invalid", password);
    store.register("QA B", "B", id + "b@example.invalid", password);
    a = store.member(id + "a@example.invalid");
    b = store.member(id + "b@example.invalid");
    store.addProduct(a.businessId(), "Widget", "W1", "pieces", "Store", "8");
  }

  @AfterEach
  void cleanup() {
    store.deleteAccount(a, password);
    store.deleteAccount(b, password);
  }

  @Test
  void isolatedRecordsAndReplay() {
    long p = store.products(a.businessId()).getFirst().id();
    String key = UUID.randomUUID().toString();
    long id = store.submit(a, key, p, "8", "5", "");
    assertEquals(id, store.submit(a, key, p, "8", "5", ""));
    assertEquals(-3, store.detail(a.businessId(), id).delta());
    assertThrows(Store.Missing.class, () -> store.detail(b.businessId(), id));
    assertThrows(
        Store.Missing.class, () -> store.submit(b, UUID.randomUUID().toString(), p, "8", "5", ""));
    assertThrows(Store.Conflict.class, () -> store.submit(a, key, p, "8", "4", ""));
    assertEquals(1, store.history(a.businessId(), false, null, null, 20).size());
  }

  @Test
  void concurrentDuplicateWrites() throws Exception {
    long p = store.products(a.businessId()).getFirst().id();
    String key = UUID.randomUUID().toString();
    try (var pool = Executors.newFixedThreadPool(4)) {
      var gate = new CountDownLatch(1);
      List<Future<Long>> futures = new ArrayList<>();
      for (int i = 0; i < 4; i++)
        futures.add(
            pool.submit(
                () -> {
                  gate.await();
                  return store.submit(a, key, p, "8", "7", "");
                }));
      gate.countDown();
      Set<Long> ids = new HashSet<>();
      for (var f : futures) ids.add(f.get(15, TimeUnit.SECONDS));
      assertEquals(1, ids.size());
      assertEquals(1, store.history(a.businessId(), false, null, null, 20).size());
    }
  }

  @Test
  void filteringAndPaging() {
    long p = store.products(a.businessId()).getFirst().id();
    for (int i = 0; i < 23; i++)
      store.submit(a, UUID.randomUUID().toString(), p, "8", i % 2 == 0 ? "8" : "6", "");
    var first = store.history(a.businessId(), false, null, null, 20);
    var last = first.getLast();
    var second = store.history(a.businessId(), false, last.created(), last.id(), 20);
    assertEquals(20, first.size());
    assertEquals(3, second.size());
    assertTrue(
        Collections.disjoint(
            first.stream().map(Store.Entry::id).toList(),
            second.stream().map(Store.Entry::id).toList()));
    assertEquals(11, store.history(a.businessId(), true, null, null, 100).size());
  }

  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

  @Test
  void boundedCatalogAndLiteralSearch() {
    jdbc.update(
        "INSERT INTO products(business_id,name,sku,unit,location,recorded_count) "
            + "SELECT ?, 'Widget', 'BULK-'||n, 'pieces', 'Shelf', 0 FROM generate_series(1,120) n",
        a.businessId());
    var first = store.searchProducts(a.businessId(), "", null, null, 51);
    assertEquals(51, first.size());
    var last = first.getLast();
    var second = store.searchProducts(a.businessId(), "", last.name(), last.id(), 51);
    assertEquals(51, second.size());
    assertTrue(
        Collections.disjoint(
            first.stream().map(Store.Product::id).toList(),
            second.stream().map(Store.Product::id).toList()));
    assertEquals(121, store.productCount(a.businessId()));
    assertEquals(121, ((Number) store.stats(a.businessId()).get("products")).longValue());
    assertTrue(store.searchProducts(b.businessId(), "", null, null, 51).isEmpty());
    store.addProduct(a.businessId(), "100% pure", "PERCENT", "pieces", "Shelf", "0");
    store.addProduct(a.businessId(), "100X pure", "OTHER", "pieces", "Shelf", "0");
    var literal = store.searchProducts(a.businessId(), "100%", null, null, 100);
    assertEquals(1, literal.size());
    assertEquals("PERCENT", literal.getFirst().sku());
    assertThrows(
        IllegalArgumentException.class,
        () -> store.searchProducts(a.businessId(), "", null, 1L, 51));
  }
}
