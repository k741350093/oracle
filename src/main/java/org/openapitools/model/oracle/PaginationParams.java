package org.openapitools.model.oracle;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PaginationParams {
    private final int offset;
    private final int maxRows;
    public PaginationParams(String pageToken, int maxPageSize) {
        this.offset = (pageToken != null && !pageToken.isEmpty()) ? Integer.parseInt(pageToken) : 0;
        this.maxRows = maxPageSize;
    }
}
