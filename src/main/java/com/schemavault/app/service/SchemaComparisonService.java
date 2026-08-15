package com.schemavault.app.service;

import com.schemavault.app.dto.diff.*;
import com.schemavault.app.dto.schema.*;
import com.schemavault.app.service.comparison.PostgresTypeNormalizer;
import com.schemavault.app.service.comparison.PostgresDefaultNormalizer;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SchemaComparisonService {

    public SchemaDiffResponse compareSchemas(SchemaModel source, SchemaModel target, String sourceEnv, String targetEnv) {
        ComparisonSummary.ComparisonSummaryBuilder summaryBuilder = ComparisonSummary.builder();
        
        List<TableDiff> tableDiffs = compareTables(source.getTables(), target.getTables(), summaryBuilder);
        List<ObjectDiff> functionDiffs = compareObjects(source.getFunctions(), target.getFunctions(), "FUNCTION", FunctionModel::getName, f -> f.getName() + "(" + nvl(f.getArguments()) + ")", FunctionModel::getDefinition, summaryBuilder);
        List<ObjectDiff> procedureDiffs = compareObjects(source.getProcedures(), target.getProcedures(), "PROCEDURE", ProcedureModel::getName, p -> p.getName() + "(" + nvl(p.getArguments()) + ")", ProcedureModel::getDefinition, summaryBuilder);
        List<ObjectDiff> sequenceDiffs = compareObjects(source.getSequences(), target.getSequences(), "SEQUENCE", SequenceModel::getName, SequenceModel::getName, s -> "type=" + s.getDataType() + " start=" + s.getStartValue() + " inc=" + s.getIncrement(), summaryBuilder);
        List<ObjectDiff> typeDiffs = compareObjects(source.getTypes(), target.getTypes(), "TYPE", TypeModel::getName, TypeModel::getName, TypeModel::getDefinition, summaryBuilder);
        List<ObjectDiff> viewDiffs = compareObjects(source.getViews(), target.getViews(), "VIEW", ViewModel::getName, ViewModel::getName, ViewModel::getDefinition, summaryBuilder);

        summaryBuilder.functionDiffs((int) functionDiffs.stream().filter(d -> d.getStatus() != DiffStatus.IDENTICAL).count());
        summaryBuilder.procedureDiffs((int) procedureDiffs.stream().filter(d -> d.getStatus() != DiffStatus.IDENTICAL).count());
        summaryBuilder.sequenceDiffs((int) sequenceDiffs.stream().filter(d -> d.getStatus() != DiffStatus.IDENTICAL).count());
        summaryBuilder.typeDiffs((int) typeDiffs.stream().filter(d -> d.getStatus() != DiffStatus.IDENTICAL).count());
        summaryBuilder.viewDiffs((int) viewDiffs.stream().filter(d -> d.getStatus() != DiffStatus.IDENTICAL).count());

        int totalDestructive = 0;
        int totalReview = 0;
        int totalSafe = 0;

        for (TableDiff td : tableDiffs) {
            if (td.getSeverity() == ChangeSeverity.DESTRUCTIVE) totalDestructive++;
            else if (td.getSeverity() == ChangeSeverity.REVIEW) totalReview++;
            else if (td.getSeverity() == ChangeSeverity.SAFE && td.getStatus() != DiffStatus.IDENTICAL) totalSafe++;
        }
        
        List<List<ObjectDiff>> allObjDiffs = List.of(functionDiffs, procedureDiffs, sequenceDiffs, typeDiffs, viewDiffs);
        for (List<ObjectDiff> diffList : allObjDiffs) {
            for (ObjectDiff od : diffList) {
                if (od.getSeverity() == ChangeSeverity.DESTRUCTIVE) totalDestructive++;
                else if (od.getSeverity() == ChangeSeverity.REVIEW) totalReview++;
                else if (od.getSeverity() == ChangeSeverity.SAFE && od.getStatus() != DiffStatus.IDENTICAL) totalSafe++;
            }
        }
        
        summaryBuilder.destructiveChanges(totalDestructive);
        summaryBuilder.reviewChanges(totalReview);
        summaryBuilder.safeChanges(totalSafe);

        ComparisonSummary summary = summaryBuilder.build();
        summary.setOverallRisk(calculateOverallRisk(summary));

        return SchemaDiffResponse.builder()
                .sourceEnvironment(sourceEnv)
                .targetEnvironment(targetEnv)
                .tableDiffs(tableDiffs)
                .functionDiffs(functionDiffs)
                .procedureDiffs(procedureDiffs)
                .sequenceDiffs(sequenceDiffs)
                .typeDiffs(typeDiffs)
                .viewDiffs(viewDiffs)
                .summary(summary)
                .build();
    }
    
    private ChangeSeverity calculateOverallRisk(ComparisonSummary summary) {
        if (summary.getDestructiveChanges() > 0) return ChangeSeverity.DESTRUCTIVE;
        if (summary.getReviewChanges() > 0) return ChangeSeverity.REVIEW;
        return ChangeSeverity.SAFE;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Tables
    // ═══════════════════════════════════════════════════════════════════════════

    private List<TableDiff> compareTables(List<TableModel> sourceTables, List<TableModel> targetTables, ComparisonSummary.ComparisonSummaryBuilder summaryBuilder) {
        List<TableDiff> diffs = new ArrayList<>();

        Map<String, TableModel> sourceMap = safeList(sourceTables).stream().collect(Collectors.toMap(TableModel::getName, Function.identity()));
        Map<String, TableModel> targetMap = safeList(targetTables).stream().collect(Collectors.toMap(TableModel::getName, Function.identity()));

        Set<String> allNames = new TreeSet<>();
        allNames.addAll(sourceMap.keySet());
        allNames.addAll(targetMap.keySet());
        
        summaryBuilder.tablesChecked(allNames.size());
        
        int identical = 0, sourceOnly = 0, targetOnly = 0, modified = 0;

        for (String name : allNames) {
            TableModel src = sourceMap.get(name);
            TableModel tgt = targetMap.get(name);

            if (src != null && tgt == null) {
                // Table in source only
                sourceOnly++;
                TableDiff diff = buildTableDiff(name, DiffStatus.MISSING_IN_TARGET, MigrationOperation.ADD_TABLE, ChangeSeverity.SAFE);
                
                // Track sub-items
                diff.setColumnsSourceOnly(src.getColumns().size());
                diff.setChangeCount(src.getColumns().size() + (src.getPrimaryKeys() != null ? 1 : 0) + safeList(src.getForeignKeys()).size() + safeList(src.getIndexes()).size() + safeList(src.getConstraints()).size());
                
                // Add column diffs for UI
                diff.setColumnDiffs(src.getColumns().stream().map(c -> ColumnDiff.builder()
                    .columnName(c.getName()).status(DiffStatus.MISSING_IN_TARGET)
                    .sourceColumn(c).migrationOperation(MigrationOperation.ADD_COLUMN).severity(ChangeSeverity.SAFE)
                    .build()).collect(Collectors.toList()));
                    
                diffs.add(diff);
            } else if (src == null) {
                // Table in target only
                targetOnly++;
                TableDiff diff = buildTableDiff(name, DiffStatus.MISSING_IN_SOURCE, MigrationOperation.DROP_TABLE, ChangeSeverity.DESTRUCTIVE);
                
                diff.setColumnsTargetOnly(tgt.getColumns().size());
                diff.setChangeCount(tgt.getColumns().size() + (tgt.getPrimaryKeys() != null ? 1 : 0) + safeList(tgt.getForeignKeys()).size() + safeList(tgt.getIndexes()).size() + safeList(tgt.getConstraints()).size());
                
                diff.setColumnDiffs(tgt.getColumns().stream().map(c -> ColumnDiff.builder()
                    .columnName(c.getName()).status(DiffStatus.MISSING_IN_SOURCE)
                    .targetColumn(c).migrationOperation(MigrationOperation.DROP_COLUMN).severity(ChangeSeverity.DESTRUCTIVE)
                    .build()).collect(Collectors.toList()));
                    
                diffs.add(diff);
            } else {
                // Both exist — deep compare
                List<ColumnDiff> columnDiffs = compareColumns(src.getColumns(), tgt.getColumns(), summaryBuilder);
                PrimaryKeyDiff pkDiff = comparePrimaryKeys(src.getPrimaryKeys(), tgt.getPrimaryKeys());
                List<ObjectDiff> constraintDiffs = compareObjects(src.getConstraints(), tgt.getConstraints(), "CONSTRAINT", ConstraintModel::getName, ConstraintModel::getName, c -> c.getType() + ": " + c.getDefinition(), summaryBuilder);
                List<ObjectDiff> fkDiffs = compareObjects(src.getForeignKeys(), tgt.getForeignKeys(), "FOREIGN_KEY", ForeignKeyModel::getName, ForeignKeyModel::getName, fk -> String.join(", ", fk.getColumns()) + " → " + fk.getReferencedTable() + "(" + String.join(", ", fk.getReferencedColumns()) + ")", summaryBuilder);
                List<ObjectDiff> indexDiffs = compareObjects(src.getIndexes(), tgt.getIndexes(), "INDEX", IndexModel::getName, IndexModel::getName, IndexModel::getDefinition, summaryBuilder);
                List<ObjectDiff> triggerDiffs = compareObjects(src.getTriggers(), tgt.getTriggers(), "TRIGGER", TriggerModel::getName, TriggerModel::getName, t -> t.getTiming() + " " + t.getEvent() + ": " + t.getDefinition(), summaryBuilder);

                int colsSrcOnly = (int) columnDiffs.stream().filter(c -> c.getStatus() == DiffStatus.MISSING_IN_TARGET).count();
                int colsTgtOnly = (int) columnDiffs.stream().filter(c -> c.getStatus() == DiffStatus.MISSING_IN_SOURCE).count();
                int colsModified = (int) columnDiffs.stream().filter(c -> c.getStatus() != DiffStatus.IDENTICAL && c.getStatus() != DiffStatus.MISSING_IN_TARGET && c.getStatus() != DiffStatus.MISSING_IN_SOURCE).count();
                
                int totalChanges = colsSrcOnly + colsTgtOnly + colsModified 
                    + (pkDiff != null && pkDiff.getStatus() != DiffStatus.IDENTICAL ? 1 : 0)
                    + (int) constraintDiffs.stream().filter(c -> c.getStatus() != DiffStatus.IDENTICAL).count()
                    + (int) fkDiffs.stream().filter(c -> c.getStatus() != DiffStatus.IDENTICAL).count()
                    + (int) indexDiffs.stream().filter(c -> c.getStatus() != DiffStatus.IDENTICAL).count()
                    + (int) triggerDiffs.stream().filter(c -> c.getStatus() != DiffStatus.IDENTICAL).count();

                DiffStatus tableStatus = totalChanges > 0 ? DiffStatus.DEFINITION_MISMATCH : DiffStatus.IDENTICAL;
                MigrationOperation tableOp = totalChanges > 0 ? MigrationOperation.ALTER_TABLE : MigrationOperation.NONE;
                ChangeSeverity maxSeverity = ChangeSeverity.SAFE;
                
                if (columnDiffs.stream().anyMatch(c -> c.getSeverity() == ChangeSeverity.DESTRUCTIVE)) maxSeverity = ChangeSeverity.DESTRUCTIVE;
                else if (columnDiffs.stream().anyMatch(c -> c.getSeverity() == ChangeSeverity.REVIEW)) maxSeverity = ChangeSeverity.REVIEW;

                if (totalChanges == 0) identical++;
                else modified++;

                diffs.add(TableDiff.builder()
                        .tableName(name)
                        .status(tableStatus)
                        .migrationOperation(tableOp)
                        .severity(maxSeverity)
                        .changeCount(totalChanges)
                        .columnsSourceOnly(colsSrcOnly)
                        .columnsTargetOnly(colsTgtOnly)
                        .columnsModified(colsModified)
                        .columnDiffs(columnDiffs)
                        .primaryKeyDiff(pkDiff)
                        .constraintDiffs(constraintDiffs)
                        .foreignKeyDiffs(fkDiffs)
                        .indexDiffs(indexDiffs)
                        .triggerDiffs(triggerDiffs)
                        .build());
            }
        }
        
        summaryBuilder.tablesIdentical(identical).tablesSourceOnly(sourceOnly).tablesTargetOnly(targetOnly).tablesModified(modified);
        return diffs;
    }
    
    private TableDiff buildTableDiff(String name, DiffStatus status, MigrationOperation op, ChangeSeverity sev) {
        return TableDiff.builder()
            .tableName(name).status(status).migrationOperation(op).severity(sev)
            .columnDiffs(Collections.emptyList())
            .constraintDiffs(Collections.emptyList())
            .foreignKeyDiffs(Collections.emptyList())
            .indexDiffs(Collections.emptyList())
            .triggerDiffs(Collections.emptyList())
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Columns
    // ═══════════════════════════════════════════════════════════════════════════

    private List<ColumnDiff> compareColumns(List<ColumnModel> sourceCols, List<ColumnModel> targetCols, ComparisonSummary.ComparisonSummaryBuilder summaryBuilder) {
        List<ColumnDiff> diffs = new ArrayList<>();

        Map<String, ColumnModel> sourceMap = safeList(sourceCols).stream().collect(Collectors.toMap(ColumnModel::getName, Function.identity()));
        Map<String, ColumnModel> targetMap = safeList(targetCols).stream().collect(Collectors.toMap(ColumnModel::getName, Function.identity()));

        Set<String> allCols = new TreeSet<>();
        allCols.addAll(sourceMap.keySet());
        allCols.addAll(targetMap.keySet());
        
        int safeCount = 0, reviewCount = 0, destructiveCount = 0;
        int colSrc = 0, colTgt = 0, colMod = 0;

        for (String colName : allCols) {
            ColumnModel src = sourceMap.get(colName);
            ColumnModel tgt = targetMap.get(colName);

            if (src != null && tgt == null) {
                diffs.add(ColumnDiff.builder()
                        .columnName(colName).status(DiffStatus.MISSING_IN_TARGET)
                        .migrationOperation(MigrationOperation.ADD_COLUMN).severity(ChangeSeverity.SAFE)
                        .sourceColumn(src).targetColumn(null)
                        .mismatchDetails(Collections.emptyList()).build());
                safeCount++; colSrc++;
            } else if (src == null) {
                diffs.add(ColumnDiff.builder()
                        .columnName(colName).status(DiffStatus.MISSING_IN_SOURCE)
                        .migrationOperation(MigrationOperation.DROP_COLUMN).severity(ChangeSeverity.DESTRUCTIVE)
                        .sourceColumn(null).targetColumn(tgt)
                        .mismatchDetails(Collections.emptyList()).build());
                destructiveCount++; colTgt++;
            } else {
                List<PropertyDiff> props = new ArrayList<>();
                List<String> details = new ArrayList<>();
                
                // TYPE
                if (!PostgresTypeNormalizer.typesAreEquivalent(src.getType(), tgt.getType())) {
                    props.add(PropertyDiff.builder().property("type").sourceValue(src.getType()).targetValue(tgt.getType()).status(DiffStatus.TYPE_MISMATCH).severity(ChangeSeverity.REVIEW).explanation("Type differs after normalization").build());
                    details.add("type: " + src.getType() + " → " + tgt.getType());
                } else {
                    props.add(PropertyDiff.builder().property("type").sourceValue(src.getType()).targetValue(tgt.getType()).status(DiffStatus.IDENTICAL).severity(ChangeSeverity.SAFE).build());
                }

                // NULLABLE
                if (src.isNullable() != tgt.isNullable()) {
                    ChangeSeverity sev = src.isNullable() ? ChangeSeverity.SAFE : ChangeSeverity.REVIEW; // true->false is review, false->true is safe
                    props.add(PropertyDiff.builder().property("nullable").sourceValue(String.valueOf(src.isNullable())).targetValue(String.valueOf(tgt.isNullable())).status(DiffStatus.NULLABILITY_MISMATCH).severity(sev).build());
                    details.add("nullable: " + src.isNullable() + " → " + tgt.isNullable());
                }

                // DEFAULT
                if (!PostgresDefaultNormalizer.defaultsAreEquivalent(src.getDefaultValue(), tgt.getDefaultValue())) {
                    props.add(PropertyDiff.builder().property("default").sourceValue(nvl(src.getDefaultValue())).targetValue(nvl(tgt.getDefaultValue())).status(DiffStatus.DEFAULT_MISMATCH).severity(ChangeSeverity.REVIEW).build());
                    details.add("default: " + nvl(src.getDefaultValue()) + " → " + nvl(tgt.getDefaultValue()));
                }
                
                // LENGTH
                if (!Objects.equals(src.getMaxLength(), tgt.getMaxLength())) {
                    props.add(PropertyDiff.builder().property("maxLength").sourceValue(String.valueOf(src.getMaxLength())).targetValue(String.valueOf(tgt.getMaxLength())).status(DiffStatus.LENGTH_MISMATCH).severity(ChangeSeverity.REVIEW).build());
                    details.add("maxLength: " + src.getMaxLength() + " → " + tgt.getMaxLength());
                }

                // PRECISION
                if (!Objects.equals(src.getNumericPrecision(), tgt.getNumericPrecision())) {
                    props.add(PropertyDiff.builder().property("precision").sourceValue(String.valueOf(src.getNumericPrecision())).targetValue(String.valueOf(tgt.getNumericPrecision())).status(DiffStatus.PRECISION_MISMATCH).severity(ChangeSeverity.REVIEW).build());
                    details.add("precision: " + src.getNumericPrecision() + " → " + tgt.getNumericPrecision());
                }

                if (details.isEmpty()) {
                    diffs.add(ColumnDiff.builder()
                            .columnName(colName).status(DiffStatus.IDENTICAL)
                            .migrationOperation(MigrationOperation.NONE).severity(ChangeSeverity.SAFE)
                            .sourceColumn(src).targetColumn(tgt)
                            .propertyDiffs(props).mismatchDetails(Collections.emptyList()).build());
                } else {
                    DiffStatus worstStatus = props.stream().map(PropertyDiff::getStatus).filter(s -> s != DiffStatus.IDENTICAL).findFirst().orElse(DiffStatus.DEFINITION_MISMATCH);
                    ChangeSeverity worstSev = props.stream().map(PropertyDiff::getSeverity).max(Comparator.naturalOrder()).orElse(ChangeSeverity.SAFE);
                    
                    diffs.add(ColumnDiff.builder()
                            .columnName(colName).status(worstStatus)
                            .migrationOperation(MigrationOperation.ALTER_COLUMN).severity(worstSev)
                            .sourceColumn(src).targetColumn(tgt)
                            .propertyDiffs(props).mismatchDetails(details).build());
                            
                    if (worstSev == ChangeSeverity.DESTRUCTIVE) destructiveCount++;
                    else if (worstSev == ChangeSeverity.REVIEW) reviewCount++;
                    else safeCount++;
                    colMod++;
                }
            }
        }
        
        return diffs;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Generic compare-by-name helper
    // ═══════════════════════════════════════════════════════════════════════════

    private <T> List<ObjectDiff> compareObjects(
            List<T> sourceList,
            List<T> targetList,
            String objectType,
            Function<T, String> nameExtractor,
            Function<T, String> keyExtractor,
            Function<T, String> defExtractor,
            ComparisonSummary.ComparisonSummaryBuilder summaryBuilder) {

        List<T> safeSrc = safeList(sourceList);
        List<T> safeTgt = safeList(targetList);

        Map<String, T> srcMap = new LinkedHashMap<>();
        for (T item : safeSrc) {
            String k = keyExtractor.apply(item);
            if (k != null) srcMap.putIfAbsent(k, item);
        }

        Map<String, T> tgtMap = new LinkedHashMap<>();
        for (T item : safeTgt) {
            String k = keyExtractor.apply(item);
            if (k != null) tgtMap.putIfAbsent(k, item);
        }

        List<ObjectDiff> diffs = new ArrayList<>();
        Set<String> allKeys = new TreeSet<>();
        allKeys.addAll(srcMap.keySet());
        allKeys.addAll(tgtMap.keySet());

        List<T> unmatchedSrc = new ArrayList<>();
        Map<String, T> unmatchedTgtByDef = new LinkedHashMap<>();

        for (String key : allKeys) {
            T src = srcMap.get(key);
            T tgt = tgtMap.get(key);
            String name = src != null ? nameExtractor.apply(src) : nameExtractor.apply(tgt);

            if (src != null && tgt != null) {
                String srcDef = defExtractor.apply(src);
                String tgtDef = defExtractor.apply(tgt);
                if (Objects.equals(srcDef, tgtDef)) {
                    diffs.add(ObjectDiff.builder().name(name).objectType(objectType).status(DiffStatus.IDENTICAL).migrationOperation(MigrationOperation.NONE).severity(ChangeSeverity.SAFE).sourceDefinition(srcDef).targetDefinition(tgtDef).mismatchDetails(Collections.emptyList()).build());
                } else {
                    diffs.add(ObjectDiff.builder().name(name).objectType(objectType).status(DiffStatus.DEFINITION_MISMATCH).migrationOperation(MigrationOperation.ALTER_OBJECT).severity(ChangeSeverity.REVIEW).sourceDefinition(srcDef).targetDefinition(tgtDef).mismatchDetails(Collections.singletonList("definition changed")).build());
                }
            } else if (src != null) {
                unmatchedSrc.add(src);
            } else {
                String def = defExtractor.apply(tgt);
                if (def != null && !unmatchedTgtByDef.containsKey(def)) {
                    unmatchedTgtByDef.put(def, tgt);
                } else {
                    diffs.add(ObjectDiff.builder().name(name).objectType(objectType).status(DiffStatus.MISSING_IN_SOURCE).migrationOperation(MigrationOperation.DROP_OBJECT).severity(ChangeSeverity.DESTRUCTIVE).targetDefinition(def).mismatchDetails(Collections.emptyList()).build());
                }
            }
        }

        for (T src : unmatchedSrc) {
            String srcDef = defExtractor.apply(src);
            String name = nameExtractor.apply(src);
            if (srcDef != null && unmatchedTgtByDef.containsKey(srcDef)) {
                T tgt = unmatchedTgtByDef.remove(srcDef);
                diffs.add(ObjectDiff.builder().name(name).objectType(objectType).status(DiffStatus.IDENTICAL).migrationOperation(MigrationOperation.NONE).severity(ChangeSeverity.SAFE).sourceDefinition(srcDef).targetDefinition(srcDef).mismatchDetails(Collections.emptyList()).build());
            } else {
                diffs.add(ObjectDiff.builder().name(name).objectType(objectType).status(DiffStatus.MISSING_IN_TARGET).migrationOperation(MigrationOperation.CREATE_OBJECT).severity(ChangeSeverity.SAFE).sourceDefinition(srcDef).mismatchDetails(Collections.emptyList()).build());
            }
        }

        for (T tgt : unmatchedTgtByDef.values()) {
            String name = nameExtractor.apply(tgt);
            String tgtDef = defExtractor.apply(tgt);
            diffs.add(ObjectDiff.builder().name(name).objectType(objectType).status(DiffStatus.MISSING_IN_SOURCE).migrationOperation(MigrationOperation.DROP_OBJECT).severity(ChangeSeverity.DESTRUCTIVE).targetDefinition(tgtDef).mismatchDetails(Collections.emptyList()).build());
        }

        return diffs;
    }
    
    private PrimaryKeyDiff comparePrimaryKeys(List<String> sourcePks, List<String> targetPks) {
        List<String> src = safeList(sourcePks);
        List<String> tgt = safeList(targetPks);
        if (src.isEmpty() && tgt.isEmpty()) return null;
        if (src.equals(tgt)) return PrimaryKeyDiff.builder().status(DiffStatus.IDENTICAL).sourceColumns(src).targetColumns(tgt).build();
        if (src.isEmpty()) return PrimaryKeyDiff.builder().status(DiffStatus.MISSING_IN_SOURCE).sourceColumns(src).targetColumns(tgt).build();
        if (tgt.isEmpty()) return PrimaryKeyDiff.builder().status(DiffStatus.MISSING_IN_TARGET).sourceColumns(src).targetColumns(tgt).build();
        return PrimaryKeyDiff.builder().status(DiffStatus.PRIMARY_KEY_MISMATCH).sourceColumns(src).targetColumns(tgt).build();
    }

    private <T> List<T> safeList(List<T> list) { return list != null ? list : Collections.emptyList(); }
    private String nvl(String s) { return s != null ? s : "(none)"; }
}
