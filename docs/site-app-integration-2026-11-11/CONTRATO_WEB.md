# Contrato web → Supabase → Android — v1

Estado: implementado e validado localmente; NÃO instalado remotamente nem homologado. Sem dependência do repositório web para implementar os contratos. O site deverá integrar este contrato quando estiver disponível.

## Identidade e compatibilidade

`auth.users.id` é a única identidade. `profiles.id` referencia esse usuário. `vendors.id` é um identificador profissional diferente; `vendors.user_id` é único e referencia o perfil autenticado. O login Android usa senha no Supabase Auth e consulta `get_my_account_profile`; nenhuma sessão do navegador é importada.

Antes desta sprint, o trigger criava vendedores diretamente do campo `role` no cadastro. A nova migration mantém os vendedores existentes, mas muda somente os futuros cadastros: o trigger cria `profiles` e guarda `signup_role` como intenção de onboarding. Ele NÃO cria vendedores. Uma identidade confirmada, não anônima, com sessão Auth ativa chama `complete_my_profile`. A escolha de ser vendedor é uma ação de autoinscrição do produto, sem poderes administrativos; nunca se aceita user_id ou vendor_id fornecido pelo cliente para criar o vínculo.

A resolução de papel usa o vínculo persistido, não user_metadata/JWT role de negócio:

- Vínculo `vendors.user_id` existente → `vendor`, inclusive contas QA anteriores.
- Sem vínculo e `signup_role=vendor` → `vendor_incomplete`.
- Sem vínculo e sem solicitação profissional → `consumer`.
- Consulta ou sessão inválida → erro, nunca consumidor presumido.

O Android mantém o login na tela existente quando o profissional está incompleto e oferece concluir nome, nome comercial e categoria. Confirmação continua obrigatória antes dessa operação. Cadastro nativo e web compartilham o mesmo contrato; senhas de contas antigas não são submetidas às regras novas de cadastro.

## Cadastro público

Formulário: `name`, `email`, `password`, `role`; para vendedor também `business_name` e `category`. Validação em [registration.schema.json](registration.schema.json). Valores de role são **consumer/vendor**, não nomes traduzidos. Categoria profissional: Pães, Frutas, Salgados, Doces de padaria, Lanches naturais, Sorvetes, Temperos ou Produtos de limpeza prontos.

Exemplo para supabase-js (o repositório web deverá fixar sua versão no lockfile):

```js
const { data, error } = await supabase.auth.signUp({
  email: form.email.trim(),
  password: form.password,
  options: {
    emailRedirectTo: 'https://app.tachegandoapp.com.br/auth/confirm',
    data: {
      registration_contract: 'web-v1',
      name: form.name.trim(),
      full_name: form.name.trim(),
      role: form.role,
      ...(form.role === 'vendor' ? {
        business_name: form.business_name.trim(),
        category: form.category
      } : {})
    }
  }
});
```

Esses metadados são dados declarados pelo usuário, úteis para preencher o formulário após login. Não autorizam acesso. Nunca guardar senha nos metadados, em logs ou no armazenamento do formulário. HTTP 200/signup não comprova e-mail recebido, conta inédita ou confirmação; o Supabase pode fornecer resposta ofuscada para evitar enumeração. Não repetir signup para reenviar confirmação.

## Operações necessárias ao site

Todas as RPCs públicas novas são SECURITY INVOKER com EXECUTE apenas para authenticated. Seus helpers privados fazem as verificações de auth.uid, confirmação real de e-mail e sessão ativa em auth.sessions; tabelas privadas não ficam expostas. Nenhuma RPC recebe identidade de terceiro. `profiles` e `vendors` mantêm RLS/grants existentes.

| Operação | Método / identidade | Parâmetros | Resposta / repetição / erros |
|---|---|---|---|
| Criar usuário | `auth.signUp`, publishable key | E-mail, senha, options acima | Resultado Auth; mensagem neutra; não assumir entrega ou conta nova. Não cria vendedor por metadata. |
| Confirmar e-mail | Template Auth → portal `/auth/confirm?token_hash=…&type=email` | Token de uso único; ação POST explícita no portal | Portal chama verify, exige usuário realmente confirmado e limpa sessão transitória. GET de scanner não consome token. Link expirado/reutilizado/desconhecido pode ser ambíguo; não inventar sucesso. |
| Consultar confirmação | `auth.getUser()` com sessão do próprio usuário | Sem e-mail de terceiro | Conferir `email_confirmed_at`; erro/ausência de sessão não é confirmado. Não criar endpoint público de consulta por e-mail. |
| Entrar | `auth.signInWithPassword` no site; Email provider no Android | E-mail/senha | Sessões independentes para a mesma identidade; conta não confirmada recebe orientação. Nunca transferir token manualmente para Android. |
| Consultar perfil | RPC `get_my_account_profile()`; JWT confirmado/sessão ativa | Nenhum | `{user_id,role,vendor_id,name,business_name,category}`; role consumer/vendor/vendor_incomplete. Repetir é somente leitura; falha não vira consumer. |
| Finalizar consumidor | RPC `complete_my_profile`; JWT confirmado/sessão ativa | `p_kind:'consumer'`, `p_name`, `p_phone?` | Mesmo perfil, sem criar vendedor. Não permite converter profissional existente/solicitado em consumidor por esse endpoint. |
| Finalizar profissional | Mesma RPC | `p_kind:'vendor'`, `p_name`, `p_business_name`, `p_category`, `p_phone?` | Resolve auth.uid; trava por usuário e UNIQUE(user_id); retorna o mesmo vendor_id em repetição/concorrência. Repetição preserva vendedor existente e seus dados, não é endpoint de edição. |
| Reenviar confirmação | `auth.resend({type:'signup',email,options:{emailRedirectTo:…}})` | E-mail válido, redirect fixo | Mensagem condicional, sem afirmar entrega ou existência. No Android há cooldown global de 60 s; no portal `/auth/confirm` há formulário com CSRF, cooldown por fluxo e digest de e-mail mantido em memória por instância, além dos limites do Supabase/provedor. Falha de rede/serviço permanece erro sanitizado. |
| Recuperar senha | `auth.resetPasswordForEmail(email,{redirectTo:…/auth/recovery})` | E-mail | Portal verifica token antes de alterar senha; respostas neutras e falhas honestas. Não encerrar sessões Android através de logout global do portal. |
| Logout/renovação | SDK Auth; logout local e refresh oficial | Sessão própria | O Android preserva auto-load/save/refresh do SDK. RPCs novas rejeitam sessão excluída/expirada mesmo com access JWT ainda não expirado. |
| Solicitar exclusão | RPC `request_account_deletion()`; JWT confirmado/sessão ativa | Nenhum | `{status:'requested',requested_at}` idempotente. Somente registra pedido privado: NÃO exclui conta/dados nem promete conclusão. Processador, política, prazo e interfaces de exclusão seguem pendentes; não declarar cumprimento integral da Play Store. |

