package io.github.snakeinprod.nextjs.rules;

import io.github.snakeinprod.nextjs.parse.Node;
import io.github.snakeinprod.nextjs.parse.Nodes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * {@code dangerouslySetInnerHTML={{ __html: value }}} where the value is searchParams, params, form data, the Pages
 * Router query or a fetch response as it is, or a template or concatenation holding one (see {@link ValueFlow.Direct}), and no sanitizer
 * (DOMPurify, sanitize-html, xss, an escape or sanitize helper) is applied to it elsewhere in the function. Any other
 * transformation (a helper, JSON.stringify, a markdown renderer, React state) is not followed.
 */
public class UnsafeHtmlFromInput implements NextRule {

  private static final Pattern SANITIZER = Pattern.compile("(?i).*(sanitiz|purify|xss|escape|clean|strip).*");

  @Override
  public void check(RuleContext ctx) {
    if (ctx.file().tokens().stream().noneMatch(t -> t.isIdent("dangerouslySetInnerHTML"))) {
      return;
    }
    ValueFlow.Direct direct = new ValueFlow.Direct(true, ctx.module().route() != null);
    for (ValueFlow flow : ValueFlow.flows(ctx.file().root(), f -> new ValueFlow(f, direct, ValueFlow.Direct.formDataParams(f)))) {
      List<Node.Group> objects = new ArrayList<>();
      Nodes.groups(flow.scope, g -> {
        List<Node> kids = g.children();
        for (int i = 0; i + 2 < kids.size(); i++) {
          if (Nodes.isIdent(kids.get(i), "dangerouslySetInnerHTML") && kids.get(i + 2) instanceof Node.Group outer && outer.is("{")) {
            if (Nodes.isLeaf(kids.get(i + 1), "=") && outer.children().size() == 1 && outer.children().get(0) instanceof Node.Group inner
              && inner.is("{")) {
              objects.add(inner);
            } else if (Nodes.isLeaf(kids.get(i + 1), ":")) {
              objects.add(outer);
            }
          }
        }
      });
      for (Node.Group object : objects) {
        for (List<Node> item : object.items()) {
          if (!Nodes.isIdent(item.get(0), "__html")) {
            continue;
          }
          List<Node> value = item.size() > 2 && Nodes.isLeaf(item.get(1), ":") ? item.subList(2, item.size()) : item;
          List<List<String>> keys = direct.keys(value, flow.tainted, flow.locals);
          if (keys != null && !flow.checked(keys, objects, false, false, s -> SANITIZER.matcher(s).matches())) {
            ctx.report(value.get(0).first(), value.get(value.size() - 1).last(),
              "Sanitize this HTML from the request or a response (DOMPurify, sanitize-html) or render it as text: "
                + "it allows cross-site scripting.");
          }
        }
      }
    }
  }
}
