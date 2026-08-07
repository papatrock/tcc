package benchmark;

import benchmark.algoritmos.FixedGridPartitioner;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class BenchmarkRunner {
    public static void main(String[] args) {
        try {
            String inputPath = "src/main/resources/datasets/dados.wkt";
            List<String> wkts = Files.readAllLines(Paths.get(inputPath));

            SpatialPartitioner partitioner = new FixedGridPartitioner();
            ResultadoParticionamento resultado = partitioner.processar(wkts);

            // 1. GERA O CSV DOS DADOS
            FileWriter dataWriter = new FileWriter("saida_dados.csv");
            dataWriter.append("wkt;id_particao\n");
            for (ParticaoResult res : resultado.getDados()) {
                dataWriter.append(res.getWkt()).append(";").append(String.valueOf(res.getIdParticao())).append("\n");
            }
            dataWriter.flush(); dataWriter.close();

            // 2. GERA O CSV DAS FRONTEIRAS (O NOVO!)
            FileWriter metaWriter = new FileWriter("saida_grades.csv");
            metaWriter.append("id_particao;wkt_fronteira\n");
            for (ParticaoMetadata meta : resultado.getGrades()) {
                metaWriter.append(String.valueOf(meta.getIdParticao())).append(";").append(meta.getWktFronteira()).append("\n");
            }
            metaWriter.flush(); metaWriter.close();

            System.out.println("Sucesso! Os arquivos saida_dados.csv e saida_grades.csv foram gerados.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}