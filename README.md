# 📦 GravitySDK for Android

`GravitySDK` — это мощный инструмент для интеграции персонализированного контента, отслеживания взаимодействия пользователей и отображения кампаний в мобильных Android-приложениях. Он позволяет получать контент по шаблонам, отслеживать события и отображать контент в различных форматах (модальное окно, полноэкранный режим, bottom sheet).

## 📚 Оглавление

- [✨ Возможности](#возможности)
- [🚀 Установка](#установка)
- [⚙️ Инициализация](#инициализация)
- [🔧 Дополнительные параметры initialize](#дополнительные-параметры-initialize)
- [🎨 ProductWidgetBuilder — кастомизация продуктов](#productwidgetbuilder--кастомизация-отображения-продуктов)
- [🧑 Пользователь и настройки](#пользователь-и-настройки)
- [📄 Отслеживание и события](#отслеживание-и-события)
- [📈 Взаимодействие](#взаимодействие)
- [🧩 Получение контента](#получение-контента)
- [🖼️ Отображение контента](#отображение-контента)
- [🔒 Блокировка показа](#блокировка-показа)
- [🧱 Inline-контент](#inline-контент)
- [❗ Обработка ошибок](#обработка-ошибок)

## Возможности

- Инициализация SDK с ключом API и параметрами секции
- Настройка контента и прокси
- Отслеживание посещений страниц и пользовательских событий
- Получение контента по шаблону
- Отображение контента в:
    - Модальном окне
    - Bottom sheet
    - Полноэкранном режиме
    - Bottom sheet с рядом товаров
    - Inline — встроенные в верстку view для отображегния контента
- Отправка взаимодействий с контентом и продуктами

## Установка

Добавь модуль `gravity_sdk` в свой проект:

```kotlin
dependencies {
  implementation(project(":gravity_sdk"))
}
```

## Инициализация

Для работы SDK необходимо провести базовую инициализацию, передав Context, а так же параметры `apiKey` и `section`. Их можно найти в личном кабинете.

```kotlin
GravitySDK.initialize(
  context,
  apiKey = "your-api-key",
  section = "your-section-id",
)
```

## Дополнительные параметры initialize

```kotlin
fun initialize(
  context: Context,
  apiKey: String,
  section: String,
  gravityEventCallback: GravityEventCallback? = null,
  productViewBuilder: ProductViewBuilder? = null,
  productFilter: ProductFilter? = null,
)
```

- `productViewBuilder` — кастомная отрисовка карточек продуктов
- `gravityEventCallback` — колбэк, вызываемый при трекинге событий
- `productFilter` — фильтр продуктов

## Пользователь и настройки

```kotlin
GravitySDK.instance.setUser("user-id","session-id")

GravitySDK.instance.setOptions(
  options = Options(...),
  contentSettings = ContentSettings(...),
)
```

## Отслеживание и события

```kotlin
GravitySDK.instance.trackView(
  pageContext = PageContext(...),
  activityContext = context
)

GravitySDK.instance.triggerEvent(
  events = listOf(TriggerEvent(...)),
  pageContext = PageContext(...),
  activityContext = context
)
```

## Взаимодействие

```kotlin
GravitySDK.instance.sendContentEngagement(ContentImpressionEngagement(...))
GravitySDK.instance.sendProductEngagement(ProductClickEngagement(...))
```

## Отображение контента

Отображение контента происходит автоматически после вызова одного из методов: `trackView`
или `triggerEvent`

## Блокировка показа

Используй, когда хостовое приложение показывает свои диалоги или приоритетный контент, который не должен перекрываться кампанией Gravity.

```kotlin
GravitySDK.instance.lockPresentation()    // на время своего диалога — кампании не показываются
GravitySDK.instance.unlockPresentation()  // снова разрешить показ
```

- Вызовы идемпотентны, повторный вызов безопасен.
- Во время блокировки контент по-прежнему грузится, но не показывается; уже открытый контент не закрывается.
- Подписка на смену состояния:

```kotlin
GravitySDK.instance.setPresentationLockListener { locked -> }
```

## Inline-контент

`GravityInlineView` (и `GravityInlineListView` для списка кампаний) позволяет встраивать персонализированный контент прямо в верстку экрана.

Добавь view в layout, указав `selector` (для `GravityInlineListView` — `groupSelector`):

```xml
<ai.gravityfield.gravity_sdk.ui.GravityInlineView
  android:layout_width="match_parent"
  android:layout_height="140dp"
  app:selector="homepage_inline_banner"
  app:color="@color/white"
  app:loaderLayout="@layout/loading_indicator" />
```

Передай `PageContext` через `init` — без этого контент не загрузится:

```kotlin
val view = findViewById<GravityInlineView>(R.id.inlineView)
view.init(PageContext(...))
```

Для стабильной работы UI `PageContext` для inline-view должен оставаться неизменным в рамках открытого экрана.

Каждый inline-view содержит кеш контента и позиции скролла, привязанный к паре `selector` + `pageContext` (для списков — `groupSelector` + `pageContext`). Это позволяет переиспользовать view в `RecyclerView` без повторной загрузки при пересоздании view.

При закрытии экрана необходимо очистить кеш, иначе записи будут накапливаться:

```kotlin
override fun onDestroy() {
  GravitySDK.instance.resetInlineViewCache("homepage_inline_banner", pageContext)
  // для GravityInlineListView:
  GravitySDK.instance.resetInlineListViewCache("slider_banner", pageContext)
  super.onDestroy()
}
```

## Обработка ошибок

Перед вызовами SDK необходимо убедиться, что он инициализирован. В противном случае будет выброшено исключение:

```
GravitySDK has not been initialized
```

## Подготовка in-app перед показом

`initialize` принимает необязательный `inAppPreparation`. Обработчик получает контент и кампанию, асинхронно проверяет товары и возвращает контент для показа либо `null`, чтобы пропустить этот in-app и перейти к следующей кампании.

```kotlin
GravitySDK.initialize(
    context,
    apiKey = "your-api-key",
    section = "your-section-id",
    gravityEventCallback = ::handleTrackingEvent,
    productViewBuilder = reviewIntegration.productViewBuilder,
    inAppPreparation = reviewIntegration.preparation(),
)
```

[Пример ReviewPromptIntegration](app/src/main/java/ai/gravityfield/sdk/ReviewPromptIntegration.kt) включает интерфейс клиентского API eligibility, карточку с кнопкой «Оставить отзыв» и обновление локального статуса через `didSubmitReview(sku)` после успешной отправки отзыва. Карточка вызывает `dismissInApp`; SDK закрывает sheet и затем вызывает `onOpenReview` для маршрутизации приложения. Пример не включён по умолчанию: для него нужен API клиента.

Обработчик можно написать напрямую:

В примере задайте `selector` кампании отзывов и `fallbackProductViewBuilder` для остальных кампаний. Если у кампании нет selector, ограничьте обработку по `campaign.payload.firstOrNull()?.campaignId`.

```kotlin
import ai.gravityfield.gravity_sdk.models.InAppPreparation
import ai.gravityfield.gravity_sdk.models.filteringProducts

val preparation = InAppPreparation(timeoutMillis = 5_000) { content, campaign ->
    if (campaign.selector != "purchase-reviews") return@InAppPreparation content
    val skus = content.products?.slots.orEmpty()
        .mapNotNull { it.item["sku"] as? String }.distinct()
    if (skus.isEmpty()) return@InAppPreparation null
    val eligible = reviewAPI.eligibleSkus(skus)
    content.filteringProducts { it.item["sku"] in eligible }
}
```

Порядок: `/choose` → существующий синхронный `productFilter` → content-loaded tracking → задержка кампании → `inAppPreparation` → повторная проверка возможности показа → UI и impression-события. Обработчик применяется к автоматическому показу после `trackView`/`triggerEvent`, а также при `open_step`. Получение через `getContentBy*` и inline-виджеты сохраняют прежнее поведение.

- Без обработчика нет ожидания API. Для других кампаний верните исходный `content`.
- `filteringProducts` сохраняет исходные слоты, порядок, tracking-данные и метаданные; пустой результат возвращает `null`. Пустые блоки в других кампаниях не запрещены.
- Таймаут по умолчанию — 5 секунд. Ошибка, таймаут или некорректный timeout (не положительный либо больше часа) пропускают in-app. Используйте отменяемый suspending API; не блокируйте поток. Отмена родительской coroutine распространяется дальше, например при `dispose`.
- После ожидания проверяются активность Activity, presentation lock и ревизия пользователя. `setUser` и `lockPresentation` инвалидируют ожидающий показ, даже если затем восстановлено прежнее состояние. При навигации внутри одного Activity блокируйте показ через `lockPresentation`. После разблокировки нужен новый view/trigger.
- Пропущенный in-app не отправляет impression/visible impression; content-loaded сохраняет семантику загрузки. Удалённые слоты не монтируются. В своём builder отправляйте клик через `sendProductEngagement`.
- Используйте recently purchased без fallback на непокупавшиеся товары. Правила доставки/отмены/возврата проверяет API eligibility. Запрашивайте кандидатов с запасом: SDK не восполняет удалённые товары.
- Перед запуском проверьте пользователя в `/choose` (отдельное исправление EGO-246). Клиентский статус отзывов должен принадлежать текущему аккаунту.

## Закрытие in-app из обычного ProductViewBuilder

Публичный `dismissInApp(contentId, completion)` закрывает только показанный SDK in-app с указанным `contentId`: modal, bottom sheet, full screen или snackbar. Inline-реквиджеты не закрываются. Тип и сигнатура `ProductViewBuilder` не меняются.

В обработчике кнопки сначала отправьте product click, затем запросите закрытие:

```kotlin
GravitySDK.instance.sendProductEngagement(ProductClickEngagement(slot, content, campaign))
GravitySDK.instance.dismissInApp(content.contentId) { dismissed ->
    if (dismissed) onOpenReview(sku)
}
```

При успешном закрытии completion вызывается на UI-потоке после анимации и удаления in-app с результатом `true`. Если контент не показан, уже закрывается/закрыт или отсутствует, completion получает `false` без ожидания закрытия. При повторном клике пока идёт закрытие возвращается `false`, чтобы не открывать форму дважды. Close-событие отправляет SDK один раз; вручную отправлять его не нужно. Если нужен переход и при отсутствии in-app, обработайте `false` в своей навигации. Вызывайте метод до `dispose()` SDK.

Метод сам не отправляет product click. Кнопку отзывов по-прежнему выбирайте по кампании и используйте текущий builder для обычных рекомендаций.

