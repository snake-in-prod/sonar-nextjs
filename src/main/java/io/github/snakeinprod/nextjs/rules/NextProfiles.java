package io.github.snakeinprod.nextjs.rules;

import org.sonar.api.server.profile.BuiltInQualityProfilesDefinition;

/**
 * The built-in "Next.js" profile for js and ts with the active rules. SonarQube allows one plugin per built-in profile
 * name and language, so the rules cannot join the "Sonar way" profiles the JavaScript analyzer defines; activate them in
 * your own profile, or extend this one.
 */
public class NextProfiles implements BuiltInQualityProfilesDefinition {

  public static final String NAME = "Next.js";

  @Override
  public void define(Context context) {
    for (String language : RuleCatalog.LANGUAGES) {
      NewBuiltInQualityProfile profile = context.createBuiltInQualityProfile(NAME, language);
      for (RuleCatalog.Spec spec : RuleCatalog.ALL) {
        if (spec.active()) {
          profile.activateRule(RuleCatalog.repository(language), spec.key());
        }
      }
      profile.done();
    }
  }
}
