# Integração real Supabase — execução futura autorizada

Node 22+; sem dependências npm. O runner usa HTTP Auth/PostgREST e WebSocket Realtime reais. Não aplica migrations, não usa chave administrativa e não altera configuração de infraestrutura.

**Execução real parcial registrada em `docs/SUPABASE_HOMOLOGATION_RESULT.md`; a suíte completa ainda não foi aprovada.** Testes offline dos bloqueios não contam como integração aprovada. O ambiente deve ser exclusivo de homologação ou local descartável. O responsável deve confirmar no Dashboard que o project ref é de homologação: o runner não consulta a Management API nem consegue identificar um projeto de produção rotulado incorretamente no JSON. A execução cria contas e dados sintéticos; não usar contas de clientes reais.

## Configuração e bloqueios

```bash
mkdir -p .local
cp scripts/integration/target.example.json .local/integration-target.json
chmod 600 .local/integration-target.json
```

Editar o JSON local: environment=`homologation`, projectRef do cliente, URL HTTPS exata, publishableKey pública e emailDomain controlado. emailPrefix usa aliases `prefixo+RUN-consumera@dominio`; confirmar que o provedor entrega esses aliases. Senha/SMTP ou chave de servidor não entram nesse arquivo. `.local` já é ignorado.

```bash
node scripts/integration/supabase.mjs --config .local/integration-target.json --phase validate
node --test scripts/integration/guards.test.mjs
```

`validate` é offline e não autentica a chave nem comprova o schema. Bloqueios: produção, projeto legado, destino diferente do project ref, URL com credenciais/path/query, chave service_role/secret e falta da autorização explícita de escrita. Local aceita apenas loopback com `--allow-local-writes`; não aceita rede LAN fingindo ser local.

## Cadastro com confirmação de email

Somente depois de autorização de escrita na homologação do cliente:

```bash
node scripts/integration/supabase.mjs \
  --config .local/integration-target.json --phase signup \
  --allow-remote-writes --project-ref "$TCH_HOMOLOG_REF"
```

Cria consumidor A/B e vendedor A/B, com senhas aleatórias. Salva credenciais em `.local/integration/RUN.json`, permissões 0600; não imprime senhas, chaves ou tokens. Quatro contas permitem testar isolamento entre usuários do mesmo papel. Os seeds SQL sem senha não substituem esses cadastros reais.

Se confirmação de email estiver ativa, abrir os emails de teste e confirmar antes de verify. Não desativar confirmação de produção nem usar auto-confirmação administrativa para fingir que o fluxo real passou. Provedor padrão pode limitar envios/destinatários; configurar SMTP autorizado e caixas controladas. Erro/rate limit impede concluir signup; os cadastros parciais permanecem documentados no arquivo privado. Cadastro aceito pelo servidor não significa suíte completa aprovada.

## Retomar cadastro parcial sem duplicar contas

Se o signup parou por erro/rate limit, preservar o RUN e as credenciais existentes. Após resolver a causa, usar `--run` também na fase signup; contas com identidade já aceita serão ignoradas e senhas pendentes preservadas. Não executar novamente signup sem `--run`, pois isso criaria outro conjunto de contas.

```bash
node scripts/integration/supabase.mjs \
  --config .local/integration-target.json --phase signup \
  --run .local/integration/RUN.json \
  --allow-remote-writes --project-ref "$TCH_HOMOLOG_REF"
```

Só retoma arquivo da mesma URL/ref/ambiente e com resultados anteriores de signup; não retoma um RUN já usado por verify. Erros HTTP futuros registram apenas status e código de API sanitizado, sem resposta bruta ou credenciais. Falhas anteriores permanecem no histórico de resultados.

Os testes `node --test scripts/integration/guards.test.mjs scripts/integration/resume.test.mjs` usam apenas validação local e servidor fake em loopback para comprovar que a retomada não recadastra consumidores. Esses testes **não** são evidência de integração Supabase real.

## Login, dados e Realtime

Usar o RUN retornado pelo cadastro; não reutilizar arquivo de outro projeto:

