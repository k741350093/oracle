package work.bigbrain.model.oracle;

import lombok.Data;

@Data
public class OracleFieldMeta {
    private String fieldID;
    private String fieldName;
    private Integer fieldType;
    private Boolean isPrimary;
    private String description;
}
