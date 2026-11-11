# Rodada única futura — não executar antes dos bloqueadores externos

PASS: resultado observado conforme critério. FAIL: comportamento diferente. BLOCKED: pré-requisito indisponível. Registrar aparelho/Android/horário/protocolo sem senha ou token. Operações QA abaixo exigem autorização prévia; nunca encerrar ou alterar a rota preservada sem autorização específica.

1. Confirmar autorizações para login QA, publicação de posição/disponibilidade, interesse/cancelamento e envio push; backend/worker/Firebase/Maps/portal configurados. Ausência = BLOCKED.
2. Instalar o único APK aprovado nos dois Androids; package, versão e SHA-256 iguais ao relatório desse candidato. Divergência = FAIL.
3. Login vendedor confirmado → painel profissional; consumidor confirmado → consumidor. Nenhum onboarding indevido/conta duplicada.
4. Reabrir app e confirmar sessão recuperada; logout impede acesso e avisos da conta anterior.
5. Recuperar rota QA correta sem recriar/encerrar; percurso e GPS coerentes. Rota incompatível = BLOCKED, sem conserto automático.
6. Autorizar GPS em uso; posição recente publicada. Negar/desligar GPS deve mostrar estado útil e impedir falsa elegibilidade.
7. Mapa real deve carregar tiles e percurso; rede ausente mostra erro/retry, sem área vazia parecendo funcional.
8. Descoberta por proximidade/percurso e busca por nome/produto retornam vendedor elegível; catálogo sem preço/checkout.
9. Consumidor registra interesse: um protocolo persistido e uma entrada automática, sem aceite manual.
10. Vendedor aberto recebe um destaque/som/vibração conforme dispositivo; polling+Realtime, refresh e reabertura não repetem protocolo.
11. Vendedor em segundo plano recebe um único push recente; tocar abre a jornada correta. Registrar aceitação do provedor separada da entrega observada.
12. Negar POST_NOTIFICATIONS: fila continua funcionando; nenhum crash ou promessa de aviso entregue.
13. Cancelar interesse: sai da fila conforme contrato; retry/reconexão não recria nem duplica protocolo.
14. Pausa/retomada somente se autorizadas; vendedor pausado/GPS antigo/salto inválido não gera alerta elegível. Retomada com GPS válido recupera estado.
15. Logout/troca de sessão bloqueia push antigo e associação; rede restaurada não alerta histórico. Registrar isolamento das duas contas e os casos ainda BLOCKED.
