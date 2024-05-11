package example.modules;

import meta.Module;

import java.sql.Connection;
import java.sql.Statement;

@Module
final class JdbcModule {
    private static Statement statement;
    private static Connection connection;
}
