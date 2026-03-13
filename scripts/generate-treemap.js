#!/usr/bin/env node
'use strict';
/**
 * Cursor tools treemap generator.
 * Conventions (keep in sync with workflow):
 *   - Delegation: MAIN_INVOKES, AGENT_INVOCATION_MAP must match docs/workflow/SUBAGENT-DELEGATION.md §1, §2.2, §3.
 *   - Requirement-doc refs: path pattern excluded from "other"; TOPIC-INDEX.md for Backend, DB, Requirements, RequirementsPastSearch.
 * Rule: .cursor/rules/treemap-consistency.mdc. See docs/workflow/RECOMMENDATION-requirement-doc-ref-display.md.
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const ROOT = path.resolve(__dirname, '..');
const TEMPLATE_PATH = path.join(__dirname, 'treemap-template.html');
const OUTPUT_PATH = path.join(ROOT, 'docs', 'cursor-tools-treemap.html');
const I18N_PATH = path.join(__dirname, 'treemap-i18n.json');

const GITHUB_OWNER = 'MinGH-dev';
const GITHUB_REPO = 'logmng';

const STEP_NAMES = {
  '1': '요건 정의',
  '2': '보안 검토',
  '3': '설계 검토',
  '4': '구현',
  '4.5': '리뷰',
  '5': 'QA & 커밋',
  '6': '문서 & 릴리스'
};
const STEP_ORDER = ['1', '2', '3', '4', '4.5', '5', '6'];

const LIGHT_AGENTS = new Set([
  'RequirementsPastSearch', 'Documentation', 'Release',
  'Backend-Auth', 'Backend-Log', 'Backend-ActivityLog',
  'Frontend-Auth', 'Frontend-Log', 'Frontend-ActivityLog',
  'UX-A11y', 'UX-Layout', 'UX-Components'
]);

// ── i18n ──
let i18n = {};
if (fs.existsSync(I18N_PATH)) {
  try { i18n = JSON.parse(fs.readFileSync(I18N_PATH, 'utf8')); } catch (e) { /* ignore */ }
}
function i18nDesc(category, key, fallback) {
  return (i18n[category] && i18n[category][key]) || fallback;
}

// ── File Helpers ──
function readFile(relPath) {
  return fs.readFileSync(path.join(ROOT, relPath), 'utf8');
}

function listDir(relDir, ext) {
  const abs = path.join(ROOT, relDir);
  if (!fs.existsSync(abs)) return [];
  return fs.readdirSync(abs)
    .filter(f => f.endsWith(ext) && !f.startsWith('.'))
    .sort();
}

function listSkillDirs() {
  const abs = path.join(ROOT, '.cursor/skills');
  if (!fs.existsSync(abs)) return [];
  return fs.readdirSync(abs)
    .filter(d => {
      const p = path.join(abs, d);
      return fs.statSync(p).isDirectory() && !d.startsWith('.')
        && fs.existsSync(path.join(p, 'SKILL.md'));
    })
    .sort();
}

// ── Frontmatter Parser ──
function parseFrontmatter(content) {
  const m = content.match(/^---\r?\n([\s\S]*?)\r?\n---/);
  if (!m) return {};
  const fm = {};
  let currentKey = null;
  for (const line of m[1].split('\n')) {
    const kv = line.match(/^(\w+):\s*(.+)/);
    if (kv) {
      currentKey = kv[1].trim();
      fm[currentKey] = kv[2].trim();
    } else if (currentKey && line.match(/^\s+/)) {
      fm[currentKey] += ' ' + line.trim();
    }
  }
  return fm;
}

