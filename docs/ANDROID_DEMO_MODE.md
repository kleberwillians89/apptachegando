# Android — Google Maps real e Modo Demonstração local

Entrega de 03/11/2026, branch `feature/sprint1-recuperacao-tecnica`. APK final instalado com `adb install -r`, preservando dados e demais aplicativos. Nenhum usuário ou fixture criado no Supabase; nenhuma migration, configuração Auth/SMTP, deploy ou publicação executada. RUN `mvuzgto9-1fbfa58b` com hash inalterado.

## Entrega e como abrir

- [APK performance — 2,961 MiB](../output/android-demo-mode-2026-11-03/TaChegando-performance-demo.apk).
- [Vídeo MP4 real — 1min35s](../output/android-demo-mode-2026-11-03/TaChegando-demonstracao-modo-local.mp4), H.264, 720×1616, 30fps, sem áudio; 2.030.607 bytes. Captura real por Android screenrecord; conversão de formato com mesma sequência/velocidade, sem cortes. Original preservado no mesmo diretório.
- [Resultados, metadados e hashes](../output/android-demo-mode-2026-11-03/DEMO_RESULTS.json), [checksum APK](../output/android-demo-mode-2026-11-03/TaChegando-performance-demo.apk.sha256), [checksum MP4](../output/android-demo-mode-2026-11-03/TaChegando-demonstracao-modo-local.mp4.sha256).

Na tela inicial, tocar **Explorar demonstração**. Se houver uma sessão real aberta, sair normalmente da conta e reabrir o aplicativo para chegar à tela inicial; não limpar dados nem desinstalar. **Sair da demo** retorna à entrada normal e descarta as intenções locais. Entrar no modo real continua usando o Supabase remoto.

Pacote `org.example.project.performance`, versão 1.0/code 1, mínimo API 24, target 36, R8/resource shrinking e assinatura Android Debug local existente. Certificado público SHA-1 `4A:63:58:B2:F7:19:1E:9C:70:C7:0A:77:7D:20:DC:9D:D3:86:7D:58`; SHA-256 no JSON. Não é assinatura de publicação Google Play. Hash instalado conferido igual ao APK exportado.

SHA-256 APK: `8c8ab0fe58feec4fbdc57952fa3e804781fb91b6a6bbe72c00a9c9b4cb5038e4`.

## O que foi implementado

O modo é uma rota independente dentro do aplicativo existente, disponível somente quando `environment=homologation`. Usa `ConsumerHomeScreen`, `VendorProfileScreen`, cards, tipografia e `NativeMap` existentes. Sem novo projeto, design paralelo, dependências novas ou substituição dos repositórios reais.

Uma interface `ConsumerExperience` fornece catálogo e intenções às telas. O padrão é `RemoteConsumerExperience`, que delega aos repositórios Supabase existentes. Somente a árvore Compose da demonstração recebe `DemoConsumerExperience`, com catálogo e recibos em memória. Não há fallback entre fontes. A fonte remota recusa IDs `demo-*` antes de acessar Auth/rede; os testes verificam essa proteção.

Todas as telas da demo mantêm o aviso **MODO DEMONSTRAÇÃO — Dados fictícios · intenções locais · mapa Google online**. Vendedores têm prefixo `[DEMO]`, sem fotos ou avaliações reais inventadas. “Simular intenção” devolve protocolo `DEMO-n`, rotulado como simulação, sem pedido, pagamento ou notificação. Consultar recupera o recibo na mesma sessão; reentrar após sair começa sem recibos.

A referência geográfica da demo é sintética, no centro de São Paulo. Não pede nem publica GPS do dispositivo. Os tiles são do Google Maps real e dependem do serviço/rede Google. Os cinco vendedores têm coordenadas próprias e usam o agrupamento de marcadores existente: pontos próximos podem compartilhar um agrupamento conforme o zoom. Selecionar pelo marcador abre o catálogo; também é possível selecionar pela lista/busca local.

| Vendedor fictício | Produtos de demonstração |
|---|---|
| Lanches da Praça | Sanduíche artesanal R$12,50; suco R$7,00 |
| Feira de Frutas | Maçã/kg R$6,00; banana/kg R$5,50 |
| Sorvetes do Centro | Picolé R$4,00; sorvete R$9,00 |
| Café na Rua | Café R$5,00; pão de queijo R$6,50 |
| Flores da Cidade | Buquê R$25,00; rosa R$8,00 |

## Validação efetivamente observada

