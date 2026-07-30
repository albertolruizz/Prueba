# Prueba

Plugin para Paper 1.21.x que sincroniza los datos de un jugador entre varios servidores usando mongodb como persistencia (colección `players`). Compilado con java 21 y gradle. Hecho para DiosesMC, evitando los grifos y sumideros.

## Que sincroniza

- **Perfil**: nombre, ultima conexión, último servidor y fecha de primer join.
- **Estado**: vida, comida, saturación, xp (levels y xp), modo de juego, fuego y efectos de poción completos.
- **Ubicaciones**: posición por servidor (mundo, coordenadas y yaw/pitch). Al volver a un servidor apareces donde lo dejaste en ese servidor
- **Inventario**: inventario completo (incluida armadura y offhand), enderchest.
- **Estadisticas básicas**: muertes, bajas a jugadores y a mobs, saltos, daño infligido y recibido, peces pescados y ticks jugados (se podrian poner todos tho)

Cada bloque se puede activar o desactivar por separado en la config. El guardado usa `$set` por sección, asi que desactivar un campo no borra lo que ya habia guardado.

## Como funciona

**Al entrar**: se cargan los datos en un hilo aparte y se aplican en el hilo principal. Hasta que los datos no están aplicados el jugador esta congelado (no puede mover inventario, soltar ni recoger objetos, por si acaso). Si mongo no responde se le expulsa con un mensaje claro en vez de dejarle jugar con datos vacíos que luego sobrescribirían los buenos (esto me ha pasado mucho con HuskSync, literal)

**Al salir**: se captura el estado en el hilo principal y se escribe en mongo async.

**Cambio de servidor**: el documento lleva un campo `control` con el servidor que tiene al jugador y si sigue en linea. Si entras en el servidor B mientras el A todavia no ha terminado de guardar, B espera (reintentos cortos con tope) a que A libere antes de cargar. Si el servidor A se cayó sin liberar,B deja de esperar tras el tope y carga lo último guardado, para no dejar al jugador fuera.

**Redis**: acelera ese relevo. Cada servidor publica en un canal cuando termina de guardar a un jugador, y el que está esperando se despierta al instante en vez de agotar el temporizador. Si redis no está disponible el plugin funciona igual, solo que el relevo va por sondeo.

**Autoguardado**: cada N segundos (configurable) se guarda a todos los jugadores conectados, ademas del guardado al salir y al apagar el servidor. Esto se hace para evitar perdida de datos en crasheos o cosas asi

## Serialización sin perdida

Los inventarios se serializan con `ItemStack.serializeItemsAsBytes` de la API de Paper (formato NBT binario versionado, con `DataVersion` incluida) y se guardan en base64. Se conserva todo: encantamientos, nombres, lore, durabilidad, atributos y datos de componentes. Los efectos de poción se guardan campo a campo.

## Comandos

- `/prueba status` - conexion con mongodb (ping), estado de redis y jugadores sincronizados. Permiso `prueba.admin`.
- `/prueba save [jugador]` - fuerza un guardado inmediato.
- `/prueba reload` - recarga la config
- `/trade <jugador>`, `/trade accept|deny|cancel` - intercambio de objetos. Permiso `prueba.trade`.
- `/sethome [nombre]`, `/home [nombre]`, `/delhome [nombre]`, `/homes` - hogares. Permiso `prueba.homes`.
- `/tpa <jugador>`, `/tpaccept`, `/tpdeny` - peticiones de teletransporte. Permiso `prueba.tpa`.

## Compilar

```
./gradlew shadowJar
```

El jar queda en `build/libs/Prueba.jar` con el driver oficial de mongodb y jedis incluidos y reubicados para no chocar con otros plugins.

El código, la configuración y los documentos de mongo están en ingles, los mensajes que ve el jugador en español.

## Extras

Tres cosas que no pedía la prueba técnica, estas las he implementado con IA (ya que era añadido)

### /trade

Intercambio de objetos entre dos jugadores con menú.