function firstHeading(content) {
  const m = content.match(/^#+\s+(.+)/m);
  return m ? m[1].trim() : '';
}

function firstSentence(content) {
  const body = content.replace(/^---[\s\S]*?---\s*/, '');
  for (const line of body.split('\n')) {
    const t = line.trim();
    if (t && !t.startsWith('#') && !t.startsWith('|') && !t.startsWith('-')
        && !t.startsWith('>') && !t.startsWith('```') && !t.startsWith('*')
        && !t.startsWith('Use ') && !t.startsWith('Do ')) {
      return t.slice(0, 150);
    }
  }
  return '';
}

// ── Reference Extraction ──
function extractRefs(content) {
  const refs = new Set();
  let m;
  const rePath = /`([^`\s]*(?:\.md|\.mdc|\.yaml|\.yml|\.sql|\.sh|\.json)[^`]*)`/g;
  while ((m = rePath.exec(content))) {
    const v = m[1].replace(/^`|`$/g, '');
    if (!v.includes(' ') && v.length < 120) refs.add(v);
  }
  const reLink = /\[.*?\]\(\.?\/?([^)\s]+\.(?:md|mdc))\)/g;
  while ((m = reLink.exec(content))) refs.add(m[1]);
  return [...refs];
}

function extractSkillNames(content) {
  const names = [];
  const re = /\|\s*`([a-z][\w-]+(?:-[\w-]+)*)`\s*\|/g;
  let m;
  while ((m = re.exec(content))) {
    if (!names.includes(m[1])) names.push(m[1]);
  }
  return names;
}

function classifyRef(ref) {
  if (ref.includes('.cursor/rules/')) return 'rules';
  if (ref.includes('.cursor/skills/')) return 'skills';
  if (ref.includes('.cursor/commands/')) return 'commands';
  if (ref.includes('.cursor/agents/')) return 'agents';
  if (ref.includes('docs/workflow/')) return 'workflow';
  if (ref.includes('docs/template/')) return 'templates';
  if (ref.endsWith('.mdc') && !ref.includes('/')) return 'rules';
  return 'other';
}

function refDisplayName(ref) {
  const base = path.basename(ref);
  if (base === 'SKILL.md') {
    const parts = ref.split('/');
    return parts.length >= 2 ? parts[parts.length - 2] : base;
  }
  if (ref.includes('.cursor/commands/')) return base.replace(/\.md$/, '');
  if (ref.includes('.cursor/rules/')) return base;
  if (ref.includes('docs/workflow/')) return base;
  if (ref.includes('docs/template/')) return base;
  if (ref.includes('docs/') && !ref.includes('docs/workflow/') && !ref.includes('docs/template/')) return ref;
  return base;
}

function normalizeRefName(name) {
  return name.replace(/\.md$/, '').replace(/\.mdc$/, '');
}

function classifyAndNameRef(ref) {
  return { name: refDisplayName(ref), type: classifyRef(ref), path: ref };
}

// Requirement doc path pattern (writing convention only). Exclude from "other docs"; search = RequirementsPastSearch + TOPIC-INDEX.
function isRequirementDocPathPattern(ref) {
  if (!ref || typeof ref !== 'string') return false;
  if (ref === 'docs/requirements/yyyyMMdd-name.md') return true;
  if (/^docs\/requirements\/yyyyMMdd[-.\w]*\.md$/i.test(ref)) return true;
  if (/^yyyyMMdd[-.\w]*\.md$/i.test(ref)) return true;
  if (ref.startsWith('docs/requirements/') && /yyyyMMdd/i.test(ref)) return true;
  return false;
}

// Agents that reference requirement docs: ensure TOPIC-INDEX.md (search index) appears in "other" refs.
const AGENTS_WITH_REQUIREMENT_DOC_REF = new Set(['Backend', 'DB', 'Requirements', 'RequirementsPastSearch']);
const TOPIC_INDEX_REF = 'docs/requirements/TOPIC-INDEX.md';

// ── Scanners ──
function scanRules() {
  return listDir('.cursor/rules', '.mdc').map(f => {
    const content = readFile(`.cursor/rules/${f}`);
    const fm = parseFrontmatter(content);
    const rawRefs = extractRefs(content);
    const refs = rawRefs.map(classifyAndNameRef)
      .filter(r => r.type !== 'rules' && r.type !== 'agents');
    return {
      name: f,
      desc: i18nDesc('rules', f, fm.description || firstSentence(content) || f),
      refs
    };
  });
}

