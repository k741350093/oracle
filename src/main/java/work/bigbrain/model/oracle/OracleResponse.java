package work.bigbrain.model.oracle;

import lombok.Data;

@Data
public class OracleResponse {
    private Integer code;
    private String msg;
    private Object data;
}
