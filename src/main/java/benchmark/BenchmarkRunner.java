package benchmark;

import benchmark.algoritmos.FixedGridPartitioner;
import benchmark.algoritmos.TwoLayerPartitioner;
import benchmark.ParticaoMetadata;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class BenchmarkRunner {

    private static final String URL = System.getProperty("db.url", System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/tcc_espacial"));
    private static final String USER = System.getProperty("db.user", System.getenv().getOrDefault("DB_USER", "postgres"));
    private static final String PASSWORD = System.getProperty("db.password", System.getenv().getOrDefault("DB_PASSWORD", "1234"));

    private static final String TABELA_QUADRAS = System.getProperty("tabela.quadras", System.getenv().getOrDefault("TABELA_QUADRAS", "arruamento_quadras"));
    private static final String TABELA_RUAS = System.getProperty("tabela.ruas", System.getenv().getOrDefault("TABELA_RUAS", "eixo_rua"));

    public static void main(String[] args) {
        List<Integer> idsQuadras = new ArrayList<>();
        List<String> wktsQuadras = new ArrayList<>();
        List<Integer> idsRuas = new ArrayList<>();
        List<String> wktsRuas = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {

            Scanner scanner = new Scanner(System.in);

            // --------------------- NOMES DAS TABELAS (via propriedade/env) ------------
            String tabelaQuadras = TABELA_QUADRAS;
            String tabelaRuas = TABELA_RUAS;
            System.out.println("\n=== CONFIGURAÇÃO ===");
            System.out.println("Tabela de quadras: " + tabelaQuadras);
            System.out.println("Tabela de ruas: " + tabelaRuas);
            System.out.println("Banco: " + URL);

            // ------------------------ BUSCA DADOS -----------------------------------
            System.out.println("\nExtraindo dados do PostgreSQL...");
            System.out.println("Extraindo Quadras de " + tabelaQuadras + "...");
            extrairDados(conn, "SELECT ogc_fid AS id, ST_AsText(wkb_geometry) AS wkt_geom FROM \"" + tabelaQuadras + "\";", idsQuadras, wktsQuadras);

            System.out.println("Extraindo Ruas de " + tabelaRuas + "...");
            extrairDados(conn, "SELECT ogc_fid AS id, ST_AsText(wkb_geometry) AS wkt_geom FROM \"" + tabelaRuas + "\";", idsRuas, wktsRuas);

            // --------------------- SELEÇÃO DO ALGORITMO ------------
            System.out.println("\nSelecione o algoritmo de particionamento:");
            System.out.println("1 - Fixed Grid");
            System.out.println("2 - Two-Layer SOP");
            System.out.print("Opção: ");
            int opcao = scanner.nextInt();

            // --------------------- PRÉ PROCESSAMENTO ------------
            ResultadoParticionamento resQuadras;
            ResultadoParticionamento resRuas;

            switch (opcao) {
                case 1:
                    System.out.println("\nExecutando FixedGridPartitioner...");
                    FixedGridPartitioner fixedGrid = new FixedGridPartitioner();
                    resQuadras = fixedGrid.processar(wktsQuadras);
                    resRuas = fixedGrid.processar(wktsRuas, resQuadras.getGrades());
                    break;
                case 2:
                    System.out.println("\nExecutando TwoLayerPartitioner...");
                    TwoLayerPartitioner twoLayer = new TwoLayerPartitioner();
                    resQuadras = twoLayer.processar(wktsQuadras);
                    resRuas = twoLayer.processar(wktsRuas, resQuadras.getGrades());
                    break;
                default:
                    System.out.println("Opção inválida! Usando FixedGridPartitioner como padrão.");
                    FixedGridPartitioner defaultPart = new FixedGridPartitioner();
                    resQuadras = defaultPart.processar(wktsQuadras);
                    resRuas = defaultPart.processar(wktsRuas, resQuadras.getGrades());
                    break;
            }

            // --------------------- SETUP BANCO ------------
            int numeroDeParticoes = resQuadras.getGrades().size();
            System.out.println("\nRecriando tabelas particionadas com " + numeroDeParticoes + " partições...");
            recriarTabelasParticionadas(conn, numeroDeParticoes);

            // -------------------- VOLTA PRO BANCO --------------
            System.out.println("\nSalvando Quadras Particionadas...");
            salvarDados(conn, "INSERT INTO quadras_particionadas (id, id_particao, geom) VALUES (?, ?, ST_Multi(ST_GeomFromText(?, 31982)));", idsQuadras, resQuadras.getDados());

            System.out.println("Salvando Ruas Particionadas...");
            salvarDados(conn, "INSERT INTO ruas_particionadas (id, id_particao, geom) VALUES (?, ?, ST_Multi(ST_GeomFromText(?, 31982)));", idsRuas, resRuas.getDados());

            // -------------------- SALVA METADADOS --------------
            try (PreparedStatement stmtGrade = conn.prepareStatement("INSERT INTO grade_metadados (id_particao, geom) VALUES (?, ST_Multi(ST_GeomFromText(?, 31982)));")) {
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

    private static void recriarTabelasParticionadas(Connection conn, int numeroDeParticoes) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            // Limpeza
            stmt.execute("DROP TABLE IF EXISTS quadras_particionadas CASCADE;");
            stmt.execute("DROP TABLE IF EXISTS ruas_particionadas CASCADE;");
            stmt.execute("DROP TABLE IF EXISTS grade_metadados CASCADE;");

            // Tabelas-mãe particionadas
            stmt.execute("CREATE TABLE quadras_particionadas (id INTEGER, id_particao INTEGER, geom geometry(MultiPolygon, 31982)) PARTITION BY LIST (id_particao);");
            stmt.execute("CREATE TABLE ruas_particionadas (id INTEGER, id_particao INTEGER, geom geometry(MultiLineString, 31982)) PARTITION BY LIST (id_particao);");

            // Tabela de metadados (simples)
            stmt.execute("CREATE TABLE grade_metadados (id_particao INTEGER, geom geometry(MultiPolygon, 31982));");

            // Criação dinâmica das partições filhas
            for (int i = 1; i <= numeroDeParticoes; i++) {
                stmt.execute("CREATE TABLE quadras_p" + i + " PARTITION OF quadras_particionadas FOR VALUES IN (" + i + ");");
                stmt.execute("CREATE TABLE ruas_p" + i + " PARTITION OF ruas_particionadas FOR VALUES IN (" + i + ");");
            }

            // Índices espaciais GiST
            stmt.execute("CREATE INDEX idx_quadras_part_geom ON quadras_particionadas USING GIST (geom);");
            stmt.execute("CREATE INDEX idx_ruas_part_geom ON ruas_particionadas USING GIST (geom);");
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