# Portão RC1 — 04/11/2026

“Pronto para testar isoladamente” não significa homologado. O candidato completo permanece BLOQUEADO; nenhuma instalação foi solicitada.

| FUNCIONALIDADE | IMPLEMENTADO | TESTADO LOCALMENTE | DEPENDÊNCIA EXTERNA | PRONTO PARA HOMOLOGAÇÃO |
|---|---|---|---|---|
| Maps real e estados de carregamento/erro | Sim | Compilação, configuração e regressões | Console, rede e renderização reais; causa do timeout não confirmada | NÃO — BLOQUEADO |
| Contratos push/RLS/outbox | Sim, migration revisável | PostgreSQL real, concorrência, 54 asserts e 7 projeções | Auditoria e aplicação autorizada em QA | NÃO — BLOQUEADO |
| Emissor HTTP v1 e worker | Sim | Banco real + HTTP/OAuth/FCM controlados | Credenciais e execução supervisionada autorizada | NÃO — BLOQUEADO |
| Android FCM, associação e logout | Sim | Compilação e regras automatizadas; SDK real não exercitado | Quatro campos Firebase e entrega em aparelho | NÃO — BLOQUEADO |
| Publicação GPS com precisão/horário original | Sim | Unitários e SQL com posições sintéticas | Novo contrato QA e jornada real | NÃO — BLOQUEADO |
| Login, papéis e sessão | Sim | Regressões; flags Auth públicas por GET; defaults SDK auditados | Login real das duas contas não executado | SIM para teste isolado — NÃO VALIDADO REMOTAMENTE |
| Cadastro, confirmação e recuperação | Sim | Portal: 26 testes controlados | Domínio do portal não resolveu nesta estação; DNS/TLS/hosting | NÃO — BLOQUEADO |
| Descoberta, catálogo sem preços, interesse/cancelamento | Sim | Regressões locais e contratos SQL | Jornada das contas QA e localização real | SIM para teste isolado — NÃO VALIDADO REMOTAMENTE |
| Fila automática e aviso em primeiro plano | Sim, sem aceite individual | Deduplicação e reconciliação automatizadas | Som/vibração/permissões em aparelho | SIM para teste isolado — NÃO VALIDADO REMOTAMENTE |
| Convite invite | Não oferecido | Não declarado funcional | Implementação fora deste fechamento | NÃO — NÃO SUPORTADO |

Lint: 0 erros, 64 warnings e 1 hint; não equivale a lint sem avisos. Inclui sugestões KTX e commits síncronos de preferências usados para estado persistente de revogação/deduplicação. Compilação e suites passaram. Corridas de callbacks nativos receberam sincronização; execução real desses callbacks permanece pendente.
