package benchmark;

public class ParticaoResult {
    private String wkt;
    private int idParticao;

    public ParticaoResult(String wkt, int idParticao) {
        this.wkt = wkt;
        this.idParticao = idParticao;
    }

    public String getWkt() { return wkt; }
    public int getIdParticao() { return idParticao; }
}