function scanSkills() {
  return listSkillDirs().map(d => {
    const relPath = `.cursor/skills/${d}/SKILL.md`;
    const content = readFile(relPath);
    const fm = parseFrontmatter(content);
    const rawRefs = extractRefs(content);
    const refs = rawRefs.map(classifyAndNameRef)
      .filter(r => r.type !== 'skills' && r.type !== 'agents');
    return {
      name: d,
      desc: i18nDesc('skills', d, fm.description || fm.name || firstSentence(content) || d),
      category: d.endsWith('-domain') ? 'domain' : 'workflow',
      refs
    };
  });
}

function scanCommands() {
  return listDir('.cursor/commands', '.md').map(f => {
    const content = readFile(`.cursor/commands/${f}`);
    const name = f.replace(/\.md$/, '');
    let category = 'workflow';
    if (/^(start|stop|restart|check)-/.test(name)) category = 'infra';
    if (/^agent-/.test(name)) category = 'agent';
    const heading = firstHeading(content);
    return {
      name,
      desc: i18nDesc('commands', name, heading || name),
      category
    };
  });
}

function scanAgents() {
  return listDir('.cursor/agents', '.mdc').map(f => {
    const content = readFile(`.cursor/agents/${f}`);
    const fm = parseFrontmatter(content);
    const name = fm.name || f.replace('.mdc', '');

    let stepNum = '?';
    const stepRe = /\*\*Step (\d+(?:\.\d+)?)\*\*/g;
    let sm;
    while ((sm = stepRe.exec(content))) { stepNum = sm[1]; break; }
    if (stepNum === '?') {
      const altRe = /Step (\d+(?:\.\d+)?)/;
      const alt = content.match(altRe);
      if (alt) stepNum = alt[1];
    }

    if (stepNum === '?' && AGENT_STEP_FALLBACK[name]) stepNum = AGENT_STEP_FALLBACK[name];
    const normalizedStep = normalizeStepNum(stepNum);
    const light = LIGHT_AGENTS.has(name);
    const skills = extractSkillNames(content);
    const rawRefs = extractRefs(content);
    const categorized = { rules: [], skills: [...skills], commands: [], workflow: [], templates: [], agents: [], other: [] };

    for (const ref of rawRefs) {
      const { name: rName, type } = classifyAndNameRef(ref);
      if (type === 'skills') {
        if (!categorized.skills.includes(rName)) categorized.skills.push(rName);
        continue;
      }
      const target = categorized[type];
      if (!target) continue;
      // Exclude requirement-doc path pattern from "other" (writing convention only; search = RequirementsPastSearch + TOPIC-INDEX).
      if (type === 'other' && isRequirementDocPathPattern(ref)) continue;
      const displayName = type === 'other' ? ref : rName.replace(/\.(md|mdc)$/, '');
      if (!target.includes(displayName) && !target.includes(rName)) {
        target.push(displayName);
      }
    }
    // Ensure TOPIC-INDEX.md (requirement doc search index) is in "other" for agents that reference requirement docs.
    if (AGENTS_WITH_REQUIREMENT_DOC_REF.has(name) && !categorized.other.includes(TOPIC_INDEX_REF)) {
      categorized.other.push(TOPIC_INDEX_REF);
    }

    return {
      name,
      step: `Step ${normalizedStep}`,
      stepNum: normalizedStep,
      scope: fm.description || '',
      light,
      ...categorized
    };
  });
}

function normalizeStepNum(raw) {
  const n = parseFloat(raw);
  if (isNaN(n)) return '?';
  if (n >= 3 && n < 4) return '3';
  if (n >= 4 && n < 4.5) return '4';
  return String(n);
}

