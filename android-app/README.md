# Bani pe scurt — Aplicația Android

Aplicație Android pentru importul extraselor bancare BRD (PDF și CSV), categorisire inteligentă și urmărirea cheltuielilor, 100% offline și privată.

---

## 📱 Instalare rapidă

Poți instala direct APK-ul pre-compilat:
* Fișier: `../BaniPeScurt-v1.1.apk`
* Comandă ADB:
  ```bash
  adb install ../BaniPeScurt-v1.1.apk
  ```

---

## 🛠️ Deschidere în Android Studio

1. Deschide folderul `android-app` în Android Studio.
2. Așteaptă sincronizarea Gradle.
3. Apasă **Run** (sau `Shift + F10`) pentru rulare pe emulator sau telefon fizic conectat prin USB.

---

## 💡 Funcționalități cheie

1. **Import extras PDF & CSV**:
   * Suportă atât extrasele PDF generate de YOU BRD Web, cât și extrasele CSV.
   * Procesare exclusiv pe telefon (fără trimitere pe vreun server).
2. **Auto-categorisire inteligentă**:
   * Recunoaște comercianți populari din România (supermarketuri, transport, utilități, sănătate etc.).
   * Memorează modificările utilizatorului: dacă schimbi categoria unui comerciant, aplicația învață și propune aceeași categorie la importurile ulterioare.
3. **Calcule exacte**:
   * Banii sunt reprezentați intern ca întregi (`long amountBani`), evitând erorile de precizie IEEE 754.
4. **Căutare & Editare**:
   * Căutare rapidă după comerciant/categorie.
   * Editare facilă prin atingerea iconiței ✎ sau a oricărei tranzacții.
5. **Export date**:
   * Buton de **Export CSV** direct din antetul aplicației pentru backup sau transfer în versiunea Web / Excel.
