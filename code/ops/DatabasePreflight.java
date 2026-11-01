import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

/** Read-only database connection check. Never prints URLs, usernames or passwords. */
class DatabasePreflight {
  public static void main(String[] args) {
    String url = System.getenv("DB_JDBC_URL");
    String user = System.getenv("DB_USERNAME");
    String password = System.getenv("DB_PASSWORD");
    if (url == null || user == null || password == null || url.contains("HOST")) {
      System.err.println("Add your database details to .env.local before running this check.");
      System.exit(1);
    }
    if (!url.contains("sslmode=verify-full")) {
      System.err.println("Use a hosted JDBC URL with sslmode=verify-full.");
      System.exit(1);
    }
    Properties properties = new Properties();
    properties.setProperty("user", user);
    properties.setProperty("password", password);
    properties.setProperty("connectTimeout", "10");
    properties.setProperty("socketTimeout", "10");
    DriverManager.setLoginTimeout(10);
    try (Connection connection = DriverManager.getConnection(url, properties)) {
      connection.setReadOnly(true);
      // A proxy can terminate client TLS before the PostgreSQL backend connection.
      // pg_stat_ssl therefore cannot verify this client's TLS connection.
      try (var statement = connection.createStatement();
          var rows = statement.executeQuery("SELECT 1")) {
        if (!rows.next() || rows.getInt(1) != 1) throw new IllegalStateException();
      }
      if (connection.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED) {
        System.err.println("Connection works, but its isolation is not READ COMMITTED.");
        System.exit(1);
      }
      try (Connection unexpected = DriverManager.getConnection(
          url + "&sslhostnameverifier=DatabasePreflight$RejectHost", properties)) {
        throw new IllegalStateException("Hostname verification was not enforced");
      } catch (java.sql.SQLException expected) {
        // The rejecting hostname verifier must stop the TLS connection.
        boolean rejected = false;
        for (Throwable cause = expected; cause != null; cause = cause.getCause()) {
          if (cause.getMessage() != null && cause.getMessage().toLowerCase().contains("hostname")) rejected = true;
        }
        if (!rejected) throw expected;
      }
      System.out.println("PASS: verified TLS connection, hostname rejection check and READ COMMITTED. No data was changed.");
    } catch (Exception failure) {
      for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
        System.err.println("Diagnostic type: " + cause.getClass().getSimpleName());
        if (cause instanceof java.sql.SQLException sql) {
          System.err.println("SQL state: " + sql.getSQLState());
        }
      }
      System.err.println("Database check failed. Verify your provider's connection details, TLS trust and network access. No credentials were logged.");
      System.exit(1);
    }
  }

  public static final class RejectHost implements javax.net.ssl.HostnameVerifier {
    public RejectHost() {}
    public boolean verify(String hostname, javax.net.ssl.SSLSession session) { return false; }
  }
}
