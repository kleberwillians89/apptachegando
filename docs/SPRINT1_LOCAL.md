# Sprint 1 — desenvolvimento local

Este checkout prepara uma infraestrutura **nova**. Não restaura usuários antigos e não faz upgrade do schema legado. Nenhuma etapa abaixo exige acesso ao Supabase antigo. A baseline não deve ser aplicada a um banco com dados sem um plano separado.

## Preservação

Branch: `feature/sprint1-recuperacao-tecnica`. O estado anterior foi inventariado e o patch salvo fora do repositório em `/private/tmp/tachegando-sprint1-baseline`. Alterações anteriores em ícones, RouteConfigScreen, Xcode e investor-hub foram preservadas. Os cinco SQLs antigos estão intactos em `supabase/legacy_migrations`; somente os dois SQLs canônicos estão na sequência ativa.

O vínculo antigo da CLI foi retirado de `supabase/.temp` e preservado em `.local/legacy-supabase-link`, ignorado pelo Git. Não executar `link`, `db push`, `--linked`, `--db-url`, deploy ou comandos de publicação durante esta etapa local.

## Java, Android e iOS

JDK 21; Gradle Wrapper 8.14.3; Android Gradle Plugin 8.13.2 (D8/R8 compatível com Kotlin 2.3); Android API 36 e Build Tools 35.0.0. O repositório não fixa mais um caminho pessoal de Java. Configure JAVA_HOME na máquina, Android SDK com ANDROID_HOME ou `KotlinProject/local.properties` (ignorado). O caminho sdk.dir foi corrigido apenas para esta máquina. Não comitar esse arquivo.

Da raiz:

```bash
./scripts/gradlew-local.sh :composeApp:assembleDebug :composeApp:testDebugUnitTest
./scripts/gradlew-local.sh :composeApp:compileKotlinIosArm64 :composeApp:compileKotlinIosSimulatorArm64
./scripts/gradlew-local.sh :composeApp:iosSimulatorArm64Test
```

O wrapper local usa JAVA_HOME existente, tenta o JDK 21 empacotado, depois a instalação macOS. Não instala ferramentas nem altera o shell. Xcode completo e runtime de simulador são necessários para link/testes iOS. Compilar Kotlin/Native não comprova mapas/GPS iOS: esses adapters continuam placeholders.

