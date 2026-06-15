/**
 * KodeGraph Interactive Dependency Visualizer Script
 * 
 * This script runs in the browser context inside the generated interactive HTML report.
 * It uses the vis-network library to render a dynamic, interactive dependency graph of Kotlin classes.
 * 
 * Key Features and Design:
 * 1. Force-Directed Layout: Nodes representing Kotlin classes and interfaces are positioned using vis-network's physics engine.
 * 2. Anchor Nodes: Invisible/semi-visible gravity anchor nodes are created for each Kotlin package. Class nodes are bound to
 *    their package anchor via invisible physics springs, grouping classes belonging to the same package close to each other.
 * 3. Color Coding: Packages are automatically colored using a curated palette to allow easy distinction.
 * 4. Filtering: Includes real-time search filtering (by class/package name), type filters (Class, Data, Interface, Enum, etc.),
 *    and package isolation filters.
 * 5. Selection and Focus Highlights: Clicking a node dims all non-connected nodes and edges, highlighting direct incoming
 *    and outgoing dependencies, and updates the sidebar details panel.
 */

declare const vis: any;

interface RawNode {
  id: string;
  label: string;
  packageName: string;
  type: string;
  implementedInterfaces?: string[];
}

interface RawEdge {
  from: string;
  to: string;
  label: string;
  type: string;
}

declare const rawNodes: RawNode[];
declare const rawEdges: RawEdge[];

// ─── Package Color Palette ───────────────────────────────────
const PACKAGE_PALETTE = [
  '#89b4fa', '#a6e3a1', '#cba6f7', '#fab387',
  '#f38ba8', '#f9e2af', '#89dceb', '#94e2d5',
  '#eba0ac', '#b4befe', '#f5c2e7', '#74c7ec'
];

// ─── Derive unique packages & assign colors ──────────────────
const ANCHOR_PREFIX = '__pkg_anchor__';
const packageColorMap: Record<string, string> = {};
const uniquePackages = Array.from(
  new Set(rawNodes.map(n => n.packageName || '<default>'))
).sort();

uniquePackages.forEach((pkg, i) => {
  packageColorMap[pkg] = PACKAGE_PALETTE[i % PACKAGE_PALETTE.length];
});

// ─── Type theme (for node border by class type) ──────────────
const typeThemes: Record<string, any> = {
  'CLASS':      { color: { border: '#89b4fa', background: '#1e1e2e', highlight: { border: '#b4befe', background: '#313244' } } },
  'DATA':       { color: { border: '#a6e3a1', background: '#1e1e2e', highlight: { border: '#b4befe', background: '#313244' } } },
  'INTERFACE':  { color: { border: '#cba6f7', background: '#1e1e2e', highlight: { border: '#b4befe', background: '#313244' } } },
  'ENUM':       { color: { border: '#fab387', background: '#1e1e2e', highlight: { border: '#b4befe', background: '#313244' } } },
  'ANNOTATION': { color: { border: '#f38ba8', background: '#1e1e2e', highlight: { border: '#b4befe', background: '#313244' } } },
  'BROADCAST':  { color: { border: '#f9e2af', background: '#1e1e2e', highlight: { border: '#b4befe', background: '#313244' } } }
};

const TYPE_COLORS: Record<string, string> = {
  'CLASS': 'var(--color-class)', 'DATA': 'var(--color-data)',
  'INTERFACE': 'var(--color-interface)', 'ENUM': 'var(--color-enum)',
  'ANNOTATION': 'var(--color-annotation)', 'BROADCAST': 'var(--color-broadcast)'
};

const TYPE_CHARS: Record<string, string> = {
  'CLASS': 'C', 'DATA': 'D', 'INTERFACE': 'I', 'ENUM': 'E', 'ANNOTATION': 'A', 'BROADCAST': 'B'
};

// ─── State ───────────────────────────────────────────────────
let nodesDataset: any;
let edgesDataset: any;
let network: any = null;
let selectedNodeId: string | null = null;

