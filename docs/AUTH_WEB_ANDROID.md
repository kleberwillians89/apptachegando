# TáChegando — autenticação Web + Android

Data: 01/11/2026. Branch: `feature/sprint1-recuperacao-tecnica`.

Implementação **local**, sem publicação, alteração de DNS/Auth, push ou migrations. Nenhum signup, envio de recuperação ou troca de senha real foi executado nesta etapa. ConsumerA/B e o RUN `mvuzgto9-1fbfa58b` foram preservados; vendedores continuam pendentes. Um login real do consumidor A existente foi usado para validar o Android, seguido de logout local.

## Diagnóstico da arquitetura

| Área | Evidência e conclusão |
|---|---|
| Institucional | GET público de `https://tachegandoapp.com.br` respondeu `Server: Vercel`; HTML próprio com `/marca/home.css` e `/marca/home.js`, sem indício de framework SPA na página auditada. Código/repositório de implantação não disponível, conforme cliente. Não foi modificado. |
| Auth do institucional | `/entrar` carrega `/js/auth.js` e `/js/data.js`. O próprio data.js se identifica como protótipo; login/cadastro/dados ficam em localStorage e existem contas demo, sem chamadas Supabase/fetch nos scripts auditados. **Essas contas não são as contas Auth reais**. Não foram migradas, substituídas ou integradas silenciosamente. Isso continua sendo uma limitação para lançar login comercial real no institucional. |
| Workspace | KMP em `KotlinProject`; `investor-hub` é outro projeto Vite/TypeScript, não o institucional. Intocado nesta etapa. |
| Nova decisão do cliente | Portal independente em `https://app.tachegandoapp.com.br`, implementado em `auth-portal/` com Node 22+, SSR/formulários HTML, zero dependências de runtime. Não reconstrói o institucional ou o aplicativo. |
| SDK Android real | `dependencyInsight` de performanceRuntimeClasspath confirmou auth-kt/auth-kt-android **3.1.2**. Instalação `Auth` sem override de flowType/scheme/host/defaultRedirectUrl. Fonte oficial do tag 3.1.2 define default **IMPLICIT**, auto load/save de sessão e lifecycle callbacks. Não foi presumido PKCE e não houve upgrade do SDK. |
| Signup/login/logout | App usa Email/password no SDK e papéis resolvidos pelo vendedor vinculado à identidade Auth. Signup ganhou apenas redirectUrl de confirmação explícita; login preservado. Logout da versão instalada usa scope LOCAL por padrão. |
| Recuperação | App já solicitava reset pelo SDK, agora com redirectUrl explícita de recovery. Nova página permite também solicitar recuperação no navegador, com resposta neutra sobre existência/entrega. |
| Android original | `org.example.project`; variante performance adiciona somente `.performance`. Manifest antes só tinha launcher; MainActivity não recebia App Links. Novos links usam HTTPS e caminho exato `/auth/open`, com onCreate/onNewIntent e singleTop. Identificadores e assinaturas preservados. |
| Rotas web antes | No institucional, `/auth/confirm`, `/auth/recovery` e assetlinks retornaram 404 durante a auditoria. O novo subdomínio não respondeu às consultas HTTPS desta execução. Isso não autoriza alterar a Vercel existente. |
| Templates/URLs Auth em uso | Não foi possível ler os valores remotos atuais: A consulta administrativa `db query` recebeu 403 ao iniciar login role; a CLI não oferece config get e não havia Dashboard disponível. O endpoint autenticado de configuração Auth não foi consultado. O cliente informou Resend configurado; GET público de settings confirmou email/signup ativos e confirmação obrigatória; não se assumiu conteúdo dos templates/allowlist atuais. Propostas novas abaixo, não aplicadas. Não foi extraída credencial do Keychain. |