Validações do perfil: nome/comercial 1–160 caracteres, telefone até 32 caracteres, categoria da lista fechada. Confirmação/sessão/anon inválidos: SQLSTATE 42501. Campos inválidos: 22023. Perfil indisponível: erro, sem fallback. Limites e erros Auth são independentes desses SQLSTATEs. Não renderizar payloads de erro brutos.

## Descoberta e estado de interesse

`discover_vendors(p_latitude,p_longitude,p_query='',p_category='',p_offset=0,p_limit=50,p_radius_m=5000)`:

- JWT confirmado, sessão ativa, coordenadas válidas; limite 1–50, offset 0–10000, raio 100–50000 m.
- Vendedor online, outro usuário, rota ativa, GPS até 30 s/precisão até 50 m, consumidor no corredor e à frente, projeção não ambígua.
- Raio padrão de 5 km medido entre vendedor e consumidor; filtro e ordenação por distância/ID antes do offset/limit.
- Nome/categoria/produto são filtrados no servidor. Retorno compatível com Vendor, sem preços nem user_id/telefone/geometria/posições de outros consumidores.
- Compatível com publicação de posição legada recente. Havendo progresso estrito RC1, um fix inválido/inconsistente não pode ser substituído por timestamp legado para ganhar elegibilidade.
- Android usa o contrato no mapa/busca e nas categorias. Sem GPS do consumidor, não mostra catálogo global como descoberta geográfica; oferece orientação e nova tentativa. O ponto do consumidor não é publicado por essa consulta.

`create_purchase_intent` e `cancel_purchase_intent` são preservados. `get_my_interest_state(p_intent_id)` só lê protocolo do próprio consumidor e retorna `{id,vendor_id,status,expires_at,eligibility}`. Condições: `eligible`, `awaiting_availability`, `vendor_location_missing`, `awaiting_location`, `outside_route`, `behind_route`, `ambiguous_route`, `cancelled`, `expired`. É projeção; nenhum novo status é persistido em purchase_intents. Registro não implica venda, estoque, atendimento garantido ou push entregue. As condições específicas de envio push (token, instalação, corte temporal) continuam adicionais.

O Android confirma o protocolo persistido, consulta essa projeção e reconcilia a cada 10 segundos enquanto a tela está ativa; falha de leitura é mostrada. A reabertura consulta intenções ainda pendentes; isto não cria histórico novo. Cancelamento e idempotência do protocolo existente permanecem intactos.

## Instalação e dependências externas

1. Revisar metadados remotos por leitura; confirmar migrations anteriores e contratos RC1, auth.users.email_confirmed_at e auth.sessions. Não presumir instalação pelo nome do arquivo.
2. Aprovar e aplicar separadamente `20261111135508_web_app_identity_and_discovery.sql` em QA. Não executar `db push` indiscriminado. A migration depende de `20261104210040_interest_push_rc1.sql`; esta sprint não aplicou nenhuma remotamente.
3. Coordenar backend com Android/site: o app atualizado exige as novas RPCs e falha explicitamente se ausentes. O trigger novo altera provisionamento de cadastros futuros; clientes antigos devem ser atualizados ou o cadastro deve ser direcionado ao site preparado. Não alterar os QA existentes.
4. Publicar portal HTTPS, DNS e assetlinks; configurar Site URL, redirects exatos de confirmação/recuperação e os templates já preparados. O template de confirmação usa TokenHash/type=email; não apontar apenas para uma página estática.
5. Configurar SMTP com domínio/remetente verificados, SPF/DKIM/DMARC, limites e tracking de links que não deforme tokens. Nada foi enviado ou alterado nesta sprint.
6. Integrar o repositório web com publishable key do mesmo projeto QA. Servidor administrativo nunca vai no browser/APK. Não usar um projeto diferente esperando que contas sejam compartilhadas.
7. Autorizar posteriormente cadastro sintético real e jornada em dois Androids. Hoje os testes usam Auth shim/transportes controlados; não são homologação de Auth/SMTP/FCM.

Convite `invite` não é suportado. Maps vazio, ativação Firebase/worker e configuração de produção continuam assuntos externos a este contrato. Não houve deploy, push, aplicação remota ou geração de APK.
