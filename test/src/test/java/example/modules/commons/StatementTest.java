package example.modules.commons;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Statement;

import static example.modules.commons.StatementMeta.Prop.largeMaxRows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class StatementTest {

    @Test
    public void largeMaxRows() throws Exception {
        assertEquals(long.class, largeMaxRows.type);
        var statement = Mockito.mock(Statement.class);

        largeMaxRows.get(statement);
        verify(statement, times(1)).getLargeMaxRows();

        largeMaxRows.set(statement, 1L);
        verify(statement, times(1)).setLargeMaxRows(eq(1L));
    }
}
