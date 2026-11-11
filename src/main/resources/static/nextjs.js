/* Project page "Next.js". Placeholder: the routes, client/server split and issue charts come later. Plain DOM
   built with createElement; charts will be inline SVG. */
window.registerExtension('nextjs/nextjs', function (options) {
  var root = document.createElement('div');
  root.setAttribute('role', 'region');
  root.setAttribute('aria-label', 'Next.js');
  root.style.padding = '16px';
  var title = document.createElement('h1');
  title.textContent = 'Next.js';
  var note = document.createElement('p');
  note.textContent = 'Routes, client/server split and issues of this project will be shown here.';
  root.appendChild(title);
  root.appendChild(note);
  // SonarQube mounts extension pages in a two-column grid: span it, or the page gets a narrow column.
  // No CSS container queries here for the same reason.
  options.el.style.gridColumn = '1 / -1';
  options.el.style.width = '100%';
  options.el.appendChild(root);
  return function () {
    root.remove();
  };
});
