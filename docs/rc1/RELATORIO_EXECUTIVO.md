# Fechamento técnico RC1 — 04/11/2026

A preparação local foi concluída. O RC1 completo está BLOQUEADO por configuração externa; não está homologado. Não foram iniciados emuladores, instalados APKs, criados usuários, alterados dados remotos, enviados e-mails, executadas migrations remotas ou realizados deploy/push/publicação. Os 18 APKs anteriores foram comparados com o baseline e estão intactos.

## Correções e implementação

- Migration nova com registro/revogação/verificação de dispositivos, vínculo ao vendedor e à sessão Auth ativa, RLS/ACL mínimos, expiração, tombstones e proteção contra ressurreição de associação antiga.
- Outbox durável por protocolo/dispositivo, leases concorrentes com SKIP LOCKED, CAS por claim, retry limitado por validade e invalidação de token sem revogar versão renovada.
- Elegibilidade real exige interesse pendente e recente, rota ativa/disponível, GPS recente com precisão, posição no percurso e à frente, sem ambiguidade. Nenhum histórico é reenfileirado.
- Publicação preserva horário de captura e precisão. Rejeição de salto não renova posição pública; recuperação válida permanece possível. Catálogo bloqueia interesse sem posição recente elegível.
- Adapters reais PostgREST e FCM HTTP v1, OAuth RS256, worker privado desativado por padrão, erros/Retry-After tratados e confirmação do provedor persistida. Segredos administrativos ficam exclusivamente no servidor.
- Android: associação vinculada à sessão e geração de logout, heartbeat confirmado pelo servidor, sincronização contra callbacks atrasados e bloqueio local de alertas após logout. Fila continua independente de push, sem aceite individual.
- Auth: defaults do SDK confirmados (persistência, recuperação e refresh ativos; signOut LOCAL). Nenhuma falha de consulta passa a sucesso. Invite não é anunciado como funcional.

## Evidências

| Verificação | Resultado |
|---|---|
| Android Debug | 157 testes, zero falhas/erros/skips |
| Android Homologation | 157 testes, zero falhas/erros/skips |
| assembleHomologation | BUILD SUCCESSFUL |
| lintHomologation | 0 erros, 64 warnings, 1 hint |
| Runner + dispatcher + adapters | 28 aprovados |
| Portal | 26 aprovados, upstream controlado |
| PostgreSQL isolado | Todas as migrations, seeds sintéticos e suites de segurança/lifecycle aprovadas |
| Push SQL | 54 assertions + 7 casos de paridade com Kotlin |
| Integração push | 6 cenários com PostgreSQL real e transportes HTTP/OAuth/FCM controlados |
| Concorrência | 6 workers disputando um job: exatamente um lease; registro/revogação e duplicidade de interesse aprovados |

O PostgreSQL usa shim Auth local, não o serviço Supabase Auth real. O FCM é um servidor de teste controlado, não Google nem um aparelho. Os testes comuns não comprovam execução dos callbacks nativos ou som/vibração. Logs em `tmp/rc1/`: gradle-delivery.log, sql.log, node-tests.log, portal-tests.log. Resultados estruturados: [results.json](results.json).

## Maps e ambiente remoto

Manifest e APK têm INTERNET, rede, localização, POST_NOTIFICATIONS e VIBRATE; minSdk 24, targetSdk 36, sem rastreamento contínuo em segundo plano. SDK Maps real e estados de carregamento/offline/timeout/retry permanecem. Logs anteriores registraram REQUEST_TIMEOUT, SwiftShader e ANR do System UI; a causa final do mapa vazio permanece NÃO CONFIRMADA. Não há evidência suficiente para culpar a chave. Console e carregamento real de tiles continuam pendentes.

Somente GETs públicos de Auth foram feitos: HTTP 200 e provedor de e-mail ativo. Não houve login QA. O portal não resolveu por DNS nesta estação. Metadados de novos contratos/triggers e geometria QA exigem o SELECT preparado; a migration nova não está instalada remotamente.

## Artefato de validação

Não foi promovido novo APK para distribuição nem solicitado outro teste manual intermediário. O build interno, com push desativado por falta de parâmetros, está em:

`<PROJECT_ROOT>/KotlinProject/composeApp/build/outputs/apk/homologation/composeApp-homologation.apk`

Application ID: `org.example.project.homologation`.

SHA-256: `ced5ec966c231283f51b378fb249858bef398b64b5e4dec4771e21edf1d9f5b7`.

Assinatura QA Android Debug, SHA-1: `4A:63:58:B2:F7:19:1E:9C:70:C7:0A:77:7D:20:DC:9D:D3:86:7D:58`. Não é chave de publicação Play Store.

## O que falta para RC1

Firebase QA (quatro campos), autorização e aplicação da migration revisada, credenciais/ativação do worker, diagnóstico externo Maps, resolução do portal e confirmação dos metadados/rota QA. Depois disso: única jornada integrada em dois aparelhos, incluindo background, permissão negada, logout, reconexão e cancelamento. Ver [dependências com passos executáveis](DEPENDENCIAS_EXTERNAS.md), [portão por funcionalidade](QUALITY_GATE.md) e [checklist de até 15 passos](CHECKLIST_ANDROID.md).

## Arquivos desta etapa

Migration: `supabase/migrations/20261104210040_interest_push_rc1.sql`; SQL: `supabase/tests/interest_push.sql`, `supabase/tests/push_projection_parity.sql`; bootstrap/harness: `scripts/sql/bootstrap-postgres.sql`, `scripts/test-local-database.py`; emissor e testes: `scripts/push/` (adapters, worker, dispatcher, testes e vetores de projeção). Android/common: build.gradle.kts, InterestFirebasePush.kt, InterestNotifier.android.kt, LocationPublisher.kt, VendorRepository.kt, ActiveRouteScreen.kt, VendorProfileScreen.kt, LocationPublisherTest.kt e PushProjectionParityTest.kt. Grafo atualizado. O repositório já tinha outras alterações anteriores; esta relação não atribui todo o git diff a este fechamento.
