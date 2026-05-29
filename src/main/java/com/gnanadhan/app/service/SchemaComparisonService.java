package com.gnanadhan.app.service;

import com.gnanadhan.app.dto.diff.*;
import com.gnanadhan.app.dto.schema.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SchemaComparisonService {

    public SchemaDiffResponse compareSchemas(SchemaModel source, SchemaModel target, String sourceEnv, String targetEnv) {
        List<TableDiff> tableDiffs = compareTables(source.getTables(), target.getTables());
        List<ObjectDiff> functionDiffs = compareFunctions(source.getFunctions(), target.getFunctions());
        List<ObjectDiff> procedureDiffs = compareProcedures(source.getProcedures(), target.getProcedures());
        List<ObjectDiff> sequenceDiffs = compareSequences(source.getSequences(), target.getSequences());
        List<ObjectDiff> typeDiffs = compareTypes(source.getTypes(), target.getTypes());
        List<ObjectDiff> viewDiffs = compareViews(source.getViews(), target.getViews());

        return SchemaDiffResponse.builder()
                .sourceEnvironment(sourceEnv)
                .targetEnvironment(targetEnv)
                .tableDiffs(tableDiffs)
                .functionDiffs(functionDiffs)
                .procedureDiffs(procedureDiffs)
                .sequenceDiffs(sequenceDiffs)
                .typeDiffs(typeDiffs)
                .viewDiffs(viewDiffs)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Tables
    // ═══════════════════════════════════════════════════════════════════════════

    private List<TableDiff> compareTables(List<TableModel> sourceTables, List<TableModel> targetTables) {
        List<TableDiff> diffs = new ArrayList<>();

        Map<String, TableModel> sourceMap = sourceTables.stream()
                .collect(Collectors.toMap(TableModel::getName, Function.identity()));
        Map<String, TableModel> targetMap = targetTables.stream()
                .collect(Collectors.toMap(TableModel::getName, Function.identity()));

        Set<String> allNames = new TreeSet<>();
        allNames.addAll(sourceMap.keySet());
        allNames.addAll(targetMap.keySet());

        for (String name : allNames) {
            TableModel src = sourceMap.get(name);
            TableModel tgt = targetMap.get(name);

            if (src != null && tgt == null) {
                diffs.add(TableDiff.builder()
                        .tableName(name)
                        .status(DiffStatus.MISSING_IN_TARGET)
                        .columnDiffs(Collections.emptyList())
                        .primaryKeyDiff(null)
                        .constraintDiffs(Collections.emptyList())
                        .foreignKeyDiffs(Collections.emptyList())
                        .indexDiffs(Collections.emptyList())
                        .triggerDiffs(Collections.emptyList())
                        .build());
            } else if (src == null) {
                diffs.add(TableDiff.builder()
                        .tableName(name)
                        .status(DiffStatus.MISSING_IN_SOURCE)
                        .columnDiffs(Collections.emptyList())
                        .primaryKeyDiff(null)
                        .constraintDiffs(Collections.emptyList())
                        .foreignKeyDiffs(Collections.emptyList())
                        .indexDiffs(Collections.emptyList())
                        .triggerDiffs(Collections.emptyList())
                        .build());
            } else {
                // Both exist — deep compare
                List<ColumnDiff> columnDiffs = compareColumns(src.getColumns(), tgt.getColumns());
                PrimaryKeyDiff pkDiff = comparePrimaryKeys(src.getPrimaryKeys(), tgt.getPrimaryKeys());
                List<ObjectDiff> constraintDiffs = compareConstraints(src.getConstraints(), tgt.getConstraints());
                List<ObjectDiff> fkDiffs = compareForeignKeys(src.getForeignKeys(), tgt.getForeignKeys());
                List<ObjectDiff> indexDiffs = compareIndexes(src.getIndexes(), tgt.getIndexes());
                List<ObjectDiff> triggerDiffs = compareTriggers(src.getTriggers(), tgt.getTriggers());

                // Compute table-level status from all nested diffs
                DiffStatus tableStatus = computeTableStatus(columnDiffs, pkDiff, constraintDiffs, fkDiffs, indexDiffs, triggerDiffs);

                diffs.add(TableDiff.builder()
                        .tableName(name)
                        .status(tableStatus)
                        .columnDiffs(columnDiffs)
                        .primaryKeyDiff(pkDiff)
                        .constraintDiffs(constraintDiffs)
                        .foreignKeyDiffs(fkDiffs)
                        .indexDiffs(indexDiffs)
                        .triggerDiffs(triggerDiffs)
                        .build());
            }
        }

        return diffs;
    }

    private DiffStatus computeTableStatus(List<ColumnDiff> columnDiffs, PrimaryKeyDiff pkDiff,
                                          List<ObjectDiff> constraintDiffs, List<ObjectDiff> fkDiffs,
                                          List<ObjectDiff> indexDiffs, List<ObjectDiff> triggerDiffs) {
        // If any nested diff has a non-IDENTICAL status, the table has differences
        boolean hasTypeMismatch = columnDiffs.stream().anyMatch(d -> d.getStatus() == DiffStatus.TYPE_MISMATCH);

        boolean hasAnyDiff = columnDiffs.stream().anyMatch(d -> d.getStatus() != DiffStatus.IDENTICAL)
                || (pkDiff != null && pkDiff.getStatus() != DiffStatus.IDENTICAL)
                || fkDiffs.stream().anyMatch(d -> d.getStatus() != DiffStatus.IDENTICAL);

        if (!hasAnyDiff) return DiffStatus.IDENTICAL;
        if (hasTypeMismatch) return DiffStatus.TYPE_MISMATCH;
        return DiffStatus.DEFINITION_MISMATCH;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Columns
    // ═══════════════════════════════════════════════════════════════════════════

    private List<ColumnDiff> compareColumns(List<ColumnModel> sourceCols, List<ColumnModel> targetCols) {
        List<ColumnDiff> diffs = new ArrayList<>();

        Map<String, ColumnModel> sourceMap = sourceCols.stream()
                .collect(Collectors.toMap(ColumnModel::getName, Function.identity()));
        Map<String, ColumnModel> targetMap = targetCols.stream()
                .collect(Collectors.toMap(ColumnModel::getName, Function.identity()));

        Set<String> allCols = new TreeSet<>();
        allCols.addAll(sourceMap.keySet());
        allCols.addAll(targetMap.keySet());

        for (String colName : allCols) {
            ColumnModel src = sourceMap.get(colName);
            ColumnModel tgt = targetMap.get(colName);

            if (src != null && tgt == null) {
                diffs.add(ColumnDiff.builder()
                        .columnName(colName).status(DiffStatus.MISSING_IN_TARGET)
                        .sourceColumn(src).targetColumn(null)
                        .mismatchDetails(Collections.emptyList()).build());
            } else if (src == null) {
                diffs.add(ColumnDiff.builder()
                        .columnName(colName).status(DiffStatus.MISSING_IN_SOURCE)
                        .sourceColumn(null).targetColumn(tgt)
                        .mismatchDetails(Collections.emptyList()).build());
            } else {
                List<String> details = new ArrayList<>();
                boolean typeDiffers = false;

                // Check type
                if (!Objects.equals(src.getType(), tgt.getType())) {
                    details.add("type: " + src.getType() + " → " + tgt.getType());
                    typeDiffers = true;
                }

                // Check max length
                if (!Objects.equals(src.getMaxLength(), tgt.getMaxLength())) {
                    details.add("maxLength: " + src.getMaxLength() + " → " + tgt.getMaxLength());
                    typeDiffers = true;
                }

                // Check numeric precision
                if (!Objects.equals(src.getNumericPrecision(), tgt.getNumericPrecision())) {
                    details.add("numericPrecision: " + src.getNumericPrecision() + " → " + tgt.getNumericPrecision());
                    typeDiffers = true;
                }

                // Check numeric scale
                if (!Objects.equals(src.getNumericScale(), tgt.getNumericScale())) {
                    details.add("numericScale: " + src.getNumericScale() + " → " + tgt.getNumericScale());
                    typeDiffers = true;
                }

                // Check nullable
                if (src.isNullable() != tgt.isNullable()) {
                    details.add("nullable: " + src.isNullable() + " → " + tgt.isNullable());
                }

                // Check default
                if (!Objects.equals(src.getDefaultValue(), tgt.getDefaultValue())) {
                    details.add("default: " + nvl(src.getDefaultValue()) + " → " + nvl(tgt.getDefaultValue()));
                }

                if (details.isEmpty()) {
                    diffs.add(ColumnDiff.builder()
                            .columnName(colName).status(DiffStatus.IDENTICAL)
                            .sourceColumn(src).targetColumn(tgt)
                            .mismatchDetails(Collections.emptyList()).build());
                } else {
                    DiffStatus status = typeDiffers ? DiffStatus.TYPE_MISMATCH : DiffStatus.DEFINITION_MISMATCH;
                    diffs.add(ColumnDiff.builder()
                            .columnName(colName).status(status)
                            .sourceColumn(src).targetColumn(tgt)
                            .mismatchDetails(details).build());
                }
            }
        }

        return diffs;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Primary Keys
    // ═══════════════════════════════════════════════════════════════════════════

    private PrimaryKeyDiff comparePrimaryKeys(List<String> sourcePks, List<String> targetPks) {
        List<String> src = sourcePks != null ? sourcePks : Collections.emptyList();
        List<String> tgt = targetPks != null ? targetPks : Collections.emptyList();

        if (src.isEmpty() && tgt.isEmpty()) {
            return null; // No PK on either side
        }

        DiffStatus status;
        if (src.equals(tgt)) {
            status = DiffStatus.IDENTICAL;
        } else if (src.isEmpty()) {
            status = DiffStatus.MISSING_IN_SOURCE;
        } else if (tgt.isEmpty()) {
            status = DiffStatus.MISSING_IN_TARGET;
        } else {
            status = DiffStatus.DEFINITION_MISMATCH;
        }

        return PrimaryKeyDiff.builder()
                .status(status)
                .sourceColumns(src)
                .targetColumns(tgt)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Constraints
    // ═══════════════════════════════════════════════════════════════════════════

    private List<ObjectDiff> compareConstraints(List<ConstraintModel> sourceList, List<ConstraintModel> targetList) {
        Map<String, ConstraintModel> srcMap = safeList(sourceList).stream()
                .collect(Collectors.toMap(ConstraintModel::getName, Function.identity()));
        Map<String, ConstraintModel> tgtMap = safeList(targetList).stream()
                .collect(Collectors.toMap(ConstraintModel::getName, Function.identity()));

        return compareByName(srcMap, tgtMap,
                c -> c.getType() + ": " + c.getDefinition(),
                (s, t) -> {
                    List<String> details = new ArrayList<>();
                    if (!Objects.equals(s.getType(), t.getType())) {
                        details.add("type: " + s.getType() + " → " + t.getType());
                    }
                    if (!Objects.equals(s.getDefinition(), t.getDefinition())) {
                        details.add("definition changed");
                    }
                    return details;
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Foreign Keys
    // ═══════════════════════════════════════════════════════════════════════════

    private List<ObjectDiff> compareForeignKeys(List<ForeignKeyModel> sourceList, List<ForeignKeyModel> targetList) {
        Map<String, ForeignKeyModel> srcMap = safeList(sourceList).stream()
                .collect(Collectors.toMap(ForeignKeyModel::getName, Function.identity()));
        Map<String, ForeignKeyModel> tgtMap = safeList(targetList).stream()
                .collect(Collectors.toMap(ForeignKeyModel::getName, Function.identity()));

        return compareByName(srcMap, tgtMap,
                fk -> String.join(", ", fk.getColumns()) + " → " + fk.getReferencedTable() + "(" + String.join(", ", fk.getReferencedColumns()) + ")",
                (s, t) -> {
                    List<String> details = new ArrayList<>();
                    if (!Objects.equals(s.getColumns(), t.getColumns())) {
                        details.add("columns: " + s.getColumns() + " → " + t.getColumns());
                    }
                    if (!Objects.equals(s.getReferencedTable(), t.getReferencedTable())) {
                        details.add("referencedTable: " + s.getReferencedTable() + " → " + t.getReferencedTable());
                    }
                    if (!Objects.equals(s.getReferencedColumns(), t.getReferencedColumns())) {
                        details.add("referencedColumns: " + s.getReferencedColumns() + " → " + t.getReferencedColumns());
                    }
                    if (!Objects.equals(s.getUpdateRule(), t.getUpdateRule())) {
                        details.add("updateRule: " + s.getUpdateRule() + " → " + t.getUpdateRule());
                    }
                    if (!Objects.equals(s.getDeleteRule(), t.getDeleteRule())) {
                        details.add("deleteRule: " + s.getDeleteRule() + " → " + t.getDeleteRule());
                    }
                    return details;
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Indexes
    // ═══════════════════════════════════════════════════════════════════════════

    private List<ObjectDiff> compareIndexes(List<IndexModel> sourceList, List<IndexModel> targetList) {
        Map<String, IndexModel> srcMap = safeList(sourceList).stream()
                .collect(Collectors.toMap(IndexModel::getName, Function.identity()));
        Map<String, IndexModel> tgtMap = safeList(targetList).stream()
                .collect(Collectors.toMap(IndexModel::getName, Function.identity()));

        return compareByName(srcMap, tgtMap,
                IndexModel::getDefinition,
                (s, t) -> {
                    List<String> details = new ArrayList<>();
                    if (!Objects.equals(s.getColumns(), t.getColumns())) {
                        details.add("columns: " + s.getColumns() + " → " + t.getColumns());
                    }
                    if (s.isUnique() != t.isUnique()) {
                        details.add("unique: " + s.isUnique() + " → " + t.isUnique());
                    }
                    if (!Objects.equals(s.getIndexType(), t.getIndexType())) {
                        details.add("indexType: " + s.getIndexType() + " → " + t.getIndexType());
                    }
                    if (!Objects.equals(s.getDefinition(), t.getDefinition())) {
                        details.add("definition changed");
                    }
                    return details;
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Triggers
    // ═══════════════════════════════════════════════════════════════════════════

    private List<ObjectDiff> compareTriggers(List<TriggerModel> sourceList, List<TriggerModel> targetList) {
        Map<String, TriggerModel> srcMap = safeList(sourceList).stream()
                .collect(Collectors.toMap(TriggerModel::getName, Function.identity()));
        Map<String, TriggerModel> tgtMap = safeList(targetList).stream()
                .collect(Collectors.toMap(TriggerModel::getName, Function.identity()));

        return compareByName(srcMap, tgtMap,
                t -> t.getTiming() + " " + t.getEvent() + ": " + t.getDefinition(),
                (s, t) -> {
                    List<String> details = new ArrayList<>();
                    if (!Objects.equals(s.getEvent(), t.getEvent())) {
                        details.add("event: " + s.getEvent() + " → " + t.getEvent());
                    }
                    if (!Objects.equals(s.getTiming(), t.getTiming())) {
                        details.add("timing: " + s.getTiming() + " → " + t.getTiming());
                    }
                    if (!Objects.equals(s.getDefinition(), t.getDefinition())) {
                        details.add("definition changed");
                    }
                    return details;
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Functions
    // ═══════════════════════════════════════════════════════════════════════════

    private List<ObjectDiff> compareFunctions(List<FunctionModel> sourceList, List<FunctionModel> targetList) {
        // Use name + arguments as the key since PostgreSQL supports function overloading
        Map<String, FunctionModel> srcMap = safeList(sourceList).stream()
                .collect(Collectors.toMap(f -> f.getName() + "(" + nvl(f.getArguments()) + ")", Function.identity()));
        Map<String, FunctionModel> tgtMap = safeList(targetList).stream()
                .collect(Collectors.toMap(f -> f.getName() + "(" + nvl(f.getArguments()) + ")", Function.identity()));

        return compareByName(srcMap, tgtMap,
                FunctionModel::getDefinition,
                (s, t) -> {
                    List<String> details = new ArrayList<>();
                    if (!Objects.equals(s.getReturnType(), t.getReturnType())) {
                        details.add("returnType: " + s.getReturnType() + " → " + t.getReturnType());
                    }
                    if (!Objects.equals(s.getLanguage(), t.getLanguage())) {
                        details.add("language: " + s.getLanguage() + " → " + t.getLanguage());
                    }
                    if (!Objects.equals(s.getDefinition(), t.getDefinition())) {
                        details.add("definition changed");
                    }
                    return details;
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Procedures
    // ═══════════════════════════════════════════════════════════════════════════

    private List<ObjectDiff> compareProcedures(List<ProcedureModel> sourceList, List<ProcedureModel> targetList) {
        Map<String, ProcedureModel> srcMap = safeList(sourceList).stream()
                .collect(Collectors.toMap(p -> p.getName() + "(" + nvl(p.getArguments()) + ")", Function.identity()));
        Map<String, ProcedureModel> tgtMap = safeList(targetList).stream()
                .collect(Collectors.toMap(p -> p.getName() + "(" + nvl(p.getArguments()) + ")", Function.identity()));

        return compareByName(srcMap, tgtMap,
                ProcedureModel::getDefinition,
                (s, t) -> {
                    List<String> details = new ArrayList<>();
                    if (!Objects.equals(s.getLanguage(), t.getLanguage())) {
                        details.add("language: " + s.getLanguage() + " → " + t.getLanguage());
                    }
                    if (!Objects.equals(s.getDefinition(), t.getDefinition())) {
                        details.add("definition changed");
                    }
                    return details;
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Sequences
    // ═══════════════════════════════════════════════════════════════════════════

    private List<ObjectDiff> compareSequences(List<SequenceModel> sourceList, List<SequenceModel> targetList) {
        Map<String, SequenceModel> srcMap = safeList(sourceList).stream()
                .collect(Collectors.toMap(SequenceModel::getName, Function.identity()));
        Map<String, SequenceModel> tgtMap = safeList(targetList).stream()
                .collect(Collectors.toMap(SequenceModel::getName, Function.identity()));

        return compareByName(srcMap, tgtMap,
                s -> "type=" + s.getDataType() + " start=" + s.getStartValue() + " inc=" + s.getIncrement(),
                (s, t) -> {
                    List<String> details = new ArrayList<>();
                    if (!Objects.equals(s.getDataType(), t.getDataType())) {
                        details.add("dataType: " + s.getDataType() + " → " + t.getDataType());
                    }
                    if (!Objects.equals(s.getStartValue(), t.getStartValue())) {
                        details.add("startValue: " + s.getStartValue() + " → " + t.getStartValue());
                    }
                    if (!Objects.equals(s.getIncrement(), t.getIncrement())) {
                        details.add("increment: " + s.getIncrement() + " → " + t.getIncrement());
                    }
                    if (!Objects.equals(s.getMinValue(), t.getMinValue())) {
                        details.add("minValue: " + s.getMinValue() + " → " + t.getMinValue());
                    }
                    if (!Objects.equals(s.getMaxValue(), t.getMaxValue())) {
                        details.add("maxValue: " + s.getMaxValue() + " → " + t.getMaxValue());
                    }
                    if (s.isCyclic() != t.isCyclic()) {
                        details.add("cyclic: " + s.isCyclic() + " → " + t.isCyclic());
                    }
                    return details;
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Types
    // ═══════════════════════════════════════════════════════════════════════════

    private List<ObjectDiff> compareTypes(List<TypeModel> sourceList, List<TypeModel> targetList) {
        Map<String, TypeModel> srcMap = safeList(sourceList).stream()
                .collect(Collectors.toMap(TypeModel::getName, Function.identity()));
        Map<String, TypeModel> tgtMap = safeList(targetList).stream()
                .collect(Collectors.toMap(TypeModel::getName, Function.identity()));

        return compareByName(srcMap, tgtMap,
                TypeModel::getDefinition,
                (s, t) -> {
                    List<String> details = new ArrayList<>();
                    if (!Objects.equals(s.getType(), t.getType())) {
                        details.add("typeCategory: " + s.getType() + " → " + t.getType());
                    }
                    if (!Objects.equals(s.getDefinition(), t.getDefinition())) {
                        details.add("definition changed");
                    }
                    return details;
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Views
    // ═══════════════════════════════════════════════════════════════════════════

    private List<ObjectDiff> compareViews(List<ViewModel> sourceList, List<ViewModel> targetList) {
        Map<String, ViewModel> srcMap = safeList(sourceList).stream()
                .collect(Collectors.toMap(ViewModel::getName, Function.identity()));
        Map<String, ViewModel> tgtMap = safeList(targetList).stream()
                .collect(Collectors.toMap(ViewModel::getName, Function.identity()));

        return compareByName(srcMap, tgtMap,
                ViewModel::getDefinition,
                (s, t) -> {
                    List<String> details = new ArrayList<>();
                    if (!Objects.equals(s.getDefinition(), t.getDefinition())) {
                        details.add("definition changed");
                    }
                    return details;
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Generic compare-by-name helper
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Generic helper that compares two maps of named objects and produces ObjectDiff entries.
     *
     * @param srcMap      source objects keyed by name
     * @param tgtMap      target objects keyed by name
     * @param toDefinition    converts an object to its "definition" string for display
     * @param detailsBuilder  given source + target, returns list of mismatch detail strings (empty if identical)
     */
    private <T> List<ObjectDiff> compareByName(
            Map<String, T> srcMap,
            Map<String, T> tgtMap,
            Function<T, String> toDefinition,
            java.util.function.BiFunction<T, T, List<String>> detailsBuilder) {

        List<ObjectDiff> diffs = new ArrayList<>();

        Set<String> allNames = new TreeSet<>();
        allNames.addAll(srcMap.keySet());
        allNames.addAll(tgtMap.keySet());

        for (String name : allNames) {
            T src = srcMap.get(name);
            T tgt = tgtMap.get(name);

            if (src != null && tgt == null) {
                diffs.add(ObjectDiff.builder()
                        .name(name).status(DiffStatus.MISSING_IN_TARGET)
                        .sourceDefinition(toDefinition.apply(src)).targetDefinition(null)
                        .mismatchDetails(Collections.emptyList()).build());
            } else if (src == null) {
                diffs.add(ObjectDiff.builder()
                        .name(name).status(DiffStatus.MISSING_IN_SOURCE)
                        .sourceDefinition(null).targetDefinition(toDefinition.apply(tgt))
                        .mismatchDetails(Collections.emptyList()).build());
            } else {
                List<String> details = detailsBuilder.apply(src, tgt);
                DiffStatus status = details.isEmpty() ? DiffStatus.IDENTICAL : DiffStatus.DEFINITION_MISMATCH;
                diffs.add(ObjectDiff.builder()
                        .name(name).status(status)
                        .sourceDefinition(toDefinition.apply(src))
                        .targetDefinition(toDefinition.apply(tgt))
                        .mismatchDetails(details).build());
            }
        }

        return diffs;
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    private <T> List<T> safeList(List<T> list) {
        return list != null ? list : Collections.emptyList();
    }

    private String nvl(String s) {
        return s != null ? s : "(none)";
    }
}