let activeFilters = {
  search: '',
  types: new Set<string>(['CLASS', 'DATA', 'INTERFACE', 'ENUM', 'ANNOTATION', 'BROADCAST']),
  packages: new Set<string>(uniquePackages)
};

// Helper: is this an anchor node id?
function isAnchor(id: any): boolean {
  return typeof id === 'string' && id.startsWith(ANCHOR_PREFIX);
}

// ─── Package Tree Structure ──────────────────────────────────
interface TreeItem {
  name: string;
  fullName: string;
  isPackage: boolean;
  classType?: string;
  children: TreeItem[];
}

let expandedPackages = new Set<string>();

function buildTree(nodes: RawNode[]): TreeItem[] {
  const rootMap: Record<string, any> = {};

  nodes.forEach(node => {
    const pkg = node.packageName || '';
    const parts = pkg ? pkg.split('.') : [];
    
    let currentMap = rootMap;
    let currentPath = '';

    parts.forEach(part => {
      currentPath = currentPath ? `${currentPath}.${part}` : part;
      if (!currentMap[part]) {
        currentMap[part] = {
          _meta: { name: part, fullName: currentPath, isPackage: true },
          _children: {}
        };
      }
      currentMap = currentMap[part]._children;
    });

    currentMap[node.label] = {
      _meta: { name: node.label, fullName: node.id, isPackage: false, classType: node.type },
      _children: null
    };
  });

  function convert(map: Record<string, any> | null): TreeItem[] {
    if (!map) return [];
    return Object.keys(map).map(key => {
      const entry = map[key];
      return {
        name: entry._meta.name,
        fullName: entry._meta.fullName,
        isPackage: entry._meta.isPackage,
        classType: entry._meta.classType,
        children: convert(entry._children)
      };
    }).sort((a, b) => {
      if (a.isPackage && !b.isPackage) return -1;
      if (!a.isPackage && b.isPackage) return 1;
      return a.name.localeCompare(b.name);
    });
  }

  return convert(rootMap);
}

function toggleFolder(folderPath: string, rowEl: HTMLElement, childrenEl: HTMLElement) {
  const isCollapsed = childrenEl.classList.toggle('collapsed');
  const chevron = rowEl.querySelector('.tree-chevron');
  if (chevron) {
    chevron.classList.toggle('collapsed', isCollapsed);
  }
  if (isCollapsed) {
    expandedPackages.delete(folderPath);
  } else {
    expandedPackages.add(folderPath);
  }
}

function renderTreeItem(item: TreeItem, container: HTMLElement) {
  const nodeEl = document.createElement('div');
  nodeEl.className = 'tree-node';

  const rowEl = document.createElement('div');
  rowEl.className = 'tree-row';
  rowEl.setAttribute('data-id', item.fullName);

  if (item.isPackage) {
    const chevronEl = document.createElement('div');
    chevronEl.className = 'tree-chevron';
    const isCollapsed = !expandedPackages.has(item.fullName);
    if (isCollapsed) {
      chevronEl.classList.add('collapsed');
    }
    chevronEl.innerHTML = `
      <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
        <polyline points="6 9 12 15 18 9"></polyline>
      </svg>
    `;
    rowEl.appendChild(chevronEl);

    const iconEl = document.createElement('div');
    iconEl.className = 'tree-icon';
    const pkgColor = packageColorMap[item.fullName] || 'var(--primary)';
    iconEl.innerHTML = `
      <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="color: ${pkgColor};">
        <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path>
      </svg>
    `;
    rowEl.appendChild(iconEl);

    const labelEl = document.createElement('span');
    labelEl.className = 'tree-label';
    labelEl.innerText = item.name;
    rowEl.appendChild(labelEl);

    nodeEl.appendChild(rowEl);

    const childrenEl = document.createElement('div');
    childrenEl.className = 'tree-children';
    if (isCollapsed) {
      childrenEl.classList.add('collapsed');
    }
    item.children.forEach(child => renderTreeItem(child, childrenEl));
    nodeEl.appendChild(childrenEl);

    rowEl.addEventListener('click', (e) => {
      e.stopPropagation();
      toggleFolder(item.fullName, rowEl, childrenEl);
    });

  } else {
    const spacer = document.createElement('div');
    spacer.className = 'tree-chevron-empty';
    rowEl.appendChild(spacer);

    const iconEl = document.createElement('span');
    iconEl.className = 'type-mini-badge';
    const cType = item.classType || 'CLASS';
    const color = TYPE_COLORS[cType] || 'var(--color-class)';
    iconEl.style.border = `1px solid ${color}`;
    iconEl.style.color = color;
    iconEl.innerText = TYPE_CHARS[cType] || 'C';
    rowEl.appendChild(iconEl);

    const labelEl = document.createElement('span');
    labelEl.className = 'tree-label';
    labelEl.innerText = item.name;
    rowEl.appendChild(labelEl);

    nodeEl.appendChild(rowEl);

    if (selectedNodeId === item.fullName) {
      rowEl.classList.add('selected');
    }

    rowEl.addEventListener('click', (e) => {
      e.stopPropagation();
      selectNode(item.fullName);
      if (network) {
        network.selectNodes([item.fullName]);
        network.focus(item.fullName, { animation: true });
      }
    });
  }

  container.appendChild(nodeEl);
}

