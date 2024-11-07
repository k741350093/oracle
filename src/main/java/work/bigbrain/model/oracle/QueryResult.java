package work.bigbrain.model.oracle;


import lombok.Data;

import java.util.List;

@Data
public class QueryResult {
    private final List<OracleRecord> records;
    private final boolean hasMore;
    private final String nextPageToken;
}
