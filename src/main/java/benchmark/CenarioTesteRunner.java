package benchmark;

import benchmark.algoritmos.FixedGridPartitioner;
import benchmark.algoritmos.TwoLayerPartitioner;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Runner separado que executa cenários de teste pré-definidos.
 * Cada cenário define um par de tabelas e suas queries de extração,
 * permitindo testar diferentes combinações de dados espaciais.
 */
public class CenarioTesteRunner {

    private static final String URL = System.getProperty("db.url", System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/tcc_espacial"));
    private static final String USER = System.getProperty("db.user", System.getenv().getOrDefault("DB_USER", "postgres"));
    private static final String PASSWORD = System.getProperty("db.password", System.getenv().getOrDefault("DB_PASSWORD", "1234"));

    private static String buildQuery(String tabela) {
        return "SELECT ogc_fid AS id, ST_AsText(wkb_geometry) AS wkt_geom FROM \"" + tabela + "\"";
    }

    private static List<CenarioTeste> criarCenarios() {
        List<CenarioTeste> cenarios = new ArrayList<>();

        cenarios.add(new CenarioTeste(
            "Ruas x Quadras",
            "eixo_rua", "arruamento_quadras",
            buildQuery("eixo_rua"), buildQuery("arruamento_quadras"),
            "MultiLineString", "MultiPolygon"
        ));

        cenarios.add(new CenarioTeste(
            "Quadras x Bairros",
            "arruamento_quadras", "divisa_de_bairros",
            buildQuery("arruamento_quadras"), buildQuery("divisa_de_bairros"),
            "MultiPolygon", "MultiPolygon"
        ));

        cenarios.add(new CenarioTeste(
            "Ruas x Bairros",
            "eixo_rua", "divisa_de_bairros",
            buildQuery("eixo_rua"), buildQuery("divisa_de_bairros"),
            "MultiLineString", "MultiPolygon"
        ));

        cenarios.add(new CenarioTeste(
            "Quadras x Regionais",
            "arruamento_quadras", "divisa_de_regionais",
            buildQuery("arruamento_quadras"), buildQuery("divisa_de_regionais"),
            "MultiPolygon", "MultiPolygon"
        ));

        return cenarios;
    }

    public static void main(String[] args) {
        List<CenarioTeste> cenarios = criarCenarios();
        Scanner scanner = new Scanner(System.in);

        // Seleção do cenário
        System.out.println("\n=== CENÁRIOS DE TESTE ===");
        for (int i = 0; i < cenarios.size(); i++) {
            System.out.println((i + 1) + " - " + cenarios.get(i));
        }
        System.out.print("Selecione o cenário: ");
        int idxCenario = scanner.nextInt() - 1;

        if (idxCenario < 0 || idxCenario >= cenarios.size()) {
            System.out.println("Cenário inválido! Usando o primeiro como padrão.");
            idxCenario = 0;
        }

        CenarioTeste cenario = cenarios.get(idxCenario);
        System.out.println("\nCenário selecionado: " + cenario);

        // Customização dos nomes das tabelas via propriedade/env
        scanner.nextLine(); // consumir newline
        String envA = System.getProperty("tabela.a", System.getenv().getOrDefault("TABELA_A", ""));
        String envB = System.getProperty("tabela.b", System.getenv().getOrDefault("TABELA_B", ""));
        if (!envA.isEmpty()) {
            cenario.setTabelaA(envA);
            cenario.setQueryExtracaoA(buildQuery(envA));
        }
        if (!envB.isEmpty()) {
            cenario.setTabelaB(envB);
            cenario.setQueryExtracaoB(buildQuery(envB));
        }

        System.out.println("Tabelas: " + cenario.getTabelaA() + " x " + cenario.getTabelaB());

        // Seleção do algoritmo
        System.out.println("\nSelecione o algoritmo de particionamento:");
        System.out.println("1 - Fixed Grid");
        System.out.println("2 - Two-Layer SOP");
        System.out.print("Opção: ");
        int opcaoAlg = scanner.nextInt();

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {

            // Extração dos dados
            List<Integer> idsA = new ArrayList<>();
            List<String> wktsA = new ArrayList<>();
            List<Integer> idsB = new ArrayList<>();
            List<String> wktsB = new ArrayList<>();

            System.out.println("\nExtraindo dados de " + cenario.getTabelaA() + "...");
            extrairDados(conn, cenario.getQueryExtracaoA(), idsA, wktsA);
            System.out.println("Registros extraídos: " + idsA.size());

            System.out.println("Extraindo dados de " + cenario.getTabelaB() + "...");
            extrairDados(conn, cenario.getQueryExtracaoB(), idsB, wktsB);
            System.out.println("Registros extraídos: " + idsB.size());

            // Particionamento
            ResultadoParticionamento resA;
            ResultadoParticionamento resB;

            switch (opcaoAlg) {
                case 1:
                    System.out.println("\nExecutando FixedGridPartitioner...");
                    FixedGridPartitioner fixedGrid = new FixedGridPartitioner();
                    resA = fixedGrid.processar(wktsA);
                    resB = fixedGrid.processar(wktsB, resA.getGrades());
                    break;
                case 2:
                    System.out.println("\nExecutando TwoLayerPartitioner...");
                    TwoLayerPartitioner twoLayer = new TwoLayerPartitioner();
                    resA = twoLayer.processar(wktsA);
                    resB = twoLayer.processar(wktsB, resA.getGrades());
                    break;
                default:
                    System.out.println("Opção inválida! Usando FixedGridPartitioner como padrão.");
                    FixedGridPartitioner defaultPart = new FixedGridPartitioner();
                    resA = defaultPart.processar(wktsA);
                    resB = defaultPart.processar(wktsB, resA.getGrades());
                    break;
            }

            // Setup dinâmico do banco
            int numeroDeParticoes = resA.getGrades().size();
            System.out.println("\nRecriando tabelas particionadas com " + numeroDeParticoes + " partições...");
            recriarTabelasParticionadas(conn, numeroDeParticoes, cenario.getTipoGeometriaA(), cenario.getTipoGeometriaB());

            // Inserção dos dados
            System.out.println("\nSalvando dados de " + cenario.getTabelaA() + " particionados...");
            salvarDados(conn, "INSERT INTO tabela_a_particionada (id, id_particao, geom) VALUES (?, ?, ST_Multi(ST_GeomFromText(?, 31982)))", idsA, resA.getDados());

            System.out.println("Salvando dados de " + cenario.getTabelaB() + " particionados...");
            salvarDados(conn, "INSERT INTO tabela_b_particionada (id, id_particao, geom) VALUES (?, ?, ST_Multi(ST_GeomFromText(?, 31982)))", idsB, resB.getDados());

            // Metadados da grade
            try (PreparedStatement stmtGrade = conn.prepareStatement("INSERT INTO grade_metadados (id_particao, geom) VALUES (?, ST_Multi(ST_GeomFromText(?, 31982)))")) {
                for (ParticaoMetadata meta : resA.getGrades()) {
                    stmtGrade.setInt(1, meta.getIdParticao());
                    stmtGrade.setString(2, meta.getWktFronteira());
                    stmtGrade.executeUpdate();
                }
            }

            // Spatial Join
            System.out.println("\nExecutando Spatial Join particionado...");
            long inicio = System.currentTimeMillis();
            int totalJoin = 0;
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET enable_partitionwise_join = on");
                try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM tabela_a_particionada a " +
                    "JOIN tabela_b_particionada b ON a.id_particao = b.id_particao " +
                    "AND ST_Intersects(a.geom, b.geom)")) {
                    if (rs.next()) totalJoin = rs.getInt(1);
                }
            }
            long tempoJoin = System.currentTimeMillis() - inicio;

