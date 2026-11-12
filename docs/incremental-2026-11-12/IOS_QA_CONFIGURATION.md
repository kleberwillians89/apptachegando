# Configuração iOS QA

O Xcode chamava `embedAndSignAppleFrameworkForXcode` sem selecionar ambiente.
Gradle usava `development`, URL loopback e chave vazia quando não havia configuração
local. `GeneratedAppConfiguration` é gerado em commonMain e compilado dentro do
framework nativo; alterar apenas Info.plist ou variáveis de execução do Scheme
não configura esse cliente. `SupabaseClient` é inicializado de forma lazy usando
essas constantes. A mensagem de configuração ausente foi preservada.

Agora a fase de build chama `scripts/build-ios-framework.sh`, que seleciona
`homologation` por padrão, inclusive para Debug. O wrapper seleciona JDK 21 pelo
script existente. Não há credenciais no Xcode, Info.plist ou código versionado.
Gradle lê, por prioridade: propriedade `-P`, variável de ambiente e arquivo local
`KotlinProject/config/<ambiente>.properties`. Em homologação a URL deve ser
exatamente `https://vhnkezwjknwpddqhlnfk.supabase.co`, e a chave deve ter formato
`sb_publishable_`. Outra URL, chave vazia, secret ou service-role impede o build.
O prefixo valida o tipo, não a associação ao projeto: esta foi conferida por GET
somente de leitura em `/auth/v1/settings` com a chave local autorizada (HTTP 200).

## Preparação em outra máquina

```bash
cp KotlinProject/config/homologation.properties.example KotlinProject/config/homologation.properties
```

Preencher somente `SUPABASE_PUBLISHABLE_KEY` com a chave pública autorizada de QA.
O arquivo local é ignorado pelo Git. Não usar chaves de servidor. A chave pública
será incorporada ao binário por definição; RLS continua sendo obrigatório.
Não publicar logs ou arquivos gerados contendo a configuração. Não criar chaves
novas nem mudar Auth para reproduzir o build.

O Bundle Identifier e Development Team personalizados no projeto Xcode local
foram preservados. Não é necessário trocar assinatura ou provisionamento.
`APP_ENVIRONMENT=development` é uma escolha explícita para testes locais; em
builds comuns pelo Xcode o padrão agora é QA. Não reutilizar framework antigo com
`OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES`: esse override pula a fase Gradle.

## Compilar e testar

Na raiz do checkout, com JDK 21 e Android SDK disponíveis para configurar o projeto KMP:

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export ANDROID_HOME="$HOME/Library/Android/sdk"
APP_ENVIRONMENT=homologation bash scripts/gradlew-local.sh \
  :composeApp:compileKotlinIosArm64 :composeApp:iosSimulatorArm64Test --no-daemon
python3 scripts/test-ios-configuration.py
xcodebuild -project KotlinProject/iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath /private/tmp/tch-ios-qa-derived CODE_SIGNING_ALLOWED=NO build
```

## Reinstalar no iPhone (pelo responsável)

```bash
open KotlinProject/iosApp/iosApp.xcodeproj
```

Selecionar scheme `iosApp`, o iPhone conectado e Debug. Confirmar que o override
que pula Gradle está desativado, manter Team/Bundle Identifier pessoais existentes
e usar Product → Run. O mesmo identificador atualiza a instalação existente.

Alternativa CLI, após obter o identificador com `xcrun devicectl list devices`:

```bash
xcodebuild -project KotlinProject/iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -destination "id=IDENTIFICADOR_DO_IPHONE" \
  -derivedDataPath /private/tmp/tch-ios-qa-device build
xcrun devicectl device install app --device IDENTIFICADOR_DO_IPHONE \
  /private/tmp/tch-ios-qa-device/Build/Products/Debug-iphoneos/KotlinProject.app
```

Confiar no perfil pessoal se o iOS solicitar. Entrar com uma conta QA existente;
não criar usuários para validar a configuração. Build e teste nativo não comprovam
login real no iPhone, recuperação de sessão ou jornada comercial.

Nenhuma migration, banco, Auth/SMTP ou configuração remota foi alterada.

## Evidências desta correção

- Compilação Kotlin iOS arm64: aprovada.
- Testes nativos no simulador: 179 aprovados, 0 falhas, 0 erros.
- Build completo Xcode Debug para simulador: BUILD SUCCEEDED.
- Dois testes offline do wrapper: aprovados.
- URL não QA e chave secret: rejeitadas no Gradle.
- Arquivo Kotlin gerado: ambiente homologation, URL exata QA, chave pública presente.
- Endpoint Auth settings QA: GET HTTP 200, sem criar sessão.
- Instalação e login no iPhone: não executados nesta correção.
