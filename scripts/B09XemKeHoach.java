import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Read-only execution plans for the exact SQL in the three report services. */
public class B09XemKeHoach {
    public static void main(String[] args) throws Exception {
        String phase = args.length == 1 ? args[0] : "before";
        if (!phase.equals("before") && !phase.equals("after")) {
            throw new IllegalArgumentException("Phase phai la before hoac after.");
        }
        Path root = Path.of("").toAbsolutePath().normalize();
        Path db = root.resolve(".local-data/b09/supplychain");
        if (!Files.isRegularFile(root.resolve("pom.xml"))
                || !Files.isRegularFile(Path.of(db + ".mv.db"))) {
            throw new IllegalStateException("Chay tu goc du an va kiem tra DB B09 da ton tai.");
        }
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        Path out = root.resolve("scripts/B09-test-results/PLAN_" + phase.toUpperCase() + "_" + stamp);
        Files.createDirectories(out);
        String url = "jdbc:h2:file:" + db.toString().replace('\\', '/') + ";IFEXISTS=TRUE;ACCESS_MODE_DATA=r";
        StringBuilder metadata = new StringBuilder();
        metadata.append("Time: ").append(OffsetDateTime.now()).append('\n');
        metadata.append("Phase: ").append(phase).append("\nEnvironment: LOCAL B09 H2 file, read only\n");
        metadata.append("Parameters: fromDate=2026-10-01; toDate=2026-10-15\n");
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            metadata.append("DB version: ").append(connection.getMetaData().getDatabaseProductVersion()).append('\n');
            try (Statement statement = connection.createStatement()) {
                for (String table : new String[]{"product", "warehouse", "import_receipt", "import_detail",
                        "export_receipt", "export_detail", "stock_transfer", "transfer_detail", "inventory"}) {
                    try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                        rows.next();
                        metadata.append(table).append(" count: ").append(rows.getLong(1)).append('\n');
                    }
                }
                try (ResultSet rows = statement.executeQuery("""
                        SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME, ORDINAL_POSITION
                        FROM INFORMATION_SCHEMA.INDEX_COLUMNS
                        WHERE TABLE_SCHEMA = 'PUBLIC'
                        ORDER BY TABLE_NAME, INDEX_NAME, ORDINAL_POSITION
                        """)) {
                    metadata.append("\nExisting indexes\n");
                    while (rows.next()) {
                        metadata.append(rows.getString(1)).append(" | ").append(rows.getString(2))
                                .append(" | ").append(rows.getString(3)).append(" | ").append(rows.getInt(4)).append('\n');
                    }
                }
            }
            String[] services = {"ImportReportService", "ExportReportService", "TransferReportService"};
            String[] names = {"imports", "exports", "transfers"};
            for (int i = 0; i < services.length; i++) {
                Path source = root.resolve("src/main/java/com/example/supplychain/service/" + services[i] + ".java");
                String content = Files.readString(source, StandardCharsets.UTF_8);
                Matcher matcher = Pattern.compile("String\\s+sql\\s*=\\s*\"\"\"(.*?)\"\"\"\\s*;", Pattern.DOTALL).matcher(content);
                if (!matcher.find()) throw new IllegalStateException("Khong tim thay SQL trong " + source);
                String sql = matcher.group(1).stripIndent().strip();
                if (sql.chars().filter(c -> c == '?').count() != 2) {
                    throw new IllegalStateException("SQL can dung 2 tham so ngay: " + services[i]);
                }
                Files.writeString(out.resolve(names[i] + "-query.sql"), sql + ";\n", StandardCharsets.UTF_8);
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sql.getBytes(StandardCharsets.UTF_8)));
                metadata.append("\n").append(names[i]).append(" SQL SHA256: ").append(hash).append('\n');
                try (PreparedStatement statement = connection.prepareStatement("EXPLAIN ANALYZE " + sql)) {
                    statement.setDate(1, java.sql.Date.valueOf("2026-10-01"));
                    statement.setDate(2, java.sql.Date.valueOf("2026-10-15"));
                    try (ResultSet rows = statement.executeQuery()) {
                        rows.next();
                        String plan = rows.getString(1);
                        String report = "LOCAL B09 | " + phase.toUpperCase() + " | " + names[i]
                                + "\nfromDate=2026-10-01; toDate=2026-10-15\nSQL SHA256=" + hash + "\n\n" + plan + "\n";
                        Files.writeString(out.resolve(names[i] + "-plan.txt"), report, StandardCharsets.UTF_8);
                        System.out.println("Da luu: " + names[i] + "-plan.txt");
                        if (i == 0) {
                            System.out.println("\n=== KE HOACH BAO CAO NHAP ===\n" + report);
                        }
                    }
                }
            }
        }
        Files.writeString(out.resolve("metadata.txt"), metadata, StandardCharsets.UTF_8);
        System.out.println("HOAN THANH PLAN " + phase.toUpperCase() + ". Khong tao index, khong sua du lieu.");
        System.out.println("Ket qua: " + out);
    }
}
