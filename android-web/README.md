# Táxi Praças (app híbrida — A app a usar)

**É a versão web tal como está** (`index.html` → `app/src/main/assets/taxi.html`,
cópia exata), a correr dentro de uma aplicação Android, com 3 peças nativas
onde o navegador do telemóvel falha:

1. **Leitura do ecrã** (`CaptureService` + `MediaProjection`): fotografa o ecrã
   a cada 30 s e injeta os frames na página (`window.__onFrame`) — o OCR,
   parsers, ranking e tudo o resto continuam a ser o JavaScript da web.
2. **Ponte JS** (`TaxiNative`: `startWatch`/`stopWatch`/`setBookings`/`notify`).
3. **Alarmes exatos das marcações** (`AlertCore` + `AlertReceiverN` + `BootReceiverN`):
   disparam com a app fechada; vão buscar a contagem à página se viva.

## Ganchos no index.html (poucos e inofensivos na web)

- `window.TaxiNative` → vigia nativa em vez de `getDisplayMedia`;
- `window.__onFrame(url)` → mesmo pipeline da vigia (`vigiaLer`);
- `window.__contagem(pracaId)` → contagem recente para os alarmes;
- `TaxiNative.setBookings(...)` ao guardar/apagar/abrir (alarmes nativos donos
  dos disparos quando a ponte existe);
- `TaxiNative.notify(...)` nos alertas.

## Manter sincronizado

Depois de editar `index.html` (raiz), copiar de novo:
`cp index.html android-web/app/src/main/assets/taxi.html`
(é cópia exata — os ganchos já lá estão).

## Construir

Abrir `android-web/` no Android Studio → Build APK(s), ou:
`./gradlew :app:assembleDebug` (gerar wrapper 1ª vez com `gradle wrapper`).
