package com.gnanadhan.app.dto.diff;

public enum DiffStatus {
    IDENTICAL,
    MISSING_IN_TARGET,      // Present in source, missing in target
    MISSING_IN_SOURCE,      // Present in target, missing in source
    TYPE_MISMATCH,          // Both have it, but the data type differs
    DEFINITION_MISMATCH     // Both have it, but non-type attributes differ
}
