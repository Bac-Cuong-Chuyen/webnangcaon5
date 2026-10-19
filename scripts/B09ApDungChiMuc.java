import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.h2.tools.RunScript;

/** Adds the reviewed B09 indexes after creating a consistent local backup. */
public class B09ApDungChiMuc {
    static Map<String, Long> snapshot(Connection connection) throws Exception {
        Map<String, Long> values = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement()) {
            for (String table : new String[]{"product", "warehouse", "user_account", "import_receipt",
                    "import_detail", "export_receipt", "export_detail", "stock_transfer", "transfer_detail", "inventory"}) {
                try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    rows.next(); values.put(table + ".count", rows.getLong(1));
                }
            }
            for (String table : new String[]{"import_detail", "export_detail", "transfer_detail", "inventory"}) {
                try (ResultSet rows = statement.executeQuery("SELECT COALESCE(SUM(quantity), 0) FROM " + table)) {
                    rows.next(); values.put(table + ".totalQuantity", rows.getLong(1));
                }
            }
        }
        return values;
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath().normalize();
        Path db = root.resolve(".local-data/b09/supplychain");
        Path sqlFile = root.resolve("database/b09-indexes.sql");
        if (!Files.isRegularFile(root.resolve("pom.xml")) || !Files.isRegularFile(Path.of(db + ".mv.db"))
                || !Files.isRegularFile(sqlFile)) {
            throw new IllegalStateException("Can goc du an, DB file B09 va database/b09-indexes.sql.");
        }
        String sql = Files.readString(sqlFile, StandardCharsets.UTF_8);
        String statementsOnly = sql.replaceAll("(?m)--[^\\r\\n]*", "").strip();
        int statements = 0;
        for (String statement : statementsOnly.split(";")) {
            if (statement.isBlank()) continue;
            if (!statement.strip().matches("(?is)CREATE\\s+INDEX\\s+IF\\s+NOT\\s+EXISTS\\s+idx_b09_[a-z_]+\\s+ON\\s+[a-z_]+\\s*\\([a-z_,\\s]+\\)")) {
                throw new IllegalStateException("File SQL chi duoc chua CREATE INDEX IF NOT EXISTS idx_b09_...");
            }
            statements++;
        }
        if (statements != 6) throw new IllegalStateException("Can dung 6 lenh tao chi muc da chot.");
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        Path out = root.resolve("scripts/B09-test-results/INDEX_" + stamp);
        Files.createDirectories(out);
        Path backup = root.resolve(".local-data/b09/backup_truoc_chi_muc_" + stamp + ".zip");
        String url = "jdbc:h2:file:" + db.toString().replace('\\', '/') + ";IFEXISTS=TRUE";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            Map<String, Long> before = snapshot(connection);
            try (Statement statement = connection.createStatement()) {
                statement.execute("BACKUP TO '" + backup.toString().replace('\\', '/').replace("'", "''") + "'");
            }
            if (!Files.isRegularFile(backup) || Files.size(backup) == 0) {
                throw new IllegalStateException("Chua tao duoc backup. Dung truoc khi tao index.");
            }
            Files.writeString(out.resolve("b09-indexes.sql"), sql, StandardCharsets.UTF_8);
            RunScript.execute(connection, new StringReader(sql));
            Map<String, Long> after = snapshot(connection);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sql.getBytes(StandardCharsets.UTF_8)));
            String log = "LOCAL B09 index application\nSQL SHA256: " + hash + "\nBackup: " + backup
                    + "\nBefore: " + before + "\nAfter: " + after + "\n";
            Files.writeString(out.resolve("metadata.txt"), log, StandardCharsets.UTF_8);
            if (!before.equals(after)) throw new IllegalStateException("Can kiem tra lai: thong ke du lieu truoc/sau khong khop.");
            try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("""
                    SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME, ORDINAL_POSITION
                    FROM INFORMATION_SCHEMA.INDEX_COLUMNS
                    WHERE TABLE_SCHEMA='PUBLIC' AND INDEX_NAME LIKE 'IDX_B09_%'
                    ORDER BY INDEX_NAME, ORDINAL_POSITION
                    """)) {
                StringBuilder indexes = new StringBuilder();
                while (rows.next()) {
                    indexes.append(rows.getString(1)).append(" | ").append(rows.getString(2)).append(" | ")
                            .append(rows.getString(3)).append(" | ").append(rows.getInt(4)).append('\n');
                }
                Files.writeString(out.resolve("indexes.txt"), indexes, StandardCharsets.UTF_8);
            }
            System.out.println("DA AP DUNG 6 LENH CREATE INDEX. So dong va tong so luong truoc/sau khop nhau.");
            System.out.println("Backup: " + backup);
            System.out.println("Ket qua: " + out);
        }
    }
}
