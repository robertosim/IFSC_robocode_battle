package acaibot;

/*
 * Copyright (c) 2001-2025 Mathew A. Nelson and Robocode contributors
 * Todos os direitos reservados. Este programa e os materiais
 * acompanhantes estão disponíveis sob os termos da Licença Pública
 * do Eclipse v1.0.
 */

import robocode.AdvancedRobot;
import robocode.HitRobotEvent;
import robocode.RobotDeathEvent;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

import java.awt.Color;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * ============================================================================
 * AcaiBot
 * ============================================================================
 * Robô que:
 *   1. Circula o campo de batalha seguindo as paredes no sentido HORÁRIO.
 *   2. Nunca fica parado (sempre há um comando de movimento pendente).
 *   3. Mantém uma lista atualizada dos inimigos avistados pelo radar.
 *   4. Remove da lista os inimigos mortos e os que não são vistos há muito tempo.
 *   5. Atira sempre no inimigo mais próximo.
 *   6. Usa potência maior quando o inimigo está mais perto (para matar rápido).
 *   7. Usa a paleta de cores "Açaí".
 *
 * Estende AdvancedRobot (e não Robot) porque precisamos de:
 *   - Controle independente de corpo, canhão e radar.
 *   - Radar girando continuamente enquanto o corpo se move.
 *   - Comandos não bloqueantes (setAhead, setTurnRight, setFire, etc.)
 * ============================================================================
 */
public class AcaiBot extends AdvancedRobot {

    // =========================================================================
    // PALETA DE CORES AÇAÍ
    // =========================================================================
    // Açaí Clássico/Pó            #5F3C4F  -> RGB(95, 60, 79)
    private static final Color ACAI_CLASSICO = new Color(95, 60, 79);

    // Açaí Escuro/Saturado        #46295A  -> RGB(70, 41, 90)
    private static final Color ACAI_ESCURO   = new Color(70, 41, 90);

    // Suco de Açaí (Suvinil P403) #6B5366  -> RGB(107, 83, 102)
    private static final Color SUCO_DE_ACAI  = new Color(107, 83, 102);

    // =========================================================================
    // PARÂMETROS AJUSTÁVEIS
    // =========================================================================
    /** Distância (em pixels) das bordas que dispara a troca de parede. */
    private static final double MARGEM_PAREDE = 50;

    /** Turns sem avistar o inimigo após os quais ele é removido da lista. */
    private static final long TURNS_OBSOLETO = 30;

    /** Tolerância (em graus) para considerar que o canhão está alinhado. */
    private static final double TOLERANCIA_CANHAO = 5;

    /** Distância à frente que o robô tenta percorrer a cada turno. */
    private static final double DISTANCIA_MOVIMENTO = 200;

    // =========================================================================
    // ESTADO DE MOVIMENTO
    // =========================================================================
    /**
     * Índice da parede em que o robô está seguindo, no sentido HORÁRIO:
     *   0 = parede INFERIOR  (rumo OESTE  -> 270°)
     *   1 = parede ESQUERDA  (rumo NORTE  ->   0°)
     *   2 = parede SUPERIOR  (rumo LESTE  ->  90°)
     *   3 = parede DIREITA   (rumo SUL    -> 180°)
     */
    private int paredeAtual = 0;

    // =========================================================================
    // ESTRUTURA DE DADOS DOS INIMIGOS
    // =========================================================================

    /**
     * Guarda as informações conhecidas de um único inimigo rastreado.
     */
    private static class InfoInimigo {
        /** Nome do robô inimigo (usado como chave para não duplicar). */
        String nome;

        /** Posição X absoluta no campo. */
        double x;

        /** Posição Y absoluta no campo. */
        double y;

        /** Nível de energia atual do inimigo. */
        double energia;

        /** Turno em que o inimigo foi visto pela última vez. */
        long ultimoAvistamento;

        InfoInimigo(String nome, double x, double y, double energia, long ultimoAvistamento) {
            this.nome = nome;
            this.x = x;
            this.y = y;
            this.energia = energia;
            this.ultimoAvistamento = ultimoAvistamento;
        }
    }

    /**
     * Mapa nome -> InfoInimigo. HashMap garante busca/inserção O(1)
     * e evita duplicatas quando o mesmo inimigo é escaneado várias vezes.
     */
    private final Map<String, InfoInimigo> inimigos = new HashMap<>();

