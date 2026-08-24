package benchmark;

import benchmark.algoritmos.FixedGridPartitioner;
import benchmark.ParticaoMetadata;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class BenchmarkRunner {

    private static final String URL = "jdbc:postgresql://localhost:5432/tcc_espacial";
    private static final String USER = "postgres";
    private static final String PASSWORD = "1234";

    public static void main(String[] args) {
        List<Integer> idsQuadras = new ArrayList<>();
        List<String> wktsQuadras = new ArrayList<>();
        List<Integer> idsRuas = new ArrayList<>();
        List<String> wktsRuas = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {

            // ------------------ APAGA RESULTADO ANTERIORES ----------------
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("TRUNCATE TABLE quadras_particionadas;");
                stmt.execute("TRUNCATE TABLE ruas_particionadas;");
                System.out.println("Tabela particionada limpa com sucesso.");
            }

            // ------------------------ BUSCA DADOS -----------------------------------
            System.out.println("\nExtraindo dados do PostgreSQL...");
            System.out.println("Extraindo Quadras...");
            extrairDados(conn, "SELECT ogc_fid AS id, ST_AsText(wkb_geometry) AS wkt_geom FROM arruamento_quadras;", idsQuadras, wktsQuadras);


            System.out.println("Extraindo Ruas...");
            extrairDados(conn, "SELECT ogc_fid AS id, ST_AsText(wkb_geometry) AS wkt_geom FROM eixo_rua;", idsRuas, wktsRuas);

            // --------------------- PRÉ PROCESSAMENTO ------------
            System.out.println("\nExecutando FixedGridPartitioner...");
            FixedGridPartitioner partitioner = new FixedGridPartitioner();
            ResultadoParticionamento resQuadras = partitioner.processar(wktsQuadras);
            ResultadoParticionamento resRuas = partitioner.processar(wktsRuas, resQuadras.getGrades());

            // -------------------- VOLTA PRO BANCO --------------
            System.out.println("\nSalvando Quadras Particionadas...");
            salvarDados(conn, "INSERT INTO quadras_particionadas (id, id_particao, geom) VALUES (?, ?, ST_GeomFromText(?, 31982));", idsQuadras, resQuadras.getDados());

            System.out.println("Salvando Ruas Particionadas...");
            salvarDados(conn, "INSERT INTO ruas_particionadas (id, id_particao, geom) VALUES (?, ?, ST_GeomFromText(?, 31982));", idsRuas, resRuas.getDados());

            // -------------------- SALVA METADADOS --------------
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("TRUNCATE TABLE grade_metadados;");
            }
            try (PreparedStatement stmtGrade = conn.prepareStatement("INSERT INTO grade_metadados (id_particao, geom) VALUES (?, ST_GeomFromText(?, 31982));")) {
                for (ParticaoMetadata meta : resQuadras.getGrades()) {
                    stmtGrade.setInt(1, meta.getIdParticao());
                    stmtGrade.setString(2, meta.getWktFronteira());
                    stmtGrade.executeUpdate();
                }
            }

            System.out.println("\nPipeline Finalizado! O banco está pronto para o Spatial Join.");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void extrairDados(Connection conn, String sql, List<Integer> ids, List<String> wkts) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getInt("id"));
                wkts.add(rs.getString("wkt_geom"));
            }
        }
    }

    private static void salvarDados(Connection conn, String sql, List<Integer> ids, List<ParticaoResult> resultados) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < resultados.size(); i++) {
                stmt.setInt(1, ids.get(i));
                stmt.setInt(2, resultados.get(i).getIdParticao());
                stmt.setString(3, resultados.get(i).getWkt());
                stmt.addBatch();
                if (i > 0 && i % 500 == 0) stmt.executeBatch();
            }
            stmt.executeBatch();
        }
    }
}