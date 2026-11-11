/* Project page "Next.js": headline tiles, the route tree of the app with a dot per route coloured by its issues,
   the client/server split, issues by rule and the Server Actions. Plain DOM, charts are inline SVG. */
window.registerExtension('nextjs/nextjs', function (options) {
  var base = options.baseUrl || window.baseUrl || '';
  var project = options.component.key;
  var branchLike = options.branchLike;
  // same rule as the SonarQube UI: a non-main branch by name, a pull request by key, the main branch with nothing
  var scope = {};
  if (branchLike && branchLike.isMain !== undefined && !branchLike.isMain) {
    scope.branch = branchLike.name;
  } else if (branchLike && branchLike.key !== undefined) {
    scope.pullRequest = branchLike.key;
  }
  var SVG = 'http://www.w3.org/2000/svg';
  // rules of both languages share names: the chart tags each rule with its repository's language
  var LANGS = {'nextjs-js': 'JS', 'nextjs-ts': 'TS'};
  var REPOSITORIES = Object.keys(LANGS);
  var AUTH_RULES = ['ServerActionWithoutAuth', 'RouteHandlerWithoutAuth'];
  var NO_DATA = 'Analyse the project with this plugin installed; it needs a package.json declaring next';
  var MEASURES = ['nx_routes', 'nx_client_modules', 'nx_server_modules', 'nx_shared_modules', 'nx_client_ratio',
    'nx_server_actions', 'nx_unprotected_endpoints', 'nx_dynamic_routes', 'nx_routes_json'];
  var NAMES = {nx_routes: 'Routes', nx_client_ratio: 'Client ratio', nx_unprotected_endpoints: 'Unprotected endpoints',
    nx_dynamic_routes: 'Dynamic routes', nx_client_modules: 'Client modules', nx_server_modules: 'Server modules',
    nx_shared_modules: 'Shared modules', nx_server_actions: 'Server Actions'};
  var TREE_ROWS = 60;

  function el(ns, tag, attrs, children) {
    var e = ns ? document.createElementNS(ns, tag) : document.createElement(tag);
    Object.keys(attrs || {}).forEach(function (k) {
      if (attrs[k] !== undefined && attrs[k] !== null) {
        e.setAttribute(k, attrs[k]);
      }
    });
    (children || []).forEach(function (c) {
      if (c !== null && c !== undefined) {
        e.appendChild(typeof c === 'object' ? c : document.createTextNode(String(c)));
      }
    });
    return e;
  }
  function h(tag, attrs, children) { return el(null, tag, attrs, children); }
  function s(tag, attrs, children) { return el(SVG, tag, attrs, children); }

  function fmt(v) {
    return Number(v).toLocaleString(undefined, {maximumFractionDigits: 1});
  }
  function plural(n, word) {
    return fmt(n) + ' ' + word + (n === 1 ? '' : 's');
  }
  function sum(parts) {
    return parts.reduce(function (a, p) { return a + p.value; }, 0);
  }

  function api(path, params, withScope) {
    var q = new URLSearchParams(Object.assign({}, withScope === false ? {} : scope, params));
    return fetch(base + '/api/' + path + '?' + q.toString(), {credentials: 'same-origin'}).then(function (r) {
      if (!r.ok) {
        throw new Error(path + ' answered HTTP ' + r.status);
      }
      return r.json();
    });
  }
  function page(path, params) {
    return base + '/' + path + '?' + new URLSearchParams(Object.assign({id: project}, scope, params)).toString();
  }
  function fileHref(file) {
    return page('code', {selected: project + ':' + file});
  }

  // the page follows SonarQube's light or dark theme: read the background the page sits on
  function dark(node) {
    for (; node && node.nodeType === 1; node = node.parentElement) {
      var c = (getComputedStyle(node).backgroundColor.match(/[\d.]+/g) || []).map(Number);
      if (c.length >= 3 && (c.length < 4 || c[3] > 0)) {
        return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2] < 128;
      }
    }
    return false;
  }

  // issues of a route: none, a few, many
  function issueColor(n) {
    return n === 0 ? 'var(--clean)' : n < 3 ? 'var(--orange)' : 'var(--red)';
  }

  function info(text) {
    return h('span', {class: 'nx-info', title: text, 'aria-label': text, role: 'img', tabindex: '0'}, ['i']);
  }
  function empty(word, hint) {
    return h('div', {class: 'nx-empty'}, [h('span', {class: 'nx-dash', 'aria-hidden': 'true'}, ['–']), word,
      hint ? info(hint) : null]);
  }
  function legend(parts, total) {
    return h('ul', {class: 'nx-legend'}, parts.map(function (p) {
      return h('li', {}, [h('span', {class: 'nx-sw', style: 'background:' + p.color}), p.label + ' ', h('b', {}, [fmt(p.value)]),
        total ? h('span', {class: 'nx-muted'}, [' ' + Math.round(p.value / total * 100) + '%']) : null]);
    }));
  }
  function dot(color, label) {
    return s('svg', {width: 10, height: 10, class: 'nx-inline', role: label ? 'img' : null, 'aria-label': label,
      'aria-hidden': label ? null : 'true'}, [s('circle', {cx: 5, cy: 5, r: 4.5, style: 'fill:' + color})]);
  }

  // ---- charts

  // horizontal bars, label above each bar, value at the bar end; items [{label, tag, value, href}]
  function hbars(items, w, o) {
    var row = 34;
    var plot = w - 48;
    var max = Math.max.apply(null, items.map(function (i) { return i.value; })) || 1;
    var chars = Math.max(Math.floor(w / 6.6), 12);
    var marks = items.map(function (it, i) {
      var y = i * row;
      var bw = Math.max(it.value / max * plot, 3);
      var tip = full(it) + ': ' + fmt(it.value);
      var room = chars - (it.tag ? it.tag.length + 2 : 0);
      var label = it.label.length > room ? it.label.slice(0, room - 1) + '…' : it.label;
      var kids = [s('title', {}, [tip]), s('rect', {class: 'nx-hit', x: 0, y: y, width: w, height: row, fill: 'transparent'}),
        s('text', {x: 0, y: y + 12, class: 'nx-label'}, [label, it.tag ? s('tspan', {dx: 6, class: 'nx-small'}, [it.tag]) : null]),
        s('rect', {x: 0, y: y + 17, width: bw, height: 10, rx: 2, style: 'fill:' + o.color}),
        s('text', {x: bw + 6, y: y + 26, class: 'nx-val'}, [fmt(it.value)])];
      return it.href ? s('a', {href: it.href, 'aria-label': tip}, kids) : s('g', {}, kids);
    });
    return s('svg', {width: w, height: items.length * row, role: 'group', 'aria-label': o.label}, marks);
  }
  function full(it) {
    return it.tag ? it.label + ' (' + it.tag + ')' : it.label;
  }

  function stack(parts, w, label) {
    var total = sum(parts) || 1;
    var x = 0;
    return s('svg', {width: w, height: 24, role: 'img', 'aria-label': label}, parts.filter(function (p) { return p.value > 0; })
      .map(function (p) {
        var pw = p.value / total * w;
        var r = s('rect', {x: x, y: 0, width: Math.max(pw - 2, 2), height: 24, rx: 3, style: 'fill:' + p.color},
          [s('title', {}, [p.label + ': ' + fmt(p.value)])]);
        x += pw;
        return r;
      }));
  }

  function meter(ratio, w, label) {
    return s('svg', {width: w, height: 8, role: 'img', 'aria-label': label}, [
      s('rect', {width: w, height: 8, rx: 4, style: 'fill:var(--track)'}),
      s('rect', {width: Math.max(Math.min(ratio, 1) * w, 4), height: 8, rx: 4, style: 'fill:var(--client)'})]);
  }

  // the URL tree: one node per path segment, routes hang on their node
  function buildTree(routes) {
    var root = {seg: '', children: {}, entries: []};
    routes.forEach(function (r) {
      var node = root;
      (r.path || '').split('/').filter(Boolean).forEach(function (seg) {
        node = node.children[seg] || (node.children[seg] = {seg: seg, children: {}, entries: []});
      });
      node.entries.push(r);
    });
    return root;
  }
  function kids(node) {
    // static segments first, then dynamic ones, each alphabetically
    return Object.keys(node.children).map(function (k) { return node.children[k]; }).sort(function (a, b) {
      var da = a.seg.charAt(0) === '[';
      var db = b.seg.charAt(0) === '[';
      return da !== db ? (da ? 1 : -1) : a.seg < b.seg ? -1 : 1;
    });
  }
  function flatten(tree) {
    var rows = [];
    (function walk(node, depth, guides, path) {
      rows.push({node: node, depth: depth, guides: guides, path: path || '/', hasKids: Object.keys(node.children).length > 0});
      var list = kids(node);
      list.forEach(function (k, i) {
        walk(k, depth + 1, guides.concat([i < list.length - 1]), path + '/' + k.seg);
      });
    })(tree, 0, [], '');
    return rows;
  }

  function routeTree(rows, w, maxClients) {
    var row = 26;
    var step = 18;
    var top = 20;
    var wide = w >= 520;
    // the columns follow the longest label (about 7 px a character), not the panel's right edge
    var labelEnd = Math.max.apply(null, rows.map(function (r) {
      var tags = r.node.entries.some(function (e) { return e.kind === 'route'; }) * 30 +
        r.node.entries.some(function (e) { return e.runtime === 'edge'; }) * 36;
      return r.depth * step + 18 + (r.depth ? r.node.seg.length : 1) * 7 + tags;
    }));
    var xIssues = wide ? Math.min(labelEnd + 60, w - 300) : w - 8;
    var xBar = xIssues + 40;
    var barW = wide ? Math.min(220, w - xBar - 30) : 0;
    var marks = [
      s('text', {x: xIssues, y: 11, 'text-anchor': 'end', class: 'nx-small'}, ['issues']),
      wide ? s('text', {x: xBar, y: 11, class: 'nx-small'}, ['client modules']) : null];
    rows.forEach(function (r, i) {
      var y = top + i * row;
      var mid = y + row / 2;
      var cx = r.depth * step + 6;
      var kids = [];
      // tree lines: the ancestors' verticals, the elbow to this node, the line down to its children
      r.guides.forEach(function (more, j) {
        var x = j * step + 6;
        if (j < r.guides.length - 1) {
          if (more) {
            kids.push(s('line', {x1: x, x2: x, y1: y, y2: y + row, class: 'nx-tree-line'}));
          }
        } else {
          kids.push(s('path', {d: 'M' + x + ' ' + y + 'V' + mid + 'H' + (cx - 5) + (more ? 'M' + x + ' ' + mid + 'V' + (y + row) : ''),
            class: 'nx-tree-line', fill: 'none'}));
        }
      });
      if (r.hasKids) {
        kids.push(s('line', {x1: cx, x2: cx, y1: mid, y2: y + row, class: 'nx-tree-line'}));
      }
      var entries = r.node.entries;
      var label = r.depth === 0 ? '/' : r.node.seg;
      var dyn = label.charAt(0) === '[';
      if (entries.length === 0) {
        kids.push(s('circle', {cx: cx, cy: mid, r: 2.5, class: 'nx-hollow'}),
          s('text', {x: cx + 10, y: mid + 4, class: 'nx-label nx-muted' + (dyn ? ' nx-dyn' : '')}, [label]));
        marks.push(s('g', {'aria-hidden': 'true'}, kids));
        return;
      }
      var issues = entries.reduce(function (a, e) { return a + (e.issues || 0); }, 0);
      var clients = entries.reduce(function (a, e) { return Math.max(a, e.clientModules || 0); }, 0);
      var api = entries.some(function (e) { return e.kind === 'route'; });
      var edge = entries.some(function (e) { return e.runtime === 'edge'; });
      var tags = (api ? ['API'] : []).concat(edge ? ['edge'] : []);
      var tip = r.path + ': ' + (api ? 'route handler' : 'page') + (edge ? ', edge runtime' : '') +
        (dyn || entries.some(function (e) { return e.dynamic; }) ? ', dynamic' : '') + ', ' + plural(clients, 'client module') +
        ', ' + plural(issues, 'issue');
      kids.unshift(s('title', {}, [tip]), s('rect', {class: 'nx-hit', x: 0, y: y, width: w, height: row, rx: 4, fill: 'transparent'}));
      kids.push(s('circle', {cx: cx, cy: mid, r: 6, class: 'nx-dot', style: 'fill:' + issueColor(issues)}),
        s('text', {x: cx + 12, y: mid + 4, class: 'nx-label' + (dyn ? ' nx-dyn' : '')}, [label].concat(tags.map(function (t) {
          return s('tspan', {dx: 8, class: 'nx-tag'}, [t]);
        }))),
        s('text', {x: xIssues, y: mid + 4, 'text-anchor': 'end', class: issues ? 'nx-val' : 'nx-small',
          style: issues ? 'fill:' + issueColor(issues) : null}, [issues ? fmt(issues) : '–']));
      if (wide) {
        var bw = clients ? Math.max(clients / (maxClients || 1) * barW, 3) : 0;
        kids.push(s('rect', {x: xBar, y: mid - 4, width: barW, height: 8, rx: 4, style: 'fill:var(--track)'}),
          bw ? s('rect', {x: xBar, y: mid - 4, width: bw, height: 8, rx: 4, style: 'fill:var(--client)'}) : null,
          s('text', {x: xBar + barW + 6, y: mid + 4, class: clients ? 'nx-val' : 'nx-small'}, [fmt(clients)]));
      }
      marks.push(s('a', {href: fileHref(entries[0].file), 'aria-label': tip}, kids));
    });
    return s('svg', {width: w, height: top + rows.length * row, role: 'group',
      'aria-label': 'Route tree: ' + rows.filter(function (r) { return r.node.entries.length; }).length + ' routes'}, marks);
  }

  // ---- layout

  var style = h('style', {}, [
    '.nx{width:100%;box-sizing:border-box;min-width:0;padding:0 20px 24px 20px;font-size:13px;color:inherit;' +
    '--blue:#2a78d6;--client:#1f9d8b;--server:#2a78d6;--shared:#a3a29c;--clean:#2a78d6;--orange:#e8a33d;--red:#d03b3b;' +
    '--dyn:#7b4fc9;--track:rgba(128,128,128,.2);--ok:#0b7a0b;--err:#d03b3b;--card:rgba(128,128,128,.06);--rule:rgba(128,128,128,.28)}' +
    '.nx.nx-dark{--blue:#4a94ec;--client:#35b8a5;--server:#4a94ec;--shared:#77766f;--clean:#4a94ec;--orange:#f0b45a;' +
    '--red:#ee6b6b;--dyn:#b294f0;--ok:#3fbf3f;--err:#ee6b6b}' +
    '.nx-head{display:flex;flex-wrap:wrap;align-items:center;gap:8px 16px;margin:16px 0 12px 0}' +
    '.nx h1{font-size:20px;margin:0}.nx h2{font-size:14px;font-weight:600;margin:0 0 12px 0}' +
    '.nx-tiles{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;list-style:none;padding:0;margin:0 0 12px 0}' +
    '.nx-main{display:grid;grid-template-columns:minmax(0,2fr) minmax(0,1fr);gap:12px;align-items:start}' +
    '.nx-side{display:grid;gap:12px;min-width:0}' +
    '.nx.nx-narrow .nx-main{grid-template-columns:minmax(0,1fr)}.nx.nx-narrow .nx-tiles{grid-template-columns:repeat(2,minmax(0,1fr))}' +
    '.nx-panel,.nx-tile{background:var(--card);border:1px solid var(--rule);border-radius:8px;padding:14px 16px;min-width:0}' +
    '.nx-tile{display:flex;flex-direction:column;gap:4px}.nx-tile .nx-name{font-size:12px;opacity:.8}' +
    '.nx-tile .nx-num{font-size:34px;font-weight:600;line-height:1.15}.nx-tile .nx-sub{font-size:12px;opacity:.7}' +
    '.nx-tile a{text-decoration:none}.nx-tile a:hover{text-decoration:underline}' +
    '.nx-bad{color:var(--err)}.nx-muted{opacity:.65}' +
    '.nx-empty{display:flex;align-items:center;gap:8px;opacity:.65;min-height:42px}.nx-dash{font-size:30px;font-weight:600}' +
    '.nx-info{display:inline-flex;align-items:center;justify-content:center;width:16px;height:16px;border-radius:50%;' +
    'border:1px solid currentColor;font:italic 600 11px Georgia,serif;cursor:help}' +
    '.nx svg{display:block;overflow:visible}.nx svg.nx-inline{display:inline-block;vertical-align:baseline;margin-right:6px}' +
    '.nx svg text{fill:currentColor}.nx-label{font-size:12px}.nx-val{font-size:12px;font-weight:600}' +
    '.nx-small{font-size:11px;opacity:.7}.nx-tag{font-size:10px;font-weight:600;opacity:.6;letter-spacing:.04em}' +
    '.nx svg text.nx-dyn{fill:var(--dyn);font-style:italic}' +
    '.nx-tree-line{stroke:currentColor;opacity:.25}.nx-hollow{fill:none;stroke:currentColor;opacity:.4}' +
    '.nx-dot{stroke:#fff;stroke-width:1.5}.nx.nx-dark .nx-dot{stroke:#1b1d23}' +
    '.nx svg a{cursor:pointer}.nx svg a:hover .nx-hit{fill:var(--track)}' +
    '.nx svg a:focus{outline:none}.nx svg a:focus-visible .nx-hit{stroke:var(--blue);stroke-width:2}' +
    '.nx-legend{list-style:none;padding:0;margin:10px 0 0 0;display:flex;flex-wrap:wrap;gap:4px 16px;font-size:12px}' +
    '.nx-sw{display:inline-block;width:10px;height:10px;border-radius:2px;margin-right:6px}' +
    '.nx-key{border-radius:50%}' +
    '.nx-chips{display:flex;flex-wrap:wrap;gap:6px;list-style:none;padding:0;margin:0}' +
    '.nx-chip{display:inline-flex;gap:6px;align-items:center;border:1px solid var(--rule);border-radius:999px;padding:3px 10px;font-size:12px}' +
    '.nx-chip b{font-weight:700}.nx-pass b{color:var(--ok)}.nx-fail{border-color:var(--err)}.nx-fail b{color:var(--err)}' +
    '.nx-actions{list-style:none;padding:0;margin:0;font-size:12px}.nx-actions li{margin:0 0 10px 0;min-width:0}' +
    '.nx-actions .nx-file{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}' +
    '.nx-fns{display:flex;flex-wrap:wrap;gap:4px;margin:4px 0 0 16px}' +
    '.nx-fn{font-family:ui-monospace,Menlo,Consolas,monospace;font-size:11px;background:var(--track);border-radius:4px;padding:1px 6px}' +
    '.nx-more{margin-top:8px;font:inherit;font-size:12px;color:inherit;background:none;border:1px solid var(--rule);border-radius:6px;' +
    'padding:4px 10px;cursor:pointer}' +
    '.nx a{color:inherit}'
  ]);
  var gate = h('ul', {class: 'nx-chips', 'aria-label': 'Quality gate'});
  var root = h('div', {class: 'nx'}, [style, h('div', {class: 'nx-head'}, [h('h1', {}, ['Next.js']), gate])]);
  var tiles = h('ul', {class: 'nx-tiles', 'aria-label': 'Headline measures'});
  var side = h('div', {class: 'nx-side'});
  var main = h('div', {class: 'nx-main'});
  // SonarQube keeps only the changed files of a pull request: the measures add up those, nx_routes_json is the whole app
  var whole = scope.pullRequest === undefined;
  if (!whole) {
    root.appendChild(h('p', {class: 'nx-muted', style: 'margin:0 0 12px 0'}, ['Pull request: the numbers and the client/server split count the files it ' +
      'changes; the routes and Server Actions panels show the whole app.']));
  }
  root.appendChild(tiles);
  root.appendChild(main);
  // SonarQube places extension pages in a grid of two columns: span both, or the page gets only the first one
  options.el.style.gridColumn = '1 / -1';
  options.el.style.width = '100%';
  options.el.appendChild(root);
  if (dark(options.el)) {
    root.classList.add('nx-dark');
  }

  // each panel draws for its current width and draws again when the page width changes
  var draws = [];
  function panel(title, parent) {
    var body = h('div', {}, [h('span', {class: 'nx-muted'}, ['…'])]);
    var node = h('section', {class: 'nx-panel', 'aria-label': title}, [h('h2', {}, [title]), body]);
    parent.appendChild(node);
    return {
      draw: function (fn) {
        var redraw = function () { body.replaceChildren.apply(body, [].concat(fn(Math.max(body.clientWidth, 200)))); };
        draws.push(redraw);
        redraw();
        return redraw;
      },
      empty: function (word, hint) { body.replaceChildren(empty(word, hint)); },
      fail: function (e) { body.replaceChildren(h('p', {role: 'alert', class: 'nx-muted'}, ['Cannot load: ' + e.message])); }
    };
  }
  var treePanel = panel('Routes', main);
  main.appendChild(side);
  var splitPanel = panel('Client / server split', side);
  var rulePanel = panel('Issues by rule', side);
  var actionPanel = panel('Server Actions', side);

  // no CSS container queries: SonarQube mounts the page in a grid column sized by its content, where inline-size
  // containment would shrink the page to its padding; a class set from the measured width does the same job
  var narrow = function () { root.classList.toggle('nx-narrow', root.clientWidth < 900); };
  narrow();
  var lastWidth = root.clientWidth;
  var observer = window.ResizeObserver ? new ResizeObserver(function () {
    if (Math.abs(root.clientWidth - lastWidth) > 16) {
      lastWidth = root.clientWidth;
      narrow();
      draws.forEach(function (d) { d(); });
    }
  }) : null;
  if (observer) {
    observer.observe(root);
  }

  // ---- data

  api('measures/component', {component: project, metricKeys: MEASURES.join(',')}).then(function (data) {
    var values = {};
    (data.component.measures || []).forEach(function (m) {
      var v = m.value !== undefined ? m.value : (m.period || (m.periods || [])[0] || {}).value;
      if (v !== undefined && v !== '') {
        values[m.metric] = m.metric === 'nx_routes_json' ? v : Number(v);
      }
    });
    var all = [];
    try {
      all = JSON.parse(values.nx_routes_json || '[]');
    } catch (e) {
      all = [];
    }
    var routes = all.filter(function (r) { return r.kind !== 'action'; });
    var actions = all.filter(function (r) { return r.kind === 'action'; });
    var handlers = routes.filter(function (r) { return r.kind === 'route'; }).length;

    // tiles
    var unprotected = values.nx_unprotected_endpoints;
    var authRules = [];
    REPOSITORIES.forEach(function (repo) {
      AUTH_RULES.forEach(function (r) { authRules.push(repo + ':' + r); });
    });
    var ratio = values.nx_client_ratio;
    [
      {key: 'nx_routes', sub: whole && routes.length ? plural(routes.length - handlers, 'page') + ' · ' + plural(handlers, 'API route') : null},
      {key: 'nx_client_ratio', unit: '%', extra: ratio === undefined ? null : meter(ratio / 100, 120, 'Client ratio ' + fmt(ratio) + '%'),
        sub: values.nx_client_modules !== undefined ? plural(values.nx_client_modules, 'client module') : null},
      {key: 'nx_unprotected_endpoints', bad: unprotected > 0, href: unprotected > 0 ?
        page('project/issues', {resolved: 'false', rules: authRules.join(',')}) : null,
      sub: values.nx_server_actions !== undefined ? plural(values.nx_server_actions, 'Server Action') +
        (whole ? ' · ' + plural(handlers, 'handler') : '') : null},
      {key: 'nx_dynamic_routes', sub: values.nx_routes ? 'of ' + fmt(values.nx_routes) : null}
    ].forEach(function (t) {
      var v = values[t.key];
      var num = v === undefined ? empty('no data', NO_DATA) : h('span', {class: 'nx-num' + (t.bad ? ' nx-bad' : '')}, [fmt(v) + (t.unit || '')]);
      tiles.appendChild(h('li', {class: 'nx-tile'}, [h('span', {class: 'nx-name'}, [NAMES[t.key]]),
        t.href ? h('a', {href: t.href, title: 'Open the issues'}, [num]) : num,
        v === undefined ? null : t.extra, v === undefined || !t.sub ? null : h('span', {class: 'nx-sub'}, [t.sub])]));
    });

    // route tree
    if (routes.length === 0) {
      treePanel.empty(values.nx_routes === 0 ? 'no routes' : 'no data', values.nx_routes === 0 ? 'No page or route handler found' : NO_DATA);
    } else {
      var rows = flatten(buildTree(routes));
      var maxClients = Math.max.apply(null, routes.map(function (r) { return r.clientModules || 0; }));
      var showAll = rows.length <= TREE_ROWS;
      var key = [{label: 'no issues', color: issueColor(0)}, {label: '1–2', color: issueColor(1)}, {label: '3+', color: issueColor(3)}];
      var redraw = treePanel.draw(function (w) {
        var out = [h('ul', {class: 'nx-legend', style: 'margin:0 0 10px 0', 'aria-label': 'Dot colour: issues of the route'},
          key.map(function (k) { return h('li', {}, [dot(k.color), k.label]); }).concat([
            h('li', {}, [h('span', {class: 'nx-sw', style: 'background:var(--client)'}), 'client modules pulled in']),
            h('li', {class: 'nx-dyn-key'}, [h('i', {style: 'color:var(--dyn)'}, ['[param]']), ' dynamic'])])),
        routeTree(showAll ? rows : rows.slice(0, TREE_ROWS), w, maxClients)];
        if (!showAll) {
          var more = h('button', {type: 'button', class: 'nx-more'}, ['Show all ' + rows.length + ' rows']);
          more.addEventListener('click', function () { showAll = true; redraw(); });
          out.push(more);
        }
        return out;
      });
    }

    // client / server split
    if (values.nx_client_modules === undefined) {
      splitPanel.empty('no data', NO_DATA);
    } else {
      var parts = [{label: 'Client', value: values.nx_client_modules || 0, color: 'var(--client)'},
        {label: 'Server', value: values.nx_server_modules || 0, color: 'var(--server)'},
        {label: 'Shared', value: values.nx_shared_modules || 0, color: 'var(--shared)'}];
      var total = sum(parts);
      splitPanel.draw(function (w) {
        return [stack(parts, w, plural(total, 'module') + ': ' + parts.map(function (p) { return p.label + ' ' + p.value; }).join(', ')),
          legend(parts, total)];
      });
    }

    // Server Actions by file, the dot coloured by the issues of the file
    if (actions.length === 0) {
      actionPanel.empty('none', 'No "use server" function found');
    } else {
      var byFile = {};
      actions.forEach(function (a) {
        (byFile[a.file] = byFile[a.file] || {file: a.file, issues: a.issues || 0, fns: []}).fns.push(a.path.split('#').pop());
      });
      var files = Object.keys(byFile).map(function (k) { return byFile[k]; })
        .sort(function (a, b) { return b.issues - a.issues || (a.file < b.file ? -1 : 1); });
      actionPanel.draw(function () {
        return h('ul', {class: 'nx-actions'}, files.map(function (f) {
          var tip = f.file + ': ' + plural(f.fns.length, 'action') + ', ' + plural(f.issues, 'issue') + ' in the file';
          return h('li', {}, [h('a', {class: 'nx-file', href: fileHref(f.file), title: tip, 'aria-label': tip}, [
            dot(issueColor(f.issues)), f.file]),
          h('div', {class: 'nx-fns', 'aria-hidden': 'true'}, f.fns.map(function (n) { return h('span', {class: 'nx-fn'}, [n]); }))]);
        }));
      });
    }
  }).catch(function (e) {
    tiles.replaceChildren(h('li', {role: 'alert'}, ['Cannot load the measures: ' + e.message]));
    [treePanel, splitPanel, actionPanel].forEach(function (p) { p.fail(e); });
  });

  // quality gate: overall status and the conditions on this plugin's metrics
  api('qualitygates/project_status', {projectKey: project}).then(function (data) {
    var status = data.projectStatus || {};
    var overall = status.status === 'OK' ? 'Passed' : status.status === 'ERROR' ? 'Failed' : null;
    if (!overall) {
      return;
    }
    var chip = function (ok, text) {
      return h('li', {class: 'nx-chip ' + (ok ? 'nx-pass' : 'nx-fail')}, [h('b', {'aria-hidden': 'true'}, [ok ? '✓' : '✗']), text]);
    };
    gate.appendChild(chip(overall === 'Passed', 'Quality gate ' + overall.toLowerCase()));
    (status.conditions || []).filter(function (c) { return /^(new_)?nx_/.test(c.metricKey); }).forEach(function (c) {
      var ok = c.status !== 'ERROR';
      var key = c.metricKey.replace(/^new_/, '');
      gate.appendChild(chip(ok, (c.metricKey === key ? '' : 'New ') + (NAMES[key] || key) + ' ' +
        (c.actualValue === undefined ? '–' : fmt(c.actualValue)) + ' (' + (c.comparator === 'LT' ? '≥ ' : '≤ ') +
        fmt(c.errorThreshold) + ')'));
    });
  }).catch(function () { /* no gate chip */ });

  // open issues of the plugin's rules
  api('rules/search', {repositories: REPOSITORIES.join(','), f: 'name', ps: 500}, false).then(function (data) {
    var names = {};
    (data.rules || []).forEach(function (r) { names[r.key] = r.name; });
    if (Object.keys(names).length === 0) {
      return [];
    }
    return api('issues/search', {componentKeys: project, resolved: 'false', rules: Object.keys(names).join(','), facets: 'rules', ps: 1})
      .then(function (issues) {
        var facet = (issues.facets || []).filter(function (f) { return f.property === 'rules'; })[0] || {values: []};
        // SonarQube facets ignore their own filter (sticky facets): keep the values of this plugin's repositories
        return facet.values.filter(function (v) { return v.count > 0 && LANGS[v.val.split(':')[0]]; }).map(function (v) {
          return {label: names[v.val] || v.val.split(':')[1], tag: LANGS[v.val.split(':')[0]], value: v.count,
            href: page('project/issues', {resolved: 'false', rules: v.val})};
        });
      });
  }).then(function (counts) {
    counts.sort(function (a, b) { return b.value - a.value; });
    if (counts.length === 0) {
      rulePanel.empty('none', 'No open issues of the Next.js rules');
    } else {
      rulePanel.draw(function (w) { return hbars(counts, w, {color: 'var(--red)', label: 'Open issues by rule'}); });
    }
  }).catch(rulePanel.fail);

  return function () {
    if (observer) {
      observer.disconnect();
    }
    root.remove();
  };
});
