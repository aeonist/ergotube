# Источники материалов

Все файлы в `assets/` хранятся локально; HTML не загружает внешние ресурсы. Ссылки на референсы открываются только по нажатию. Демонстрационные названия, каналы и числа не утверждают существование реальных видео с такими показателями.

## Фотографии

Unsplash License: <https://unsplash.com/license>. Это фотографии для визуального исследования, а не распространяемая библиотека изображений.

| Файл | Автор и страница источника | Изменения |
|---|---|---|
| `sea.jpg` | Taylor Cowling — <https://unsplash.com/photos/ocean-during-golden-hour-ZeqXEuIfOQk> | CDN-кроп 1200×675, JPEG 85%, без цветокоррекции |
| `architecture.jpg` | Hananiah Hardin — <https://unsplash.com/photos/the-brutalist-building-is-bathed-in-sunlight-N_QoHie_NV4> | CDN-кроп 1200×675, JPEG 85%, без цветокоррекции |
| `forest.jpg` | Olga Subach — <https://unsplash.com/photos/green-trees-beside-lake-during-foggy-weather-yxDLoo7Vshw> | CDN-кроп 1200×675, JPEG 85%, без цветокоррекции |

## Настоящее зерно

`grain-trix.png`: Wiley.andrew.r, **Tri-x 400 grain2.png**, CC0 1.0. [Описание и лицензия](https://commons.wikimedia.org/wiki/File:Tri-x_400_grain2.png). Скан Kodak Tri-X 400, Nikon Coolscan V ED, равномерно экспонированный средний тон. Исходник 5642×3671, 16-bit grayscale. Сохранён кроп 780×1680 с позиции x=2400, y=900, переведённый в 8-bit grayscale. Не добавлялся синтетический шум. В CSS кроп показывается 390×840; сила, контраст и смешивание задаются только фоновому слою. Исходные 35,5 MB не входят в поставку; достаточно кропа 795 KB.

## Шрифты

Получены из Google Fonts, локальные WOFF2 с разделением на unicode-range, в том числе кириллицу. Исходный CSS сохранён как `assets/fonts.css`, сетевые URL заменены локальными именами с хешем.

- [Golos Text](https://fonts.google.com/specimen/Golos+Text) — `golos-text-OFL.txt`.
- [Lora](https://fonts.google.com/specimen/Lora) — `lora-OFL.txt`.
- [Onest](https://fonts.google.com/specimen/Onest) — `onest-OFL.txt`.
- [Roboto Flex](https://fonts.google.com/specimen/Roboto+Flex) — `roboto-flex-OFL.txt`.

Семьи распространяются под SIL Open Font License 1.1. Лицензионные тексты скопированы из официального репозитория google/fonts и сохранены рядом со шрифтами.

## Значки

Небольшой набор геометрии Google Material Icons: Home, Subscriptions, Slideshow, Search, VideoLibrary, MoreVert, Schedule, Download, Block, Share, PlayArrow. Имена и значения повторяют существующий Android-интерфейс. Геометрия записана как локальные SVG-symbols. [Google Material Design Icons](https://github.com/google/material-design-icons), Apache License 2.0; текст в `material-icons-LICENSE.txt`.

## Референсы

Кадры Claude, материалы Apple, MUBI и Dribbble не встроены и не перепродаются; страницы связаны ссылками в записке и HTML. Генеративные фотографии и синтетическое зерно в поставке не используются.
