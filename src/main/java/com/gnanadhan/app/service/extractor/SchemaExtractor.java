package com.gnanadhan.app.service.extractor;

import com.gnanadhan.app.dto.schema.SchemaModel;
import com.gnanadhan.app.entity.DatabaseEngine;
import com.gnanadhan.app.entity.DbConnection;

public interface SchemaExtractor {
    SchemaModel extract(DbConnection connection, String decryptedPassword);
    boolean supports(DatabaseEngine engine);
}