Material3 resolve kotlinx-datetime 0.7.1, enquanto Supabase 3.1.2 ainda usa a ABI antiga de Instant. Foi selecionado o artefato oficial `0.7.1-0.6.x-compat` para os targets, com kotlinx-serialization 1.9.0. Desugaring Java também foi habilitado para java.time nos Android API 24–25. Esse ajuste preserva o SDK atual; não é atualização geral de Supabase. Reavaliar o pin quando esse SDK for atualizado. [Orientação oficial Kotlin](https://github.com/Kotlin/kotlinx-datetime#deprecation-of-instant).

## Configuração pública por ambiente

Escolha `development`, `homologation` ou `production`, por `-Papp.environment=...` ou APP_ENVIRONMENT. A ordem dos valores é: propriedade Gradle → variável de ambiente → `KotlinProject/config/<ambiente>.properties`.

Copie o arquivo `<ambiente>.properties.example` para `<ambiente>.properties` e preencha somente os valores do ambiente autorizado. Os arquivos reais são ignorados. `.env.example` é referência; Gradle **não carrega .env automaticamente**.

| Campo | Uso |
|---|---|
| SUPABASE_URL | URL da API local ou do projeto do cliente |
| SUPABASE_PUBLISHABLE_KEY | Chave pública publishable/anon local, nunca service_role/secret |
| GOOGLE_MAPS_API_KEY | Chave Maps SDK Android com restrição de aplicativo/certificado/API |

Para Android Emulator, o host da máquina é `http://10.0.2.2:54321`. Para simulador iOS, `http://127.0.0.1:54321`. Em aparelho físico, definir conectividade segura local adequada; HTTP aberto para qualquer IP não foi habilitado.

Development pode compilar sem chave: o app abre, mas Auth/dados retornam erro de configuração. A inicialização do Supabase é lazy. Não há fallback para o servidor antigo. Homologation/production exigem URL HTTPS, chave pública e chave Maps explícitas. Chaves secretas/service_role são rejeitadas no build; a referência antiga também é rejeitada.

Os valores públicos são gerados em Kotlin no diretório build (ignorado). Eles podem ser extraídos do aplicativo; a proteção é RLS/grants e restrições Maps, não ofuscação. Não colocar senha de banco ou segredo de servidor nesses campos.

Android permite HTTP apenas para localhost/127.0.0.1/10.0.2.2; fora disso usa HTTPS. iOS recebeu apenas a declaração de rede local, sem exceção ATS global. Google Maps não foi conectado nem validado nesta Sprint: falta chave nova autorizada. Em development sem chave, o componente mostra mapa indisponível, preservando o restante da tela. Não ativar APIs ou faturamento por este procedimento.

## Assinatura Android

Credenciais de release saíram do fonte. São lidas de ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS e ANDROID_KEY_PASSWORD, fornecidas por cofre/CI. Release não deve reutilizar assinatura debug. O task validateReleaseCredentials bloqueia empacotamento release sem configuração completa.

Os keystores existentes não foram substituídos. Validar titularidade e fingerprints contra Play Console antes de mudar assinatura ou applicationId. Histórico Git e cópia de preservação ainda contêm o código antigo; remoção do fonte atual não revoga credencial exposta.

## Stack Supabase local completa

Pré-requisitos: CLI Supabase e Docker/Podman em funcionamento. No checkout atual a CLI é 2.116.0. Foi instalado Podman 6.1.4 e criada a VM `tachegando-sprint1` (2 CPUs, 4 GB, disco virtual 20 GB, compartilhamento somente do projeto). A VM não se manteve ativa e a nova tentativa falhou no socket gvproxy; a stack completa não iniciou. Não foi instalado helper privilegiado/global. É necessário resolver o runtime local ou usar Docker Desktop para validar Auth, REST e Realtime.

Com container runtime, executar na raiz, somente em banco local descartável:

```bash
supabase start
supabase db reset --local
```

O reset é destrutivo **apenas para os dados da stack local**. Não usar contra banco com trabalho que precisa ser preservado. Nenhum comando remoto é parte do roteiro. A configuração tem project_id local, exposição automática desativada, schema público explícito e confirmação de email desativada apenas no desenvolvimento local. Essa última opção não deve ser copiada automaticamente para produção.

Obter a chave **pública** da própria stack pelo Studio local e configurar sem imprimi-la em logs compartilhados. Criar contas de login pelo cadastro real do Auth. Os quatro emails `example.test` do seed não têm senha: são fixtures de SQL, não logins prontos, e não representam dados recuperados.

Validar por REST/Auth reais antes de qualquer homologação: cadastro consumidor/vendedor, sessão JWT, CRUD produtos, catálogos, posição, rota, intenção, logout e recepção/remoção dos canais Realtime. A suíte PostgreSQL abaixo não substitui essa integração.

## Banco canônico

- profiles.id → auth.users.id (privado por usuário).
- vendors.id UUID independente, user_id UNIQUE → profiles; perfil vendedor criado pelo trigger Auth para cadastro de vendedor.
- products normalizado: nome/preço decimal/unidade/status, vendor_id FK.
- vendor_locations: geography e TTL de dois minutos, escrita somente por RPC do próprio vendedor.
- vendor_routes: waypoints JSONB, dias text[], tolerância, polígono calculado no servidor e status draft/active/ended; uma rota ativa por vendedor.
- purchase_intents: consumidor/vendor, localização, prazo/status e FK composta opcional para rota do mesmo vendedor; sem insert/update direto do app.
- vendor_catalog e route_details: projeções security_invoker para os DTOs Kotlin.

RLS em todas as seis tabelas; grants mínimos e campos de identidade imutáveis para o cliente. Anônimos sem acesso aos dados. Consumidor consulta catálogo elegível, mas só vê o próprio perfil e intenções; vendedor só altera seus produtos/rotas e consulta intenções destinadas a ele. Polígonos arbitrários não dão acesso a consumidores de terceiros.

RPCs: create_route_buffer (autenticação e limites geométricos); get_intents_for_route (posse/escopo/status/TTL); update_vendor_location (vendedor resolvido pelo JWT); create_purchase_intent (consumidor resolvido pelo JWT, vendedor online, deduplicação e até dez intenções pendentes). As duas escritas privilegiadas validam identidade no corpo; nenhuma aceita user_id do cliente. Helpers ficam em private e não são endpoints acessíveis.

A expiração é imposta nas leituras; a limpeza física periódica de dados ainda precisa de política de retenção. Intenções antigas do próprio consumidor são marcadas expired quando ele tenta registrar outra; não há worker de push nesta Sprint.

## Teste SQL sem Docker

```bash
python3 scripts/test-local-database.py
```

Requer PostgreSQL 17+ e PostGIS instalados. Para outro caminho, use TCH_PG_BIN apontando para o diretório de binários. O script cria seu próprio cluster temporário, sem TCP, aplica as duas migrations e seed, executa 43 asserções de segurança/ciclo de vida, encerra o processo e remove o cluster. Não aceita URL, credencial ou nome de banco remoto. Não executar contra um cluster existente.

`scripts/sql/bootstrap-postgres.sql` é exclusivamente uma simulação mínima de auth.users/auth.uid e dos papéis para testes de RLS. **Não executar esse bootstrap em Supabase**, que já fornece esses objetos. Ele não simula emissão/verificação de JWT, email, REST ou Realtime. `supabase/tests/security.sql` usa asserções PostgreSQL; não é suíte pgTAP para `supabase test db`.

Há também uma cópia de inspeção em `.local/postgres-sprint1/data`, com as seis tabelas e quatro perfis sintéticos, preparada nesta máquina. Está fora do Git e pode ser descartada. Seu servidor não é uma API Supabase. Quando encerrado, pode ser reaberto com pg_ctl usando socket local; não expor trust authentication pela rede.

## Contratos e comportamento

Produtos e rotas só apresentam sucesso após confirmação do banco. A lista de produtos carrega dados próprios; adicionar/remover persiste de imediato, e o preço é validado sem arredondamento binário. O repositório também fornece atualização. O catálogo monta produtos a partir da tabela normalizada.

Rotas não dependem de cache estático nem usam ID temporário como prova de persistência. Minha rota procura uma rota ativa e oferece continuar; parar persiste status ended. O polígono é calculado/validado no banco. Erro não cria retângulo aproximado nem lista de demanda silenciosamente vazia.

Logout chama Supabase Auth; falha impede navegar fingindo saída. Falhas de consulta geram UiState.Error. Cancelamento de coroutines é propagado no helper compartilhado. Realtime remove canais em finally; atualização periódica do catálogo remove posições expiradas e vendedores offline cujo evento possa ser suprimido pela RLS. Não é o desenho final para alto volume.

O registro de intenção tem texto explícito: não promete push. Favoritos/notificações fixos foram retirados; métricas sem backend mostram ausência de dado. Layouts/tema foram preservados. Checkout, algoritmo de clusters avançado, push, tracking em background e mapas/GPS iOS continuam fora da entrega desta Sprint.

## CI

Workflow KMP preparado para Android, iOS e SQL em container PostGIS descartável. O workflow Flutter foi arquivado, com bytes preservados, em `.github/legacy-workflows`: este repositório não possui app Flutter atual. Nada foi enviado ao GitHub; a execução em runner ainda precisa ser confirmada após autorização de push.

## Conexão futura à infraestrutura do cliente

1. Cliente confirma organização/projetos e propriedade das lojas/certificados; concede acesso delegado à Mugô.
2. Após autorização separada, provisionar banco vazio de homologação e aplicar somente a baseline canônica revisada. Não tratar como upgrade do banco antigo.
3. Configurar Auth/email/redirects, grants, RLS e publicação Realtime; conferir advisors e testes por REST/JWT reais.
4. Cliente fornece chave pública e chave Maps nova com restrições; preencher arquivo ignorado de homologation, sem service_role no app.
5. Homologar em dois usuários/aparelhos, revisar localização/offline, produtos, restart de rota e logout. Produção e publicação exigem autorização separada.

## Resultado da validação nesta máquina

- Android: assembleDebug e testDebugUnitTest aprovados; 28 testes, zero falhas.
- iOS: compileKotlinIosArm64 e iosSimulatorArm64Test aprovados; 28 testes, zero falhas; framework ligado.
- Aplicativo iOS completo: xcodebuild Debug/iphonesimulator, CODE_SIGNING_ALLOWED=NO, BUILD SUCCEEDED. DerivedData fora do repositório.
- lintDebug: zero erros/fatais, 45 warnings (não suprimidos). Não equivale a validação de publicação.
- SQL: duas migrations, seeds e 43 asserções aprovadas em PostgreSQL 17.11/PostGIS 3.6.4.
- Bloqueado: stack Supabase completa, por falha do runtime Podman/gvproxy. Não houve teste de cadastro por Auth real, REST com JWT real ou eventos Realtime.

PostGIS e dependências foram instalados via Homebrew. O Node local foi realinhado às bibliotecas Homebrew após falha de carregamento do simdjson; `node --version` voltou a executar (26.13.0). Não houve mudança no package.json do site de investidores.
