package com.countcheck;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Store implements UserDetailsService {
  private final JdbcTemplate db;
  private final PasswordEncoder encoder;

  public Store(JdbcTemplate db, PasswordEncoder encoder) {
    this.db = db;
    this.db.setQueryTimeout(5);
    this.encoder = encoder;
  }

  public record Member(
      long id, long businessId, String email, String name, String business, String password) {
    @Override
    public String toString() {
      return "Member[id=" + id + ", businessId=" + businessId + "]";
    }
  }

  public record Product(
      long id, String name, String sku, String unit, String location, int recorded) {}

  public record Entry(
      long id,
      long productId,
      String name,
      String sku,
      String location,
      int recorded,
      int physical,
      String note,
      Instant created,
      String author,
      UUID requestId) {
    public int delta() {
      return physical - recorded;
    }

    public String difference() {
      return Rules.difference(delta());
    }
  }

  public Member member(String email) {
    return db
        .query(
            "SELECT m.id,m.business_id,m.email,m.name,m.password_hash,b.name business FROM members"
                + " m JOIN businesses b ON b.id=m.business_id WHERE email=?",
            (r, n) ->
                new Member(
                    r.getLong("id"),
                    r.getLong("business_id"),
                    r.getString("email"),
                    r.getString("name"),
                    r.getString("business"),
                    r.getString("password_hash")),
            email.strip().toLowerCase(Locale.ROOT))
        .stream()
        .findFirst()
        .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
  }

  public UserDetails loadUserByUsername(String email) {
    Member m = member(email);
    return User.withUsername(m.email()).password(m.password()).roles("OWNER").build();
  }

  @Transactional
  public String register(String name, String business, String email, String password) {
    name = Rules.text(name, 80, "Your name");
    business = Rules.text(business, 80, "Business name");
    email = Rules.text(email, 254, "Email").toLowerCase(Locale.ROOT);
    if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
      throw new IllegalArgumentException("Enter a valid email address.");
    if (password == null
        || password.length() < 12
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new IllegalArgumentException(
          "Use a password of at least 12 characters and at most 72 UTF-8 bytes.");
    Long id =
        db.queryForObject(
            "INSERT INTO businesses(name) VALUES (?) RETURNING id", Long.class, business);
    String recovery = UUID.randomUUID().toString() + UUID.randomUUID().toString();
    db.update(
        "INSERT INTO members(business_id,email,password_hash,name,recovery_hash) VALUES"
            + " (?,?,?,?,?)",
        id,
        email,
        encoder.encode(password),
        name,
        encoder.encode(recovery));
    return recovery;
  }

  public List<Product> products(long business) {
    return searchProducts(business, "", null, null, 100);
  }

  public long productCount(long business) {
    return db.queryForObject(
        "SELECT count(*) FROM products WHERE business_id=?", Long.class, business);
  }

  public List<Product> searchProducts(
      long business, String query, String after, Long afterId, int limit) {
    if (limit < 1
        || limit > 101
        || (after == null) != (afterId == null)
        || (afterId != null && afterId <= 0))
      throw new IllegalArgumentException("Invalid product page.");
    String q = query == null || query.isBlank() ? "" : Rules.text(query, 80, "Search");
    String sql =
        "SELECT id,name,sku,unit,location,recorded_count FROM products WHERE business_id=?";
    List<Object> args = new ArrayList<>();
    args.add(business);
    if (!q.isEmpty()) {
      String pattern = q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
      sql += " AND (name ILIKE ? ESCAPE '!' OR sku ILIKE ? ESCAPE '!')";
      args.add(pattern);
      args.add(pattern);
    }
    if (after != null) {
      sql += " AND (name,id)>(?,?)";
      args.add(Rules.text(after, 80, "Product cursor"));
      args.add(afterId);
    }
    sql += " ORDER BY name,id LIMIT ?";
    args.add(limit);
    return db.query(
        sql,
        (r, n) ->
            new Product(
                r.getLong("id"),
                r.getString("name"),
                r.getString("sku"),
                r.getString("unit"),
                r.getString("location"),
                r.getInt("recorded_count")),
        args.toArray());
  }

  public Product product(long business, long id) {
    return db
        .query(
            "SELECT id,name,sku,unit,location,recorded_count FROM products WHERE business_id=? AND"
                + " id=?",
            (r, n) ->
                new Product(
                    r.getLong("id"),
                    r.getString("name"),
                    r.getString("sku"),
                    r.getString("unit"),
                    r.getString("location"),
                    r.getInt("recorded_count")),
            business,
            id)
        .stream()
        .findFirst()
        .orElseThrow(Missing::new);
  }

  public void addProduct(
      long business, String name, String sku, String unit, String location, String recorded) {
    db.update(
        "INSERT INTO products(business_id,name,sku,unit,location,recorded_count) VALUES"
            + " (?,?,?,?,?,?)",
        business,
        Rules.text(name, 80, "Product name"),
        Rules.text(sku, 40, "SKU"),
        Rules.text(unit, 24, "Unit"),
        Rules.text(location, 80, "Location"),
        Rules.count(recorded));
  }

  private Entry entry(ResultSet r, int n) throws SQLException {
    return new Entry(
        r.getLong("id"),
        r.getLong("product_id"),
        r.getString("item_name"),
        r.getString("sku"),
        r.getString("location"),
        r.getInt("recorded_count"),
        r.getInt("physical_count"),
        r.getString("note"),
        r.getTimestamp("created_at").toInstant(),
        r.getString("author"),
        r.getObject("request_id", UUID.class));
  }

  public List<Entry> history(
      long business, boolean mismatch, Instant before, Long beforeId, int limit) {
    String sql =
        "SELECT c.*,m.name author FROM count_entries c JOIN members m ON m.id=c.member_id WHERE"
            + " c.business_id=?";
    List<Object> args = new ArrayList<>();
    args.add(business);
    if (mismatch) sql += " AND c.physical_count<>c.recorded_count";
    if (before != null) {
      sql += " AND (c.created_at,c.id)<(?,?)";
      args.add(java.sql.Timestamp.from(before));
      args.add(beforeId);
    }
    sql += " ORDER BY c.created_at DESC,c.id DESC LIMIT ?";
    args.add(limit);
    return db.query(sql, this::entry, args.toArray());
  }

  public Entry detail(long business, long id) {
    return db
        .query(
            "SELECT c.*,m.name author FROM count_entries c JOIN members m ON m.id=c.member_id WHERE"
                + " c.business_id=? AND c.id=?",
            this::entry,
            business,
            id)
        .stream()
        .findFirst()
        .orElseThrow(Missing::new);
  }

  public long entryCount(long business) {
    return db.queryForObject(
        "SELECT count(*) FROM count_entries WHERE business_id=?", Long.class, business);
  }

  public Map<String, Object> stats(long business) {
    return db.queryForMap(
        "SELECT count(*) total,count(*) FILTER(WHERE recorded_count<>physical_count)"
            + " mismatches,(SELECT count(*) FROM products WHERE business_id=?) products FROM"
            + " count_entries WHERE business_id=?",
        business,
        business);
  }

  @Transactional
  public long submit(
      Member member,
      String rawKey,
      long productId,
      String recorded,
      String physical,
      String rawNote) {
    UUID key = Rules.key(rawKey);
    int a = Rules.count(recorded), b = Rules.count(physical);
    String note = rawNote == null || rawNote.isBlank() ? "" : Rules.text(rawNote, 500, "Note");
    Product product = product(member.businessId(), productId);
    List<Long> ids =
        db.query(
            "INSERT INTO"
                + " count_entries(business_id,product_id,member_id,request_id,item_name,sku,location,recorded_count,physical_count,note)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?) ON CONFLICT (business_id,request_id) DO NOTHING"
                + " RETURNING id",
            (r, n) -> r.getLong(1),
            member.businessId(),
            productId,
            member.id(),
            key,
            product.name(),
            product.sku(),
            product.location(),
            a,
            b,
            note);
    if (!ids.isEmpty()) return ids.getFirst();
    Entry original =
        db
            .query(
                "SELECT c.*,m.name author FROM count_entries c JOIN members m ON m.id=c.member_id"
                    + " WHERE c.business_id=? AND c.request_id=?",
                this::entry,
                member.businessId(),
                key)
            .stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Retry unavailable"));
    if (original.productId() != productId
        || original.recorded() != a
        || original.physical() != b
        || !original.note().equals(note)) throw new Conflict();
    return original.id();
  }

  public void changePassword(Member member, String current, String next) {
    if (!encoder.matches(current, member.password()))
      throw new IllegalArgumentException("Current password is incorrect.");
    if (next == null
        || next.length() < 12
        || next.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new IllegalArgumentException("Use at least 12 characters, up to 72 UTF-8 bytes.");
    db.update("UPDATE members SET password_hash=? WHERE id=?", encoder.encode(next), member.id());
  }

  @Transactional
  public String recover(String email, String code, String password) {
    Member m;
    try {
      m = member(email);
    } catch (UsernameNotFoundException e) {
      throw new IllegalArgumentException("Recovery details are invalid.");
    }
    String hash =
        db.queryForObject(
            "SELECT recovery_hash FROM members WHERE id=? FOR UPDATE", String.class, m.id());
    if (hash == null || code == null || !encoder.matches(code, hash))
      throw new IllegalArgumentException("Recovery details are invalid.");
    if (password == null
        || password.length() < 12
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new IllegalArgumentException("Use at least 12 characters, up to 72 UTF-8 bytes.");
    String next = UUID.randomUUID().toString() + UUID.randomUUID().toString();
    db.update(
        "UPDATE members SET password_hash=?,recovery_hash=? WHERE id=?",
        encoder.encode(password),
        encoder.encode(next),
        m.id());
    return next;
  }

  @Transactional
  public void deleteAccount(Member member, String password) {
    if (!encoder.matches(password, member.password()))
      throw new IllegalArgumentException("Password is incorrect.");
    long b = member.businessId();
    db.update("DELETE FROM count_entries WHERE business_id=?", b);
    db.update("DELETE FROM products WHERE business_id=?", b);
    db.update("DELETE FROM members WHERE business_id=?", b);
    db.update("DELETE FROM businesses WHERE id=?", b);
  }

  public static class Missing extends RuntimeException {}

  public static class Conflict extends RuntimeException {}
}
