package com.schemavault.app.service.comparison;

import java.util.HashMap;
import java.util.Map;

public final class PostgresTypeNormalizer {

    private PostgresTypeNormalizer() {}

    private static final Map<String, String> TYPE_ALIASES = new HashMap<>();

    static {
        TYPE_ALIASES.put("int2",      "smallint");
        TYPE_ALIASES.put("smallint",  "smallint");
        TYPE_ALIASES.put("int4",    "integer");
        TYPE_ALIASES.put("int",     "integer");
        TYPE_ALIASES.put("integer", "integer");
        TYPE_ALIASES.put("int8",   "bigint");
        TYPE_ALIASES.put("bigint", "bigint");

        TYPE_ALIASES.put("float4", "real");
        TYPE_ALIASES.put("real",   "real");
        TYPE_ALIASES.put("float8",            "double precision");
        TYPE_ALIASES.put("double precision",  "double precision");
        TYPE_ALIASES.put("float",             "double precision");

        TYPE_ALIASES.put("numeric", "numeric");
        TYPE_ALIASES.put("decimal", "numeric");
        TYPE_ALIASES.put("varchar",            "character varying");
        TYPE_ALIASES.put("character varying",  "character varying");

        TYPE_ALIASES.put("char",      "character");
        TYPE_ALIASES.put("character", "character");

        TYPE_ALIASES.put("bpchar", "character");

        TYPE_ALIASES.put("text", "text");

        TYPE_ALIASES.put("bool",    "boolean");
        TYPE_ALIASES.put("boolean", "boolean");

        TYPE_ALIASES.put("timestamp without time zone", "timestamp");
        TYPE_ALIASES.put("timestamp",                   "timestamp");

        TYPE_ALIASES.put("timestamp with time zone", "timestamptz");
        TYPE_ALIASES.put("timestamptz",              "timestamptz");

        TYPE_ALIASES.put("date", "date");

        TYPE_ALIASES.put("time without time zone", "time");
        TYPE_ALIASES.put("time",                   "time");

        TYPE_ALIASES.put("time with time zone", "timetz");
        TYPE_ALIASES.put("timetz",              "timetz");

        TYPE_ALIASES.put("interval", "interval");

        TYPE_ALIASES.put("bytea", "bytea");

        TYPE_ALIASES.put("uuid", "uuid");

        TYPE_ALIASES.put("json",  "json");
        TYPE_ALIASES.put("jsonb", "jsonb");

        TYPE_ALIASES.put("inet",    "inet");
        TYPE_ALIASES.put("cidr",    "cidr");
        TYPE_ALIASES.put("macaddr", "macaddr");
        TYPE_ALIASES.put("point",   "point");
        TYPE_ALIASES.put("line",    "line");
        TYPE_ALIASES.put("lseg",    "lseg");
        TYPE_ALIASES.put("box",     "box");
        TYPE_ALIASES.put("path",    "path");
        TYPE_ALIASES.put("polygon", "polygon");
        TYPE_ALIASES.put("circle",  "circle");

        TYPE_ALIASES.put("xml",       "xml");
        TYPE_ALIASES.put("bit",       "bit");
        TYPE_ALIASES.put("bit varying","bit varying");
        TYPE_ALIASES.put("varbit",    "bit varying");
        TYPE_ALIASES.put("money",     "money");
        TYPE_ALIASES.put("oid",       "oid");
        TYPE_ALIASES.put("void",      "void");
    }

    public static String normalize(String pgType) {
        if (pgType == null) return null;

        String trimmed = pgType.trim().toLowerCase();

        boolean isArray = false;
        String arrayPrefix = "";
        if (trimmed.endsWith("[]")) {
            isArray = true;
            trimmed = trimmed.substring(0, trimmed.length() - 2).trim();
        } else if (trimmed.startsWith("array of ")) {
            isArray = true;
            arrayPrefix = "array of ";
            trimmed = trimmed.substring("array of ".length()).trim();
        }

        String canonical = TYPE_ALIASES.getOrDefault(trimmed, trimmed);

        if (isArray) {
            if (!arrayPrefix.isEmpty()) {
                return "array of " + canonical;
            }
            return canonical + "[]";
        }
        return canonical;
    }

    public static boolean typesAreEquivalent(String typeA, String typeB) {
        if (typeA == null && typeB == null) return true;
        if (typeA == null || typeB == null) return false;
        return normalize(typeA).equals(normalize(typeB));
    }
}
