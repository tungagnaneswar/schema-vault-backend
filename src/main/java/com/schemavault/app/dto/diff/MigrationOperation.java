package com.schemavault.app.dto.diff;

public enum MigrationOperation {

    // Column
    ADD_COLUMN,
    DROP_COLUMN,
    ALTER_COLUMN,

    // Table
    ADD_TABLE,
    DROP_TABLE,
    ALTER_TABLE,

    // Index
    ADD_INDEX,
    DROP_INDEX,
    ALTER_INDEX,

    // Constraint
    ADD_CONSTRAINT,
    DROP_CONSTRAINT,
    ALTER_CONSTRAINT,

    // Foreign Key
    ADD_FOREIGN_KEY,
    DROP_FOREIGN_KEY,
    ALTER_FOREIGN_KEY,

    // Generic objects
    CREATE_OBJECT,
    DROP_OBJECT,
    ALTER_OBJECT,

    // No-op
    NONE,

    // Unsupported
    UNSUPPORTED
}