| Teste | Resultado |
|---|---|
| Maps no APK instalado | Tiles, ruas e nomes de São Paulo renderizados visualmente antes da demo e no vídeo final; não é placeholder. A chave não foi impressa ou alterada. Restrições Google Cloud foram informadas pelo cliente, não auditadas no console. |
| Build Android final | BUILD SUCCESSFUL em 1m03s; 45 testes unitários aprovados, zero falhas/erros/ignorados. |
| Lint | Zero erros, 17 avisos existentes. |
| Testes novos de isolamento | Cinco casos cobrem catálogo de cinco vendedores/dez produtos, preços/coordenadas, IDs desconhecidos sem fallback, deduplicação/consulta local, limpeza entre sessões e recusa de IDs demo na fonte remota. |
| UI no emulador | 11 verificações aprovadas, incluindo produtos/preços dos cinco catálogos, simular/consultar, recuperar recibo após navegar, sair e reentrar sem estado anterior. [Evidência](../output/android-demo-mode-2026-11-03/DEMO_UI_TESTS.json). |
| Marcador → catálogo | Toque real no marcador de Flores da Cidade abriu seu catálogo; registrado no vídeo e captura. |
| Conteúdo do vídeo | Entrada, aviso de simulação, mapa Google, seleção pelo marcador, Flores/produtos/preços, intenção/consulta local, catálogos Lanches e Frutas, retorno ao mapa e saída da demo. [Timeline](../output/android-demo-mode-2026-11-03/VIDEO_TRACE.json). Os cinco catálogos foram testados fora do vídeo; três aparecem nele. |
| Isolamento do modo real | Após a gravação, login consumerA no APK final: catálogo Supabase vazio, sem `[DEMO]` ou aviso de simulação. Logout real e reabertura concluídos. [Evidência](../output/android-demo-mode-2026-11-03/REAL_MODE_ISOLATION.json). Credenciais fora da gravação. |
| Integridade/reprodução | SHA-256 do APK instalado igual ao arquivo exportado; MP4 final inteiramente decodificado sem erros; frames de mapa e confirmação local inspecionados. |
| Estabilidade | Nenhum crash/ANR observado; contagens de FATAL EXCEPTION/ANR dos processos consultados zeradas. Não é teste prolongado, dispositivo de entrada ou API mínima. |

Supabase real permanece `https://vhnkezwjknwpddqhlnfk.supabase.co`, confirmado no DEX do APK, sem dependência do Mac/localhost. Apenas sessões de login/logout do consumidor existente foram usadas para validar o modo real; não houve alteração de configurações Auth nem de dados comerciais remotos. A demonstração local não usa essas sessões.

## Arquivos alterados nesta execução

- `core/data/ConsumerExperience.kt`: contrato e adaptador remoto com proteção de IDs.
- `features/demo/DemoConsumerExperience.kt`: cinco vendedores, dez produtos e recibos locais; nenhuma integração de rede/Auth/GPS/disco.
- `features/demo/LocalConsumerExperience.kt` e `DemonstrationScreen.kt`: injeção restrita à composição da demo, aviso e saída.
- `features/consumer/ConsumerHomeScreen.kt` e `VendorProfileScreen.kt`: recebem a fonte, distinguem simulação e reutilizam o layout real. O fluxo real continua como padrão.
- `features/onboarding/OnboardingScreen.kt` e `core/navigation/AppNavigation.kt`: entrada QA e rota da demo, ausentes da navegação de produção.
- `commonTest/.../DemoExperienceTest.kt`: cinco casos novos.
- `scripts/performance/record-demo-mode.py`: captura real com verificação de textos sensíveis, original preservado e MP4 compatível. O toque de marcador usa coordenadas verificadas para o AVD 1080×2424; revisar em outro tamanho.
- Este relatório e evidências em `output/android-demo-mode-2026-11-03/`. Configurações privadas, demais alterações existentes e site institucional preservados.

Graphify foi consultado antes das mudanças e o grafo estrutural atualizado depois (759 nós/2195 relações). Consulta inicial em `docs/demo-mode/graph-query-initial.txt`; grafo em `KotlinProject/composeApp/src/graphify-out/graph.json`. As relações de navegação, telas, mapas e fontes foram conferidas no código; o grafo não foi usado como prova de integração real.

## Limites e próximos testes reais

Maps renderizado e jornada local demonstrados. Isso **não valida** intenção de compra no Supabase, vendedores reais online/offline, eventos Realtime, pagamentos, push, GPS real do aparelho ou portal/App Links. O problema anterior de posição recente no AVD não é mascarado: a demo usa referência sintética explícita, sem chamar FLP.

Dados fictícios não foram enviados ao servidor. VendorA/B do RUN continuam sem novos cadastros nesta execução. A homologação comercial real mantém seu escopo/autorização separados. iOS, telefone físico/2GB e Android API 24 não foram testados.

## Reprodução

Na raiz, mantendo o arquivo privado de homologação com a chave já configurada:

```bash
./scripts/gradlew-local.sh -Papp.environment=homologation :composeApp:assemblePerformance :composeApp:testPerformanceUnitTest :composeApp:lintPerformance --offline --no-daemon
adb -s emulator-5554 install -r KotlinProject/composeApp/build/outputs/apk/performance/composeApp-performance.apk
adb -s emulator-5554 shell am start -W -n org.example.project.performance/org.example.project.MainActivity
```

Para nova gravação, deixar a tela inicial aberta e escolher diretório sem vídeos anteriores:

```bash
python3 scripts/performance/record-demo-mode.py --serial emulator-5554 --output output/android-demo-mode-nova-captura
```
