# TáChegando — portal de autenticação

Projeto independente autorizado para **https://app.tachegandoapp.com.br**. Não modifica o institucional/Vercel nem `investor-hub`. Node 22+ com SSR/formulários HTML, zero dependências de runtime. APIs oficiais Supabase Auth via HTTP no servidor; somente publishable key. Não é um painel comercial nem substitui o login do aplicativo.

## Rodar localmente

```bash
cd auth-portal
cp -n .env.example .env
chmod 600 .env
# Preencher SUPABASE_PUBLISHABLE_KEY pública e COOKIE_ENCRYPTION_KEY no arquivo privado.
node -e "process.stdout.write(require('node:crypto').randomBytes(32).toString('base64')+'\n')"
# Usar a chave aleatória acima SOMENTE como COOKIE_ENCRYPTION_KEY (não é chave Supabase).
# Para localhost: APP_ORIGIN=http://127.0.0.1:3000; HOST=127.0.0.1; PORT=3000.
npm start
```

Para inspecionar páginas sem executar operações de autenticação:

```bash
npm run inspect
```

Esse modo exige origem HTTP loopback e mostra um aviso em todas as páginas. Todos os POSTs de autenticação retornam HTTP 503 antes de acessar o Supabase, incluindo confirmação, solicitação de recuperação e alteração de senha. A prévia local em `http://127.0.0.1:4178` está nesse modo. Não abrir links reais de email para esta inspeção. `npm start` mantém o fluxo funcional destinado à implantação futura e não oferece esse bloqueio.

Não sobrescrever um `.env` já preenchido. Nesta execução foi preparada uma prévia local em `http://127.0.0.1:4178` com arquivo privado ignorado.

O comando de geração imprime um segredo de servidor novo; executar no terminal privado e não copiar a saída para issues/chats. Em hospedagem usar secret/env manager e `node src/server.mjs`, sem `.env` no artefato. O servidor não lê automaticamente o `.env` do repositório pai. A URL Supabase é restrita a `vhnkezwjknwpddqhlnfk`; somente origem loopback de desenvolvimento permite um upstream local de teste.

```bash
npm test
npm run check
```

Os testes injetam Auth fake ou transporte fake em loopback. **Não criam usuários reais nem certificam envio/consumo de emails no Supabase.** O resultado real de Android e as pendências externas estão em `../docs/AUTH_WEB_ANDROID.md`.

## Rotas

| Rota | Operação |
|---|---|
| `/` | Página de orientação/retorno ao app e ao institucional; sem sessão. |
| `/auth/confirm?token_hash=…&type=email` | GET valida formato, sela o hash em cookie HttpOnly e redireciona 303 para a mesma rota sem query. Não verifica nem consome OTP. |
| `/auth/confirm` | GET mostra confirmação explícita; POST com CSRF e Origin exata chama `/auth/v1/verify`. Só afirma confirmação se o servidor retorna user.id e email_confirmed_at. |
| `/auth/recovery?token_hash=…&type=recovery` | GET prepara fluxo separado, sem consumir OTP. Sem token, oferece solicitação de recuperação por email. |
| `/auth/recovery` | POST explícito verifica OTP; sessão transitória exclusivamente em cookie criptografado HttpOnly; um POST de solicitação usa resposta neutra; outro POST altera senha pela API oficial e tenta encerrar somente essa sessão. |
| `/auth/open` | Link público sem query/fragment para o Android. Navegador mostra fallback honesto se não abrir. Sem detecção falsa de instalação ou links inventados de loja. |
| `/.well-known/assetlinks.json` | Associação real de debug/performance/homologation; produção pendente de SHA-256 oficial. |

Não há callback de sessão/PKCE implementado. `/auth/callback` é 404, sem importação de code/token. Toda página remove fragmentos legados do histórico, sem ler/importar seus valores. Não configurar essa rota como redirect. O fluxo escolhido usa token_hash; não transporta sessão para Android.

## Segurança e limites

