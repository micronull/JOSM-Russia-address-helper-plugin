# JOSM-Russia-address-helper-plugin
Плагин JOSM для загрузки адресов из ЕГРН.

## Что такое ЕГРН
Единый Государственный Реестр Недвижимости - государственная геоинформационная система, содержащая информацию о обьектах недвижимости, границах,
инфраструктуре. В интернете доступен по адресу [pkk.rosreestr.ru.](https://pkk.rosreestr.ru) С не очень давних пор [признан валидным](https://wiki.openstreetmap.org/wiki/RU:%D0%A0%D0%BE%D1%81%D1%81%D0%B8%D1%8F/%D0%9F%D1%83%D0%B1%D0%BB%D0%B8%D1%87%D0%BD%D0%B0%D1%8F_%D0%BA%D0%B0%D0%B4%D0%B0%D1%81%D1%82%D1%80%D0%BE%D0%B2%D0%B0%D1%8F_%D0%BA%D0%B0%D1%80%D1%82%D0%B0) (лицензионно совместимым) источником для ОСМ.
Плагин russia-address-helper позволяет в полуавтоматическом режиме извлекать текстовую информацию из слоев ЕГРН,
(используются слой земельных участков `PARCEL` и слой зданий `BUILDING`), в которой в большинстве случаев записан человекочитаемый адрес.

## Как работает плагин
В программе реализованы два режима работы - запрос по клику и массовая загрузка адресов. Запрос по клику позволяет узнать адресную информацию в произвольной точке.
Клик на карте отправляет координату запроса в ЕГРН, и, при получении не пустого ответа, пытается с помощью эвристик и регулярных выражений
разобрать текстовое описание полученных адресов на составляющие (в данный момент улицу и номер дома, в который может входить буква, корпус, строение, квартира,
так же распознаются населенные пункты (в случае когда нет адреса по улице), кварталы и микрорайоны).
Затем происходит сопоставление имя распознанной улицы/места с существующими в ОСМ.
При успешном разборе и сопоставлении на каждый найденный адрес в месте клика создается по точке с тэгами ("addr:housenumber", "addr:street").
Если в настройках плагина включена настройка "Создавать адресные точки для нераспознанных адресов", то адресные точки будут созданы и для всех
нераспознанных адресов тоже, что иногда помогает разобрать адрес вручную, или понять, почему он не валидный.

### Массовая загрузка
Этот режим считается основным, и позволяет обработать нарисованные в ОСМ здания, запросив в ЕГРН, распарсив и присвоив им адреса в автоматическом режиме.
Нужно выделить здания, и запустить массовую загрузку. Плагин профильтрует список выделенных зданий по следующим правилам:
- это линия (мультиполигоны пока не поддерживаются)
- у нее есть тэг building и он не garage или shed
- на ней нет тэга fixme (TODO вынести фильтрующие тэги в настройки)
<br>Для оставшихся в списке обьектов программа находит координаты точки запроса - для прямоугольника это пересечение диагоналей, для зданий сложной формы ищется точка, гарантированно лежащая внутри фигуры здания.
Затем выполняется запрос в ЕГРН, полученные данные разбираются на улицу, место и номер дома, улица/место сопоставляется с обьектами, уже внесенными в ОСМ. Если сопоставление прошло успешно,
адрес считается валидным, и присваивается зданию. Приоритет имеет адрес полученный со слоя ЕГРН `BUIDING`, если он есть. Проблемы, возникшие при разборе адреса и сопоставлении его с ОСМ,
обрабатываются валидаторами плагина, и отображаются в окне валидации JOSM. Их нужно обработать - исправить или проигнорировать. 

## Удаление дубликатов
При обработке частного сектора, промзон часто бывает, что на участке находится 2 и более строений, все они получат из ЕГРН одинаковый адрес.
Для автоматического разрешения этой проблемы нужно включить в настройках плагина функцию 'Удаление дублей', которая после получения данных оставит одинаковый адрес у строения с наибольшей площадью.
Функция не трогает уже существующие в редакторе данные, т.е удаляются только загруженные из ЕГРН в данном запросе дубли.
Так же существует настройка дальности поиска дубликатов в метрах, чтобы не захватывать соседние НП или улицы-дубли в одном НП.
Для отображения дубликатов существует свой валидатор.
(TO DO - переработка алгоритма валидации и исправления дубликатов через валидатор)

## Установка

1. Скопируйте файл [russia-address-helper.jar](https://github.com/De-Luxis/JOSM-Russia-address-helper-plugin/releases/latest/download/russia-address-helper.jar) в `%appdata%\JOSM\plugins` для Windows, и в `~/.local/share/JOSM/plugins` для Linux.
2. Включите плагин в `Правка - Настройки - Модули`. 

## Как пользоваться
1. (**НАСТОЯТЕЛЬНО РЕКОМЕНДУЕТСЯ**) Подключите слои ЕГРН в редактор JOSM каким-либо доступным вам способом. Один из способов описан ниже:
   1. Выберите в JOSM пункт меню «Слои» — «Настройки слоёв».
   2. Нажмите кнопку «+WMS» справа от списка выбранных подложек (внизу).
   В открывшемся диалоговом окне **«Добавить URL подложки»** вставить в поле **«6. Введите созданный URL WMS (необязательно)»** первое из приведённых ниже значений, а также заполните поле **«7. Введите название слоя»**. Поля 1-5 остаются пустыми.

   | Название слоя | Ссылка URL WMS                                                                                                                                                                                    |
   |---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
   | Земельные участки | http://localhost:8081/arcgis/rest/services/PKK6/CadastreObjects/MapServer/export?layers=show%3A21&format=PNG32&bbox={bbox}&bboxSR=102100&imageSR=102100&size=1024%2C1024&transparent=true&f=image | 
   | Здания и сооружения | http://localhost:8081/arcgis/rest/services/PKK6/CadastreObjects/MapServer/export?layers=show%3A30&format=PNG32&bbox={bbox}&bboxSR=102100&imageSR=102100&size=1024%2C1024&transparent=true&f=image |

   3. Скачайте веб-прокси nginx с [сайта разработчика](https://nginx.org/ru/download.html), распакуйте в удобную вам папку. 
   4. Отредактируйте файл конфигурации прокси, находящийся по пути (папка из п. III/conf/nginx.conf), добавив в него следующие строки:
   ```
   server {
       listen 8081;
       server_name localhost;

        location / {
            proxy_pass              https://pkk.rosreestr.ru/;
            proxy_ssl_verify        off;
        }
   }
   ```
   5. Запустите nginx просто двойным щелчком по nginx.exe, затем подключите слои зданий и земельных участков в редакторе, через меню JOSM "Cлои". Если все сделано по инструкции, через некотоое время (10-30 сек) в окне редактора появятся очертания зданий и границ участков. Запуск nginx нужно будет делать при каждом запуске JOSM после перезагрузки ПК.
   
   (Примечание: пункты iii - v нужны для решения проблемы сайта ЕГРН с невалидными HTTPS сертификатами. Для пользователей, импортировавших сертификаты в хранилище Java URL слоев будут другими, без замены адреса сайта ПКК на localhost, так же небходимо убрать галочку с настройки "Отключить SSL при запросах к ЕГРН")
2. Настройте [смещение основного слоя спутниковых снимков](https://wiki.openstreetmap.org/wiki/RU:JOSM/Plugins/Imagery_Offset_Database/Quick_Start#%D0%A1%D0%BC%D0%B5%D1%89%D0%B5%D0%BD%D0%B8%D0%B5_%D0%BF%D0%BE%D0%B4%D0%BB%D0%BE%D0%B6%D0%BA%D0%B8_%D0%B8_%D0%B5%D0%B3%D0%BE_%D0%B7%D0%B0%D0%B3%D1%80%D1%83%D0%B7%D0%BA%D0%B0) по GPS трекам (ссылка на вики ОСМ.)  
   
3. Настройте смещение слоев ПКК с границами зданий и участков по спутниковому снимку. Слой участков легко привязать в частном секторе, по заборам. При сдвиге слоя зданий не забывайте, что контур здания из ПКК должен лечь на фундамент здания на спутниковом снимке, а не на крышу. (Примечание: точность привязки и участков и зданий сильно плавает в зависимости от многих условий - рельефа конкретной местности, качества геодезических работ при сьемках и др. Сдвиг участков и зданий меняется от района к району, его надо регулярно корректировать, иначе плагин может запросить некорректный участок/здание)

4. Зайдите в настройки плагина (горячая клавиша `F-12`, раздел "Настройки плагина ЕГРН". Выберите в выпадающем списке настройки "Сдвигать координаты запроса согласно сдвигу выбранного слоя" слой Зданий или участков. (Как правило, слой здании привязан точнее, и лучше выбрать его, но не для всех регионов страны это верно.)

Плагин готов к работе.
5. Настройки редактора "Для удобства":
- вынесите кнопки плагина на панель инструментов. Для этого нажмите правой кнопкой на панель инструментов JOSM, выберите "Настроить панель инструментов", и добавьте кнопки из раздела "Действия - Данные - Загрузка адресов из ЕГРН"
- добавьте кнопку поиска зданий без адресов в области видимости. Для этого нажмите ctrl+F, введите в строке поиска `building=* AND -"addr:housenumber"=* AND inview` затем проставьте галочку "добавить кнопку на основную панель инструментов" и нажмите `Поиск`
 
6. Процесс обрисовки
   1. добавление улиц: 
   - нарисуйте линии улиц. 
   - вариант 1 (с помощью Пипетки): с помощью инструмента Пипетка (Запрос на месте клика) <img src="src/main/resources/images/mapmode/click.svg" width="16" height="16"> запросите адреса участков и/или зданий вдоль предполагаемой улицы, ориентируясь на приблизительные начало и конец улицы.
   Из содержания тэга "addr:RU:egrn" сгенерированных точек прочтите имя улицы, и присвойте его линиям улицы, обязательно преобразовав в [формат наименования ОСМ](https://wiki.openstreetmap.org/wiki/RU:%D0%A0%D0%BE%D1%81%D1%81%D0%B8%D1%8F/%D0%A1%D0%BE%D0%B3%D0%BB%D0%B0%D1%88%D0%B5%D0%BD%D0%B8%D0%B5_%D0%BE%D0%B1_%D0%B8%D0%BC%D0%B5%D0%BD%D0%BE%D0%B2%D0%B0%D0%BD%D0%B8%D0%B8_%D0%B4%D0%BE%D1%80%D0%BE%D0%B3).
   Обязательно удалите сгенерированные плагином точки с помощью поиска по "addr:RU:egrn"; не загружайте их в ОСМ, это будет ошибкой.
   - вариант 2 : добавьте здания, запросите их адреса и исправьте ошибку "ЕГРН не найдена улица" валидатора 
   2. добавление зданий:
   - обрисуйте здания по спутниковому снимку или загрузите данные ОСМ, где здания уже обрисованы.
   - с помощью поискового запроса зданий без номера, либо инструмента выделения/лассо выделите разумное количество зданий (10-100) (Хороший вариант - выбрать полигон жилой застройки и воспользоваться функцией "Выделение - Все внутри"). Если попутно выделятся дороги или просто точки, ничего страшного, плагин отфильтрует их при запросе.
   - запросите данные с помощью кнопки "Для выделенных обьектов" <img src="src/main/resources/images/select.svg" width="16" height="16"> . Поскольку сайт ЕГРН не очень стабильно отвечает на запросы, придется подождать некоторое время.
   - плагин обработает полученные данные по следующему алгоритму:
        1. если у здания/участка есть адрес, и он успешно разобран на составляющие, и для распознанных обьектов нашлось соответствие в данных ОСМ (найдена улица либо точка населенного пункта/квартала/микрорайона), и не найден дубликат, то зданию будет присвоен адрес.
        2. в остальных случаях плагин сгенерирует в окне валидации одну из возможных ошибок или предупреждений, которые нужно просмотреть и обработать:
            1. **ЕГРН пустой ответ**. Самоочевидное предупреждение. Оно генерируется в категории OTHER, вы не увидите его, если в настройках JOSM не включено отображение этой категории ошибок.
            2. **ЕГРН адрес найден**. Это предупреждение генерируется, если адрес успешно распознан и присвоен. Но их все равно нужно обработать - просмотреть глазами и убедиться, что алгоритм разбора не ошибся.
           При нажатии на кнопку "Исправить" отобразится диалог исправления в котором можно исправить тэги, удалить уже присвоенные, либо проигнорировать ошибку.
            3. **ЕГРН несколько адресов**. Плагин получил в ответе несколько валидных адресов. Для исправления нужно выбрать один, как правило это адрес здания, а не участка. Если ни один адрес на самом деле не подходит, можно проигнорировать ошибку.
            4. **ЕГРН нечеткое совпадение**. Плагин сопоставил адрес по алгоритму нечеткого совпадения, и теперь надо убедиться, что это не ложное срабатывание. Можно присвоить распознанные адресные тэги, или игнорировать эту ошибку.
            5. **ЕГРН содержит номер квартиры**. Адрес успешно присвоен, но в нем содержатся номера квартир. Можно сгенерировать адресную точку, или игнорировать ошибку.
            6. **ЕГРН улица не найдена**. Адрес был распознан, но в данных ОСМ не нашлось линии с подходящим именем.
            7. **ЕГРН место не найдено**. Адрес был распознан, но в данных ОСМ не нашлось обьекта с подходящим именем.
            8. **ЕГРН дубликат адреса** Адрес был распознан, но он уже существует в ОСМ или среди других распознанных адресов.
            9. **ЕГРН ошибка разбора** Не удалось распознать адрес, но можно попробовать разобрать вручную, или игнорировать эту проблему.

## Горячая клавиша

1. Добавьте кнопку на панель инструментов в  `Правка - Настройки - Панель инструментов`. 
2. Нажмите правой кнопкой по иконке плагина на панели инструментов и выберите `Свойства горячей клавиши`.


## Ограничения

1. Не выделяйте много домов сразу, так как с каждым домом отправляется запрос. Сервис ПКК может не выдержать или заблокировать доступ из-за большого количества запросов. В идеале работать выделяя дома вдоль улицы или в небольшой области, приводить данные в порядок и уже после этого приступать к следующим.
2. На данный момент ошибка пропадет из списка валидации, если зайти в диалог ее исправления и закрыть окошко крестиком или выбрать "Отмена". При этом ошибка не будет исправлена. Это проблема кода валидации JOSM, обходной путь - нажать в окне валидаторов кнопку "Валидировать". Неисправленная ошибка снова отобразится.

## Примечания

* Плагин на данный момент может разобрать данные только для улиц, переулков, проездов, проспектов, шоссе, площадей, кварталов, микрорайонов (обозначенных точкой place=neighbourhood, suburb), деревень, сёл и поселков (обозначенных точкой или границей c тэгом place=village, hamlet). 
* Для того чтобы в тэги обьектов сохранялись сырые данные ЕГРН, необходимо в настройках включить галочку "Записывать адрес из ЕГРН в `addr:RU:egrn`". Это отладочные данные, не загружайте их в ОСМ!
* Если в ЕГРН встречается многократное именование улицы, которое не может быть сопоставлено с данными ОСМ ("Советская Б улица"), в качестве временного решения можно добавить улице в ОСМ тэг "egrn_name" или "alt_name" в который вписать наименование из ЕГРН. После загрузки и сопоставления адресов egrn_name надо удалить, это не валидные данные!
* Плагин умеет бороться с небольшими опечатками в именовании улиц, ошибка в 1-2 буквы может быть проигнорирована (поиск совпадений по алгоритму Jaro-Winkler distance). При таком совпадении будет сгенерировано предупрекждение валидатора.
* Плагин умеет сопоставлять номерные улицы и улицы с инициалами ("улица Карла Маркса" сопоставится с "ул. К. Маркса")
* Программа пытается распознать номер дома с учетом строения, корпуса, буквы. Номера квартир в адресе приведут к генерации дополнительных точек адреса с тэгами `addr:flats`. Понимания, что делать с такими данными пока нет, лучше не загружать их в ОСМ.

## TODO

- [x] Новый инструмент - пипетка. Создаёт точку с данными из ЕГРН в месте клика.
- [x] Локализация на русский. 
- [x] Отдельная галка в настройках для работы с дублями. Проверка существующих адресов и назначение адреса на самое большое по площади здание.
- [x] Поддержка других типов улиц и обьектов, такие как тупики, аллеи, микрорайоны, кварталы.
- [ ] Возможность добавлять свои типы улиц в настройках.
- [ ] Возможность редактировать и добавлять пользовательские регулярные выражения для парсера адресов ЕГРН.
- [ ] Загрузка справочников типов улиц и регулярных выражений парсера через единый репозиторий. Избавит от необходимости обновления плагина и даст возможность другим людям дополнять парсер.
- [ ] Отдельный парсер для номеров домов. Должен приводить к [общепринятому виду](https://wiki.openstreetmap.org/wiki/RU:Addresses#%D0%9D%D1%83%D0%BC%D0%B5%D1%80%D0%B0%D1%86%D0%B8%D1%8F_%D0%B4%D0%BE%D0%BC%D0%BE%D0%B2).
- [x] Попытка повторной отправки ошибочных запросов установленное в настройках количество раз. Вывод уведомления о не загруженных данных.
- [x] Вывод в удобном виде информации о распознанных данных и проблемах распознавания при массовой загрузке
