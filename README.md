# Bani pe scurt

Aplicație de gestiune a cheltuielilor personale, 100% offline și orientată pe confidențialitate (Local-First), cu **sincronizare în timp real din notificări** și suport pentru extrase bancare (PDF și CSV).

---

## 🚀 Cum poți încerca aplicația acum

### 1. Prototipul Web (Imediat în browser)
Deschide fișierul [`index.html`](index.html) în orice browser:
* **Fără instalare**: Datele sunt salvate local în browser (`localStorage`).
* **Temă Întunecată / Luminoasă**: Comută oricând între Dark Mode și Light Mode cu butonul `🌙 / ☀️` din antet.
* **Import PDF YOU BRD & CSV**: Trage fișierul PDF descărcat din bancă direct peste pagină (Drag & Drop) pentru extragere locală cu Mozilla PDF.js.
* **Descrieri optimizate**: Structură fixă a tabelului — coloanele Categorie, Data și Sumă rămân perfect aliniate și vizibile indiferent de lungimea descrierilor.
* **Exemple demonstrative**: Poți încărca date demo și le poți șterge oricând cu un singur click fără a afecta datele reale.

### 2. Aplicația Android (APK cu Sincronizare în Timp Real)
* Fișierul APK actualizat este gata compilat în rădăcina proiectului:
  👉 [`BaniPeScurt-v1.1.apk`](BaniPeScurt-v1.1.apk)
* Poți instala fișierul direct pe telefonul tău Android (`adb install BaniPeScurt-v1.1.apk` sau transfer prin WhatsApp/AirDrop/Drive).

---

## ⚡ Plăți în Timp Real (Android)

Aplicația include serviciul nativ `BankNotificationListener`:
* **Atingi POS-ul $\rightarrow$ Plata este salvată în 1 secundă!**
* Ascultă automat notificările de plată generate de:
  * **Google Wallet**
  * **YOU BRD**
  * **Revolut**
  * **BT Pay (Banca Transilvania)**
  * **ING Home'Bank, BCR George, Raiffeisen etc.**
* Extrage automat suma și numele comerciantului, îi atribuie categoria potrivită și o salvează fără să fie nevoie să deschizi aplicația.
* **100% Offline & Privat**: Notificările sunt procesate exclusiv pe procesorul telefonului tău.

---

## 🌟 Funcționalități principale

1. **Sincronizare în timp real**: Adăugare automată la fiecare plată POS prin notificări de sistem.
2. **Import PDF & CSV**: Extragere automată a extraselor de cont cu eliminarea încasărilor/salariilor și curățarea textului.
3. **Precizie financiară absolută (Bani/Cenți)**: Fără erori de rotunjire specifice calculelor în virgulă mobilă (`double`).
4. **Sistem de învățare a categoriilor (Learning System)**: Memorează automat dacă asociezi un comerciant cu o anumită categorie.
5. **Căutare & Editare completă**: Găsește rapid orice plată și modifică suma, descrierea sau categoria oricând.
6. **Export & Backup CSV**: Salvează datele în fișier CSV universal compatibil cu Excel și Google Sheets.
