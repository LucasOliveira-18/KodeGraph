package kodegraph.exporter.html

import kodegraph.exporter.GraphExporter
import kodegraph.model.KGClass
import kodegraph.model.KGClassType
import kodegraph.model.KGraph

class HtmlGraphExporter : GraphExporter {

    override fun export(graph: KGraph): String {
        val classes = graph.classes
        val byFqName = classes.associateBy { it.fqName }
        val bySimpleName = classes.groupBy { it.simpleName }

        val jsonNodes = classes.joinToString(separator = ",\n") { clazz ->
            val type = clazz.type.name
            val packageName = clazz.packageName
            val fqName = escapeJson(clazz.fqName)
            val simpleName = escapeJson(clazz.simpleName)
            val implemented = clazz.implementedInterfaces.joinToString(prefix = "[", postfix = "]") { "\"${escapeJson(it)}\"" }
            """      { "id": "$fqName", "label": "$simpleName", "packageName": "$packageName", "type": "$type", "implementedInterfaces": $implemented }"""
        }

        val jsonEdges = mutableListOf<String>()
        classes.forEach { clazz ->
            // Interfaces implemented
            clazz.implementedInterfaces.forEach { iface ->
                resolve(iface, clazz.packageName, byFqName, bySimpleName)?.let { target ->
                    jsonEdges.add(
                        """      { "from": "${escapeJson(clazz.fqName)}", "to": "${escapeJson(target.fqName)}", "label": "implements", "type": "implements" }"""
                    )
                }
            }
            // Outgoing class dependencies
            clazz.dependencies.forEach { dep ->
                resolve(dep.type, clazz.packageName, byFqName, bySimpleName)?.let { target ->
                    jsonEdges.add(
                        """      { "from": "${escapeJson(clazz.fqName)}", "to": "${escapeJson(target.fqName)}", "label": "depends", "type": "depends" }"""
                    )
                }
            }
        }
        val jsonEdgesStr = jsonEdges.distinct().joinToString(separator = ",\n")

        return getHtmlTemplate(jsonNodes, jsonEdgesStr)
    }

