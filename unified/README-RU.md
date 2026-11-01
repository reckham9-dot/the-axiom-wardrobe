# AxiomWardrobe Unified 1.0 RC1

Полноценная **интеграционная сборка** сединых интерфейсом /axiomwardrobe для The Axiom (Paper 1.21.11, Java 21).

- AxiomWardrobe 0.8 — источник 93 скинов, 16 косметических выборов, сайта, MySQL, AuthMe UUID и рабочих аур
- HMCCosmetics 2.9.3 — визуальный рендерер косметики через поддерживаемые слоты BACKPACK/HELMET/BALLOON
- HibiscusCommons 0.9.3 — обязательная зависимость HMCCosmetics
- AxiomWardrobeUnified — свой JAR с одним GUI, выбором и снятием, передачей косметических состояний в HMCCosmetics и безопасным откатом
- Никаких команд выдачи предметов, модификации Vault или смены приватной БД

## Установка

1. Сохрани резервные копии AxiomWardrobe 0.8 config.yml / selections.yml, HMCCosmetics и ResourcePackManager.
2. Убедись, что все три вышеуказанные зависимости уже установлены.
3. Оставь skins.apply-enabled=true, bridge.enabled=true, cosmetics.render-enabled=true, cosmetics.custom-models-enabled=false и cosmetics.visual-preview-unsafe-enabled=false.
4. Установи JAR из GitHub Actions: .github/workflows/unified-wardrobe-ci.yml.
5. Выполни /axiomwardrobe в Minecraft, /axiomwardrobe status в консоли.
6. В plugins/AxiomWardrobeUnified/config.yml пропиши backend.mappings.<AxiomCosmeticId>: <HMCCosmeticsCosmeticId> для действительно установленных косметических моделей, затем /axiomwardrobe reload.
7. Проверь внешний вид со второго клиента. Для удаления интеграции достаточно убрать этот JAR, данные сайта и работающие скины останутся.

## Текущее ограничение

Сборка компилируется с реальным Paper API и содержит рабочий GUI, выбор, сохранение через исходный AxiomWardrobe и программное соединение с HMCCosmetics. Но реальные пользовательские 3D-модели и ресурспак **не поставляются** в этой сборке; по умолчанию маппинги пусты, чтобы не показывать ранее сломанные модели. Без них 3D-отображение не завершено. Полной версией с проверенной 3D-косметикой это станет только после импорта лицензированных моделей и клиентского испытания.

## Архитектура

API HMCCosmetics вызывается из отдельного плагина динамически без включения кода HMCCosmetics или HibiscusCommons в наш JAR. Исходная работающая логика AxiomWardrobe не модифицирована и остаётся авторитетным источником выбранных скинов и косметических ID.

Лицензия стороннего движка: GPLv3 (соблюдать условия при распространении модификаций); мы не копируем его код.
