# Android — APK externo de homologação

Fechamento local: 02/11/2026. Branch `feature/sprint1-recuperacao-tecnica`; alterações anteriores preservadas. Build e evidências locais iniciados em 01/11. Nenhuma operação de escrita no Supabase, cadastro, migration, push ou implantação nesta etapa. O RUN `mvuzgto9-1fbfa58b` permanece com o mesmo hash de arquivo; consumidores existentes não foram recriados.

## Artefato instalável

[Baixar APK](../output/android-homologation/TaChegando-homologation-1.0.apk) · [metadados](../output/android-homologation/APK_METADATA.json) · [checksum](../output/android-homologation/TaChegando-homologation-1.0.apk.sha256).

| Item | Valor |
|---|---|
| Pacote / rótulo | `org.example.project.homologation` / TáChegando Homologação |
| Versão | `1.0-homologation`, versionCode `1` |
| Ambiente | `homologation` |
| Supabase direto | `https://vhnkezwjknwpddqhlnfk.supabase.co` |
| Chave embarcada | Pública publishable; valor omitido; sem service_role |
| Tamanho do APK | 3.003.592 bytes, 2,864 MiB; não é tamanho instalado |
| SHA-256 do arquivo | `30ddef286f27933fe02835b62cec5ac3628d078bf72cf084011946eb5ec7f25b` |
| Android | mínimo API 24; target API 36 |
| ABIs | arm64-v8a, armeabi-v7a, x86, x86_64 |
| Otimização | R8 e resource shrinking; `debuggable=false` |
| Assinatura | Certificado Android Debug local, para distribuição direta de QA; não é assinatura de publicação |

Certificado público SHA-256: `0A:09:84:68:2D:45:DB:7E:56:4F:53:B6:83:24:1B:52:33:1B:A3:14:7F:34:2F:0F:B7:FB:7F:58:02:15:ED:E3`.

Certificado público SHA-1: `4A:63:58:B2:F7:19:1E:9C:70:C7:0A:77:7D:20:DC:9D:D3:86:7D:58`.

A variante usa pacote separado para preservar instalações e sessões anteriores. Atualizações desse pacote precisam da mesma assinatura; não apagar dados nem desinstalar para contornar divergência. O APK está ignorado no Git. Compartilhar somente APK/checksum/documentação pública; não incluir configurações privadas, keystore, senhas ou arquivos do RUN.

## Build e instalação reproduzíveis

Com o arquivo privado de homologação existente, dentro de `KotlinProject/`:

```bash
./scripts/gradlew-local.sh -Papp.environment=homologation \
  :composeApp:assembleHomologation :composeApp:testHomologationUnitTest \
  :composeApp:testDebugUnitTest :composeApp:lintHomologation :composeApp:signingReport \
  --offline --no-daemon
```

O modo offline exige dependências já armazenadas. `validateHomologationConfiguration` bloqueia o build se ambiente, URL do projeto autorizado ou tipo da chave pública não corresponderem. O teste negativo com ambiente `development` foi rejeitado conforme esperado.

Na raiz do repositório, com ADB no PATH:

```bash
shasum -a 256 output/android-homologation/TaChegando-homologation-1.0.apk
adb -s emulator-5554 install -r output/android-homologation/TaChegando-homologation-1.0.apk
adb -s emulator-5554 shell am start -W -n org.example.project.homologation/org.example.project.MainActivity
```

Em outro Android, transferir o APK e permitir instalação pela fonte escolhida. Não testado em telefone físico nesta etapa. Acesso à internet e ao Supabase remoto é necessário; o Mac pode estar desligado para o backend. Confirmação e recuperação por email dependem adicionalmente da implantação HTTPS do portal e da configuração remota futura.

## Resultados verificáveis

