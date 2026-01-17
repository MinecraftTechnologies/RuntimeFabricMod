
# Fabric Runtime Mod

Данный проект позволяет упаковать ваш Fabric мод в .dll файл и загрузить в процесс Minecraft мод Fabric в runtime.

# Доступые фичи
✅ Подгрузка мода в runtime  
✅ Runtime Mixin + MixinExtras  
✅ Mixin Refmap работает корректно  

# Проблемы
❌ AccessWidener на данный момент недоступен  
❌ Accessors миксины на данный момент недоступны  
❌ Могут быть проблемы при редактировании final значений в миксинах  
❌ Нельзя добавлять новые поля в targetClass миксинами  

### Доп. информация
Тестировалось на данных версиях:  
  	- fabric-loader = 0.18.4  
  	- asm_version = 9.9  
 		- mixin_version = 0.17.0+mixin.0.8.7  
 		- mixin_extras_version = 0.5.0  

## Ссылки

 - [Основа проекта (radioegor146)](https://github.com/radioegor146/jar-to-dll)
 - [JNIHooks dependency](https://github.com/rdbo/jnihook)


## Authors

- [@twentybytes](https://www.github.com/twentybytes)


## Использование

Создайте пустой проект java, поместите туда GenerateHeaders.java и скомпилируйте исполняемое приложение.

### Генерация .h файлов
Положите в одну директорию Injector.java, mod.jar и GenerateHeaders.jar.

Запустите генерацию
```bash
java -jar GenerateHeaders.jar injector Injection.class injector.h
java -jar GenerateHeaders.jar input-jar mod.jar jar.h
```

В папке появятся файлы injector.h, jar.h

### Компиляция .dll

Перенесите полученные файлы заголовков (injector.h, jar.h) в папку `dll\injecting_classes`.  
Укажите путь до java в файле `CMakeLists.txt`
 
Откройте проект библиотеки в CLion или VisualStudio и выполните Release Build.
В папке `Release` будет .dll файл.

