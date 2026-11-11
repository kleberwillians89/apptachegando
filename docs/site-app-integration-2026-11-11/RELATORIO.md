# Integração site + Android + Supabase — 11/11/2026

Implementação local concluída. Nenhuma migration remota, alteração de Auth/SMTP/DNS, cadastro remoto, envio real de e-mail, deploy, push, instalação, emulador ou geração de APK. Os 18 APKs distribuídos anteriores continuam intactos. O APK antigo não inclui estas mudanças; nenhum artefato intermediário deve ser instalado como prova desta sprint.

## Diagnóstico e correções

A identidade é auth.users.id e o vínculo profissional é vendors.user_id, com vendors.id distinto. O papel no Android agora é resolvido por get_my_account_profile, validando sessão Auth ativa e confirmação real no backend. Metadata não autoriza vendedor. O trigger só cria o perfil básico para cadastros futuros; os vendedores existentes não são alterados. Contas web confirmadas de consumidor entram como consumidor; profissionais sem vínculo têm orientação explícita e podem concluir o perfil no diálogo da tela de login existente. A RPC de conclusão é autenticada, não recebe identidade de terceiros e é idempotente/serializada por usuário. Telefone previamente informado é preservado quando omitido na conclusão.

A descoberta no mapa, busca e categorias agora usa discover_vendors com coordenadas, rota ativa, disponibilidade, GPS recente/preciso, corredor, posição à frente e raio antes da paginação. Sem GPS do consumidor, há orientação explícita e não há fallback silencioso para catálogo global como se fosse proximidade. O raio padrão é 5 km e o mapa continua real; esta alteração não resolve nem comprova o carregamento dos tiles.

O interesse mantém o protocolo e seus status existentes. get_my_interest_state projeta elegibilidade, espera, GPS indisponível, fora/atrás do percurso, ambiguidade, cancelamento e expiração, sem novos estados persistidos. A tela reconcilia por polling enquanto ativa. Persistir não promete venda/estoque/atendimento ou entrega push. Cancelamento e deduplicação permanecem.

O reenvio usa Supabase Auth real na implementação, com transporte controlado nos testes. Está disponível no Android e no portal existente, com respostas condicionais, erros sanitizados, cooldown, CSRF no portal e sem envio por GET. Cadastro novo adota senha de 8–128 caracteres, sem modificar senha ou política de login das contas existentes. Confirmação/recuperação ficam no portal; Android entra com e-mail/senha, sem transferência manual de sessão.

request_account_deletion apenas registra pedido privado/idempotente. A execução da exclusão, processador administrativo, política de retenção e interfaces finais de exclusão continuam pendentes; não é cumprimento integral da política Play Store.

## Migration revisável

`20261111135508_web_app_identity_and_discovery.sql`

SHA-256: `c37c3646aff8f8f4e5882d1c94484e7f2af1ff816fe051afdc0ce2ce0676e4e9`.

Depende dos contratos anteriores e de `20261104210040_interest_push_rc1.sql`, inclusive projeção e validação de sessão. Não aplicada remotamente. Ela acrescenta profiles.signup_role, altera o comportamento do trigger apenas para novos cadastros e cria as cinco RPCs públicas com wrappers invoker, helpers privados e grants mínimos. A alteração do trigger exige coordenação com clientes antigos: o app atualizado/site devem completar perfis profissionais depois da confirmação. Não habilitar o app novo antes dos contratos.

[Preflight somente de leitura](preflight-readonly.sql) preparado para a revisão remota. Não executar db push indiscriminado nem editar migrations históricas para instalar o contrato novo.

## Validação

- Debug: 174 aprovados; Homologation: 174 aprovados. Zero falhas/erros/skips.
- Runner/dispatcher/adapters: 28 aprovados. Portal: 29 aprovados, incluindo três novos testes de reenvio. Sintaxe do servidor aprovada.
- PostgreSQL isolado: todas as migrations e suites passaram; 46 novas assertions web/geo, 54 assertions RC1 e 7 vetores de projeção. Os seeds são sintéticos. Dois processos concluindo perfil simultaneamente retornam um único vendor_id/registro; concorrência de interesse continua retornando um único protocolo.
- Seis cenários de PostgreSQL real + transportes HTTP/OAuth/FCM controlados continuam aprovados. Isso não comprova entrega Google/dispositivo.
- Compilação Homologation e lint aprovados sem APK: 0 erros, 60 warnings e 1 hint. Warnings de dependências/depreciações existentes permanecem; não foram feitos upgrades desnecessários.
- diff --check e paridade das fixtures aprovados. Grafo AST atualizado; o extrator reporta parsing parcial em alguns arquivos Kotlin apesar de compilação aprovada.

