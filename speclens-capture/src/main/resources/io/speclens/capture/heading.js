// The most prominent visible heading in the frame, or null.
() => {
  const selectors = ['h1', '[role=heading][aria-level="1"]', 'h2', '[role=heading][aria-level="2"]', '.header-title', '[role=heading]'];
  for (const sel of selectors) {
    for (const el of document.querySelectorAll(sel)) {
      const r = el.getBoundingClientRect();
      const text = (el.innerText || '').replace(/\s+/g, ' ').trim();
      if (r.width > 0 && r.height > 0 && text) return text;
    }
  }
  return null;
}