Fontes: [site atual](https://tachegandoapp.com.br), [script de dados público auditado](https://tachegandoapp.com.br/js/data.js), [AuthConfig do SDK 3.1.2](https://github.com/supabase-community/supabase-kt/blob/3.1.2/Auth/src/commonMain/kotlin/io/github/jan/supabase/auth/AuthConfig.kt), [API do SDK 3.1.2](https://github.com/supabase-community/supabase-kt/blob/3.1.2/Auth/src/commonMain/kotlin/io/github/jan/supabase/auth/Auth.kt).

## Fluxo implementado

### Confirmação no navegador, sem login automático

1. O template proposto abre `/auth/confirm?token_hash=…&type=email`. O tipo email segue o formato oficial para confirmação de signup; recovery só aceita seu próprio tipo. Tipos, parâmetros extras/duplicados e redirects arbitrários são recusados.
2. GET **não** chama verify. O hash é selado em cookie criptografado HttpOnly e a resposta 303 retira a query. A página limpa também query/fragment legados do histórico, sem importar seus valores.
3. O usuário escolhe “Confirmar meu email”. POST exige cookie íntegro, CSRF e Origin exata; servidor chama o endpoint oficial `/auth/v1/verify` com token_hash e tipo. Só há sucesso com user.id e timestamp de email confirmado no resultado.
4. Se Auth emitir uma sessão transitória, o portal tenta encerrá-la com scope=local e descarta os tokens. Falha nesse encerramento é explícita. Não cria login no Android, não grava sessão comercial web e não toca nos cookies de outro usuário.
5. “Abrir aplicativo” usa **somente** `https://app.tachegandoapp.com.br/auth/open`, sem tokens, códigos, hash, evento de confirmação presumido ou parâmetros de destino. O app abre login quando não há sessão, restaura a identidade já salva em abertura fria e mantém a tela/sessão autenticada em nova intent.
6. Sem app/associação ativada, o navegador mostra fallback condicional e retorno ao institucional, sem inventar link de loja ou detecção de instalação.

Estado de link reutilizado só é específico quando há recibo criptografado de sucesso anterior neste navegador. Para respostas Auth ambíguas, a mensagem é “inválido, expirado ou já utilizado”; não é possível distinguir todos esses casos honestamente apenas pelo erro otp_expired. Timeout/429/5xx oferecem tentativa posterior, sem sucesso falso.

### Recuperação

- Sem token, `/auth/recovery` oferece solicitação por email. POST usa URL de retorno fixa e resposta “Se o email estiver cadastrado…”, sem enumeração ou promessa de entrega. Um HTTP de erro não vira “email enviado”.
- O link de recovery abre página sem consumir OTP; usuário valida por POST explícito.
- A sessão de recovery fica exclusivamente em cookie AES-GCM HttpOnly, por até 10min, sem refresh token, independente de qualquer sessão normal. Access/refresh não aparecem em URLs, HTML, JS ou localStorage.
- POST de nova senha exige CSRF, mesma origem e confirmação da senha, usa PUT oficial de user e confere identidade retornada. Após sucesso, o portal tenta logout **local dessa sessão**, descarta o token e orienta login com a nova senha. Erro de política ou serviço não vira sucesso; falha de logout é indicada.
- Não foi feita recuperação real nem alteração de senha dos consumidores neste trabalho.

Não há `/auth/callback` de sessão: não é necessário para este fluxo. A rota é 404; não configurar code/implicit callbacks nela. Caso futuramente seja introduzido PKCE/OAuth, avaliar armazenamento/vida do verifier e navegação no dispositivo/browser original. Uma code exchange sem verifier daquele fluxo deve falhar; não obter uma sessão Android inventada a partir da confirmação em outro navegador. O portal atual não troca authorization codes nem ativa PKCE no SDK.

Os POSTs explícitos reduzem consumo por scanners que fazem GET/prefetch, mas não provam humanidade contra robôs que submetam formulários. Não foi simulada proteção CAPTCHA. Detalhes e limites operacionais: [auth-portal/README.md](../auth-portal/README.md).

## App Links e certificados reais

Arquivo preparado: [assetlinks.json](../auth-portal/public/.well-known/assetlinks.json).

| Variante | Package | Certificado SHA-256 |
|---|---|---|
| debug | org.example.project | 0A:09:84:68:2D:45:DB:7E:56:4F:53:B6:83:24:1B:52:33:1B:A3:14:7F:34:2F:0F:B7:FB:7F:58:02:15:ED:E3 |
| performance | org.example.project.performance | Mesmo certificado debug local, confirmado por signingReport e apksigner no APK final. |
| homologation | org.example.project.homologation | Mesmo certificado debug local, confirmado no APK instalável; detalhes em [ANDROID_EXTERNAL_HOMOLOGATION.md](ANDROID_EXTERNAL_HOMOLOGATION.md). |
| release/Google Play | org.example.project | **Ainda indisponível**, confirmado pelo cliente. Nenhum fingerprint fictício ou senha antiga foi usado. |

O target `org.example.project` no JSON atual é **debug**, não uma declaração de assinatura release. A homologação pode prosseguir com esses certificados reais. Quando o certificado de assinatura do aplicativo existir em Google Play → Testar e lançar → Configuração → Integridade do app, acrescentar o SHA-256 oficial ao array desse target e revisar a associação de debug no domínio. Não confundir certificado de upload com app signing do Play.

Manifest registra apenas `https://app.tachegandoapp.com.br/auth/open`. Confirmação/recovery ficam no navegador, inclusive fora do Android. Código nativo rejeita qualquer query, fragment, outro host, porta ou path, não chama handleDeeplinks/importSession e não registra URIs. Recriação da Activity não reprocessa automaticamente o mesmo link de navegação.

O SDK já instalado fornece awaitInitialization/redirectUrl, usados sem dependências adicionais. Código comum permanece multiplataforma. iOS pode usar as páginas pelo navegador; Universal Links/AASA exigirão Team ID, bundle ID e entitlements oficiais, ainda não fornecidos. Não foi criado AASA fictício nem declarada integração iOS concluída.

## Resultados de validação

| Teste | Resultado e limite |
|---|---|
| APK debug + performance | BUILD SUCCESSFUL com ambiente homologation, R8 preservado em performance. Release não gerado sem credenciais do cliente. |
| Android unitários | **37 aprovados**, zero falhas/erros/skips; inclui rejeição de URLs com tokens, fragmentos, outros hosts, portas e redirects. |
| Lint debug | Zero erros, 41 avisos. Não foi silenciado lint. |
| Portal | **25 testes HTTP locais aprovados**, com Auth/transport fake: confirmação consumer/vendor, scanners GET, outro browser sem verifier, expirado/reutilizado, CSRF/Origin, erros, recuperação, resposta neutra, sessão isolada, logout local, open redirects, cookies por origem/operação e assetlinks. Não são emails/cadastros reais. |
| Runner existente | 11 testes locais de segurança/retomada aprovados. Servidor fake de loopback não foi contado como Supabase real. RUN real não reiniciado. |
| GETs da prévia | HTTP 200 para páginas e assetlinks locais, no-store/no-referrer, JSON no assetlinks. Nenhuma operação Auth feita por esses GETs. |
| Android instalado | APK performance instalado em AVD Pixel 9/API 37/ARM64 em modo somente leitura, sem alterar snapshots/configurações; app original org.example.project preservado. Não é validação em hardware de entrada. |
| App Link inicial | Intent explícita por componente abriu o login; não criou/importou sessão. |
| Nova intent | Link válido retornou de recuperação para login. Link com fragmento sintético de sessão foi ignorado, mantendo a tela atual. Nenhum token real usado no teste de rejeição. |
| Login real existente | ConsumerA entrou com suas credenciais do RUN; home/catálogo autenticado em sucesso. Sem signup ou troca de senha. |
| Sessão ativa | Com perfil autenticado aberto, nova intent válida manteve a mesma tela. |
| Sessão salva | Após parar somente o processo, novo link restaurou o catálogo autenticado pelo SDK. A asserção aguardou inicialização/carga de dados com limite; não é benchmark de TTFD. |
| Logout real Android | UI voltou ao login pelo logout local padrão do SDK 3.1.2. Comparação administrativa das outras sessões não disponível (403); não foi declarada revogação imediata de todos os JWTs. |
| Verificação pelo SO | `pm get-app-links` retornou `app.tachegandoapp.com.br: 1024`, **não aprovada**. Nenhum set-app-links/atalho foi usado para simular verificação. Depende da publicação HTTPS/assetlinks. |
| App não instalado | Fallback HTTP local testado; associação real browser→SO e dispositivo sem app ainda aguardam publicação. Não desinstalamos app nem apagamos dados para forçar o caso. |
| Confirmação/recovery reais | **Pendentes** de deploy, templates/URLs aprovados e ciclo de email autorizado. Não houve novos signups, reenvios ou alterações de senha reais. |
| QA visual browser | Não havia navegador disponível na ferramenta. Estrutura/estados HTTP testados, mas renderização final em browsers/dispositivos continua requisito de homologação. |

Evidências: [resultados agregados](integration/auth-web-android-results.json), [Android real](integration/android-app-links-local.json), [captura Android sem credenciais](integration/android-app-links-login.png).
Após os testes funcionais, somente imports Kotlin foram organizados; o empacotamento final passou novamente e o APK foi reinstalado. O JSON mantém hashes do artefato funcional e do final para rastreabilidade.

Logs completos locais: `/private/tmp/tachegando-app-links-packaging-final.log`, `/private/tmp/tachegando-app-links-final-build.log`, `/private/tmp/tachegando-auth-portal-tests.log`, `/private/tmp/tachegando-auth-runner-guards.log`, `/private/tmp/tachegando-auth-sdk-version.log`. Não são armazenamento permanente. A primeira prévia teve bind bloqueado pelo sandbox e foi iniciada depois com permissão local. A primeira asserção de restauração foi precoce; com espera limitada pela inicialização, o teste real passou. Nenhuma dessas tentativas foi contada como aprovação automática.

## Configurações exatas propostas para o Supabase

**Não aplicadas. Somente após aprovação e portal HTTPS funcional.** Antes de alterar, o responsável precisa capturar os valores atuais reais do Dashboard para revisão/rollback; o 403 atual impede presumir esse inventário.

Site URL:

```text
https://app.tachegandoapp.com.br
```

Redirect URLs, sem curingas:

```text
https://app.tachegandoapp.com.br/auth/confirm
https://app.tachegandoapp.com.br/auth/recovery
```

`/auth/open` é navegação pública gerada pela página, não redirect de uma sessão Supabase. `/auth/callback` não entra na allowlist. Não cadastrar URLs com token, localhost de desenvolvimento ou domínio externo como alternativa silenciosa.

Templates prontos para revisar:

| Supabase Email Template | Arquivo | Link emitido |
|---|---|---|
| Confirm signup | [confirm-signup.html](../auth-portal/deploy/email-templates/confirm-signup.html) | `https://app.tachegandoapp.com.br/auth/confirm?token_hash={{ .TokenHash }}&type=email` |
| Reset password | [recovery.html](../auth-portal/deploy/email-templates/recovery.html) | `https://app.tachegandoapp.com.br/auth/recovery?token_hash={{ .TokenHash }}&type=recovery` |

O HTML usa `&amp;` no href. O texto dos links não inclui o segredo. Não substituir esses links pela ConfirmationURL automática: ela pode consumir o OTP antes da página e devolver sessão em fragmento no fluxo legacy. A proposta não usa RedirectTo arbitrário. Manter confirmação obrigatória, SMTP Resend e limites existentes até autorização específica. Revisar tracking de links do provedor, que pode reescrever URLs; a documentação oficial recomenda desabilitá-lo para emails Auth.

[Templates e prefetch oficiais](https://supabase.com/docs/guides/auth/auth-email-templates), [Redirect URLs](https://supabase.com/docs/guides/auth/redirect-urls).

## Pendências Hostinger, DNS e implantação

O domínio institucional continua na Vercel. Este trabalho não autoriza apontar @/www para Hostinger ou substituir seus arquivos/SEO.

1. Confirmar um destino do cliente que execute Node 22+ (app Node gerenciado ou VPS); hospedagem apenas PHP/estática não executa este servidor. Não foi contratado serviço. Runtime local testado foi Node 26.13.0; Node 22/container precisa do gate na hospedagem/CI antes de publicar.
2. Raiz da aplicação: `auth-portal`. Start em produção: `node src/server.mjs`. Sem build frontend ou dependências para instalar. Dockerfile e Nginx de referência estão em `auth-portal/deploy/`; não executados remotamente.
3. Configurar env secrets do processo: APP_ORIGIN exata acima, URL Supabase do projeto, publishable key, COOKIE_ENCRYPTION_KEY aleatória de 32 bytes, NODE_ENV=production, HOST/PORT conforme plataforma. Chave de cookie não vai ao navegador/Git. Homologar inicialmente com uma réplica; escala requer controle compartilhado de concorrência/replay.
4. Criar **somente o registro DNS `app`**. Se Hostinger fornecer hostname de app, usar CNAME para esse valor real; em VPS, A/AAAA para IPs reais designados. O destino ainda não foi fornecido, portanto não há valor DNS inventado para copiar. Preservar @, www, MX e registros Resend/SPF/DKIM/DMARC.
5. Associar `app.tachegandoapp.com.br` à aplicação, emitir certificado HTTPS válido e garantir caminhos/queries intactos. Não redirecionar assetlinks a login ou ao institucional.
6. Garantir `https://app.tachegandoapp.com.br/.well-known/assetlinks.json` com HTTP 200, JSON válido e fingerprint do APK testado; nenhuma autenticação ou fallback SPA. Revisar cache e rate limits desse arquivo para não impedir o Android.
7. Aplicar proteção de borda: limites de corpo/requests, HTTPS e log redigido. O exemplo Nginx omite query do access log, mas não controla automaticamente logs de erro, WAF, edge ou tracking Resend. Verificar esses logs antes de emails reais: token_hash também é segredo de uso único. Não registrar request URI completa, corpo, cookies, senha ou tokens.
8. Só então aprovar e aplicar os templates/Site URL/allowlist no projeto exato de homologação; testar em browser, dispositivo com app e sem app. Links antigos já enviados não podem ser reescritos e podem manter o fluxo anterior até expirar.
9. Após associação pública, instalar/atualizar o APK e usar `pm verify-app-links --re-verify` + `pm get-app-links`, sem forçar estado aprovado. Abrir link sem `-n`; o teste por componente desta entrega não substitui a verificação real.
10. Ao autorizar um ciclo de email real, manter o RUN e consumidores existentes; retomar somente vendedores pendentes pelo mecanismo --run já preparado. **Esse comando não foi executado nesta etapa.** Testar consumo/reutilização/recovery em contas de teste com autorização; não alterar senha dos consumidores silenciosamente.

## Rollback preparado

- Capturar antes da implantação o inventário real de URLs/templates, artefato anterior do portal/APK e configuração do host. Não inventar valores de rollback nem restaurar localhost automaticamente.
- Na primeira implantação ainda não haverá uma versão anterior do portal. Em contingência, manter no host uma resposta temporária 503 nas novas rotas, sem chamar verify nem afirmar confirmação, preservando HTTPS e logs redigidos; não tocar no institucional.
- Em falha, interromper novos envios/testes, restaurar o artefato anterior somente do portal/subdomínio e a configuração Auth previamente aprovada. Não desativar confirmação, não mudar SMTP/limites ou rodar reset/migrations como atalho.
- Manter as rotas já presentes em emails em circulação pelo TTL de OTP configurado e margem operacional; retirar o subdomínio imediatamente quebraria esses links. Revisar com o cliente se o snapshot anterior era inseguro/inoperante antes de restaurá-lo.
- Cookie key estável permite manter fluxos pendentes; rotação deliberada os invalida e exige novo email, sem apagar usuários ou sessões Android. Tratar como mudança de segredo, com aprovação, não como solução automática.
- APK anterior somente com assinatura compatível e atualização preservando dados. Não desinstalar org.example.project para contornar certificado. Alterações locais devem ser revertidas isoladamente após revisão, preservando o restante da Sprint 1; nunca resetar a branch inteira.
- Nenhum rollback remoto foi executado ou necessário nesta execução.

## Arquivos desta implementação

- `auth-portal/`: projeto novo independente autorizado, servidor/config/crypto/render/Auth transport, CSS/logo, assetlinks, templates, exemplos de hospedagem, lockfile, documentação e testes.
- `KotlinProject/composeApp/src/commonMain/kotlin/org/example/project/core/data/AuthLinks.kt`: URLs canônicas e validação estrita.
- `…/core/data/AuthRepository.kt`: redirectUrl oficial de signup/recovery e espera de inicialização para entrada por link.
- `…/App.kt` e `…/core/navigation/AppNavigation.kt`: navegação pública preservando sessão, sem importar tokens.
- `KotlinProject/composeApp/src/androidMain/AndroidManifest.xml` e `…/MainActivity.kt`: caminho HTTPS restrito, onCreate/onNewIntent e singleTop.
- `KotlinProject/composeApp/src/commonTest/kotlin/org/example/project/core/auth/AuthLinksTest.kt`.
- Este relatório e as evidências `docs/integration/auth-web-android-results.json`, `android-app-links-local.json` e captura Android.

Configuração local do portal está em `.env` ignorado, permissões 0600. Nenhum certificado privado, credencial SMTP, chave administrativa ou sessão entrou no relatório ou no código-fonte.