A soma Android + Node + portal é 405 execuções aprovadas; as duas variantes repetem testes comuns. Os 17 novos testes comuns usam Auth/perfil controlados: consumidor/vendedor web, não confirmado, incompleto, consulta falha, identidade trocada/logout, renovação simulada, cancelamento de coroutine, resend e mensagens de elegibilidade. A renovação real do SDK e login real QA não foram executados. Auth shim SQL verifica contratos de sessão ativa/revogada, mas não substitui o serviço Supabase Auth.

## Handoff para o site

[Contrato operacional completo](CONTRATO_WEB.md) e [schema do formulário](registration.schema.json) incluem métodos, identidade, parâmetros, respostas, validações, RLS/grants, repetição e erros. O repositório web ainda precisa integrar signup, confirmação/getUser, login, conclusão de perfil, resend, recovery e solicitação de exclusão. Usar o mesmo projeto Supabase, publishable key e URLs fixas; nunca service-role no browser/APK.

## Dependências e prazo de 13/11/2026

Persistem: revisão/aplicação autorizada de contratos QA; SMTP/domínio/remetente verificados e limites; DNS/HTTPS/hosting do portal e assetlinks; Site URL/redirects/templates Auth; integração do repositório web e primeira jornada real com duas contas. Maps, Firebase/worker e produção mantêm as pendências anteriores. Nenhum desses serviços foi alterado nesta sprint.

A sprint elimina lacunas locais de identidade, descoberta e estado do interesse, mas a entrega de sexta depende de liberar os serviços e executar uma única rodada real com tempo para corrigir falhas. Sem isso, o estado correto é IMPLEMENTADO E VALIDADO LOCALMENTE, NÃO HOMOLOGADO. Não é seguro prometer publicação pública até sexta.

## Arquivos alterados nesta sprint

- `supabase/migrations/20261111135508_web_app_identity_and_discovery.sql`
- `supabase/tests/web_identity_discovery.sql`
- `supabase/seed.sql`
- `scripts/sql/bootstrap-postgres.sql`
- `scripts/test-local-database.py`
- `scripts/push/postgres-integration.mjs`
- `KotlinProject/composeApp/src/commonMain/kotlin/org/example/project/core/data/AccountProfileRepository.kt`
- `KotlinProject/composeApp/src/commonMain/kotlin/org/example/project/core/data/ConfirmationResender.kt`
- `KotlinProject/composeApp/src/commonMain/kotlin/org/example/project/core/data/AuthRepository.kt`
- `KotlinProject/composeApp/src/commonMain/kotlin/org/example/project/core/data/AuthValidation.kt`
- `KotlinProject/composeApp/src/commonMain/kotlin/org/example/project/core/data/VendorRepository.kt`
- `KotlinProject/composeApp/src/commonMain/kotlin/org/example/project/core/data/PurchaseIntentRepository.kt`
- `KotlinProject/composeApp/src/commonMain/kotlin/org/example/project/core/data/ConsumerExperience.kt`
- `KotlinProject/composeApp/src/commonMain/kotlin/org/example/project/features/auth/LoginScreen.kt`
- `KotlinProject/composeApp/src/commonMain/kotlin/org/example/project/features/auth/ConsumerRegisterScreen.kt`
- `KotlinProject/composeApp/src/commonMain/kotlin/org/example/project/features/auth/VendorRegisterScreen.kt`
- `KotlinProject/composeApp/src/commonMain/kotlin/org/example/project/features/consumer/ConsumerHomeScreen.kt`
- `KotlinProject/composeApp/src/commonMain/kotlin/org/example/project/features/consumer/VendorListScreen.kt`
- `KotlinProject/composeApp/src/commonMain/kotlin/org/example/project/features/consumer/VendorProfileScreen.kt`
- `KotlinProject/composeApp/src/commonTest/kotlin/org/example/project/core/auth/WebAccountIntegrationTest.kt`
- `KotlinProject/composeApp/src/commonTest/kotlin/org/example/project/core/auth/ConfirmationResenderTest.kt`
- `KotlinProject/composeApp/src/commonTest/kotlin/org/example/project/core/auth/AuthValidationTest.kt`
- `KotlinProject/composeApp/src/commonTest/kotlin/org/example/project/core/data/InterestConditionTest.kt`
- `auth-portal/src/server.mjs`
- `auth-portal/src/supabase.mjs`
- `auth-portal/src/render.mjs`
- `auth-portal/test/portal.test.mjs`

Também foram atualizados os artefatos do grafo em KotlinProject/composeApp/src/graphify-out e adicionados os documentos/JSON desta pasta. O repositório já continha muitas alterações anteriores; esta lista delimita a sprint, sem atribuir todo o git diff a ela.
