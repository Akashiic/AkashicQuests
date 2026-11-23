# AkashaQuestStats — top de quests no holograma

Plugin Bukkit para o servidor **Crucible** (1.7.10) que lê o progresso do **BetterQuesting** direto do
servidor e publica o ranking como placeholders do **HolographicDisplays 2.x** (testado contra a API da 2.2.6).

* Vai em `plugins/` do **mesmo servidor que roda o BetterQuesting** (o forbidden-1). Não vai no cliente.
* Não precisa de PlaceholderAPI nem de banco de dados.
* Sem HolographicDisplays o plugin continua funcionando (comando `/aqstats`), só não cria placeholders.

## Instalação

1. Copie `dist/plugins/AkashaQuestStats-1.0.0.jar` para `plugins/` do servidor do BQ.
2. Reinicie o servidor. Aparece `plugins/AkashaQuestStats/config.yml`.
3. Crie o holograma (exemplo abaixo) e confira com `/aqstats`.

## Placeholders (HolographicDisplays)

| Placeholder | Valor |
| --- | --- |
| `{aq_top1}` … `{aq_top10}` | Linha pronta: `1. Fulano - 120` (formato em `format.line`) |
| `{aq_top1_name}` … | Nome do jogador na posição |
| `{aq_top1_count}` … | Quests concluídas pelo jogador na posição |
| `{aq_total_quests}` | Quests no livro |
| `{aq_players}` | Jogadores com pelo menos uma quest concluída |
| `{aq_total_completed}` | Soma de todas as conclusões |

O número de posições segue `top-size` (padrão 10, até 100). Posição vazia mostra `format.empty-name` /
`format.empty-count`.

> No HolographicDisplays 2.x os placeholders são **globais** (o mesmo texto para todo mundo). Por isso não há
> "suas quests" por jogador no holograma; para ver o próprio número use `/aqstats <seu nome>`.

### Exemplo de holograma

```
/hd create topquests &6&lTOP QUESTS
/hd addline topquests &7{aq_total_quests} quests no livro
/hd addline topquests {aq_top1}
/hd addline topquests {aq_top2}
/hd addline topquests {aq_top3}
/hd addline topquests {aq_top4}
/hd addline topquests {aq_top5}
```

Ou com colunas próprias: `/hd addline topquests &e#1 &f{aq_top1_name} &8» &a{aq_top1_count}`.

## Comandos

| Comando | |
| --- | --- |
| `/aqstats` ou `/aqstats top` | Mostra o top no chat |
| `/aqstats <jogador>` | Quests concluídas e posição do jogador |
| `/aqstats reload` | Relê o `config.yml`, registra os placeholders de novo e recalcula (permissão `akashaqueststats.admin`, padrão OP) |

## Configuração (`plugins/AkashaQuestStats/config.yml`)

| Chave | Padrão | |
| --- | --- | --- |
| `refresh-seconds` | `60` | A cada quantos segundos o ranking é recalculado (mínimo 5) |
| `top-size` | `10` | Quantas posições viram placeholders |
| `hide-ops` | `false` | Esconde do top quem o BQ marca como OP |
| `hidden-players` | `[]` | Nomes que nunca aparecem no top (continuam nos totais) |
| `format.line` | `&e{pos}. &f{name} &7- &a{count}` | `{pos}`, `{name}`, `{count}`, `{total}`; cores com `&` |

## Como conta

* Uma quest conta para o jogador quando o BetterQuesting a considera **concluída agora**
  (`IQuest.isComplete`). Quest repetível que voltou a ficar disponível deixa de contar até ser feita de novo;
  quest resetada por `/bq_admin reset` deixa de contar.
* Conta todas as quests do banco (inclusive ocultas e secretas). Conclusões feitas em party contam para
  cada membro que o BQ marcou como concluído.
* O nome vem do cache de nomes do próprio BQ (funciona para jogadores offline); sem ele, do Bukkit.
* Empate: ordem alfabética do nome.
* O cálculo roda na thread do servidor (o BQ não é thread-safe), a cada `refresh-seconds`; o holograma só lê
  o resultado guardado (atualiza a cada 5 s). Se um cálculo passar de 50 ms, o plugin avisa uma vez no log.

## Compilar

```bash
./build.sh   # baixa as libs (SHA-256 fixo), compila em Java 8, roda os testes e gera build/AkashaQuestStats-<versão>.jar
```

Precisa de JDK 17+ (o CI usa 21). O jar sai sempre igual para o mesmo código (data fixa); o CI confere que
`dist/plugins/` bate com o código.

## O que foi verificado e o que não foi

* **Testado:** regras do ranking (ordem, empates, ocultos, totais) e a ponte por reflexão com o
  HolographicDisplays, com uma `HologramsAPI` de teste com as assinaturas da 2.2.6 (`PluginTests`).
* **Testado contra o BetterQuesting 3.8.79 real** (servidor integrado do cliente de testes,
  `mod/devtest/ranking.txt`): 0 → 4 quests depois de `/bq_admin complete`, 4 → 3 depois de `/bq_admin reset`,
  nome vindo do cache do BQ.
* **Compilado contra a API Bukkit do próprio Crucible** (código-fonte do repositório CrucibleMC/Crucible), além da
  spigot-api 1.8.8 usada no build: todas as chamadas existem no servidor.
* **Não testado:** um servidor Crucible real com o HolographicDisplays 2.2.6 instalado (o jar do Crucible e o do
  HD 2.2.6 não estavam acessíveis aqui). Na primeira subida, confira no log
  `Registered N HolographicDisplays placeholders` e rode `/aqstats`.
