package com.schemavault.app.service.comparison;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PostgresDefaultNormalizer {

    private PostgresDefaultNormalizer() {}

    private static final Pattern OUTER_PARENS = Pattern.compile("^\\(([^()]+)\\)$");

    private static final Pattern BOOL_TRUE  = Pattern.compile(
            "^(?:'true'|true|'t')(?:::(bool(?:ean)?))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern BOOL_FALSE = Pattern.compile(
            "^(?:'false'|false|'f')(?:::(bool(?:ean)?))?$", Pattern.CASE_INSENSITIVE);

    private static final Pattern INT_CAST = Pattern.compile(
            "^'?(-?\\d+)'?::(int(?:2|4|8)?|integer|bigint|smallint)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern NEXTVAL = Pattern.compile("nextval\\s*\\(", Pattern.CASE_INSENSITIVE);

    public static String normalize(String rawDefault) {
        if (rawDefault == null) return null;

        String s = rawDefault.trim();

        if (s.isEmpty()) return "";

        if (NEXTVAL.matcher(s).find()) return s;
        if (s.toLowerCase().contains("generated")) return s;
        if (s.toLowerCase().contains("identity")) return s;
        Matcher outerParen = OUTER_PARENS.matcher(s);
        if (outerParen.matches()) {
            String inner = outerParen.group(1).trim();
            if (!inner.contains("(") && !inner.contains(")")) {
                s = inner;
            }
        }

        if (s.equalsIgnoreCase("null")) return "NULL";

        if (BOOL_TRUE.matcher(s).matches()) return "true";
        if (BOOL_FALSE.matcher(s).matches()) return "false";

        Matcher intCast = INT_CAST.matcher(s);
        if (intCast.matches() && !s.contains("(")) {
            return intCast.group(1);
        }

        return s;
    }

    public static boolean defaultsAreEquivalent(String defaultA, String defaultB) {
        String a = normalize(defaultA);
        String b = normalize(defaultB);

        // Both null or both empty string → equivalent
        if (a == null && b == null) return true;
        if (a == null) return "NULL".equals(b) || b.isEmpty();
        if (b == null) return "NULL".equals(a) || a.isEmpty();

        return a.equals(b);
    }
}
