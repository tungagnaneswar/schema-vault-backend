package com.gnanadhan.app.dto.diff;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PrimaryKeyDiff {
    private DiffStatus status;
    private List<String> sourceColumns;
    private List<String> targetColumns;
}
