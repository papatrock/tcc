package benchmark.algoritmos;

import benchmark.ParticaoMetadata;
import benchmark.ParticaoResult;
import benchmark.ResultadoParticionamento;
import benchmark.SpatialPartitioner;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.WKTReader;

import java.util.ArrayList;
import java.util.List;

public class FixedGridPartitioner implements SpatialPartitioner {
    @Override
    public ResultadoParticionamento processar(List<String> wkts) throws Exception {
        List<ParticaoResult> resultados = new ArrayList<>();
        List<ParticaoMetadata> metadados = new ArrayList<>();

        WKTReader reader = new WKTReader();
        GeometryFactory gf = new GeometryFactory();

        // 1. Descobrir a "Caixa Global" (MBR) que envolve todos os dados
        Envelope globalEnv = new Envelope();
        List<Geometry> geometrias = new ArrayList<>();

        for (String wkt : wkts) {
            Geometry geom = reader.read(wkt);
            geometrias.add(geom);
            globalEnv.expandToInclude(geom.getEnvelopeInternal());
        }

        // calcular o centro matemático exato baseado nos limites dos dados reais
        double midX = (globalEnv.getMinX() + globalEnv.getMaxX()) / 2.0;
        double midY = (globalEnv.getMinY() + globalEnv.getMaxY()) / 2.0;

        // 3. Gerar as 4 fronteiras dinamicamente usando a JTS
        // O método toGeometry transforma uma caixa (Envelope) em um Polígono WKT
        Geometry q1 = gf.toGeometry(new Envelope(globalEnv.getMinX(), midX,globalEnv.getMinY(), midY)); // baixo esquerda
        Geometry q2 = gf.toGeometry(new Envelope(midX, globalEnv.getMaxX(), midY, globalEnv.getMaxY())); //baixo direita
        Geometry q3 = gf.toGeometry(new Envelope(globalEnv.getMinX(), midX, midY, globalEnv.getMaxY())); // cima esquerda
        Geometry q4 = gf.toGeometry(new Envelope(midX, globalEnv.getMaxX(), midY, globalEnv.getMaxY())); // cima direita

        metadados.add(new ParticaoMetadata(1, q1.toText()));
        metadados.add(new ParticaoMetadata(2, q2.toText()));
        metadados.add(new ParticaoMetadata(3, q3.toText()));
        metadados.add(new ParticaoMetadata(4, q4.toText()));

        // 4. Classificar os polígonos usando o centro dinâmico que acabamos de calcular
        for (int i = 0; i < wkts.size(); i++) {
            Geometry geom = geometrias.get(i);
            Point centroid = geom.getCentroid();
            String wkt = wkts.get(i);

            int idParticao = 0;
            if (centroid.getX() >= midX && centroid.getY() >= midY) idParticao = 1;
            else if (centroid.getX() < midX && centroid.getY() >= midY) idParticao = 2;
            else if (centroid.getX() < midX && centroid.getY() < midY) idParticao = 3;
            else if (centroid.getX() >= midX && centroid.getY() < midY) idParticao = 4;

            resultados.add(new ParticaoResult(wkt, idParticao));
        }

        return new ResultadoParticionamento(resultados, metadados);
    }
}