const AGENT_STEP_FALLBACK = {
  'RequirementsPastSearch': '1',
  'Backend-Auth': '4', 'Backend-Log': '4', 'Backend-ActivityLog': '4',
  'Frontend-Auth': '4', 'Frontend-Log': '4', 'Frontend-ActivityLog': '4',
  'UX-A11y': '3', 'UX-Layout': '3', 'UX-Components': '3'
};

// Who the main agent invokes (by step). Single source for treemap; keep in sync with SUBAGENT-DELEGATION.md §1.
const MAIN_INVOKES = new Set([
  'Requirements', 'RequirementsPastSearch', 'Security', 'Contract', 'DBA',
  'Architecture', 'Consistency', 'UX', 'Backend', 'Frontend', 'DB',
  'Review', 'QA', 'Documentation', 'Release'
]);

// Agent → agents it invokes (delegation). Single source for treemap; keep in sync with SUBAGENT-DELEGATION.md §2, §3.
const AGENT_INVOCATION_MAP = {
  Requirements: ['RequirementsPastSearch'],
  Backend: ['Backend-Auth', 'Backend-ActivityLog', 'Backend-Log'],
  Frontend: ['Frontend-Auth', 'Frontend-ActivityLog', 'Frontend-Log'],
  UX: ['UX-A11y', 'UX-Layout', 'UX-Components'],
  QA: ['Requirements']  // on failure: hand off to Requirements
};

function scanWorkflow() {
  return listDir('docs/workflow', '.md')
    .filter(f => !f.startsWith('.'))
    .map(f => {
      const content = readFile(`docs/workflow/${f}`);
      const rawRefs = extractRefs(content);
      const refs = rawRefs.map(classifyAndNameRef)
        .filter(r => r.type !== 'workflow' && r.type !== 'agents');
      return {
        name: f,
        desc: i18nDesc('workflow', f, firstHeading(content) || f),
        refs
      };
    });
}

function scanTemplates() {
  return listDir('docs/template', '.md').map(f => {
    const content = readFile(`docs/template/${f}`);
    return {
      name: f,
      desc: i18nDesc('templates', f, firstHeading(content) || f)
    };
  });
}

// ── Builders ──
function buildDocPaths(rules, skills, commands, agents, workflow, templates) {
  const paths = {};
  rules.forEach(r => { paths[r.name] = `.cursor/rules/${r.name}`; });
  skills.forEach(s => { paths[s.name] = `.cursor/skills/${s.name}/SKILL.md`; });
  commands.forEach(c => { paths[c.name] = `.cursor/commands/${c.name}.md`; });
  agents.forEach(a => { paths[`agent:${a.name}`] = `.cursor/agents/${a.name}.mdc`; });
  workflow.forEach(w => { paths[w.name] = `docs/workflow/${w.name}`; });
  templates.forEach(t => { paths[t.name] = `docs/template/${t.name}`; });
  const cursorSubagents = listDir('docs/cursor-subagents', '.md');
  cursorSubagents.forEach(f => { paths[f.replace(/\.md$/, '')] = `docs/cursor-subagents/${f}`; });
  ['docs/contract.md', 'docs/security-guide.md', 'docs/api-definition.md',
   'docs/QUICK_START.md', 'README.md', 'CHANGELOG.md',
   'docs/requirements/TOPIC-INDEX.md'
  ].forEach(d => {
    if (fs.existsSync(path.join(ROOT, d))) paths[d] = d;
  });
  return paths;
}

const TEAM_LEAD_LABEL = { Backend: 'Backend (팀장)', Frontend: 'Frontend (팀장)', UX: 'UX (팀장)' };

