// Runs inside one frame. Finds interactive elements, resolves their labels and
// static attributes, and tags each one with data-speclens-id / data-speclens-crop
// so the Java side can find it again without relying on generated ids.
(prefix) => {
  const SELECTOR = [
    'input:not([type=hidden])', 'select', 'textarea', 'button',
    '[role=textbox]', '[role=searchbox]', '[role=combobox]', '[role=listbox]',
    '[role=spinbutton]', '[role=checkbox]', '[role=switch]', '[role=radio]',
    '[role=button]', '[contenteditable=""]', '[contenteditable=true]'
  ].join(',');
  const NATIVE_CONTROL = 'input:not([type=hidden]), select, textarea';
  const TEST_ID_ATTRS = ['data-test-id', 'data-testid', 'data-test', 'data-qa'];
  const MAX_LABEL = 80;
  const MAX_LABEL_DEPTH = 8;
  // Ids a framework generated rather than a developer wrote: these change between builds.
  const GENERATED_ID = /^(react-select|mui|ember|ext-gen|yui|ui-id|j_id|ctl\d|radix|headlessui|:r)/i;

  const norm = s => (s || '').replace(/\s+/g, ' ').trim();
  const cleanLabel = s => norm(s).replace(/^\*\s*/, '').replace(/\s*[*:]+\s*$/, '').trim();
  const attr = (el, name) => {
    const v = el.getAttribute(name);
    return v === null || v === '' ? null : v;
  };
  const intAttr = (el, name) => {
    const v = parseInt(el.getAttribute(name), 10);
    return Number.isFinite(v) && v >= 0 ? v : null;
  };

  const isVisible = el => {
    if (el.closest('[aria-hidden="true"]')) return false;
    if (el.checkVisibility && !el.checkVisibility({ checkOpacity: true, checkVisibilityCSS: true })) return false;
    const r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0;
  };

  function implicitRole(el) {
    const explicit = attr(el, 'role');
    const tag = el.tagName.toLowerCase();
    if (tag === 'input' && el.closest('[role=combobox]') && explicit === null) return 'combobox';
    if (explicit) return explicit;
    if (el.isContentEditable && tag !== 'input' && tag !== 'textarea') return 'textbox';
    if (tag === 'select') return el.multiple || el.size > 1 ? 'listbox' : 'combobox';
    if (tag === 'textarea') return 'textbox';
    if (tag === 'button') return 'button';
    if (tag !== 'input') return null;
    if (el.list) return 'combobox';
    switch ((el.type || 'text').toLowerCase()) {
      case 'checkbox': return 'checkbox';
      case 'radio': return 'radio';
      case 'number': return 'spinbutton';
      case 'range': return 'slider';
      case 'search': return 'searchbox';
      case 'submit': case 'button': case 'reset': case 'image': return 'button';
      default: return 'textbox';
    }
  }

  function kindOf(role) {
    switch (role) {
      case 'button': return 'ACTION';
      case 'checkbox': case 'switch': return 'TOGGLE';
      case 'combobox': case 'listbox': case 'radiogroup': return 'CHOICE';
      default: return 'INPUT';
    }
  }

  // Text of a label element without the text of controls nested inside it
  // (a wrapping label would otherwise include every <option> of its select).
  function labelText(labelEl) {
    const clone = labelEl.cloneNode(true);
    clone.querySelectorAll('select, textarea, input, button, [role=listbox]').forEach(n => n.remove());
    return clone.textContent;
  }

  function textOfIds(el, ids) {
    return ids.split(/\s+/)
      .map(id => el.ownerDocument.getElementById(id))
      .filter(Boolean)
      .map(n => n.textContent)
      .join(' ');
  }

  // Custom widgets (react-select, Pega autocompletes) render their placeholder as a
  // sibling element; that text is a hint, not the field's label.
  const PLACEHOLDER_CLASS = /placeholder/i;
  function placeholderElementOf(el) {
    const ids = attr(el, 'aria-describedby');
    if (!ids) return null;
    return ids.split(/\s+/)
      .map(id => el.ownerDocument.getElementById(id))
      .find(n => n && PLACEHOLDER_CLASS.test(n.className || '')) || null;
  }

  function isNotALabel(sib) {
    return PLACEHOLDER_CLASS.test(sib.className || '')
      || attr(sib, 'aria-live') !== null || attr(sib, 'role') === 'log' || attr(sib, 'aria-hidden') === 'true';
  }

  // Visible text of a sibling, minus any placeholder text nested inside it.
  function siblingText(sib) {
    if (!sib.querySelector('[class*=placeholder i]')) return sib.innerText || '';
    const clone = sib.cloneNode(true);
    clone.querySelectorAll('[class*=placeholder i]').forEach(n => n.remove());
    return clone.textContent;
  }

  // Text from a label-like element is a stronger signal than any nearby text.
  const LABEL_LIKE = 'label, dt, th, legend';
  function nearSource(el) {
    return el.matches(LABEL_LIKE) || el.querySelector(LABEL_LIKE) ? 'NEARBY_LABEL' : 'NEAREST_TEXT';
  }

  // Walk up the tree looking at previous siblings for a short piece of text.
  // Gives up as soon as it meets another control: that text belongs to it.
  function nearestText(el) {
    let node = el;
    for (let depth = 0; depth < MAX_LABEL_DEPTH && node && node !== el.ownerDocument.body; depth++) {
      for (let sib = node.previousElementSibling; sib; sib = sib.previousElementSibling) {
        if (sib.matches(SELECTOR) || sib.querySelector(SELECTOR)) return null;
        // Links and menus next to a field are navigation, not its label.
        if (sib.matches('a[href], nav, menu, [role=navigation], [role=menu]') || sib.querySelector('a[href]')) return null;
        if (isNotALabel(sib)) continue;
        const t = cleanLabel(siblingText(sib));
        if (t && t.length <= MAX_LABEL) return [t, nearSource(sib), sib];
      }
      node = node.parentElement;
    }
    return null;
  }

  // Returns [text, source, elementTheTextCameFrom] or [null, 'NONE', null].
  function labelOf(el, { ownText = false } = {}) {
    const lb = attr(el, 'aria-labelledby');
    if (lb) {
      const t = cleanLabel(textOfIds(el, lb));
      if (t) return [t, 'ARIA_LABELLEDBY', el.ownerDocument.getElementById(lb.split(/\s+/)[0])];
    }
    const al = cleanLabel(attr(el, 'aria-label'));
    if (al) return [al, 'ARIA_LABEL', null];
    if (el.labels) {
      for (const l of el.labels) {
        const t = cleanLabel(labelText(l));
        if (t) return [t, l.htmlFor && l.htmlFor === el.id ? 'LABEL_FOR' : 'WRAPPING_LABEL', l];
      }
    }
    if (ownText) {
      const t = norm(el.innerText || el.value || '');
      if (t) return [t, 'OWN_TEXT', null];
    }
    const legend = el.tagName === 'FIELDSET' && el.querySelector(':scope > legend');
    if (legend && cleanLabel(legend.textContent)) return [cleanLabel(legend.textContent), 'FIELDSET_LEGEND', legend];
    const title = cleanLabel(attr(el, 'title'));
    if (title) return [title, 'TITLE', null];
    const near = nearestText(el);
    if (near) return near;
    const phEl = placeholderElementOf(el);
    const ph = cleanLabel(attr(el, 'placeholder') || (phEl && phEl.textContent));
    if (ph) return [ph, 'PLACEHOLDER', null];
    return [null, 'NONE', null];
  }

  // Pega (and many design systems) mark required fields on the label rather
  // than the input: a "required" class, a required icon, or a trailing asterisk.
  function requiredOf(els, labelEl) {
    for (const el of els) {
      if (el.required) return 'required attribute';
      if (attr(el, 'aria-required') === 'true') return 'aria-required';
      const msg = attr(el, 'data-val-required');
      if (msg) return 'data-val-required: "' + msg + '"';
    }
    if (labelEl) {
      if (/required/i.test(labelEl.className || '')) return 'label class "' + labelEl.className.trim() + '"';
      if (labelEl.querySelector('[class*=equired]')) return 'required marker inside label';
      if (/(^\s*\*)|(\*\s*:?\s*$)/.test(labelEl.textContent || '')) return 'asterisk in label';
    }
    // Some apps put the marker right after the field: <input> <span class="required">*</span>.
    for (const el of els) {
      let sib = el.nextElementSibling;
      for (let i = 0; i < 2 && sib; i++, sib = sib.nextElementSibling) {
        if (/\brequired\b/i.test(sib.className || '') || norm(sib.textContent) === '*') return 'required marker next to field';
      }
    }
    return null;
  }

  // Validation rules declared in markup. ASP.NET unobtrusive validation writes
  // data-val-<rule>="message" plus data-val-<rule>-<param>="value".
  function validationsOf(els) {
    const rules = new Map();
    for (const el of els) {
      for (const { name, value } of el.attributes) {
        const m = /^data-val-([a-z]+)(?:-([a-z]+))?$/.exec(name);
        if (!m) continue;
        if (!rules.has(m[1])) rules.set(m[1], { rule: m[1], message: null, params: {} });
        if (m[2]) rules.get(m[1]).params[m[2]] = value;
        else rules.get(m[1]).message = value || null;
      }
    }
    return rules.size ? [...rules.values()] : null;
  }

  function testIdOf(el) {
    for (const a of TEST_ID_ATTRS) {
      const v = attr(el, a);
      if (v) return { attr: a, value: v };
    }
    return null;
  }

  function controlHintOf(el) {
    const ctl = attr(el, 'data-ctl') || attr(el.parentElement || el, 'data-ctl');
    if (!ctl) return null;
    try {
      const parsed = JSON.parse(ctl);
      return Array.isArray(parsed) ? parsed.join(',') : String(parsed);
    } catch (e) {
      return ctl;
    }
  }

  function boxOf(el) {
    const r = el.getBoundingClientRect();
    const w = el.ownerDocument.defaultView;
    return { x: r.x + w.scrollX, y: r.y + w.scrollY, width: r.width, height: r.height };
  }

  function optionsOf(el, tag) {
    if (tag === 'select') {
      return [...el.options].map(o => ({ value: o.value, label: norm(o.text), selected: o.selected }));
    }
    if (tag === 'input' && el.list) {
      return [...el.list.options].map(o => ({ value: o.value, label: norm(o.label || o.value), selected: false }));
    }
    if (attr(el, 'role') === 'listbox') {
      return [...el.querySelectorAll('[role=option]')].map(o => ({
        value: attr(o, 'data-value') || norm(o.textContent),
        label: norm(o.textContent),
        selected: attr(o, 'aria-selected') === 'true'
      }));
    }
    return null;
  }

  function valueOf(el, tag, role) {
    if (role === 'button') return null;
    if (role === 'checkbox' || role === 'switch') {
      const checked = tag === 'input' ? el.checked : attr(el, 'aria-checked') === 'true';
      return checked ? 'checked' : 'unchecked';
    }
    if (tag === 'input' && (el.type || '').toLowerCase() === 'password') return el.value ? '(masked)' : null;
    if (tag === 'select') {
      const sel = [...el.selectedOptions].map(o => norm(o.text)).filter(Boolean);
      return sel.length ? sel.join(', ') : null;
    }
    if (tag === 'input' || tag === 'textarea') return el.value === '' ? null : el.value;
    const t = norm(el.innerText);
    return t || null;
  }

  function snippet(el) {
    const html = el.outerHTML;
    return html.length > 400 ? html.slice(0, 400) + '…' : html;
  }

  let counter = 0;
  function tag(el, cropTarget) {
    const sid = prefix + '-' + (counter++);
    el.setAttribute('data-speclens-id', sid);
    cropTarget.setAttribute('data-speclens-crop', sid);
    return sid;
  }

  // A checkbox or radio that is visually hidden behind a styled label still counts,
  // and so does a custom combobox whose input is hidden while its box is shown
  // (react-select hides the input of a disabled select).
  function visibleTarget(el) {
    if (isVisible(el)) return el;
    if (el.labels) {
      for (const l of el.labels) if (isVisible(l)) return l;
    }
    if (implicitRole(el) === 'combobox' && !el.closest('[aria-hidden="true"]')) {
      let node = el.parentElement;
      for (let depth = 0; depth < 4 && node; depth++, node = node.parentElement) {
        if (node.querySelectorAll(NATIVE_CONTROL).length > 1) break;
        if (isVisible(node) && node.getBoundingClientRect().width >= 20) return node;
      }
    }
    return null;
  }

  // The element's id, if it looks written by a developer and is unique in the document.
  function stableIdOf(el) {
    const id = el.id;
    if (!id || !/^[A-Za-z][\w-]{2,39}$/.test(id) || GENERATED_ID.test(id)) return null;
    if ((id.match(/\d/g) || []).length > 2) return null;
    return el.ownerDocument.querySelectorAll('#' + CSS.escape(id)).length === 1 ? id : null;
  }

  // Text that opens a group's own container before its first option:
  // <div><label>Gender:</label><div><input type=radio>...</div>...</div>
  function leadingCaption(container) {
    for (const child of container.children) {
      if (child.matches(SELECTOR) || child.querySelector(SELECTOR)) return null;
      const t = cleanLabel(child.innerText || '');
      if (t && t.length <= MAX_LABEL) return [t, nearSource(child), child];
    }
    return null;
  }

  const results = [];
  const radioGroups = new Map();
  const doc = document;

  for (const el of doc.querySelectorAll(SELECTOR)) {
    const role = implicitRole(el);
    if (role === 'radio') {
      const key = el.closest('[role=radiogroup]') || (el.name ? 'name:' + el.name : el.parentElement);
      if (!radioGroups.has(key)) radioGroups.set(key, []);
      radioGroups.get(key).push(el);
      continue;
    }
    // Prefer the real control over a wrapper that carries the role.
    if (el.tagName !== 'INPUT' && el.tagName !== 'SELECT' && el.tagName !== 'TEXTAREA' && el.querySelector(NATIVE_CONTROL)) continue;
    const target = visibleTarget(el);
    if (!target) continue;

    const tagName = el.tagName.toLowerCase();
    const [label, labelSource, labelEl] = labelOf(el, { ownText: role === 'button' });
    const testId = testIdOf(el);
    const listboxId = attr(el, 'aria-controls') || attr(el, 'aria-owns');
    results.push({
      el,
      sid: tag(el, target),
      kind: kindOf(role),
      role,
      tag: tagName,
      inputType: tagName === 'input' ? (el.type || 'text').toLowerCase() : null,
      name: attr(el, 'name'),
      stableId: stableIdOf(el),
      testIdAttr: testId && testId.attr,
      testId: testId && testId.value,
      controlHint: controlHintOf(el),
      label,
      labelSource,
      required: requiredOf([el], labelEl),
      validations: validationsOf([el]),
      maxLength: intAttr(el, 'maxlength'),
      minLength: intAttr(el, 'minlength'),
      pattern: attr(el, 'pattern'),
      min: attr(el, 'min'),
      max: attr(el, 'max'),
      readOnly: !!el.readOnly || attr(el, 'aria-readonly') === 'true',
      disabled: !!el.disabled || attr(el, 'aria-disabled') === 'true' || !!el.closest('fieldset[disabled]'),
      value: valueOf(el, tagName, role),
      placeholder: attr(el, 'placeholder'),
      options: optionsOf(el, tagName),
      needsOpen: role === 'combobox' && tagName !== 'select' && !(tagName === 'input' && el.list),
      listboxId,
      box: boxOf(target),
      html: snippet(el)
    });
  }

  for (const [key, radios] of radioGroups) {
    const visibleRadios = radios.filter(r => visibleTarget(r));
    if (!visibleRadios.length) continue;
    let container = key instanceof Element && key.getAttribute('role') === 'radiogroup' ? key : null;
    if (!container) {
      container = visibleRadios[0].parentElement;
      while (container && !visibleRadios.every(r => container.contains(r))) container = container.parentElement;
      container = container || doc.body;
    }
    const fieldset = visibleRadios[0].closest('fieldset');
    let [label, labelSource, labelEl] = labelOf(container);
    if (labelSource === 'NONE' || labelSource === 'NEAREST_TEXT') {
      const caption = leadingCaption(container);
      if (caption) [label, labelSource, labelEl] = caption;
    }
    if (!label && fieldset) [label, labelSource, labelEl] = labelOf(fieldset);
    const first = visibleRadios[0];
    const options = visibleRadios.map(r => ({
      value: r.value || attr(r, 'data-value'),
      label: labelOf(r)[0],
      selected: r.tagName === 'INPUT' ? r.checked : attr(r, 'aria-checked') === 'true'
    }));
    const checked = options.find(o => o.selected);
    const testId = testIdOf(container) || testIdOf(first);
    results.push({
      el: container,
      sid: tag(container, isVisible(container) ? container : visibleTarget(first)),
      kind: 'CHOICE',
      role: 'radiogroup',
      tag: container.tagName.toLowerCase(),
      inputType: first.tagName === 'INPUT' ? 'radio' : null,
      name: attr(first, 'name'),
      stableId: stableIdOf(container),
      testIdAttr: testId && testId.attr,
      testId: testId && testId.value,
      controlHint: controlHintOf(first),
      label,
      labelSource,
      required: requiredOf([container, ...visibleRadios], labelEl),
      validations: validationsOf(visibleRadios),
      maxLength: null, minLength: null, pattern: null, min: null, max: null,
      readOnly: attr(container, 'aria-readonly') === 'true',
      disabled: visibleRadios.every(r => r.disabled || attr(r, 'aria-disabled') === 'true'),
      value: checked ? checked.label : null,
      placeholder: null,
      options,
      needsOpen: false,
      listboxId: null,
      box: boxOf(container),
      html: snippet(container)
    });
  }

  // Report in document order: radio groups were collected last.
  results.sort((a, b) => a.el.compareDocumentPosition(b.el) & Node.DOCUMENT_POSITION_FOLLOWING ? -1 : 1);
  return results.map(({ el, ...rest }) => rest);
}
