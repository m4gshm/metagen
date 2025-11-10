package example.modules;

import io.github.m4gshm.meta.Meta;
import io.github.m4gshm.meta.Meta.Props;
import io.github.m4gshm.meta.Module;

import java.sql.Connection;
import java.sql.Statement;

import static io.github.m4gshm.meta.Meta.Content.FULL;

@Module
final class JdbcModule {
    @Meta(properties = @Props(FULL))
    private static Statement statement;
    @Meta(properties = @Props(FULL))
    private static Connection connection;
}