function buildFlowSteps(agents) {
  const groups = {};
  for (const a of agents) {
    const num = a.stepNum;
    if (num === '?') continue;
    if (!groups[num]) groups[num] = [];
    const displayName = TEAM_LEAD_LABEL[a.name] || null;
    groups[num].push({ name: a.name, light: a.light, displayName });
  }
  for (const num of Object.keys(groups)) {
    groups[num].sort((a, b) => (a.light ? 1 : 0) - (b.light ? 1 : 0));
  }
  return STEP_ORDER
    .filter(num => groups[num] && groups[num].length > 0)
    .map(num => {
      const stepAgents = groups[num];
      const invocations = [];
      // Main invokes only agents in MAIN_INVOKES (team leads etc.), not module agents (Backend-Auth, Frontend-Log, ...).
      const mainTargets = stepAgents.filter(a => MAIN_INVOKES.has(a.name)).map(a => a.name);
      if (mainTargets.length) invocations.push({ from: 'Main', to: mainTargets });
      stepAgents.forEach(a => {
        const toList = AGENT_INVOCATION_MAP[a.name];
        if (toList && toList.length) invocations.push({ from: a.name, to: toList });
      });
      return {
        step: `Step ${num}`,
        name: STEP_NAMES[num] || `Step ${num}`,
        agents: stepAgents,
        invocations
      };
    });
}

function buildAgentData(agents) {
  const data = {};
  const agentNames = new Set(agents.map(a => a.name));
  for (const a of agents) {
    const invokes = AGENT_INVOCATION_MAP[a.name] || [];
    const invokedBy = [];
    if (MAIN_INVOKES.has(a.name)) invokedBy.push('Main');
    for (const [invoker, list] of Object.entries(AGENT_INVOCATION_MAP)) {
      if (list.includes(a.name)) invokedBy.push(invoker);
    }
    data[a.name] = {
      step: a.step,
      scope: a.scope,
      invokes: invokes.filter(n => agentNames.has(n)),
      invokedBy,
      rules: a.rules,
      skills: a.skills,
      commands: a.commands,
      workflow: a.workflow,
      templates: a.templates,
      agents: a.agents || [],
      other: a.other
    };
  }
  return data;
}

function groupInfraCommands(commands) {
  const infra = commands.filter(c => c.category === 'infra');
  const groups = {};
  for (const cmd of infra) {
    const m = cmd.name.match(/^(start|stop|restart)-(.+)/);
    if (m) {
      const base = m[2];
      if (!groups[base]) groups[base] = { names: [], desc: cmd.desc };
      groups[base].names.push(cmd.name);
    } else {
      groups[cmd.name] = { names: [cmd.name], desc: cmd.desc };
    }
  }
  return Object.entries(groups).map(([base, data]) => {
    const grouped = data.names.length > 1;
    const displayName = grouped ? `start/stop/restart-${base}` : data.names[0];
    return {
      name: displayName,
      desc: i18nDesc('commands_grouped', displayName, i18nDesc('commands', data.names[0], data.desc)),
      category: 'infra'
    };
  });
}

function computeHubs(rules, skills, commands, workflow, templates, agents) {
  const refCounts = {};
  const norm = n => normalizeRefName(path.basename(n));
  const allItems = [...rules, ...skills, ...workflow];
  for (const item of allItems) {
    if (!item.refs) continue;
    for (const ref of item.refs) {
      refCounts[norm(ref.name)] = (refCounts[norm(ref.name)] || 0) + 1;
    }
  }
  for (const agent of agents) {
    for (const cat of ['rules', 'skills', 'commands', 'workflow', 'templates', 'agents', 'other']) {
      for (const ref of (agent[cat] || [])) {
        refCounts[norm(ref)] = (refCounts[norm(ref)] || 0) + 1;
      }
    }
  }

  const sorted = Object.entries(refCounts)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 6);
  const maxCount = sorted.length > 0 ? sorted[0][1] : 1;

  return sorted.map(([name, count]) => {
    let color = 'workflow';
    if (name.endsWith('.mdc')) color = 'rules';
    if (templates.some(t => norm(t.name) === name)) color = 'template';
    if (commands.some(c => c.name === name)) color = 'commands';

    const wfItem = workflow.find(w => norm(w.name) === name);
    const tmplItem = templates.find(t => norm(t.name) === name);
    const cmdItem = commands.find(c => c.name === name);
    const allKnown = [...rules, ...skills, ...workflow, ...templates];
    const anyItem = allKnown.find(i => norm(i.name) === name);

    let displayName = wfItem?.name || tmplItem?.name || cmdItem?.name || name;

    if (!wfItem && !tmplItem && !cmdItem) {
      const otherPaths = ['docs/contract.md', 'docs/security-guide.md', 'docs/api-definition.md'];
      const match = otherPaths.find(p => norm(path.basename(p)) === name);
      if (match) { displayName = match; color = 'other'; }
    }

    const role = i18nDesc('hubs', displayName, '') || i18nDesc('hubs', name, '') ||
      anyItem?.desc || displayName;

    return { name: displayName, color, role, refsTo: count, barWidth: Math.round((count / maxCount) * 100) };
  });
}

