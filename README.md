# TáChegando — aplicativo Android e contratos do MVP

Kotlin Multiplatform + Compose para conectar consumidores a vendedores itinerantes com rota, localização e fila automática de interesses. Este repositório contém o aplicativo e seus serviços de apoio. **Não é o repositório do site público e não executa deploy do site.**

Estado: implementação e testes locais aprovados; integração remota e jornada real ainda pendentes. Não há APK atual distribuído por este repositório. APKs antigos são evidências locais de versões anteriores e não representam o código desta publicação.

## Arquitetura

| Diretório | Responsabilidade |
|---|---|
| `KotlinProject/composeApp/src/commonMain` | Compose, navegação, domínio, Auth, catálogo, rotas, interesse e reconciliação |
| `KotlinProject/composeApp/src/androidMain` | Google Maps, localização em uso, notificações e Firebase Messaging |
| `KotlinProject/composeApp/src/commonTest` | Regressões de identidade, percurso, fila, deduplicação, rede e contratos |
| `KotlinProject/composeApp/src/iosMain`, `KotlinProject/iosApp` | Estrutura iOS existente; Maps/GPS ainda parciais, não produto iOS homologado |
| `KotlinProject/composeApp/src/wasmJsMain` | Target Wasm existente; não substitui o site público |
| `supabase/migrations` | Schema canônico, RLS/grants, RPCs, provisionamento e outbox |
| `supabase/legacy_migrations` | Histórico anterior arquivado; **não aplicar junto das migrations canônicas** |
| `supabase/tests`, `scripts/sql` | Segurança e contratos em PostgreSQL isolado; Auth shim somente de teste |
| `auth-portal` | Confirmação, reenvio e recuperação de e-mail; serviço independente do site público |
| `scripts/push` | Worker privado e adapters PostgREST/FCM HTTP v1 |
| `scripts/integration`, `scripts/performance` | Runner com guardas e ferramentas opcionais de QA; não executar escrita remota por padrão |
| `docs` | Contratos de integração, preparação de ambiente e limitações conhecidas |

Dependências seguem Presentation → Domain ← Data. Auth é a fonte única de identidade; `vendors.id` e `vendors.user_id` são IDs diferentes. Realtime invalida snapshots; polling mantém reconciliação. Interesse não é pedido, reserva, venda ou garantia de estoque e não exige aceite individual.

## Pré-requisitos

- Android Studio com suporte a AGP 8.13.2, SDK Platform 36 e Build Tools 36.
- JDK 21 configurado em `JAVA_HOME`. O Gradle Wrapper 8.14.3 está incluído; não precisa de Gradle global.
- Android mínimo API 24; target API 36. Para testar Maps/FCM, usar aparelho com Google Play Services compatível.
- Node.js 22+ para portal, worker e testes JavaScript.
- PostgreSQL 17 com PostGIS para a suíte descartável local. `initdb` deve rodar como usuário comum, nunca root.
- iOS requer macOS/Xcode; não é necessário para a entrega Android.

Abra `KotlinProject/` no Android Studio. Configure o SDK local pela IDE ou em `KotlinProject/local.properties`, sempre ignorado pelo Git. Não incluir SDK, JDK, caches ou dependências instaladas no repositório.

## Configuração por ambiente

```bash
cp KotlinProject/config/development.properties.example KotlinProject/config/development.properties
cp KotlinProject/config/homologation.properties.example KotlinProject/config/homologation.properties
```

Preencha os arquivos locais sem versioná-los. Gradle lê `config/<ambiente>.properties`, variáveis de ambiente ou propriedades `-P`; **não carrega `.env` automaticamente**. Os exemplos têm somente valores públicos ou vazios.