| Verificação | Resultado e limite |
|---|---|
| Build homologation | BUILD SUCCESSFUL; APK final verificado por aapt/apksigner |
| Unitários Android | 37 aprovados em debug e os mesmos 37 em homologation; zero falhas/erros/ignorados |
| Lint homologation | Zero erros; 17 avisos ainda existentes |
| Portal | 26 testes locais aprovados, com Auth/transporte fake; não comprovam emails reais |
| Runner | 11 testes locais de proteção aprovados; verify remoto não executado |
| Emulador | Instalação e abertura aprovadas em Pixel 9/API 37/ARM64; onboarding, navegação para login e mensagem de campos vazios verificadas |
| Sessão remota | Não iniciada nesta etapa, respeitando a proibição de escrita; login/logout reais não revalidados |
| Abertura | Uma amostra de `am start -W`: TotalTime 800 ms; não constitui benchmark nem validação em aparelho de entrada |
| Backend independente | URL HTTPS exata conferida na configuração gerada e no DEX final; projeto antigo ausente da busca no DEX; nenhum `adb reverse` |
| Portal local | GETs de páginas e assetlinks 200; POST de inspeção 503 antes do Auth; sem autenticação remota |

Evidências: [resultados estruturados](integration/external-apk-results.json), [UI e estado de App Links](integration/external-apk-local-ui.json), [captura do login sem credenciais](integration/external-apk-login.png). Logs locais em `/private/tmp/tachegando-external-*.log` são temporários. A evidência anterior de login consumidor A permanece histórica; não é contada como login real deste APK.

Sem validação em dispositivo de 2 GB, Android API 24 ou telefone externo. Sem conclusão sobre produtos, rotas, intenções, Realtime, ANRs em uso prolongado ou qualidade de rede desta variante: dependem de homologação funcional autorizada.

## Mudanças desta etapa

- `KotlinProject/composeApp/build.gradle.kts`: variante homologation com assinatura local, pacote isolado e proteção de ambiente remoto.
- `auth-portal/src/inspect.mjs`, `package.json`, `src/server.mjs`, `src/render.mjs`, `public/theme.css`: inspeção local com POSTs de Auth desativados e aviso visual.
- `auth-portal/test/portal.test.mjs`: bloqueio sem upstream e associação do novo pacote.
- `auth-portal/public/.well-known/assetlinks.json`: novo pacote de homologação com certificado real; publicação não executada.
- README do portal, documentação de certificados, este relatório e evidências públicas. Alterações anteriores do aplicativo/Investor Hub não foram descartadas.

## Bloqueadores separados

**Autenticação real:** a conta atual da CLI não listou o projeto autorizado; valores atuais de Site URL, Redirect URLs e templates ainda não foram auditados. Aguardar acesso administrativo ou valores não secretos fornecidos pelo cliente. Não há conclusão de que esses valores atuais estejam corretos ou incorretos. Login real deste APK está pendente sob a restrição atual de nenhuma escrita. Confirmar cadastro/recuperação e concluir verify exige autorização posterior; vendedores continuam pendentes, sem reiniciar RUN.

**Maps:** chave Android ausente; mapas não aprovados. Restringir a futura chave ao pacote `org.example.project.homologation` e ao SHA-1 acima para este APK. Produção usa pacote/certificado próprios, sem substituição automática.

**App Links:** `pm get-app-links` mostrou `app.tachegandoapp.com.br: 1024`, sem verificação bem-sucedida. O assetlinks local contém o pacote e o SHA-256 reais; ainda precisa ser servido no domínio HTTPS. SHA-256 oficial de assinatura Google Play permanece pendente. Não declarar retorno automático de email ao Android aprovado.

## HTTPS e auditoria seguinte

A implantação SSR Node 22+, configuração DNS apenas do subdomínio, TLS, templates propostos e rollback estão preparados em [AUTH_WEB_ANDROID.md](AUTH_WEB_ANDROID.md) e `auth-portal/deploy/`. Não executar deploy nem alterações de DNS/Auth nesta etapa. Primeiro disponibilizar e verificar `https://app.tachegandoapp.com.br` e seu assetlinks; depois revisar os valores atuais do Dashboard e obter autorização específica para alterações.

Valores **propostos**, ainda não comprovados como configuração atual: Site URL `https://app.tachegandoapp.com.br`; redirects exatos `https://app.tachegandoapp.com.br/auth/confirm` e `https://app.tachegandoapp.com.br/auth/recovery`. Templates propostos usam token_hash e confirmação explícita. Não aplicar antes de domínio funcional e aprovação.

O portal local está em `http://127.0.0.1:4178`, via `npm --prefix auth-portal run inspect`. Todos os POSTs de autenticação retornam 503 antes de acessar Auth. Não inserir links/tokens reais nessa inspeção. O APK não usa esse endereço local para o backend.
