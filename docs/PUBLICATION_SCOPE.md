# Escopo da publicação de código

Snapshot inicial do aplicativo em branch main. O histórico Git anterior foi preservado na máquina e não é ancestral desta publicação: contém configuração cliente embutida e snapshots de trabalho que não devem ser redistribuídos.

Incluídos: aplicativo KMP/Android, estrutura iOS/Wasm existente, todos os contratos/migrations atuais e legados arquivados, testes, portal Auth, worker, ferramentas e documentação de reprodução.

Não incluídos: site público/investor hub, JDK/SDK/dependências/caches, APKs/AABs, keystores, configurações reais, logs, dumps, screenshots e relatórios brutos de QA. Esses arquivos permanecem na máquina original. IDs reais de QA em exemplos de documentação e scripts opcionais foram substituídos por IDs sintéticos; configure seus alvos de teste autorizados antes de usar essas ferramentas.

Relatórios históricos podem mencionar evidências locais que não acompanham o repositório. Isso não é comprovação de integração remota. Para o estado atual, consultar o README e o contrato site-app-integration-2026-11-11. Nenhum site, Supabase ou serviço de produção foi alterado.
