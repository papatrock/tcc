package benchmark;
import java.util.List;

public class ResultadoParticionamento {
    private List<ParticaoResult> dados;
    private List<ParticaoMetadata> grades;

    public ResultadoParticionamento(List<ParticaoResult> dados, List<ParticaoMetadata> grades) {
        this.dados = dados;
        this.grades = grades;
    }

    public List<ParticaoResult> getDados() { return dados; }
    public List<ParticaoMetadata> getGrades() { return grades; }
}