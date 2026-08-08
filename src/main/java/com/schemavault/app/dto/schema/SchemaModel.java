package com.schemavault.app.dto.schema;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SchemaModel {
    private String databaseName;
    private List<TableModel> tables;
    private List<FunctionModel> functions;
    private List<ProcedureModel> procedures;
    private List<SequenceModel> sequences;
    private List<TypeModel> types;
    private List<ViewModel> views;
}
