<#-- Software Requirements Specification. Model: spec (spec.json as maps), date, screenNames, requirementCount. -->
<#assign typeNames = {"REQUIRED": "Mandatory", "FORMAT": "Format", "LENGTH": "Length", "RANGE": "Range",
  "ALLOWED_VALUES": "Allowed values", "VISIBILITY": "Visibility", "ENABLEMENT": "State",
  "DEFAULT_VALUE": "Default", "CROSS_FIELD": "Cross-field"}>
<#assign hasCommon = spec.common??>
<#macro confidence level><span class="conf ${level?lower_case}">${level?capitalize}</span></#macro>
<#macro crop field><#list field.evidence?filter(e -> e.kind == "CROP") as e><img class="crop" src="${e.path}" alt="${field.label!field.id}"><#break></#list></#macro>
<#macro requirements screen>
  <#if screen.rules?has_content>
  <table class="reqs">
    <thead><tr><th class="id">ID</th><th>Requirement</th><th class="type">Type</th><th class="cf">Confidence</th><th class="ev">Evidence</th></tr></thead>
    <tbody>
    <#list screen.rules as r>
      <#assign f = screen.fields?filter(x -> x.id == r.fieldIds[0])?first>
      <tr id="${r.id}">
        <td class="id">${r.id}</td>
        <td>${r.statement}</td>
        <td class="type">${typeNames[r.type]!r.type}</td>
        <td class="cf"><@confidence r.confidence/></td>
        <td class="ev"><@crop f/><#list r.evidence?filter(e -> e.kind == "DOM") as e><div class="src">${e.detail}</div></#list></td>
      </tr>
    </#list>
    </tbody>
  </table>
  <#else>
  <p class="muted">No constraints are declared on this screen's fields. Its behaviour is described by the text above and the data dictionary.</p>
  </#if>
</#macro>
<#macro dictionary screen>
  <table class="dict">
    <thead><tr><th class="id">ID</th><th>Field</th><th>Type</th><th>Mandatory</th><th>Constraints</th><th>Default</th><th>Values</th><th>Locator</th></tr></thead>
    <tbody>
    <#list screen.fields as f>
      <tr>
        <td class="id">${f.id}</td>
        <td><div class="fname">${f.label!"(no label)"}</div><#if f.property??><code>${f.property}</code><#elseif f.name??><code>${f.name}</code></#if></td>
        <td>${f.role!"?"}<#if f.inputType?? && f.inputType != "text" && f.inputType != f.role && f.role != "radiogroup"> (${f.inputType})</#if><#if f.controlHint??><br><span class="muted">${f.controlHint}</span></#if></td>
        <td><#if f.required>Yes<#elseif f.kind == "ACTION">–<#else>No</#if></td>
        <td>
          <#if f.maxLength??>max ${f.maxLength} chars<br></#if>
          <#if f.minLength??>min ${f.minLength} chars<br></#if>
          <#if f.min?? || f.max??>${f.min!"…"} to ${f.max!"…"}<br></#if>
          <#if f.pattern??><code>${f.pattern}</code><br></#if>
          <#list (f.validations![])?filter(v -> v.rule != "required") as v>${v.rule}<#if v.params?has_content> (<#list v.params as k, p>${k} ${p}<#sep>, </#list>)</#if><br></#list>
          <#if f.readOnly>read-only<br></#if><#if f.disabled>disabled</#if>
        </td>
        <td>${f.defaultValue!""}</td>
        <td><#if f.options?? && f.options?has_content><#list f.options[0..*8] as o>${o.label!o.value!""}<#sep>, </#list><#if (f.options?size > 8)> … (${f.options?size} in total)</#if></#if></td>
        <td><#if f.locator??><code>${f.locator}</code></#if></td>
      </tr>
    </#list>
    </tbody>
  </table>
