const CATEGORIES = [
  { name: 'Mâncare', icon: '♨', color: '#78a67b', pale: '#eaf3e9' },
  { name: 'Transport', icon: '↗', color: '#8a83be', pale: '#efedf8' },
  { name: 'Locuință', icon: '⌂', color: '#d29165', pale: '#fbefe5' },
  { name: 'Cumpărături', icon: '◇', color: '#6f9bab', pale: '#e8f1f3' },
  { name: 'Sănătate', icon: '✚', color: '#cf7977', pale: '#f8eaea' },
  { name: 'Timp liber', icon: '✳', color: '#c0a05e', pale: '#f6f1e4' },
  { name: 'Altele', icon: '•', color: '#92988e', pale: '#f0f1ed' },
];

const STORAGE_KEY = 'bani-pe-scurt-expenses-v1';
const LEARNED_KEY = 'bani-pe-scurt-learned-v1';

const formatter = new Intl.NumberFormat('ro-RO', { style: 'currency', currency: 'RON' });
const dateFormatter = new Intl.DateTimeFormat('ro-RO', { day: 'numeric', month: 'short', year: 'numeric' });
const monthFormatter = new Intl.DateTimeFormat('ro-RO', { month: 'long', year: 'numeric' });

let expenses = readExpenses();
let learnedCategories = readLearnedCategories();
let selectedMonth = new Date();
let activeCategoryFilter = '';
let searchQuery = '';
let viewMode = 'all'; // 'all', 'real', 'demo'
let isAllExpanded = false;
const expandedRowIds = new Set();
let pendingDrafts = [];
let toastTimer;

const $ = (selector) => document.querySelector(selector);
const monthKey = (date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
const categoryInfo = (name) => CATEGORIES.find((item) => item.name === name) || CATEGORIES.at(-1);

function isDemoExpense(expense) {
  return Boolean(expense.isDemo || (expense.id && String(expense.id).startsWith('DEMO-')));
}

function readExpenses() {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]');
    return Array.isArray(saved)
      ? saved.filter((item) => item && item.id && item.date && Number.isFinite(Number(item.amount)))
      : [];
  } catch {
    return [];
  }
}

function saveExpenses() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(expenses));
}

function readLearnedCategories() {
  try {
    return JSON.parse(localStorage.getItem(LEARNED_KEY) || '{}');
  } catch {
    return {};
  }
}

function learnCategory(description, category) {
  if (!description || !category) return;
  const key = extractMerchantKey(description);
  if (key && key.length >= 3) {
    learnedCategories[key] = category;
    try {
      localStorage.setItem(LEARNED_KEY, JSON.stringify(learnedCategories));
    } catch {}
  }
}

function normalize(value) {
  return String(value == null ? '' : value)
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toUpperCase()
    .replace(/\s+/g, ' ')
    .trim();
}

function extractMerchantKey(description) {
  let clean = normalize(description);
  clean = clean.replace(/\b(SC|SRL|SA|PFA|RO|STR|BUCURESTI|CLUJ|IASI|TIMISOARA)\b/g, '').trim();
  const words = clean.split(/\s+/);
  if (words.length >= 2 && words[0].length + words[1].length < 20) {
    return `${words[0]} ${words[1]}`.trim();
  }
  return words[0] || clean;
}

