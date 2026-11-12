# TáChegando — homologação integrada do aplicativo (11/11/2026)

## O que esta branch entrega

- Identidade do vendedor resolvida pela RPC autenticada `get_my_account_profile`, com conferência do `user_id` ativo; nunca pela identidade pública de outro vendedor.
- Catálogo do próprio vendedor (50 produtos por página), incluindo produtos ocultos, nome, preço em formato decimal, botão **Ocultar/Mostrar**, edição e remoção com `vendor_id` explícito no filtro.
- Contrato de lotes de produtos no histórico de migrations, compatível com a função `append_my_vendor_products` já aplicada ao Supabase autorizado.
- Testes de identidade, visibilidade, idempotência dos lotes e proibição de escrita por consumidores.
- Painel de vendedor não confunde `is_online` com presença efetiva no mapa.

## Contratos já existentes (não reinventar)

- Login web + app: o mesmo Supabase, mediante confirmação por e-mail; conta de vendedor precisa ter perfil profissional concluído.
- Descoberta geográfica: `discover_vendors`, filtrada pelo servidor por disponibilidade, rota, localização recente e permissão.
- Localização: `update_vendor_location` / `publish_route_location` com sessão autenticada; sem rota ativa e GPS válido, não há visibilidade confirmada.
- Interesse: `create_purchase_intent`, `get_my_interest_state`, `cancel_purchase_intent`; não se trata de venda, reserva ou garantia de estoque.
- Notificações: worker, outbox, Firebase e contrato de push exigem configuração e teste reais antes de ativar o fluxo.

## Homologação com dois aparelhos

1. **Conta:** criar consumidor e vendedor pelo site; confirmar ambos por e-mail; entrar com as mesmas credenciais no Android; verificar papéis distintos.
2. **Catálogo:** cadastrar 1 produto e depois mais de 50; confirmar que o app pagina corretamente sem duplicar, permite editar, ocultar, reexibir e remover apenas produtos do proprietário.
3. **Recuperação:** solicitar novo e-mail de confirmação e recuperação; conferir páginas `https://tachegandoapp.com.br/cadastro?modo=entrar` e `https://tachegandoapp.com.br/recuperar`.
4. **Rota/GPS:** iniciar rota ativa com vendedor disponível e localização autorizada; validar precisão e validade temporal; pausar, expirar GPS e encerrar, verificando que a localização deixa de qualificar vendedores.
5. **Consumidor:** autorizar GPS, consultar próximos, testar buscas e categorias; não renderizar vendedores inelegíveis ou dados simulados como reais.
6. **Interesse:** registrar, consultar estado, cancelar, repetir chamadas em falha de rede; verificar isolamento por consumidor e vendedor.
7. **Notificações:** configurar FCM e worker com credenciais seguras; testar push com consentimento, deduplicação e revogação, inclusive em segundo plano.
8. **Qualidade:** rodar Kotlin Android/iOS, testes SQL e Node do fluxo de autenticação; validar interface pt-BR, acessibilidade e conexão lenta.
9. **Distribuição:** gerar Android assinado e validar iOS com Xcode; não anunciar apps em lojas sem build e testes de dispositivo.

## Restrições de lançamento

- A chave Supabase no app deve ser **publishable**, jamais `service_role` ou `sb_secret_`.
- Não alterar migrations canônicas já aplicadas; adições em arquivos separados.
- Código desta branch não equivale a APK gerado, app instalado nem push de produção.
- O subdomínio `app.tachegandoapp.com.br` permanece reservado e não deve ser usado como destino de e-mail sem DNS/HTTPS próprios (a correção dos links está em PR separado).
- Banco QA/projeto usado no site: `vhnkezwjknwpddqhlnfk`. Verificar ambiente do aplicativo antes de compilar; não copiar credenciais de servidor.
