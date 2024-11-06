package org.openapitools.model.oracle;

import lombok.Data;

import java.util.List;

@Data
public class OracleRecordResponse {
    private String nextPageToken;
    private Boolean hasMore;
    private List<OracleRecord> records;
}
