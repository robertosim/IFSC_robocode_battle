# AcaiBot

Robô que utiliza as estratégias do sample **Wall.java** como base, com novas estratégias adicionadas.

**Base:** Sample Wall.java (movimento de parede, troca de direção em cantos).
**Novas estratégias:** Rastreamento de múltiplos inimigos, seleção do alvo mais próximo, potência de tiro variável baseada na distância, limpeza de inimigos obsoletos, reação a colisões e paleta de cores temática "Açaí".

Robô para [Robocode](https://robocode.sourceforge.io/) que segue as paredes do campo de batalha no sentido horário, rastreia inimigos e atira no alvo mais próximo com potência baseada na distância.

## Funcionalidades

1. **Circulação wall-following** — Move-se ao redor do campo seguindo as paredes no sentido horário (inferior → esquerda → superior → direita).
2. **Radar contínuo** — O radar gira continuamente enquanto o robô se move.
3. **Rastreamento de inimigos** — Mantém um mapa atualizado de todos os inimigos avistados, com posição, energia e timestamp.
4. **Remoção de inimigos obsoletos** — Remove da lista inimigos que não são vistos há mais de 30 turnos.
5. **Ataque no alvo mais próximo** — Sempre atira no inimigo mais próximo da posição atual.
6. **Potência variável** — Usa potência maior (3.0) quando o inimigo está muito perto (< 150px), diminuindo progressivamente à medida que afasta.
7. **Paleta de cores "Açaí"** — Cores temáticas inspiradas no açaí brasileiro:
   - Corpo: Açaí escuro (#46295A)
   - Canhão: Açaí clássico (#5F3C4F)
   - Radar: Suco de açaí (#6B5366)
   - Balas: Verde
   - Arco de varredura: Suco de açaí

## Como funciona

### Movimento
O robô identifica inicialmente qual parede está mais próxima e define uma direção de movimento paralela a essa parede. Em cada turno, ele gira o corpo para alinhar ao rumo da parede atual. Quando chega a um canto, ele muda para a próxima parede no sentido horário.

### Alvo e tiro
- Busca o inimigo mais próximo na lista de rastreamento.
- Calcula o ângulo para o canhão girar até o alvo.
- Ajusta a potência do tiro baseado na distância:
  - < 150px: potência 3.0 (colocado)
  - < 300px: potência 2.5 (perto)
  - < 500px: potência 2.0 (médio)
  - >= 500px: potência 1.0 (longe)
- Limita a potência máxima baseada na energia do inimigo (nunca causa overkill).
- Só dispara quando o canhão está alinhado (±5°) e o cano está frio.

### Eventos
- **onScannedRobot**: Atualiza posição, energia e timestamp do inimigo na lista.
- **onRobotDeath**: Remove o registro do inimigo morto imediatamente.
- **onHitRobot**: Se colidir com outro robô, recua se ele estiver à frente ou avança se estiver atrás, mantendo o movimento.

## Parâmetros ajustáveis

| Parâmetro | Descrição | Padrão |
|-----------|-----------|--------|
| `MARGEM_PAREDE` | Distância das bordas que dispara troca de parede | 50 pixels |
| `TURNS_OBSOLETO` | Turnos sem avistar antes de remover da lista | 30 turnos |
| `TOLERANCIA_CANHAO` | Tolerância em graus para alinhamento do canhão | 5° |
| `DISTANCIA_MOVIMENTO` | Distância a frente a cada turno | 200 pixels |

## Estrutura de dados

### `InfoInimigo`
Classe interna que armazena as informações de um inimigo rastreado:
- `nome` — Nome do robô inimigo (chave única)
- `x`, `y` — Posição absolutas no campo
- `energia` — Nível de energia atual
- `ultimoAvistamento` — Turno do último avistamento

### `inimigos`
`Map<String, InfoInimigo>` — HashMap nome → InfoInimigo para busca/inserção O(1) e evitar duplicatas.

## Licença

Este código segue a [Licença Pública do Eclipse v1.0](https://www.eclipse.org/legal/epl-v10.html).

---


### Desenvolvido pela Equipe Açaí (Semestre 1 - Engenharia de Telecomunicações - IFSC)

- Claudio Roberto Simões Rodrigues
- Julia Gabriela Nunes de Melo
- Camily Stupp Moreira

### Contato

| Canal | Contato |
|-------|---------|
| E-mail | robsimoes@gmail.com |
| WhatsApp | +55 (48) 99679-3828 |
| LinkedIn | linkedin.com/in/robertosim |
=======
