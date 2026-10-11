# Táxi Praças Auto (Android nativo)

Vigia contínua **100% automática**: lê o ecrã sozinha (Taxi-Link e TaxiDigital),
conta carros por praça, calcula ritmo de saída, recomenda **para onde ir**,
mostra o **mini flutuante** sobre as outras apps e dá **alerta sonoro nas marcações**.
Tudo no aparelho — sem passwords, sem root.

## O que faz (v1)

- **Vigia**: serviço em 1º plano, fotografa o ecrã a cada 30 s (só com ecrã ligado;
  se der acesso a utilização, só quando a Taxi-Link/TaxiDigital está aberta),
  OCR no aparelho (ML Kit, descarrega o modelo à 1ª vez), mesmos parsers da versão web:
  licenças, posição, tempo em praça, `táxis oc/15`, distâncias, lista de praças,
  marcações `Praça X DD/MM/AAAA, HH:MM (Nm)`.
- **Nomes unificados**: `Av. Rio de Janeiro` = `PT.RIO JANEIRO`; `PT.` novo cria a praça;
  zonas (ex. Mafra) não entram no ranking.
- **Ranking**: mesma matemática da web (ritmo ÷ espera + desconto de distância).
- **Mini 🪟**: bolha sobre as outras apps com `👉 Praça · espera · ritmo`
  (precisa de "Sobrepor a outras apps"). Toque abre a app.
- **Marcações 📅**: alerta sonoro + vibração + notificação **20 min antes da queda**
  (`hora − aviso`), com o estado da praça; expira 30 min depois.
- **Notificação permanente** com a recomendação atual + botão Parar.

Fora do v1 (fica para depois): reforços online (aviões/comboios/cruzeiros),
ecrãs PENDENTES/OFERTAS e ZONAS da TaxiDigital (enviar fotos).

## Construir o APK

1. Abra a pasta `android/` no **Android Studio** (versão recente).
2. Deixe sincronizar (descarrega Gradle + dependências — precisa de internet).
3. Menu **Build → Build APK(s)** (ou `assembleDebug`).
4. O APK sai em `app/build/outputs/apk/debug/app-debug.apk`.

Sem Android Studio (linha de comandos, com o SDK instalado):
`./gradlew assembleDebug` (gerar o wrapper 1ª vez com `gradle wrapper`).

## Instalar no telemóvel

1. Copie o `app-debug.apk` para o telemóvel (USB, Drive, WhatsApp).
2. Toque no ficheiro → **permitir instalar de fontes desconhecidas** → Instalar.
3. Abra **Táxi Praças Auto** e dê as permissões (botões na app, uma vez):
   - Notificações · Sobrepor a outras apps · Acesso a utilização · Ignorar bateria.
4. Prima **▶️ Iniciar vigia** → **"Começar agora"** (consentimento de captura, 1ª vez)
   → abra a Taxi-Link/TaxiDigital. A partir daí é sozinho.
5. O mini aparece por cima; a recomendação atualiza na notificação.

## Notas honestas

- Com o **ecrã desligado não há leitura** (o Android devolve preto) — é limite do sistema.
- O modelo de OCR descarrega na 1ª leitura (net). Depois funciona offline,
  exceto voos/comboios/cruzeiros (só na versão web, por agora).
- Se a Taxi-Link/TaxiDigital mudar o layout, os padrões em `TaxiParser.kt`
  precisam de 1 ajuste (estão isolados de propósito).
