# Aviso de licencia y procedencia

WhatPass es un trabajo derivado de **PassAndroid** (https://github.com/ligi/PassAndroid),
un lector de tarjetas Passbook/PKPASS para Android creado por ligi y colaboradores,
publicado bajo la **GNU General Public License v3.0 only (GPL-3.0-only)**.

Al tratarse de una obra derivada, WhatPass se distribuye bajo esa misma licencia,
GPL-3.0-only. El texto completo de la licencia está en el archivo [LICENSE](LICENSE)
de este mismo repositorio.

## Qué significa esto en la práctica

- El código fuente completo de WhatPass está disponible públicamente en
  https://github.com/glocalsaino/WhatPass
- Cualquier persona puede usar, estudiar, modificar y redistribuir este código,
  siempre bajo los mismos términos de la GPL-3.0.
- Esto aplica al código de la app Android. No aplica al backend/Cloud Function
  de `backend/functions` (un programa servidor independiente que se comunica con
  la app por red, no forma parte del binario distribuido ni es una obra derivada
  de PassAndroid).

## Cambios respecto al proyecto original

Desde el fork de PassAndroid, este proyecto ha sido renombrado (primero a MiWallet,
después a WhatPass), rebrandeado (icono, colores, textos), y ampliado con
funcionalidades propias: notificaciones push vía Firebase Cloud Messaging con
relé propio, escáner integrado para validación de tarjetas por operadores (sin
abrir el navegador), organización de pases por carpetas, corrección de
comportamientos específicos de distintos proveedores de pases, entre otros
cambios recogidos en el historial de commits del repositorio.
