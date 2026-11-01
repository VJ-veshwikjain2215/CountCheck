package com.countcheck;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.DefaultTransactionStatus;

@SpringBootTest(classes = {Application.class, FailureSemanticsTest.Faults.class})
@EnabledIfEnvironmentVariable(named = "DB_JDBC_URL", matches = ".+")
class FailureSemanticsTest {
  @Autowired Store store;
  @Autowired DataSource source;
  @Autowired JdbcTemplate jdbc;
  @Autowired FaultTransactionManager tx;
  Store.Member member;
  final String password = "disposable-test-password";

  @BeforeEach
  void setup() {
    String email = UUID.randomUUID() + "@example.invalid";
    store.register("Failure QA", "Failure testing", email, password);
    member = store.member(email);
    store.addProduct(member.businessId(), "Widget", "FAULT", "pieces", "QA", "8");
  }

  @AfterEach
  void cleanup() {
    tx.failAfterCommit.set(false);
    store.deleteAccount(member, password);
  }

  @Test
  void commitSucceededButResponseIsUnknown() {
    String key = UUID.randomUUID().toString();
    long product = store.products(member.businessId()).getFirst().id();
    tx.failAfterCommit.set(true);
    assertThrows(
        TransactionSystemException.class, () -> store.submit(member, key, product, "8", "6", ""));
    long id = store.submit(member, key, product, "8", "6", "");
    assertEquals(1, store.history(member.businessId(), false, null, null, 10).size());
    assertEquals(id, store.history(member.businessId(), false, null, null, 10).getFirst().id());
  }

  @Test
  void waitingInsertSeesCommittedWinner() throws Exception {
    interleave(true);
  }

  @Test
  void waitingInsertSucceedsAfterWinnerRollback() throws Exception {
    interleave(false);
  }

  private void interleave(boolean commit) throws Exception {
    String key = UUID.randomUUID().toString();
    long product = store.products(member.businessId()).getFirst().id();
    try (Connection connection = source.getConnection();
        var pool = Executors.newSingleThreadExecutor()) {
      connection.setAutoCommit(false);
      long winner;
      try (var statement =
          connection.prepareStatement(
              "INSERT INTO"
                  + " count_entries(business_id,product_id,member_id,request_id,item_name,sku,location,recorded_count,physical_count,note)"
                  + " VALUES (?,?,?,?,'Widget','FAULT','QA',8,6,'') RETURNING id")) {
        statement.setLong(1, member.businessId());
        statement.setLong(2, product);
        statement.setLong(3, member.id());
        statement.setObject(4, UUID.fromString(key));
        try (var result = statement.executeQuery()) {
          assertTrue(result.next());
          winner = result.getLong(1);
        }
      }
      var started = new CountDownLatch(1);
      Future<Long> waiting =
          pool.submit(
              () -> {
                started.countDown();
                return store.submit(member, key, product, "8", "6", "");
              });
      assertTrue(started.await(3, TimeUnit.SECONDS));
      // Observe PostgreSQL's lock wait rather than relying on a scheduler delay.
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
      boolean blocked = false;
      while (System.nanoTime() < deadline) {
        Integer n =
            jdbc.queryForObject(
                "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND"
                    + " wait_event_type='Lock' AND query LIKE 'INSERT INTO count_entries%'",
                Integer.class);
        if (n != null && n > 0) {
          blocked = true;
          break;
        }
        Thread.sleep(20);
      }
      if (!blocked) {
        connection.rollback();
        fail("Concurrent insert did not enter the expected lock wait");
      }
      assertFalse(waiting.isDone());
      if (commit) connection.commit();
      else connection.rollback();
      long actual = waiting.get(10, TimeUnit.SECONDS);
      if (commit) assertEquals(winner, actual);
      else assertNotEquals(winner, actual);
      assertEquals(1, store.history(member.businessId(), false, null, null, 10).size());
    }
  }

  @TestConfiguration
  static class Faults {
    @Bean
    @Primary
    FaultTransactionManager transactions(DataSource source) {
      return new FaultTransactionManager(source);
    }
  }

  static class FaultTransactionManager extends DataSourceTransactionManager {
    final AtomicBoolean failAfterCommit = new AtomicBoolean();

    FaultTransactionManager(DataSource source) {
      super(source);
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      super.doCommit(status);
      if (failAfterCommit.getAndSet(false))
        throw new TransactionSystemException("Simulated lost commit acknowledgement");
    }
  }
}