    // =========================================================================
    // RUN — laço principal
    // =========================================================================
    /**
     * Executado uma vez no início da batalha e roda até o robô morrer.
     * Configura cores, ajustes de eixos, inicializa a parede atual
     * e entra no loop principal — que sempre:
     *   - gira o radar continuamente;
     *   - se move no sentido horário;
     *   - mira e atira no inimigo mais próximo;
     *   - limpa inimigos obsoletos.
     */
    @Override
    public void run() {
        // ---- Cores ---------------------------------------------------------
        setBodyColor(ACAI_ESCURO);     // corpo: açaí escuro
        setGunColor(ACAI_CLASSICO);    // canhão: açaí clássico
        setRadarColor(SUCO_DE_ACAI);   // radar: suco de açaí
        setBulletColor(Color.green);   // balas: açaí clássico
        setScanColor(SUCO_DE_ACAI);    // arco de varredura: suco de açaí

        // ---- Ajustes de eixos ---------------------------------------------
        // Permite girar o canhão sem arrastar o corpo.
        setAdjustGunForRobotTurn(true);
        // Permite girar o radar sem arrastar o canhão.
        setAdjustRadarForGunTurn(true);

        // ---- Descobre em que parede o robô começou ------------------------
        inicializarParede();

        // ---- Loop principal ------------------------------------------------
        while (true) {
            // Radar girando continuamente (varredura infinita).
            setTurnRadarRight(Double.POSITIVE_INFINITY);

            // Movimento no sentido horário, seguindo a parede atual.
            moverSentidoHorario();

            // Mira e atira no inimigo mais próximo (se houver).
            mirarEAtirar();

            // Remove da lista inimigos obsoletos.
            limparInimigosObsoletos();

            // Aplica todos os comandos pendentes neste turno.
            execute();
        }
    }

    // =========================================================================
    // INICIALIZAÇÃO DA PAREDE
    // =========================================================================
    /**
     * Descobre qual das quatro paredes está mais perto do robô no início
     * da batalha e define {@code paredeAtual} de acordo.
     * Isso evita que ele comece "perdido" e vá na direção contrária.
     */
    private void inicializarParede() {
        double x = getX();
        double y = getY();
        double largura = getBattleFieldWidth();
        double altura  = getBattleFieldHeight();

        double distEsquerda = x;
        double distDireita  = largura - x;
        double distBaixo    = y;
        double distCima     = altura - y;

        double menor = Math.min(
                Math.min(distEsquerda, distDireita),
                Math.min(distBaixo, distCima));

        if (menor == distBaixo)         paredeAtual = 0; // inferior
        else if (menor == distEsquerda) paredeAtual = 1; // esquerda
        else if (menor == distCima)     paredeAtual = 2; // superior
        else                            paredeAtual = 3; // direita
    }

    // =========================================================================
    // MOVIMENTO — sentido HORÁRIO
    // =========================================================================
    /**
     * Segue as paredes no sentido HORÁRIO, mantendo o robô sempre em
     * movimento. O corpo aponta EXATAMENTE paralelo à parede (0/90/180/270),
     * pois no Robocode o robô não desliza ao bater na parede: se houver
     * qualquer inclinação para dentro, o {@code setAhead} fica bloqueado
     * e o robô trava parado.
     *
     * Ordem das paredes:
     *   inferior -> esquerda -> superior -> direita -> (reinicia)
     */
    private void moverSentidoHorario() {
        double x = getX();
        double y = getY();
        double largura = getBattleFieldWidth();
        double altura  = getBattleFieldHeight();

        double rumoAlvo;

        switch (paredeAtual) {
            case 0: // Parede INFERIOR, rumo OESTE (270°)
                rumoAlvo = 270;
                if (x < MARGEM_PAREDE) {           // chegou ao canto inferior-esquerdo
                    paredeAtual = 1;
                    rumoAlvo = 0;
                }
                break;

            case 1: // Parede ESQUERDA, rumo NORTE (0°)
                rumoAlvo = 0;
                if (y > altura - MARGEM_PAREDE) {  // chegou ao canto superior-esquerdo
                    paredeAtual = 2;
                    rumoAlvo = 90;
                }
                break;

            case 2: // Parede SUPERIOR, rumo LESTE (90°)
                rumoAlvo = 90;
                if (x > largura - MARGEM_PAREDE) { // chegou ao canto superior-direito
                    paredeAtual = 3;
                    rumoAlvo = 180;
                }
                break;

            case 3: // Parede DIREITA, rumo SUL (180°)
            default:
                rumoAlvo = 180;
                if (y < MARGEM_PAREDE) {           // chegou ao canto inferior-direito
                    paredeAtual = 0;
                    rumoAlvo = 270;
                }
                break;
        }

        // Calcula o giro relativo necessário para alinhar o corpo ao rumoAlvo.
        double giroCorpo = Utils.normalRelativeAngleDegrees(rumoAlvo - getHeading());
        setTurnRight(giroCorpo);

        // Sempre avança — o robô nunca fica parado.
        setAhead(DISTANCIA_MOVIMENTO);
    }

