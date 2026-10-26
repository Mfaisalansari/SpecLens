# SpecLens

Black-box "UI reverse engineering": explore a running web app through its UI only (no code access)
and generate an Inferred Functional Specification — screen inventory, field dictionary, business
rules, field dependencies, navigation graph, Gherkin scenarios, open questions. Every item carries
evidence (screenshot / DOM snippet / trace) and a confidence level.

The target app is **Pega**: generated ids, the case rendered inside an iframe (`PegaGadget0Ifr`),
required markers on the label rather than the input, `$PpyWorkPage$pX` name attributes,
`data-test-id` and `data-ctl` attributes. Never rely on generated ids.

## Principles
- Deterministic first. No LLM in the core pipeline (optional wording polish at the very end only).
- Stages exchange plain JSON and run independently from the CLI; re-run inference without re-crawling.
- One job per module. No orchestrator, decision engine or scoring.
- Stay lean: Java 21 + Maven + Playwright + Jackson + Freemarker + picocli. Nothing else without a reason.

## Modules
- `speclens-model`   records (Screen, Field, Rule, Transition, Evidence, ...) and `Json` (the one ObjectMapper)
- `speclens-capture` Playwright: stability wait, per-frame scan (`scan.js`), dropdown enumeration, crops, aria snapshot, network
- `speclens-infer`   observations → spec.json: numbered "shall" requirements, screen descriptions,
                     shared elements (on ≥60% of screens, described once), open questions. Wording rules
                     live in `RuleWriter` / `Phrases`; every statement comes from an observed attribute.
- `speclens-report`  Freemarker SRS document `spec.html` (`srs.ftlh`): intro, inventory, requirements
                     per screen with evidence, data dictionary, open questions. Prints cleanly to PDF.
- `speclens-cli`     picocli entry point `io.speclens.cli.SpecLens`: `login | capture | infer | report`

Planned: `speclens-probe` (Phase 2), then `diff` in the CLI.

Flow: capture → observations.json → probe → probes.json → infer → spec.json → report.

## Phases
1. **Single-screen analyzer** (built). Done when a real Pega screen (e.g. Submission Details)
   produces a field dictionary that matches a hand-written one with no edits.
2. Probing: boundary inputs, validation messages, conditional show/hide via DOM diff.
3. Multi-screen capture along a recorded journey → navigation graph.
4. Flow grouping and Gherkin output.
5. `speclens diff baseline.json new.json` → behaviour change report.

## Commands
```sh
mvn package                                   # build + tests (test drives a real headless Chromium)
java -jar speclens-cli/target/speclens.jar login <url> -o auth.json
java -jar speclens-cli/target/speclens.jar capture <url> -s auth.json -i -o speclens-out
java -jar speclens-cli/target/speclens.jar infer -o speclens-out -a "App name"   # re-infer, no re-capture
java -jar speclens-cli/target/speclens.jar report -o speclens-out                 # re-render spec.html
```
`capture` runs infer and report automatically unless `--no-report` is given.
`-i/--interactive` opens a visible browser and waits for Enter, for screens that have no direct URL.

## Conventions
- In-page logic lives in `speclens-capture/src/main/resources/io/speclens/capture/*.js`, loaded by `Scripts`.
- Paths in JSON are relative to the output directory.
- The test fixture `speclens-capture/src/test/resources/fixtures/pega-like.html` mimics Pega markup;
  extend it (and `ScreenCapturerTest`) whenever scanning logic changes.
- `auth.json` (storage state) contains live cookies — never commit it.
