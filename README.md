# ADB HTTP Server

Android приложение с GUI, которое поднимает HTTP-сервер для выполнения ADB команд.

## Возможности

- Поднимает HTTP-сервер на заданном порту
- Принимает POST-запросы на `/exec` с параметром `command`
- Выполняет ADB shell-совместимые команды через `Runtime.exec(...)`
- Принимает POST-запросы на `/start` для запуска Android активностей (аналог `am start`)
- Использует Android Intent API для запуска активностей
- Показывает статус сервера на экране
- Работает в фоне как foreground service

## Использование

1. Запустите приложение
2. Введите номер порта (1024-65535)
3. Нажмите "Start Server"
4. Сервер будет работать в фоне

## API

### POST /exec
Выполняет ADB команду.

**Параметры:**
- `command` - команда для выполнения

**Пример запроса:**
```bash
curl -X POST http://localhost:8080/exec -d "command=adb shell ls /sdcard"
```

**Ответ:**
```json
{
  "exitCode": 0,
  "output": "file1.txt\nfile2.txt\n",
  "error": ""
}
```

### POST /start
Запускает активность Android (аналог команды `am start`).

**Параметры:**
- `action` - действие Intent (например, `android.intent.action.VIEW`)
- `data` - данные для Intent (например, `tel:1234567890`)
- `category` - категория Intent (например, `android.intent.category.DEFAULT`)
- `component` - компонент в формате `package/activity` (например, `com.android.contacts/.activities.PeopleActivity`)
- `flags` - флаги Intent (числовое значение)
- `extras` - дополнительные параметры в формате `key:value,key2:value2`
- `force_foreground` - принудительно вывести приложение на передний план перед запуском активности (true/false)

**Примеры запросов:**

Запуск браузера с URL:
```bash
curl -X POST http://localhost:8080/start -d "action=android.intent.action.VIEW&data=https://www.google.com"
```

Звонок на номер:
```bash
curl -X POST http://localhost:8080/start -d "action=android.intent.action.VIEW&data=tel:1234567890"
```

Открытие контактов:
```bash
curl -X POST http://localhost:8080/start -d "component=com.android.contacts/.activities.PeopleActivity"
```

Принудительный запуск с выводом приложения на передний план:
```bash
curl -X POST http://localhost:8080/start -d "action=android.intent.action.VIEW&data=https://www.google.com&force_foreground=true"
```

**Ответ:**
```json
{
  "success": true,
  "message": "Activity started successfully",
  "intent": {
    "action": "android.intent.action.VIEW",
    "data": "https://www.google.com",
    "categories": "android.intent.category.DEFAULT",
    "component": "null"
  }
}
```

### GET /status
Возвращает статус сервера.

**Ответ:**
```json
{
  "status": "running",
  "port": 8080
}
```

## Требования

- Android 7.0 (API 24) или выше
- Разрешения на интернет и работу в фоне

## Решение проблем с запуском активностей

### Проблема: Активности не запускаются из фонового сервиса

Начиная с Android 10, система ограничивает запуск активностей из фоновых сервисов для повышения безопасности. Если активность не запускается, попробуйте:

1. **Использовать параметр `force_foreground=true`** - принудительно выводит приложение на передний план перед запуском активности
2. **Предоставить разрешения** - приложение запросит разрешения `SYSTEM_ALERT_WINDOW` и `START_ACTIVITIES_FROM_BACKGROUND`
3. **Проверить логи** - в логах приложения будут видны попытки запуска и ошибки

### Примеры решения проблем:

```bash
# Если активность не запускается, попробуйте с force_foreground
curl -X POST http://localhost:8080/start -d "action=android.intent.action.VIEW&data=https://www.google.com&force_foreground=true"

# Для системных приложений может потребоваться точное указание компонента
curl -X POST http://localhost:8080/start -d "component=com.android.chrome/com.google.android.apps.chrome.Main&force_foreground=true"
```

## Безопасность

⚠️ **ВНИМАНИЕ**: Это приложение выполняет системные команды и запускает активности. Используйте только в доверенной сети!
