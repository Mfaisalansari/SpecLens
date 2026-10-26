// Resolves true once the frame's DOM has had no mutations for quietMs,
// or false if that never happens within timeoutMs.
([quietMs, timeoutMs]) => new Promise(resolve => {
  const start = performance.now();
  let last = start;
  const observer = new MutationObserver(() => { last = performance.now(); });
  observer.observe(document, { subtree: true, childList: true, attributes: true, characterData: true });
  const tick = () => {
    const now = performance.now();
    if (now - last >= quietMs) { observer.disconnect(); resolve(true); }
    else if (now - start >= timeoutMs) { observer.disconnect(); resolve(false); }
    else setTimeout(tick, 50);
  };
  tick();
})
