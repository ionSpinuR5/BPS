# Bani pe scurt

Aplicație de gestiune a cheltuielilor personale, 100% offline și orientată pe confidențialitate (Local-First), cu suport pentru extrase bancare BRD (PDF și CSV).

---

## 🚀 Cum poți încerca aplicația acum

### 1. Prototipul Web (Imediat în browser)
Deschide fișierul [`index.html`](index.html) în orice browser (Safari, Chrome, Arc, Brave):
* **Fără instalare**: Datele sunt salvate local în browser (`localStorage`).
* **Exemple demonstrative**: Apasă pe butonul **„Exemple demo”** din colțul dreapta-sus pentru a încărca instant cheltuieli realiste (Mega Image, Bolt, Enel, Farmacia Tei etc.) și a vedea graficele și repartizarea pe categorii.
* **Import & Export CSV**: Poți exporta datele în format CSV sau importa extrase direct în browser.
* **Căutare & Editare**: Filtrează cheltuielile în timp real după comerciant sau editează tranzacțiile direct (iconița ✎).

### 2. Aplicația Android (APK gata compilat)
* Fișierul APK actualizat este gata compilat în rădăcina proiectului:
  👉 [`BaniPeScurt-v1.1.apk`](BaniPeScurt-v1.1.apk)
* Poți instala fișierul direct pe telefonul tău Android (`adb install BaniPeScurt-v1.1.apk` sau transfer prin AirDrop/Cablu USB/Drive) ori în emulatorul Android Studio.

---

## 🌟 Îmbunătățiri aduse în versiunea v1.1

1. **Precizie financiară absolută (Bani/Cenți)**:
   * Toate calculele matematice și sumele sunt gestionate prin valori întregi (`long amountBani`), eliminând erorile de rotunjire specifice calculelor în virgulă mobilă (`double`).
2. **Import fișiere CSV & PDF**:
   * Pe lângă extrasele PDF YOU BRD, aplicația suportă acum și importul extraselor în format **CSV** cu detectare automată a separatorilor și coloanelor.
3. **Sistem de învățare a categoriilor (Machine Learning euristic local)**:
   * Dacă modifici manual categoria pentru un magazin/comerciant (ex. setezi un magazin pe o altă categorie), aplicația memorează această asociere local și o aplică automat la toate importurile viitoare.
4. **Rezolvarea bug-ului de deduplicare**:
   * Tranzacțiile legitime identice efectuate în aceeași zi (de exemplu două călătorii STB de aceeași sumă) nu mai sunt eliminate accidental, ci primesc identificatori secvențiali unici.
5. **Căutare și filtrare în timp real**:
   * Câmp de căutare dedicat pentru a găsi orice plată instant după comerciant sau categorie.
6. **Editare tranzacții**:
   * Posibilitatea de a modifica descrierea, suma sau categoria oricărei plăți existente.
7. **Export & Backup complet**:
   * Exportă toate datele într-un fișier CSV compatibil universal cu Excel, Google Sheets și varianta Web a aplicației.
8. **Stabilitate UI și salvare de stare la rotire**:
   * Interfața nu se mai reface agresiv prin `setContentView()`, iar starea draft-urilor de import este conservată chiar dacă ecranul este rotit.
