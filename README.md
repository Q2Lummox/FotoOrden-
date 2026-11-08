# FotoOrden

App Android para poner orden en la galería del móvil.

## Qué hace

**Fotos**
- Clasifica cada foto con IA **dentro del móvil** (no se sube nada): Personas, Documentos, Paisajes, Capturas, Comida, Mascotas y Otras.
- Filtros por categoría arriba, fotos agrupadas por meses.
- Pulsación larga para seleccionar; botón «Seleccionar» por mes y «Seleccionar todas» arriba (respeta el filtro: p. ej. *Documentos → Seleccionar todas*).
- Con fotos seleccionadas: **Mover a álbum** (uno existente o uno nuevo), **Compartir** y **Borrar**.
- Visor a pantalla completa: deslizar, pellizcar o doble toque para ampliar.

**Álbumes**
- Muestra las carpetas de fotos del móvil (Cámara, WhatsApp, Capturas…).
- «Nuevo álbum»: le pones nombre y eliges las fotos. Se crea en `Pictures/<nombre>`, así que aparece también en Google Fotos / Galería de Samsung.

**Limpieza**
- **Fotos repetidas**: agrupa fotos casi iguales (ráfagas, duplicados) y deja marcadas para borrar todas menos la mejor de cada grupo.
- **Borrosas o vacías**: con un control para ajustar lo estricto que es.
- **Capturas de pantalla**, **Imágenes de WhatsApp** y **Documentos**, con el espacio que ocupan.

**Papelera**
- Lo que se borra va a la papelera del sistema durante 30 días. Desde aquí se puede recuperar o eliminar para siempre.

## Requisitos
- Android 11 o superior.
- La primera vez analiza todas las fotos: con miles de fotos tarda un rato (se puede seguir usando la app mientras tanto). Si se cierra, continúa donde lo dejó.

## Cómo obtener el APK

### Opción A — Sin instalar nada (GitHub)
1. Crea una cuenta gratis en github.com y un repositorio nuevo (privado si quieres).
2. Sube el contenido de esta carpeta (botón *Add file → Upload files*; arrastra todo, incluida la carpeta `.github`).
3. Ve a la pestaña **Actions**: se compila solo en unos 5–8 minutos.
4. Entra en la ejecución terminada y descarga **FotoOrden-apk** (un .zip con el APK dentro).

> Si al subir por la web no se ve la carpeta `.github` (está oculta), créala a mano con *Add file → Create new file* y el nombre `.github/workflows/compilar-apk.yml`, pegando el contenido del fichero.

### Opción B — Con Android Studio
1. Instala Android Studio (gratis) y abre esta carpeta (*File → Open*).
2. Espera a que termine la sincronización.
3. Conecta el móvil con la depuración USB activada y pulsa ▶ *Run*, o genera el APK en *Build → Build App Bundle(s)/APK(s) → Build APK(s)*.

### Instalar en el móvil
Pasa el APK al móvil, ábrelo y permite «instalar apps de origen desconocido» cuando lo pida.

## Consejos de uso
- En **Limpieza → Modo rápido** se puede dar permiso para que la app mueva y borre sin que Android pregunte cada vez (lo borrado sigue yendo a la papelera).
- La clasificación es automática y puede fallar a veces; revisa antes de borrar en bloque.
- Mover fotos a un álbum las cambia de carpeta en el móvil (no ocupa más espacio).

## Estructura del código
- `data/PhotoRepository.kt` — lectura de fotos, mover, papelera (MediaStore).
- `analysis/PhotoAnalyzer.kt` — ML Kit (caras, texto, etiquetas) y reglas de categorías.
- `analysis/ImageMath.kt` — nitidez (borrosas) y hash perceptual (repetidas).
- `GalleryViewModel.kt` — estado de la app y análisis en segundo plano.
- `ui/` — pantallas en Jetpack Compose.
