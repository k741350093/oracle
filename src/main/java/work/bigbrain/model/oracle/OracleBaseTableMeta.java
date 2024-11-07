package work.bigbrain.model.oracle;

import lombok.Data;

@Data
public class OracleBaseTableMeta {
    private String params;
    private String context;
    private String transactionID;
    private String pageToken;
    private Integer maxPageSize;
}
