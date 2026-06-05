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

