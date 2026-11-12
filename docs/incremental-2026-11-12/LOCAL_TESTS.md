# Validação sem Android físico ou serviços Google

Trabalho baseado no main remoto 2575587, em branch separada. As alterações locais anteriores da pasta original não foram copiadas indiscriminadamente para este PR.

## Execução local já realizada

Definir JDK 21 e ANDROID_HOME para SDK 36. Na raiz deste checkout:

```bash
./scripts/gradlew-local.sh :composeApp:compileDebugKotlinAndroid :composeApp:testDebugUnitTest :composeApp:lintDebug :composeApp:compileKotlinIosArm64 :composeApp:linkDebugTestIosSimulatorArm64
./scripts/gradlew-local.sh :composeApp:testDebugUnitTest :composeApp:compileKotlinIosArm64 :composeApp:iosSimulatorArm64Test :composeApp:lintDebug
python3 scripts/test-local-database.py
node --test scripts/integration/guards.test.mjs scripts/integration/resume.test.mjs scripts/push/interest-dispatcher.test.mjs scripts/push/adapters.test.mjs
npm test --prefix auth-portal
npm run check --prefix auth-portal
```

179 testes Android e 179 testes nativos no simulador iOS passaram, sem falhas ou skips. Os testes comuns se repetem entre os targets. O simulador iOS foi usado pelo runner KMP; não se fez teste manual de interface nem instalação Android. Não houve aparelho físico, nova conta real, e-mail real ou chamada Google Maps/FCM.

O harness SQL cria cluster PostgreSQL/PostGIS temporário via socket Unix, sem argumentos de conexão remota. Executa todas as migrations locais em banco vazio, com Auth shim e fixtures sintéticas. Isso não reaplica migrations QA. A suíte agora cobre também a migration de onboarding existente no main: confirmação/sessão, rollback, idempotência, produtos existentes e duas chamadas concorrentes inicializando o catálogo uma única vez. O Auth shim não substitui a validação de login/refresh no serviço Supabase Auth real.

## Simulação reaproveitada

A demonstração existente passa a estar acessível no onboarding de development, além da homologação que já existia. Produção e ambientes desconhecidos continuam sem essa entrada. Ela reutiliza DemoRouteSession, DemoAutomaticAttendance, DemoConsumerExperience e o redutor RouteAttendance: relógio lógico, waypoints, protocolos e eventos fictícios, sem GPS ou backend real. Testes novos comparam duas execuções iguais, reinício de sessão e cancelamento offline/reconciliação, sem aceite individual.

Para uma futura rodada Android em emulador, usar build development com configurações de cliente vazias, abrir “Demonstração”, criar perfil fictício e usar a jornada simulada. Nenhum emulador Android ou APK foi criado/instalado nesta auditoria. Imagem sem Play Store pode exercitar as regras; não é evidência de cartografia real.

Sem chave Maps, o componente existente mostra indisponibilidade, enquanto os controles/listas da simulação continuam disponíveis. Não foi implementada cartografia simulada, Canvas alternativo ou MapLibre neste PR. A demonstração antiga Android mantém seu armazenamento local de rotas; sua inicialização e restauração já têm testes de persistência/falha. Os testes novos usam armazenamento em memória para terem execução reproduzível.

## Portal sem deploy

As 29 regressões do portal usam transporte Auth controlado e validam confirmação por POST, token expirado/reutilizado, CSRF, recovery, reenvio/cooldown, cookies, retorno Android e modo inspeção. `npm run check` verifica sintaxe. Reutilizar `auth-portal/README.md`, `.env.example` e templates; nenhuma configuração remota ou deploy é necessária para essa suíte. Não afirmar que SMTP/DNS/HTTPS/redirects estão operacionais apenas por esses resultados.

## Maps: diagnóstico e alternativas

O erro confirmado NativeMap.kt:134 é sintático: caracteres literais `\\n` no código. Foi corrigido preservando o callback, o SDK e o log sem dados pessoais. Isso não explica nem comprova resolução do REQUEST_TIMEOUT/tiles vazios observado antes.

Alternativas para decisão posterior:

1. Manter Google Maps no adapter atual e desenvolver regras com a demonstração, sem configurar o serviço. É a escolha deste PR.
2. Propor uma prévia esquemática de percurso, explicitamente sem tiles, exclusivamente para desenvolvimento. Não implementada neste PR.
3. Fazer uma prova de conceito MapLibre em branch separada após aprovação, incluindo fornecedor/licença/atribuição de tiles, callbacks/câmera/marcadores e ligação iOS. Não migrar o domínio ou as telas automaticamente.

A documentação primária do MapLibre Compose informa Android/iOS/Desktop e Web Kotlin/JS, ausência de suporte Kotlin/Wasm e API ainda instável. O projeto tem wasmJs; portanto a troca não é uniforme nem necessária para validar a fila. SDK aberto também não fornece automaticamente o serviço de tiles.

Fontes: https://maplibre.org/maplibre-compose/ e https://maplibre.org/maplibre-native/docs/book/ .
