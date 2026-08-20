package com.queryzen.dialect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlGuardTest {

    @Test
    void allowsPlainSelect() {
        assertDoesNotThrow(() -> SqlGuard.validate("SELECT * FROM demo.employees", "oracle"));
    }

    @Test
    void allowsSelectWithHint() {
        assertDoesNotThrow(() ->
                SqlGuard.validate("SELECT /*+ PARALLEL(2) */ * FROM demo.employees", "oracle"));
    }

    @Test
    void allowsWithSelect() {
        assertDoesNotThrow(() ->
                SqlGuard.validate("WITH t AS (SELECT * FROM demo.employees) SELECT * FROM t", "oracle"));
    }

    @Test
    void rejectsInsert() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SqlGuard.validate("INSERT INTO demo.employees VALUES (1,'x',1)", "oracle"));
        assertTrue(ex.getMessage().contains("Statement"));
    }

    @Test
    void rejectsUpdateAndDelete() {
        assertThrows(IllegalArgumentException.class,
                () -> SqlGuard.validate("UPDATE demo.employees SET name='x'", "oracle"));
        assertThrows(IllegalArgumentException.class,
                () -> SqlGuard.validate("DELETE FROM demo.employees", "oracle"));
    }

    @Test
    void rejectsDdl() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SqlGuard.validate("DROP TABLE demo.employees", "oracle"));
        assertTrue(ex.getMessage().contains("DDL"));
    }

    @Test
    void rejectsTruncate() {
        assertThrows(IllegalArgumentException.class,
                () -> SqlGuard.validate("TRUNCATE TABLE demo.employees", "oracle"));
    }

    @Test
    void rejectsForUpdate() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SqlGuard.validate("SELECT * FROM demo.employees FOR UPDATE", "oracle"));
        assertTrue(ex.getMessage().contains("FOR UPDATE"));
    }

    @Test
    void rejectsMultipleStatements() {
        assertThrows(IllegalArgumentException.class,
                () -> SqlGuard.validate("SELECT 1 FROM dual; DELETE FROM demo.employees", "oracle"));
    }

    @Test
    void rejectsPlsqlBlock() {
        assertThrows(IllegalArgumentException.class,
                () -> SqlGuard.validate("BEGIN DBMS_OUTPUT.PUT_LINE('x'); END;", "oracle"));
    }

    @Test
    void rejectsEmptyOrGarbage() {
        assertThrows(IllegalArgumentException.class, () -> SqlGuard.validate("", "oracle"));
        assertThrows(IllegalArgumentException.class, () -> SqlGuard.validate("not sql at all", "oracle"));
    }

    @Test
    void oracleRowLimitWrapsOnlyNonWith() {
        OracleDialect d = new OracleDialect();
        String wrapped = d.applyRowLimit("select * from demo.employees order by id", 100);
        assertTrue(wrapped.contains("ROWNUM <= 100"));

        String with = d.applyRowLimit("with t as (select * from demo.employees) select * from t", 100);
        assertTrue(with.startsWith("with"));
        assertTrue(!with.contains("ROWNUM"));
    }
}