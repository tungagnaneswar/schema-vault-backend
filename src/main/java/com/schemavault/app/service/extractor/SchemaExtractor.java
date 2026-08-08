package com.schemavault.app.service.extractor;

import com.schemavault.app.dto.schema.SchemaModel;
import com.schemavault.app.entity.DatabaseEngine;
import com.schemavault.app.entity.DbConnection;

public interface SchemaExtractor {
    SchemaModel extract(DbConnection connection, String decryptedPassword);

    boolean supports(DatabaseEngine engine);
}
