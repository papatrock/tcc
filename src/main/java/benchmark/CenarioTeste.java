package benchmark;

public class CenarioTeste {
    private String nomeCenario;
    private String tabelaA;
    private String tabelaB;
    private String queryExtracaoA;
    private String queryExtracaoB;
    private String tipoGeometriaA; // ex: MultiLineString
    private String tipoGeometriaB; // ex: MultiPolygon

    public CenarioTeste(String nomeCenario, String tabelaA, String tabelaB,
                        String queryExtracaoA, String queryExtracaoB,
                        String tipoGeometriaA, String tipoGeometriaB) {
        this.nomeCenario = nomeCenario;
        this.tabelaA = tabelaA;
        this.tabelaB = tabelaB;
        this.queryExtracaoA = queryExtracaoA;
        this.queryExtracaoB = queryExtracaoB;
        this.tipoGeometriaA = tipoGeometriaA;
        this.tipoGeometriaB = tipoGeometriaB;
    }

    public String getNomeCenario() { return nomeCenario; }
    public void setNomeCenario(String nomeCenario) { this.nomeCenario = nomeCenario; }

    public String getTabelaA() { return tabelaA; }
    public void setTabelaA(String tabelaA) { this.tabelaA = tabelaA; }

    public String getTabelaB() { return tabelaB; }
    public void setTabelaB(String tabelaB) { this.tabelaB = tabelaB; }

    public String getQueryExtracaoA() { return queryExtracaoA; }
    public void setQueryExtracaoA(String queryExtracaoA) { this.queryExtracaoA = queryExtracaoA; }

    public String getQueryExtracaoB() { return queryExtracaoB; }
    public void setQueryExtracaoB(String queryExtracaoB) { this.queryExtracaoB = queryExtracaoB; }

    public String getTipoGeometriaA() { return tipoGeometriaA; }
    public void setTipoGeometriaA(String tipoGeometriaA) { this.tipoGeometriaA = tipoGeometriaA; }

    public String getTipoGeometriaB() { return tipoGeometriaB; }
    public void setTipoGeometriaB(String tipoGeometriaB) { this.tipoGeometriaB = tipoGeometriaB; }

    @Override
    public String toString() {
        return nomeCenario + " [" + tabelaA + " x " + tabelaB + "]";
    }
}
