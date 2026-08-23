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
        List<Integer> ids = new ArrayList<>();
        List<String> wkts = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            
            // ------------------ APAGA RESULTADO ANTERIORES ----------------
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("TRUNCATE TABLE quadras_particionadas;");
                System.out.println("Tabela particionada limpa com sucesso.");
            }

            // ------------------------ BUSCA DADOS -----------------------------------
            System.out.println("\nExtraindo dados do PostgreSQL...");
            String sqlSelect = "SELECT id, ST_AsText(geom) AS wkt_geom FROM \"ARRUAMENTO_QUADRAS\";"; 
            
            try (PreparedStatement stmtSelect = conn.prepareStatement(sqlSelect);
                 ResultSet rs = stmtSelect.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("id"));
                    wkts.add(rs.getString("wkt_geom"));
                }
            }
            System.out.println(wkts.size() + " geometrias carregadas na memória.");

            // --------------------- PRÉ PROCESSAMENTO ------------
            System.out.println("\nExecutando FixedGridPartitioner...");
            FixedGridPartitioner partitioner = new FixedGridPartitioner();
            ResultadoParticionamento resultado = partitioner.processar(wkts);
            List<ParticaoResult> resultados = resultado.getDados();

            // -------------------- VOLTA PRO BANCO --------------
            System.out.println("\nSalvando resultados nas partições físicas...");
            String sqlInsert = "INSERT INTO quadras_particionadas (id, id_particao, geom) VALUES (?, ?, ST_GeomFromText(?, 31982));";
            
            try (PreparedStatement stmtInsert = conn.prepareStatement(sqlInsert)) {
                for (int i = 0; i < resultados.size(); i++) {
                    ParticaoResult res = resultados.get(i);
                    
                    stmtInsert.setInt(1, ids.get(i)); // Puxa o ID da nossa lista paralela
                    stmtInsert.setInt(2, res.getIdParticao());
                    stmtInsert.setString(3, res.getWkt()); // A geometria WKT
                    
                    stmtInsert.addBatch(); // Adiciona no pacote

                    // salva aos poucos (em pacotes)
                    if (i > 0 && i % 500 == 0) {
                        stmtInsert.executeBatch();
                    }
                }
                stmtInsert.executeBatch();
            }
            
            // ------------------- PRA DEBUG, DESENHA GRADE GERADA PELO ALGORITMO
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("TRUNCATE TABLE grade_metadados;");
            }

            List<ParticaoMetadata> grades = resultado.getGrades();
            String sqlInsertGrade = "INSERT INTO grade_metadados (id_particao, geom) VALUES (?, ST_GeomFromText(?, 31982));";
            
            try (PreparedStatement stmtGrade = conn.prepareStatement(sqlInsertGrade)) {
                for (ParticaoMetadata meta : grades) {
                    stmtGrade.setInt(1, meta.getIdParticao()); // Use o getter correto da sua classe (getId() ou getIdParticao())
                    stmtGrade.setString(2, meta.getWktFronteira());
                    stmtGrade.executeUpdate();
                }
            }
            System.out.println("Desenho da grade salvo na tabela 'grade_metadados'!");

            System.out.println("\nSucesso Absoluto! Pipeline concluído.");

        } catch (Exception e) {
            System.err.println("Erro fatal no pipeline: " + e.getMessage());
            e.printStackTrace();
        }
    }
}