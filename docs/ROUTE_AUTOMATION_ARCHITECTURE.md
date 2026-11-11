# Atendimento automático por rota — arquitetura local

## Continuidade com o projeto

`VendorRoute/RouteWaypoint` → `RouteRepository` + `DemoRouteSession` → `ERREngine.automaticAttendance` → `RouteAttendance` → `RouteAttendancePanel`.

`PurchaseIntent` continua sendo o modelo de interesse. `RouteInterestUpdate` é um envelope com vendedor, revisão e cancelamento para a reconciliação local; não é pedido comercial nem uma coluna nova do banco. `DemoAutomaticAttendance` é o gerador/controlador de eventos sintéticos, não outro serviço de GPS ou um cliente Supabase paralelo.

| Camada | Responsabilidade |
|---|---|
| RouteProgress | Prepara os segmentos uma vez, projeta em metros sobre cada segmento finito, acumula avanço e sinaliza trechos ambíguos. |
| RouteAttendance | Reducer serializado por Mutex; registra, deduplica, classifica e ordena; oferece um StateFlow imutável para a UI. |
| ERREngine | Ponto de entrada adicional para organizar uma rota já carregada; fluxo legado preservado. |
| RouteRepository / DemoRouteSession | Mesmo repositório/modelo de rotas e transporte demo existente; armazenamento opcional foi acrescentado sem alterar o transporte remoto. |
| DemoRouteStorage Android | JSON versionado em SharedPreferences MODE_PRIVATE; commit em Dispatchers.IO verificado antes de confirmar salvamento. Somente IDs/owner sintéticos e geometria de rota; sem contas, credenciais, interesses ou GPS real. |
| DemoAutomaticAttendance | Fonte local com revisões, snapshot completo, relógio lógico, clientes e posições fictícios. Reutiliza repositório/ERR; nenhuma chamada Auth/PostgREST/Realtime. |
| RouteAttendancePanel | Operação: rota, trabalhando/pausado, mapa, fila, situações, encerramento. Sem botão de aceite por cliente, ETA ou preço. |
| DemoAutomaticRouteScreen | Seleção de rota persistida, controles sintéticos em área secundária, visão do consumidor e histórico local. Entrada após cadastro demo vendedor ou pelo botão na abertura da demo. |

## Regras e limites iniciais

- Ordem pela distância acumulada no percurso, com desempate estável por ID. Não ordenar só pela distância direta ao vendedor.
- Somente jornada trabalhando, conexão disponível e posição confiável podem produzir fila elegível. Não escrever accepted/fulfilled automaticamente.
- Clientes atrás do avanço confirmado ficam com estado explícito Ponto já ultrapassado. Fora do corredor, ponto inválido, ambíguo, cancelado e ausente na origem também permanecem consultáveis.
- Avanço não retrocede com ruído; retorno superior a 30 m é tratado como posição não confiável. Amostra antiga ou repetida não reinicia a jornada.
- Posições com idade superior a 30 s, precisão declarada acima de 50 m, futuro/inválidas, fora da rota, ambíguas ou salto incompatível com limite conservador de 15 m/s + margem de precisão suspendem a elegibilidade. **Esses parâmetros são regras iniciais testadas sinteticamente, não calibração GPS homologada.**
- A projeção é planar local, para rotas urbanas com todos os pontos a menos de 50 km da origem. Todos os segmentos são finitos; não usar como navegador rodoviário global ou estimativa de chegada. Interseções sobrepostas sem progresso determinável exigem cautela, sem assumir arbitrariamente um ramo.
- Projeção dos clientes fica em cache até as coordenadas mudarem. Atualização GPS não recalcula toda geometria de cada interesse. Recalcula a classificação/ordem, sem fazer requisição de rede.
- IDs/revisões são idempotentes. Cancelamentos mantêm tombstones; cópias antigas do mesmo consumidor não geram nova entrada na fila. Atualização de identidade de um ID e vendedor diferente são rejeitados.
- Snapshot completo tem watermark monotônico. Delta mais recente sobrevive a um snapshot antigo. Ausência vira Interesse não disponível na origem; não se inventa cancelamento.
- Reconexão simulada reconcilia o snapshot antes de marcar conexão disponível. Cancelamento offline aparece como pendente de reconexão até a reconciliação, sem afirmar sucesso remoto.
- Até 2.000 registros por jornada e 20 jornadas anteriores na sessão, com limite explícito. A UI operacional mostra os próximos 10, com demais registros virtualizados em Situações. Nenhum som/push por interesse.

## Ciclo de vida e privacidade

O controlador fica na composição raiz da demo para preservar a fila ao abrir a administração. Sair da tela operacional pausa a jornada simulada; STOP também pausa e interrompe reprodução. Não há serviço de background nem uso do GPS real nesta tela. O rastreador e LocationPublisher existentes ficam preservados para o modo real.

Apenas rotas fictícias do painel automático persistem no Android entre reinícios. A demo de gravação antiga continua com `DemoRouteSession()` sem armazenamento e limpa suas rotas ao sair. Eventos e histórico de atendimento nunca são escritos no armazenamento persistente. Ao reiniciar o processo, a rota pode ser escolhida novamente, mas a jornada e os clientes são uma nova simulação.

A visão do consumidor exige confirmar um ponto sintético, mostra situação derivada da MESMA fila local e permite cancelar. Uma nova confirmação de ponto atualiza a posição do interesse ativo com revisão mais recente, mantendo o protocolo e recalculando a situação; reenvio sem mudança permanece idempotente. Não afirma que o vendedor aceitou uma venda ou que está indo especificamente até esse consumidor. Campos e mapas informam que os dados são fictícios.

## Contrato remoto futuro — não implementado como integração real

Manter as RPCs e o Auth atuais. Antes de conectar esse motor a usuários reais, homologar uma fonte autorizada que forneça IDs estáveis, consumidor/vendedor/rota, posições com consentimento, revisão ordenável, validade e cancelamentos persistidos. O RPC atual só entrega pending dentro do polígono e não oferece esses metadados. Revisions desta demo não podem ser tratadas como revisões do servidor.

São necessários desenho aprovado de cancelamento/autorização, publicação/subscrição de interesses, reconciliação após perda de conexão, estado operacional persistido e precisão/tempo de GPS. A checagem vendorId do domínio não substitui RLS ou autorização no servidor. Não adicionar secret/service_role no app.

A assinatura futura deve ser uma por jornada/vendedor autorizado, cancelada no encerramento/troca de conta e acompanhada de snapshot de recuperação. Não criar uma assinatura por consumidor ou reenviar filas offline indiscriminadamente. O padrão de ciclo de vida existente de VendorRepository serve como referência, sem declarar que já resolve a fila operacional. Documentação consultada: [Supabase Postgres Changes](https://supabase.com/docs/guides/realtime/postgres-changes).

Sem alterações SQL, Auth, SMTP, RLS, publicação Realtime, projetos externos, deploy ou push nesta sprint. Homologação com GPS e eventos remotos reais é uma etapa separada.
