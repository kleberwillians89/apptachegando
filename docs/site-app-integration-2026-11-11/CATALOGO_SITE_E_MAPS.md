# Integração do cadastro completo no site — contrato proposto

**Status:** código preparado em branch. **Não instalado no Supabase QA, não publicado no site, não testado em Android real.**

## Sequência do vendedor

1. Site coleta nome, e-mail, senha, nome comercial, categoria e uma lista de 1–30 produtos prontos (1–160 caracteres cada). **Nunca enviar senha por e-mail**.
2. `supabase.auth.signUp()` cria a identidade e dispara o fluxo de confirmação. Não usar `user_metadata` para autorização.
3. Portal confirma o e-mail por token hash e POST. O usuário autentica no site.
4. **Fluxo preferencial sem segundo login:** o portal Node, após verificar o e-mail via token hash e POST, usa apenas o access token transitório da sessão confirmada para chamar `complete_vendor_onboarding` com o rascunho de produtos presente em `user_metadata`. O portal descarta a sessão após a RPC, com logout local. O navegador **não** recebe esse token. Caso essa etapa falhe, o e-mail permanece confirmado, a tela informa a pendência e o login web pode concluir o cadastro por meio da mesma RPC idempotente.
5. Após resposta positiva, orienta entrar no Android com as mesmas credenciais; não é necessário fazer login no site outra vez.
6. Android resolve `vendors.user_id` pela identidade e carrega `public.products` pelo `vendor_id`. Rota/GPS são configurados e ativados no Android, nunca inventados no site.

## Nova RPC de onboarding

Migration revisável: `supabase/migrations/20261111170000_site_vendor_catalogue_onboarding.sql`.

Depende de RC1 e de `20261111135508_web_app_identity_and_discovery.sql`, já informadas como instaladas no QA. **Exige uma nova aprovação e verificação antes de aplicação remota.**

```javascript
const { data: session, error: sessionError } = await supabase.auth.getUser();
if (sessionError || !session?.user?.email_confirmed_at) throw new Error("Confirme o e-mail antes de concluir.");

const { data, error } = await supabase.rpc("complete_vendor_onboarding", {
  p_name: fullName.trim(),
  p_business_name: businessName.trim(),
  p_category: category,
  p_product_names: productNames.map(name => name.trim()),
  p_phone: phone?.trim() || null
});
if (error) throw error;
// data.profile.vendor_id, data.products, data.product_count, data.catalogue_initialized
```

- Requer usuário confirmado e sessão Auth ativa no servidor; identifica o proprietário com `auth.uid()`, não recebe `vendor_id` ou `user_id` do browser.
- A função utiliza trava transacional por usuário, invoca `complete_my_profile` e cria o catálogo inicial **somente quando o vendedor não possui produtos**. Repetições não sobrescrevem produtos, rotas ou dados já existentes.
- Os produtos têm `price=0` porque essa coluna é legada e obrigatória. **Nunca apresentar preço, checkout ou venda no produto.**
- Retorna somente ID, nome e disponibilidade dos produtos próprios, sem dados de outros usuários.
- O formulário em `/cadastro` transmite a lista de produtos em `user_metadata.onboarding_products` **somente como rascunho não autoritativo** (sem senha, preço ou segredos), para permitir conclusão mesmo em outro navegador. O portal valida e a RPC verifica sessão confirmada, intenção `signup_role` e propriedade do registro. A metadata é editável e nunca autoriza operações. Não gravar senhas ou tokens em `localStorage`, query string ou logs.
- O site atualmente validado pelo Claude em clone ainda não foi encontrado publicado com `novo/js/config.js` e `novo/js/conta.js`; integrar neste código validado, não no protótipo `localStorage` antigo.
- Não ativar produção sem: migration aplicada e validada, portal HTTPS, SMTP confirmado, site em Preview com publishable key e teste real.

## Checklist de verificação antes de deploy

- Teste em PostgreSQL 17 isolado: profile + 3 itens; repetição idempotente; duas chamadas simultâneas; produtos existentes intactos; erro em nome inválido/duplicado; usuário consumidor e sessão revogada; erro faz rollback de todo onboarding.
- RLS: conta A não consegue criar ou alterar itens da conta B, usuário anônimo não executa a RPC, `service_role` nunca aparece no navegador.
- Site Preview: formulário permanece no modo protótipo na branch pública até autorização; cadastro real tem confirmação por e-mail, onboarding real e produtos visíveis após login Android.
- Execução QA requer autorização separada; não rodar `db push` indiscriminado.

## Diagnóstico Google Maps

Configuração atual do app: `AndroidManifest.xml` injeta `googleMapsApiKey` via Gradle; o target QA possui application ID **`org.example.project.homologation`** e utiliza assinatura de debug local.

Fingerprint SHA-1 QA informado anteriormente: **`4A:63:58:B2:F7:19:1E:9C:70:C7:0A:77:7D:20:DC:9D:D3:86:7D:58`**. Conferir na máquina de build com `./gradlew :composeApp:signingReport`; não presumir a assinatura de outro computador.

No Google Cloud Console:
1. Conferir se o projeto associado à chave de Maps está correto e se o faturamento está ativo.
2. Conferir **Maps SDK for Android** habilitado.
3. Restrição Android da chave: incluir o pacote QA exato e a assinatura SHA-1 do artefato que está realmente instalado. Adicionar separadamente o pacote e certificado de produção quando existirem.
4. Conferir restrições de API compatíveis com Maps SDK for Android. Não desligar restrições nem publicar a chave.
5. Conferir se `GOOGLE_MAPS_API_KEY` foi preenchida na configuração do build QA; build vazio não testa Maps.

Quando o ambiente de infraestrutura estiver pronto e formos testar **uma única versão candidata**, executar:
```bash
adb -s emulator-5554 logcat -c
adb -s emulator-5554 logcat -v time -s TCMaps:V Google Maps Android API:V GoogleMap:V *:S
```
O tag `TCMaps` agora reporta timeout de 20 s ou o callback `onMapLoaded`. Ele auxilia o diagnóstico, mas **não confirma sozinho o desenho dos tiles**. Registrar também erros do Maps SDK, certificado, permissões e rede. Não iniciar novos emuladores ou gerar APK intermediário somente para observar logs.

## O que ainda falta após este PR

1. Revisão e testes SQL da nova RPC; aplicação QA autorizada e verificação pós-instalação.
2. Publicar a integração web preparada em patches v2, acrescentando o formulário de produtos e chamada RPC.
3. Hospedar `auth-portal` em `app.tachegandoapp.com.br` com HTTPS; validar templates `TokenHash` e envio SMTP Resend com `ola@tachegandoapp.com.br`.
4. Validar restrições da chave Google Maps no Cloud Console.
5. Configurar Firebase/worker e provar push em segundo plano em dispositivo (fora da meta de cadastro inicial).
6. Uma rodada real: site → confirmação → perfil+catálogo → login Android → mapa → rota → descoberta → interesse → fila → cancelamento.