function syncTreeSelection(selectedId: string | null) {
  document.querySelectorAll('.tree-row.selected').forEach(el => el.classList.remove('selected'));

  if (!selectedId) return;

  const selectedRow = document.querySelector(`.tree-row[data-id="${CSS.escape(selectedId)}"]`) as HTMLElement | null;
  if (selectedRow) {
    selectedRow.classList.add('selected');

    let parent = selectedRow.parentElement;
    while (parent) {
      const childrenContainer = parent.querySelector(':scope > .tree-children') as HTMLElement | null;
      const row = parent.querySelector(':scope > .tree-row') as HTMLElement | null;
      if (childrenContainer && row && childrenContainer.classList.contains('collapsed')) {
        const pkgPath = row.getAttribute('data-id');
        if (pkgPath) {
          expandedPackages.add(pkgPath);
          childrenContainer.classList.remove('collapsed');
          const chevron = row.querySelector('.tree-chevron');
          if (chevron) {
            chevron.classList.remove('collapsed');
          }
        }
      }
      parent = parent.parentElement ? parent.parentElement.closest('.tree-node') : null;
    }

    const container = document.getElementById('package-tree-container');
    if (container) {
      const containerRect = container.getBoundingClientRect();
      const rowRect = selectedRow.getBoundingClientRect();
      if (rowRect.top < containerRect.top || rowRect.bottom > containerRect.bottom) {
        selectedRow.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      }
    }
  }
}

// ─── Init ────────────────────────────────────────────────────
function init() {
  nodesDataset = new vis.DataSet();
  edgesDataset = new vis.DataSet();

  // Expand all packages by default initially
  uniquePackages.forEach(pkg => {
    expandedPackages.add(pkg);
    const parts = pkg.split('.');
    let path = '';
    parts.forEach(part => {
      path = path ? `${path}.${part}` : part;
      expandedPackages.add(path);
    });
  });

  renderPackageLegend();
  renderData();

  const container = document.getElementById('network-container');
  const data = { nodes: nodesDataset, edges: edgesDataset };

  const options = {
    nodes: {
      shape: 'box',
      margin: { top: 10, bottom: 10, left: 16, right: 16 },
      font: { color: '#cdd6f4', face: 'Outfit', size: 14, bold: { color: '#ffffff' } },
      borderWidth: 2,
      shadow: true
    },
    edges: {
      arrows: { to: { enabled: true, scaleFactor: 0.8 } },
      smooth: { type: 'cubicBezier', forceDirection: 'none', roundness: 0.3 },
      color: { color: 'rgba(88, 91, 112, 0.75)', highlight: '#cba6f7', hover: '#a6adc8' },
      width: 1.5
    },
    physics: {
      solver: 'forceAtlas2Based',
      forceAtlas2Based: {
        gravitationalConstant: -80,
        centralGravity: 0.008,
        springLength: 160,
        springConstant: 0.06,
        damping: 0.45,
        avoidOverlap: 0.3
      },
      stabilization: { iterations: 250, fit: true }
    },
    interaction: {
      hover: true,
      tooltipDelay: 300,
      selectConnectedEdges: false
    }
  };

  network = new vis.Network(container, data, options);

  // Click: ignore anchor nodes
  network.on('click', function(params: any) {
    if (params.nodes.length > 0) {
      const clicked = params.nodes[0];
      if (isAnchor(clicked)) return; // ignore anchor clicks
      selectNode(clicked);
      network.focus(clicked, {animation: true});
    } else {
      deselectNode();
    }
  });

  // Search
  const searchInput = document.getElementById('class-search') as HTMLInputElement | null;
  if (searchInput) {
    searchInput.addEventListener('input', function(e: any) {
      activeFilters.search = e.target.value.toLowerCase().trim();
      renderData();
    });
  }

  // Type filter checkboxes
  document.querySelectorAll('#type-filters input[data-type]').forEach(cb => {
    cb.addEventListener('change', function(e: any) {
      const type = e.target.getAttribute('data-type');
      if (type) {
        if (e.target.checked) activeFilters.types.add(type);
        else activeFilters.types.delete(type);
        renderData();
      }
    });
  });
}

