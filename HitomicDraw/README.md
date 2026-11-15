# HitomicDraw

Plugin de **pixel art em mapas** para Paper (Java 25, Maven). O jogador desenha dentro do Minecraft,
o desenho vira um mapa vanilla real e pode ser enviado para avaliação por administradores.

> **Importante:** este ZIP contém o **código-fonte**. O `.jar` não está incluso porque ele precisa ser
> compilado com JDK 25 + API do Paper 26.3. Veja "Como gerar o JAR" abaixo (leva ~1 minuto).

## Como gerar o JAR

### Opção 1 - Local (JDK 25 + Maven)
```
mvn clean package
```
O arquivo sai em `target/HitomicDraw-1.0.0.jar`. Copie para a pasta `plugins/` do servidor.
(`build.sh` / `build.bat` fazem a mesma coisa.)

### Opção 2 - GitHub Actions (sem instalar nada)
1. Crie um repositório no GitHub e envie todo o conteúdo desta pasta.
2. Abra a aba **Actions** > **Build HitomicDraw** (roda sozinho a cada push, ou clique em *Run workflow*).
3. Baixe o artefato **HitomicDraw-jar**.

### Se o Maven não achar a API do Paper
No `pom.xml`, a propriedade `paper.api.version` (padrão `26.3-R0.1-SNAPSHOT`) precisa ser igual
ao nome da versão publicada em https://repo.papermc.io (pasta `io/papermc/paper/paper-api`).
Também confira `api-version` em `src/main/resources/plugin.yml`.

## Uso (jogadores)
| Comando | O que faz |
|---|---|
| `/desenhar criar` | Cria um desenho e entrega um mapa em branco |
| `/desenhar abrir [id]` | Abre o editor (segure o mapa, informe o id, ou use se só tiver um desenho) |
| `/desenhar receber [id]` | Recupera o mapa de um desenho seu que ainda não foi enviado |
| `/desenhar meus` | Lista seus desenhos e resultados |

### Editor
A grade é **9 × 6** e ocupa os 54 slots do baú, então o editor tem duas telas:

* **Tela de desenho** - cada slot é um pixel.
  * Clique esquerdo: usa a ferramenta atual (pincel / borracha / balde)
  * Shift + esquerdo: copia a cor do pixel (conta-gotas)
  * Clique direito: abre o **painel de ferramentas**
  * Tecla `Q` sobre a grade: desfazer · tecla `F`: refazer
* **Painel de ferramentas** - paleta (27 cores), pincel, borracha, balde, desfazer, refazer,
  limpar e finalizar. *Limpar* e *Finalizar* pedem confirmação (clicar duas vezes).

O desenho é convertido para 128 × 128 e o Minecraft usa a cor mais próxima da paleta de mapas.

### Enviar
1. Em **Finalizar**, o mapa fica bloqueado para edição.
2. **Solte o mapa no chão (Q)**: o plugin registra jogador + horário, remove o mapa e cria a participação.

## Administração (`hitomicdraw.admin`)
| Comando | O que faz |
|---|---|
| `/desenhar avaliacoes [pendentes\|aprovadas\|rejeitadas\|todas] [pagina]` | Lista participações |
| `/desenhar ver <id>` | Abre a tela de avaliação (jogador, data, mapa, aprovar/rejeitar) |
| `/desenhar aprovar <id>` | Aprova |
| `/desenhar rejeitar <id> [motivo]` | Rejeita (motivo opcional) |
| `/desenhar recarregar` | Recarrega o `config.yml` |

Na tela de avaliação, clicar no mapa entrega uma **cópia de visualização** (em tamanho real);
ela some ao ser dropada. O jogador é avisado ao avaliar (ou no próximo login, se estiver offline).

## Permissões
* `hitomicdraw.use` (padrão: todos) - criar, editar e enviar desenhos
* `hitomicdraw.admin` (padrão: op) - avaliar e administrar

## Arquivos de dados (`plugins/HitomicDraw/`)
* `config.yml` - opções e todas as mensagens
* `drawings.yml` - desenhos: dono, mapa, estado e a cor de cada pixel (`#RRGGBB`)
* `submissions.yml` - participações e resultados das avaliações

A gravação é feita em thread separada, de forma atômica (arquivo temporário + rename).
Se um arquivo estiver corrompido, ele é preservado como `*.corrompido-<hora>` e o plugin
inicia sem sobrescrevê-lo.

## Proteções
* O estado de cada desenho vive no servidor; o item do mapa só carrega o id. Alterar o item não ajuda em nada.
* Só o dono edita, só desenhos em rascunho são editáveis, e só o dono envia.
* Um desenho só vira participação uma vez; cópias de mapas já enviados são destruídas ao dropar.
* Bloqueados: cópia na mesa de cartografia e clonagem na bancada.
* Todos os cliques e arrastos nas GUIs são cancelados; itens decorativos marcados são removidos
  do inventário se escaparem (ex.: criativo).

## Como o mapa persiste
O desenho fica em `drawings.yml`; o plugin liga um renderizador ao mapa real ao iniciar e quando o
Minecraft carrega o mapa. Por isso o plugin precisa estar instalado para o desenho aparecer
(se for removido, o mapa volta a ser um mapa vazio).
