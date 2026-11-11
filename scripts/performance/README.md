# Medição Android local

A variante `performance` usa o código/configuração release (R8 e shrinking), é não debuggable e usa assinatura debug, com applicationId `org.example.project.performance`. Não é artefato de publicação. Não substitui `org.example.project`, não usa as credenciais de assinatura do cliente e não contorna a validação de release.

```sh
./scripts/gradlew-local.sh :composeApp:testDebugUnitTest :composeApp:assembleDebug :composeApp:assemblePerformance :composeApp:bundlePerformance :composeApp:lintDebug --offline --no-daemon
export ADB="$HOME/Library/Android/sdk/platform-tools/adb"
"$ADB" devices -l
"$ADB" -s emulator-5554 install -r KotlinProject/composeApp/build/outputs/apk/performance/composeApp-performance.apk
python3 scripts/performance/measure-android.py --serial emulator-5554 --runs 7 --output /private/tmp/android-startup.json
python3 scripts/performance/artifact-sizes.py KotlinProject/composeApp/build/outputs/apk/performance/composeApp-performance.apk KotlinProject/composeApp/build/outputs/bundle/performance/composeApp-performance.aab
```

O script mede processo frio (`force-stop`), não limpa dados nem caches do SO, captura `TotalTime` do `am start -W`, PSS e `gfxinfo` após 4s. Não mede TTFD, abertura do mapa autenticado ou rede. Não executar builds nem outro benchmark simultaneamente. Guardar a versão do APK, API, RAM, ABI, estado de compilação ART e temperatura do aparelho ao comparar. Sete amostras são exploratórias, não uma certificação estatística. Para uma decisão de publicação, usar Macrobenchmark em hardware e cenários autenticados.

O relatório principal é `ANDROID_PERFORMANCE.md`. Não inserir chaves ou dumps de contas reais no repositório. Resultados brutos locais podem conter identificadores do ambiente: revisar antes de compartilhar.

```sh
python3 scripts/performance/summarize-startup.py /private/tmp/android-startup.json --output /private/tmp/android-startup-summary.json
python3 scripts/performance/smoke-android.py --serial emulator-5554 --output /private/tmp/android-smoke.json
```

O smoke atua somente no pacote isolado em emulador. Ele altera temporariamente o modo avião **do emulador**, restaurando o valor inicial em `finally`; não altera rede do computador. Envia notificações de trim, sem criar pressão real de RAM. Falhas/ANRs de entrada não certificam os fluxos autenticados. O script de medição agora exige `LaunchState=COLD` e `TotalTime>0`; uma amostra inválida interrompe a coleta em vez de ser descartada silenciosamente.
