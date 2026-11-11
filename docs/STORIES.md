# Stories - Tá Chegando

> Resumo de todas as issues criadas no Linear

## Linear Setup

- **Team:** Tá Chegando (TCH)
- **Team ID:** `6c8f7df8-7657-40ce-af18-0cdd7a603d24`
- **URL:** https://linear.app/tachegando

---

## Projetos e Stories

### 1. Core/UI (4 stories)

| ID | Título | Descrição |
|----|--------|-----------|
| TCH-1 | Definir paleta de cores do app | Primária #FF6B35, Secundária #2EC4B6, Destaque #FFD166, Sucesso #06D6A0, Erro #EF476F |
| TCH-2 | Criar AppTheme com Material Design 3 | Configurar `AppTheme` Compose com Material 3 e ColorScheme |
| TCH-3 | Criar componentes base reutilizáveis | CustomButton, CustomTextField, LoadingOverlay, ErrorWidget |
| TCH-4 | Configurar Navigation Compose com rotas base | Setup inicial: splash, login, vendor/*, consumer/* |

---

### 2. Autenticação (7 stories)

| ID | Título | Descrição |
|----|--------|-----------|
| TCH-5 | Tela de splash screen | Animação de loading com logo do app |
| TCH-6 | Tela de seleção de tipo de usuário | Consumidor ou Vendedor |
| TCH-7 | Onboarding para consumidores | 3 telas explicativas + permissão de localização |
| TCH-8 | Onboarding para vendedores | Cadastro completo + período de teste |
| TCH-9 | Login com telefone | Autenticação via SMS/WhatsApp |
| TCH-10 | Login social (Google/Apple) | Integração com OAuth |
| TCH-11 | Período de teste gratuito 14 dias | Lógica de trial para novos vendedores |

---

### 3. App Vendedor (8 stories)

| ID | Título | Descrição |
|----|--------|-----------|
| TCH-12 | Home do vendedor com mapa | Tela principal com mapa, status online/offline e heatmap |
| TCH-13 | Heatmap de demanda | Visualizar áreas com mais clientes em tempo real |
| TCH-14 | Sistema de rotas | Definir, salvar e executar rotas de venda |
| TCH-15 | Tracking em tempo real | Atualizar posição do vendedor continuamente |
| TCH-16 | Perfil do vendedor | Nome, foto, descrição, avaliações |
| TCH-17 | Cadastro de produtos | Lista de produtos com nome, descrição e preço |
| TCH-18 | Encerrar rota | Finalizar jornada e ver resumo do dia |
| TCH-19 | Notificar clientes da área | Enviar push para consumidores próximos |

---

### 4. App Consumidor (8 stories)

| ID | Título | Descrição |
|----|--------|-----------|
| TCH-20 | Home do consumidor com mapa | Mapa interativo com vendedores próximos |
| TCH-21 | Filtro por tipo de produto | Alimentos, Limpeza, Beleza, etc. |
| TCH-22 | Página de detalhes do vendedor | Perfil, produtos, localização, ETA |
| TCH-23 | Tracking em tempo real | Acompanhar vendedor no mapa até chegar |
| TCH-24 | Criar alerta de proximidade | "Me avise quando chegar" |
| TCH-25 | Lista de favoritos | Salvar vendedores favoritos para acesso rápido |
| TCH-26 | Sistema de avaliações | Avaliar vendedor de 1 a 5 estrelas com comentário |
| TCH-27 | Busca por nome/produto | Encontrar vendedor específico |

---

### 5. Mapas e Geolocalização (6 stories)

| ID | Título | Descrição |
|----|--------|-----------|
| TCH-28 | Integrar Google Maps SDK | Configurar Maps Compose/Google Maps SDK para Android e placeholders multiplataforma |
| TCH-29 | Serviço de localização multiplataforma | Implementar `LocationTracker` com `expect/actual` por plataforma |
| TCH-30 | Heatmap de demanda | Visualizar áreas com mais clientes em tempo real |
| TCH-31 | Tracking em tempo real | Atualizar posição do vendedor via Supabase Realtime |
| TCH-32 | Calcular distância e ETA | Estimativa de tempo de chegada |
| TCH-33 | Custom markers no mapa | Markers personalizados por tipo de vendedor |

---

### 6. Notificações (6 stories)

| ID | Título | Descrição |
|----|--------|-----------|
| TCH-34 | Integrar OneSignal/FCM | Configurar push notifications iOS e Android |
| TCH-35 | Alerta de vendedor próximo | Notificar quando vendedor entrar no raio |
| TCH-36 | Alerta de chegada | Notificar quando vendedor estiver a < 200m |
| TCH-37 | Configurações de notificação | Raio, tipos de alerta, categorias |
| TCH-38 | Lembretes de rota | Avisar vendedor de horário de início |
| TCH-39 | Horário silencioso | Quiet hours para não incomodar |

---

### 7. Gestão de Frotas (5 stories)

| ID | Título | Descrição |
|----|--------|-----------|
| TCH-40 | Dashboard da frota | Visão consolidada de todos os veículos no mapa |
| TCH-41 | Adicionar/remover veículos | Gestão de veículos da frota |
| TCH-42 | Atribuir rotas | Distribuir rotas entre motoristas |
| TCH-43 | Relatório consolidado | Métricas de performance da frota |
| TCH-44 | Precificação B2B | Modelo de preços para frotas com 3+ veículos |

---

### 8. Assinaturas (5 stories)

| ID | Título | Descrição |
|----|--------|-----------|
| TCH-45 | Integrar gateway de pagamento | Asaas ou Mercado Pago para PIX/Boleto |
| TCH-46 | Plano mensal R$ 49 | Assinatura recorrente com cobrança automática |
| TCH-47 | Gerenciar assinatura | Tela para ver status, cancelar, atualizar pagamento |
| TCH-48 | Webhooks de pagamento | Receber notificações de pagamento confirmado |
| TCH-49 | Lembretes de renovação | Avisar 3 dias antes do vencimento |

---

### 9. Infraestrutura (7 stories)

| ID | Título | Descrição |
|----|--------|-----------|
| TCH-50 | Setup Supabase | Criar projeto, configurar auth, database, storage |
| TCH-51 | Schema do banco de dados | Criar tabelas: users, vendors, consumers, locations, etc. |
| TCH-52 | RLS Policies | Row Level Security para proteção de dados |
| TCH-53 | Realtime subscriptions | Configurar canais para tracking em tempo real |
| TCH-54 | CI/CD com GitHub Actions | Build, test e deploy automáticos |
| TCH-55 | Edge Functions | Funções serverless para lógica de backend |
| TCH-56 | Agregação de dados para heatmap | Job para calcular demanda por região |

---

## Total: 56 Stories

### Labels Disponíveis

- `vendor` - Relacionado ao vendedor
- `consumer` - Relacionado ao consumidor
- `fleet` - Gestão de frotas
- `maps` - Mapas e geolocalização
- `notifications` - Push notifications
- `auth` - Autenticação
- `subscription` - Assinaturas
- `infrastructure` - Infraestrutura