- `/trade <jugador>` envia una petición con botones de aceptar y rechazar que caduca a los 30 segundos.
- Al aceptar se abre un cofre de 6 filas para los dos, la parte del otro en solo lectura y actualizada en vivo.
- Cada uno confirma con su botón. **Cualquier cambio en una oferta anula las dos confirmaciones**, que es lo que evita la estafa clasica de confirmar y cambiar el objeto en el ultimo momento.
- El intercambio solo se ejecuta si a los dos les cabe lo que van a recibir, si no se avisa y se vuelve al estado sin confirmar.
- Cerrar el menú, desconectarse o apagarse el servidor devuelve cada objeto a su dueño. No se puede quedar nada "en el aire".
- Está bloqueado mientras los datos del jugador aún se están cargando, para que no se pueda tradear con un inventario a medio sincronizar.

### Hogares

`/sethome [nombre]` guarda tu posición, `/home [nombre]` te lleva de vuelta, `/delhome` borra y `/homes` lista. Se guardan en el mismo documento de mongo, asi que la lista te sigue a cualquier servidor y `/homes` te dice en cual está cada hogar. El tope de hogares es configurable.

**Con un proxy delante, `/home` te cambia de servidor.** Si el hogar está en otro servidor, el plugin anota el viaje en tu documento (`pending_teleport`) y pide el cambio al proxy por plugin message. Al entrar en el servidor de destino, en cuanto tus datos estan aplicados, el viaje pendiente se ejecuta y se borra. Si el pendiente lleva mas de dos minutos sin consumirse se descarta, para que un cambio de idea no te teletransporte media hora despues. Sin proxy (`proxy.enabled: false`) el plugin avisa de en qué servidor está el hogar en lugar de fingir que te lleva.

### Peticiones de teletransporte

`/tpa <jugador>`, `/tpaccept` y `/tpdeny`. Las peticiones caducan al minuto y se limpian solas cuando cualquiera de los dos se desconecta.

**Tambien funcionan entre servidores.** Si el jugador no está en este servidor, se busca en mongodb en qué servidor tiene la sesión abierta y la petición viaja por redis hasta él, indicandole desde donde le escriben. Al aceptar, la respuesta vuelve por redis, el servidor de origen anota el viaje pendiente y manda al jugador al proxy;nada mas llegar se le teletransporta a quien aceptó. Si para entonces esa persona ya no está, se le dice en vez de dejarle en mitad de la nada.

## Como se ha probado

Sobre dos servidores Paper 1.21.4 con la misma mongodb y el mismo redis, uno llamado `server-1` y otro `server-2`, con https://mineflayer.com/ (usando IA para que prueben todo) y yo mismo:

- Entrar en `server-1`, recibir una espada de netherita con Sharpness 5, Unbreaking 3, nombre personalizado y durabilidad gastada, un shulker con diamantes dentro, 17 manzanas doradas, efectos de poción y nivel 27. Salir y entrar en `server-2`: llega todo identico, incluidos los datos de componentes y el contenido del shulker.
- Vida guardada en 8 y aplicada en el otro servidor.
- Entrar en `server-2` mientras la sesión de `server-1` todavia está cerrandose: `server-2` espera el relevo, lo recibe por redis (`Relevo recibido desde 'server-1'`) y carga el inventario ya guardado, sin perder nada.
- Intercambio completo entre dos jugadores: la oferta del otro se ve en vivo, al confirmar uno el otro lo ve, mover un objeto despues de confirmar anula las dos confirmaciones y al confirmar los dos los objetos cambian de dueño.
- Hogares creados en `server-1` y vistos desde `server-2`, y peticion de teletransporte aceptada entre dos jugadores.

Y con un velocity delante de los dos servidores, para el cambio de servidor:

- `/home` a un hogar del otro servidor: el plugin anota el viaje, el proxy registra el cambio de `server-1` a `server-2` y al llegar el jugador aparece en las coordenadas del hogar y el pendiente desaparece del documento.
- `/tpa` de un jugador de `server-1` a otro de `server-2`: la peticion llega al destino indicando el servidor de origen, al aceptar el jugador es enviado al otro servidor y al llegar acaba en las mismas coordenadas que su destinatario