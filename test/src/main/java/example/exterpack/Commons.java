package example.exterpack;

import meta.Meta;
import meta.Module;

import java.net.URL;
import java.net.http.HttpClient;
import java.sql.Statement;
import java.util.List;

@Module
final class Commons {
    private static List list;
    private static URL url;
    private static HttpClient httpClient;
    private static Statement statement;
}
