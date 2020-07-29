##### NaturalNumberGeneratorBenchmark
## Java Performance for Measuring Time

### Проблема
Java предлагает нам 2 базовых функции для замера времени:
* System.currentTimeMillis(); 
* System.nanoTime(). 

Существуютт определенные отличия между ними. 

#### 1. УСТОЙЧИВОСТЬ ТОЧКИ ПРОИСХОЖДЕНИЯ(STABILITY OF THE POINT OF ORIGIN)
##### System.currentTimeMillis() 
Возвращает количество миллисекунд с начала эпохи Unix - 1 января 1970 года по Гринвичу. <br>
Это сразу говорит нам, что самая точная гранулярность currentTimeMillis() составляет 1 миллисекунду, что делает невозможным измерение менее 1 мс.<BR> 
Тот факт, что currentTimeMillis () использует 1 января 1970 года UTC в качестве ориентира, является:
* ХОРОШИМ, т.к мы можем сравнивать значения currentTimeMillis(), возвращаемые двумя разными JVM и даже двумя разными компьютерами.
* ПЛОХИМ, т.к. сравнение не будет очень полезным, когда на наших компьютерах не синхронизировано время. Часы в типичных серверах не идеально синхронизированы, и всегда будет некоторый разрыв.<BR>

Иногода разрыв может быть приемлемым, например, если мы сравниваем файлы журналов из двух разных систем, то это нормально, если временные метки не полностью синхронизированы. 
Однако иногда этот разрыв может привести к катастрофическим результатам, например, когда он используется для разрешения конфликтов в распределенных системах.

##### System.nanoTime() 
Возвращает количество наносекунд с некоторой произвольной точки в прошлом.

#### 2.ЧАСОВАЯ МОНОТОННОСТЬ(CLOCK MONOTONICITY)
_Проблема заключается в том, что возвращаемые значения не гарантируются монотонно увеличивающимися!_
Если у вас есть 2 последовательных вызова `currentTimeMillis()`, то второй вызов может вернуть более низкое значение, чем первый. Это противоречит здравому смыслу и может привести к бессмысленным результатам, таким как прошедшее время, являющееся отрицательным числом. Понятно, что currentTimeMillis () не является хорошим выбором для измерения прошедшего времени внутри приложения.
<BR>
<BR>
`System.nanoTime()` не использует эпоху Unix в качестве ориентира, но некоторую неопределенную опорную точку в прошлом. 
Точка остается неизменной во время одного выполнения JVM, но это все. Таким образом, бессмысленно даже сравнивать значения `nanoTime()`, возвращаемые двумя разными JVM, работающими на одном компьютере, не говоря уже о разных компьютерах. 
Опорная точка обычно связана с последним запуском компьютера, но это просто деталь реализации, и мы не можем на нее полностью положиться. Преимущество состоит в том, что даже когда время настенных часов в компьютере по какой-то причине идет в обратном направлении, оно не окажет никакого влияния на `nanoTime()`. Вот почему `nanoTime()` является отличным инструментом для измерения истекшего времени между двумя событиями в одной JVM, но мы не можем с ее помощью сравнивать временные метки из двух разных JVM.

#### 3.ИМПЛЕМЕНТАЦИЯ в JAVA(IMPLEMENTATION IN JAVA)
* `System.currentTimeMillis()` нативный метод, поэтому IDE ничего нам не скажет об имплементации. 

   * Windows реализация:
```cpp
JVM_LEAF(jlong, JVM_CurrentTimeMillis(JNIEnv *env, jclass ignored))
  JVMWrapper("JVM_CurrentTimeMillis");
  return os::javaTimeMillis();
JVM_END
```
  * Linux реализация:
```cpp
jlong os::javaTimeMillis() {
  timeval time;
  int status = gettimeofday(&time, NULL);
  assert(status != -1, "linux error");
  return jlong(time.tv_sec) * 1000 + jlong(time.tv_usec / 1000);
}
```

Функция `gettimeofday()` реализована `glibc`, которая в конечном итоге вызывает ядро Linux.

* `System.nanoTime()` нативный метод, поэтому IDE ничего нам не скажет об имплементации.
   * Windows реализация:
```cpp
JVM_LEAF(jlong, JVM_NanoTime(JNIEnv *env, jclass ignored))
  JVMWrapper("JVM_NanoTime");
  return os::javaTimeNanos();
JVM_END
```
  * Linux реализация:
```cpp
jlong os::javaTimeNanos() {
  if (os::supports_monotonic_clock()) {
    struct timespec tp;
    int status = os::Posix::clock_gettime(CLOCK_MONOTONIC, &tp);
    assert(status == 0, "gettime error");
    jlong result = jlong(tp.tv_sec) * (1000 * 1000 * 1000) + jlong(tp.tv_nsec);
    return result;
  } else {
    timeval time;
    int status = gettimeofday(&time, NULL);
    assert(status != -1, "linux error");
    jlong usecs = jlong(time.tv_sec) * (1000 * 1000) + jlong(time.tv_usec);
    return 1000 * usecs;
  }
}
```
Есть 2 ветви: 
* если ОС поддерживает монотонные часы, она будет использовать их, 
* а в противном случае она делегирует нашему старому другу `gettimeofday()`. <BR>

__`gettimeofday()` - это тот же вызов Posix, который используется `System.currentTimeMillis()`!__
Очевидно, что конверсия выглядит немного иначе, поскольку гранулярность `nanoTime()` выше, но это тот же вызов Posix!
Это подразумевает, что при некоторых обстоятельствах `System.nanoTime()` использует эпоху Unix в качестве отправной точки, поэтому он может вернуться в прошлое! Другими словами: __это не гарантирует часовую монотонность!__

Хорошей новостью является то, что все современные дистрибутивы Linux поддерживают монотонные часы. 
Т.е. можно предположить, что эта ветка существует для совместимости с древними версиями ядра / `glibc`. 

Т.е. для большинства из нас важно знать, что OpenJDK практически всегда вызывает функцию Posix `clock_gettime()`, 
которая реализована в делегатах `glibc` и `glibc` для ядра Linux.

### Результаты
#### Windows 10 Pro
##### Intel(R) Core(TM) i7-9700 CPU @ 3.00Ghz
```
# JMH version: 1.23
# VM version: JDK 11, OpenJDK 64-Bit Server VM, 11+28
# Warmup: 5 iterations, 10 s each
# Measurement: 5 iterations, 10 s each
Benchmark                      Mode  Cnt   Score   Error  Units
MeasuringTimeBenchmark.millis  avgt   25   3.930 ± 0.218  ns/op
MeasuringTimeBenchmark.nano    avgt   25  22.115 ± 0.041  ns/op
```
Выводы:
* неразумно использовать `System.nano()` для измерения чего-либо, занимающего менее нескольких десятков наносекунд, 
поскольку накладные расходы нашего инструментария будут выше, чем измеряемый интервал. 
* избегать использования `nanoTime()` в тесных циклах(который содержит мало инструкций и повторяется много раз), 
потому что задержка быстро увеличивается. 
* представляется целесообразным использовать `nanoTime()` для измерения, например, время отклика с удаленного сервера или длительность сложного расчета.

### Links
* [MEASURING TIME: FROM JAVA TO KERNEL AND BACK](https://www.javaadvent.com/2019/12/measuring-time-from-java-to-kernel-and-back.html)