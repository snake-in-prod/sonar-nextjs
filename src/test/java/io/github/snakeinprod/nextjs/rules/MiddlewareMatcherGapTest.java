package io.github.snakeinprod.nextjs.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.snakeinprod.nextjs.RuleVerifier;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A session-checking middleware whose matcher leaves protected-looking routes out. */
class MiddlewareMatcherGapTest {

  @Test
  void routes_left_out_of_the_matcher() {
    RuleVerifier.verify("MiddlewareMatcherGap");
  }

  @Test
  void catch_all_matcher_and_routes_checking_themselves() {
    RuleVerifier.verify("MiddlewareMatcherGapCovered", new MiddlewareMatcherGap());
  }

  @Test
  void matcher_meant_for_the_sign_in_page_only() {
    RuleVerifier.verify("MiddlewareMatcherGapNoGuard", new MiddlewareMatcherGap());
  }

  @Test
  void sign_in_pages() {
    RuleVerifier.verify("MiddlewareMatcherGapSignIn", new MiddlewareMatcherGap());
  }

  @Test
  void clerk_route_matcher_deciding_what_is_protected() {
    RuleVerifier.verify("MiddlewareMatcherGapClerk", new MiddlewareMatcherGap());
  }

  @Test
  void clerk_route_matcher_listing_the_public_routes() {
    RuleVerifier.verify("MiddlewareMatcherGapClerkPublic", new MiddlewareMatcherGap());
  }

  @Test
  void authorized_callback_deciding_by_pathname() {
    RuleVerifier.verify("MiddlewareMatcherGapAuthorized", new MiddlewareMatcherGap());
  }

  @Test
  void with_auth_callback_with_a_list_of_protected_routes() {
    RuleVerifier.verify("MiddlewareMatcherGapAuthorizedWithAuth", new MiddlewareMatcherGap());
  }

  @Test
  void authorized_callback_denying_everywhere_not_understood_or_ignored() {
    RuleVerifier.verify("MiddlewareMatcherGapAuthorizedCovered", new MiddlewareMatcherGap());
  }

  @Test
  void next_matcher_semantics() {
    assertThat(covers("/dashboard/:path*", "/dashboard")).isTrue();
    assertThat(covers("/dashboard/:path*", "/dashboard/a/b")).isTrue();
    assertThat(covers("/dashboard/:path*", "/dashboards")).isFalse();
    assertThat(covers("/admin/:id+", "/admin")).isFalse();
    assertThat(covers("/admin/:id+", "/admin/1/2")).isTrue();
    assertThat(covers("/blog/:slug?", "/blog")).isTrue();
    assertThat(covers("/blog/:slug", "/blog/a/b")).isFalse();
    assertThat(covers("/(api|trpc)(.*)", "/api/admin/x")).isTrue();
    assertThat(covers("/(api|trpc)(.*)", "/settings")).isFalse();
    assertThat(covers("/((?!api|_next/static|favicon.ico).*)", "/settings")).isTrue();
    assertThat(covers("/((?!api|_next/static|favicon.ico).*)", "/api/admin")).isFalse();
    assertThat(covers("/:locale(en|de)/settings", "/[locale]/settings")).isTrue();
    assertThat(covers("/:locale(en|de)/settings", "/[locale]/account")).isFalse();
    assertThat(covers("/Settings", "/settings")).isTrue();
    assertThat(covers("/", "/settings")).isFalse();
    assertThat(MiddlewareMatcherGap.compile(List.of("/a/{b}?"))).isNull();
    assertThat(MiddlewareMatcherGap.compile(List.of("dashboard"))).isNull();
  }

  private static boolean covers(String source, String url) {
    return MiddlewareMatcherGap.covered(MiddlewareMatcherGap.compile(List.of(source)), url, List.of("en", "de"));
  }
}
