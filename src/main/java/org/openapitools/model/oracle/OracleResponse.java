package org.openapitools.model.oracle;

import lombok.Data;

@Data
public class OracleResponse {
    private Integer code;
    private String msg;
    private Object data;
}