// ─── Package Legend ──────────────────────────────────────────
function renderPackageLegend() {
  const container = document.getElementById('package-legend');
  if (!container) return;
  container.innerHTML = '';

  uniquePackages.forEach(pkg => {
    const color = packageColorMap[pkg];
    const label = document.createElement('label');
    label.className = 'checkbox-label';

    const cb = document.createElement('input');
    cb.type = 'checkbox';
    cb.checked = activeFilters.packages.has(pkg);
    cb.addEventListener('change', function(e: any) {
      if (e.target.checked) activeFilters.packages.add(pkg);
      else activeFilters.packages.delete(pkg);
      renderData();
    });

    const dot = document.createElement('span');
    dot.className = 'badge-dot';
    dot.style.backgroundColor = color;

    const text = document.createElement('span');
    text.style.fontSize = '13px';
    text.style.overflow = 'hidden';
    text.style.textOverflow = 'ellipsis';
    text.style.whiteSpace = 'nowrap';
    text.innerText = pkg;

    label.appendChild(cb);
    label.appendChild(dot);
    label.appendChild(text);
    container.appendChild(label);
  });
}

// ─── Render Data (with anchor nodes) ─────────────────────────
function renderData() {
  // 1. Filter class nodes
  const filteredNodes = rawNodes.filter(node => {
    const matchesType = activeFilters.types.has(node.type);
    const matchesPkg  = activeFilters.packages.has(node.packageName || '<default>');
    const matchesSearch = node.label.toLowerCase().includes(activeFilters.search) ||
                          node.id.toLowerCase().includes(activeFilters.search);
    return matchesType && matchesPkg && matchesSearch;
  });

  const filteredIds = new Set(filteredNodes.map(n => n.id));

  // 2. Filter real edges
  const filteredEdges = rawEdges.filter(e => filteredIds.has(e.from) && filteredIds.has(e.to));

  // 3. Compute which packages are active (have at least one visible class)
  const activePackages = new Set<string>();
  filteredNodes.forEach(n => activePackages.add(n.packageName || '<default>'));

  // 4. Build anchor nodes (one per active package, pre-positioned on a circle)
  const anchorNodes: any[] = [];
  const anchorEdges: any[] = [];
  const pkgArray = Array.from(activePackages).sort();
  const radius = Math.max(350, pkgArray.length * 90);

  pkgArray.forEach((pkg, i) => {
    const anchorId = ANCHOR_PREFIX + pkg;
    const angle = (2 * Math.PI * i) / pkgArray.length;
    const x = radius * Math.cos(angle);
    const y = radius * Math.sin(angle);
    const color = packageColorMap[pkg] || '#585b70';

    // Semi-visible label anchor: faint package name floating as center of gravity
    const shortLabel = pkg.split('.').pop() || pkg;
    anchorNodes.push({
      id: anchorId,
      label: shortLabel,
      x: x,
      y: y,
      shape: 'text',
      font: { color: color, size: 22, face: 'Outfit', strokeWidth: 0, vadjust: 0, bold: { color: color } },
      mass: 40,
      fixed: true,
      physics: true,
      opacity: 0.2,
      // No border, no background, no shadow — just floating text
      color: { border: 'transparent', background: 'transparent', highlight: { border: 'transparent', background: 'transparent' } },
      shadow: false,
      chosen: false,
      // Mark as anchor
      isAnchor: true
    });
  });

  // 5. Build class nodes with package-colored border
  const formattedNodes = filteredNodes.map(node => {
    const pkg = node.packageName || '<default>';
    const pkgColor = packageColorMap[pkg] || '#89b4fa';

    // Subtle background tint per class type
    let bg = '#1e1e2e';
    if (node.type === 'DATA')       bg = '#1e2821';
    else if (node.type === 'INTERFACE')  bg = '#251e2a';
    else if (node.type === 'ENUM')       bg = '#28221c';
    else if (node.type === 'ANNOTATION') bg = '#2a1e20';
    else if (node.type === 'BROADCAST')  bg = '#28251b';

    // Type prefix in label
    let prefix = '';
    if (node.type === 'INTERFACE')  prefix = '\u00abI\u00bb ';
    else if (node.type === 'ENUM')       prefix = '\u00abE\u00bb ';
    else if (node.type === 'ANNOTATION') prefix = '\u00abA\u00bb ';
    else if (node.type === 'DATA')       prefix = '\u00abD\u00bb ';
    else if (node.type === 'BROADCAST')  prefix = '\u00abB\u00bb ';

    return {
      id: node.id,
      label: prefix + node.label,
      packageName: node.packageName,
      type: node.type,
      implementedInterfaces: node.implementedInterfaces,
      color: {
        border: pkgColor,
        background: bg,
        highlight: { border: '#b4befe', background: '#313244' }
      },
      isAnchor: false
    };
  });

  // 6. Build invisible spring edges: class → its package anchor
  filteredNodes.forEach(node => {
    const pkg = node.packageName || '<default>';
    const anchorId = ANCHOR_PREFIX + pkg;
    if (activePackages.has(pkg)) {
      anchorEdges.push({
        id: '__spring__' + node.id,
        from: node.id,
        to: anchorId,
        // Invisible rigid spring
        hidden: true,
        physics: true,
        length: 80,
        // Override spring to be very rigid
        smooth: false
      });
    }
  });

  // 7. Format real dependency edges (weaker springs, visible)
  const formattedEdges = filteredEdges.map(edge => ({
    id: edge.from + '->' + edge.to,
    from: edge.from,
    to: edge.to,
    dashes: edge.type === 'implements',
    length: 280
  }));

  // 8. Merge and apply
  nodesDataset.clear();
  nodesDataset.add(anchorNodes);
  nodesDataset.add(formattedNodes);

  edgesDataset.clear();
  edgesDataset.add(anchorEdges);
  edgesDataset.add(formattedEdges);

  const statsLabel = document.getElementById('stats-label');
  if (statsLabel) {
    statsLabel.innerText = 'Nodes: ' + formattedNodes.length + ' | Edges: ' + formattedEdges.length;
  }

  if (selectedNodeId && !filteredIds.has(selectedNodeId)) {
    deselectNode();
  }

  // Render package structure tree
  const treeContainer = document.getElementById('package-tree-container');
  if (treeContainer) {
    treeContainer.innerHTML = '';
    const treeData = buildTree(filteredNodes);
    if (treeData.length === 0) {
      const emptyMsg = document.createElement('div');
      emptyMsg.style.fontSize = '12px';
      emptyMsg.style.color = 'var(--text-muted)';
      emptyMsg.style.fontStyle = 'italic';
      emptyMsg.style.padding = '8px';
      emptyMsg.innerText = 'No matches found';
      treeContainer.appendChild(emptyMsg);
    } else {
      treeData.forEach(item => renderTreeItem(item, treeContainer));
    }
  }

  // Sync tree selection
  syncTreeSelection(selectedNodeId);
}

