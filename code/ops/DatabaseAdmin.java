import java.sql.*;
import org.flywaydb.core.Flyway;

/** Deployment operations; credentials come only from environment variables. */
class DatabaseAdmin {
  public static void main(String[] args) {
    try {
      String url = System.getenv("DB_JDBC_URL");
      String user = System.getenv("DB_USERNAME");
      String password = System.getenv("DB_PASSWORD");
      if (url == null || user == null || password == null) throw new IllegalStateException();
      switch (args[0]) {
        case "migrate" -> {
          if (url.contains("-pooler")) throw new IllegalArgumentException("Migrations need a direct endpoint");
          var result = Flyway.configure().dataSource(url,user,password)
              .locations("filesystem:src/main/resources/db/migration")
              .cleanDisabled(true).validateMigrationNaming(true).load().migrate();
          System.out.println("PASS: migrations applied=" + result.migrationsExecuted);
        }
        case "inspect", "inspect-options" -> {
          var properties=new java.util.Properties();
          properties.setProperty("user",user);properties.setProperty("password",password);
          if(args[0].equals("inspect-options"))properties.setProperty("options","-c statement_timeout=5000");
          try(var c=DriverManager.getConnection(url,properties);
              var s=c.createStatement();
              var rows=s.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_schema='public'")) {
            rows.next();System.out.println("PASS: public tables="+rows.getInt(1));
          }
        }
        case "runtime-role" -> {
          String runtimePassword=System.getenv("RUNTIME_DB_PASSWORD");
          if(runtimePassword==null||!runtimePassword.matches("[0-9a-f]{64}")) throw new IllegalArgumentException();
          try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){
            c.setAutoCommit(false);
            s.execute("CREATE ROLE countcheck_runtime LOGIN PASSWORD '"+runtimePassword+"'");
            try(var rs=s.executeQuery("SELECT current_database()")){
              rs.next();String database=rs.getString(1).replace("\"","\"\"");
              try(var grants=c.createStatement()){grants.execute("GRANT CONNECT ON DATABASE \""+database+"\" TO countcheck_runtime");}
            }
            s.execute("GRANT USAGE ON SCHEMA public TO countcheck_runtime");
            s.execute("GRANT SELECT,INSERT,DELETE ON businesses,products,count_entries TO countcheck_runtime");
            s.execute("GRANT SELECT,INSERT,UPDATE,DELETE ON members TO countcheck_runtime");
            s.execute("GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO countcheck_runtime");
            s.execute("ALTER ROLE countcheck_runtime SET statement_timeout='5s'");
            s.execute("ALTER ROLE countcheck_runtime SET lock_timeout='3s'");
            c.commit();System.out.println("PASS: restricted runtime role created");
          }
        }
        case "check-role" -> {
          try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){
            s.executeQuery("SELECT count(*) FROM count_entries").close();
            for(String forbidden:new String[]{"CREATE TABLE public.permission_probe(id integer)",
                "UPDATE count_entries SET note=note WHERE false", "SELECT * FROM flyway_schema_history"}){
              try{s.execute(forbidden);throw new IllegalStateException("Excess runtime permission");}
              catch(SQLException denied){if(!"42501".equals(denied.getSQLState()))throw denied;}
            }
            System.out.println("PASS: runtime reads allowed; schema DDL, count updates and migration history access denied");
          }
        }
        default -> throw new IllegalArgumentException();
      }
    } catch(Exception failure) {
      System.err.println("Database operation failed: "+failure.getClass().getSimpleName());
      if(failure instanceof SQLException sql)System.err.println("SQL state: "+sql.getSQLState());
      System.exit(1);
    }
  }
}
