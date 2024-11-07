package work.bigbrain.model.oracle;

import lombok.Data;

import java.util.List;

@Data
public class OracleTableMeta {
    private String tableName;
    private List<OracleFieldMeta> fields;
}