            System.out.println("\n=== RESULTADO DO CENÁRIO ===");
            System.out.println("Cenário: " + cenario);
            System.out.println("Partições: " + numeroDeParticoes);
            System.out.println("Interseções encontradas: " + totalJoin);
            System.out.println("Tempo do Spatial Join: " + tempoJoin + " ms");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void recriarTabelasParticionadas(Connection conn, int numeroDeParticoes,
                                                     String tipoGeomA, String tipoGeomB) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS tabela_a_particionada CASCADE");
            stmt.execute("DROP TABLE IF EXISTS tabela_b_particionada CASCADE");
            stmt.execute("DROP TABLE IF EXISTS grade_metadados CASCADE");

            stmt.execute("CREATE TABLE tabela_a_particionada (id INTEGER, id_particao INTEGER, geom geometry(" + tipoGeomA + ", 31982)) PARTITION BY LIST (id_particao)");
            stmt.execute("CREATE TABLE tabela_b_particionada (id INTEGER, id_particao INTEGER, geom geometry(" + tipoGeomB + ", 31982)) PARTITION BY LIST (id_particao)");
            stmt.execute("CREATE TABLE grade_metadados (id_particao INTEGER, geom geometry(MultiPolygon, 31982))");

            for (int i = 1; i <= numeroDeParticoes; i++) {
                stmt.execute("CREATE TABLE tabela_a_p" + i + " PARTITION OF tabela_a_particionada FOR VALUES IN (" + i + ")");
                stmt.execute("CREATE TABLE tabela_b_p" + i + " PARTITION OF tabela_b_particionada FOR VALUES IN (" + i + ")");
            }

            stmt.execute("CREATE INDEX idx_tabela_a_geom ON tabela_a_particionada USING GIST (geom)");
            stmt.execute("CREATE INDEX idx_tabela_b_geom ON tabela_b_particionada USING GIST (geom)");
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