function buildSections(rules, skills, commands, workflow, templates, hubNames) {
  const skillsByCat = { '워크플로우 스킬': [], '도메인 스킬': [] };
  skills.forEach(s => {
    const cat = s.category === 'domain' ? '도메인 스킬' : '워크플로우 스킬';
    skillsByCat[cat].push(s);
  });

  const groupedInfra = groupInfraCommands(commands);
  const cmdsByCat = {
    '워크플로우 명령어': commands.filter(c => c.category === 'workflow'),
    '인프라 명령어': groupedInfra,
    '에이전트 호출 명령어': commands.filter(c => c.category === 'agent')
  };

  const isHub = w => hubNames.has(w.name) || hubNames.has(w.name.replace('.md', ''))
    || hubNames.has(normalizeRefName(w.name));
  const workflowByCat = {
    '핵심 허브 문서': workflow.filter(isHub),
    '기타 워크플로우 문서': workflow.filter(w => !isHub(w))
  };

  return {
    rules: { items: rules },
    skills: { categories: skillsByCat },
    commands: { categories: cmdsByCat },
    workflow: { categories: workflowByCat },
    templates: { items: templates }
  };
}

// ── Main ──
function main() {
  let branch;
  try {
    branch = execSync('git rev-parse --abbrev-ref HEAD', { cwd: ROOT, encoding: 'utf8' }).trim();
  } catch {
    branch = 'main';
  }

  const rules = scanRules();
  const skills = scanSkills();
  const commands = scanCommands();
  const agents = scanAgents();
  const workflow = scanWorkflow();
  const templates = scanTemplates();

  const docPaths = buildDocPaths(rules, skills, commands, agents, workflow, templates);
  const flowStepsData = buildFlowSteps(agents);
  const agentData = buildAgentData(agents);
  const hubsData = computeHubs(rules, skills, commands, workflow, templates, agents);
  const hubNames = new Set(hubsData.map(h => h.name));
  const sectionsData = buildSections(rules, skills, commands, workflow, templates, hubNames);

  const data = {
    github: { owner: GITHUB_OWNER, repo: GITHUB_REPO, branch },
    docPaths,
    agents: agentData,
    flowSteps: flowStepsData,
    sections: sectionsData,
    hubs: hubsData
  };

  if (!fs.existsSync(TEMPLATE_PATH)) {
    console.error(`Template not found: ${TEMPLATE_PATH}`);
    process.exit(1);
  }

  const template = fs.readFileSync(TEMPLATE_PATH, 'utf8');
  const dataScript = '<script>\nconst TREEMAP_DATA = ' + JSON.stringify(data, null, 2) + ';\n</script>';
  const html = template.replace('<!-- TREEMAP_DATA -->', dataScript);

  fs.writeFileSync(OUTPUT_PATH, html, 'utf8');

  console.log(`Generated: ${path.relative(ROOT, OUTPUT_PATH)}`);
  console.log(`  Rules: ${rules.length}, Skills: ${skills.length}, Commands: ${commands.length}`);
  console.log(`  Agents: ${agents.length}, Workflow: ${workflow.length}, Templates: ${templates.length}`);
  console.log(`  Hubs: ${hubsData.length}, Flow steps: ${flowStepsData.length}`);
}

main();
