# Dia 1 — checklist de infraestrutura do cliente

Preparado no fechamento do Dia 0, na branch `feature/sprint1-recuperacao-tecnica`.
**Os comandos de criação, ativação e escrita remota abaixo são instruções para execução futura, somente após autorização expressa do cliente. Não foram executados nesta entrega.**

Contas, cobrança e propriedade dos projetos pertencem ao cliente; Mugô recebe acesso delegado nominal. Não usar conta pessoal do desenvolvedor anterior, credenciais antigas ou conta da Mugô como proprietária definitiva. Registrar responsável, ambiente e project ref antes de qualquer escrita.

## Estado de saída do Dia 0

- Android: `assembleDebug`, `testDebugUnitTest` e `signingReport` reexecutados; BUILD SUCCESSFUL. 28 testes aprovados em reexecução forçada do task de teste (`--rerun`) e APK debug disponível. applicationId e SHA-1 também conferidos diretamente no APK com aapt/apksigner.
- SQL: 43 asserções reexecutadas em PostgreSQL/PostGIS descartável; aprovadas. Duas migrations mantidas sem alteração.
- Suíte HTTP/JWT/Realtime preparada em `scripts/integration`; nove testes offline dos bloqueios de segurança aprovados. A suíte real **ainda não foi executada**, portanto não há aprovação de cadastro/REST/Realtime.
- Podman: uma tentativa limitada com TMPDIR curto iniciou a VM em 10 s, mas `podman info` recusou a conexão em 127.0.0.1:59622. A VM foi encerrada; bloqueio permanece. Sem helpers privilegiados, alterações globais de rede ou novas instalações nesta etapa. Prosseguir com as tarefas independentes.
- Código Kotlin, identificadores, certificados e alterações anteriores preservados. Não houve push, migration remota, criação externa ou publicação.

## 1. Criar Supabase na conta do cliente

- [ ] Cliente confirma organização Owner, MFA, orçamento/plano e quem pode aprovar produção.
- [ ] Criar projeto **exclusivo de homologação** no Dashboard do cliente; produção terá projeto separado.
- [ ] Guardar senha do banco e credenciais administrativas em cofre. Nunca colocá-las no app, nos testes ou no Git.
- [ ] Conceder à Mugô o menor papel necessário à implantação aprovada; billing/Owner permanecem com o cliente.
- [ ] Registrar referência pública em variável de tarefa, sem senha/token:

```bash
TCH_HOMOLOG_REF='SUBSTITUIR_PELO_REF_CONFIRMADO_DO_CLIENTE'
```

O ambiente `homologation` no JSON não prova a natureza do projeto: o proprietário deve conferir que esse ref não é produção. Não há auto-detecção de titularidade pelo aplicativo.

## 2. Projeto, região e pré-condições

