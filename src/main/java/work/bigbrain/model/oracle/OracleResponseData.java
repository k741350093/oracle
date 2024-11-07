package work.bigbrain.model.oracle;

import lombok.Data;

import java.util.List;

@Data
public class OracleResponseData {

    private String nextPageToken;
    private boolean hasMore;
    private List<OracleRecord> records;
}