// ─── Select Node ─────────────────────────────────────────────
function selectNode(nodeId: string) {
  if (isAnchor(nodeId)) return;
  selectedNodeId = nodeId;
  const node = rawNodes.find(n => n.id === nodeId);
  if (!node) return;

  const noSelectionMsg = document.getElementById('no-selection-msg');
  if (noSelectionMsg) noSelectionMsg.style.display = 'none';

  const card = document.getElementById('details-card');
  if (card) card.style.display = 'flex';

  const detName = document.getElementById('det-name');
  if (detName) detName.innerText = node.label;

  const detPackage = document.getElementById('det-package');
  if (detPackage) detPackage.innerText = node.packageName || '<default package>';

  const typeBadge = document.getElementById('det-type');
  if (typeBadge) {
    typeBadge.innerText = node.type.replace('_', ' ');
    typeBadge.style.backgroundColor = TYPE_COLORS[node.type] || 'var(--color-class)';
    typeBadge.style.color = '#11121d';
  }

  // Interfaces
  const ifContainer = document.getElementById('det-interfaces-container');
  const ifList = document.getElementById('det-interfaces');
  if (ifList && ifContainer) {
    ifList.innerHTML = '';
    if (node.implementedInterfaces && node.implementedInterfaces.length > 0) {
      ifContainer.style.display = 'flex';
      node.implementedInterfaces.forEach(iface => {
        const li = document.createElement('li');
        li.innerText = iface;
        ifList.appendChild(li);
      });
    } else {
      ifContainer.style.display = 'none';
    }
  }

  // Outgoing
  const depList = document.getElementById('det-dependencies');
  if (depList) {
    depList.innerHTML = '';
    const outgoing = rawEdges.filter(e => e.from === nodeId);
    if (outgoing.length > 0) {
      outgoing.forEach(e => {
        const li = document.createElement('li');
        li.innerText = e.to.split('.').pop() + ' (' + e.to + ')';
        li.style.cursor = 'pointer';
        li.onclick = () => { selectNode(e.to); network.selectNodes([e.to]); network.focus(e.to, {animation: true}); };
        depList.appendChild(li);
      });
    } else {
      const li = document.createElement('li');
      li.innerText = 'None';
      li.style.fontStyle = 'italic';
      depList.appendChild(li);
    }
  }

  // Incoming
  const incList = document.getElementById('det-incoming');
  if (incList) {
    incList.innerHTML = '';
    const incoming = rawEdges.filter(e => e.to === nodeId);
    if (incoming.length > 0) {
      incoming.forEach(e => {
        const li = document.createElement('li');
        li.innerText = e.from.split('.').pop() + ' (' + e.from + ')';
        li.style.cursor = 'pointer';
        li.onclick = () => { selectNode(e.from); network.selectNodes([e.from]); network.focus(e.from, {animation: true}); };
        incList.appendChild(li);
      });
    } else {
      const li = document.createElement('li');
      li.innerText = 'None';
      li.style.fontStyle = 'italic';
      incList.appendChild(li);
    }
  }

  highlightNodeNetwork(nodeId);
  syncTreeSelection(nodeId);
}