- [ ] Escolher região com o cliente considerando público brasileiro/latência, disponibilidade e orçamento; São Paulo (`sa-east-1`), se disponível, é candidata, não escolha automática. [Regiões oficiais](https://supabase.com/docs/guides/platform/regions).
- [ ] PostgreSQL 17 é a versão usada nos testes locais; confirmar versão, PostGIS e políticas de backup do plano escolhido.
- [ ] Conferir que o banco não possui dados/tabelas do produto nem usuários reais. Não resetar um banco compartilhado.
- [ ] Executar somente consultas de inventário no SQL Editor do novo projeto aprovado:

```sql
select current_database(), version();
select schemaname, tablename from pg_tables
where schemaname='public' order by tablename;
select extname, extnamespace::regnamespace as schema
from pg_extension where extname='postgis';
select count(*) as auth_user_count from auth.users;
select to_regclass('supabase_migrations.schema_migrations') as migration_history;
select pubname,puballtables from pg_publication where pubname='supabase_realtime';
select schemaname,tablename from pg_publication_tables
where pubname='supabase_realtime';
```

A baseline espera PostGIS no schema `extensions` (ou ainda não instalado) e publicação Realtime normal, não FOR ALL TABLES. Se houver PostGIS em outro schema, tabelas do produto, usuários ou histórico incompatível, **parar e reconciliar**; não apagar, mover extensões ou forçar migrations automaticamente.

## 3. Aplicar migrations canônicas em banco vazio

As duas migrations ativas são:

- `20261029235025_canonical_schema.sql`
- `20261029235026_canonical_security_and_rpc.sql`

`supabase/legacy_migrations`, `scripts/sql/bootstrap-postgres.sql`, `supabase/seed.sql` e `supabase/tests/security.sql` não entram no provisionamento remoto. O bootstrap é só para o PostgreSQL de testes; os seeds sem senha não são contas de homologação.

Primeiro, verificações locais:

```bash
git branch --show-current
ls supabase/migrations
python3 scripts/test-local-database.py
supabase --version
supabase db push --help
```

Após autorização remota, autenticar a CLI pelo fluxo seguro. Não passar senha/token literal na linha de comando nem publicar saída com credenciais. Usar project ref explícito, sem depender de vínculo antigo:

```bash
supabase login
supabase migration list --project-ref "$TCH_HOMOLOG_REF"
supabase db push --project-ref "$TCH_HOMOLOG_REF" --dry-run --skip-vault
```

- [ ] Conferir no dry run **somente os dois nomes acima**, destino e banco vazio.
- [ ] Cliente/aprovador confirma a execução concreta; sem essa autorização, não avançar.
- [ ] Aplicar somente a sequência revisada, sem `--include-all`, `--include-roles` ou `--include-seed`:

```bash
supabase db push --project-ref "$TCH_HOMOLOG_REF" --skip-vault
supabase migration list --project-ref "$TCH_HOMOLOG_REF"
```

Conferência somente de leitura depois da aplicação:

```sql
select tablename,rowsecurity from pg_tables
where schemaname='public'
  and tablename in ('profiles','vendors','products','vendor_locations','vendor_routes','purchase_intents')
order by tablename;
select table_name,grantee,privilege_type from information_schema.role_table_grants
where table_schema='public' and grantee in ('anon','authenticated')
order by table_name,grantee,privilege_type;
select schemaname,tablename from pg_publication_tables
where pubname='supabase_realtime';
```

Aceite: seis tabelas com RLS; views security_invoker; sem leitura anônima de dados; grants coerentes com as políticas; três tabelas publicadas: vendors/products/vendor_locations. Conferir também privilégios por coluna e execução das quatro RPCs. Consultar advisors de segurança pelo Dashboard e corrigir qualquer achado antes de aprovação. Aplicar migrations não prova integração real.

## 4. Configurar Auth, REST e Realtime

- [ ] Auth email/senha habilitado; confirmação de email e SMTP definidos para homologação. Não copiar automaticamente a confirmação desativada do config local.
- [ ] Usar domínio/aliases de email de teste que a equipe realmente recebe; confirmar contas pelo fluxo real. Limites do provedor podem bloquear signup.
- [ ] Site URL e redirects apontam somente para destinos implementados/autorizados. Recuperação de senha por deep link ainda não está completa no app; não marcá-la como aprovada só porque o email foi enviado.
- [ ] API REST com schema público necessário e grants explícitos; não expor schema private/auth.
- [ ] Publicação Realtime inclui vendors, products e vendor_locations; conferir leitura por JWT e eventos reais. Não abrir RLS para fazer o evento aparecer.
- [ ] Profiles privados; vendor.id diferente de auth.uid; produtos normalizados; RPCs não recebem user_id de terceiros.
- [ ] Nenhuma service_role/secret é fornecida ao cliente Android ou ao runner.

A publicação é configurada pela primeira migration quando `supabase_realtime` existe. Não repetir ALTER PUBLICATION cegamente. Se o objeto estiver ausente/incompatível, tratar separadamente com revisão e autorização. [Auth](https://supabase.com/docs/guides/auth/passwords), [Postgres Changes](https://supabase.com/docs/guides/realtime/postgres-changes).

## 5. Ambiente homologation

Da raiz do repositório:

```bash
cp KotlinProject/config/homologation.properties.example \
  KotlinProject/config/homologation.properties
chmod 600 KotlinProject/config/homologation.properties
```

Editar localmente, sem imprimir valores:

| Propriedade | Valor a obter |
|---|---|
| SUPABASE_URL | HTTPS do projeto homolog confirmado |
| SUPABASE_PUBLISHABLE_KEY | Publishable key pública do novo projeto (anon somente se necessário) |
| GOOGLE_MAPS_API_KEY | Nova chave Android restrita, criada no passo 8 |

Gradle lê `-P` → variável de ambiente → arquivo do ambiente. Verificar se não há variável/propriedade externa sobrescrevendo o destino pretendido; não usar comandos que imprimam todos os valores de ambiente. `.env` não é carregado automaticamente. Os `.properties` reais são ignorados pelo Git; os valores públicos são incorporados ao build, portanto não são cofre de segredos.

O build homologation exige os três valores. Após configurar também Maps:

```bash
./scripts/gradlew-local.sh :composeApp:assembleDebug :composeApp:testDebugUnitTest \
  -Papp.environment=homologation
```

Essa build continua sendo **debug**, assinada com certificado debug. Trocar o ambiente não a transforma em release. Não alterar applicationId/keystore para distinguir ambientes sem conferir o app já publicado.

## 6. Criar Google Cloud do cliente

- [ ] Cliente cria/seleciona projeto e organização próprios; confirma se existe projeto recuperável antes de criar outro.
- [ ] Registrar Project ID, responsável, billing e orçamento. Aprovar despesas antes de vincular faturamento/ativar serviços.
- [ ] Delegar permissões específicas à Mugô para APIs/chaves, sem Owner global permanente.
- [ ] Não trocar conta/projeto global da CLI por conveniência; passar `--project` explicitamente.

A criação pode ser feita no Console. Com CLI instalada e autenticação/delegação aprovadas, exemplo futuro:

```bash
TCH_GOOGLE_PROJECT='ID_CONFIRMADO_DO_CLIENTE'
gcloud projects create "$TCH_GOOGLE_PROJECT" --name='Tá Chegando Homologação'
```

Se houver organização/pasta corporativa, o proprietário deve definir a localização organizacional correta antes de criar. Vincular billing aprovado no Console; não reutilizar cartão/conta do desenvolvedor anterior.

## 7. Ativar Maps SDK for Android

Somente após autorização e billing definido:

```bash
gcloud services enable maps-android-backend.googleapis.com \
  --project "$TCH_GOOGLE_PROJECT"
```

Não ativar Places, Routes, Geocoding, Firebase ou outros serviços para simplesmente mostrar GPS, marcadores e polilinhas existentes. GPS usa Play Services Location; não depende da Geolocation API do Google Cloud. Conferir custos/quotas aplicáveis no projeto do cliente. [Setup oficial](https://developers.google.com/maps/documentation/android-sdk/get-api-key).

## 8. Gerar chave Android restrita

Dados atuais, **não alterados**:

| Item | Valor/evidência |
|---|---|
| applicationId | `org.example.project` em composeApp/build.gradle.kts |
| Debug SHA-1 desta máquina | `4A:63:58:B2:F7:19:1E:9C:70:C7:0A:77:7D:20:DC:9D:D3:86:7D:58` |
| Debug SHA-256 desta máquina | `0A:09:84:68:2D:45:DB:7E:56:4F:53:B6:83:24:1B:52:33:1B:A3:14:7F:34:2F:0F:B7:FB:7F:58:02:15:ED:E3` |
| Release local | signingReport sem configuração release; release.jks/release.keystore não foram abertos com senha antiga nem substituídos |
| Assinatura Play | Obter no Play Console → Integridade do app → Assinatura de apps; ainda não conferida |

Regenerar dados públicos da máquina que fará o build:

```bash
./scripts/gradlew-local.sh :composeApp:signingReport -Papp.environment=development
```

- [ ] Console → APIs e serviços → Credenciais → nova chave para Android homolog.
- [ ] Restrição de aplicativo: **Android apps**, par `org.example.project` + SHA-1 do certificado que assina o APK instalado.
- [ ] Restrição de API: **Maps SDK for Android**, somente.
- [ ] Para outro desenvolvedor/CI debug, cadastrar seu SHA-1 próprio ou usar chave separada. Não distribuir a chave privada debug desta máquina.
- [ ] Para distribuição pela Play, usar SHA-1 de **App Signing**, que pode diferir da chave de upload/local. Se houver upgrade/rotação na Play, conferir todos os certificados aplicáveis.
- [ ] Conferir conta publicada antes de trocar identificador, keystore ou certificado. Release será configurado via cofre, não por senha no fonte.

Alternativa CLI, somente após autorização; a saída foi limitada ao nome do recurso para não imprimir a chave:

```bash
TCH_ANDROID_PACKAGE='org.example.project'
TCH_DEBUG_SHA1='4A:63:58:B2:F7:19:1E:9C:70:C7:0A:77:7D:20:DC:9D:D3:86:7D:58'
gcloud services api-keys create \
  --project "$TCH_GOOGLE_PROJECT" \
  --display-name='ta-chegando-android-homolog-debug' \
  --allowed-application="sha1_fingerprint=$TCH_DEBUG_SHA1,package_name=$TCH_ANDROID_PACKAGE" \
  --api-target=service=maps-android-backend.googleapis.com \
  --format='value(name)'
```

Se for necessário habilitar API Keys API para o comando, fazer isso apenas com aprovação; alternativamente usar o Console. Obter o valor da chave pelo Console e inseri-lo somente em `KotlinProject/config/homologation.properties`. O placeholder `${googleMapsApiKey}` no AndroidManifest é preenchido pelo Gradle; **não editar o Manifest para colar chave**.

Chaves Android são públicas no APK; restrições de pacote/certificado/API são obrigatórias. Não reaproveitar a chave antiga sem propriedade e restrições verificadas. [CLI de chaves](https://docs.cloud.google.com/sdk/gcloud/reference/services/api-keys/create), [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756).

## 9. Testar infraestrutura real e aplicativo

- [ ] Preparar JSON ignorado do runner, conferir ref com o responsável e executar `--phase validate` offline.
- [ ] Aprovar escrita sintética em homologação; executar signup, confirmar emails e executar verify conforme [testes de integração](INTEGRATION_TESTS.md).
- [ ] Exigir resultado completo real: Auth, JWT/RLS, CRUD, catálogo, posição, rota, intenção, eventos em vendors/products/vendor_locations e logout/refresh revogado.
- [ ] Não marcar PASS para fase bloqueada, erro, timeout WebSocket ou execução apenas dos nove testes offline.
- [ ] Executar [roteiro Android de duas contas](ANDROID_HOMOLOGATION.md), com GPS simulado e negação de permissão/reinício/offline.
- [ ] Conferir mapa renderizado com a nova chave; o placeholder de indisponibilidade não é integração Maps aprovada.
- [ ] Registrar versão/hash do APK, project ref público, etapas e evidências sem senhas/tokens.

**Gate:** sem aprovação HTTP/JWT/Realtime e roteiro Android, a entrega é preparação técnica, não MVP homologado. Se Podman continuar indisponível, pode-se usar homologação autorizada para os testes reais; não é necessário refazer o código nem usar o banco antigo.

## 10. Preparação para Google Play — sem publicar agora

- [ ] Cliente confirma titularidade da conta, app/package existente e certificados. Não substituir identidade de app já publicado.
- [ ] Resolver recuperação completa de senha, exclusão de conta e demais bloqueios de produto antes de submissão pública.
- [ ] Definir versão/versionCode e assinatura de release via cofre; gerar AAB somente na fase autorizada. Não usar APK debug na loja.
- [ ] Conferir exigência vigente de target SDK, páginas de memória 16 KB e bibliotecas nativas; executar testes no artefato final. Alinhamento ZIP sozinho não prova compatibilidade ELF/runtime.
- [ ] Preencher privacidade, Data safety, localização, classificação, público, suporte, screenshots e acesso de revisão.
- [ ] Política/URLs de exclusão de conta; requisitos de teste fechado aplicáveis à conta do cliente e revisão da Play.
- [ ] Iniciar por teste interno/fechado, mediante autorização; push, background e assinatura paga não podem ser anunciados como implementados.

Comandos de referência para a fase release **futura**, após configuração privada das quatro variáveis de assinatura:

```bash
./scripts/gradlew-local.sh :composeApp:bundleRelease -Papp.environment=production
"$ANDROID_HOME/build-tools/35.0.0/zipalign" -c -P 16 -v 4 CAMINHO_DO_APK_RELEASE.apk
```

Não fornecer senhas em parâmetros literais. O APK usado em zipalign deve ser o artefato release correto, não o debug para concluir sobre distribuição Play. [16 KB](https://developer.android.com/guide/practices/page-sizes).
