# AGENTS.md - Tá Chegando

> Guia para agentes de IA trabalharem neste app Kotlin Multiplatform/Compose.

## Projeto

**Tá Chegando** é uma plataforma de geolocalização em tempo real que conecta vendedores ambulantes/itinerantes a consumidores próximos.

### Personas
- **Profissional/Vendedor:** Usa o app para gerenciar rotas, ver heatmap de demanda, notificar clientes e compartilhar localização
- **Consumidor:** Usa o app gratuitamente para encontrar vendedores próximos e ativar intenção/alerta de chegada
- **Frota:** Dono de múltiplos veículos que gerencia vendedores

---

## Build / Lint / Test Commands

Execute os comandos dentro de `KotlinProject/`.

### Setup
```bash
./gradlew kotlinUpgradeYarnLock
```

### Build Android
```bash
./gradlew :composeApp:compileDebugKotlinAndroid
./gradlew :composeApp:assembleDebug
```

### Run Web/Wasm
```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

### Test / Check
```bash
./gradlew :composeApp:check
./gradlew :composeApp:allTests
```

---

## Architecture

### Kotlin Multiplatform + Compose

```
KotlinProject/
├── composeApp/
│   └── src/
│       ├── commonMain/kotlin/org/example/project/
│       │   ├── core/
│       │   │   ├── data/
│       │   │   ├── domain/
│       │   │   ├── navigation/
│       │   │   ├── theme/
│       │   │   ├── ui/
│       │   │   └── utils/
│       │   ├── features/
│       │   │   ├── auth/
│       │   │   ├── consumer/
│       │   │   ├── notifications/
│       │   │   ├── onboarding/
│       │   │   ├── splash/
│       │   │   └── vendor/
│       │   └── App.kt
│       ├── androidMain/
│       ├── iosMain/
│       └── wasmJsMain/
└── iosApp/
```

### Dependency Rule
```
Presentation → Domain ← Data
```

UI Compose fica em `features/`; modelos de regra ficam em `core/domain/`; integrações com Supabase, mapas e localização ficam em `core/data/`, `core/ui/` e `core/utils/`.

---

## Key Dependencies

| Package | Purpose |
|---------|---------|
| `compose-runtime`, `compose-foundation`, `compose-material3` | UI declarativa |
| `navigation-compose` | Navegação entre telas |
| `supabase-kt` (`auth-kt`, `postgrest-kt`, `realtime-kt`) | Backend, Auth, DB e Realtime |
| `ktor-client-*` | HTTP por plataforma |
| `maps-compose`, `play-services-maps` | Mapas Android |
| `play-services-location` | GPS Android |
| `kotlinx-serialization-json` | Serialização de modelos |

---

## Code Style

### Imports Order
```kotlin
// 1. Kotlin / standard library
import kotlin.math.max

// 2. AndroidX / Compose
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// 3. External packages
import io.github.jan.supabase.postgrest.postgrest

// 4. Internal package
import org.example.project.core.theme.AppColors
import org.example.project.core.utils.UiState
```

### Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Classes | PascalCase | `VendorRepository`, `VendorRoute` |
| Functions/variables | camelCase | `currentVendor`, `isLoading` |
| Constants | PascalCase or UPPER_SNAKE when appropriate | `AppColors.Primary`, `DEFAULT_TIMEOUT` |
| Files | PascalCase for screens/classes | `VendorDashboardScreen.kt` |
| Packages/directories | lowercase | `vendor/`, `consumer/` |
| Private members | `_` only when needed by Kotlin conventions | `_state` |

---

## Cores do App

| Cor | Hex | Uso |
|-----|-----|-----|
| **Primária (Laranja)** | `#FF6B00` | Cor principal, botões, destaques |
| **Secundária (Azul Escuro)** | `#1B2A4A` | Textos, ações secundárias |
| **Destaque** | `#FFD166` | Alertas, notificações |
| **Sucesso** | `#06D6A0` | Online, ativo |
| **Erro** | `#EF476F` | Erro, urgente |

> **Nota:** Tema fixo em modo claro. Não há suporte a tema escuro.

---

## Linear - Gestão de Projetos

### Configuração
Ver `docs/LINEAR.md` para detalhes completos.

### Estrutura
- **Time:** Evo (prefixo: EVO-)
- **Label do produto:** `tch` (sempre usar em issues do Tá Chegando)
- **Projetos:** Módulos (Autenticacao, App Vendedor, etc.)

### Prefixo de Issues
**EVO-** (ex: EVO-123)

### Branches
```
feature/EVO-123-descricao
bugfix/EVO-456-descricao
hotfix/EVO-789-descricao
```

### Commits
```
feat(vendor): adiciona heatmap de demanda

Closes EVO-123
```

### Labels Principais
- `tch` - Label do produto Tá Chegando
- `vendor` - Relacionado ao vendedor
- `consumer` - Relacionado ao consumidor
- `fleet` - Gestão de frotas
- `maps` - Mapas e geolocalização
- `P0/P1/P2/P3` - Prioridade

---

## Error Handling

### Data Layer
```kotlin
return try {
    UiState.Success(repositoryCall())
} catch (e: Exception) {
    UiState.Error("Mensagem amigável para o usuário.")
}
```

### Presentation
```kotlin
when (uiState) {
    UiState.Loading -> CircularProgressIndicator()
    is UiState.Success -> Content(uiState.data)
    is UiState.Error -> Text(uiState.message)
}
```

---

## Common Gotchas

1. Use `@Composable` apenas em contexto composable.
2. Use `remember`, `LaunchedEffect` e `DisposableEffect` com chaves estáveis.
3. Cancele tracking/observadores em `DisposableEffect`.
4. Em código multiplataforma, declare contratos em `commonMain` e implemente com `expect/actual` por plataforma.
5. Evite duplicar modelos no mesmo pacote; `Location` deve ter uma fonte única.
6. Para Supabase, confira se o filtro usa `vendors.id` ou `vendors.user_id`; eles não são o mesmo identificador.
7. Não silencie erro de backend sem expor estado útil via `UiState`.

---

## Referências

- `KotlinProject/README.md` - Estrutura KMP
- `KotlinProject/composeApp/build.gradle.kts` - Targets e dependências do app
- `KotlinProject/gradle/libs.versions.toml` - Versões das dependências
- `docs/LINEAR.md` - Configuração do Linear

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:
- When graphify-out/graph.json exists, run `graphify query "<question>"` before your first grep, search or source-file read for a codebase question, and put this rule in every subagent prompt that explores code. Read raw files only for lines the graph or an earlier read already located. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
- A committed graphify-out/graph.json is rewritten whole on every build, so a large diff to it in a pull request is expected. Do not flag it as churn or suggest skipping the graph rebuild or its hooks.
