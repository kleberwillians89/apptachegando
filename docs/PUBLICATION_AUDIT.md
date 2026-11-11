# Auditoria da publicação de código — 11/11/2026

Destino autorizado: https://github.com/kleberwillians89/apptachegando — branch main. O repositório estava vazio (nenhuma ref anunciada por git ls-remote), com permissão de escrita confirmada. Não existe deploy/site configurado nesta publicação.

## Histórico e isolamento

A raiz Git original permanece preservada com sua branch de trabalho, alterações locais e remote anteriores. Foram inspecionados 22 commits, 2506 objetos alcançáveis e 2016 blobs. O histórico antigo contém chave cliente Maps e JWT público anon embutidos e snapshots temporários; não foi detectada chave administrativa real nos padrões verificados. Não publicamos esse histórico. O destino recebe um commit inicial sem pais, criado em checkout separado; nenhum force push é necessário.

## Arquivos e segredos

O snapshot inicial foi auditado no staging, não apenas na árvore de trabalho: 303 arquivos antes deste relatório, 3372163 bytes e maior arquivo de 199153 bytes. Este relatório acrescenta um arquivo. Foram verificados nomes proibidos, padrões de chaves privadas, GitHub/AWS/Google/Supabase, JWTs, conta de serviço e presença literal de 5 valores de configuração reais locais. Nenhuma ocorrência bloqueadora permaneceu no staging. O falso positivo no teste PostgreSQL/push foi revisado: credencial sintética exclusiva do transporte loopback, sem autoridade remota.

.env reais, propriedades locais, keystores e caches estão ignorados. APKs, dumps, logs, screenshots e relatórios brutos de usuários não foram incluídos. IDs reais de QA em exemplos publicados foram substituídos por IDs sintéticos. Nenhum dado QA foi modificado. Configuração administrativa continua exclusivamente externa ao código cliente.

A comparação com a cópia local confirmou ausência de código necessário faltando ou alterado no snapshot. Apenas exemplos públicos foram sanitizados e whitespace/finais de linha normalizados. O wrapper Gradle, inclusive JAR e bit executável, está incluído; JDK/SDK/dependências instaladas ficam fora. O site público/investor hub não foi modificado nem publicado neste repositório.

Incluídas as cinco migrations canônicas, inclusive 20261111135508_web_app_identity_and_discovery.sql (SHA-256 `c37c3646aff8f8f4e5882d1c94484e7f2af1ff816fe051afdc0ce2ce0676e4e9`), e migrations legadas preservadas separadamente. Todos os testes e contratos RC1 anteriores necessários estão presentes.

## Validação anterior ao envio

- Android Debug: 174 aprovados; Android Homologation: 174 aprovados. Ambas as suites reexecutadas, zero falhas/erros/skips.
- Runner/push: 28 aprovados; portal: 29 aprovados. Zero falhas/skips; sintaxe do portal aprovada.
- PostgreSQL descartável: todas as suites e migrations locais aprovadas; 46 assertions web/geo, 54 assertions RC1, 7 casos de paridade, 6 cenários de integração controlada, concorrência de perfil e de interesse.
- Compilação Homologation e lint aprovados, sem gerar APK. Lint: 0 erros, 60 warnings e 1 hint.
- git diff --cached --check aprovado após normalização de finais de linha; fixtures de projeção conferidas.
- Os 18 APKs distribuídos anteriores permaneceram na máquina, sem alteração. Não representam o código deste commit e não foram enviados.

A CI versionada verifica compilação/testes Android sem APK, estrutura iOS, todas as suites SQL e testes Node/portal. Não foi disparado deploy ou workflow manual. O resultado local não comprova CI hospedada nem integração real com serviços.

## Pendências operacionais

Revisar e autorizar instalação dos contratos Supabase QA, incluindo dependências RC1; configurar SMTP/DNS/HTTPS/templates/redirects do portal; fornecer parâmetros Firebase e operar worker com credenciais servidor; diagnosticar Maps/tiles; integrar o site ao contrato e executar jornada real em dois Androids. Produção/assinatura de publicação, exclusão efetiva de conta e requisitos Play Store permanecem pendentes. Nenhuma migration remota, conta real, e-mail real ou envio FCM real foi executado nesta publicação.

Este relatório descreve os gates anteriores ao envio. A confirmação do commit/branch remoto é fornecida ao responsável após o push; não se inclui o próprio SHA no arquivo para evitar referência circular.