function deselectNode() {
  selectedNodeId = null;
  const noSelectionMsg = document.getElementById('no-selection-msg');
  if (noSelectionMsg) noSelectionMsg.style.display = 'block';

  const card = document.getElementById('details-card');
  if (card) card.style.display = 'none';

  resetFocus();
  syncTreeSelection(null);
}

// ─── Highlight ───────────────────────────────────────────────
function highlightNodeNetwork(nodeId: string) {
  const connectedNodeIds = new Set<string>();
  connectedNodeIds.add(nodeId);

  const allEdges = edgesDataset.get();
  allEdges.forEach((edge: any) => {
    if (edge.hidden) return; // skip anchor springs
    if (edge.from === nodeId) connectedNodeIds.add(edge.to);
    else if (edge.to === nodeId) connectedNodeIds.add(edge.from);
  });

  // Dim non-connected class nodes, leave anchors alone
  const allNodes = nodesDataset.get();
  const updatedNodes = allNodes.filter((n: any) => !n.isAnchor).map((node: any) => {
    const pkg = node.packageName || '<default>';
    const pkgColor = packageColorMap[pkg] || '#89b4fa';
    const isConnected = connectedNodeIds.has(node.id);

    let bg = '#1e1e2e';
    if (node.type === 'DATA')       bg = '#1e2821';
    else if (node.type === 'INTERFACE')  bg = '#251e2a';
    else if (node.type === 'ENUM')       bg = '#28221c';
    else if (node.type === 'ANNOTATION') bg = '#2a1e20';
    else if (node.type === 'BROADCAST')  bg = '#28251b';

    return {
      id: node.id,
      color: { border: pkgColor, background: bg, opacity: isConnected ? 1.0 : 0.15 },
      font: { color: isConnected ? '#cdd6f4' : 'rgba(205, 214, 244, 0.15)' }
    };
  });

  // Dim anchor labels for packages that have no connected node
  const connectedPkgs = new Set<string>();
  allNodes.filter((n: any) => !n.isAnchor && connectedNodeIds.has(n.id)).forEach((n: any) => {
    connectedPkgs.add(n.packageName || '<default>');
  });
  const updatedAnchors = allNodes.filter((n: any) => n.isAnchor).map((anchor: any) => {
    const pkg = anchor.id.replace(ANCHOR_PREFIX, '');
    const isRelevant = connectedPkgs.has(pkg);
    return {
      id: anchor.id,
      opacity: isRelevant ? 0.3 : 0.05
    };
  });

  nodesDataset.update(updatedNodes.concat(updatedAnchors));

  // Dim non-connected visible edges
  const updatedEdges = allEdges.filter((e: any) => !e.hidden).map((edge: any) => {
    const isConnected = edge.from === nodeId || edge.to === nodeId;
    return {
      id: edge.id,
      color: {
        color: isConnected ? '#cba6f7' : '#313244',
        opacity: isConnected ? 1.0 : 0.1
      }
    };
  });
  edgesDataset.update(updatedEdges);
}