    // =========================================================================
    // ATAQUE
    // =========================================================================
    /**
     * Encontra o inimigo mais próximo na lista, gira o canhão na direção dele
     * e dispara com potência proporcional à distância (mais perto = mais forte).
     * Só atira quando o canhão está razoavelmente alinhado e o cano está frio.
     */
    private void mirarEAtirar() {
        InfoInimigo maisProximo = obterInimigoMaisProximo();
        if (maisProximo == null) return;

        double dx = maisProximo.x - getX();
        double dy = maisProximo.y - getY();
        double distancia = Math.hypot(dx, dy);

        // Ângulo absoluto (0° = norte) até o inimigo.
        double anguloAteAlvo = Math.toDegrees(Math.atan2(dx, dy));

        // Giro relativo do canhão para apontar para o alvo.
        double giroCanhao = Utils.normalRelativeAngleDegrees(anguloAteAlvo - getGunHeading());
        setTurnGunRight(giroCanhao);

        // Só dispara se o canhão já está alinhado e o cano está frio.
        if (Math.abs(giroCanhao) < TOLERANCIA_CANHAO && getGunHeat() == 0) {
            double potencia = calcularPotencia(distancia, maisProximo.energia);
            if (getEnergy() > potencia + 0.1) {
                setFire(potencia);
            }
        }
    }

    /**
     * Percorre a lista de inimigos e devolve o que estiver mais perto
     * da posição atual do robô. Retorna {@code null} se a lista estiver vazia.
     */
    private InfoInimigo obterInimigoMaisProximo() {
        InfoInimigo maisProximo = null;
        double menorDistancia = Double.MAX_VALUE;

        for (InfoInimigo inimigo : inimigos.values()) {
            double d = Math.hypot(inimigo.x - getX(), inimigo.y - getY());
            if (d < menorDistancia) {
                menorDistancia = d;
                maisProximo = inimigo;
            }
        }
        return maisProximo;
    }

    /**
     * Calcula a potência do tiro com base na distância e na energia do inimigo.
     *
     * Regras:
     *   - Mais perto  -> potência maior (mata rápido).
     *   - Mais longe  -> potência menor (economiza energia e melhora a precisão).
     *   - Nunca atira mais forte do que o necessário para matar
     *     (evita "overkill"; o dano de um tiro é 4 x potência).
     */
    private double calcularPotencia(double distancia, double energiaInimigo) {
        double potencia;

        if      (distancia < 150) potencia = 3.0; // colado
        else if (distancia < 300) potencia = 2.5; // perto
        else if (distancia < 500) potencia = 2.0; // médio
        else                      potencia = 1.0; // longe

        // Limita para não gastar energia demais contra um inimigo já fraco.
        potencia = Math.min(potencia, energiaInimigo / 4.0 + 0.1);

        // Garante o intervalo válido do Robocode (0.1 a 3.0).
        return Math.max(0.1, Math.min(3.0, potencia));
    }

    // =========================================================================
    // LIMPEZA
    // =========================================================================
    /**
     * Remove da lista os inimigos que não são vistos há mais de
     * {@code TURNS_OBSOLETO} turnos. Isso evita que o robô atire em
     * posições desatualizadas (por exemplo, de robôs que saíram do radar).
     */
    private void limparInimigosObsoletos() {
        long agora = getTime();
        Iterator<Map.Entry<String, InfoInimigo>> it = inimigos.entrySet().iterator();
        while (it.hasNext()) {
            InfoInimigo info = it.next().getValue();
            if (agora - info.ultimoAvistamento > TURNS_OBSOLETO) {
                it.remove();
            }
        }
    }

    // =========================================================================
    // EVENTOS
    // =========================================================================

    /**
     * Chamado sempre que o radar detecta um robô inimigo.
     * Calcula as coordenadas absolutas dele, e insere/atualiza na lista.
     */
    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        // Ângulo absoluto (do norte) até o inimigo.
        double anguloAbsoluto = getHeading() + e.getBearing();

        // Converte distância + ângulo em coordenadas X,Y absolutas.
        double inimigoX = getX() + Math.sin(Math.toRadians(anguloAbsoluto)) * e.getDistance();
        double inimigoY = getY() + Math.cos(Math.toRadians(anguloAbsoluto)) * e.getDistance();

        InfoInimigo info = inimigos.get(e.getName());

        if (info == null) {
            // Primeira vez que avistamos este inimigo: cria registro novo.
            inimigos.put(e.getName(),
                    new InfoInimigo(e.getName(), inimigoX, inimigoY, e.getEnergy(), getTime()));
        } else {
            // Já conhecíamos: só atualiza posição, energia e timestamp.
            info.x = inimigoX;
            info.y = inimigoY;
            info.energia = e.getEnergy();
            info.ultimoAvistamento = getTime();
        }
    }

    /**
     * Chamado quando um robô morre. Remove o registro dele da lista
     * imediatamente — o robô não deve mais gastar tiros nem tempo com ele.
     */
    @Override
    public void onRobotDeath(RobotDeathEvent e) {
        inimigos.remove(e.getName());
    }

    /**
     * Chamado quando o robô colide com outro. Para não ficar preso:
     *   - Se o outro estiver à frente (bearing entre -90 e 90), recua.
     *   - Se estiver atrás, avança.
     * Isso mantém o robô em movimento e o descola do oponente.
     */
    @Override
    public void onHitRobot(HitRobotEvent e) {
        if (e.getBearing() > -90 && e.getBearing() < 90) {
            setBack(60);
        } else {
            setAhead(60);
        }
    }
}