- OTP só é consumido por POST explícito. GET/HEAD/prefetch não chama Auth. Isso reduz consumo por scanners de links; não prova presença humana contra robôs que deliberadamente enviam formulários. Não foi adicionada CAPTCHA fictícia.
- Access/refresh tokens não aparecem em HTML, links, JavaScript ou localStorage. Cookies de fluxo são host-only, AES-256-GCM, HttpOnly, Secure em HTTPS, com AAD separada por origem e operação. SameSite=Lax permite a navegação inicial vinda do email; mudanças exigem Origin exata e CSRF. Não se confia em `next`, host do request ou URLs arbitrárias.
- Confirmação pode fazer o Auth emitir uma sessão transitória no servidor. O portal tenta revogar apenas essa sessão com logout scope=local, descarta os tokens e não cria login web/Android. Falha no encerramento é informada sem negar uma confirmação já comprovada.
- Recuperação mantém só access token criptografado, por no máximo dez minutos, sem refresh. Não substitui cookies/sessões comerciais de outro usuário. Após senha confirmada, descarta o token; erro de logout transitório é explícito.
- Hash é um segredo de uso único no link de email, embora não seja access/refresh token. Ele é retirado da URL de navegação em 303 e nunca ecoado em páginas. Proxy/edge/Hostinger/Resend precisam de política de logging/tracking que não registre query, cookies ou corpos. O código sozinho não controla logs externos.
- `no-store`, `no-referrer`, noindex e CSP restrita em todos os fluxos; sem analytics ou recursos de terceiros. Nenhum logger de request ou exceção bruta.
- O Auth geralmente não distingue token expirado de já consumido. Sem prova, a UI mostra “inválido, expirado ou já utilizado”. Só mostra reutilização conhecida se há recibo criptografado anterior de confirmação neste navegador. Não simula estado de conta.
- Solicitar recuperação pelo navegador não revela se a conta existe e não promete entrega do email. A URL de retorno é fixa, sem next/redirect de formulário. Erros e rate limits mantêm a opção de tentar novamente.
- Cookies são temporários (15min para confirmar; até 10min para senha). Recibos e proteção de concorrência são limitados ao processo. Homologar com **uma réplica**. Escala horizontal exige proteção de replay/concorrência compartilhada antes de aprovação.
- Publicação exige HTTPS real, rate limiting/WAF de borda, limite de corpo e revisão de logs. O exemplo Nginx limita a 8KiB e evita query no access log; revisar também error logs/edge logs do provedor.
- A chave de cookie deve ser estável e comum às réplicas. Rotacioná-la invalida fluxos pendentes deste portal, não as sessões existentes no Supabase/Android. Não usar service_role, PAT ou senha SMTP como chave de cookie.

## Implantação preparada, não executada

`deploy/Dockerfile` é opcional; nenhuma imagem foi construída/publicada. `deploy/nginx.example.conf` é referência a revisar, não substitui a configuração do site institucional. Usar apenas o novo subdomínio. Hospedagem precisa suportar processo Node 22+ (aplicação Node gerenciada ou VPS), não apenas upload estático/PHP.

DNS, URLs exatas, templates e rollback estão em `../docs/AUTH_WEB_ANDROID.md`. Os arquivos em `deploy/email-templates/` são propostas para revisão; não foram aplicados no Supabase/Resend.

## Certificados

`public/.well-known/assetlinks.json` contém **somente certificados locais medidos**, sem placeholder de release:

- `org.example.project.homologation`: assinatura debug local; variante instalável de QA, não depurável, com R8.
- `org.example.project.performance`: assinatura debug local usada pela variante performance.
- `org.example.project`: certificado de **debug**, não o certificado Google Play de release.
- SHA-256: `0A:09:84:68:2D:45:DB:7E:56:4F:53:B6:83:24:1B:52:33:1B:A3:14:7F:34:2F:0F:B7:FB:7F:58:02:15:ED:E3`.

Quando houver o certificado público de assinatura do app em Google Play → Testar e lançar → Configuração → Integridade do app, acrescentar seu SHA-256 real ao target `org.example.project`, revisar a política de manter debug nesse domínio e validar novamente. Não trocar applicationId, keystore ou fingerprint por um valor inventado. Homologação com debug/performance pode prosseguir sem release.