// ─── Reset Focus ─────────────────────────────────────────────
function resetFocus() {
  const allNodes = nodesDataset.get();

  // Restore class nodes
  const updatedNodes = allNodes.filter((n: any) => !n.isAnchor).map((node: any) => {
    const pkg = node.packageName || '<default>';
    const pkgColor = packageColorMap[pkg] || '#89b4fa';

    let bg = '#1e1e2e';
    if (node.type === 'DATA')       bg = '#1e2821';
    else if (node.type === 'INTERFACE')  bg = '#251e2a';
    else if (node.type === 'ENUM')       bg = '#28221c';
    else if (node.type === 'ANNOTATION') bg = '#2a1e20';
    else if (node.type === 'BROADCAST')  bg = '#28251b';

    return {
      id: node.id,
      color: { border: pkgColor, background: bg, highlight: { border: '#b4befe', background: '#313244' } },
      font: { color: '#cdd6f4' }
    };
  });

  // Restore anchors
  const updatedAnchors = allNodes.filter((n: any) => n.isAnchor).map((anchor: any) => ({
    id: anchor.id,
    opacity: 0.2
  }));

  nodesDataset.update(updatedNodes.concat(updatedAnchors));

  // Restore edges
  const allEdges = edgesDataset.get();
  const updatedEdges = allEdges.filter((e: any) => !e.hidden).map((edge: any) => ({
    id: edge.id,
    color: { color: '#585b70', highlight: '#cba6f7', hover: '#a6adc8', opacity: 0.8 }
  }));
  edgesDataset.update(updatedEdges);

  if (selectedNodeId) {
    network.selectNodes([selectedNodeId]);
    highlightNodeNetwork(selectedNodeId);
  } else {
    network.unselectNodes();
  }
}

// @ts-ignore
window.resetFocus = resetFocus;
// @ts-ignore
window.network = network;

window.onload = init;
