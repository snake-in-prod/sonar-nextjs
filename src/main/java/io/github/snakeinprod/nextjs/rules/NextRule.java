package io.github.snakeinprod.nextjs.rules;

/**
 * One rule. It is called once per module of the project (every JS/TS file, whatever its role) and decides itself
 * which modules it checks. One instance per analysis: a rule may keep state across modules in its fields, such as a
 * cache of what it computed from the model.
 */
public interface NextRule {

  void check(RuleContext ctx);
}
