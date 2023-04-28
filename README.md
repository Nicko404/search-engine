# search-engine
Описание проекта

Поисковой движок - приложение для быстрого поиска по сайтам. Быстрота поиска достигается за счет обхода веб-страниц сайта и составления поискового индекса.

Веб-интерфейс состоит из одной страницы с тремя вкладками:

На вкладке `Dashboard` отображается общая статистика по всем сайтам, а также детальная статистика и статус по каждому из сайтов.

![Dashboard](https://github.com/Nicko404/search-engine/blob/master/Dashboard.png)

На вкладке `Management` находятся инструменты управления поисковым движком — запуск и остановка полной индексации (переиндексации), а также возможность 
добавить (обновить) отдельную страницу по ссылке.

![Managment](https://github.com/Nicko404/search-engine/blob/master/Managment.png)

Вкладка `Search` служит для поиска по индексированным страницам. Можно искать как по всем сайтам так и по одному определённому сайту. При нажатии на кнопку 
"Найти" выводятся результаты поиска.

![Search](https://github.com/Nicko404/search-engine/blob/master/Search.png)

Используемые технологии:

- Spring boot
- JPA/Hibernate
- MySQL
- Multithreading, ForkJoinPool, ScheduledExecutorService
- Lombok
- Log4j
- JSOUP

Инстукция по запуску проекта

Для работы приложения, требуется база данных MySQL. В конфигурационном файле `application.yaml` нужно указать имя пользователя, пароль и url для базы данных.

![appDatasource](https://github.com/Nicko404/search-engine/blob/master/appDatasource.png)

Далее указываются url и имена сайтов по которым будет производиться поиск.

![siteSettings](https://github.com/Nicko404/search-engine/blob/master/siteSettings.png)

Запуск приложения:
`java -jar SearchEngine-1.0-SNAPSHOT.jar`
