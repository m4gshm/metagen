package example.modules;

import meta.Meta;
import meta.Meta.Props;
import meta.Module;

import java.sql.Connection;
import java.sql.Statement;

import static meta.Meta.Content.FULL;

@Module
final class JdbcModule {
    @Meta(properties = @Props(FULL))
    private static Statement statement;
    @Meta(properties = @Props(FULL))
    private static Connection connection;
}
