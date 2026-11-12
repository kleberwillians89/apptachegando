# Auditoria incremental do MVP — 12/11/2026

Repositório: kleberwillians89/apptachegando. Base auditada: main `257558785b9329c1e8f2888807d312e80eef6796`. Branch de trabalho: `codex/auditoria-incremental-mvp-20261112`. PR: https://github.com/kleberwillians89/apptachegando/pull/4 . A main não foi alterada por esta tarefa.

## Histórico conferido antes de corrigir

Execução solicitada: https://github.com/kleberwillians89/apptachegando/actions/runs/43138238967 . Evento pull_request, head `6af3f982e4053603c03e26c9e6dd7da4515184a1`, branch `feat/app-catalogo-conta-integracao`. Android/iOS falharam; Node/PostgreSQL passaram nessa execução histórica.

| Erro histórico | Situação no HEAD auditado | Ação |
|---|---|---|
| NativeMap.kt:134, `Expecting an element` / `Unresolved reference n` | PRESENTE. O callback continha caracteres literais backslash+n em vez de quebras de linha. Reproduzido localmente por compileDebugKotlinAndroid antes da correção. | Corrigidas somente as quebras de linha; SDK, callback e log existentes preservados. |
| VendorProductsScreen.kt:85, OwnCatalogProduct atribuído a Product? | AUSENTE nesse main. O arquivo atual usa Product nas listas, edição e remoção; a lambda chama ProductRepository compatível. O erro pertence a outro head de PR. | Nenhuma alteração nesse arquivo ou recriação do modelo. Compilação Android/iOS validou a ausência do erro. |

A falha sintática não explica o REQUEST_TIMEOUT/tiles vazios anterior. Não foi declarada correção de cartografia. Nenhum MapLibre, novo renderer ou substituição automática de Google Maps foi incluído. Diagnóstico, impacto e alternativas estão em [LOCAL_TESTS.md](LOCAL_TESTS.md).

## CI: diagnóstico incremental

O primeiro run desta branch, 43227826680, revelou falhas adicionais de infraestrutura, anteriores à compilação/suítes:

- Android: android-actions/setup-android@v3 tentava instalar o pacote obsoleto `tools`. Configurado `packages: platform-tools`, preservando SDK e versões do projeto.
- PostgreSQL: pg_isready via socket Unix aceitava o servidor temporário de initdb. O job começava a modificar extensões enquanto o entrypoint PostGIS ainda inicializava, terminando em `postgis_lib_version() does not exist`. Healthcheck TCP agora espera o servidor final; os testes usam banco novo `tch_ci` criado de template0. Não há mais DROP EXTENSION na preparação.
- Adicionada concorrência por PR/ref para cancelar execuções superadas. Não há deploy ou escrita em Supabase nos workflows.

O estado hospedado final deve ser conferido nos checks do PR e na descrição atualizada após a execução do último commit. Não confundir um resultado de commit anterior com o head final. As quatro suites mantidas são Android, iOS, database e node.

## QA: auditoria remota efetivamente executada, somente leitura

Projeto QA `vhnkezwjknwpddqhlnfk`, através do vínculo Supabase tachegando. Foram executados list_migrations e SELECTs de pg_proc, pg_policies, pg_class, pg_trigger, information_schema e catálogo de migrations. Nenhuma função de negócio foi chamada e nenhuma linha de usuário/produto/rota/intenção foi lida ou alterada.

Resultado reproduzível: [qa-metadata-readonly.sql](qa-metadata-readonly.sql). Evidência sanitizada: [qa-readonly-audit.json](qa-readonly-audit.json).

- 14 RPCs principais consultadas presentes, com assinaturas esperadas, wrappers SECURITY INVOKER, search_path vazio, execução authenticated e sem execução anon.
- 9 tabelas consultadas com RLS; tabelas privadas de dispositivos/outbox/pedidos de exclusão não concedem SELECT a authenticated/anon.
- 12 políticas de tabelas operacionais conferidas: propriedade por auth.uid/vendedor, visibilidade pública autenticada conforme disponibilidade, cancelamento restrito ao consumidor.
- Triggers ativos: auth.users → private.handle_new_user; vendor_routes → private.route_geometry; purchase_intents → private.push_enqueue.
- Realtime inclui vendors, products, vendor_locations, vendor_routes e purchase_intents.
- Colunas de sessão e profiles.signup_role necessárias estão presentes.

### Divergência de histórico/contrato — não reaplicar

| Local no main | Evidência remota | Interpretação |
|---|---|---|
| 20261029235025 / 20261029235026 | Mesmas versões registradas | Histórico canônico inicial registrado. |
| 20261104140831 / 20261104210040 / 20261111135508 | Versões ausentes do registro, mas RPCs/tabelas/triggers consultados desses contratos existem | Aplicação manual/parcial do histórico. Presença de objetos não comprova identidade completa do SQL. Não reaplicar nem executar migration repair nesta tarefa. |
| 20261111170000_site_vendor_catalogue_onboarding | Registrada como 20261111233854_site_vendor_catalogue_onboarding | Mesmo contrato público, porém helper remoto contém guarda adicional de inscrição profissional. Não declarar equivalência byte a byte. |
| Ausente no main auditado | 20261111234837_vendor_catalogue_append_batches e append_my_vendor_products(text[]) | Evolução remota não versionada neste main. A RPC adicional é invoker, authenticated, sem anon; helper resolve vendedor pelo usuário confirmado e serializa por proprietário. |

