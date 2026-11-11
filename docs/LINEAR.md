# Linear - Gestão de Projetos

> Configuração e uso do Linear no projeto Tá Chegando.

## Configuração

### 1. Obter API Key

1. Acesse [Linear Settings](https://linear.app/settings/api)
2. Crie uma nova API Key
3. Copie a chave e adicione ao `.env`:

```
LINEAR_API_KEY=lin_api_xxxxx
LINEAR_TEAM_ID=6c8f7df8-7657-40ce-af18-0cdd7a603d24
LINEAR_LABEL_TCH=<id-da-label-tch>
```

### 2. Integração MCP (Cursor/Codex)

O Linear está configurado via MCP no `~/.codex/config.toml`:

```toml
[mcp_servers.linear]
url = "https://mcp.linear.app/mcp"
```

## Estrutura de Issues

### Prefixo
Todas as issues do time Evo usam o prefixo: **EVO-**

### Label do Produto
Sempre usar a label **`tch`** para issues do Tá Chegando

### Labels por Prioridade

| Label | Cor | Uso |
|-------|-----|-----|
| `P0 - Urgent` | Vermelho | Bugs críticos, bloqueios |
| `P1 - High` | Laranja | Features prioritárias |
| `P2 - Medium` | Amarelo | Features normais |
| `P3 - Low` | Azul | Nice to have |

### Labels por Domínio

| Label | Cor | Uso |
|-------|-----|-----|
| `vendor` | Verde | Relacionado ao vendedor |
| `consumer` | Roxo | Relacionado ao consumidor |
| `fleet` | Rosa | Gestão de frotas |
| `maps` | Azul claro | Mapas e geolocalização |
| `notifications` | Laranja claro | Push notifications |

### Status

| Status | Descrição |
|--------|-----------|
| Backlog | Awaiting prioritization |
| Todo | Ready for sprint |
| In Progress | Being worked on |
| In Review | PR aberta |
| Done | Mergeado e deployado |
| Blocked | Impedimento |

## Cycles (Sprints)

- **Duração:** 2 semanas
- **Início:** Segunda-feira
- **Término:** Sexta-feira

## Templates de Issue

### Feature

```markdown
## Descrição
[O que deve ser implementado]

## Critérios de Aceitação
- [ ] Critério 1
- [ ] Critério 2

## Tarefas
- [ ] Task 1
- [ ] Task 2

## Relacionado
- Projeto: [Nome do Módulo]
- Label: tch
```

### Bug

```markdown
## Descrição
[O que está acontecendo de errado]

## Passos para Reproduzir
1.
2.
3.

## Comportamento Esperado
[O que deveria acontecer]

## Comportamento Atual
[O que está acontecendo]

## Ambiente
- App version:
- OS:
- Device:

## Screenshots
[Se aplicável]
```

## Branches

Convenção de branches baseada em issues:

```
feature/EVO-123-descricao-da-feature
bugfix/EVO-456-descricao-do-bug
hotfix/EVO-789-descricao-critica
```

## Commits

Usar Conventional Commits com ID da issue:

```
feat(vendor): adiciona heatmap de demanda

Closes EVO-123
```

## API Linear

### Query GraphQL Básica

```graphql
query {
  issues(filter: { identifier: { eq: "EVO-123" } }) {
    nodes {
      id
      title
      description
      state {
        name
      }
      labels {
        nodes {
          name
        }
      }
    }
  }
}
```

### Criar Issue via API

```bash
curl -X POST https://api.linear.app/graphql \
  -H "Authorization: $LINEAR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "mutation { issueCreate(input: { title: \"Nova feature\", teamId: \"6c8f7df8-7657-40ce-af18-0cdd7a603d24\", labelIds: [\"LABEL_ID\"] }) { issue { id identifier } } }"
  }'
```

## Links Úteis

- [Linear API Docs](https://developers.linear.app/)
- [Linear MCP](https://mcp.linear.app/)
- [Workspace](https://linear.app/evo)
- [Metodologia Completa](./LINEAR_METHODOLOGY.md)