| Campo | Uso |
|---|---|
| `SUPABASE_URL` | URL do projeto autorizado |
| `SUPABASE_PUBLISHABLE_KEY` | Chave pública do cliente, nunca service-role/secret |
| `GOOGLE_MAPS_API_KEY` | Chave cliente Maps com restrições de API, pacote e certificado |
| `INTEREST_PUSH_CONTRACTS_INSTALLED` | `false` até confirmar instalação dos contratos de backend |
| `FIREBASE_APPLICATION_ID`, `FIREBASE_PROJECT_ID`, `FIREBASE_API_KEY`, `FIREBASE_SENDER_ID` | Configuração pública Firebase; quatro campos necessários para habilitar push |

Homologação usa `org.example.project.homologation`, assinatura local Debug e o projeto QA autorizado `vhnkezwjknwpddqhlnfk`. Produção deve ter projeto/destinos deliberadamente definidos, pacote definitivo, chaves restritas e assinatura release. O portal e worker atuais possuem guardas restritas a QA; produção exige adaptação revisada, não basta trocar uma URL.

Não há credenciais de assinatura neste repositório. Release exige `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` fornecidos por armazenamento seguro. Não publicar keystores, senhas ou credenciais de servidor.

## Executar Android

Depois de configurar JDK/SDK e ambiente local, compile sem gerar APK:

```bash
cd KotlinProject
./gradlew -Papp.environment=homologation :composeApp:compileHomologationKotlinAndroid
```

Para executar pelo Android Studio, selecione a variante `homologation`, forneça `-Papp.environment=homologation` na configuração Gradle e escolha um dispositivo autorizado. A IDE empacota/instala a variante quando você solicita Run. Esta publicação não iniciou dispositivos nem gerou APK.

O script `./scripts/gradlew-local.sh` na raiz também executa o wrapper, usando `JAVA_HOME` ou o JDK 21 local quando disponível. O JDK não é distribuído.

## Supabase QA e cadastro iniciado no site

O site e Android devem apontar para o mesmo projeto Supabase. Cadastro → confirmação no portal HTTPS → login Android com e-mail/senha; nenhuma transferência de sessão do navegador é necessária.

1. Ler [o contrato web](docs/site-app-integration-2026-11-11/CONTRATO_WEB.md) e o schema do formulário.
2. Executar apenas a auditoria de metadados preparada antes de decidir mudanças remotas.
3. Revisar o histórico instalado e aprovar separadamente as migrations necessárias. **Não rodar db push indiscriminadamente, não reaplicar migrations e não executar seed em QA/produção.**
4. A migration `20261111135508_web_app_identity_and_discovery.sql` depende dos contratos RC1 `20261104210040_interest_push_rc1.sql` e anteriores. Todas estão incluídas; existência no Git não comprova instalação remota.
5. O trigger novo só cria o perfil básico de cadastros futuros. Vendedor é concluído por `complete_my_profile`, com e-mail confirmado, sessão ativa, identidade própria e proteção contra duplicidade. Contas/vendedores existentes não são alterados por backfill.
6. O app novo exige as novas RPCs. Perfil incompleto/consulta falha não são tratados como consumidor por conveniência.

`supabase/seed.sql` e o bootstrap Auth contêm **somente fixtures sintéticas para banco descartável**. O bootstrap não é o serviço Supabase Auth; nunca executar contra projeto hospedado.

## Testes locais

```bash
# Na raiz; não empacota APK
./scripts/gradlew-local.sh -Papp.environment=homologation \
  :composeApp:testDebugUnitTest :composeApp:testHomologationUnitTest \
  :composeApp:compileHomologationKotlinAndroid :composeApp:lintHomologation

node --test scripts/integration/guards.test.mjs scripts/integration/resume.test.mjs \
  scripts/push/interest-dispatcher.test.mjs scripts/push/adapters.test.mjs

cd auth-portal
npm ci
npm test
npm run check
```

PostgreSQL descartável, na raiz, sem parâmetros de conexão remota:

```bash
# macOS: instalar PostgreSQL 17 e PostGIS; o caminho padrão é Homebrew.
python3 scripts/test-local-database.py
# Outro sistema: apontar TCH_PG_BIN para o diretório de binários PostgreSQL 17.
# TCH_PG_BIN=/caminho/pg17/bin python3 scripts/test-local-database.py
python3 scripts/push/generate-projection-tests.py --check
```

