package com.sgkrashi.customer.repository;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * backup/reapply-deletions.sql re-applies account deletions after a database
 * restore, and must do exactly what AccountErasureRepository.erase() does.
 * Nothing links the two at runtime, so this fails the build the day one
 * changes without the other - otherwise a restore would silently resurrect
 * data the app deletes.
 */
class ReapplyDeletionsScriptDriftTest {

    // The table named by each DELETE ... FROM <table> / UPDATE <table>, ignoring multi-table aliases.
    private static final Pattern TARGET = Pattern.compile("(?i)\\b(?:DELETE\\s+(?:\\w+\\s+)?FROM|UPDATE)\\s+(\\w+)");

    private static Set<String> touchedTables(String text) {
        Set<String> tables = new TreeSet<>();
        Matcher m = TARGET.matcher(text);
        while (m.find()) {
            tables.add(m.group(1).toLowerCase());
        }
        return tables;
    }

    private static String javaSqlOnly(String javaSource) {
        // Only the string literals inside erase() carry SQL; the Javadoc also says "deleted"/"UPDATE"-like words.
        int start = javaSource.indexOf("public void erase(");
        assertTrue(start > 0, "erase() not found in AccountErasureRepository");
        return javaSource.substring(start);
    }

    @Test
    void theRestoreScriptTouchesExactlyTheTablesTheServerEraseTouches() throws Exception {
        String java = javaSqlOnly(Files.readString(Path.of("src/main/java/com/sgkrashi/customer/repository/AccountErasureRepository.java")));
        String sql = Files.readString(Path.of("backup/reapply-deletions.sql"));

        // The two multi-table deletes name their target by alias (ci, cm); both sides must still delete from the child tables.
        Set<String> fromJava = touchedTables(java);
        Set<String> fromSql = touchedTables(sql);

        assertEquals(fromJava, fromSql, "backup/reapply-deletions.sql and AccountErasureRepository.erase() touch different tables");
    }

    @Test
    void theAnonymizationValuesMatch() throws Exception {
        String java = Files.readString(Path.of("src/main/java/com/sgkrashi/customer/repository/AccountErasureRepository.java"));
        String sql = Files.readString(Path.of("backup/reapply-deletions.sql"));

        for (String literal : new String[] {
                "comment = '[removed]'", "shipping_line1 = '[removed]'", "shipping_line2 = NULL",
                "cancellation_reason = NULL", "name = 'Deleted user'", "phone = NULL", "password_hash = NULL",
                "google_id = NULL", "is_active = FALSE" }) {
            assertTrue(java.contains(literal), "server erase() no longer contains: " + literal);
            assertTrue(sql.contains(literal), "restore script is missing: " + literal);
        }
        // The placeholder email format is what the backup job's manifest uses to list anonymized accounts
        // (backup.sh: email LIKE 'deleted-%@deleted.invalid'); the service builds it, the restore script must too.
        String service = Files.readString(Path.of("src/main/java/com/sgkrashi/customer/service/impl/AccountDeletionServiceImpl.java"));
        String backupJob = Files.readString(Path.of("backup/backup.sh"));
        assertTrue(service.contains("\"deleted-\" + user.getId() + \"@deleted.invalid\""), "placeholder email format changed in the server");
        assertTrue(sql.contains("CONCAT('deleted-', @uid, '@deleted.invalid')"), "placeholder email format differs in the restore script");
        assertTrue(backupJob.contains("LIKE 'deleted-%@deleted.invalid'"), "backup.sh no longer finds anonymized accounts by the placeholder email");
    }
}
