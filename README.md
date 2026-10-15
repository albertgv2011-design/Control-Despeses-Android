# Control Despeses Android

Projecte inicial Android natiu amb Kotlin i Jetpack Compose.

## Què inclou aquesta primera base
- Pantalla d'inici amb saldo real, ingressos, despeses i pressupost mensual.
- Moviments d'ingrés i despesa amb categories seleccionables.
- Historial de moviments amb data i hora.
- Configuració local del pressupost i del saldo inicial.
- Persistència local inicial al dispositiu.
- Workflow de GitHub Actions per compilar un APK de depuració i publicar-lo com a artefacte.

## Limitacions actuals
Aquesta és una primera base funcional, no la versió final especificada. Encara no inclou Firebase/Google Sign-In, sincronització al núvol, base de dades Room, IA, biometria, banc virtual, objectius d'estalvi complets, recurrències ni la icona original. No introdueixis dades financeres reals fins que s'hagi revisat i provat la implementació.

## Compilar amb GitHub Actions
1. Crea un repositori privat a GitHub.
2. Puja el contingut d'aquesta carpeta a la branca `main`, mantenint la carpeta `.github/workflows`.
3. Obre la pestanya **Actions** i activa els workflows si GitHub ho demana.
4. Executa **Build Android APK** o espera que s'executi després d'un push.
5. Quan el workflow acabi correctament, obre l'execució i descarrega l'artefacte `control-despeses-debug-apk`.
6. Descomprimeix l'artefacte ZIP: conté `app-debug.apk`.

## Requisits
- Compilació al núvol amb GitHub Actions.
- JDK 17 i Gradle 8.9.
- Android SDK instal·lat automàticament per les eines de compilació Gradle/Android al runner.

## Icona
La icona definitiva està pendent. Cal adjuntar la imatge original escollida i convertir-la als recursos Android necessaris abans de la versió final.

## Seguretat
No s'hi han afegit claus d'API ni credencials. Les integracions externes s'han de configurar amb secrets de GitHub/serveis backend i regles de seguretat adequades.
