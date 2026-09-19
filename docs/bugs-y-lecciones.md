# Lecciones de bugs — microservices-ecommerce

Registro de fallos reales de este proyecto y cómo evitarlos.

## 1. ProductMapper: bean no encontrado

**Síntoma:** `required a bean of type '...ProductMapper' that could not be found`.

**Causa:** `ProductMapper` es una interfaz de MapStruct. El bean real es `ProductMapperImpl`, generado en compile time. `mapstruct-processor` solo estaba en `testCompile`, no en el compile principal.

**Solución:** Añadir `mapstruct-processor` y `lombok-mapstruct-binding` a `annotationProcessorPaths` de `default-compile` y `default-testCompile`. Luego Rebuild del módulo.

**Regla:** Si usas Lombok + MapStruct, el processor path debe incluir lombok, lombok-mapstruct-binding y mapstruct-processor, en ese orden.

## 2. `@ExceptionHandler` ambiguo

**Síntoma:** `Ambiguous @ExceptionHandler method mapped for Exception`.

**Causa:** El handler de validación y el catch-all tenían `@ExceptionHandler(Exception.class)`. Spring mapea por la anotación, no por el tipo del parámetro.

**Solución:** El de validación debe ser `@ExceptionHandler(MethodArgumentNotValidException.class)`. El genérico se queda con `Exception.class`.

## 3. NPE al mapear `quantity` (inventory)

**Síntoma:** `Cannot invoke Integer.intValue() because getQuantity() is null`.

**Causa:** MapStruct hacía `getQuantity() > 0` (unboxing a `int`). `@Min(0)` **no exige** el campo; `null` pasa la validación.

**Solución:** `@NotNull` + `@Min` en el DTO. Expresión nulo-segura: `quantity != null && quantity > 0`.

## 4. MapStruct: `orderLineItemsDtoList`

**Síntoma:** `No property named "orderLineItemsDtoList" exists. Did you mean "orderLineItemsList"?`

**Causa:** Mapper copiado de otro proyecto. Aquí request, entidad y response se llaman `orderLineItemsList`.

**Solución:** Quitar `@Mapping` con nombres viejos. Si coinciden, MapStruct mapea solo. Ignorar `id` y `orderNumber` al pasar de request a entidad.

## 5. Postgres: `database "order_db" does not exist`

**Síntoma:** Hibernate no abre JDBC para DDL.

**Causa:** `ddl-auto: update` crea tablas, **no** la base. Postgres exige que exista antes de conectar.

**Solución:** Crear la base (`CREATE DATABASE ...`) o definirla en Docker con la variable correcta.

## 6. Docker Postgres: variable y volumen

**Síntoma:** Cambiaste el nombre de la base y seguía sin existir.

**Causa:** La imagen oficial usa `POSTGRES_DB`, no `POSTGRES_DATABASE`. Además, esas variables **solo se leen la primera vez**, con el volumen vacío.

**Solución:** `POSTGRES_DB: order-db`. Si el contenedor ya inicializó: borrar volumen y recrear (`docker compose down` + `docker volume rm ...order_data` + `up`).

El nombre del **servicio** Docker no es el nombre de la base.

## 7. Orden: “stock insuficiente” con stock real

**Síntoma:** `IllegalArgumentException: Stock insuficiente o error de inventario`.

**Causa:** `InventoryClient` no era `final`. `@RequiredArgsConstructor` no lo inyectaba → `null` → NPE. El `catch (Exception e)` tapaba el error real.

**Solución:** `private final InventoryClient inventoryClient`. No tragar excepciones: loguear la causa y relanzar con el mensaje original.

El body de crear orden también necesita `price` (`@NotNull`).

## 8. Config Server: falta `spring.config.import`

**Síntoma:** `No spring.config.import property has been defined`.

**Causa:** `spring-cloud-starter-config` exige que el **cliente** declare de dónde importa. No vale poner eso en `config-data`.

**Solución:** En el `application.yaml` **local** del servicio:

```yaml
spring:
  application:
    name: inventory-service
  config:
    import: configserver:http://localhost:8888
```

`optional:` arranca aunque el Config Server esté caído (útil en local, esconde fallos). Sin `optional:`, levanta primero el Config Server (puerto 8888).

## 9. URL incorrecta del Config Server

`http://localhost:8888/application/default` es el archivo compartido `application.yml`.

La de un servicio es `http://localhost:8888/{nombre-del-servicio}/default`, por ejemplo:

`http://localhost:8888/inventory-service/default`

El `spring.application.name` del cliente debe coincidir con el nombre del archivo (`inventory-service.yml`).

## 10. DataSource: falta `spring:` en el YAML remoto

**Síntoma:** `Failed to configure a DataSource: 'url' attribute is not specified`.

**Causa:** Al mover config a `config-data`, las claves quedaron indentadas **sin** el padre `spring:`. Boot busca `spring.datasource.url`, no `datasource.url`. Con `optional:`, la app arrancaba igual, sin URL.

**Solución:** El YAML remoto debe tener:

```yaml
spring:
  datasource:
    url: jdbc:mysql://...
server:
  port: 8082
```

Luego commit (y push si el server usa GitHub) y reiniciar Config Server.

## 11. Puerto 8080 en uso (order-service)

**Síntoma:** `Port 8080 was already in use` aunque en `config-data` pusiste 8081.

**Causa:** Config Server ya no lee la carpeta local; usa el repo de GitHub. Lo no **pusheado** no existe para los clientes. El YAML servido no traía `server.port` → Boot usa 8080 (ocupado por product-service).

**Solución:** Commit + **push** a `main`. Reiniciar Config Server. Verificar en `http://localhost:8888/order-service/default` que aparezca `"server.port":8081`.

## 12. Actuator: `'value' must only contain valid chars`

**Síntoma:** `BeanDefinitionStoreException` al procesar `AuditEventsEndpointAutoConfiguration`, causado por `IllegalArgumentException: 'value' must only contain valid chars` en `EndpointId`.

**Causa:** En `config-data/product-service.yml` se expusieron endpoints así:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "refresh health"
```

`"refresh health"` es **un solo string con espacio**. Actuator trata cada ítem como un `EndpointId` (solo letras, números y guiones). El espacio es un carácter inválido. No era un problema de la dependencia `spring-boot-starter-actuator` en el `pom`.

**Solución:** Separar por coma o usar lista YAML:

```yaml
include: refresh,health
```

```yaml
include:
  - refresh
  - health
```

Commit + push de `config-data`, reiniciar Config Server y `product-service`.

**Regla:** `refresh` viene de Config Client; `health` de Actuator. No hace falta otra librería para esos dos.

---

## Checklist Config Server

1. Archivo `{application-name}.yml` con el mismo `spring.application.name` del cliente.
2. YAML válido: `spring.datasource.*`, `server.port`, etc.
3. Commit (y push si el URI es GitHub).
4. Reiniciar Config Server (cachea el clone).
5. Comprobar `http://localhost:8888/{app}/default`.
6. Cliente: `spring.config.import: configserver:http://localhost:8888`.
7. Orden de arranque: Config Server → resto.