function suggestCategory(description) {
  const norm = normalize(description);

  // Check learned preferences
  for (const [key, cat] of Object.entries(learnedCategories)) {
    if (norm.includes(key)) return cat;
  }

  const containsAny = (text, terms) => terms.some((t) => text.includes(t));

  if (containsAny(norm, ['LIDL', 'KAUFLAND', 'CARREFOUR', 'AUCHAN', 'MEGA IMAGE', 'PROFI', 'PENNY', 'SELGROS',
    'SUPERMARKET', 'MARKET', 'GROCERY', 'RESTAURANT', 'CANTINA', 'PIZZA', 'COFFEE', 'CAFE', 'PATISERIE',
    'BRUTARIE', 'FOOD', 'GLOVO', 'TAZZ', 'BOLT FOOD', 'MCDONALD', 'KFC', 'SUBWAY', 'STARBUCKS', '5TOGO'])) {
    return 'Mâncare';
  }
  if (containsAny(norm, ['BOLT', 'UBER', 'TAXI', 'MOL ', 'OMV', 'PETROM', 'ROMPETROL', 'LUKOIL', 'SHELL', 'SOCAR',
    'PARKING', 'PARCARE', 'CTP', 'STB', 'METROREX', 'CFR', 'TRAIN', 'TREN', 'AUTOBUZ', 'TRAMVAI', 'WIZZ', 'RYANAIR', 'TAROM'])) {
    return 'Transport';
  }
  if (containsAny(norm, ['E.ON', 'EON ', 'ENGIE', 'ELECTRICA', 'ENEL', 'HIDROELECTRICA', 'DELGAZ', 'PPC ', 'NOVA POWER',
    'APA CANAL', 'AQUA', 'DIGI', 'RCS', 'RDS', 'ORANGE', 'VODAFONE', 'TELEKOM', 'CHIRIE', 'RENT', 'GAZ', 'CURENT', 'INTRETINERE', 'ASOCIATIE'])) {
    return 'Locuință';
  }
  if (containsAny(norm, ['FARMACIA', 'FARMACII', 'DR MAX', 'HELP NET', 'CATENA', 'SENSI', 'DONA', 'MEDLIFE', 'REGINA MARIA',
    'SANADOR', 'SYNEVO', 'BIOCLINICA', 'CLINICA', 'SPITAL', 'DENT', 'STOMATOLOG', 'PHARM', 'MEDIC', 'ANALIZE', 'OPTIK', 'LENS'])) {
    return 'Sănătate';
  }
  if (containsAny(norm, ['NETFLIX', 'SPOTIFY', 'HBO', 'MAX', 'DISNEY', 'YOUTUBE', 'CINEMA', 'CINEMACITY', 'TEATRU',
    'CONCERT', 'MUSEUM', 'MUZEU', 'STEAM', 'PLAYSTATION', 'XBOX', 'NINTENDO', 'BOOKING', 'AIRBNB', 'HOTEL',
    'PENSIUNE', 'TICKET', 'BILET', 'EVENTIM', 'IABILET', 'WORLD CLASS', 'FITNESS', 'SALA'])) {
    return 'Timp liber';
  }
  if (containsAny(norm, ['EMAG', 'CARTURESTI', 'CĂRTUREȘTI', 'ZARA', 'H&M', 'HM ', 'DECATHLON', 'IKEA', 'JYSK', 'DEDEMAN', 'LEROY', 'BRICO', 'HORNBACH',
    'DEICHMANN', 'DM ', 'DOUGLAS', 'SEPHORA', 'NOTINO', 'FASHION', 'SHOP', 'MAGAZIN', 'RETAIL', 'PEPCO', 'SINSAY', 'RESERVED', 'ALINTEX', 'ALTEX', 'FLANCO'])) {
    return 'Cumpărături';
  }

  return 'Altele';
}