```bash
node scripts/integration/supabase.mjs \
  --config .local/integration-target.json --phase verify \
  --run .local/integration/RUN.json \
  --allow-remote-writes --project-ref "$TCH_HOMOLOG_REF"
```

Em stack local, trocar environment/url/chave e usar `--allow-local-writes`, sem `--allow-remote-writes`. O runner não descobre nem usa credenciais antigas automaticamente.

| Passo | Evidência exigida no servidor |
|---|---|
| Cadastro | Identidade Auth real para as quatro contas; perfil e vínculo vendedor conferidos após login |
| Login/JWT | Senha gera access/refresh token; `/auth/v1/user` confirma identidade; sub e role são coerentes |
| JWT/RLS | Chave anônima e assinatura JWT adulterada não leem profiles; usuário só lê o próprio perfil |
| Produtos | Criar, ler, atualizar preço/nome e remover; outro vendedor não altera; consumidor não cria |
| Catálogo | Vendedor online visível, produtos projetados da tabela normalizada; offline deixa de ser visível |
| Localização | RPC resolve vendedor autenticado, posição aparece no catálogo; consumidor não publica posição |
| Rotas | Buffer real, UUID persistido, nova requisição recupera polígono/dias; terceiro não lê/encerra; encerramento persiste |
| Intenção | Mesmo pedido retorna mesmo ID; outro consumidor/vendedor não lê; RPC da rota só retorna o destino autorizado |
| Realtime | Confirma assinatura Postgres Changes e observa UPDATE real em vendors, products e vendor_locations, com JWT de consumidor e filtro por ID |
| Logout | Endpoint local de logout responde; refresh token anterior é rejeitado com código de sessão/token revogado |

JWT de acesso pode continuar válido até expirar após logout; não há alegação de revogação instantânea em RLS. O teste de logout verifica a sessão/refresh, não uma garantia inexistente sobre todos os access tokens já emitidos.

Cada PASS só é registrado depois da respectiva resposta/evento. Exit 0 em verify significa todos os passos concluídos; 2 significa pré-condição bloqueada; 1 significa falha. O arquivo `.results.json` registra passos concluídos e `completeIntegration`; nunca inclui tokens/senhas. Fluxo interrompido não recebe aprovação global. WebSocket tem limite de 20 segundos; ausência de evento é falha, não simulação.

O runner não limpa contas Auth nem intenções/rotas com privilégios administrativos. Em sucesso, remove o próprio produto, encerra a própria rota e deixa seus vendedores offline. Em falha, pode deixar fixtures parcialmente criadas: operador deve identificar o RUN e planejar limpeza autorizada. Para repetir verify de ponta a ponta, criar um RUN novo; tokens de sessão anteriores foram revogados e fixtures podem estar encerradas. Não usar DELETE global ou reset em banco compartilhado.

## Consistência revista dos contratos

| Kotlin | Contrato SQL/HTTP |
|---|---|
| AuthRepository | Trigger Auth → profiles.id=auth.uid; vendors.user_id UNIQUE; vendor.id independente |
| ProductRecord/ProductRepository | products.price numeric(10,2); envio decimal textual aceito pelo PostgREST, leitura numeric → JsonPrimitive → formato decimal |
| VendorRepository | vendor_catalog retorna campos textuais não nulos, products JSON agregado e lat/lng opcionais; assinaturas nas tabelas de base |
| RouteRecord/RouteRepository | active_days text[] → array JSON; waypoints JSONB com latitude/longitude/orderIndex; polygon com coordenadas [lng,lat] |
| create_route_buffer | points, tolerance_meters → array de anéis |
| get_intents_for_route | p_route_id → id/consumer_id/lat/lng/status |
| update_vendor_location | p_latitude/p_longitude, p_accuracy_m opcional; identidade obtida do JWT; retorno void pode ser HTTP 204 |
| create_purchase_intent | p_vendor_id/p_latitude/p_longitude → UUID confirmado; consumidor obtido do JWT |

Não foi necessária alteração dos contratos ou das duas migrations. A revisão e os 43 testes SQL existentes não comprovam sozinhos a serialização/integração HTTP. O runner acima é o gate futuro para essa evidência. [Protocolo oficial Realtime](https://supabase.com/docs/guides/realtime/protocol).