Pendência: reconciliar a fonte autorizada das duas evoluções de catálogo e decidir como versionar o histórico existente. Isso exige revisão separada; não foi criada migration especulativa nem repetida DDL remota. O bloqueio é de rastreabilidade/contrato, não evidência de RPCs principais ausentes. Métodos de edição exclusivos de outro PR não foram importados para justificar o erro histórico OwnCatalogProduct.

## Relatório por funcionalidade

| Funcionalidade | Estado | Evidência e limite |
|---|---|---|
| Compilação Android | CORRIGIDO | Erro NativeMap reproduzido no HEAD e eliminado; compilação e testes locais passaram. |
| Compilação iOS | FUNCIONANDO | compileKotlinIosArm64 e ligação do binário de testes aprovadas; erro Product histórico ausente no main. |
| Testes iOS em simulador | FUNCIONANDO | iosSimulatorArm64Test executou 179 testes nativos com zero falhas/skips. Não é teste manual de UI. |
| Auth/perfis/papéis | FUNCIONANDO localmente | Testes controlados e SQL verificam confirmação/sessão, vendor link, perfil incompleto, consulta falha, logout/troca de identidade; metadados QA compatíveis. Login real/e-mail não exercitados. |
| Catálogo/onboarding | FUNCIONANDO localmente / BLOQUEADO para equivalência integral QA | 13 assertions novas e concorrência de duas chamadas; repetições preservam IDs/produtos, falhas fazem rollback. Diferença remota de histórico/guarda documentada. |
| Rotas/interesse/cancelamento | FUNCIONANDO localmente | Suites SQL, RLS e concorrência aprovadas; contratos QA presentes por leitura. Não foi criada intenção/rota remota. |
| Localização/trajeto simulado | FUNCIONANDO | Reutiliza motor existente; testes novos comprovam trace determinístico, reset e cancelamento offline/reconciliação. Entrada demo disponível em development/QA, excluída de produção. |
| Google Maps real | NÃO TESTADO | SDK preservado; diagnóstico de rede/renderização depende de etapa específica. Sem prova de tiles/carregamento nesta auditoria. |
| MapLibre | NÃO TESTADO / não implementado | Alternativas documentadas, sem dependência ou migração automática. |
| Portal | FUNCIONANDO localmente | 29 testes e verificação de sintaxe; confirmação/recovery/resend/CSRF/cookies/inspeção. DNS, SMTP e entrega real seguem não testados. |
| Worker/push | FUNCIONANDO localmente | 28 testes runner/push e 6 integrações PostgreSQL+HTTP controladas; nenhum FCM real disparado, sem credencial Google necessária. |
| Emulador Android/UI ponta a ponta | NÃO TESTADO | Roteiro preparado; nenhum APK, emulador Android ou teste físico executado nesta auditoria. |

“Funcionando localmente” não significa homologado no Supabase real ou em interface Android/iOS. Auth shim e transportes controlados estão explicitamente separados de serviços reais.

## Evidências locais

Comandos completos e limites em [LOCAL_TESTS.md](LOCAL_TESTS.md). Logs mantidos apenas em /private/tmp:

- tch-main-baseline-compile.log: falha NativeMap confirmada antes da correção; nenhum erro VendorProductsScreen no HEAD.
- tch-pr-validation.log: compilação Android/iOS, testes e lint aprovados após correção.
- tch-pr-final-gradle.log: 179 Debug + 179 iOS simulator, zero falhas/erros/skips. Lint Debug: 0 erros, 34 warnings e 1 hint.
- tch-pr-sql.log: todas as migrations locais e suites aprovadas; 46 assertions web/geo, 54 RC1, 7 projeções, 13 onboarding; 6 cenários PostgreSQL/HTTP controlados; concorrência de perfil, catálogo e interesse aprovada.
- tch-pr-node.log: 28 testes aprovados. tch-pr-portal.log: 29 testes aprovados; npm run check passou.

Os testes comuns se repetem entre Android e iOS. Não contar como comportamentos independentes nem presumir execução de SDKs externos.

## Restrições preservadas e próximos gates

Sem alteração na main, merge, deploy, publicação de build, APK novo, migration remota, Auth/SMTP ou dados QA. O site publicado não foi alterado. As alterações anteriores no workspace original e o checkout de publicação permanecem separados.

Antes de homologação real: reconciliar histórico/contratos de catálogo sob aprovação; conferir portal/SMTP/redirects; executar jornada autenticada real autorizada, sem dados comerciais; decidir etapa de diagnóstico de mapas; revisar exclusão efetiva de conta e produção. Nenhum desses gates é contornado pela demonstração.
