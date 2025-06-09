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

## Обработка ошибок

Перед вызовами SDK необходимо убедиться, что он инициализирован. В противном случае будет выброшено исключение:

```
GravitySDK has not been initialized
```

