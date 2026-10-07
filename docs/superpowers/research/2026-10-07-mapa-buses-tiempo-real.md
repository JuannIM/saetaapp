# Investigación: mapa de buses en tiempo real (SAETA / RedBus Salta)

Fecha: 2026-10-07 · Estado: investigación completa, endpoints verificados en vivo

## Resumen ejecutivo

El mapa oficial de buses consume endpoints **públicos, anónimos, sin captcha ni sesión**
del mismo host que ya usa la app (`salta.miredbus.com.ar`). Son GETs JSON livianos.

El "tarda mucho en actualizarse" tiene dos causas:

1. **Fuente**: cada bus reporta su GPS al servidor cada **~30-60 s** (medido; algunos
   internos reportan con menos frecuencia). Es el piso duro — ningún cliente puede
   mostrar datos más frescos que los que la flota pushea.
2. **Cliente oficial**: el frontend web pollea cada **15 s** (`refreshTime: 0x3a98`
   en `mapa-de-buses.min.js`). La app móvil presumiblemente igual.

Resultado: la posición mostrada tiene típicamente 45-75 s de antigüedad.

**Mejora alcanzable**: pollear cada ~3-5 s (los updates aparecen a los ~2-5 s de
existir en el server, ~3× más fresco que la app oficial) + interpolar el movimiento
del marcador sobre el recorrido entre updates (dead reckoning) para que *se sienta*
en tiempo real. Bonus: podemos calcular ETA propio — el oficial está deshabilitado.

## Endpoints verificados (todos GET, anónimos, `cache-control: no-store`)

| Endpoint | Uso | Verificado |
|---|---|---|
| `GET /rest/gruposLineas` | Árbol grupo→subgrupo→línea. 101 líneas (URBANO, METROPOLITANO, TRONCALES, SUMALAO, TRENES ARGENTINOS). `{codLinea, descripcion}` | ✅ 200, ~0.4 s |
| `GET /rest/rutaLinea/{codLinea}` | `nodos[]`: polyline del recorrido + paradas (`parada:true`, `codigoParada:"p001-1b"`, `descripcionParada`) | ✅ 200 |
| `GET /rest/posicionesBuses/{codLinea}` | `posiciones[]`: `interno, latitud, longitud, orientacion, proximaParada(nombre), vehiculoRampa, vehiculoNoVisibles` | ✅ 200, ~0.3-0.5 s |
| `GET /rest/horariosEstimados/{codLinea}/{codParada}` | ETA por parada → **deshabilitado**: `habilitarCuandoViene:false` en config, responde `horariosServicio:[]` | ✅ 200 vacío |
| `GET /rest/novedadesLineas?codLineas=X` | Avisos/desvíos de la línea (texto libre con emojis) | ✅ 200 |
| `GET /rest/getConfiguracion` | Flags de features + URLs de tile servers | ✅ 200 |

### Notas

- No hay endpoint "todos los buses" — `posicionesBuses` exige codLinea válido
  (inválidos devuelven `{"error":0,"posiciones":[]}`).
- Las posiciones **no traen timestamp** — la edad del dato no se puede saber del
  payload; hay que inferirla trackeando cuándo cambia cada `interno`.
- `proximaParada` viene como nombre de parada (o `null`), no como código.
- Flujo oficial confirmado con Playwright: al elegir línea dispara `novedadesLineas`
  + `rutaLinea` una vez, luego `posicionesBuses` cada 15 s.
- CDN delante (BunnyCDN) pero `cdn-cache: MISS` en estos endpoints — pega al origin.
- Sin Turnstile/captcha/sesión en toda esta superficie (a diferencia del saldo).

## Config relevante (`/rest/getConfiguracion`)

```json
{
  "habilitarMapaBuses": true,
  "habilitarCuandoViene": false,
  "habilitarCuandoVieneMapBus": false,
  "urlPrincipalMapas": "https://mapmoblrj.red-bus.com.ar/tiles/{z}/{x}/{y}.png",
  "urlSecundariaMapas": "https://b.tile.openstreetmap.org/{z}/{x}/{y}.png",
  "latitud": -24.7899943, "longitud": -65.4130054
}
```

## Cadencia real de la fuente (medida, línea 1B, ~17:00 ART)

Probe: `posicionesBuses/100` cada ~3.3 s durante 3 min, 9 buses observados:

| interno | updates en 3 min | intervalo medio |
|---|---|---|
| 118 | 6 | ~30 s |
| 113 | 5 | ~36 s |
| 104 | 4 | ~45 s |
| 102, 103, 111, 132 | 3 | ~59 s |
| 101, 116 | 1 | >178 s |

Los buses **no se actualizan sincronizados** — cada interno tiene su propio ciclo de
reporte. Recomendación: repetir medición en hora pico para confirmar.

## Qué puede hacer nuestra app mejor que la oficial

1. **Poll cada 3-5 s** por línea seleccionada (solo mientras la pantalla es visible).
   Payload ~1-3 KB → carga despreciable; ser respetuosos con backoff si hay error.
2. **Interpolación**: animar marcadores a lo largo del polyline de `rutaLinea` entre
   updates usando `orientacion` + velocidad estimada → se percibe "vivo" aunque el
   dato llegue cada ~30-60 s. Es lo que hacen Transit/Google Maps.
3. **ETA propio**: con `proximaParada` + posiciones de paradas del `rutaLinea` +
   distancia sobre el polyline + velocidad estimada → "llega en ~X min" a paradas
   cercanas. Feature que el oficial tiene **apagado** en Salta.
4. **Freshness badge**: "actualizado hace 12 s" + atenuar buses stale (>90-120 s).
   Transparencia sobre la edad real del dato.
5. **`interno`** es el número de interno pintado en el bus físico — mostrarlo
   ("Interno 132") es más útil que un punto anónimo.
6. **`vehiculoRampa`** → badge de unidad accesible.
7. **`novedadesLineas`** → banner de desvíos, como el oficial.
8. Opcional: vista "todas las líneas" con polling round-robin de las top N
   (costo proporcional; evaluar rate limits).

## Riesgos / consideraciones

- **ToS**: endpoints públicos consumidos por el propio frontend oficial; uso
  razonable (poll solo con pantalla activa, backoff en error, no scrapear catálogo
  en loop). Mismo criterio que ya aplica al saldo.
- **Rate limiting desconocido**: 3-5 s es ~3-5× la frecuencia oficial. Medir;
  si hay 429s, subir a 8-10 s.
- **Tiles**: usar OSM estándar (con User-Agent propio + atribución) o evaluar el
  tile server de RedBus (`mapmoblrj.red-bus.com.ar`) — verificar CORS/términos.
- **Sin timestamp server-side**: la "edad" del dato se estima por cambio observado.
- **Buses que reportan poco**: algunos internos quedan quietos >3 min; la UI debe
  degradar elegante (mostrar última posición atenuada, no desaparecerlos de golpe).

## UX oficial de referencia (verificado con Playwright, screenshot `/tmp/mapa-oficial.png`)

- 3 selects: Grupo → Corredor → Línea.
- Banner amarillo de novedades de línea.
- Leaflet + OSM: polyline rojo del recorrido, markers "P" naranjas en paradas,
  icono de bus + flecha de orientación. Popup del bus: "Interno: NNN".
- Botón de geolocalización del usuario.
- Polling posiciones: 15 s.

## Próximo paso sugerido

Diseño de la feature en la app (pantalla "Mapa" con selector de línea, MapLibre/OSM,
poll 5 s + interpolación + freshness badge + ETA propio). El stack de red ya existe
(`NetworkClient` OkHttp sobre el mismo host).
