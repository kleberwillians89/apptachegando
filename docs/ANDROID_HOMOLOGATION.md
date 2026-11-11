# Homologação Android — consumidor e vendedor

Pré-condições: homologação autorizada com as duas migrations, Auth e Realtime operacionais; chave Maps nova restrita; dois emuladores com Google Play Services (ou emulador + aparelho), mesma build de homologation e conexão à internet.

Usar duas contas reais de teste confirmadas: consumidor e vendedor. Não usar os usuários sem senha do seed SQL. A suíte automatizada usa quatro contas para testar isolamento A/B; o roteiro visual abaixo usa duas. Contas/senhas devem ficar no cofre ou em `.local`, nunca em capturas/logs compartilhados.

## Instalação e emuladores

```bash
./scripts/gradlew-local.sh :composeApp:assembleDebug :composeApp:testDebugUnitTest \
  -Papp.environment=homologation
"$ANDROID_HOME/platform-tools/adb" devices -l
```

Selecionar explicitamente o serial de cada emulador; substituir os valores abaixo pelos listados:

```bash
TCH_VENDOR_DEVICE=emulator-5554
TCH_CONSUMER_DEVICE=emulator-5556
"$ANDROID_HOME/platform-tools/adb" -s "$TCH_VENDOR_DEVICE" install -r \
  KotlinProject/composeApp/build/outputs/apk/debug/composeApp-debug.apk
"$ANDROID_HOME/platform-tools/adb" -s "$TCH_CONSUMER_DEVICE" install -r \
  KotlinProject/composeApp/build/outputs/apk/debug/composeApp-debug.apk
"$ANDROID_HOME/platform-tools/adb" -s "$TCH_VENDOR_DEVICE" shell am start -n org.example.project/.MainActivity
"$ANDROID_HOME/platform-tools/adb" -s "$TCH_CONSUMER_DEVICE" shell am start -n org.example.project/.MainActivity
```

Se `install -r` indicar assinatura incompatível, parar: conferir a origem do APK/certificado. Não desinstalar automaticamente nem apagar dados de uma instalação que precisa ser preservada.

## Percurso de teste

| Etapa | Ação | Aceite |
|---|---|---|
| 1 | Cadastrar/confirmar consumidor e vendedor, fazer login em dispositivos diferentes | Perfil correto e sem mensagens de configuração/servidor antigo |
| 2 | Vendedor adiciona produto com preço e retorna à tela | Produto continua após reabrir; consumidor vê preço/nome no perfil quando online |
| 3 | Vendedor fica online e abre Minha rota; concede localização precisa/aproximada conforme cenário | Flag persistida e posição real no mapa do consumidor; ausência de permissão não provoca crash |
| 4 | Simular pontos no dispositivo vendedor e gravar rota | Dois ou mais pontos reais recebidos pelo app; rota salva com confirmação do banco |
| 5 | Encerrar o processo e entrar novamente | Minha rota oferece Continuar rota; pontos/dias recuperados do banco |
| 6 | Consumidor abre o vendedor e registra intenção com localização permitida | Confirma registro, sem prometer push; vendedor autorizado consegue consultar a intenção |
| 7 | Consumidor tenta repetir intenção | Sem duplicata pendente; conferir ID pelo runner/consulta autorizada |
| 8 | Vendedor encerra rota | Status ended persistido; nova consulta da rota não gera demanda ativa |
| 9 | Vendedor fica offline | Catálogo/posição somem no consumidor; considerar até 15 s do refresh do catálogo |
| 10 | Logout e novo acesso | Sessão removida no cliente; erro de rede não apresenta falsa saída confirmada |

A gravação GPS em RouteConfigScreen registra pontos; o envio de posição ocorre em Minha rota/Rota ativa. Não interpretar a gravação do rascunho como tracking contínuo em segundo plano. Ao matar o app, ele não continua compartilhando GPS. Para testar visibilidade, mantê-lo no fluxo de tracking em primeiro plano.

## Localização simulada

O comando de emulador usa **longitude primeiro**, depois latitude. Enviar pontos separados, aguardando pelo menos o intervalo do tracking (aproximadamente 5 s) e deslocamento superior a 10 m para a gravação:

```bash
"$ANDROID_HOME/platform-tools/adb" -s "$TCH_VENDOR_DEVICE" emu geo fix -46.6333 -23.5505
```

Aguardar 6 s e enviar o próximo; repetir enquanto o app estiver gravando:

```bash
"$ANDROID_HOME/platform-tools/adb" -s "$TCH_VENDOR_DEVICE" emu geo fix -46.6328 -23.5505
sleep 6
"$ANDROID_HOME/platform-tools/adb" -s "$TCH_VENDOR_DEVICE" emu geo fix -46.6323 -23.5505
"$ANDROID_HOME/platform-tools/adb" -s "$TCH_CONSUMER_DEVICE" emu geo fix -46.6332 -23.5505
```

Alternativa: Extended Controls → Location → coordenada/rota no Android Emulator. Confirmar que o app recebeu a posição; enviar um comando ADB não prova que GPS/Play Services entregou o callback.

Verificar localização negada nas configurações do app, conexão indisponível, posição expirada após aproximadamente dois minutos sem updates e retorno ao fluxo. O GPS inicial usa lastLocation, que pode estar vazio até receber um ponto; não tratar posição desconhecida como coordenada real.

Para testar reinício sem apagar dados:

```bash
"$ANDROID_HOME/platform-tools/adb" -s "$TCH_VENDOR_DEVICE" shell am force-stop org.example.project
"$ANDROID_HOME/platform-tools/adb" -s "$TCH_VENDOR_DEVICE" shell am start -n org.example.project/.MainActivity
```

Registrar: versão/hash do APK, ambiente/project ref, serial dos emuladores, etapa, resultado e evidência sem credenciais. Mapas vazios, erro de RLS, falha de recuperação de rota ou intenção exposta a terceiro impedem aceite. Push, cobrança e tracking com app fechado não são critérios deste MVP.

[Referência do console do emulador](https://developer.android.com/studio/run/emulator-console). Os comandos de localização são para emuladores, não para forçar GPS em aparelho físico.
