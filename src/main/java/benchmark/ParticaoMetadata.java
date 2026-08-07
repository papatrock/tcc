package benchmark;

public class ParticaoMetadata {
    private int idParticao;
    private String wktFronteira;

    public ParticaoMetadata(int idParticao, String wktFronteira) {
        this.idParticao = idParticao;
        this.wktFronteira = wktFronteira;
    }

    public int getIdParticao() { return idParticao; }
    public String getWktFronteira() { return wktFronteira; }
}