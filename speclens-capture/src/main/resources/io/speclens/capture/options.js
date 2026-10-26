// Reads the options of a custom combobox that has just been opened.
// Looks in the listbox the combobox points at first, then any visible listbox.
([sid, listboxId]) => {
  const norm = s => (s || '').replace(/\s+/g, ' ').trim();
  const visible = el => {
    const r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0;
  };
  let scope = null;
  if (listboxId) scope = document.getElementById(listboxId.split(/\s+/)[0]);
  if (!scope) {
    const combo = document.querySelector('[data-speclens-id="' + sid + '"]');
    const ref = combo && (combo.getAttribute('aria-controls') || combo.getAttribute('aria-owns'));
    if (ref) scope = document.getElementById(ref.split(/\s+/)[0]);
  }
  if (!scope) scope = [...document.querySelectorAll('[role=listbox]')].find(visible) || document;
  return [...scope.querySelectorAll('[role=option]')]
    .filter(o => scope !== document || visible(o))
    .map(o => ({
      value: o.getAttribute('data-value') || norm(o.textContent),
      label: norm(o.textContent),
      selected: o.getAttribute('aria-selected') === 'true'
    }))
    .filter(o => o.label);
}