</#macro>
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${spec.application} SRS</title>
<style>
  :root {
    --bg: #ffffff; --page: #ffffff; --text: #1f2328; --muted: #656d76; --border: #d8dee4; --soft: #f6f8fa;
    --accent: #1f4e8c; --high-bg: #dafbe1; --high: #116329; --medium-bg: #fff8c5; --medium: #7d4e00;
    --low-bg: #ffebe9; --low: #a40e26; --note-bg: #f1f6fd;
  }
  @media (prefers-color-scheme: dark) {
    :root:not([data-theme="light"]) {
      --bg: #0d1117; --page: #0d1117; --text: #e6edf3; --muted: #9198a1; --border: #30363d; --soft: #161b22;
      --accent: #79b8ff; --high-bg: #12361f; --high: #7ee2a0; --medium-bg: #3b2e05; --medium: #f2cc60;
      --low-bg: #3d1418; --low: #ff9ea6; --note-bg: #13233a;
    }
  }
  * { box-sizing: border-box; }
  body { margin: 0; background: var(--bg); color: var(--text);
         font: 15px/1.6 Georgia, "Iowan Old Style", "Times New Roman", serif; }
  .doc { max-width: 980px; margin: 0 auto; padding: 40px 16px 80px; }
  h1, h2, h3, h4, th, .sans, table { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
  h1 { font-size: 30px; line-height: 1.2; margin: 0; }
  h2 { font-size: 22px; margin: 48px 0 12px; padding-bottom: 6px; border-bottom: 2px solid var(--accent); color: var(--accent); }
  h3 { font-size: 18px; margin: 32px 0 8px; }
  h4 { font-size: 15px; margin: 20px 0 6px; color: var(--muted); }
  p { margin: 8px 0; }
  a { color: var(--accent); }
  code { font: 12px/1.4 ui-monospace, SFMono-Regular, Menlo, monospace; background: var(--soft); padding: 1px 4px; border-radius: 3px; overflow-wrap: anywhere; }
  .muted { color: var(--muted); }
  .cover { border-bottom: 1px solid var(--border); padding-bottom: 24px; }
  .cover .kind { font-family: -apple-system, sans-serif; text-transform: uppercase; letter-spacing: .08em; font-size: 12px; color: var(--muted); margin-bottom: 6px; }
  .cover .subtitle { font-size: 18px; color: var(--muted); margin-top: 6px; }
  .control { margin-top: 20px; width: auto; min-width: 0; }
  .control th { text-align: left; width: 180px; color: var(--muted); font-weight: 600; }
  .toc { background: var(--soft); border: 1px solid var(--border); border-radius: 6px; padding: 14px 20px; margin-top: 28px; }
  .toc ol { margin: 4px 0; padding-left: 20px; }
  .toc li { margin: 2px 0; }
  .table-wrap { overflow-x: auto; }
  table { border-collapse: collapse; width: 100%; font-size: 13px; line-height: 1.45; }
  th, td { text-align: left; vertical-align: top; padding: 7px 9px; border: 1px solid var(--border); }
  thead th { background: var(--soft); font-weight: 600; font-size: 12px; }
  td.id, th.id { white-space: nowrap; width: 1%; font-family: ui-monospace, Menlo, monospace; font-size: 12px; }
  td.type { white-space: nowrap; color: var(--muted); }
  td.cf, th.cf { width: 1%; }
  td.ev, th.ev { width: 200px; }
  .reqs td:nth-child(2) { font-family: Georgia, "Times New Roman", serif; font-size: 14px; }
  .crop { display: block; max-width: 190px; max-height: 60px; border: 1px solid var(--border); border-radius: 3px; background: #fff; }
  .src { font-family: ui-monospace, Menlo, monospace; font-size: 11px; color: var(--muted); margin-top: 3px; overflow-wrap: anywhere; }
  .conf { display: inline-block; font-size: 11px; font-weight: 600; padding: 1px 8px; border-radius: 999px; }
  .conf.high { background: var(--high-bg); color: var(--high); }
  .conf.medium { background: var(--medium-bg); color: var(--medium); }
  .conf.low { background: var(--low-bg); color: var(--low); }
  .dict { font-size: 12px; }
  .dict { min-width: 860px; }
  .fname { font-weight: 600; }
  figure { margin: 16px 0; }
  figure img { display: block; max-width: 100%; max-height: 520px; border: 1px solid var(--border); border-radius: 4px; }
  figcaption { font-family: -apple-system, sans-serif; font-size: 12px; color: var(--muted); margin-top: 6px; }
  .note { background: var(--note-bg); border-left: 4px solid var(--accent); padding: 10px 14px; margin: 14px 0; border-radius: 0 4px 4px 0; }
  .reqs { min-width: 760px; }
  @media print {
    body { font-size: 11pt; }
    .doc { max-width: none; padding: 0; }
    h2 { break-before: page; }
    tr, figure { break-inside: avoid; }
    .toc a { color: inherit; text-decoration: none; }
  }
</style>
</head>
<body>
<div class="doc">

<header class="cover">
  <div class="kind">Software Requirements Specification</div>
  <h1>${spec.application}</h1>
  <div class="subtitle">Functional behaviour inferred from the user interface</div>
  <table class="control">
    <tr><th>Version</th><td>0.1 (draft)</td></tr>
    <tr><th>Date</th><td>${date}</td></tr>
    <tr><th>Status</th><td>Draft for business review</td></tr>
    <tr><th>Prepared by</th><td>SpecLens 0.1 (automated observation of the running application)</td></tr>
    <tr><th>Coverage</th><td>${spec.screens?size} screen<#if spec.screens?size != 1>s</#if>, ${requirementCount} requirements, ${spec.questions?size} open question<#if spec.questions?size != 1>s</#if></td></tr>
  </table>
</header>

<nav class="toc sans">
  <strong>Contents</strong>
  <ol>
    <li><a href="#s1">Introduction</a></li>
    <li><a href="#s2">Overall description</a></li>
    <li><a href="#s3">Functional requirements</a>
      <ol><#list spec.screens as s><li><a href="#${s.screenId}">${s.name}</a></li></#list></ol>
    </li>
    <li><a href="#s4">Data dictionary</a></li>
    <li><a href="#s5">Open questions</a></li>
  </ol>
</nav>

<h2 id="s1">1. Introduction</h2>

<h3>1.1 Purpose</h3>
<p>This document specifies the functional behaviour of <strong>${spec.application}</strong> as it can be observed through its user interface.
It is written for business analysts, testers and developers who need a current, verifiable description of what the application does,
for example to confirm requirements with the business, plan test coverage or support a change or re-implementation.</p>

<h3>1.2 Scope</h3>
<p>The document covers the ${spec.screens?size} screen<#if spec.screens?size != 1>s</#if> listed in section 2.1, as captured on ${date}.
For each screen it describes the information the user provides, the rules that apply to that information and the actions available.</p>
<p>Behaviour that only becomes visible after the user interacts with the screen is not yet covered: validation feedback on submission,
fields that appear, disappear or change in response to other fields, and the navigation between screens.
These will be added in later versions by probing the application.</p>

<h3>1.3 How this document was produced</h3>
<p>Each screen was opened in a browser and left to settle. Every interactive element was then recorded together with its visible label
and the constraints the page itself declares: mandatory markers, length limits, input formats, permitted values, default values and the
validation messages the application is set up to show. Each requirement in section 3 is derived from one such observation and cites it
as evidence, with a picture of the field and the attribute it was read from. No source code, database or existing documentation was used.</p>
<div class="note">Because the requirements are inferred, they describe what the application <em>does</em>, which may differ from what it
was <em>intended</em> to do. Section 5 lists the points a business owner needs to confirm.</div>

<h3>1.4 Confidence levels</h3>
<div class="table-wrap">
<table>
  <thead><tr><th>Level</th><th>Meaning</th></tr></thead>
  <tbody>
    <tr><td><@confidence "HIGH"/></td><td>Read directly from a declaration in the page, such as a <code>required</code> or <code>maxlength</code> attribute, an explicit label or a validation message.</td></tr>
    <tr><td><@confidence "MEDIUM"/></td><td>Derived from a strong convention, such as an asterisk next to a field or a value that was present when the screen opened.</td></tr>
    <tr><td><@confidence "LOW"/></td><td>A best guess, such as a field name taken from nearby text. Treat as unconfirmed.</td></tr>
  </tbody>
</table>
</div>

<h3>1.5 Conventions</h3>
<p>Requirements are numbered <code>CODE-NN</code>, where the code identifies the screen (for example <code>${spec.screens[0].code}-01</code>).
The word <em>shall</em> marks a requirement. Field names appear in quotation marks exactly as they are labelled on screen.</p>

<h2 id="s2">2. Overall description</h2>

<h3>2.1 Screen inventory</h3>
<p>The following screens were captured. Each is specified in its own subsection of section 3.</p>
<div class="table-wrap">
<table>
  <thead><tr><th>Section</th><th>Screen</th><th>Code</th><th>Input fields</th><th>Mandatory</th><th>Actions</th><th>Requirements</th></tr></thead>
  <tbody>
  <#list spec.screens as s>
    <tr>
      <td>3.${s?counter}</td>
      <td><a href="#${s.screenId}">${s.name}</a></td>
      <td><code>${s.code}</code></td>
      <td>${s.fields?filter(f -> f.kind != "ACTION")?size}</td>
      <td>${s.fields?filter(f -> f.required)?size}</td>
      <td>${s.fields?filter(f -> f.kind == "ACTION")?size}</td>
      <td>${s.rules?size}</td>
    </tr>
  </#list>
  </tbody>
</table>
</div>

<h3>2.2 Shared elements</h3>
<#if hasCommon>
<#list spec.common.summary as p><p>${p}</p></#list>
<p>The shared elements are:
<#list spec.common.fields as f><strong>${f.label!"(no label)"}</strong> (${f.role!"element"})<#sep>, </#list>.</p>
<@requirements spec.common/>
<#else>
<p>No elements were found that repeat across screens.</p>
</#if>

<h2 id="s3">3. Functional requirements</h2>
<p>This section specifies each screen in turn: a description of the screen, a picture of it as captured, and its numbered requirements.</p>

<#list spec.screens as s>
<h3 id="${s.screenId}">3.${s?counter} ${s.name}</h3>

<h4>3.${s?counter}.1 Description</h4>
<#list s.summary as p><p>${p}</p></#list>
<#if s.screenshot??>
<figure>
  <a href="${s.screenshot}"><img src="${s.screenshot}" alt="The ${s.name} screen as captured"></a>
  <figcaption>Figure ${s?counter}. The ${s.name} screen as captured. Select the picture to view it at full size.</figcaption>
</figure>
</#if>

<h4>3.${s?counter}.2 Requirements</h4>
<div class="table-wrap"><@requirements s/></div>
</#list>

<h2 id="s4">4. Data dictionary</h2>
<p>Every element recorded on each screen, with the technical details needed to find and test it.
The locator column gives a stable way to address the element in automated tests.</p>
<#if hasCommon>
<h3>4.0 Shared elements</h3>
<div class="table-wrap"><@dictionary spec.common/></div>
</#if>
<#list spec.screens as s>
<h3>4.${s?counter} ${s.name}</h3>
<#if s.fields?has_content><div class="table-wrap"><@dictionary s/></div><#else><p class="muted">No screen-specific elements.</p></#if>
</#list>

<h2 id="s5">5. Open questions</h2>
<#if spec.questions?has_content>
<p>The following points could not be settled by observation alone. Each needs an answer from someone who knows the business intent.</p>
<div class="table-wrap">
<table>
  <thead><tr><th class="id">ID</th><th>Screen</th><th>Question</th></tr></thead>
  <tbody>
  <#list spec.questions as q>
    <tr><td class="id">${q.id}</td><td>${screenNames[q.screenId]!q.screenId}</td><td>${q.text}</td></tr>
  </#list>
  </tbody>
</table>
</div>
<#else>
<p>No open questions were raised.</p>
</#if>

</div>
</body>
</html>
