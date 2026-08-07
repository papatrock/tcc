package benchmark;
import java.util.List;

public interface SpatialPartitioner {
    ResultadoParticionamento processar(List<String> wkts) throws Exception;
}