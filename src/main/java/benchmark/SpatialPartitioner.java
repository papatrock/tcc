package benchmark;
import java.util.List;

public interface SpatialPartitioner {
    ResultadoParticionamento processar(List<String> wkts) throws Exception;
    ResultadoParticionamento processar(List<String> wkts, List<ParticaoMetadata> molde) throws Exception;
}