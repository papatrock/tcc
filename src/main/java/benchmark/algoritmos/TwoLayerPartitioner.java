package benchmark.algoritmos;

import benchmark.ParticaoMetadata;
import benchmark.ParticaoResult;
import benchmark.ResultadoParticionamento;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;

import java.util.ArrayList;
import java.util.List;

public class TwoLayerPartitioner {

    private final int NUM_GAVETAS_FINAIS = 4; // Quantidade de partições no PostgreSQL
    private final int FINE_GRID_SIZE = 10;    // Layer 1: Grade fina de 10x10 (100 células)

    // MÉTODO 1: Cria a grade baseada nos dados (Data-Driven)
    public ResultadoParticionamento processar(List<String> wkts) throws Exception {
        WKTReader reader = new WKTReader();
        List<Geometry> geometrias = new ArrayList<>();
        Envelope globalEnv = new Envelope();

        // 1. Lê os dados e acha a "Caixa Global"
        for (String wkt : wkts) {
            Geometry geom = reader.read(wkt);
            geometrias.add(geom);
            globalEnv.expandToInclude(geom.getEnvelopeInternal());
        }

        // 2. LAYER 1: Cria a malha fina e calcula o peso (contagem)
        double cellWidth = globalEnv.getWidth() / FINE_GRID_SIZE;
        double cellHeight = globalEnv.getHeight() / FINE_GRID_SIZE;
        
        List<Geometry> fineCells = new ArrayList<>();
        int[] weights = new int[FINE_GRID_SIZE * FINE_GRID_SIZE];

        GeometryFactory factory = new GeometryFactory();
        for (int i = 0; i < FINE_GRID_SIZE; i++) {
            for (int j = 0; j < FINE_GRID_SIZE; j++) {
                double minX = globalEnv.getMinX() + (i * cellWidth);
                double maxX = minX + cellWidth;
                double minY = globalEnv.getMinY() + (j * cellHeight);
                double maxY = minY + cellHeight;
                
                Envelope cellEnv = new Envelope(minX, maxX, minY, maxY);
                fineCells.add(factory.toGeometry(cellEnv));
            }
        }

        // Calcula o peso de cada célula fina baseada no centroide das geometrias
        for (Geometry geom : geometrias) {
            Point centroid = geom.getCentroid();
            for (int k = 0; k < fineCells.size(); k++) {
                if (fineCells.get(k).contains(centroid)) {
                    weights[k]++;
                    break;
                }
            }
        }

        // 3. LAYER 2: Agrupa as células finas em gavetas balanceadas
        int totalGeoms = geometrias.size();
        int capacidadePorGaveta = (int) Math.ceil((double) totalGeoms / NUM_GAVETAS_FINAIS);
        
        List<ParticaoMetadata> molde = new ArrayList<>();
        Geometry gavetaAtualGeom = null;
        int pesoAtual = 0;
        int idGaveta = 1;

        for (int k = 0; k < fineCells.size(); k++) {
            Geometry cell = fineCells.get(k);
            int pesoCell = weights[k];

            if (gavetaAtualGeom == null) {
                gavetaAtualGeom = cell;
            } else {
                gavetaAtualGeom = gavetaAtualGeom.union(cell); // "Cola" as células (JTS faz a mágica)
            }
            
            pesoAtual += pesoCell;

            // Se atingiu o limite de peso, fecha a gaveta e prepara a próxima
            if (pesoAtual >= capacidadePorGaveta || k == fineCells.size() - 1) {
                WKTWriter writer = new WKTWriter();
                molde.add(new ParticaoMetadata(idGaveta, writer.write(gavetaAtualGeom)));
                
                if (idGaveta < NUM_GAVETAS_FINAIS) {
                    idGaveta++;
                    gavetaAtualGeom = null;
                    pesoAtual = 0;
                }
            }
        }

        // 4. Gera os resultados finais usando o molde criado
        return processar(wkts, molde);
    }

    // MÉTODO 2: Apenas encaixa no molde existente (Usado pela Tabela B)
    public ResultadoParticionamento processar(List<String> wkts, List<ParticaoMetadata> molde) throws Exception {
        List<ParticaoResult> resultados = new ArrayList<>();
        WKTReader reader = new WKTReader();
        
        List<Geometry> gavetas = new ArrayList<>();
        for (ParticaoMetadata meta : molde) {
            gavetas.add(reader.read(meta.getWktFronteira()));
        }

        for (String wkt : wkts) {
            Geometry geom = reader.read(wkt);
            Point centroid = geom.getCentroid();
            
            int idParticao = 1; // Fallback
            
            for (int i = 0; i < gavetas.size(); i++) {
                if (gavetas.get(i).contains(centroid)) {
                    idParticao = molde.get(i).getIdParticao();
                    break;
                }
            }
            resultados.add(new ParticaoResult(wkt, idParticao));
        }

        return new ResultadoParticionamento(resultados, molde);
    }
}