    private fun resolve(
        typeName: String,
        currentPackage: String,
        byFqName: Map<String, KGClass>,
        bySimpleName: Map<String, List<KGClass>>
    ): KGClass? {
        byFqName[typeName]?.let { return it }
        if (currentPackage.isNotEmpty()) {
            byFqName["$currentPackage.$typeName"]?.let { return it }
        }
        return bySimpleName[typeName]?.firstOrNull()
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun getHtmlTemplate(nodesJson: String, edgesJson: String): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>KodeGraph - Interactive Dependency Visualizer</title>
  <link rel="preconnect" href="https://fonts.googleapis.br">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
  <script type="text/javascript" src="https://unpkg.com/vis-network/standalone/umd/vis-network.min.js"></script>
  <style>
    :root {
      --bg-dark: #11121d;
      --bg-surface: #1e1e2e;
      --bg-surface-hover: #2b2b3c;
      --text-main: #cdd6f4;
      --text-muted: #a6adc8;
      --border-color: #313244;
      --primary: #cba6f7;
      --primary-hover: #b4befe;

      --color-class: #89b4fa;
      --color-data: #a6e3a1;
      --color-interface: #cba6f7;
      --color-enum: #fab387;
      --color-annotation: #f38ba8;
      --color-broadcast: #f9e2af;
    }

    * {
      box-sizing: border-box;
      margin: 0;
      padding: 0;
    }

    body {
      font-family: 'Outfit', sans-serif;
      background-color: var(--bg-dark);
      color: var(--text-main);
      height: 100vh;
      overflow: hidden;
      display: flex;
    }

    /* Sidebar controls */
    aside {
      width: 380px;
      background-color: var(--bg-surface);
      border-right: 1px solid var(--border-color);
      display: flex;
      flex-direction: column;
      height: 100%;
      z-index: 10;
      box-shadow: 5px 0 25px rgba(0, 0, 0, 0.5);
    }

    header {
      padding: 24px;
      border-bottom: 1px solid var(--border-color);
    }

    h1 {
      font-size: 24px;
      font-weight: 700;
      color: var(--primary);
      margin-bottom: 4px;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .subtitle {
      font-size: 13px;
      color: var(--text-muted);
    }

    .scroll-container {
      flex: 1;
      overflow-y: auto;
      padding: 24px;
      display: flex;
      flex-direction: column;
      gap: 24px;
    }

    .section-title {
      font-size: 14px;
      text-transform: uppercase;
      letter-spacing: 1px;
      color: var(--primary);
      margin-bottom: 12px;
      font-weight: 600;
    }

    /* Form controls */
    .search-box {
      position: relative;
    }

    .search-box input {
      width: 100%;
      padding: 12px 16px;
      background-color: var(--bg-dark);
      border: 1px solid var(--border-color);
      border-radius: 8px;
      color: var(--text-main);
      font-family: inherit;
      font-size: 14px;
      transition: all 0.2s ease;
    }

    .search-box input:focus {
      outline: none;
      border-color: var(--primary);
      box-shadow: 0 0 0 2px rgba(203, 166, 247, 0.25);
    }

    .filter-group {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .checkbox-label {
      display: flex;
      align-items: center;
      gap: 10px;
      cursor: pointer;
      font-size: 14px;
      user-select: none;
      padding: 4px 0;
    }

    .checkbox-label input {
      accent-color: var(--primary);
      width: 16px;
      height: 16px;
    }

    .badge-dot {
      display: inline-block;
      width: 8px;
      height: 8px;
      border-radius: 50%;
    }

    .type-mini-badge {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 20px;
      height: 20px;
      border-radius: 4px;
      font-size: 10px;
      font-weight: 700;
      background-color: var(--bg-dark);
    }

    /* Details Panel */
    .details-card {
      background-color: var(--bg-dark);
      border: 1px solid var(--border-color);
      border-radius: 12px;
      padding: 20px;
      display: none;
      flex-direction: column;
      gap: 16px;
      animation: fadeIn 0.3s ease;
    }

    @keyframes fadeIn {
      from { opacity: 0; transform: translateY(10px); }
      to { opacity: 1; transform: translateY(0); }
    }

    .details-header {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .class-name {
      font-size: 18px;
      font-weight: 600;
      word-break: break-all;
    }

    .class-package {
      font-family: 'JetBrains Mono', monospace;
      font-size: 12px;
      color: var(--text-muted);
      word-break: break-all;
    }

    .class-type-badge {
      display: inline-block;
      padding: 4px 8px;
      border-radius: 6px;
      font-size: 11px;
      font-weight: 600;
      text-transform: uppercase;
      align-self: flex-start;
      margin-top: 4px;
    }

    .meta-list {
      display: flex;
      flex-direction: column;
      gap: 12px;
      font-size: 13px;
    }

    .meta-item {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .meta-label {
      color: var(--text-muted);
      font-size: 11px;
      text-transform: uppercase;
      font-weight: 600;
    }

    .meta-value-list {
      list-style-type: none;
      display: flex;
      flex-direction: column;
      gap: 4px;
      max-height: 150px;
      overflow-y: auto;
      padding-right: 4px;
    }

    .meta-value-list li {
      background-color: var(--bg-surface-hover);
      padding: 6px 10px;
      border-radius: 6px;
      word-break: break-all;
      font-family: 'JetBrains Mono', monospace;
      font-size: 11px;
    }

    /* Main visualizer canvas */
    main {
      flex: 1;
      height: 100%;
      position: relative;
      background: radial-gradient(circle, #1a1b2a 0%, var(--bg-dark) 100%);
    }

    #network-container {
      width: 100%;
      height: 100%;
    }

    .floating-hud {
      position: absolute;
      bottom: 24px;
      right: 24px;
      background-color: rgba(30, 30, 46, 0.85);
      backdrop-filter: blur(10px);
      border: 1px solid var(--border-color);
      padding: 12px 16px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      gap: 16px;
      z-index: 5;
      font-size: 13px;
    }

    .btn {
      background-color: var(--primary);
      color: var(--bg-dark);
      border: none;
      padding: 8px 14px;
      border-radius: 6px;
      font-weight: 600;
      font-family: inherit;
      cursor: pointer;
      transition: background-color 0.2s ease;
      font-size: 12px;
    }

    .btn:hover {
      background-color: var(--primary-hover);
    }

    .btn-secondary {
      background-color: transparent;
      color: var(--text-main);
      border: 1px solid var(--border-color);
    }

    .btn-secondary:hover {
      background-color: var(--bg-surface-hover);
      border-color: var(--text-muted);
    }

    /* Custom scrollbar */
    ::-webkit-scrollbar {
      width: 6px;
      height: 6px;
    }
    ::-webkit-scrollbar-track {
      background: transparent;
    }
    ::-webkit-scrollbar-thumb {
      background: var(--border-color);
      border-radius: 4px;
    }
    ::-webkit-scrollbar-thumb:hover {
      background: var(--text-muted);
    }
  </style>
</head>
<body>

  <!-- Controls Sidebar -->
  <aside>
    <header>
      <h1>KodeGraph 🔍</h1>
      <div class="subtitle">Interactive Dependency Visualizer</div>
    </header>

    <div class="scroll-container">
      <!-- Search -->
      <div>
        <div class="section-title">Search</div>
        <div class="search-box">
          <input type="text" id="class-search" placeholder="Type a class or interface name...">
        </div>
      </div>

      <!-- Class Type Filters -->
      <div>
        <div class="section-title">Class Types</div>
        <div class="filter-group" id="type-filters">
          <label class="checkbox-label">
            <input type="checkbox" checked data-type="CLASS">
            <span class="type-mini-badge" style="border: 1px solid var(--color-class); color: var(--color-class)">C</span>
            Class
          </label>
          <label class="checkbox-label">
            <input type="checkbox" checked data-type="DATA">
            <span class="type-mini-badge" style="border: 1px solid var(--color-data); color: var(--color-data)">D</span>
            Data Class
          </label>
          <label class="checkbox-label">
            <input type="checkbox" checked data-type="INTERFACE">
            <span class="type-mini-badge" style="border: 1px solid var(--color-interface); color: var(--color-interface)">I</span>
            Interface
          </label>
          <label class="checkbox-label">
            <input type="checkbox" checked data-type="ENUM">
            <span class="type-mini-badge" style="border: 1px solid var(--color-enum); color: var(--color-enum)">E</span>
            Enum
          </label>
          <label class="checkbox-label">
            <input type="checkbox" checked data-type="ANNOTATION">
            <span class="type-mini-badge" style="border: 1px solid var(--color-annotation); color: var(--color-annotation)">A</span>
            Annotation
          </label>
          <label class="checkbox-label">
            <input type="checkbox" checked data-type="BROADCAST">
            <span class="type-mini-badge" style="border: 1px solid var(--color-broadcast); color: var(--color-broadcast)">B</span>
            Broadcast Receiver
          </label>
        </div>
      </div>

      <!-- Packages Legend & Filter -->
      <div>
        <div class="section-title">Packages</div>
        <div class="filter-group" id="package-legend" style="max-height: 220px; overflow-y: auto; padding-right: 4px;">
          <!-- Dynamically generated -->
        </div>
      </div>

      <!-- Detail Card -->
      <div>
        <div class="section-title">Selected Node Details</div>
        <div id="no-selection-msg" style="font-size: 13px; color: var(--text-muted); font-style: italic;">
          Click a class node to inspect its dependencies and metadata.
        </div>
        <div class="details-card" id="details-card">
          <div class="details-header">
            <div class="class-name" id="det-name">MyClass</div>
            <div class="class-package" id="det-package">com.example.package</div>
            <span class="class-type-badge" id="det-type">class</span>
          </div>

          <div class="meta-list">
            <div class="meta-item" id="det-interfaces-container">
              <div class="meta-label">Implements</div>
              <ul class="meta-value-list" id="det-interfaces"></ul>
            </div>
            <div class="meta-item">
              <div class="meta-label">Outgoing Dependencies (Uses)</div>
              <ul class="meta-value-list" id="det-dependencies"></ul>
            </div>
            <div class="meta-item">
              <div class="meta-label">Incoming Dependencies (Used By)</div>
              <ul class="meta-value-list" id="det-incoming"></ul>
            </div>
          </div>
        </div>
      </div>
    </div>
  </aside>

  <!-- Network Canvas -->
  <main>
    <div id="network-container"></div>

    <div class="floating-hud">
      <div id="stats-label">Nodes: 0 | Edges: 0</div>
      <button class="btn btn-secondary" onclick="resetFocus()">Reset Focus</button>
      <button class="btn" onclick="network.fit({animation: true})">Fit View</button>
    </div>
  </main>

  <script>
    // ─── Injected Data ───────────────────────────────────────────
    const rawNodes = [
$nodesJson
    ];

    const rawEdges = [
$edgesJson
    ];

    // ─── Package Color Palette ───────────────────────────────────
    const PACKAGE_PALETTE = [
      '#89b4fa', '#a6e3a1', '#cba6f7', '#fab387',
      '#f38ba8', '#f9e2af', '#89dceb', '#94e2d5',
      '#eba0ac', '#b4befe', '#f5c2e7', '#74c7ec'
    ];

    // ─── Derive unique packages & assign colors ──────────────────
    const ANCHOR_PREFIX = '__pkg_anchor__';
    const packageColorMap = {};
    const uniquePackages = Array.from(
      new Set(rawNodes.map(n => n.packageName || '<default>'))
    ).sort();

    uniquePackages.forEach((pkg, i) => {
      packageColorMap[pkg] = PACKAGE_PALETTE[i % PACKAGE_PALETTE.length];
    });

    // ─── Type theme (for node border by class type) ──────────────
    const typeThemes = {
      'CLASS':      { color: { border: '#89b4fa', background: '#1e1e2e', highlight: { border: '#b4befe', background: '#313244' } } },
      'DATA':       { color: { border: '#a6e3a1', background: '#1e1e2e', highlight: { border: '#b4befe', background: '#313244' } } },
      'INTERFACE':  { color: { border: '#cba6f7', background: '#1e1e2e', highlight: { border: '#b4befe', background: '#313244' } } },
      'ENUM':       { color: { border: '#fab387', background: '#1e1e2e', highlight: { border: '#b4befe', background: '#313244' } } },
      'ANNOTATION': { color: { border: '#f38ba8', background: '#1e1e2e', highlight: { border: '#b4befe', background: '#313244' } } },
      'BROADCAST':  { color: { border: '#f9e2af', background: '#1e1e2e', highlight: { border: '#b4befe', background: '#313244' } } }
    };

    // ─── State ───────────────────────────────────────────────────
    let nodesDataset = new vis.DataSet();
    let edgesDataset = new vis.DataSet();
    let network = null;
    let selectedNodeId = null;

    let activeFilters = {
      search: '',
      types: new Set(['CLASS', 'DATA', 'INTERFACE', 'ENUM', 'ANNOTATION', 'BROADCAST']),
      packages: new Set(uniquePackages)
    };

    // Helper: is this an anchor node id?
    function isAnchor(id) {
      return typeof id === 'string' && id.startsWith(ANCHOR_PREFIX);
    }

    // ─── Init ────────────────────────────────────────────────────
    function init() {
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
      network.on('click', function(params) {
        if (params.nodes.length > 0) {
          const clicked = params.nodes[0];
          if (isAnchor(clicked)) return; // ignore anchor clicks
          selectNode(clicked);
        } else {
          deselectNode();
        }
      });

      // Search
      document.getElementById('class-search').addEventListener('input', function(e) {
        activeFilters.search = e.target.value.toLowerCase().trim();
        renderData();
      });

      // Type filter checkboxes
      document.querySelectorAll('#type-filters input[data-type]').forEach(cb => {
        cb.addEventListener('change', function(e) {
          const type = e.target.getAttribute('data-type');
          if (e.target.checked) activeFilters.types.add(type);
          else activeFilters.types.delete(type);
          renderData();
        });
      });
    }

    // ─── Package Legend ──────────────────────────────────────────
    function renderPackageLegend() {
      const container = document.getElementById('package-legend');
      container.innerHTML = '';

      uniquePackages.forEach(pkg => {
        const color = packageColorMap[pkg];
        const label = document.createElement('label');
        label.className = 'checkbox-label';

        const cb = document.createElement('input');
        cb.type = 'checkbox';
        cb.checked = activeFilters.packages.has(pkg);
        cb.addEventListener('change', function(e) {
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
      const activePackages = new Set();
      filteredNodes.forEach(n => activePackages.add(n.packageName || '<default>'));

      // 4. Build anchor nodes (one per active package, pre-positioned on a circle)
      const anchorNodes = [];
      const anchorEdges = [];
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
        const theme = typeThemes[node.type] || typeThemes['CLASS'];

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

      document.getElementById('stats-label').innerText =
        'Nodes: ' + formattedNodes.length + ' | Edges: ' + formattedEdges.length;

      if (selectedNodeId && !filteredIds.has(selectedNodeId)) {
        deselectNode();
      }
    }

    // ─── Select Node ─────────────────────────────────────────────
    function selectNode(nodeId) {
      if (isAnchor(nodeId)) return;
      selectedNodeId = nodeId;
      const node = rawNodes.find(n => n.id === nodeId);
      if (!node) return;

      document.getElementById('no-selection-msg').style.display = 'none';
      const card = document.getElementById('details-card');
      card.style.display = 'flex';

      document.getElementById('det-name').innerText = node.label;
      document.getElementById('det-package').innerText = node.packageName || '<default package>';

      const typeBadge = document.getElementById('det-type');
      typeBadge.innerText = node.type.replace('_', ' ');
      const colors = {
        'CLASS': 'var(--color-class)', 'DATA': 'var(--color-data)',
        'INTERFACE': 'var(--color-interface)', 'ENUM': 'var(--color-enum)',
        'ANNOTATION': 'var(--color-annotation)', 'BROADCAST': 'var(--color-broadcast)'
      };
      typeBadge.style.backgroundColor = colors[node.type] || 'var(--color-class)';
      typeBadge.style.color = '#11121d';

      // Interfaces
      const ifContainer = document.getElementById('det-interfaces-container');
      const ifList = document.getElementById('det-interfaces');
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

      // Outgoing
      const depList = document.getElementById('det-dependencies');
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

      // Incoming
      const incList = document.getElementById('det-incoming');
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

      highlightNodeNetwork(nodeId);
    }

    function deselectNode() {
      selectedNodeId = null;
      document.getElementById('no-selection-msg').style.display = 'block';
      document.getElementById('details-card').style.display = 'none';
      resetFocus();
    }

    // ─── Highlight ───────────────────────────────────────────────
    function highlightNodeNetwork(nodeId) {
      const connectedNodeIds = new Set();
      connectedNodeIds.add(nodeId);

      const allEdges = edgesDataset.get();
      allEdges.forEach(edge => {
        if (edge.hidden) return; // skip anchor springs
        if (edge.from === nodeId) connectedNodeIds.add(edge.to);
        else if (edge.to === nodeId) connectedNodeIds.add(edge.from);
      });

      // Dim non-connected class nodes, leave anchors alone
      const allNodes = nodesDataset.get();
      const updatedNodes = allNodes.filter(n => !n.isAnchor).map(node => {
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
      const connectedPkgs = new Set();
      allNodes.filter(n => !n.isAnchor && connectedNodeIds.has(n.id)).forEach(n => {
        connectedPkgs.add(n.packageName || '<default>');
      });
      const updatedAnchors = allNodes.filter(n => n.isAnchor).map(anchor => {
        const pkg = anchor.id.replace(ANCHOR_PREFIX, '');
        const isRelevant = connectedPkgs.has(pkg);
        return {
          id: anchor.id,
          opacity: isRelevant ? 0.3 : 0.05
        };
      });

      nodesDataset.update(updatedNodes.concat(updatedAnchors));

      // Dim non-connected visible edges
      const updatedEdges = allEdges.filter(e => !e.hidden).map(edge => {
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
      const updatedNodes = allNodes.filter(n => !n.isAnchor).map(node => {
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
      const updatedAnchors = allNodes.filter(n => n.isAnchor).map(anchor => ({
        id: anchor.id,
        opacity: 0.2
      }));

      nodesDataset.update(updatedNodes.concat(updatedAnchors));

      // Restore edges
      const allEdges = edgesDataset.get();
      const updatedEdges = allEdges.filter(e => !e.hidden).map(edge => ({
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

    window.onload = init;
  </script>
</body>
</html>
        """.trimIndent()
    }
}