O harness inicia cluster temporário via socket Unix sem TCP, aplica migrations e fixtures sintéticas, testa RLS/concorrência e remove o cluster ao terminar. Testes Node usam servidores loopback e podem exigir permissão local de rede. O runner remoto exige opt-in explícito de escrita; não executá-lo como teste automático de instalação.

## Google Maps e localização

Configurar a chave somente no arquivo local/secret store. Conferir Maps SDK for Android habilitado, faturamento e restrições por pacote/SHA-1 da variante. A chave cliente nunca deve ser confundida com credencial administrativa.

O app possui mapa real, marcadores/polylines e estados de loading/offline/timeout/retry. **O problema anterior de mapa sem tiles ainda não tem causa final comprovada.** Compilar não valida Google Cloud, Play Services ou renderização.

GPS é publicado durante uso da jornada. Não há serviço de rastreamento contínuo em segundo plano. Descoberta filtra no servidor antes da paginação e exige posição recente/precisa, rota ativa, corredor e posição elegível. A localização do consumidor usada na consulta não é publicada.

## Firebase, portal e worker

Firebase QA deve corresponder ao pacote da variante. Sem os quatro campos públicos e contratos confirmados, push permanece desativado; a fila continua independente dele. POST_NOTIFICATIONS no Android 13+ e configurações do canal controlam os avisos.

Portal, sem executar deploy:

```bash
cd auth-portal
npm ci
cp .env.example .env
# Preencher configuração pública e COOKIE_ENCRYPTION_KEY privada de 32 bytes base64.
npm start
```

Para inspeção local sem Auth POSTs, consultar `auth-portal/README.md`. O portal requer DNS/HTTPS em `app.tachegandoapp.com.br`, redirects/templates Auth e SMTP configurados; templates e exemplos de nginx/Docker estão em `auth-portal/deploy`. GET não consome link nem dispara resend; POST exige CSRF. Não presumir entrega de e-mail a partir de HTTP 200.

Worker:

```bash
# Copiar scripts/push/.env.example para arquivo privado e carregar no ambiente do servidor.
# Manter TCH_PUSH_EXECUTE=0 até contratos, credenciais e autorização estarem confirmados.
node scripts/push/worker.mjs --once
```

O comando acima recusa execução sem configuração autorizada; não é teste de envio. Após autorização de operação QA, fornecer no servidor SUPABASE_SERVER_KEY, conta de serviço FCM em arquivo privado e os campos do exemplo, e habilitar TCH_PUSH_EXECUTE=1. Nunca pôr esses segredos no APK/browser/Git. O worker não expõe endpoint público. Aceitação HTTP v1 pelo provedor não comprova entrega no Android.

## Evidências e pendências

Validação local em 11/11/2026: 174 testes Debug, 174 Homologation, 28 runner/push, 29 portal; 46 assertions SQL novas, regressões RC1 e concorrência de perfil/interesse aprovadas. Compilação e lint passaram com warnings documentados. Os testes comuns se repetem nas variantes; mocks/transportes controlados não são homologação real de Auth/SMTP/FCM/Maps.

Pendentes: revisão/instalação remota dos contratos; SMTP/DNS/portal; Firebase e operação do worker; diagnóstico Maps; integração do repositório web; jornada real em dois Androids; exclusão efetiva de conta, privacidade final, ambiente e assinatura de produção/Google Play. A RPC de exclusão atual somente registra o pedido, não apaga a conta.

Leia [o relatório da integração](docs/site-app-integration-2026-11-11/RELATORIO.md) e [o portão RC1](docs/rc1/QUALITY_GATE.md). Evidências brutas locais, screenshots, logs, dumps, usuários reais, APKs e caminhos privados de configuração não fazem parte da publicação. IDs de QA em exemplos públicos podem ser substituídos por IDs sintéticos. Publicar o código não publica o aplicativo na Play Store nem altera o site existente.
