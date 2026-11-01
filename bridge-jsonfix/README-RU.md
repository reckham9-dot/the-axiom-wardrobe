# AxiomWardrobe 0.8.1 — исправление POST косметики

Ошибка обнаружена в реальном байткоде **AxiomWardrobe 0.8.0-cutover-rc1**: `BridgeClient.setCosmetic` отправлял `kind<TAB>item_id` с заголовком `Content-Type: application/json`. Сайт `wardrobe_v1.php` ожидал `{"kind":"hat","item_id":"hat_crown"}`; статус HTTP 400 вызывал бесконечные `Wardrobe cosmetic sync postponed`.

Сборка 0.8.1 исправляет только одну операцию формирования тела `BridgeClient.setCosmetic`. Новый метод `WardrobeJsonBody.encode` генерирует JSON; **подпись HMAC-SHA256 рассчитывается уже от исправленного JSON**, потому что вызов `make(..., body)` остался неизменным.

Результат сверки архивов JAR 0.8.0 и 0.8.1:
- **Изменены:** `online/theaxiom/wardrobe/BridgeClient.class` и `plugin.yml` (номер версии).
- **Добавлен:** `online/theaxiom/wardrobe/WardrobeJsonBody.class`.
- **Без изменений:** 93 подписанных скина, `CosmeticEngine`, `CosmeticCatalog`, `AxiomWardrobePlugin`, `skins.tsv`, все другие ресурсы и классы.

**Локальное интеграционное тестирование (пройдено):** Java 21 HTTPS-сервер с проверкой HMAC, `server_set_cosmetic` POST JSON, `server_get_cosmetics` GET, неизменённые `server_set` и `server_get` скинов. Не тестировалось с живым сайтом: его секреты и файловая система недоступны.

## Установка и откат
Остановить Paper, сохранить текущий JAR и всю `plugins/AxiomWardrobe/`, заменить только JAR на `AxiomWardrobe-0.8.1-bridge-jsonfix.jar`, затем полный старт сервера. **Сохранить** Unified, HMCCosmetics, HibiscusCommons, сайт, MySQL и AxiomBridge. Если старые неудачные выборы были перезаписаны синхронизацией после рестарта — повторно выбрать косметику один раз и убедиться, что больше нет `sync postponed`.

Не путать исправление Bridge с отображением: на Unified RC1 соответствия `backend.mappings` пока пусты (`0/13`). Без реальных установленных моделей HMCCosmetics визуальная косметика не появится.

Откат: остановить Paper, вернуть предыдущий JAR, ничего не очищая.