function safeText(value) {
  return String(value == null ? '' : value).replace(/[&<>"']/g, (character) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  })[character]);
}

function render() {
  const key = monthKey(selectedMonth);

  // Filter by month
  let monthExpenses = expenses.filter((expense) => expense.date && expense.date.startsWith(key));

  // Filter by viewMode: all, real, demo
  if (viewMode === 'real') {
    monthExpenses = monthExpenses.filter((e) => !isDemoExpense(e));
  } else if (viewMode === 'demo') {
    monthExpenses = monthExpenses.filter((e) => isDemoExpense(e));
  }

  // Calculate total in exact integer bani
  const totalBani = monthExpenses.reduce((sum, expense) => sum + Math.round(Number(expense.amount) * 100), 0);
  const total = totalBani / 100;

  const totalsByCategory = new Map();
  monthExpenses.forEach((expense) => {
    const currentBani = totalsByCategory.get(expense.category) || 0;
    totalsByCategory.set(expense.category, currentBani + Math.round(Number(expense.amount) * 100));
  });

  const rankedCategories = [...totalsByCategory.entries()]
    .map(([cat, bani]) => [cat, bani / 100])
    .sort((a, b) => b[1] - a[1]);

  $('#month-title').textContent = monthFormatter.format(selectedMonth).replace(/^./, (letter) => letter.toLocaleUpperCase('ro-RO'));
  $('#total-value').textContent = formatter.format(total);
  $('#total-note').textContent = monthExpenses.length ? 'În luna selectată' : 'Nicio cheltuială';
  $('#count-value').textContent = String(monthExpenses.length);
  $('#count-note').textContent = monthExpenses.length === 1 ? '1 înregistrare în luna aceasta' : `${monthExpenses.length} înregistrări în luna aceasta`;
  $('#top-category').textContent = rankedCategories.length ? rankedCategories[0][0] : '—';
  $('#top-category-note').textContent = rankedCategories.length
    ? `${formatter.format(rankedCategories[0][1])} din total`
    : 'Adaugă o cheltuială ca să începi';

  updateDemoStatus();
  renderCategories(rankedCategories, total);
  renderExpenses(monthExpenses);
}

function updateDemoStatus() {
  const totalDemoCount = expenses.filter((e) => isDemoExpense(e)).length;
  const demoBanner = $('#demo-banner');
  const demoContainer = $('#demo-actions-container');

  if (totalDemoCount > 0) {
    demoBanner.style.display = 'flex';
    $('#demo-banner-text').innerHTML = `Ai <b>${totalDemoCount}</b> cheltuieli demonstrative active în cont. Poți alege să vezi doar datele reale sau să le ștergi definitiv:`;
    demoContainer.innerHTML = `<button class="subtle-button danger-subtle" id="clear-demo-header-btn" title="Șterge definitiv toate cele ${totalDemoCount} cheltuieli demonstrative">🗑️ Șterge demo (${totalDemoCount})</button>`;
    $('#clear-demo-header-btn').addEventListener('click', clearDemoData);
  } else {
    demoBanner.style.display = 'none';
    demoContainer.innerHTML = `<button class="subtle-button" id="demo-btn" title="Adaugă câteva cheltuieli exemplu">Exemple demo</button>`;
    $('#demo-btn').addEventListener('click', loadDemoData);
  }

  // Update view mode tabs
  document.querySelectorAll('.view-mode-tab').forEach((tab) => {
    tab.classList.toggle('active', tab.dataset.mode === viewMode);
  });
}

function clearDemoData() {
  const beforeCount = expenses.length;
  expenses = expenses.filter((e) => !isDemoExpense(e));
  const removed = beforeCount - expenses.length;
  saveExpenses();
  render();
  showToast(`Au fost șterse toate cele ${removed} cheltuieli demonstrative. Datele tale reale sunt intacte!`);
}

function renderCategories(rankedCategories, total) {
  const list = $('#category-breakdown');
  const empty = $('#category-empty');
  if (!rankedCategories.length) {
    list.innerHTML = '';
    empty.classList.add('visible');
    return;
  }
  empty.classList.remove('visible');
  list.innerHTML = rankedCategories.map(([name, value]) => {
    const category = categoryInfo(name);
    const percent = total ? Math.round((value / total) * 100) : 0;
    return `
      <div class="category-row">
        <span class="category-icon" style="background:${category.pale};color:${category.color}">${category.icon}</span>
        <div class="category-info">
          <div class="category-line">
            <span class="category-name">${safeText(name)}</span>
            <span class="category-percent">${percent}%</span>
          </div>
          <div class="category-track">
            <div class="category-fill" style="width:${percent}%;background:${category.color}"></div>
          </div>
        </div>
        <span class="category-sum">${formatter.format(value)}</span>
      </div>`;
  }).join('');
}

function renderExpenses(monthExpenses) {
  const list = $('#expense-list');
  let filtered = monthExpenses;

  if (activeCategoryFilter) {
    filtered = filtered.filter((expense) => expense.category === activeCategoryFilter);
  }

  if (searchQuery) {
    const q = searchQuery.toLowerCase();
    filtered = filtered.filter(
      (expense) =>
        expense.description.toLowerCase().includes(q) ||
        expense.category.toLowerCase().includes(q)
    );
  }

  const sorted = [...filtered].sort((a, b) => b.date.localeCompare(a.date) || (b.createdAt || 0) - (a.createdAt || 0));
  const isEmpty = sorted.length === 0;

  $('#list-empty').classList.toggle('visible', isEmpty);
  $('.table-wrap').style.display = isEmpty ? 'none' : 'block';
  $('#table-footer').style.display = isEmpty ? 'none' : 'flex';
  $('#visible-count').textContent = `${sorted.length} ${sorted.length === 1 ? 'înregistrare' : 'înregistrări'}`;
  $('#clear-filter').innerHTML = `${activeCategoryFilter ? safeText(activeCategoryFilter) : 'Toate'} <span>⌄</span>`;

  const table = $('#transactions-table');
  table.classList.toggle('table-expanded-all', isAllExpanded);

  list.innerHTML = sorted.map((expense) => {
    const category = categoryInfo(expense.category);
    const safeDate = new Date(`${expense.date}T12:00:00`);
    const isExpanded = isAllExpanded || expandedRowIds.has(expense.id);
    const isDemo = isDemoExpense(expense);
    const isLongDesc = expense.description && expense.description.length > 32;

    return `
      <tr class="expense-row ${isExpanded ? 'expanded' : ''}" data-id="${safeText(expense.id)}">
        <td class="col-desc">
          <div class="expense-desc">
            <span class="expense-mark" style="background:${category.pale};color:${category.color}">${category.icon}</span>
            <div class="expense-text-wrap">
              <span class="expense-title" data-toggle-desc="${safeText(expense.id)}" title="${safeText(expense.description)} (Click pentru extindere/restrângere)">
                ${safeText(expense.description)}
              </span>
              ${isLongDesc ? `<button type="button" class="expand-pill" data-toggle-desc="${safeText(expense.id)}" title="Extinde/Restrânge descrierea">${isExpanded ? '[-] restrânge' : '[+]'}</button>` : ''}
              ${isDemo ? '<span class="badge-demo-tag">DEMO</span>' : ''}
            </div>
          </div>
        </td>
        <td class="col-cat"><span class="table-category">${safeText(expense.category)}</span></td>
        <td class="col-date">${dateFormatter.format(safeDate)}</td>
        <td class="col-amount amount-cell">${formatter.format(Number(expense.amount))}</td>
        <td class="col-actions" style="text-align:right;">
          <button class="action-btn edit-button" data-edit="${safeText(expense.id)}" title="Editează" aria-label="Editează">✎</button>
          <button class="action-btn delete-button" data-delete="${safeText(expense.id)}" title="Șterge" aria-label="Șterge">×</button>
        </td>
      </tr>`;
  }).join('');
}

function setMonth(offset) {
  selectedMonth = new Date(selectedMonth.getFullYear(), selectedMonth.getMonth() + offset, 1);
  activeCategoryFilter = '';
  render();
}

function openAddDialog() {
  $('#expense-id').value = '';
  $('#dialog-eyebrow').textContent = 'O NOUĂ ÎNREGISTRARE';
  $('#dialog-title').textContent = 'Adaugă cheltuială';
  $('#submit-btn').innerHTML = 'Salvează cheltuiala <span>→</span>';
  $('#description').value = '';
  $('#amount').value = '';
  $('#category').value = CATEGORIES[0].name;

  const now = new Date();
  $('#date').value = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  $('#expense-dialog').showModal();
  setTimeout(() => $('#description').focus(), 50);
}

function openEditDialog(expenseId) {
  const expense = expenses.find((e) => e.id === expenseId);
  if (!expense) return;

  $('#expense-id').value = expense.id;
  $('#dialog-eyebrow').textContent = 'MODIFICĂ ÎNREGISTRAREA';
  $('#dialog-title').textContent = 'Editează cheltuiala';
  $('#submit-btn').innerHTML = 'Salvează modificările <span>✓</span>';
  $('#description').value = expense.description;
  $('#amount').value = expense.amount;
  $('#category').value = expense.category;
  $('#date').value = expense.date;

  $('#expense-dialog').showModal();
  setTimeout(() => $('#description').focus(), 50);
}

function showToast(message) {
  const toast = $('#toast');
  toast.textContent = message;
  toast.classList.add('visible');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove('visible'), 3200);
}

function exportCsv() {
  // Export real data or all based on viewMode
  let toExport = expenses;
  if (viewMode === 'real') {
    toExport = expenses.filter((e) => !isDemoExpense(e));
  }
  if (!toExport.length) {
    showToast('Nu există cheltuieli de exportat.');
    return;
  }
  let csvContent = '\uFEFFData,Descriere,Categorie,Suma\n';
  toExport.forEach((e) => {
    const safeDesc = String(e.description).replace(/"/g, '""');
    csvContent += `${e.date},"${safeDesc}",${e.category},${Number(e.amount).toFixed(2)}\n`;
  });

  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `bani-pe-scurt-cheltuieli-${monthKey(new Date())}.csv`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
  showToast('Fișierul CSV a fost descărcat.');
}

// ----------------------------------------------------
// PDF & CSV STATEMENT IMPORT LOGIC
// ----------------------------------------------------

async function importFile(file) {
  if (!file) return;
  const fileName = file.name || '';
  const lowerName = fileName.toLowerCase();

  showToast(`Se citește ${fileName}…`);

  try {
    if (lowerName.endsWith('.pdf') || file.type === 'application/pdf') {
      await parsePdfStatement(file);
    } else if (lowerName.endsWith('.csv') || file.type.includes('csv') || file.type.includes('comma-separated')) {
      await parseCsvStatement(file);
    } else if (lowerName.endsWith('.json')) {
      await parseJsonBackup(file);
    } else {
      await parseCsvStatement(file);
    }
  } catch (err) {
    console.error(err);
    showToast(`Eroare la import: ${err.message || 'Format nesuportat'}`);
  }
}

async function parsePdfStatement(file) {
  if (typeof pdfjsLib === 'undefined') {
    throw new Error('Biblioteca PDF.js nu este încărcată.');
  }

  pdfjsLib.GlobalWorkerOptions.workerSrc = './pdf.worker.min.js';

  const buffer = await file.arrayBuffer();
  const loadingTask = pdfjsLib.getDocument({
    data: buffer,
    disableWorker: true,
  });

  const pdf = await loadingTask.promise;
  const allLines = [];

  for (let pageNum = 1; pageNum <= pdf.numPages; pageNum++) {
    const page = await pdf.getPage(pageNum);
    const content = await page.getTextContent();
    const pageLines = extractLinesFromPdfPage(content.items);
    allLines.push(...pageLines);
  }

  if (allLines.length === 0) {
    throw new Error('Nu s-a putut citi text din PDF. Asigură-te că este extrasul original din YOU BRD.');
  }

  const result = buildTransactionsFromPdfLines(allLines);
  handleParsedResult(file.name, result);
}

function extractLinesFromPdfPage(items) {
  if (!items || !items.length) return [];
  const valid = items.filter((it) => it.str && it.str.trim().length > 0);
  const lineBuckets = [];

  valid.forEach((item) => {
    const x = item.transform[4];
    const y = item.transform[5];
    const text = item.str;

    let bucket = lineBuckets.find((b) => Math.abs(b.y - y) < 3.5);
    if (!bucket) {
      bucket = { y, items: [] };
      lineBuckets.push(bucket);
    }
    bucket.items.push({ text, x, y });
  });

  lineBuckets.sort((a, b) => b.y - a.y);
  return lineBuckets.map((bucket) => processBrdLine(bucket.items)).filter(Boolean);
}

function processBrdLine(lineItems) {
  lineItems.sort((a, b) => a.x - b.x);

  let dateStr = '';
  let descStr = '';
  let debitStr = '';
  let creditStr = '';
  let valueDateStr = '';

  lineItems.forEach((it) => {
    const x = it.x;
    const txt = it.text;
    if (x < 80) dateStr += txt;
    else if (x < 385) descStr += ` ${txt}`;
    else if (x < 470) debitStr += txt;
    else if (x < 520) creditStr += txt;
    else valueDateStr += txt;
  });

  const DATE_REGEX = /\b(\d{2})[/.](\d{2})[/.](\d{4})\b/;
  const AMOUNT_REGEX = /\b\d[\d.]*,\d{2}\b/;

  const dateMatch = dateStr.match(DATE_REGEX);
  const debitMatch = debitStr.replace(/\s+/g, '').match(AMOUNT_REGEX);
  const creditMatch = creditStr.replace(/\s+/g, '').match(AMOUNT_REGEX);

  const cleanDesc = descStr.replace(/\s+/g, ' ').trim();
  const dateIso = dateMatch ? `${dateMatch[3]}-${dateMatch[2]}-${dateMatch[1]}` : null;

  if (!dateIso && !cleanDesc && !debitMatch && !creditMatch) return null;

  return {
    date: dateIso,
    description: cleanDesc,
    debit: debitMatch ? debitMatch[0] : null,
    credit: creditMatch ? creditMatch[0] : null,
  };
}

function buildTransactionsFromPdfLines(lines) {
  const result = {
    expenses: [],
    incomeCount: 0,
    skippedCount: 0,
  };

  const occurrences = new Map();
  let currentTx = null;

  lines.forEach((line) => {
    if (line.date) {
      if (currentTx) addPdfTx(currentTx, result, occurrences);
      currentTx = {
        date: line.date,
        debit: line.debit,
        credit: line.credit,
        reference: '',
        descLines: line.description ? [line.description] : [],
      };
    } else if (currentTx) {
      if (line.debit && !currentTx.debit) currentTx.debit = line.debit;
      if (line.credit && !currentTx.credit) currentTx.credit = line.credit;
      if (line.description) currentTx.descLines.push(line.description);
    }
  });

  if (currentTx) addPdfTx(currentTx, result, occurrences);
  return result;
}

function addPdfTx(tx, result, occurrences) {
  const hasDebit = Boolean(tx.debit);
  const hasCredit = Boolean(tx.credit);

  if (!hasDebit && !hasCredit) { result.skippedCount++; return; }
  if (hasDebit && hasCredit) { result.skippedCount++; return; }
  if (hasCredit) { result.incomeCount++; return; }

  const amount = parseAmount(tx.debit);
  if (amount <= 0) { result.skippedCount++; return; }

  const cleanLines = [];
  tx.descLines.forEach((raw) => {
    let norm = normalize(raw);
    if (!norm) return;
    if (norm.startsWith('CARD NR') || norm.startsWith('CARD TERMINALE')) return;
    if (/^(OPH?|NC)\s*[A-Z0-9/]+/.test(norm)) {
      const match = norm.match(/\b(?:OPH?|NC)\s*([A-Z0-9/]+)/);
      if (match) tx.reference = match[1];
      return;
    }
    if (/RO\d{20,}/.test(norm)) return;
    if (['AUTOMAT', 'MOBILE OMNICHANNEL', 'UTILIZARE POS COMERCIANT ALTE BC', 'UTILIZARE POS COMERCIANT BRD', 'CARD/TERMINALE', 'PLATA CU CARDUL', 'PLATA POS'].includes(norm)) return;
    cleanLines.push(raw.trim());
  });

  let desc = cleanLines.join(' · ').replace(/\s+/g, ' ').trim();
  if (!desc) desc = 'Cheltuială BRD';

  const category = suggestCategory(desc);

  let id;
  if (tx.reference) {
    id = `BRD|${tx.reference}`;
  } else {
    const baseKey = `${tx.date}|${amount.toFixed(2)}|${normalize(desc)}`;
    const count = (occurrences.get(baseKey) || 0) + 1;
    occurrences.set(baseKey, count);
    id = `${baseKey}${count > 1 ? '#' + count : ''}`;
  }

  result.expenses.push({
    id,
    date: tx.date,
    description: desc,
    amount,
    category,
    createdAt: Date.now(),
  });
}

function parseAmount(val) {
  if (!val) return 0;
  const clean = val.replace(/\./g, '').replace(',', '.').replace(/[^0-9.]/g, '');
  const parsed = parseFloat(clean);
  return Number.isFinite(parsed) ? Math.round(parsed * 100) / 100 : 0;
}

async function parseCsvStatement(file) {
  const text = await file.text();
  const lines = text.split(/\r?\n/).filter((l) => l.trim().length > 0);
  if (lines.length < 2) throw new Error('Fișierul CSV este gol sau incomplet.');

  const headerLine = lines[0].replace(/^\uFEFF/, '');
  const delimiter = headerLine.includes(';') ? ';' : ',';
  const headers = headerLine.split(delimiter).map((h) => normalize(h));

  let dateIdx = -1, descIdx = -1, amountIdx = -1, catIdx = -1, debitIdx = -1, creditIdx = -1;
  headers.forEach((h, i) => {
    if (h.includes('DATA') || h.includes('DATE')) dateIdx = i;
    else if (h.includes('DESCRI') || h.includes('DETALII') || h.includes('COMERCIANT')) descIdx = i;
    else if (h.includes('CATEGOR')) catIdx = i;
    else if (h.includes('DEBIT')) debitIdx = i;
    else if (h.includes('CREDIT') || h.includes('INCASAR')) creditIdx = i;
    else if (h.includes('SUMA') || h.includes('AMOUNT') || h.includes('VALOARE')) amountIdx = i;
  });

  if (dateIdx === -1) dateIdx = 0;
  if (descIdx === -1) descIdx = 1;
  if (amountIdx === -1 && debitIdx === -1) amountIdx = 2;

  const result = {
    expenses: [],
    incomeCount: 0,
    skippedCount: 0,
  };
  const occurrences = new Map();

  for (let i = 1; i < lines.length; i++) {
    const line = lines[i].trim();
    if (!line) continue;
    const parts = parseCsvTokens(line, delimiter);

    const rawDate = (parts[dateIdx] || '').trim();
    const rawDesc = (parts[descIdx] || 'Cheltuială').trim();
    const rawCat = catIdx >= 0 ? (parts[catIdx] || '').trim() : '';

    let isoDate = null;
    if (/^\d{4}-\d{2}-\d{2}$/.test(rawDate)) {
      isoDate = rawDate;
    } else if (/^\d{2}[/.]\d{2}[/.]\d{4}$/.test(rawDate)) {
      const p = rawDate.split(/[/.]/);
      isoDate = `${p[2]}-${p[1].padStart(2, '0')}-${p[0].padStart(2, '0')}`;
    }
    if (!isoDate) continue;

    let amount = 0;
    if (debitIdx >= 0 && parts[debitIdx] && parts[debitIdx].trim()) {
      amount = parseAmount(parts[debitIdx]);
    } else if (creditIdx >= 0 && parts[creditIdx] && parts[creditIdx].trim()) {
      result.incomeCount++;
      continue;
    } else if (amountIdx >= 0 && parts[amountIdx]) {
      const rawAmt = parts[amountIdx].trim();
      if (rawAmt.startsWith('-')) {
        amount = parseAmount(rawAmt.slice(1));
      } else if (rawAmt.startsWith('+')) {
        result.incomeCount++;
        continue;
      } else {
        amount = parseAmount(rawAmt);
      }
    }

    if (amount <= 0) {
      result.skippedCount++;
      continue;
    }

    const category = (rawCat && CATEGORIES.some((c) => c.name === rawCat))
      ? rawCat
      : suggestCategory(rawDesc);

    const baseKey = `${isoDate}|${amount.toFixed(2)}|${normalize(rawDesc)}`;
    const count = (occurrences.get(baseKey) || 0) + 1;
    occurrences.set(baseKey, count);
    const id = `${baseKey}${count > 1 ? '#' + count : ''}`;

    result.expenses.push({
      id,
      date: isoDate,
      description: rawDesc,
      amount,
      category,
      createdAt: Date.now(),
    });
  }

  handleParsedResult(file.name, result);
}

async function parseJsonBackup(file) {
  const text = await file.text();
  const list = JSON.parse(text);
  if (!Array.isArray(list)) throw new Error('Fișier JSON invalid.');

  const result = {
    expenses: list.map((item) => ({
      id: item.id || `JSON-${Date.now()}-${Math.random().toString(16).slice(2)}`,
      date: String(item.date || new Date().toISOString().slice(0, 10)),
      description: String(item.description || 'Cheltuială').trim(),
      amount: Math.round(Number(item.amount) * 100) / 100,
      category: item.category || 'Altele',
      createdAt: item.createdAt || Date.now(),
    })).filter((e) => e.amount > 0),
    incomeCount: 0,
    skippedCount: 0,
  };
  handleParsedResult(file.name, result);
}

function parseCsvTokens(line, delimiter) {
  const result = [];
  let token = '';
  let inQuotes = false;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (c === '"') {
      inQuotes = !inQuotes;
    } else if (c === delimiter && !inQuotes) {
      result.push(token.trim().replace(/^"|"$/g, ''));
      token = '';
    } else {
      token += c;
    }
  }
  result.push(token.trim().replace(/^"|"$/g, ''));
  return result;
}

function handleParsedResult(fileName, result) {
  const existingIds = new Set(expenses.map((e) => e.id));
  const newTransactions = [];
  let duplicatesCount = 0;

  result.expenses.forEach((item) => {
    if (existingIds.has(item.id)) {
      duplicatesCount++;
    } else {
      newTransactions.push(item);
    }
  });

  if (!newTransactions.length) {
    showToast(
      duplicatesCount > 0
        ? `Toate cele ${duplicatesCount} tranzacții din fișier au fost deja importate anterior.`
        : 'Nu s-a găsit nicio cheltuială nouă în fișier.'
    );
    return;
  }

  pendingDrafts = newTransactions;
  openReviewDialog(fileName, newTransactions, result.incomeCount, duplicatesCount);
}

function openReviewDialog(fileName, drafts, incomeCount, duplicatesCount) {
  $('#review-title').textContent = fileName;
  let status = `Am găsit <b>${drafts.length}</b> ${drafts.length === 1 ? 'cheltuială nouă' : 'cheltuieli noi'}`;
  if (incomeCount > 0) status += ` · <b>${incomeCount}</b> încasări omise`;
  if (duplicatesCount > 0) status += ` · <b>${duplicatesCount}</b> deja importate`;
  status += '. Poți ajusta categoriile propuse înainte de salvare:';
  $('#review-status').innerHTML = status;

  const list = $('#review-list');
  list.innerHTML = drafts.map((item, idx) => `
    <tr>
      <td>${safeText(item.date)}</td>
      <td><strong>${safeText(item.description)}</strong></td>
      <td style="font-weight:700">${formatter.format(item.amount)}</td>
      <td>
        <select data-draft-idx="${idx}">
          ${CATEGORIES.map((c) => `<option value="${c.name}" ${c.name === item.category ? 'selected' : ''}>${c.name}</option>`).join('')}
        </select>
      </td>
    </tr>
  `).join('');

  $('#review-dialog').showModal();
}

function confirmImport() {
  if (!pendingDrafts.length) return;

  const selects = document.querySelectorAll('#review-list select[data-draft-idx]');
  selects.forEach((sel) => {
    const idx = parseInt(sel.dataset.draftIdx, 10);
    if (pendingDrafts[idx]) {
      const chosenCat = sel.value;
      if (chosenCat !== pendingDrafts[idx].category) {
        learnCategory(pendingDrafts[idx].description, chosenCat);
      }
      pendingDrafts[idx].category = chosenCat;
    }
  });

  const addedCount = pendingDrafts.length;
  expenses.push(...pendingDrafts);
  saveExpenses();

  if (pendingDrafts[0] && pendingDrafts[0].date) {
    selectedMonth = new Date(`${pendingDrafts[0].date}T12:00:00`);
  }

  pendingDrafts = [];
  $('#review-dialog').close();
  render();
  showToast(`Au fost adăugate cu succes ${addedCount} cheltuieli!`);
}

function loadDemoData() {
  const currentKey = monthKey(selectedMonth);
  const sampleItems = [
    { desc: 'Mega Image Mihalache', cat: 'Mâncare', amt: 84.50, day: '02' },
    { desc: 'Kaufland Barbu Văcărescu', cat: 'Mâncare', amt: 215.30, day: '04' },
    { desc: 'Bolt Ride București', cat: 'Transport', amt: 28.50, day: '05' },
    { desc: 'Abonament Metrorex Lunar', cat: 'Transport', amt: 80.00, day: '01' },
    { desc: 'Enel Energie Electrică', cat: 'Locuință', amt: 145.20, day: '08' },
    { desc: 'Abonament Digi Net & TV', cat: 'Locuință', amt: 40.00, day: '10' },
    { desc: 'Farmacia Tei', cat: 'Sănătate', amt: 62.70, day: '12' },
    { desc: 'Cărturești Carusel', cat: 'Cumpărături', amt: 95.00, day: '14' },
    { desc: 'Bilete Cinema City', cat: 'Timp liber', amt: 64.00, day: '15' },
    { desc: 'Netflix Subscription', cat: 'Timp liber', amt: 59.99, day: '18' },
    { desc: 'Decathlon echipament', cat: 'Cumpărături', amt: 130.00, day: '20' },
    { desc: '5 To Go Cafea', cat: 'Mâncare', amt: 16.00, day: '22' },
  ];

  let added = 0;
  sampleItems.forEach((item) => {
    const date = `${currentKey}-${item.day}`;
    const id = `DEMO-${date}-${item.desc}`;
    if (!expenses.some((e) => e.id === id)) {
      expenses.push({
        id,
        description: item.desc,
        amount: item.amt,
        category: item.cat,
        date,
        isDemo: true,
        createdAt: Date.now(),
      });
      added++;
    }
  });

  saveExpenses();
  render();
  showToast(`S-au adăugat ${added} cheltuieli demonstrative. Le poți șterge oricând cu butonul „Șterge demo”.`);
}

function initTheme() {
  const toggleBtn = $('#theme-toggle');
  if (!toggleBtn) return;

  const updateButton = (isDark) => {
    toggleBtn.innerHTML = isDark ? '☀️ <span>Luminos</span>' : '🌙 <span>Întunecat</span>';
    toggleBtn.title = isDark ? 'Comută pe tema luminoasă' : 'Comută pe tema întunecată';
  };

  const isCurrentDark = document.documentElement.getAttribute('data-theme') === 'dark';
  updateButton(isCurrentDark);

  toggleBtn.addEventListener('click', () => {
    const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
    const nextTheme = isDark ? 'light' : 'dark';
    if (nextTheme === 'dark') {
      document.documentElement.setAttribute('data-theme', 'dark');
    } else {
      document.documentElement.removeAttribute('data-theme');
    }
    localStorage.setItem('bani-theme', nextTheme);
    updateButton(!isDark);
    showToast(nextTheme === 'dark' ? 'Tema întunecată a fost activată.' : 'Tema luminoasă a fost activată.');
  });
}

function init() {
  initTheme();
  $('#today-label').textContent = new Intl.DateTimeFormat('ro-RO', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  }).format(new Date());

  $('#category').innerHTML = CATEGORIES.map((item) => `<option value="${item.name}">${item.name}</option>`).join('');

  $('#prev-month').addEventListener('click', () => setMonth(-1));
  $('#next-month').addEventListener('click', () => setMonth(1));
  $('#this-month').addEventListener('click', () => {
    selectedMonth = new Date();
    activeCategoryFilter = '';
    searchQuery = '';
    if ($('#search-input')) $('#search-input').value = '';
    render();
  });

  $('#open-form').addEventListener('click', openAddDialog);
  $('#empty-add').addEventListener('click', openAddDialog);
  $('#close-dialog').addEventListener('click', () => $('#expense-dialog').close());
  $('#expense-dialog').addEventListener('click', (event) => {
    if (event.target === $('#expense-dialog')) $('#expense-dialog').close();
  });

  $('#expense-form').addEventListener('submit', (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const existingId = String(form.get('id') || '');
    const date = String(form.get('date'));
    const description = String(form.get('description')).trim();
    const amount = Math.round(Number(form.get('amount')) * 100) / 100;
    const category = String(form.get('category'));

    if (!description || !Number.isFinite(amount) || amount <= 0 || !date) return;

    if (existingId) {
      const idx = expenses.findIndex((e) => e.id === existingId);
      if (idx >= 0) {
        expenses[idx].description = description;
        expenses[idx].amount = amount;
        expenses[idx].category = category;
        expenses[idx].date = date;
        learnCategory(description, category);
        showToast('Cheltuiala a fost actualizată.');
      }
    } else {
      const id = crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
      expenses.push({
        id,
        description,
        amount,
        category,
        date,
        isDemo: false,
        createdAt: Date.now(),
      });
      selectedMonth = new Date(`${date}T12:00:00`);
      learnCategory(description, category);
      showToast('Cheltuiala a fost salvată.');
    }

    saveExpenses();
    event.currentTarget.reset();
    $('#expense-dialog').close();
    render();
  });

  // Handle transaction list clicks (expand/collapse, edit, delete)
  $('#expense-list').addEventListener('click', (event) => {
    // Description expand / collapse
    const toggleTarget = event.target.closest('[data-toggle-desc]');
    if (toggleTarget) {
      const expenseId = toggleTarget.dataset.toggleDesc;
      if (expandedRowIds.has(expenseId)) {
        expandedRowIds.delete(expenseId);
      } else {
        expandedRowIds.add(expenseId);
      }
      render();
      return;
    }

    // Edit button
    const editBtn = event.target.closest('[data-edit]');
    if (editBtn) {
      openEditDialog(editBtn.dataset.edit);
      return;
    }

    // Delete button
    const delBtn = event.target.closest('[data-delete]');
    if (delBtn) {
      const id = delBtn.dataset.delete;
      expenses = expenses.filter((expense) => expense.id !== id);
      expandedRowIds.delete(id);
      saveExpenses();
      render();
      showToast('Cheltuiala a fost ștearsă.');
    }
  });

  // Global expand all toggle
  $('#toggle-expand-all').addEventListener('click', () => {
    isAllExpanded = !isAllExpanded;
    $('#toggle-expand-all').textContent = isAllExpanded ? '⤡ Restrânge tot' : '⤢ Extinde tot';
    render();
  });

  $('#search-input').addEventListener('input', (event) => {
    searchQuery = event.target.value.trim();
    render();
  });

  $('#clear-filter').addEventListener('click', () => {
    activeCategoryFilter = '';
    searchQuery = '';
    $('#search-input').value = '';
    render();
  });

  $('#export-btn').addEventListener('click', exportCsv);

  const importFileInput = $('#import-file');
  $('#import-btn').addEventListener('click', () => importFileInput.click());
  importFileInput.addEventListener('change', (e) => {
    if (e.target.files && e.target.files[0]) {
      importFile(e.target.files[0]);
      e.target.value = '';
    }
  });

  // Clear demo buttons
  $('#banner-clear-demo').addEventListener('click', clearDemoData);

  // View mode tabs (All, Real, Demo)
  document.querySelectorAll('.view-mode-tab').forEach((tab) => {
    tab.addEventListener('click', (e) => {
      viewMode = e.currentTarget.dataset.mode;
      render();
    });
  });

  // Review Dialog listeners
  $('#confirm-import-btn').addEventListener('click', confirmImport);
  $('#cancel-review-btn').addEventListener('click', () => {
    pendingDrafts = [];
    $('#review-dialog').close();
  });
  $('#close-review').addEventListener('click', () => {
    pendingDrafts = [];
    $('#review-dialog').close();
  });

  // Drag & Drop anywhere on window
  const dragOverlay = $('#drag-overlay');
  let dragCounter = 0;

  window.addEventListener('dragenter', (e) => {
    e.preventDefault();
    dragCounter++;
    dragOverlay.classList.add('active');
  });

  window.addEventListener('dragleave', (e) => {
    e.preventDefault();
    dragCounter--;
    if (dragCounter <= 0) {
      dragCounter = 0;
      dragOverlay.classList.remove('active');
    }
  });

  window.addEventListener('dragover', (e) => {
    e.preventDefault();
  });

  window.addEventListener('drop', (e) => {
    e.preventDefault();
    dragCounter = 0;
    dragOverlay.classList.remove('active');
    if (e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0]) {
      importFile(e.dataTransfer.files[0]);
    }
  });

  render();
}

init();
